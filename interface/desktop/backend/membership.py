"""Durable list membership ledger: first/last seen, departures and returns.

Only completed list scans are evidence. A departure or return happened at some
moment between two completed scans, so the ledger keeps both boundaries and
never claims an exact event time. Entries are replaced, never mutated, so a
published view can share them with a shallow copy.
"""
import math
import re

VERSION = 1
MAX_SPANS = 24
MAX_ENTRIES = 40000
_ID = re.compile(r'[1-9][0-9]{0,29}')
_NAME = re.compile(r'[A-Za-z0-9._]{1,30}')


def _blank():
    return {'version': VERSION, 'scans': 0, 'baselineAt': None, 'appliedAt': None, 'appliedId': None, 'members': {}}


def _finished(scan):
    return scan.get('finishedAt') or scan.get('startedAt') or 0


def apply(record, scan):
    """Fold one completed scan into the record's ledger. Returns returned member IDs."""
    if not scan or scan.get('complete') is not True:
        return []
    ledger = record.get('membership')
    at = _finished(scan)
    if not isinstance(ledger, dict):
        # A restored or older record: earlier retained scans come first.
        ledger = record['membership'] = _blank()
        for older in sorted((s for s in record.get('snapshots', []) if s is not scan and s.get('complete') is True
                             and _finished(s) < at), key=_finished):
            apply(record, older)
    # Each scan is evidence once; an older scan never rewrites newer facts.
    if scan.get('id') == ledger['appliedId'] or (ledger['appliedAt'] is not None and at < ledger['appliedAt']):
        return []
    members = ledger['members']
    present = scan['members']
    returned = []
    for identity, person in present.items():
        entry = members.get(identity)
        spans = list(entry['spans']) if entry else []
        if spans and spans[-1]['gone'] is None:
            spans[-1] = {**spans[-1], 'to': at}
        else:
            if spans:
                returned.append(identity)
            spans.append({'from': at, 'to': at, 'gone': None})
        members[identity] = {'username': person['username'], 'name': person.get('name'),
                             'spans': spans[-MAX_SPANS:], 'scanId': scan.get('id')}
    for identity, entry in list(members.items()):
        if identity not in present and entry['spans'][-1]['gone'] is None:
            spans = list(entry['spans'])
            spans[-1] = {**spans[-1], 'gone': at}
            members[identity] = {**entry, 'spans': spans}
    if len(members) > MAX_ENTRIES:
        # Present members always stay; the oldest departures leave first.
        gone = sorted((e['spans'][-1]['gone'], i) for i, e in members.items() if e['spans'][-1]['gone'] is not None)
        for _, identity in gone[:len(members) - MAX_ENTRIES]:
            members.pop(identity)
    if ledger['baselineAt'] is None:
        ledger['baselineAt'] = at
    ledger['scans'] += 1
    ledger['appliedAt'] = at
    ledger['appliedId'] = scan.get('id')
    return returned


def ensure(data):
    """Backfill a ledger from the retained completed snapshots of older vaults."""
    for record in data['lists'].values():
        if isinstance(record.get('membership'), dict):
            continue
        scans = sorted((s for s in record.get('snapshots', []) if s.get('complete') is True), key=_finished)
        if not scans:
            continue
        record['membership'] = _blank()
        for scan in scans:
            apply(record, scan)


def returned_at(entry, at):
    """True when the member came back in the scan that finished at `at`."""
    return any(index > 0 and span['from'] == at for index, span in enumerate(entry['spans']))


def movement(entry, ledger):
    spans = entry['spans']
    last = spans[-1]
    result = {'firstSeen': spans[0]['from'], 'baseline': spans[0]['from'] == ledger['baselineAt'],
              'lastSeen': last['to'], 'returns': len(spans) - 1, 'present': last['gone'] is None}
    if last['gone'] is not None:
        result['leftBetween'] = [last['to'], last['gone']]
    if len(spans) > 1:
        before = spans[-2] if last['gone'] is None else spans[-1]
        if last['gone'] is None:
            result['returnedAt'] = last['from']
            result['previousLeftBetween'] = [before['to'], before['gone']]
        else:
            result['previousReturnedAt'] = last['from']
    return result


def counts(ledger):
    if not isinstance(ledger, dict):
        return None
    returned = left = 0
    for entry in ledger['members'].values():
        if entry['spans'][-1]['gone'] is not None:
            left += 1
        elif len(entry['spans']) > 1:
            returned += 1
    return {'scans': ledger['scans'], 'baselineAt': ledger['baselineAt'], 'appliedAt': ledger['appliedAt'],
            'returned': returned, 'left': left}


def rows(ledger, kind):
    """(member, entry) pairs for 'returned' or 'left', newest movement first."""
    if not isinstance(ledger, dict):
        return []
    selected = []
    for identity, entry in ledger['members'].items():
        last = entry['spans'][-1]
        if kind == 'left' and last['gone'] is not None:
            selected.append((last['gone'], identity, entry))
        elif kind == 'returned' and last['gone'] is None and len(entry['spans']) > 1:
            selected.append((last['from'], identity, entry))
    selected.sort(key=lambda item: (-item[0], item[2]['username']))
    return [({'id': identity, 'username': entry['username'], 'name': entry.get('name')}, entry)
            for _, identity, entry in selected]


def prune(record, before):
    """Retention: forget movement older than `before`; current membership stays."""
    ledger = record.get('membership')
    if not before or not isinstance(ledger, dict):
        return
    kept = {}
    for identity, entry in ledger['members'].items():
        spans = [span for index, span in enumerate(entry['spans'])
                 if index == len(entry['spans']) - 1 or (span['gone'] or span['to']) >= before]
        last = spans[-1]
        if last['gone'] is not None and last['gone'] < before:
            continue
        kept[identity] = {**entry, 'spans': spans} if len(spans) != len(entry['spans']) else entry
    ledger['members'] = kept


def validate(record):
    ledger = record.get('membership')
    if ledger is None:
        return

    def moment(value):
        return type(value) in (int, float) and math.isfinite(value) and value >= 0
    if (not isinstance(ledger, dict) or set(ledger) != {'version', 'scans', 'baselineAt', 'appliedAt', 'appliedId', 'members'}
            or ledger['version'] != VERSION or type(ledger['scans']) is not int or ledger['scans'] < 0
            or not isinstance(ledger['members'], dict) or len(ledger['members']) > MAX_ENTRIES):
        raise ValueError('membership')
    for key in ('baselineAt', 'appliedAt'):
        if ledger[key] is not None and not moment(ledger[key]):
            raise ValueError('membership time')
    if ledger['appliedId'] is not None and type(ledger['appliedId']) is not str:
        raise ValueError('membership scan')
    for identity, entry in ledger['members'].items():
        if (not isinstance(identity, str) or not _ID.fullmatch(identity) or not isinstance(entry, dict)
                or set(entry) != {'username', 'name', 'spans', 'scanId'}
                or type(entry['username']) is not str or not _NAME.fullmatch(entry['username'])
                or (entry['name'] is not None and type(entry['name']) is not str)
                or (entry['scanId'] is not None and type(entry['scanId']) is not str)
                or not isinstance(entry['spans'], list) or not 1 <= len(entry['spans']) <= MAX_SPANS):
            raise ValueError('membership entry')
        previous = None
        for index, span in enumerate(entry['spans']):
            if (not isinstance(span, dict) or set(span) != {'from', 'to', 'gone'} or not moment(span['from'])
                    or not moment(span['to']) or span['from'] > span['to']
                    or (span['gone'] is not None and (not moment(span['gone']) or span['gone'] < span['to']))
                    or (index < len(entry['spans']) - 1 and span['gone'] is None)
                    or (previous is not None and span['from'] < previous)):
                raise ValueError('membership span')
            previous = span['gone']
