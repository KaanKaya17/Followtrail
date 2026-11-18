"""Engine paths that had no test before the restructuring; offline, synthetic data only."""
import copy
import json
import os
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from engine import Engine, blank, validate_archive
from network import AppFailure, parse_profile
from storage import protect
from test_backend import FakeClient, page, profile


def person(identity, username=None):
    return {'id': identity, 'username': username or 'qa.' + identity, 'name': 'QA ' + identity}


def list_change(**changes):
    value = {'direction': 'followers', 'previousAt': 1799990000, 'ownerUsername': 'qa.owner',
             'membersAdded': [person('301')], 'added': 1, 'membersRemoved': [person('302')], 'removed': 1}
    value.update(changes)
    return value


def archive_with(change):
    d = blank()
    d['events'] = [{'id': 'e1', 'title': 'Liste', 'at': 1800000000, 'type': 'list', 'new': change}]
    return d


class EngineGapTests(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.addCleanup(self.home.cleanup)
        self.engine = Engine(self.home.name, clock=lambda: 1800000000)

    def connected(self):
        e = self.engine
        e.record_profile(parse_profile(profile()), own=True)
        e.record_profile(parse_profile(profile('200')))
        e.data['session'] = {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC-SECRET'}}
        e.save()
        return e

    # Profile "about" details: saved, cleared, validated, and rolled back on a failed save.
    def test_profile_about_saves_clears_and_validates(self):
        e = self.connected()
        about = {'joined': '2015-04', 'country': 'Türkiye'}
        e.run('save_profile_about', {'accountId': '100', 'id': '200', 'about': about})
        self.assertEqual(e.data['people']['200']['about'], about)
        self.assertEqual(Engine(self.home.name, clock=lambda: 1800000000).data['people']['200']['about'], about)
        e.run('save_profile_about', {'accountId': '100', 'id': '100', 'about': {'country': 'QA'}})
        self.assertEqual(e.data['account']['about'], {'country': 'QA'})
        e.run('save_profile_about', {'accountId': '100', 'id': '200', 'about': {}})
        self.assertNotIn('about', e.data['people']['200'])
        refused = [
            {'accountId': '999', 'id': '200', 'about': about},
            {'accountId': '100', 'id': 200, 'about': about},
            {'accountId': '100', 'id': '404', 'about': about},
            {'accountId': '100', 'id': '200', 'about': about, 'extra': True},
            {'accountId': '100', 'id': '200', 'about': {'joined': '2009-01'}},
            {'accountId': '100', 'id': '200', 'about': {'joined': '2999-01'}},
            {'accountId': '100', 'id': '200', 'about': {'unknown': 'x'}},
        ]
        for args in refused:
            with self.subTest(args=args), self.assertRaises(AppFailure) as caught:
                e.run('save_profile_about', args)
            self.assertEqual(caught.exception.code, 'input')
        self.assertNotIn('about', e.data['people']['200'])

    def test_profile_about_restores_previous_value_when_save_fails(self):
        e = self.connected()
        e.run('save_profile_about', {'accountId': '100', 'id': '200', 'about': {'country': 'Old'}})
        with patch.object(e, 'save', side_effect=OSError('synthetic disk failure')), self.assertRaises(OSError):
            e.save_profile_about({'accountId': '100', 'id': '200', 'about': {'country': 'New'}})
        self.assertEqual(e.data['people']['200']['about'], {'country': 'Old'})
        with patch.object(e, 'save', side_effect=OSError('synthetic disk failure')), self.assertRaises(OSError):
            e.save_profile_about({'accountId': '100', 'id': '200', 'about': {}})
        self.assertEqual(e.data['people']['200']['about'], {'country': 'Old'})

    # Session import from the earlier Windows prototype.
    def legacy_session(self, payload):
        folder = Path(self.home.name) / 'local' / 'InstagramCounter'
        folder.mkdir(parents=True, exist_ok=True)
        (folder / 'session.dat').write_bytes(protect(json.dumps(payload).encode('utf-8')))
        return {'LOCALAPPDATA': str(Path(self.home.name) / 'local')}

    def test_legacy_session_import_accepts_only_a_matching_version_one_file(self):
        e = self.engine
        with patch.dict(os.environ, {'LOCALAPPDATA': str(Path(self.home.name) / 'missing')}), \
                self.assertRaises(AppFailure) as caught:
            e.run('import_counter', {})
        self.assertEqual(caught.exception.code, 'import')
        with patch.dict(os.environ, self.legacy_session({'version': 2, 'settings': {}, 'account_id': '100'})), \
                self.assertRaises(AppFailure) as caught:
            e.run('import_counter', {})
        self.assertEqual(caught.exception.code, 'import')
        settings = {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC-SECRET'}}
        with patch.dict(os.environ, self.legacy_session({'version': 1, 'settings': settings, 'account_id': '555'})), \
                patch.object(e, 'new_client', return_value=FakeClient([])), self.assertRaises(AppFailure) as caught:
            e.run('import_counter', {})
        self.assertEqual(caught.exception.code, 'identity')
        self.assertIsNone(e.data['session'])
        client = FakeClient([profile(followers=7)])
        with patch.dict(os.environ, self.legacy_session({'version': 1, 'settings': settings, 'account_id': '100'})), \
                patch.object(e, 'new_client', return_value=client) as factory, patch.object(e, 'read_avatar'):
            e.run('import_counter', {})
        factory.assert_called_once_with(settings)
        self.assertEqual(e.data['account']['id'], '100')
        self.assertEqual(e.data['account']['followers'], 7)
        self.assertIsNotNone(e.data['session'])

    # Authenticated client creation.
    def test_client_requires_session_and_closes_a_mismatched_identity(self):
        e = self.engine
        with self.assertRaises(AppFailure) as caught:
            e.client()
        self.assertEqual(caught.exception.code, 'session')
        self.connected()
        wrong = Mock(user_id=999)
        with patch.object(e, 'new_client', return_value=wrong), patch.object(e, 'close_client') as close, \
                self.assertRaises(AppFailure) as caught:
            e.client()
        self.assertEqual(caught.exception.code, 'identity')
        close.assert_called_once_with(wrong)
        right = Mock(user_id=100)
        with patch.object(e, 'new_client', return_value=right) as factory, patch.object(e, 'close_client') as close:
            self.assertIs(e.client(), right)
        factory.assert_called_once_with(e.data['session'])
        close.assert_not_called()

    # Login error projection: HTTP status turns a connection error into server/response.
    def test_login_connection_errors_name_the_http_class_and_stage(self):
        e = self.engine
        for http, code in ((503, 'server'), (400, 'response')):
            def failing(_args, _http=http):
                e.login_stage = 'authentication'
                e.login_http = _http
                e.login_request = 2
                raise ConnectionError('synthetic connection failure')
            with self.subTest(http=http), patch.object(e, 'connect', side_effect=failing), \
                    self.assertRaises(AppFailure) as caught:
                e.run('login', {'username': 'qa.owner', 'password': 'SYNTHETIC'})
            self.assertEqual(caught.exception.code, code)
            self.assertEqual(e.data['lastLoginError']['code'], code)
            self.assertEqual(e.data['lastLoginError']['http'], http)
            self.assertEqual(e.data['lastLoginError']['loginRequest'], 2)
            self.assertEqual(e.data['lastLoginError']['loginStage'], 'authentication')
            self.assertIsNone(e.login_stage)
            self.assertIsNone(e.login_http)
            self.assertEqual(e.login_request, 0)

    # Two-step verification code hand-off between the worker thread and the UI.
    def test_verification_code_is_accepted_once_while_waiting(self):
        e = self.engine
        with self.assertRaises(AppFailure) as caught:
            e.submit_code('123456')
        self.assertEqual(caught.exception.code, 'code')
        result = {}
        waiter = threading.Thread(target=lambda: result.setdefault('code', e.request_code()))
        waiter.start()
        deadline = time.monotonic() + 5
        while not e.waiting_code and time.monotonic() < deadline:
            time.sleep(.01)
        self.assertTrue(e.waiting_code)
        for bad in ('12345', 'abcdef', 123456, '1' * 11):
            with self.subTest(code=bad), self.assertRaises(AppFailure):
                e.submit_code(bad)
        e.submit_code('654321')
        waiter.join(5)
        self.assertEqual(result['code'], '654321')
        self.assertFalse(e.waiting_code)

    def test_verification_wait_stops_when_cancelled(self):
        e = self.engine
        e.cancel.set()
        with self.assertRaises(AppFailure) as caught:
            e.request_code()
        self.assertEqual(caught.exception.code, 'cancelled')
        self.assertFalse(e.waiting_code)

    def test_stale_codes_are_dropped_before_each_operation(self):
        e = self.engine
        e.codes.put_nowait('111111')
        e.run('state', {})
        self.assertTrue(e.codes.empty())

    # A list keeps only its six newest complete snapshots, and the vault still opens.
    def test_list_keeps_six_newest_snapshots(self):
        e = self.connected()
        now = [1800000000]
        e.clock = lambda: now[0]
        finished = []
        for n in range(8):
            now[0] += 86400
            client = FakeClient([profile(followers=2), page(['301', str(400 + n)])])
            with patch.object(e, 'client', return_value=client):
                e.run('scan', {'direction': 'followers'})
            finished.append(now[0])
        snapshots = e.data['lists']['100:followers']['snapshots']
        self.assertEqual([s['finishedAt'] for s in snapshots], finished[-6:])
        reopened = Engine(self.home.name, clock=lambda: now[0])
        self.assertEqual(len(reopened.data['lists']['100:followers']['snapshots']), 6)

    # Backup import size limit.
    def test_oversized_backup_is_refused_before_reading(self):
        e = self.connected()
        target = Path(self.home.name) / 'big.backup'
        target.write_bytes(b'x' * 64)
        with patch.object(e, 'max_vault', 32), patch.object(e, 'protect') as decrypt, \
                self.assertRaises(AppFailure) as caught:
            e.run('import_backup', {'path': str(target)})
        self.assertEqual(caught.exception.code, 'backup')
        decrypt.assert_not_called()

    # Archive validation of stored list differences.
    def test_valid_list_difference_is_accepted(self):
        validate_archive(archive_with(list_change()))
        validate_archive(archive_with(list_change(returnedIds=['301'], returned=1)))
        validate_archive(archive_with({'summary': 'older event without identities'}))

    def test_invalid_list_differences_are_refused(self):
        cases = {
            'direction': list_change(direction='sideways'),
            'previous time': list_change(previousAt='yesterday'),
            'owner': list_change(ownerUsername='not valid!'),
            'member list type': list_change(membersAdded='301'),
            'count mismatch': list_change(added=2),
            'member identity': list_change(membersAdded=[person('0x1')]),
            'member username': list_change(membersAdded=[person('301', 'bad name!')]),
            'member name type': list_change(membersAdded=[{**person('301'), 'name': 5}]),
            'member picture': list_change(membersAdded=[{**person('301'), 'pictureUrl': 'https://example.com/a.jpg'}]),
            'duplicates': list_change(membersAdded=[person('301'), person('301')], added=2),
            'overlap': list_change(membersRemoved=[person('301')]),
            'returns not added': list_change(returnedIds=['999'], returned=1),
            'returns count': list_change(returnedIds=['301'], returned=2),
            'returns duplicate': list_change(membersAdded=[person('301'), person('303')], added=2,
                                             returnedIds=['301', '301'], returned=2),
            'returns type': list_change(returnedIds='301', returned=1),
        }
        for name, change in cases.items():
            with self.subTest(case=name), self.assertRaises(ValueError):
                validate_archive(archive_with(change))

    def test_invalid_archive_shapes_are_refused(self):
        base = blank()
        cases = {
            'version': lambda d: d.update(version=2),
            'session without account': lambda d: d.update(session={'cookies': {}}),
            'session type': lambda d: d.update(session='x'),
            'people key': lambda d: d['people'].update({'201': {**person('200')}}),
            'count': lambda d: d['people'].update({'200': {**person('200'), 'followers': -1}}),
            'text': lambda d: d['people'].update({'200': {**person('200'), 'note': 5}}),
            'settings interval': lambda d: d['settings'].update(interval=7),
            'language': lambda d: d['settings'].update(language='xx'),
            'theme': lambda d: d['settings'].update(theme='neon'),
            'list key': lambda d: d['lists'].update({'200:likes': {'snapshots': []}}),
            'history': lambda d: d['history'].update({'200': [{'observedAt': 'x'}]}),
            'event type': lambda d: d['events'].append({'id': 'e', 'title': 't', 'at': 1, 'type': 'other'}),
            'automatic task': lambda d: d.update(lastAutomaticTask='unknown'),
        }
        for name, change in cases.items():
            d = copy.deepcopy(base)
            change(d)
            with self.subTest(case=name), self.assertRaises(ValueError):
                validate_archive(d)


if __name__ == '__main__':
    unittest.main()
