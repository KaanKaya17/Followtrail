"""List preparation for the owner and watched profiles, with shared pacing."""
import math

DIRECTIONS = ('followers', 'following')
DAY = 86400
MIN_RESCAN = 900
RESUME_AGE = 86400
ATTENTION = {'capacity', 'pagination', 'list_coverage', 'network_policy'}
PLAN_VERSION = 2


def initialize(data):
    data['settings'].setdefault('automaticLists', bool(data['settings'].get('automatic')))


def resumable(draft, now):
    return bool(draft and draft.get('nextCursor') and draft.get('status') in
                ('running', 'queued', 'interrupted', 'partial', 'cancelled') and
                0 <= now - draft.get('startedAt', 0) < RESUME_AGE)


def targets(data):
    return ([data['account']] if data['account'] else []) + [
        person for person in data['people'].values() if person.get('watching')]


def plan(data, now, account=None):
    account = account or data['account']
    if not account:
        return []
    result = []
    for direction in DIRECTIONS:
        record = data['lists'].get(account['id'] + ':' + direction, {})
        latest = next((s for s in reversed(record.get('snapshots', [])) if s.get('complete')), None)
        draft, schedule = record.get('draft'), record.get('schedule', {})
        due = (latest['finishedAt'] + DAY) if latest else 0
        if resumable(draft, now):
            due = 0
        elif latest and latest.get('expected') is not None and account.get(direction) != latest['expected']:
            due = min(due, max(latest['startedAt'] + MIN_RESCAN, account.get('observedAt') or now))
        due = max(due, schedule.get('after', 0))
        error = schedule.get('error')
        result.append({'ownerId': account['id'], 'direction': direction, 'ready': latest is not None,
                       'count': len((draft or latest or {}).get('members', {})),
                       'pages': (draft or latest or {}).get('pages', 0),
                       'resumable': resumable(draft, now), 'error': error,
                       'scanning': bool(draft),
                       'dueAt': None if error in ATTENTION else due})
    return result


def next_job(data, now):
    if not data['settings'].get('automaticLists'):
        return None
    tasks = [p for owner in targets(data) for p in plan(data, now, owner) if p['dueAt'] is not None]
    # Equal baseline deadlines must advance other profiles instead of draining
    # every page of one large profile first. Completed pages own their turn time.
    def order(task):
        records = [data['lists'].get(task['ownerId'] + ':' + direction, {}) for direction in DIRECTIONS]
        attempt = max((record.get('schedule', {}).get('lastAt', 0) for record in records), default=0)
        return task['dueAt'], attempt, task['ownerId'] != (data['account'] or {}).get('id'), task['ownerId'], DIRECTIONS.index(task['direction'])
    return min(tasks, key=order) if tasks else None


def view(data, now):
    items = plan(data, now)
    return {'enabled': data['settings'].get('automaticLists', False),
            'ready': len(items) == 2 and all(p['ready'] for p in items), 'directions': items,
            'rescanSeconds': DAY, 'minimumRescanSeconds': MIN_RESCAN}


def validate(data):
    if type(data['settings'].get('automaticLists', True)) is not bool:
        raise ValueError('automatic lists')
    for record in data['lists'].values():
        if not isinstance(record, dict):
            raise ValueError('list record')
        schedule = record.get('schedule', {})
        if (not isinstance(schedule, dict) or type(schedule.get('after', 0)) not in (int, float) or
                not math.isfinite(schedule.get('after', 0)) or schedule.get('after', 0) < 0 or
                type(schedule.get('lastAt', 0)) not in (int, float) or
                not math.isfinite(schedule.get('lastAt', 0)) or schedule.get('lastAt', 0) < 0 or
                schedule.get('error') is not None and not isinstance(schedule['error'], str)):
            raise ValueError('list schedule')
        draft = record.get('draft')
        if draft is not None and not isinstance(draft, dict):
            raise ValueError('list draft')
        if draft and 'nextCursor' in draft:
            if (type(draft.get('pages')) is not int or not 0 <= draft['pages'] <= 400 or
                    type(draft.get('startedAt')) not in (int, float) or not math.isfinite(draft['startedAt']) or draft['startedAt'] < 0 or
                    draft.get('expected') is not None and (type(draft['expected']) is not int or draft['expected'] < 0)):
                raise ValueError('list progress')
            if (draft['nextCursor'] is not None and
                    (type(draft['nextCursor']) is not str or not 1 <= len(draft['nextCursor']) <= 4096) or
                    not isinstance(draft.get('seenCursors'), list) or len(draft['seenCursors']) > 400 or
                    any(type(v) is not str or len(v) != 64 or any(c not in '0123456789abcdef' for c in v)
                        for v in draft['seenCursors'])):
                raise ValueError('list cursor')
