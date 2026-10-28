"""Per-identity cadence through the real engine and encrypted vault, offline."""
import copy
import tempfile
import unittest
from unittest.mock import patch

from engine import Engine
from network import AppFailure, parse_profile
from test_backend import FakeClient, profile


class ScheduleTests(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.now = 1800000000
        self.e = Engine(self.home.name, clock=lambda: self.now)
        self.e.record_profile(parse_profile(profile()), own=True)
        for identity in ('200', '300'):
            self.e.record_profile(parse_profile(profile(identity)))
            self.e.save_person({'id': identity, 'watching': True})
        self.e.data['session'] = {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC-SECRET'}}
        self.e.settings({'automatic': True, 'interval': 5})

    def tearDown(self):
        self.home.cleanup()

    def tick(self, offset, identity):
        self.now = 1800000000 + offset
        engine = self.e

        class PacedClient(FakeClient):
            def private_request(self, endpoint, **kwargs):
                engine.before_request()
                return super().private_request(endpoint, **kwargs)

        client = PacedClient([profile(identity)])
        with patch.object(engine, 'client', return_value=client):
            engine.run('automatic', {})
        self.assertEqual([call[0] for call in client.calls], [f'users/{identity}/info/'])

    def test_three_profiles_each_repeat_in_five_minutes_with_global_pacing(self):
        self.now += 299
        with patch.object(self.e, 'client') as client:
            self.e.run('automatic', {}); client.assert_not_called()
        for offset, identity in [(300, '100'), (315, '200'), (330, '300'),
                                 (600, '100'), (615, '200'), (630, '300')]:
            self.tick(offset, identity)
            # No other profile can bypass the global API-start reservation.
            self.assertGreaterEqual(self.e.monitor_status()['nextAt'], self.now + 15)
        for identity in ('100', '200', '300'):
            times = [h['observedAt'] for h in self.e.data['history'][identity]][-2:]
            self.assertEqual(times[1] - times[0], 300)
        self.assertEqual(self.e.data['gate']['attempts'], 6)

    def test_failed_attempt_survives_restart_and_other_due_profiles_continue(self):
        self.now += 300
        with patch.object(self.e, 'refresh', side_effect=AppFailure('connection', 'Synthetic')):
            with self.assertRaises(AppFailure): self.e.run('automatic', {})
        self.e = Engine(self.home.name, clock=lambda: self.now)
        self.tick(315, '200')
        self.tick(330, '300')
        self.assertEqual(self.e.monitor_status()['nextAt'], 1800000600)

    def test_manual_success_reschedules_only_that_profile_and_unwatched_is_excluded(self):
        self.now += 250
        self.e.record_profile(parse_profile(profile('200')))
        self.e.save_person({'id': '300', 'watching': False})
        self.tick(300, '100')
        self.assertEqual(self.e.monitor_status()['nextAt'], 1800000550)
        self.assertEqual(self.e.monitor_status()['profileCount'], 2)

    def test_interval_change_and_sleep_do_not_replay_missed_observations(self):
        self.e.settings({'interval': 1})
        self.assertEqual(self.e.monitor_status()['nextAt'], 1800000060)
        self.tick(3600, '100')
        self.tick(3615, '200')
        self.tick(3630, '300')
        self.assertEqual(self.e.monitor_status()['nextAt'], 1800003660)
        self.assertEqual(len(self.e.data['history']['100']), 2)

    def test_schedule_migration_and_invalid_timestamps_are_rejected(self):
        old = copy.deepcopy(self.e.data)
        old.pop('automaticAttempts'); old.pop('lastInvestigationAt')
        old['lastAutomatic'] = self.now - 100
        self.e.vault.save(old)
        reopened = Engine(self.home.name, clock=lambda: self.now)
        self.assertEqual(reopened.data['lastInvestigationAt'], self.now - 100)
        self.assertEqual(reopened.monitor_status()['nextAt'], self.now + 300)
        for value in (True, -1, '1'):
            bad = copy.deepcopy(reopened.data); bad['automaticAttempts'] = {'100': value}
            reopened.vault.save(bad)
            with self.assertRaises(ValueError): Engine(self.home.name)

    def test_removing_person_removes_reserved_attempt(self):
        self.e.data['automaticAttempts']['200'] = self.now
        self.e.run('remove_person', {'id': '200'})
        self.assertNotIn('200', self.e.data['automaticAttempts'])


if __name__ == '__main__':
    unittest.main()
