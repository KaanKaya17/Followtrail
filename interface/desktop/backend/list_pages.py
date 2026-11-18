"""Published local list views. Paging never opens a client or writes a vault."""
import unicodedata
import uuid
import re
from network import AppFailure, avatar_url
import membership as ledger_rules


def validate_exceptions(data):
    values = data.get('followingExceptions', [])
    if (not isinstance(values, list) or len(values) > 40000 or
            any(not isinstance(value, str) or not re.fullmatch(r'[1-9][0-9]{0,29}', value) for value in values)
            or len(set(values)) != len(values)):
        raise ValueError('followingExceptions')


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
        exceptions = tuple(data.get('followingExceptions', []))
        signature = (account, owners, exceptions, tuple((key, tuple((id(s), id(s['members']), len(s['members']),
                            {k:v for k,v in s.items() if k not in ('members','nextCursor','seenCursors')})
                            for s in record.get('snapshots', []) +
                            [s for s in (record.get('draft'), record.get('retainedPartial')) if s]))
                            for key, record in data['lists'].items()),
                     tuple((key, ledger.get('appliedId'), id(ledger.get('members')), len(ledger.get('members') or ()))
                           for key, record in data['lists'].items() for ledger in [record.get('membership') or {}]))
        if signature != self.signature:
            # Copy the membership mapping at a completed publication boundary.
            # Pages use this stable view even while a later network scan runs.
            records = {}
            for key, record in data['lists'].items():
                def freeze(scan):
                    if scan is None:
                        return None
                    return {**scan, 'members': dict(scan['members'])}
                ledger = record.get('membership')
                records[key] = {'snapshots': [freeze(s) for s in record.get('snapshots', [])],
                                'draft': freeze(record.get('draft')), 'retainedPartial': freeze(record.get('retainedPartial')),
                                # Entries are replaced, never mutated: a shallow copy is a stable view.
                                'membership': {**ledger, 'members': dict(ledger['members'])} if ledger else None}
            self.sources = tuple(s for record in data['lists'].values() for s in record.get('snapshots', []) + [s for s in (record.get('draft'), record.get('retainedPartial')) if s])
            self.view = {'accountId': account, 'revision': uuid.uuid4().hex, 'records': records, 'owners': owners,
                         'followingExceptions': frozenset(exceptions)}
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
                args['direction'] not in ('followers','following') or
                args['relation'] not in ('all','mutual','oneway','returned','left')):
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
            ledger = record.get('membership')
            entries = ledger['members'] if ledger else {}
            # Mark returns only where the ledger has seen the scan that holds them.
            rows = [(p, scan, 'returned' if change == 'added' and p['id'] in entries and
                     ledger_rules.returned_at(entries[p['id']], current.get('finishedAt')) else change, owner)
                    for p, scan, change, owner in rows]
            other = view['records'].get(args['ownerId'] + ':' + ('following' if args['direction']=='followers' else 'followers'), {}).get('snapshots', [])
            if args['relation'] in ('returned', 'left'):
                if (not scans or current is not scans[-1] or args['comparison'] or not ledger
                        or ledger['appliedId'] != current.get('id')):
                    raise AppFailure('input', 'Bu kayıt için ilişki filtresi kullanılamıyor.')
                # Departed members are not in the current scan; keep the photo
                # source only while that older scan is still retained.
                retained = {s['id'] for s in scans}
                rows = [(p, entry['scanId'] if entry['scanId'] in retained else '',
                         args['relation'], args['ownerId'], ledger_rules.movement(entry, ledger))
                        for p, entry in ledger_rules.rows(ledger, args['relation'])]
            elif args['relation'] != 'all':
                if not scans or current is not scans[-1] or not other or args['comparison']:
                    raise AppFailure('input', 'Bu kayıt için ilişki filtresi kullanılamıyor.')
                opposite = other[-1]['members']
                rows = [r for r in rows if (r[0]['id'] in opposite) == (args['relation']=='mutual')]
            query = text(args['query'].strip())
            if query:
                rows = [r for r in rows if query in text((r[0].get('name') or '') + ' ' + r[0]['username'])]
            summary = {'added': added, 'removed': removed}
            if (args['ownerId'] == view['accountId'] and args['direction'] == 'following'
                    and args['relation'] == 'oneway' and not args['comparison']):
                exceptions = view['followingExceptions']
                normal = [r for r in rows if r[0]['id'] not in exceptions]
                separate = [r for r in rows if r[0]['id'] in exceptions]
                rows = normal + separate
                summary.update(mainTotal=len(normal), exceptionTotal=len(separate))
            self.cached = (key, rows, summary)
        start = args['offset']
        items = [{**public_member(p), 'photoScan': scan, 'photoOwner': owner, 'change': change,
                  **({'movement': extra[0]} if extra else {}),
                  **({'exception': index >= summary['mainTotal']} if 'mainTotal' in summary else {})}
                 for index, (p, scan, change, owner, *extra) in enumerate(rows[start:start+100], start)]
        return {'accountId':view['accountId'], 'revision':view['revision'], 'offset':start,
                'items':items, 'total':len(rows), 'nextOffset': start+len(items) if start+len(items)<len(rows) else None,
                **summary}

    def membership(self, args):
        """The active account's own two lists, for one person. Local only."""
        view = self.view
        if (not isinstance(args, dict) or set(args) != {'accountId', 'id'} or not view
                or args['accountId'] != view['accountId'] or not isinstance(args['id'], str)):
            raise AppFailure('input', 'Geçersiz liste isteği.')
        result = {'accountId': view['accountId'], 'id': args['id']}
        for direction in ('followers', 'following'):
            ledger = view['records'].get(f"{view['accountId']}:{direction}", {}).get('membership')
            entry = ledger['members'].get(args['id']) if ledger else None
            result[direction] = ({**ledger_rules.movement(entry, ledger), 'scans': ledger['scans'],
                                  'appliedAt': ledger['appliedAt']} if entry else
                                 {'present': False, 'scans': ledger['scans'], 'appliedAt': ledger['appliedAt']} if ledger else None)
        return result

    def set_exception(self, data, args):
        view = self.view
        account = (data['account'] or {}).get('id')
        identity = args.get('id')
        enabled = args.get('enabled')
        if (not account or not view or account != view['accountId'] or args.get('accountId') != account
                or args.get('revision') != view['revision'] or not isinstance(identity, str) or type(enabled) is not bool):
            raise AppFailure('input', 'Geçersiz liste isteği.')
        records = view['records']
        following = records.get(account + ':following', {}).get('snapshots', [])
        followers = records.get(account + ':followers', {}).get('snapshots', [])
        if (not following or not followers or identity not in following[-1]['members']
                or identity in followers[-1]['members']):
            raise AppFailure('list_view', 'Liste güncellendi. Güncel kayıt yükleniyor.')
        current = view['followingExceptions']
        updated = (current | {identity}) if enabled else (current - {identity})
        if len(updated) > 40000:
            raise AppFailure('input', 'Geçersiz liste isteği.')
        if updated == current:
            return False
        data['followingExceptions'] = sorted(updated)
        return True

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
