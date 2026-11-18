"""Behaviour contract for restructuring the engine; offline, synthetic data only.

A scripted session runs many operations through the real ``Accounts`` worker
object. Every step records the requests sent, the error code, the projected
state, the stored vault and the account catalog. The result must match the
recorded file in ``test-fixtures/refactor-golden.json`` exactly, so moving code
between modules cannot change what the app does without this test failing.

After an intended behaviour change, regenerate the file with
``TRACKER_UPDATE_GOLDEN=1 python -m unittest test_refactor_contract`` and review
the diff before committing it.
"""
import copy
import json
import os
import re
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from accounts import Accounts
from network import AppFailure
from test_backend import FakeClient
from test_scan_progress import ClockWait

GOLDEN = Path(__file__).resolve().parents[3] / 'test-fixtures' / 'refactor-golden.json'
HEX_ID = re.compile(r'(?<![0-9a-f])[0-9a-f]{32}(?![0-9a-f])')
START = 1800000000.0
AVATAR = 'data:image/jpeg;base64,UUE='
# Engine methods that Accounts overrides. Code moved out of Engine must keep
# calling them through the engine object, or the per-account behaviour is lost.
HOOKS = ('save', 'check_connection_identity', 'login_device_settings', 'select_connection_account',
         'limited', 'progress', 'prepare_restore', 'snapshot')


def members(prefix, count):
    return [str(prefix + n) for n in range(count)]


class LoginRequired(Exception):
    """Named like the client error that the engine maps to a rejected session."""


class ClientThrottledError(Exception):
    """Named like the client error that the engine maps to a rate limit."""


class World:
    """Synthetic Instagram answering by endpoint, so request order can change only on purpose."""

    def __init__(self):
        self.users = {'100': 'qa.owner', '200': 'qa.person'}
        self.lists = {('100', 'followers'): members(3000, 55), ('100', 'following'): members(4000, 3),
                      ('200', 'followers'): members(5000, 4), ('200', 'following'): members(6000, 2)}
        self.bio = {'100': 'Only synthetic data', '200': 'Synthetic person'}
        self.failures = {}

    def fail(self, endpoint, kind):
        return lambda: self.failures.__setitem__(endpoint, kind)

    def profile(self, identity):
        return {'user': {'pk': identity, 'username': self.users[identity], 'full_name': 'QA ' + identity,
                         'follower_count': len(self.lists[(identity, 'followers')]),
                         'following_count': len(self.lists[(identity, 'following')]),
                         'media_count': 0, 'biography': self.bio[identity], 'is_private': False,
                         'profile_pic_id': 'pic-' + identity,
                         'profile_pic_url': 'https://scontent.cdninstagram.com/qa-' + identity + '.jpg'}}

    def respond(self, endpoint, params):
        found = re.fullmatch(r'users/([0-9]+)/info/', endpoint)
        if found:
            return self.profile(found.group(1))
        found = re.fullmatch(r'users/([A-Za-z0-9._]+)/usernameinfo/', endpoint)
        if found:
            identity = next(k for k, v in self.users.items() if v == found.group(1))
            return self.profile(identity)
        found = re.fullmatch(r'friendships/([0-9]+)/(followers|following)/', endpoint)
        if found:
            ids = self.lists[(found.group(1), found.group(2))]
            start = int(params.get('max_id') or 0)
            size = int(params.get('count') or 50)
            chunk = ids[start:start + size]
            cursor = str(start + size) if start + size < len(ids) else ''
            return {'status': 'ok', 'next_max_id': cursor,
                    'users': [{'pk': i, 'username': 'qa.' + i, 'full_name': 'QA ' + i} for i in chunk]}
        if endpoint == 'users/search/':
            return {'status': 'ok', 'users': [{'pk': '200', 'username': 'qa.person', 'full_name': 'QA 200'}]}
        if re.fullmatch(r'feed/user/[0-9]+/story/', endpoint):
            return {'status': 'ok', 'reel': None}
        raise AssertionError('Unexpected synthetic endpoint ' + endpoint)


class RouterClient(FakeClient):
    def __init__(self, engine, world, log):
        super().__init__([])
        self.engine, self.world, self.log = engine, world, log

    def login(self, *_):
        return True

    def private_request(self, endpoint, **kwargs):
        self.engine.before_request()
        params = dict(kwargs.get('params') or {})
        self.log.append({'endpoint': endpoint, 'params': params})
        kind = self.world.failures.pop(endpoint, None)
        if kind == 'session':
            self.last_response = SimpleNamespace(status_code=403)
            self.engine.after_response(SimpleNamespace(status_code=403, headers={}))
            raise LoginRequired('synthetic rejected session')
        if kind == 429:
            self.last_response = SimpleNamespace(status_code=429)
            self.engine.after_response(SimpleNamespace(status_code=429, headers={'Retry-After': '90'}))
            raise ClientThrottledError('synthetic rate limit')
        if kind == 'throttle':
            # A limit reported in the body of an HTTP 200 answer, without 429.
            self.last_response = SimpleNamespace(status_code=200)
            self.engine.after_response(SimpleNamespace(status_code=200, headers={}))
            raise ClientThrottledError('synthetic feedback limit')
        value = self.world.respond(endpoint, params)
        self.last_response = SimpleNamespace(status_code=200)
        self.engine.after_response(SimpleNamespace(status_code=200, headers={}))
        return copy.deepcopy(value)


def normalize(value, home):
    text = json.dumps(value, sort_keys=True, ensure_ascii=False, default=str)
    for spelling in {home, home.replace('\\', '/'), str(Path(home).resolve())}:
        text = text.replace(json.dumps(spelling)[1:-1], '<home>')
    ids = {}
    text = HEX_ID.sub(lambda m: ids.setdefault(m.group(0), '<id-%d>' % len(ids)), text)
    return json.loads(text)


def session():
    """Run the scripted session in a fresh directory and return its normalized record."""
    with tempfile.TemporaryDirectory(prefix='TrackerQA-') as home:
        now = [START]
        emitted = []
        app = Accounts(home, emit=emitted.append, clock=lambda: now[0], request_delay=lambda: 7)
        calls = {}
        for hook in HOOKS:
            def counted(*args, _real=getattr(app, hook), _name=hook, **kwargs):
                calls[_name] = calls.get(_name, 0) + 1
                return _real(*args, **kwargs)
            setattr(app, hook, counted)
        app.cancel = ClockWait(now)
        world = World()
        record = []

        def client_factory(_settings):
            return RouterClient(app, world, requests)

        def fake_avatar(profile):
            profile['avatar'] = AVATAR

        def step(name, operation, args=None, *, advance=0, before=None):
            nonlocal requests
            now[0] += advance
            if before:
                before()
            requests = []
            calls.clear()
            del emitted[:]
            args = dict(args or {})
            error = None
            with patch.object(app, 'new_client', side_effect=client_factory), \
                    patch.object(app, 'read_avatar', side_effect=fake_avatar):
                try:
                    state = app.run(operation, args)
                except AppFailure as exc:
                    error = exc.code
                    state = app.snapshot()
            record.append({'step': name, 'operation': operation, 'error': error, 'requests': requests,
                           'hooks': dict(calls), 'events': copy.deepcopy(emitted),
                           'notifications': copy.deepcopy(getattr(app, 'notifications', [])),
                           'state': state, 'vault': copy.deepcopy(app.data),
                           'catalog': copy.deepcopy(app.catalog)})

        requests = []
        backup = str(Path(home) / 'qa.backup')
        export = str(Path(home) / 'qa-followers.csv')

        def change_world():
            followers = world.lists[('100', 'followers')]
            del followers[:2]
            followers.append('3999')
            world.bio['200'] = 'Changed synthetic biography'

        def snapshot_id():
            return app.data['lists']['100:followers']['snapshots'][-1]['id']

        step('login', 'login', {'username': 'qa.owner', 'password': 'SYNTHETIC-PASSWORD'})
        step('settings', 'settings', {'interval': 60})
        step('add person', 'add_person', {'username': 'qa.person'})
        step('scan own followers', 'scan', {'direction': 'followers'}, advance=60)
        step('scan own following', 'scan', {'direction': 'following'}, advance=60)
        step('scan person followers', 'scan', {'direction': 'followers', 'id': '200'}, advance=60)
        step('save person', 'save_person', {'id': '200', 'note': 'QA note', 'group': 'QA', 'favorite': True})
        step('notification rule', 'notification_rule', {'id': '200', 'enabled': True, 'fields': {'bio': False}})
        step('refresh person after change', 'refresh', {'id': '200'}, advance=3600, before=change_world)
        step('refresh own profile', 'refresh', {}, advance=60)
        step('session rejected', 'refresh', {}, advance=60, before=world.fail('users/100/info/', 'session'))
        step('refresh after session', 'refresh', {}, advance=60)
        step('rescan own followers', 'scan', {'direction': 'followers'}, advance=3600)
        step('read all notifications', 'read_all_notifications', {'accountId': '100'})
        step('search people', 'search_people', {'query': 'qa'}, advance=60)
        step('profile detail', 'profile_detail', {'accountId': '100', 'id': '200'}, advance=60)
        step('check access', 'check_access', {'id': '200'}, advance=60)
        step('story fetch', 'story_fetch', {'accountId': '100', 'id': '200'}, advance=60)
        step('story view', 'story_view', {'accountId': '100', 'id': '200'})
        step('wayback', 'wayback', {'id': '200', 'enabled': False})
        step('daily clear', 'daily_clear', {'accountId': '100', 'id': '200'})
        step('export backup', 'export_backup', {'path': backup})
        now[0] += 1
        step('export list', 'export_list', {'accountId': '100', 'id': '100', 'direction': 'followers',
                                            'snapshotId': snapshot_id(), 'path': export})
        record[-1]['file'] = Path(export).read_text(encoding='utf-8-sig')
        step('automatic', 'automatic', {}, advance=7200)
        step('rate limited', 'scan', {'direction': 'following'}, advance=60,
             before=world.fail('friendships/100/following/', 429))
        step('scan while limited', 'scan', {'direction': 'following'}, advance=60)
        step('refresh after cooldown', 'refresh', {}, advance=7200)
        step('limited without 429', 'scan', {'direction': 'following'}, advance=60,
             before=world.fail('friendships/100/following/', 'throttle'))
        step('import backup', 'import_backup', {'path': backup}, advance=60)
        step('unsupported operation', 'no_such_operation', {})
        step('bad input', 'save_person', {'id': '200', 'note': 'x' * 100000})
        step('remove person', 'remove_person', {'id': '200'})
        step('disconnect', 'disconnect', {})
        step('delete all', 'delete_all', {})
        return normalize(record, home)


# Steps whose success is part of the contract. The others record an expected
# refusal; their error code is pinned by the golden file as well.
MUST_SUCCEED = {
    'login', 'settings', 'add person', 'scan own followers', 'scan own following', 'scan person followers',
    'save person', 'notification rule', 'refresh person after change', 'refresh own profile',
    'refresh after session', 'refresh after cooldown', 'rescan own followers', 'read all notifications', 'search people', 'profile detail', 'check access',
    'story fetch', 'story view', 'wayback', 'daily clear', 'export backup', 'export list', 'automatic', 'import backup', 'remove person',
    'disconnect', 'delete all'}


class RefactorContractTests(unittest.TestCase):
    maxDiff = None

    @classmethod
    def setUpClass(cls):
        cls.first = session()

    def test_session_is_deterministic(self):
        # A contract that changes between two identical runs would hide real changes.
        self.assertEqual(session(), self.first)

    def test_expected_steps_succeed(self):
        failed = {s['step']: s['error'] for s in self.first if s['step'] in MUST_SUCCEED and s['error']}
        self.assertEqual(failed, {})
        self.assertEqual(next(s for s in self.first if s['step'] == 'unsupported operation')['error'], 'operation')
        self.assertEqual(next(s for s in self.first if s['step'] == 'bad input')['error'], 'input')
        self.assertEqual(next(s for s in self.first if s['step'] == 'session rejected')['error'], 'session')
        self.assertEqual(next(s for s in self.first if s['step'] == 'rate limited')['error'], 'limited')
        soft = next(s for s in self.first if s['step'] == 'limited without 429')
        self.assertEqual(soft['error'], 'limited')
        self.assertEqual((soft['vault']['gate']['manualOnly'], soft['vault']['gate']['streak']), (True, 2))
        held = next(s for s in self.first if s['step'] == 'scan while limited')
        self.assertEqual((held['error'], held['requests']), ('cooldown', []))

    def test_every_accounts_hook_is_reached(self):
        reached = {hook for step in self.first for hook in step['hooks']}
        self.assertEqual(reached, set(HOOKS))

    def test_scenario_exercises_real_paging_and_changes(self):
        steps = {s['step']: s for s in self.first}
        pages = [r for r in steps['scan own followers']['requests'] if r['endpoint'] == 'friendships/100/followers/']
        self.assertEqual(len(pages), 2)
        self.assertTrue(steps['rescan own followers']['vault']['notificationLog'])
        self.assertIn('3999', steps['export list']['file'])

    def test_matches_recorded_golden_file(self):
        if os.environ.get('TRACKER_UPDATE_GOLDEN') == '1':
            GOLDEN.write_text(json.dumps(self.first, ensure_ascii=False, indent=1, sort_keys=True) + '\n',
                              encoding='utf-8', newline='\n')
        expected = json.loads(GOLDEN.read_text(encoding='utf-8'))
        self.assertEqual(len(self.first), len(expected))
        for actual, recorded in zip(self.first, expected):
            # Compare step by step so a failure names the operation that changed.
            with self.subTest(step=recorded['step']):
                self.assertEqual(actual, recorded)


if __name__ == '__main__':
    unittest.main()
