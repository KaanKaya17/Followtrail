"""Real encrypted reservations and scan boundaries with synthetic time/HTTP."""
import copy
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from accounts import Accounts
from engine import Engine, REQUEST_SPACING
from network import AppFailure, parse_profile
from storage import Vault
from test_backend import profile, page, FakeClient
from test_scan_progress import ClockWait, PacedClient


class RequestJitterTests(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.addCleanup(self.home.cleanup)
        self.now = [1800000000.0]
        self.events = []

    def engine(self, delay=None, cls=Engine):
        e = cls(self.home.name, emit=self.events.append, clock=lambda:self.now[0], request_delay=delay)
        e.cancel = ClockWait(self.now)
        return e

    def account(self, e, identity):
        p = parse_profile(profile(identity))
        e.select_connection_account(p)
        e.record_profile(p, own=True)
        e.data['session'] = {'cookies':{'ds_user_id':identity,'sessionid':'SYNTHETIC'}}
        e.save()

    def test_default_uniform_bounds_and_one_draw_per_reservation(self):
        with patch('engine.random.uniform', side_effect=[12.0,18.0,15.375]) as draw:
            e = self.engine()
            for spacing in (12.0,18.0,15.375):
                e.before_request()
                self.assertEqual(e.data['gate']['nextAt']-e.data['gate']['lastAt'],spacing)
                e.snapshot()
            self.assertEqual(draw.call_count,3)
            self.assertTrue(all(call.args==REQUEST_SPACING for call in draw.call_args_list))
        self.assertEqual(e.cancel.waits,[12.0,18.0])
        self.assertEqual(Vault(self.home.name).load()['gate'],e.data['gate'])
        self.assertEqual(e.snapshot()['requestPacing'],{'minSeconds':12,'maxSeconds':18})

    def test_restart_switch_and_local_edits_keep_the_same_shared_deadline(self):
        first = Mock(return_value=17.25)
        e = self.engine(first, Accounts)
        self.account(e,'100')
        e.before_request()
        reserved = e.data['gate']['nextAt']
        self.account(e,'300')
        next_draw = Mock(return_value=12.5)
        restarted = self.engine(next_draw, Accounts)
        for identity in ('100','300','100'):
            restarted.run('switch_account',{'id':identity})
            restarted.run('settings',{'interval':5})
            self.assertEqual(restarted.data['gate']['nextAt'],reserved)
        next_draw.assert_not_called()
        restarted.before_request()
        self.assertEqual(restarted.cancel.waits,[17.25])
        self.assertEqual(restarted.data['gate']['nextAt'],reserved+12.5)
        next_draw.assert_called_once_with()
        self.assertEqual(restarted.data['settings']['interval'],5)

    def test_existing_longer_reservation_is_not_shortened_on_upgrade(self):
        e = self.engine()
        e.data['gate'] = {'nextAt':self.now[0]+40,'attempts':8}
        e.save()
        draw = Mock(return_value=12)
        reopened = self.engine(draw)
        self.assertEqual(reopened.snapshot()['gate']['nextAt'],self.now[0]+40)
        draw.assert_not_called()
        reopened.before_request()
        self.assertEqual(reopened.cancel.waits,[40])
        self.assertEqual(reopened.data['gate']['attempts'],9)

    def test_cancelled_wait_neither_redraws_nor_changes_the_reservation(self):
        draw = Mock(return_value=18)
        e = self.engine(draw)
        e.before_request()
        before = copy.deepcopy(e.data['gate'])
        e.cancel.cancel_next = True
        with self.assertRaises(AppFailure) as caught:
            e.before_request()
        self.assertEqual(caught.exception.code,'cancelled')
        draw.assert_called_once_with()
        self.assertEqual(e.data['gate'],before)
        self.assertEqual(Vault(self.home.name).load()['gate'],before)

    def test_idle_time_does_not_add_a_fresh_wait_before_the_next_request(self):
        e = self.engine(Mock(side_effect=[12,18]))
        e.before_request()
        self.now[0] += 300
        e.before_request()
        self.assertEqual(e.cancel.waits,[])
        self.assertEqual(e.data['gate']['nextAt'],self.now[0]+18)

    def test_429_keeps_server_hold_and_blocked_requests_draw_nothing(self):
        draw = Mock(return_value=12)
        e = self.engine(draw, Accounts)
        self.account(e,'100')
        e.before_request()
        with self.assertRaises(AppFailure):
            e.after_response(SimpleNamespace(status_code=429,headers={'Retry-After':'7200'}))
        self.assertEqual(e.data['gate']['blockedUntil'],self.now[0]+7200)
        before = copy.deepcopy(e.catalog['gate'])
        fake = FakeClient([])
        def gated_read(*_args):
            e.before_request()
            raise AssertionError('No request during the hold')
        with patch.object(e,'client',return_value=fake), patch.object(e,'read_profile',side_effect=gated_read):
            with self.assertRaises(AppFailure) as caught:
                e.run('refresh',{})
        self.assertEqual(caught.exception.code,'cooldown')
        self.assertEqual(e.catalog['gate'],before)
        self.assertTrue(e.snapshot()['backgroundMonitor']['held'])
        draw.assert_called_once_with()
        self.assertEqual(fake.calls,[])

    def test_paged_scan_uses_chosen_delays_and_reports_each_actual_deadline(self):
        e = self.engine(Mock(side_effect=[12,18,13.25,17.75]))
        e.record_profile(parse_profile(profile()),own=True)
        e.data['session'] = {'cookies':{'ds_user_id':'100','sessionid':'SYNTHETIC'}}
        client = PacedClient(e,[profile(followers=3),page(['200'],'next1'),
                                page(['201'],'next2'),page(['202'])])
        with patch.object(e,'client',return_value=client):
            e.run('scan',{'direction':'followers'})
        self.assertEqual(e.cancel.waits,[12,18,13.25])
        waits = [event for event in self.events if event.get('phase')=='waiting']
        self.assertEqual([event['until']-event['waitStartedAt'] for event in waits],[12,18,13.25])
        self.assertEqual([event['scan']['page'] for event in waits],[1,2,3])
        self.assertTrue(e.data['lists']['100:followers']['snapshots'][-1]['complete'])
        self.assertEqual(e.request_delay.call_count,4)


if __name__ == '__main__':
    unittest.main()
