"""A reader paused by a changed Instagram response: field names, one alert, value-free state. Offline only."""
import json
import tempfile
import unittest
from unittest.mock import patch

from accounts import Accounts
from network import AppFailure, parse_page, parse_profile
from test_backend import profile, page
from test_scan_progress import ClockWait, PacedClient
import read_health
import worker


class MissingFieldTests(unittest.TestCase):
    def test_list_and_profile_bodies_name_only_expected_fields(self):
        self.assertEqual(read_health.missing('followers', None), ['json'])
        self.assertEqual(read_health.missing('followers', {'status': 'ok'}), ['users'])
        self.assertEqual(read_health.missing('following', {'status': 'fail', 'users': [{'id': '7', 'username': 'qa'}]}),
                         ['status', 'users.pk'])
        self.assertEqual(read_health.missing('followers', {'status': 'ok', 'users': [{'pk': '7'}]}), ['users.username'])
        self.assertEqual(read_health.missing('followers', page(['200'])), [])
        self.assertEqual(read_health.missing('profile', {'status': 'ok'}), ['user'])
        self.assertEqual(read_health.missing('profile', {'user': {'pk': 5, 'username': 'qa', 'edge_followed_by': {'count': 3}}}),
                         ['user.follower_count', 'user.following_count'])
        self.assertEqual(read_health.missing('profile', profile()), [])

    def test_a_rejected_body_stays_on_its_error(self):
        body = {'status': 'ok', 'users': [{'id': '7', 'username': 'qa'}]}
        with self.assertRaises(AppFailure) as caught:
            parse_page(body)
        self.assertIs(caught.exception.payload, body)
        with self.assertRaises(AppFailure) as caught:
            parse_profile({'status': 'ok'})
        self.assertEqual(caught.exception.payload, {'status': 'ok'})
        self.assertEqual(parse_page.__name__, 'parse_page')

    def test_validation_accepts_known_fields_only_for_a_format_reason(self):
        entry = {'reason': 'format', 'http': 200, 'at': 1.0, 'streak': 1, 'paused': False}
        read_health.validate({'readHealth': {'followers': entry}})
        read_health.validate({'readHealth': {'followers': {**entry, 'missing': ['users.pk']}}})
        for bad in ({**entry, 'missing': []}, {**entry, 'missing': ['users.pk', 'users.pk']},
                    {**entry, 'missing': ['cookie']}, {**entry, 'missing': 'users'},
                    {**entry, 'reason': 'route', 'missing': ['users']}):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                read_health.validate({'readHealth': {'followers': bad}})


class ReadAlertTests(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.addCleanup(self.home.cleanup)
        self.now = [1800000000.0]
        self.e = Accounts(self.home.name, clock=lambda: self.now[0], request_delay=lambda: 15)
        self.e.cancel = ClockWait(self.now)
        client = PacedClient(self.e, [profile(followers=3, following=1)])
        client.login = lambda *_: True
        with patch.object(self.e, 'new_client', return_value=client):
            self.e.run('login', {'username': 'qa.owner', 'password': 'SYNTHETIC'})
        self.e.run('settings', {'interval': 60, 'notifications': True})

    def fail_scan(self, body):
        self.now[0] = max(self.now[0], self.e.data['gate'].get('nextAt', 0))
        own = profile(followers=3, following=1)
        with patch.object(self.e, 'client', return_value=PacedClient(self.e, [own, body])), self.assertRaises(AppFailure):
            self.e.run('scan', {'id': '100', 'direction': 'followers'})
        return worker.read_alerts(self.e)

    def test_third_changed_response_pauses_once_and_names_the_missing_field(self):
        changed = {'status': 'ok', 'users': [{'id': '7', 'username': 'qa.member'}], 'next_max_id': 'QA-CURSOR'}
        self.assertEqual(self.fail_scan(changed), [])
        self.assertEqual(self.fail_scan(changed), [])
        self.assertEqual(self.fail_scan(changed), [{'reader': 'followers', 'reason': 'format',
                                                    'ownerId': '100', 'ownerUsername': 'qa.owner'}])
        diagnosis = self.e.snapshot()['readHealth']['followers']
        self.assertEqual((diagnosis['reason'], diagnosis['missing'], diagnosis['paused']), ('format', ['users.pk'], True))
        # A paused reader is not diagnosed again, so the alert is not repeated.
        self.assertEqual(self.fail_scan(changed), [])
        text = json.dumps(self.e.snapshot()['readHealth'])
        for value in ('qa.member', 'QA-CURSOR', '"7"'):
            self.assertNotIn(value, text)
        # The stored entry survives a reload of the encrypted vault.
        reloaded = Accounts(self.home.name, clock=lambda: self.now[0], request_delay=lambda: 15)
        self.assertEqual(reloaded.snapshot()['readHealth']['followers']['missing'], ['users.pk'])

    def test_route_pause_has_no_field_list_and_no_alert_when_notifications_are_off(self):
        self.e.run('settings', {'notifications': False})
        for _ in range(3):
            alerts = self.fail_scan(AppFailure('connection', 'Synthetic 405', http=405))
        self.assertEqual(alerts, [])
        diagnosis = self.e.snapshot()['readHealth']['followers']
        self.assertEqual((diagnosis['reason'], diagnosis['paused']), ('route', True))
        self.assertNotIn('missing', diagnosis)

    def test_alerts_are_cleared_by_the_next_operation(self):
        changed = {'status': 'ok'}
        for _ in range(3):
            alerts = self.fail_scan(changed)
        self.assertEqual(len(alerts), 1)
        self.e.run('state', {})
        self.assertEqual(worker.read_alerts(self.e), [])


if __name__ == '__main__':
    unittest.main()
