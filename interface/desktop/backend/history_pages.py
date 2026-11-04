"""Immutable published history for local, account-scoped paging. No network or writes."""
import uuid
from network import AppFailure


def event_kind(event):
    return 'access' if event.get('topic') == 'access' else event['type']


def log_kind(change):
    return change['category'] if change['category'] in ('lists', 'access') else 'profile'


def preview_value(value):
    if not isinstance(value, dict) or not {'membersAdded', 'membersRemoved'} & value.keys():
        return value
    return {**value, **{key: value[key][:2] for key in ('membersAdded', 'membersRemoved') if key in value}}


def preview_event(event):
    return {**event, 'old': preview_value(event.get('old')), 'new': preview_value(event.get('new'))}


def preview_log(entry):
    return {**entry, 'changes': [{**c, 'before': preview_value(c['before']), 'after': preview_value(c['after'])} for c in entry['changes']]}


class HistoryPages:
    def __init__(self):
        self.signature = self.view = self.sources = None

    def publish(self, state):
        events, logs = state['events'], state['notificationLog']
        account = (state['account'] or {}).get('id') or ''
        signature = (account, id(events), tuple(e['id'] for e in events), id(logs),
                     tuple((e['id'], e['readAt'], len(e['changes'])) for e in logs))
        if signature != self.signature:
            # Events and individual changes are immutable after production.
            # Freeze mutable log containers/read markers at publication, so a
            # concurrent read never observes half of the next observation.
            frozen_logs = tuple({**e, 'changes': tuple(e['changes']),
                                 'selectedChangeIds': tuple(e['selectedChangeIds'])} for e in logs)
            self.view = dict(accountId=account, revision=uuid.uuid4().hex,
                             events=tuple(events), notifications=frozen_logs)
            self.sources = (events, logs)  # Keep signature identities alive.
            self.signature = signature
            people = {}
            for e in events:
                if e.get('personId') and e['personId'] not in people:
                    value = e.get('new') or {}
                    people[e['personId']] = value.get('ownerUsername') or value.get('username')
            counts = {read: {kind: 0 for kind in ('all', 'profile', 'lists', 'access')}
                      for read in ('all', 'unread')}
            for e in frozen_logs:
                for read in ('all', 'unread') if e['readAt'] is None else ('all',):
                    counts[read]['all'] += 1
                    for kind in {log_kind(c) for c in e['changes']}:
                        counts[read][kind] += 1
            self.meta = dict(revision=self.view['revision'], events=len(events),
                             profileCount=sum(e['type'] == 'profile' for e in events),
                             people=people, general=any(e.get('personId') is None for e in events),
                             notificationCounts=counts)
        return {**state, 'events': [preview_event(e) for e in self.view['events'][:5]], 'notificationLog': [], 'historyMeta': self.meta}

    def checked(self, args, fields):
        if not isinstance(args, dict) or set(args) != fields:
            raise AppFailure('input', 'Geçersiz geçmiş isteği.')
        if any(type(args[k]) is not str for k in fields - {'offset'}):
            raise AppFailure('input', 'Geçersiz geçmiş isteği.')
        if not self.view or args['accountId'] != self.view['accountId'] or args['revision'] != self.view['revision']:
            raise AppFailure('history_view', 'Geçmiş güncellendi. Güncel kayıt yükleniyor.')
        return self.view

    def page(self, args):
        view = self.checked(args, {'accountId', 'revision', 'kind', 'type', 'personId', 'section', 'read', 'offset'})
        if (type(args['offset']) is not int or not 0 <= args['offset'] <= 2000 or
                args['kind'] not in ('events', 'notifications') or
                args['type'] not in ('all', 'profile', 'access', 'list', 'lists', 'check', 'error') or
                args['section'] not in ('all', 'changes', 'checks') or args['read'] not in ('all', 'unread')):
            raise AppFailure('input', 'Geçersiz geçmiş isteği.')
        if args['kind'] == 'events':
            def matches(e):
                routine = e['type'] == 'check' and e.get('topic') != 'access'
                return ((not args['personId'] or (e.get('personId') is None if args['personId'] == 'general' else e.get('personId') == args['personId'])) and
                        (args['type'] == 'all' or (e['type'] == 'check' if args['type'] == 'check' else event_kind(e) == args['type'])) and
                        (args['section'] == 'all' or routine == (args['section'] == 'checks')))
            rows = [e for e in view['events'] if matches(e)]
        else:
            rows = [e for e in view['notifications'] if (args['read'] == 'all' or e['readAt'] is None) and
                    (args['type'] == 'all' or any(log_kind(c) == args['type'] for c in e['changes']))]
        offset = args['offset']
        items = rows[offset:offset+100]
        return dict(accountId=view['accountId'], revision=view['revision'], offset=offset,
                    items=[preview_event(e) if args['kind'] == 'events' else preview_log(e) for e in items],
                    total=len(rows), nextOffset=offset+len(items) if offset+len(items) < len(rows) else None)

    def detail(self, args):
        view = self.checked(args, {'accountId', 'revision', 'kind', 'id'})
        if args['kind'] == 'event':
            return next((e for e in view['events'] if e['id'] == args['id']), None)
        if args['kind'] == 'notification':
            return next((e for e in view['notifications'] if e['id'] == args['id']), None)
        if args['kind'] == 'change':
            event = next((e for e in view['events'] if e['id'] == args['id'] and e['type'] == 'list'), None)
            if event:
                return dict(after=event['new'], personId=event.get('personId'), at=event['at'])
            return next((c for e in view['notifications'] for c in e['changes'] if c['eventId'] == args['id'] and c['category'] == 'lists'), None)
        raise AppFailure('input', 'Geçersiz geçmiş isteği.')
