import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile


SCRIPT = Path(__file__).with_name('watch-instagram-exports.py')
spec = importlib.util.spec_from_file_location('watch_instagram_exports', SCRIPT)
watch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(watch)


class ExportWatcherTests(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.directory = Path(folder.name)
        self.watcher = watch.ExportWatcher(self.directory, 'qa_test', mode='snapshot')

    def export(self, day, *, followers=None, following=None, account='qa_test', copy='sample'):
        path = self.directory / f'instagram-{account}-{day}-{copy}.zip'
        with ZipFile(path, 'w') as archive:
            for direction, names in (('followers', followers), ('following', following)):
                if names is None:
                    continue
                rows = [{'string_list_data': [{'value': name}]} for name in names]
                data = rows if direction == 'followers' else {'relationships_following': rows}
                archive.writestr(f'connections/followers_and_following/{direction}.json', json.dumps(data))
        return path

    def test_observes_a_new_file_after_it_settles_and_reports_equal_count_changes(self):
        self.export('2026-11-10', followers=['alice'], following=[])
        self.assertEqual(list(self.watcher.scan()), [])
        self.assertEqual(list(self.watcher.scan())[0]['lists']['followers']['status'], 'baseline')
        self.export('2026-11-11', followers=['bob'], following=[])
        self.assertEqual(list(self.watcher.scan()), [])
        with patch('socket.socket.connect', side_effect=AssertionError('network access')):
            latest, = self.watcher.scan()
        self.assertEqual(latest['lists']['followers'],
                         {'status': 'changed', 'previous_date': '2026-11-10', 'count': 1, 'added': 1, 'removed': 1})
        self.assertEqual(latest['lists']['following']['status'], 'unchanged')
        self.assertEqual(list(self.watcher.scan()), [])

    def test_missing_direction_keeps_its_last_baseline_and_other_accounts_are_ignored(self):
        self.export('2026-11-10', followers=['alice'], following=['bob'])
        self.export('2026-11-11', following=['bob'])
        self.export('2026-11-12', followers=[], following=['carol'])
        self.export('2026-11-13', followers=['bob'], account='other')
        first, middle, last = self.watcher.scan(once=True)
        self.assertEqual(middle['lists']['followers'], {'status': 'missing'})
        self.assertEqual(last['lists']['followers']['previous_date'], first['date'])
        self.assertEqual(last['lists']['followers']['removed'], 1)
        self.assertEqual(last['lists']['following']['added'], 1)

    def test_scheduled_delta_is_reported_without_treating_absent_accounts_as_removals(self):
        self.export('2026-11-10', followers=['alice', 'charlie'])
        self.export('2026-11-11', followers=['bob'])
        watcher = watch.ExportWatcher(self.directory, 'qa_test', mode='incremental')
        first, second = watcher.scan(once=True)
        self.assertEqual(first['lists']['followers'], {'status': 'delivered', 'count': 2})
        self.assertEqual(second['lists']['followers'], {'status': 'delivered', 'count': 1})
        self.assertEqual(second['lists']['following'], {'status': 'missing'})
        self.assertEqual(watcher.baselines, {})

    def test_partial_download_and_duplicate_date_do_not_emit_a_change(self):
        path = self.directory / 'instagram-qa_test-2026-11-10-sample.zip'
        path.write_bytes(b'PK unfinished')
        self.assertEqual(list(self.watcher.scan()), [])
        self.assertEqual(list(self.watcher.scan())[0]['status'], 'unreadable')
        self.export('2026-11-10', followers=['alice'])
        self.assertEqual(list(self.watcher.scan()), [])
        self.assertEqual(list(self.watcher.scan())[0]['lists']['followers']['count'], 1)
        self.export('2026-11-10', followers=['bob'], copy='duplicate')
        self.assertEqual(list(self.watcher.scan()), [])
        self.assertEqual(list(self.watcher.scan())[0]['status'], 'ambiguous_date')
        self.assertEqual(list(self.watcher.scan()), [])
        self.export('2026-11-11', followers=['alice'])
        self.assertEqual(list(self.watcher.scan(once=True))[0]['lists']['followers']['status'], 'unchanged')


if __name__ == '__main__':
    unittest.main()
