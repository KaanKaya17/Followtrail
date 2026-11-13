import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

from instagram_archive import InvalidArchive, read_archive

FOLDER = 'connections/followers_and_following/'


class InstagramArchiveTests(unittest.TestCase):
    def open_zip(self, entries):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'instagram-export.zip'
            with ZipFile(path, 'w', compression=ZIP_DEFLATED) as archive:
                for name, content in entries.items():
                    archive.writestr(name, content if isinstance(content, str) else json.dumps(content))
            return read_archive(path)

    def test_reads_split_lists_and_ignores_unrelated_entries(self):
        found = self.open_zip({
            FOLDER + 'followers_1.json': [{'string_list_data': [{'value': 'Alice', 'timestamp': 1700000000}]}],
            FOLDER + 'followers_2.json': [{'string_list_data': [{'value': 'alice', 'timestamp': 1710000000}]},
                                           {'string_list_data': [{'value': 'carol', 'timestamp': 1700000001}]}],
            FOLDER + 'following.json': {'relationships_following': [
                {'title': 'alice', 'string_list_data': [{'href': 'https://www.instagram.com/_u/alice', 'timestamp': 1720000000}]},
                {'string_list_data': [{'href': 'https://www.instagram.com/_u/bob', 'timestamp': True}]},
            ]},
            'messages/inbox/ignored.json': 'invalid JSON',
        })
        self.assertEqual(found, {
            'fileName': 'instagram-export.zip',
            'followers': [{'username': 'alice', 'timestamp': None}, {'username': 'carol', 'timestamp': 1700000001}],
            'following': [{'username': 'alice', 'timestamp': 1720000000}, {'username': 'bob', 'timestamp': None}],
        })

    def test_reads_html_relationship_rows_and_ignores_navigation(self):
        found = self.open_zip({
            FOLDER + 'following.html': (
                '<html><body><header><a href="https://www.instagram.com/ignored/">Navigation</a></header>'
                '<main><div><h2>Alice</h2><a href="https://www.instagram.com/_u/Alice">Link</a></div>'
                '<div><a href="https://www.instagram.com/bob/">Link</a></div></main></body></html>'
            ),
        })
        self.assertIsNone(found['followers'])
        self.assertEqual(found['following'], [{'username': name, 'timestamp': None} for name in ('alice', 'bob')])

    def test_distinguishes_empty_list_from_missing_list(self):
        found = self.open_zip({FOLDER + 'followers_1.json': []})
        self.assertEqual(found['followers'], [])
        self.assertIsNone(found['following'])

    def test_rejects_gaps_cross_export_entries_and_conflicting_names(self):
        for entries in (
            {FOLDER + 'followers_2.json': []},
            {FOLDER + 'followers_1.json': [], 'other/' + FOLDER + 'following.json': []},
            {FOLDER + 'followers_1.json': [{'title': 'alice', 'string_list_data': [{'value': 'bob'}]}]},
            {FOLDER + 'followers_1.html': '<html></html>'},
        ):
            with self.subTest(entries=entries), self.assertRaises(InvalidArchive):
                self.open_zip(entries)
