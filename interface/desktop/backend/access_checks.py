"""Evidence of visibility from the connected account, never proof of a block."""
import math
import re
import uuid
from dataclasses import dataclass

from instagrapi.exceptions import ClientNotFoundError
from network import AppFailure, failure, parse_profile
import anonymous_visibility

MAX_CASES = 200
ASSESSMENTS = {'accessible', 'suspected', 'unavailable', 'you_blocked', 'unknown', 'present'}
STAGES = {'pending', 'checking', 'done'}


def initialize(data):
    data.setdefault('access', {})
    data.setdefault('accessPlan', {'status': 'idle'})
    for case in data['access'].values():
        if isinstance(case, dict):
            case.setdefault('alertStrength', 'limited' if case.get('alertRecorded') else None)
    if isinstance(data.get('settings'), dict):
        data['settings'].setdefault('investigateMissing', False)


def cases_view(data):
    return [{**case, 'status': case['stage'] if case['stage'] != 'done' else
             case['checks'][-1]['status'] if case['checks'] else 'unknown'}
            for case in sorted(data['access'].values(), key=lambda c: c['detectedAt'], reverse=True)]


def ensure_case(data, member, now):
    identity = member['id']
    case = data['access'].get(identity)
    if case:
        return case
    if len(data['access']) >= MAX_CASES:
        finished = [c for c in data['access'].values() if c['stage'] == 'done']
        if not finished:
            raise AppFailure('capacity', 'Erişim kontrol sırası dolu. Önce bekleyen kontrolleri tamamla.')
        oldest = min(finished, key=lambda c: c['detectedAt'])
        del data['access'][oldest['id']]
    case = {'id': identity, 'ownerId': data['account']['id'],
            'username': member['username'], 'name': member.get('name') or member['username'],
            'detectedAt': now, 'stage': 'done', 'episode': uuid.uuid4().hex,
            'absence': None, 'alertRecorded': False, 'alertStrength': None, 'checks': []}
    data['access'][identity] = case
    return case


def absence_evidence(data, identity):
    owner = (data.get('account') or {}).get('id')
    scans = [s for s in data['lists'].get(f'{owner}:followers', {}).get('snapshots', []) if s.get('complete') is True]
    if not scans or identity in scans[-1]['members']:
        return None
    latest = scans[-1]
    before = next((s for s in reversed(scans[:-1]) if identity in s['members']), None)
    previous = data['access'].get(identity, {}).get('absence')
    if before:
        return {'ownerId': owner, 'previousScanId': before['id'], 'previousAt': before['finishedAt'],
                'currentScanId': latest['id'], 'currentAt': latest['finishedAt']}
    if previous and previous['ownerId'] == owner:
        return {**previous, 'currentScanId': latest['id'], 'currentAt': latest['finishedAt']}
    return None


def discover(data, record, now):
    """Called only for a finished own-follower scan, never counters/partial lists."""
    scans = [s for s in record['snapshots'] if s.get('complete') is True]
    if not scans:
        return 0
    latest = scans[-1]
    previous = scans[-2] if len(scans) > 1 else None
    created = 0
    for case in data['access'].values():
        if case['id'] in latest['members'] and case['absence']:
            case['absence'] = None
            case['stage'] = 'done'
            case['checks'].append({'status': 'present', 'checkedAt': now,
                                   'reason': 'list_present', 'currentScanId': latest['id']})
            del case['checks'][:-10]
        elif case['absence']:
            case['absence'] = absence_evidence(data, case['id'])
    if not previous:
        return 0
    for identity in set(previous['members']) - set(latest['members']):
        # Automatic diagnostics concern explicitly saved people only.
        if identity not in data['people']:
            continue
        try:
            case = ensure_case(data, previous['members'][identity], now)
        except AppFailure:
            break
        case.update(stage='pending', detectedAt=now, episode=uuid.uuid4().hex, alertRecorded=False, alertStrength=None,
                    absence=absence_evidence(data, identity))
        created += 1
    return created


def parse_visibility(payload, expected_id):
    user = payload.get('user') if isinstance(payload, dict) and payload.get('status') == 'ok' else None
    if not isinstance(user, dict):
        return {'status': 'unknown', 'reason': 'invalid_response'}
    identity, username = user.get('pk'), user.get('username')
    if type(identity) not in (str, int) or str(identity) != expected_id:
        return {'status': 'unknown', 'reason': 'identity'}
    if not isinstance(username, str) or not re.fullmatch(r'[A-Za-z0-9._]{1,30}', username):
        return {'status': 'unknown', 'reason': 'invalid_response'}
    relation = user.get('friendship_status')
    blocking = relation.get('blocking') if isinstance(relation, dict) else None
    # "blocking" means YOU blocked that account. Do not reverse its direction.
    return {'status': 'you_blocked' if blocking is True else 'accessible',
            'reason': 'own_block' if blocking is True else 'profile_visible', 'username': username}


@dataclass(frozen=True)
class ProfileProbe:
    visibility: dict
    profile: dict | None = None


def probe(client, identity):
    try:
        payload = client.private_request(f'users/{identity}/info/',
                                         params={'is_prefetch': 'false', 'from_module': 'profile'})
        visibility = {**parse_visibility(payload, identity), 'targetHTTP': response_status(client)}
        profile = None
        if visibility['status'] == 'accessible' and visibility['targetHTTP'] == 200:
            try:
                profile = parse_profile(payload, identity)
            except AppFailure:
                # A valid visibility result need not contain a complete profile.
                pass
        return ProfileProbe(visibility, profile)
    except Exception as exc:
        error = failure(exc)
        if error['code'] in ('limited', 'cooldown', 'session', 'verification', 'cancelled', 'storage'):
            raise
        status = getattr(getattr(exc, 'response', None), 'status_code', None)
        # A bare "Not Found" is a masked challenge in the pinned client and is
        # raised as ChallengeRequired, never interpreted here as profile absence.
        if isinstance(exc, ClientNotFoundError) and status == 404:
            return ProfileProbe({'status': 'unavailable', 'reason': 'profile_not_found', 'targetHTTP': 404})
        return ProfileProbe({'status': 'unknown', 'reason': 'access_denied' if status == 403 else
                'server_error' if type(status) is int and status >= 500 else 'connection',
                'targetHTTP': status if type(status) is int else response_status(client)})


def response_status(client):
    status = getattr(getattr(client, 'last_response', None), 'status_code', None)
    return status if type(status) is int and 100 <= status <= 599 else None


def last_username(data, identity, member):
    """Resolve the newest observed name for this ID without changing profile counts."""
    candidates = [(0, 0, member['username'])]

    def consider(name, at, priority):
        if isinstance(name, str) and re.fullmatch(r'[A-Za-z0-9._]{1,30}', name):
            at = at if type(at) in (int, float) and math.isfinite(at) and at >= 0 else 0
            candidates.append((at, priority, name))

    consider(member['username'], member.get('observedAt'), 1)
    for row in data.get('history', {}).get(identity, []):
        consider(row.get('username'), row.get('observedAt'), 1)
    for record in data['lists'].values():
        for scan in record.get('snapshots', []) + ([record['draft']] if record.get('draft') else []):
            row = scan['members'].get(identity)
            if row:
                consider(row.get('username'), scan.get('finishedAt'), 0)
    for check in data['access'].get(identity, {}).get('checks', []):
        if check['status'] in ('accessible', 'you_blocked'):
            consider(check.get('username'), check['checkedAt'], 2)
        public = check.get('anonymous', {})
        if public.get('status') == 'visible' and public.get('identityMatched') is True and public.get('observedId') == identity:
            consider(public.get('observedUsername'), public['checkedAt'], 2)
    return max(candidates, key=lambda row: row[:2])[2]


def assess(outcome, absence, anonymous=None):
    result = dict(outcome)
    if anonymous is not None:
        result['anonymous'] = anonymous
        if anonymous['status'] == 'visible':
            result.update(status='suspected' if absence else 'unavailable', reason='anonymous_visible')
        elif anonymous['status'] == 'not_found':
            result.update(status='unavailable', reason='both_unavailable')
        else:
            result.update(status='unknown', reason='anonymous_inconclusive')
        return result
    if outcome['status'] == 'unavailable' and absence:
        result.update(status='suspected', reason='missing_and_unavailable')
    # No assertion about the cause: deletion/deactivation and own blocking can
    # produce the same evidence as being blocked by the target.
    return result


def validate(data):
    cases = data.get('access')
    if not isinstance(cases, dict) or len(cases) > MAX_CASES:
        raise ValueError('access cases')
    if type(data['settings'].get('investigateMissing')) is not bool:
        raise ValueError('access setting')
    plan = data.get('accessPlan')
    if not isinstance(plan, dict) or plan.get('status') not in ('idle', 'pending', 'baseline_needed', 'needs_manual_scan'):
        raise ValueError('access plan')
    for key in ('triggerAt',):
        if key in plan and (type(plan[key]) not in (int, float) or not math.isfinite(plan[key]) or plan[key] < 0):
            raise ValueError('access plan time')
    for identity, case in cases.items():
        if (not isinstance(case, dict) or case.get('id') != identity
                or not re.fullmatch(r'[1-9][0-9]{0,29}', identity)
                or not isinstance(case.get('ownerId'), str) or case['ownerId'] != (data.get('account') or {}).get('id')
                or type(case.get('username')) is not str or not re.fullmatch(r'[A-Za-z0-9._]{1,30}', case['username'])
                or type(case.get('name')) is not str or case.get('stage') not in STAGES
                or type(case.get('alertRecorded')) is not bool or type(case.get('episode')) is not str
                or case.get('alertStrength') not in (None, 'limited', 'strong')
                or type(case.get('detectedAt')) not in (int, float) or not math.isfinite(case['detectedAt'])
                or case['detectedAt'] < 0
                or 'absence' not in case
                or not isinstance(case.get('checks'), list) or len(case['checks']) > 10):
            raise ValueError('access case')
        if case.get('absence') is not None:
            a = case['absence']
            if (not isinstance(a, dict) or a.get('ownerId') != case['ownerId']
                    or any(type(a.get(k)) is not str for k in ('previousScanId', 'currentScanId'))
                    or any(type(a.get(k)) not in (int, float) or not math.isfinite(a[k]) for k in ('previousAt', 'currentAt'))):
                raise ValueError('access absence')
        for check in case['checks']:
            if (not isinstance(check, dict) or check.get('status') not in ASSESSMENTS
                    or type(check.get('checkedAt')) not in (int, float) or not math.isfinite(check['checkedAt']) or check['checkedAt'] < 0):
                raise ValueError('access assessment')
            if 'anonymous' in check:
                anonymous_visibility.validate(check['anonymous'], identity)
                expected = {'anonymous_visible': 'visible', 'both_unavailable': 'not_found', 'anonymous_inconclusive': 'unknown'}
                if (check.get('reason') not in expected or check['anonymous']['status'] != expected[check['reason']]
                        or check.get('ownHTTP') != 200 or check.get('targetHTTP') != 404):
                    raise ValueError('anonymous comparison context')
            for key in ('username', 'reason'):
                if key in check and type(check[key]) is not str:
                    raise ValueError('access assessment field')
            for key in ('targetHTTP', 'ownHTTP'):
                if check.get(key) is not None and (type(check[key]) is not int or not 100 <= check[key] <= 599):
                    raise ValueError('access assessment HTTP')
