"""Account-local diagnosis for ordinary profile and list reads."""
import math


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


def paused(data, reader):
    return data.get('readHealth', {}).get(reader, {}).get('paused') is True


def record(data, reader, reason, status, now):
    if reason is None:
        return
    health = data.setdefault('readHealth', {})
    previous = health.get(reader, {})
    if previous.get('paused'):
        # A failed manual retry cannot overwrite the reason for the stop.
        return
    streak = (previous['streak'] + 1 if previous.get('reason') == reason else 1) if reason in COMPATIBILITY else 0
    health[reader] = {'reason': reason, 'http': status, 'at': now, 'streak': streak,
                      'paused': streak >= PAUSE_AFTER}


def success(data, reader, *, complete=True):
    if complete or not paused(data, reader):
        data.get('readHealth', {}).pop(reader, None)


def validate(data):
    health = data.get('readHealth', {})
    if not isinstance(health, dict) or set(health) - set(READERS):
        raise ValueError('reader health')
    for item in health.values():
        if (not isinstance(item, dict) or set(item) != {'reason', 'http', 'at', 'streak', 'paused'}
                or type(item['reason']) is not str or item['reason'] not in REASONS
                or (item['http'] is not None and (type(item['http']) is not int or not 100 <= item['http'] <= 599))
                or type(item['at']) not in (int, float) or not math.isfinite(item['at']) or item['at'] < 0
                or type(item['streak']) is not int or not 0 <= item['streak'] <= PAUSE_AFTER
                or type(item['paused']) is not bool or item['paused'] != (item['streak'] == PAUSE_AFTER)
                or (item['reason'] in COMPATIBILITY) != (item['streak'] > 0)):
            raise ValueError('reader health entry')
