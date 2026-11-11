"""Published local list views. Paging never opens a client or writes a vault."""
import unicodedata
import uuid
from network import AppFailure, avatar_url


def public_member(value):
    return {key:value.get(key) for key in ('id','username','name')}


def visible_partial(record):
    candidates=[scan for scan in (record.get('draft'),record.get('retainedPartial')) if scan]
    return max(candidates,key=lambda scan:(len(scan.get('members',{})),scan.get('finishedAt') or scan.get('startedAt') or 0),default=None)


def comparison_source(record):
    for scan in reversed(record.get('snapshots', [])):
        if scan.get('complete'):
            return scan
    scan = visible_partial(record)
    return scan if scan and (scan.get('pages', 0) > 0 or scan.get('members')) else None


def text(value):
    return ''.join(c for c in unicodedata.normalize('NFKD', value.casefold()) if not unicodedata.combining(c))


class ListPages:
    def __init__(self):
        self.view = None
        self.signature = None
        self.cached = None

    def publish(self, data):
        account = (data['account'] or {}).get('id')
        owners = frozenset((*data['people'], account))
        signature = (account, owners, tuple((key, tuple((id(s), id(s['members']), len(s['members']),
                            {k:v for k,v in s.items() if k not in ('members','nextCursor','seenCursors')})
                            for s in record.get('snapshots', []) +
                            [s for s in (record.get('draft'), record.get('retainedPartial')) if s]))
                            for key, record in data['lists'].items()))
        if signature != self.signature:
            # Copy the membership mapping at a completed publication boundary.
            # Pages use this stable view even while a later network scan runs.
            records = {}
            for key, record in data['lists'].items():
                def freeze(scan):
                    if scan is None:
                        return None
                    return {**scan, 'members': dict(scan['members'])}
                records[key] = {'snapshots': [freeze(s) for s in record.get('snapshots', [])],
                                'draft': freeze(record.get('draft')), 'retainedPartial': freeze(record.get('retainedPartial'))}
            self.sources = tuple(s for record in data['lists'].values() for s in record.get('snapshots', []) + [s for s in (record.get('draft'), record.get('retainedPartial')) if s])
            self.view = {'accountId': account, 'revision': uuid.uuid4().hex, 'records': records, 'owners': owners}
            self.signature = signature
            self.cached = None
        return self.view

    def picture(self, args):
        view = self.view
        if not isinstance(args,dict) or not view or args.get('accountId') != view['accountId']:return None
        owner, direction, scan_id, identity = (args.get(k) for k in ('ownerId','direction','scanId','id'))
        if (not all(isinstance(v,str) for v in (owner,direction,scan_id,identity)) or
                direction not in ('followers','following')):return None
        record = view['records'].get(owner+':'+direction, {})
        scans = record.get('snapshots',[]) + [s for s in (record.get('draft'),record.get('retainedPartial')) if s]
        scan = next((s for s in scans if s.get('id') == scan_id),None)
        member = scan.get('members',{}).get(identity) if scan else None
        if not member:return None
        picture = member.get('pictureUrl')
        if not avatar_url(picture):
            # Old visible lists may predate thumbnail retention. The same ID's
            # newer page can supply its photo without rewriting membership.
            candidates = [s for s in (record.get('draft'),*reversed(record.get('snapshots',[])),record.get('retainedPartial')) if s]
            picture = next((p['pictureUrl'] for s in candidates for p in [s.get('members',{}).get(identity,{})]
                            if avatar_url(p.get('pictureUrl'))),None)
        if not avatar_url(picture):return None
        return view['accountId'], owner+':'+direction+':'+scan_id, identity, picture

    def page(self, args):
        fields = {'accountId','revision','ownerId','direction','scanId','previousScanId','comparison','relation','query','offset'}
        view = self.view
        if isinstance(args, dict) and 'peerOwnerId' in args:
            fields.add('peerOwnerId')
        if (not isinstance(args, dict) or set(args) != fields or
                any(not isinstance(args[k], str) for k in fields - {'comparison','offset'}) or
                type(args['comparison']) is not bool or type(args['offset']) is not int or args['offset'] < 0 or
                args['offset'] > 40000 or len(args['query']) > 256 or
                args['direction'] not in ('followers','following') or args['relation'] not in ('all','mutual','oneway')):
            raise AppFailure('input', 'Geçersiz liste isteği.')
        if not view or args['accountId'] != view['accountId'] or args['revision'] != view['revision']:
            raise AppFailure('list_view', 'Liste güncellendi. Güncel kayıt yükleniyor.')
        key = (view['revision'], tuple((k, args[k]) for k in sorted(fields - {'offset'})))
        cached = self.cached
        if cached is not None and cached[0] == key:
            rows, summary = cached[1:]
        elif 'peerOwnerId' in args:
            rows, summary = self.common_rows(view, args)
            self.cached = (key, rows, summary)
        else:
            record = view['records'].get(args['ownerId'] + ':' + args['direction'], {})
            scans = record.get('snapshots', [])
            sources = scans + [s for s in (record.get('retainedPartial'), record.get('draft')) if s]
            current = next((s for s in sources if s['id'] == args['scanId']), None)
            previous = next((s for s in scans if s['id'] == args['previousScanId']), None)
            if current is None:
                raise AppFailure('list_view', 'Liste güncellendi. Güncel kayıt yükleniyor.')
            comparable = current in scans and previous in scans and scans.index(previous) < scans.index(current)
            if args['comparison'] and not comparable:
                raise AppFailure('input', 'Karşılaştırma için iki tamamlanmış liste seç.')
            old = previous['members'] if previous else {}
            members = current['members']
            added = sum(identity not in old for identity in members) if comparable else 0
            removed = sum(identity not in members for identity in old) if comparable else 0
            if args['comparison']:
                rows = [(p, current['id'], 'added', args['ownerId']) for i,p in members.items() if i not in old]
                rows += [(p, previous['id'], 'removed', args['ownerId']) for i,p in old.items() if i not in members]
            else:
                rows = [(p, current['id'], 'added' if previous and i not in old else '', args['ownerId']) for i,p in members.items()]
            other = view['records'].get(args['ownerId'] + ':' + ('following' if args['direction']=='followers' else 'followers'), {}).get('snapshots', [])
            if args['relation'] != 'all':
                if not scans or current is not scans[-1] or not other or args['comparison']:
                    raise AppFailure('input', 'Bu kayıt için ilişki filtresi kullanılamıyor.')
                opposite = other[-1]['members']
                rows = [r for r in rows if (r[0]['id'] in opposite) == (args['relation']=='mutual')]
            query = text(args['query'].strip())
            if query:
                rows = [r for r in rows if query in text((r[0].get('name') or '') + ' ' + r[0]['username'])]
            summary = {'added': added, 'removed': removed}
            self.cached = (key, rows, summary)
        start = args['offset']
        items = [{**public_member(p), 'photoScan': scan, 'photoOwner': owner, 'change': change}
                 for p, scan, change, owner in rows[start:start+100]]
        return {'accountId':view['accountId'], 'revision':view['revision'], 'offset':start,
                'items':items, 'total':len(rows), 'nextOffset': start+len(items) if start+len(items)<len(rows) else None,
                **summary}

    def common_rows(self, view, args):
        owners = (args['ownerId'], args['peerOwnerId'])
        if (owners[0] == owners[1] or any(owner not in view['owners'] for owner in owners) or
                args['comparison'] or args['relation'] != 'all' or args['scanId'] or args['previousScanId']):
            raise AppFailure('input', 'Ortak hesaplar için iki farklı kayıtlı profil seç.')
        sources = [comparison_source(view['records'].get(f"{owner}:{args['direction']}", {}))
                   for owner in owners]
        report = {'sources': [metadata(scan) for scan in sources], 'count': None, 'partial': None}
        rows = []
        first, second = sources
        if first is not None and second is not None:
            report['partial'] = not (first['complete'] and second['complete'])
            query = text(args['query'].strip())
            common_count = 0
            for identity, member in first['members'].items():
                peer_member = second['members'].get(identity)
                if peer_member is None:
                    continue
                common_count += 1
                if not query or query in text((member.get('name') or '') + ' ' + member['username']):
                    photo_source = 1 if (not avatar_url(member.get('pictureUrl')) and
                                         avatar_url(peer_member.get('pictureUrl'))) else 0
                    rows.append((member, sources[photo_source]['id'], '', owners[photo_source]))
            report['count'] = common_count
        return rows, {'added': 0, 'removed': 0, 'common': report}


def metadata(scan):
    if scan is None:
        return None
    return {**{k:v for k,v in scan.items() if k not in ('nextCursor','seenCursors','members')}, 'count':len(scan['members'])}
