"""Owner of per-account preferences and one persistent log per observation."""
import copy
import math
import re

from network import AppFailure

TYPES = {'username': 'Kullanıcı adı', 'name': 'Görünen ad', 'bio': 'Biyografi',
         'picture': 'Profil fotoğrafı', 'followers': 'Takipçi sayısı', 'following': 'Takip edilen sayısı',
         'posts': 'Gönderi sayısı', 'url': 'Profil bağlantısı', 'private': 'Hesap gizliliği',
         'verified': 'Doğrulanma durumu', 'lists': 'Liste taraması', 'access': 'Engellenme şüphesi'}
MAX_LOGS = 500


def initialize(data):
    data.setdefault('notificationRules', {})
    data.setdefault('notificationLog', [])


def rule(data, identity):
    selected = data['notificationRules'].get(identity, {})
    return {'enabled': selected.get('enabled', True),
            'fields': {key: selected.get('fields', {}).get(key, True) for key in TYPES}}


def set_rule(data, identity, args):
    if type(args.get('enabled')) is not bool or not isinstance(args.get('fields'), dict):
        raise AppFailure('input', 'Bildirim tercihleri geçersiz.')
    fields = args['fields']
    if any(key not in TYPES or type(value) is not bool for key, value in fields.items()):
        raise AppFailure('input', 'Bilinmeyen bildirim türü veya geçersiz tercih.')
    # Canonical complete contract; omitted checkboxes retain their current values.
    current = rule(data, identity)
    current['fields'].update(fields)
    current['enabled'] = args['enabled']
    data['notificationRules'][identity] = current


def category(event):
    if event.get('topic') == 'access':
        return 'access' if event.get('new', {}).get('status') == 'suspected' and event['type'] == 'profile' else None
    if event['type'] == 'list':
        return 'lists'
    if event['type'] == 'profile' and isinstance(event.get('new'), dict) and len(event['new']) == 1:
        key = next(iter(event['new']))
        return key if key in TYPES and key not in ('lists', 'access') else None
    return None


def record(data, event, group_id):
    kind = category(event)
    if not kind:
        return
    identity = event.get('personId')
    if type(identity) is not str or not re.fullmatch(r'[1-9][0-9]{0,29}', identity):
        return
    own = data.get('account')
    person = own if own and own['id'] == identity else data['people'].get(identity)
    if not person:
        person = data.get('access', {}).get(identity, {})
    # Freeze display identity and old/new evidence at observation time.
    username = person.get('username') or identity
    change = {'eventId': event['id'], 'personId': identity, 'username': username,
              'category': kind, 'label': TYPES[kind], 'title': event['title'], 'at': event['at'],
              'before': copy.deepcopy(event.get('old')), 'after': copy.deepcopy(event.get('new'))}
    logs = data['notificationLog']
    entry = next((item for item in logs if item['id'] == group_id), None)
    if entry is None:
        entry = {'id': group_id, 'at': event['at'], 'readAt': None, 'changes': [],
                 'selectedChangeIds': [], 'desktopEnabledAtCheck': data['settings']['notifications'],
                 'desktopEligible': False}
        logs.insert(0, entry)
        del logs[MAX_LOGS:]
    if any(item['eventId'] == event['id'] for item in entry['changes']):
        return
    entry['changes'].append(change)
    selected = rule(data, identity)
    if selected['enabled'] and selected['fields'][kind]:
        entry['selectedChangeIds'].append(event['id'])
    entry['desktopEligible'] = entry['desktopEnabledAtCheck'] and bool(entry['selectedChangeIds'])


def mark_read(data, identity, now):
    entry = next((item for item in data['notificationLog'] if item['id'] == identity), None)
    if entry is None:
        raise AppFailure('input', 'Bildirim kaydı artık saklanan geçmişte yok.')
    if entry['readAt'] is None:
        entry['readAt'] = now


def validate(data):
    def identity(value):
        return type(value) is str and re.fullmatch(r'[1-9][0-9]{0,29}', value)
    def timestamp(value):
        return type(value) in (int, float) and math.isfinite(value) and value >= 0
    rules, logs = data.get('notificationRules'), data.get('notificationLog')
    if not isinstance(rules, dict) or not isinstance(logs, list) or len(logs) > MAX_LOGS:
        raise ValueError('notification schema')
    for key, value in rules.items():
        if (not identity(key) or not isinstance(value, dict) or type(value.get('enabled')) is not bool
                or not isinstance(value.get('fields'), dict) or set(value['fields']) != set(TYPES)
                or any(type(item) is not bool for item in value['fields'].values())):
            raise ValueError('notification rule')
    seen = set()
    for entry in logs:
        if (not isinstance(entry, dict) or type(entry.get('id')) is not str or not entry['id'] or entry['id'] in seen
                or not timestamp(entry.get('at')) or (entry.get('readAt') is not None and not timestamp(entry['readAt']))
                or not isinstance(entry.get('changes'), list) or not entry['changes']
                or not isinstance(entry.get('selectedChangeIds'), list)
                or type(entry.get('desktopEnabledAtCheck')) is not bool or type(entry.get('desktopEligible')) is not bool):
            raise ValueError('notification entry')
        seen.add(entry['id'])
        event_ids = set()
        for change in entry['changes']:
            if (not isinstance(change, dict) or type(change.get('eventId')) is not str
                    or change['eventId'] in event_ids or not identity(change.get('personId'))
                    or type(change.get('username')) is not str or change.get('category') not in TYPES
                    or type(change.get('label')) is not str or type(change.get('title')) is not str
                    or not timestamp(change.get('at'))):
                raise ValueError('notification change')
            event_ids.add(change['eventId'])
        if (any(type(key) is not str or key not in event_ids for key in entry['selectedChangeIds'])
                or len(set(entry['selectedChangeIds'])) != len(entry['selectedChangeIds'])
                or entry['desktopEligible'] != (entry['desktopEnabledAtCheck'] and bool(entry['selectedChangeIds']))):
            raise ValueError('notification selection')
