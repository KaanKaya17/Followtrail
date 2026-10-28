"""Production paging and pacing boundaries with synthetic HTTP and time."""
from types import SimpleNamespace
from unittest.mock import patch
import tempfile
import threading
import unittest

from engine import Engine
from network import AppFailure, parse_profile
from test_backend import FakeClient, page, profile


class ClockWait(threading.Event):
    def __init__(self, now):
        super().__init__()
        self.now = now
        self.waits = []
        self.cancel_next = False

    def wait(self, seconds):
        self.waits.append(seconds)
        if self.cancel_next:
            self.set()
            return True
        self.now[0] += seconds
        return False


class PacedClient(FakeClient):
    def __init__(self, engine, responses):
        super().__init__(responses)
        self.engine = engine

    def private_request(self, endpoint, **kwargs):
        self.engine.before_request()
        value = super().private_request(endpoint, **kwargs)
        if value == 429:
            self.engine.after_response(SimpleNamespace(status_code=429, headers={'Retry-After': '90'}))
        self.engine.after_response(SimpleNamespace(status_code=200, headers={}))
        return value


class ScanProgressTests(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.now = [1800000000.0]
        self.events = []
        self.engine = Engine(self.home.name, emit=self.events.append, clock=lambda: self.now[0])
        self.engine.cancel = ClockWait(self.now)
        self.engine.record_profile(parse_profile(profile()), own=True)
        self.engine.data['session'] = {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC'}}

    def tearDown(self):
        self.home.cleanup()

    def run_scan(self, responses):
        client = PacedClient(self.engine, responses)
        with patch.object(self.engine, 'client', return_value=client):
            self.engine.scan({'direction': 'followers'})
        return client

    def test_page_context_survives_real_pacing_and_copies_partial_counts(self):
        self.run_scan([profile(followers=5), page(['200', '201'], 'next1'),
                       page(['201', '202', '203'], 'next2'), page(['204'])])
        waiting = [e for e in self.events if e.get('phase') == 'waiting']
        self.assertEqual(len(waiting), 3)
        second = waiting[1]
        self.assertEqual(second['scan'], {'ownerId': '100', 'username': 'qa.owner',
            'direction': 'followers', 'step': 'page', 'page': 2,
            'completedPages': 1, 'count': 2, 'expected': 5})
        self.assertEqual(second['until'] - second['waitStartedAt'], 15)
        self.assertEqual(self.engine.cancel.waits, [15, 15, 15])
        requests = [e for e in self.events if e.get('phase') == 'request']
        self.assertEqual(requests[2]['scan'], second['scan'])
        received = [e for e in self.events if e.get('phase') == 'list_received']
        self.assertEqual([e['scan']['count'] for e in received], [2, 4, 5])
        self.assertEqual(received[-1]['scan']['completedPages'], 3)
        self.assertTrue(self.engine.data['lists']['100:followers']['snapshots'][-1]['complete'])
        self.assertIsNone(self.engine.scan_progress)
        self.engine.progress('profile')
        self.assertNotIn('scan', self.events[-1])
        self.assertNotIn('totalPages', second['scan'])

    def test_initial_profile_wait_is_not_misrepresented_as_a_list_page(self):
        self.engine.data['gate']['nextAt'] = self.now[0] + 9
        self.run_scan([profile(followers=0), page([])])
        initial = next(e for e in self.events if e.get('phase') == 'waiting')
        self.assertEqual(initial['scan']['step'], 'profile')
        self.assertIsNone(initial['scan']['page'])
        self.assertIsNone(initial['scan']['expected'])
        self.assertEqual(initial['until'] - initial['waitStartedAt'], 9)

    def test_cancellation_during_spacing_preserves_received_page_without_next_request(self):
        engine = self.engine
        client = PacedClient(engine, [profile(), page(['200'], 'next'), page(['201'])])
        original = engine.emit
        def emit(event):
            original(event)
            if event.get('phase') == 'list_received':
                engine.cancel.cancel_next = True
        engine.emit = emit
        with patch.object(engine, 'client', return_value=client), self.assertRaises(AppFailure) as caught:
            engine.scan({'direction': 'followers'})
        self.assertEqual(caught.exception.code, 'cancelled')
        self.assertEqual(len(client.calls), 2)
        draft = engine.data['lists']['100:followers']['draft']
        self.assertEqual(draft['status'], 'cancelled')
        self.assertEqual(len(draft['members']), 1)
        self.assertIsNone(engine.scan_progress)

    def test_429_is_stopped_not_a_short_countdown_and_keeps_partial_context(self):
        engine = self.engine
        client = PacedClient(engine, [profile(), page(['200'], 'next'), 429, page(['201'])])
        with patch.object(engine, 'client', return_value=client), self.assertRaises(AppFailure) as caught:
            engine.scan({'direction': 'followers'})
        self.assertEqual(caught.exception.code, 'limited')
        limited = next(e for e in self.events if e.get('phase') == 'limited')
        self.assertEqual(limited['http'], 429)
        self.assertEqual(limited['scan']['page'], 2)
        self.assertEqual(limited['scan']['count'], 1)
        self.assertGreaterEqual(limited['until'], self.now[0] + 3600)
        self.assertTrue(engine.data['gate']['manualOnly'])
        self.assertEqual(len(client.calls), 3)
        self.assertEqual(engine.data['lists']['100:followers']['draft']['status'], 'partial')
        self.assertIsNone(engine.scan_progress)

    def test_failed_initial_save_still_closes_client_and_clears_scan_state(self):
        client = PacedClient(self.engine, [])
        with patch.object(self.engine, 'client', return_value=client), \
             patch.object(self.engine, 'save', side_effect=OSError('synthetic disk failure')), \
             patch('engine.close_client') as close, self.assertRaises(OSError):
            self.engine.scan({'direction': 'followers'})
        close.assert_called_once_with(client)
        self.assertIsNone(self.engine.scan_progress)
        self.assertFalse(self.engine.list_scan_active)


if __name__ == '__main__':
    unittest.main()
