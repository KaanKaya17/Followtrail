"""Request reuse and headroom through production operations; no real HTTP."""
import tempfile
import unittest
from unittest.mock import patch
from engine import Engine
from network import AppFailure, parse_profile
from test_backend import profile, page
from test_scan_progress import ClockWait, PacedClient
import request_budget


class EfficiencyTests(unittest.TestCase):
    def test_full_device_day_is_retained_and_blocks_before_an_extra_reservation(self):
        now = self.now[0]
        count = request_budget.SHARED[1]
        times = [now - 7200] * count
        gate = self.engine.data['gate']
        gate.update(requestTimes=times.copy(), accountRequestTimes={
            '100':times[:count // 2], '200':times[count // 2:]})
        request_budget.validate_gate(gate)
        with self.assertRaises(AppFailure) as failure: self.engine.before_request()
        self.assertEqual(failure.exception.code, 'budget')
        self.assertEqual(len(gate['requestTimes']), count)
        self.assertEqual(sum(map(len, gate['accountRequestTimes'].values())), count)
        self.assertEqual(request_budget.next_at(gate, '100', now), now + 79200)

    def setUp(self):
        self.home = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.addCleanup(self.home.cleanup)
        self.now = [1800000000.0]
        self.engine = Engine(self.home.name, clock=lambda: self.now[0], request_delay=lambda: 8)
        self.engine.cancel = ClockWait(self.now)
        self.engine.record_profile(parse_profile(profile()), own=True)
        self.engine.data['session'] = {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC-SECRET'}}

    def test_automatic_starts_share_recent_profile_without_fabricating_observations(self):
        client = PacedClient(self.engine, [profile(), page(['200', '201']), page(['202'])])
        with patch.object(self.engine, 'client', return_value=client), patch.object(self.engine, 'read_avatar'):
            self.engine.refresh({})
            observed = self.engine.data['account']['observedAt']
            checks = len([e for e in self.engine.data['events'] if e['type'] == 'check'])
            for direction in ('followers', 'following'):
                self.engine.scan({'direction': direction}, reuse_profile=True)
        self.assertEqual(len(client.calls), 3)
        self.assertEqual([e for e, _ in client.calls].count('users/100/info/'), 1)
        self.assertEqual(self.engine.data['account']['observedAt'], observed)
        self.assertEqual(len([e for e in self.engine.data['events'] if e['type'] == 'check']), checks)
        self.assertTrue(all(r['snapshots'][-1]['complete'] for r in self.engine.data['lists'].values()))

    def test_expiry_connection_change_and_explicit_scan_require_new_read(self):
        for reason in ('expiry', 'connection', 'manual'):
            with self.subTest(reason=reason):
                self.engine.data['session']['cookies']['sessionid'] = 'SYNTHETIC-SECRET'
                self.engine.record_profile(parse_profile(profile()), own=True)
                if reason == 'expiry': self.now[0] += 60
                if reason == 'connection': self.engine.data['session']['cookies']['sessionid'] = 'OTHER-SYNTHETIC'
                client = PacedClient(self.engine, [profile(), page(['200', '201'])])
                with patch.object(self.engine, 'client', return_value=client):
                    self.engine.scan({'direction': 'followers'}, reuse_profile=reason != 'manual')
                self.assertEqual([e for e, _ in client.calls], ['users/100/info/', 'friendships/100/followers/'])

    def test_cached_basis_cannot_bypass_cooldown_or_replay_429(self):
        self.engine.record_profile(parse_profile(profile()), own=True)
        self.engine.data['gate']['blockedUntil'] = self.now[0] + 60
        client = PacedClient(self.engine, [])
        with patch.object(self.engine, 'client', return_value=client):
            with self.assertRaises(AppFailure): self.engine.scan({'direction':'followers'}, reuse_profile=True)
        self.assertEqual(client.calls, [])
        self.engine.data['gate']['blockedUntil'] = 0
        client = PacedClient(self.engine, [429])
        with patch.object(self.engine, 'client', return_value=client):
            with self.assertRaises(AppFailure): self.engine.scan({'direction':'followers'}, reuse_profile=True)
        self.assertEqual(len(client.calls), 1)
        self.assertGreater(self.engine.data['gate']['blockedUntil'], self.now[0])

    def test_profile_headroom_preserves_explicit_and_list_capacity(self):
        now = self.now[0]
        times = [now] * request_budget.PROFILE_ACCOUNT[0]
        gate = self.engine.data['gate']
        gate.update(requestTimes=times.copy(), accountRequestTimes={'100':times.copy()})
        self.assertEqual(request_budget.next_at(gate, '100', now), 0)
        self.assertEqual(request_budget.profile_next_at(gate, '100', now), now + 3600)
        self.assertEqual(request_budget.profile_next_at(gate, '200', now), 0)
        self.engine.data['settings'].update(automatic=True, automaticLists=False)
        self.assertEqual(self.engine.monitor_status()['nextAt'], now + 3600)
        self.engine.data['settings']['automaticLists'] = True
        self.assertLessEqual(self.engine.monitor_status()['nextAt'], now)
        client = PacedClient(self.engine, [profile(), page(['200', '201'])])
        with patch.object(self.engine, 'client', return_value=client):
            self.engine.automatic()
        self.assertEqual(len(client.calls), 2)
        self.assertEqual(len(gate['accountRequestTimes']['100']), len(times) + 2)

    def test_daily_and_device_headroom_and_clock_rollback_keep_reservations(self):
        now = self.now[0]
        daily = [now - 7200] * request_budget.PROFILE_ACCOUNT[1]
        gate = {'requestTimes':daily, 'accountRequestTimes':{'100':daily}}
        self.assertEqual(request_budget.profile_next_at(gate, '100', now), now + 79200)
        self.assertEqual(request_budget.next_at(gate, '100', now), 0)
        device = [now] * request_budget.PROFILE_SHARED[0]
        gate = {'requestTimes':device, 'accountRequestTimes':{}}
        self.assertEqual(request_budget.profile_next_at(gate, '200', now), now + 3600)
        self.assertEqual(request_budget.profile_next_at(gate, '200', now - 30), now + 3600)
        self.assertEqual(request_budget.next_at(gate, '200', now), 0)

    def test_basis_is_bounded_and_rejects_future_and_unbound_observations(self):
        basis = self.engine.profile_facts
        for n in range(21):
            p = parse_profile(profile(str(200 + n)))
            p.update(observedAt=self.now[0], avatar='not retained', biography='not retained')
            basis.record(self.engine.data, p)
        self.assertIsNone(basis.recent(self.engine.data, '200', self.now[0]))
        latest = basis.recent(self.engine.data, '220', self.now[0])
        self.assertNotIn('avatar', latest)
        self.assertNotIn('biography', latest)
        self.assertIsNone(basis.recent(self.engine.data, '220', self.now[0] - 1))
        self.engine.data['session'] = None
        self.assertIsNone(basis.recent(self.engine.data, '219', self.now[0]))
        self.assertEqual(basis.entries, {})


if __name__ == '__main__':
    unittest.main()
