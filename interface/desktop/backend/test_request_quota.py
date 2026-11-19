"""Hourly and 24-hour request counts shown on the overview. Offline only."""
import tempfile
import unittest

from engine import Engine
import request_budget


class RequestQuotaTests(unittest.TestCase):
    def test_usage_counts_each_rolling_window_for_the_account_and_the_device(self):
        now = 1800000000.0
        gate = {'requestTimes': [now - 90000, now - 7200, now - 1800, now - 60, now + 30],
                'accountRequestTimes': {'100': [now - 7200, now - 60], '200': [now - 1800]}}
        self.assertEqual(request_budget.usage(gate, '100', now),
                         {'accountHourUsed': 1, 'accountDayUsed': 2, 'sharedHourUsed': 3, 'sharedDayUsed': 4})
        self.assertEqual(request_budget.usage(gate, None, now)['accountDayUsed'], 0)
        self.assertEqual(request_budget.usage({}, '100', now),
                         {'accountHourUsed': 0, 'accountDayUsed': 0, 'sharedHourUsed': 0, 'sharedDayUsed': 0})

    def test_snapshot_carries_counts_next_to_the_limits(self):
        with tempfile.TemporaryDirectory(prefix='TrackerQA-') as home:
            engine = Engine(home, clock=lambda: 1800000000.0)
            budget = engine.snapshot()['requestBudget']
            self.assertEqual((budget['accountHourUsed'], budget['sharedDayUsed']), (0, 0))
            self.assertEqual((budget['accountHourly'], budget['accountDaily']), request_budget.ACCOUNT)


if __name__ == '__main__':
    unittest.main()
