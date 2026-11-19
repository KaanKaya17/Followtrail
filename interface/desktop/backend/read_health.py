"""Account-local diagnosis for ordinary profile and list reads."""
import math

from network import failure


READERS = ('profile', 'followers', 'following')
PAUSE_AFTER = 3
COMPATIBILITY = {'format', 'identity', 'route', 'redirect'}
OTHER_REASONS = {
    'limited': 'limited', 'session': 'session', 'verification': 'verification',
    'private': 'private', 'list_count': 'partial', 'list_coverage': 'partial',
    'pagination': 'pagination', 'network_policy': 'policy', 'budget': 'budget',
    'cooldown': 'budget', 'capacity': 'capacity',
}
REASONS = COMPATIBILITY | set(OTHER_REASONS.values()) | {'server', 'connection', 'target', 'cursor', 'unknown'}
# The request each reader sends, without its identity. A report quotes this
# template so a changed Instagram route can be matched to the app's own one.
ROUTES = {
    'profile': 'GET i.instagram.com/api/v1/users/{id}/info/',
    'followers': 'GET i.instagram.com/api/v1/friendships/{id}/followers/',
    'following': 'GET i.instagram.com/api/v1/friendships/{id}/following/',
}
# Field names a reader needs. Only names are kept: never a value, a name or an ID.
FIELDS = ('json', 'status', 'users', 'users.pk', 'users.username', 'user', 'user.pk',
          'user.username', 'user.follower_count', 'user.following_count')


def http_status(error, response=None):
    received = getattr(error, 'response', None)
    value = getattr(error, 'http', None)
    if value is None:
        value = getattr(received if received is not None else response, 'status_code', None)
    return value if type(value) is int and 100 <= value <= 599 else None


def diagnose(error, code, *, status=None, own=False, continuation=False):
    if code == 'cancelled':
        return None
    if code in OTHER_REASONS:
        return OTHER_REASONS[code]
    if continuation and status in (400, 404):
        return 'cursor'
    if status == 405 or (own and status in (404, 410)):
        return 'route'
    if status in (404, 410) or code == 'not_found':
        return 'target'
    if status is not None and status >= 500:
        return 'server'
    if code == 'identity':
        return 'identity'
    if code == 'redirect':
        return 'redirect'
    if code in ('profile', 'list', 'response_size') or (
            type(error).__name__ == 'ClientJSONDecodeError' and status == 200):
        return 'format'
    return 'connection' if code == 'connection' and status is None else 'unknown'


def missing(reader, payload):
    """Names of the expected fields a response lacked; empty when it had them all."""
    if not isinstance(payload, dict):
        return ['json']
    if reader == 'profile':
        user = payload.get('user')
        if not isinstance(user, dict):
            return ['user']
        found = [] if type(user.get('pk')) in (int, str) and str(user['pk']).isdigit() else ['user.pk']
        found += [] if isinstance(user.get('username'), str) and user['username'] else ['user.username']
        return found + ['user.' + key for key in ('follower_count', 'following_count')
                        if type(user.get(key)) is not int or user[key] < 0]
    found = [] if payload.get('status') == 'ok' else ['status']
    users = payload.get('users')
    if not isinstance(users, list):
        return found + ['users']
    rows = [row if isinstance(row, dict) else {} for row in users]
    if any(type(row.get('pk')) not in (int, str) or not str(row.get('pk')).isdigit() for row in rows):
        found.append('users.pk')
    if any(not isinstance(row.get('username'), str) or not row['username'] for row in rows):
        found.append('users.username')
    return found


def observe(data, reader, error, response, now, *, own=False, continuation=False):
    """Diagnose and record one failed read. Returns (HTTP status, whether this failure paused the reader)."""
    code = failure(error)['code']
    local = code in ('cancelled', 'budget', 'cooldown', 'network_policy')
    status = None if local else http_status(error, response)
    reason = diagnose(error, code, status=status, own=own, continuation=continuation)
    # Field names come only from a body that arrived and failed to parse.
    fields = missing(reader, error.payload) if code in ('profile', 'list') and hasattr(error, 'payload') else ()
    return status, record(data, reader, reason, status, now, fields)


def paused(data, reader):
    return data.get('readHealth', {}).get(reader, {}).get('paused') is True


def record(data, reader, reason, status, now, fields=()):
    """Store the diagnosis. True only when this failure is the one that pauses the reader."""
    if reason is None:
        return False
    health = data.setdefault('readHealth', {})
    previous = health.get(reader, {})
    if previous.get('paused'):
        # A failed manual retry cannot overwrite the reason for the stop.
        return False
    streak = (previous['streak'] + 1 if previous.get('reason') == reason else 1) if reason in COMPATIBILITY else 0
    health[reader] = {'reason': reason, 'http': status, 'at': now, 'streak': streak,
                      'paused': streak >= PAUSE_AFTER}
    fields = sorted({field for field in fields if field in FIELDS}, key=FIELDS.index)
    if reason == 'format' and fields:
        health[reader]['missing'] = fields
    return health[reader]['paused']


def success(data, reader, *, complete=True):
    if complete or not paused(data, reader):
        data.get('readHealth', {}).pop(reader, None)


def validate(data):
    health = data.get('readHealth', {})
    if not isinstance(health, dict) or set(health) - set(READERS):
        raise ValueError('reader health')
    for item in health.values():
        fields = item.get('missing', ['json']) if isinstance(item, dict) else None
        if (not isinstance(item, dict) or set(item) - {'missing'} != {'reason', 'http', 'at', 'streak', 'paused'}
                or not isinstance(fields, list) or not fields or len(set(fields)) != len(fields)
                or any(field not in FIELDS for field in fields) or ('missing' in item and item['reason'] != 'format')
                or type(item['reason']) is not str or item['reason'] not in REASONS
                or (item['http'] is not None and (type(item['http']) is not int or not 100 <= item['http'] <= 599))
                or type(item['at']) not in (int, float) or not math.isfinite(item['at']) or item['at'] < 0
                or type(item['streak']) is not int or not 0 <= item['streak'] <= PAUSE_AFTER
                or type(item['paused']) is not bool or item['paused'] != (item['streak'] == PAUSE_AFTER)
                or (item['reason'] in COMPATIBILITY) != (item['streak'] > 0)):
            raise ValueError('reader health entry')
