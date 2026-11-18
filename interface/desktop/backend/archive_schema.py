"""Shape of the stored observation archive: the empty archive and its validation."""
import math
import re

import access_checks
import data_privacy
import list_pages
import list_preparation
import localization
import membership
import notification_log
import profile_about
import profile_links
import read_health
import request_budget
import wayback
from network import avatar_url
from request_defaults import CONTROL_INTERVALS, DEFAULT_INTERVAL_MINUTES

MAX_MEMBERS = 20000

def blank(gate=None):
    result = {'version': 1, 'account': None, 'session': None, 'people': {},
            'history': {}, 'lists': {}, 'events': [], 'gate': gate or {},
            'settings': {'compact': False, 'notifications': False, 'automatic': False, 'interval': DEFAULT_INTERVAL_MINUTES,
                         'investigateMissing': False, 'language': 'tr', 'theme': 'system', 'automaticLists': False},
            'access': {}, 'accessPlan': {'status': 'idle'}, 'lastAutomaticTask': 'profile',
            'notificationRules': {}, 'notificationLog': [],
            'wayback': {'profiles': {}, 'images': {}},
            'automaticAttempts': {}, 'lastInvestigationAt': 0,
            'lastError': None, 'lastAutomatic': 0}
    data_privacy.initialize(result)
    return result


def initialize_schedule(d):
    # Existing observations already carry per-profile successful read times.
    # Preserve the old background timestamp only as the auxiliary job cadence.
    d.setdefault('automaticAttempts', {})
    d.setdefault('lastAutomaticTask', 'profile')
    d.setdefault('lastInvestigationAt', d.get('lastAutomatic', 0))


def validate_archive(d):
    data_privacy.validate(d)
    """Validate the owning representation before it reaches UI or scheduling."""
    if (not isinstance(d, dict) or d.get('version') != 1 or not isinstance(d.get('people'), dict)
            or len(d['people']) > 200 or not isinstance(d.get('lists'), dict)
            or not isinstance(d.get('history'), dict) or not isinstance(d.get('events'), list)
            or len(d['events']) > 2000 or not isinstance(d.get('gate'), dict)
            or not isinstance(d.get('settings'), dict)):
        raise ValueError('schema')
    request_budget.validate_gate(d['gate'])
    list_preparation.validate(d)
    list_pages.validate_exceptions(d)
    read_health.validate(d)
    if d.get('session') is not None and (not isinstance(d['session'], dict) or d.get('account') is None):
        raise ValueError('session')
    def identity(value):
        return type(value) is str and re.fullmatch(r'[1-9][0-9]{0,29}', value)
    def timestamp(value):
        return type(value) in (int, float) and math.isfinite(value) and value >= 0
    def member(p):
        if (not isinstance(p, dict) or not identity(p.get('id'))
                or type(p.get('username')) is not str or not re.fullmatch(r'[A-Za-z0-9._]{1,30}', p['username'])
                or (p.get('name') is not None and type(p['name']) is not str)):
            raise ValueError('identity')
        if p.get('pictureUrl') is not None and not avatar_url(p['pictureUrl']):
            raise ValueError('member picture')
    def list_details(value):
        if not isinstance(value, dict) or not {'membersAdded', 'membersRemoved'} & value.keys():
            return  # Older summaries did not retain identity details.
        if value.get('direction') not in list_preparation.DIRECTIONS or not timestamp(value.get('previousAt')):
            raise ValueError('list difference')
        if type(value.get('ownerUsername')) is not str or not re.fullmatch(r'[A-Za-z0-9._]{1,30}', value['ownerUsername']):
            raise ValueError('list difference owner')
        identities = []
        for key, count in (('membersAdded', 'added'), ('membersRemoved', 'removed')):
            items = value.get(key)
            if (not isinstance(items, list) or len(items) > MAX_MEMBERS or
                    type(value.get(count)) is not int or len(items) != value[count]):
                raise ValueError('list difference members')
            for person in items:
                member(person)
            ids = {person['id'] for person in items}
            if len(ids) != len(items):
                raise ValueError('list difference duplicates')
            identities.append(ids)
        if identities[0] & identities[1]:
            raise ValueError('list difference overlap')
        if 'returnedIds' in value or 'returned' in value:
            back = value.get('returnedIds')
            if (not isinstance(back, list) or type(value.get('returned')) is not int or len(back) != value['returned']
                    or len(set(back)) != len(back) or not set(back) <= identities[0]):
                raise ValueError('list difference returns')
    for p in [d.get('account'), *d['people'].values()]:
        if p is None:
            continue
        member(p)
        if 'links' in p and not profile_links.validate(p['links']):raise ValueError('profile links')
        if 'about' in p and not profile_about.validate(p['about']):raise ValueError('profile about')
        for key in ('followers', 'following', 'posts'):
            if p.get(key) is not None and (type(p[key]) is not int or p[key] < 0):
                raise ValueError('count')
        for key in ('note', 'group', 'bio', 'url', 'picture', 'avatar'):
            if p.get(key) is not None and type(p[key]) is not str:
                raise ValueError('text')
    if any(key != p['id'] for key, p in d['people'].items()):
        raise ValueError('person key')
    for key in ('blockedUntil', 'nextAt', 'loginAfter', 'attempts', 'streak'):
        if key in d['gate'] and not timestamp(d['gate'][key]):
            raise ValueError('gate')
    if type(d['settings'].get('interval')) is not int or d['settings']['interval'] not in CONTROL_INTERVALS:
        raise ValueError('settings')
    for key in ('automatic', 'notifications', 'compact'):
        if type(d['settings'].get(key)) is not bool:
            raise ValueError('settings')
    if d['settings'].get('language') not in localization.LANGUAGES:
        raise ValueError('language')
    if d['settings'].get('theme', 'system') not in ('system', 'light', 'dark', 'oled'):
        raise ValueError('theme')
    for key, record in d['lists'].items():
        owner, separator, direction = key.partition(':')
        if not separator or not identity(owner) or direction not in ('followers', 'following'):
            raise ValueError('list identity')
        if not isinstance(record, dict) or not isinstance(record.get('snapshots'), list) or len(record['snapshots']) > 6:
            raise ValueError('lists')
        retained = record.get('retainedPartial')
        if retained is not None and (not isinstance(retained, dict) or retained.get('complete') is not False or
                retained.get('ownerId') != owner or retained.get('direction') != direction or
                'nextCursor' in retained or 'seenCursors' in retained):
            raise ValueError('retained partial')
        membership.validate(record)
        for snapshot in record['snapshots'] + [s for s in (record.get('draft'),retained) if s is not None]:
            if not isinstance(snapshot, dict) or not isinstance(snapshot.get('members'), dict) or len(snapshot['members']) > MAX_MEMBERS:
                raise ValueError('members')
            if type(snapshot.get('complete')) is not bool:
                raise ValueError('complete')
            for identity_key, p in snapshot['members'].items():
                member(p)
                if identity_key != p['id']:
                    raise ValueError('member key')
    for key, history in d['history'].items():
        if not identity(key) or type(history) is not list or len(history) > 366:
            raise ValueError('history')
        for h in history:
            if not isinstance(h, dict) or not timestamp(h.get('observedAt')):
                raise ValueError('history')
    for event in d['events']:
        if (not isinstance(event, dict) or type(event.get('id')) is not str
                or type(event.get('title')) is not str or not timestamp(event.get('at'))
                or event.get('type') not in ('profile', 'list', 'check', 'error')):
            raise ValueError('event')
        if event['type'] == 'list':
            list_details(event.get('new'))
    if (not timestamp(d.get('lastAutomatic')) or not timestamp(d.get('lastInvestigationAt'))
            or not isinstance(d.get('automaticAttempts'), dict) or len(d['automaticAttempts']) > 201
            or any(not identity(key) or not timestamp(value) for key, value in d['automaticAttempts'].items())):
        raise ValueError('automatic')
    for p in [d.get('account'), *d['people'].values()]:
        if p is not None and p.get('observedAt') is not None and not timestamp(p['observedAt']):
            raise ValueError('profile observation time')
    if d.get('lastAutomaticTask') not in ('profile', 'access', 'followers_scan', 'lists'):
        raise ValueError('automatic task')
    access_checks.validate(d)
    notification_log.validate(d)
    for entry in d['notificationLog']:
        for change in entry['changes']:
            if change['category'] == 'lists':
                list_details(change.get('after'))
    wayback.validate(d)
