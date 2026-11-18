"""Bounded rolling local budgets, not claimed Instagram quotas."""
import math
from request_defaults import ACCOUNT_BUDGET, DEVICE_BUDGET, AUTO_PROFILE_PERCENT, MEDIA_BUDGET

SHARED = DEVICE_BUDGET
ACCOUNT = ACCOUNT_BUDGET
PROFILE_ACCOUNT = tuple(max(1, limit * AUTO_PROFILE_PERCENT // 100) for limit in ACCOUNT)
PROFILE_SHARED = tuple(max(1, limit * AUTO_PROFILE_PERCENT // 100) for limit in SHARED)
# Old vaults may contain more reservations than the new policy permits. Keep
# them counted until they age out; lowering a quota must never invalidate/reset it.
MAX_STORED = max(4800, SHARED[1])
# Cookieless CDN media has its own device ledger; it never spends API budget.
MEDIA = MEDIA_BUDGET
MAX_MEDIA_STORED = MEDIA[1]


def policy():
    return {'sharedHourly': SHARED[0], 'sharedDaily': SHARED[1],
            'accountHourly': ACCOUNT[0], 'accountDaily': ACCOUNT[1],
            'mediaHourly': MEDIA[0], 'mediaDaily': MEDIA[1],
            'automaticProfilePercent': AUTO_PROFILE_PERCENT}


def validate(times, maximum):
    if (not isinstance(times, list) or len(times) > maximum or
            any(type(t) not in (int, float) or not math.isfinite(t) or t < 0 for t in times) or
            times != sorted(times)):
        raise ValueError('request budget')


def available_at(times, now, limits):
    # Future timestamps remain counted after a clock rollback. No budget reset.
    recent = [t for t in times if t > now - 86400]
    hour = [t for t in recent if t > now - 3600]
    return max(hour[-limits[0]] + 3600 if len(hour) >= limits[0] else 0,
               recent[-limits[1]] + 86400 if len(recent) >= limits[1] else 0)


def reserve(times, now):
    return sorted([t for t in times if t > now - 86400] + [now])


def next_at(gate, owner, now):
    return max(available_at(gate.get('requestTimes', []), now, SHARED),
               available_at(gate.get('accountRequestTimes', {}).get(owner, []), now, ACCOUNT) if owner else 0)


def record(gate, owner, now):
    gate['requestTimes'] = reserve(gate.get('requestTimes', []), now)
    accounts = {key:[t for t in times if t > now - 86400]
                for key,times in gate.get('accountRequestTimes', {}).items()}
    accounts = {key:times for key,times in accounts.items() if times}
    if owner:
        accounts[owner] = reserve(accounts.get(owner, []), now)
    gate['accountRequestTimes'] = accounts


def media_next_at(gate, now):
    return available_at(gate.get('mediaTimes', []), now, MEDIA)


def record_media(gate, now):
    gate['mediaTimes'] = reserve(gate.get('mediaTimes', []), now)


def profile_next_at(gate, owner, now):
    """Leave headroom for lists and explicit work; never modify the full ledger."""
    return max(available_at(gate.get('requestTimes', []), now, PROFILE_SHARED),
               available_at(gate.get('accountRequestTimes', {}).get(owner, []), now, PROFILE_ACCOUNT) if owner else 0)


def validate_gate(gate):
    validate(gate.get('requestTimes', []), MAX_STORED)
    validate(gate.get('mediaTimes', []), MAX_MEDIA_STORED)
    media_at = gate.get('mediaNextAt', 0)
    if type(media_at) not in (int, float) or not math.isfinite(media_at) or media_at < 0:
        raise ValueError('media reservation')
    accounts = gate.get('accountRequestTimes', {})
    if not isinstance(accounts, dict):
        raise ValueError('account budget')
    for owner, times in accounts.items():
        if not isinstance(owner, str) or not owner.isascii() or not owner.isdigit() or not 1 <= len(owner) <= 30:
            raise ValueError('budget owner')
        validate(times, MAX_STORED)
