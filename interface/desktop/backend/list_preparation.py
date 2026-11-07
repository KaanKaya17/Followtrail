"""List preparation for the owner and watched profiles, with shared pacing."""
import math

DIRECTIONS = ('followers', 'following')
DAY = 86400
MIN_RESCAN = 60
RESUME_AGE = 86400
RECOVERY_AGE = 600
RECOVERY_PASSES = 3
FIRST_RETRY = 1800
ATTENTION = {'capacity', 'pagination', 'list_coverage', 'network_policy'}
PLAN_VERSION = 6


def initialize(data):
    data['settings'].setdefault('automaticLists', bool(data['settings'].get('automatic')))
    # Older scans persisted the wire parser's empty end marker in partial
    # drafts. The vault owns absence as None, including after a count mismatch.
    for record in data['lists'].values():
        draft = record.get('draft') if isinstance(record, dict) else None
        if isinstance(draft, dict) and draft.get('nextCursor') == '':
            draft['nextCursor'] = None


def resumable(draft, now):
    age = RECOVERY_AGE if draft and draft.get('recoveryPass') else RESUME_AGE
    return bool(draft and (draft.get('nextCursor') or draft.get('restartPending') is True) and draft.get('status') in
                ('running', 'queued', 'interrupted', 'partial', 'cancelled') and
                0 <= now - draft.get('startedAt', 0) < age)


def queue_initial(data, identity):
    for direction in DIRECTIONS:
        data['lists'].setdefault(f'{identity}:{direction}', {
            'snapshots': [], 'schedule': {'initial': 'queued'},
        })


def count_retry_delay(schedule):
    if schedule.get('initial') == 'queued':
        schedule['initial'] = 'retry'
        return FIRST_RETRY
    return DAY


def plan(data, now, account=None):
    account = account or data['account']
    if not account:
        return []
    result = []
    for direction in DIRECTIONS:
        record = data['lists'].get(account['id'] + ':' + direction, {})
        latest = next((s for s in reversed(record.get('snapshots', [])) if s.get('complete')), None)
        draft, schedule = record.get('draft'), record.get('schedule', {})
        initial = not latest and schedule.get('initial') in ('queued', 'retry')
        due = (latest['finishedAt'] + DAY) if latest else 0
        if resumable(draft, now):
            due = 0
        elif latest and latest.get('expected') is not None and account.get(direction) != latest['expected']:
            due = min(due, max(latest['startedAt'] + MIN_RESCAN, account.get('observedAt') or now))
        error = schedule.get('error')
        if error == 'list_count' and draft and draft.get('finishedAt') is not None:
            # A fresh changed count is new evidence; repeating the identical
            # exhausted scan every half hour is not. Apply to old schedules too.
            finished = draft['finishedAt']
            changed = (account.get(direction) is not None and account.get(direction) != draft.get('expected') and
                       (account.get('observedAt') or 0) > finished)
            retry = FIRST_RETRY if initial and schedule['initial'] == 'retry' else DAY
            due = max(finished + MIN_RESCAN, account['observedAt']) if changed else max(
                due, schedule.get('after', 0), finished + retry)
        else:
            due = max(due, schedule.get('after', 0))
        result.append({'ownerId': account['id'], 'direction': direction, 'ready': latest is not None,
                       'count': len((draft or latest or {}).get('members', {})),
                       'pages': (draft or latest or {}).get('pages', 0),
                       'resumable': resumable(draft, now), 'error': error, 'initial': initial,
                       'scanning': bool(draft),
                       'dueAt': None if error in ATTENTION else due})
    return result


def next_job(data, now):
    if not data['settings'].get('automaticLists'):
        return None
    own = data['account']
    owners = ([own] if own else []) + list(data['people'].values())
    tasks = [task for owner in owners for task in plan(data, now, owner)
             if task['dueAt'] is not None and (owner is own or owner.get('watching') or task['initial'])]
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
                schedule.get('error') is not None and not isinstance(schedule['error'], str) or
                schedule.get('initial') not in (None, 'queued', 'retry')):
            raise ValueError('list schedule')
        draft = record.get('draft')
        if draft is not None and not isinstance(draft, dict):
            raise ValueError('list draft')
        if draft and 'nextCursor' in draft:
            if (type(draft.get('recoveryPass', 0)) is not int or not 0 <= draft.get('recoveryPass', 0) <= RECOVERY_PASSES or
                    type(draft.get('restartPending', False)) is not bool or
                    type(draft.get('overlapRows', 0)) is not int or not 0 <= draft.get('overlapRows', 0) <= 400000 or
                    draft.get('recoveryPass') and (draft.get('overlapRows', 0) < 1 or draft.get('pages', 0) < 1 or draft.get('expected') is None) or
                    type(draft.get('recoveryStartCount', 0)) is not int or not 0 <= draft.get('recoveryStartCount', 0) <= 20000 or
                    draft.get('restartPending') and (not draft.get('recoveryPass') or draft.get('nextCursor') is not None)):
                raise ValueError('list recovery')
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
