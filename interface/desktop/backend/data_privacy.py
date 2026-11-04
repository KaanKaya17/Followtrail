"""Local archive retention. Current profiles and the latest list baseline stay usable."""
import copy
import math

CHOICES = (0, 30, 90, 365)
FIELDS = ('historyRetentionDays', 'dailyRetentionDays')
DAY = 86400


def initialize(data):
    for field in FIELDS:
        data['settings'].setdefault(field, 0)
    data.setdefault('privacyMaintenance', {'at': 0, 'failed': False})


def validate(data):
    for field in FIELDS:
        if type(data['settings'].get(field)) is not int or data['settings'][field] not in CHOICES:
            raise ValueError('retention preference')
    state = data['privacyMaintenance']
    if (not isinstance(state, dict) or set(state) != {'at', 'failed'} or
            type(state['at']) not in (int, float) or not math.isfinite(state['at']) or state['at'] < 0 or
            type(state['failed']) is not bool):
        raise ValueError('retention maintenance')


def cutoff(days, now):
    return max(0, now - days * DAY) if days else 0


def prune(data, before):
    """Mutates only archived observations, never counters or list membership facts."""
    if not before:
        return
    for identity, history in data['history'].items():
        # Keep a current baseline even when no newer observation exists.
        data['history'][identity] = [p for index, p in enumerate(history)
                                     if index == len(history)-1 or p['observedAt'] >= before]
    for record in data['lists'].values():
        scans = record.get('snapshots', [])
        record['snapshots'] = [scan for index, scan in enumerate(scans)
                               if index == len(scans)-1 or (scan.get('finishedAt') or scan['startedAt']) >= before]
        retained = record.get('retainedPartial')
        if retained and (retained.get('finishedAt') or retained['startedAt']) < before:
            record.pop('retainedPartial')
        # The current draft belongs to a resumable scan; it is not an old comparison snapshot.
    data['events'] = [event for event in data['events'] if event['at'] >= before]
    logs = []
    for entry in data['notificationLog']:
        changes = [change for change in entry['changes'] if change['at'] >= before]
        if not changes:
            continue
        ids = {change['eventId'] for change in changes}
        logs.append(dict(entry, changes=changes, selectedChangeIds=[key for key in entry['selectedChangeIds'] if key in ids]))
    data['notificationLog'] = logs
    for target in data['wayback']['profiles'].values():
        old = target['entries']
        target['entries'] = [entry for entry in old if entry['lastSeen'] >= before]
        if len(old) != len(target['entries']):
            target['full'] = False
    data['access'] = {identity: case for identity, case in data['access'].items()
                      if max([case['detectedAt'], *[item['checkedAt'] for item in case['checks']]]) >= before}
    import wayback
    wayback.collect(data)


def erase(data, identity):
    """Erase this saved profile's archives, not its membership in somebody else's current list."""
    for field in ('people', 'history', 'automaticAttempts', 'access', 'notificationRules'):
        data[field].pop(identity, None)
    data['lists'] = {key: value for key, value in data['lists'].items() if key.partition(':')[0] != identity}
    data['events'] = [event for event in data['events'] if event.get('personId') != identity]
    logs = []
    for entry in data['notificationLog']:
        changes = [change for change in entry['changes'] if change['personId'] != identity]
        if changes:
            ids = {change['eventId'] for change in changes}
            logs.append(dict(entry, changes=changes, selectedChangeIds=[key for key in entry['selectedChangeIds'] if key in ids]))
    data['notificationLog'] = logs
    data['wayback']['profiles'].pop(identity, None)
    import wayback
    wayback.collect(data)


def retained(data, before):
    result = copy.deepcopy(data)
    prune(result, before)
    return result
