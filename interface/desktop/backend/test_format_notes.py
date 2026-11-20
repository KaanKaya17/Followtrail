import tempfile
import unittest
from pathlib import Path

from export_browser import ExportBrowser
from instagram_archive import format_notes
from test_instagram_archive import FOLDER, follower, following, write_zip


class FormatNotesTests(unittest.TestCase):
    def test_known_lists_and_parts_produce_no_notes(self):
        self.assertEqual(format_notes([
            FOLDER + 'followers_1.json', FOLDER + 'followers_2.json', FOLDER + 'following.json',
            FOLDER + 'close_friends.json', FOLDER + "follow_requests_you've_received.json",
            FOLDER + 'pending_follow_requests_2.json', FOLDER + 'following_hashtags.json',
            'your_instagram_activity/likes/liked_posts.json', 'media/posts/1.jpg',
        ]), {})

    def test_unrecognized_list_files_are_named_without_part_numbers(self):
        notes = format_notes([FOLDER + 'followers_1.json', FOLDER + 'mutual_followers_1.json',
                              FOLDER + 'mutual_followers_2.json', FOLDER + 'subscribers.html',
                              FOLDER + 'notes.txt'])
        self.assertEqual(notes, {'unrecognized': ['mutual_followers.json', 'subscribers.html']})

    def test_lists_outside_the_expected_folder_are_reported(self):
        notes = format_notes(['connections/followers_and_followings/followers_1.json',
                              'connections/followers_and_followings/following.json'])
        self.assertEqual(notes, {'elsewhere': ['connections/followers_and_followings']})

    def test_unsafe_and_unusual_names_are_ignored(self):
        self.assertEqual(format_notes([FOLDER + '../escape.json', 'C:\\x\\followers_1.json',
                                       FOLDER + ('x' * 200) + '.json', FOLDER + 'liste listesi.json']), {})

    def test_notes_are_capped(self):
        notes = format_notes([FOLDER + f'new_list_{chr(97 + i // 26)}{chr(97 + i % 26)}.json' for i in range(40)])
        self.assertEqual(len(notes['unrecognized']), 20)


class SummaryFormatNotesTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.addCleanup(directory.cleanup)
        self.home = Path(directory.name)
        self.browser = ExportBrowser()
        self.addCleanup(self.browser.close)

    def summary(self, entries):
        token = self.browser.open(write_zip(self.home / 'a.zip', entries))['token']
        return self.browser.read_summary(token)

    def test_summary_lists_unrecognized_files_next_to_readable_lists(self):
        summary = self.summary({FOLDER + 'followers_1.json': [follower('ada')],
                                FOLDER + 'following.json': {'relationships_following': [following('efe')]},
                                FOLDER + 'mutual_followers.json': {'relationships_mutual': []}})
        self.assertEqual(summary['metrics']['followers']['count'], 1)
        self.assertEqual(summary['formatNotes'], {'unrecognized': ['mutual_followers.json']})

    def test_renamed_folder_is_reported_when_lists_are_missing(self):
        summary = self.summary({'connections/relationships/followers_1.json': [follower('ada')]})
        self.assertEqual(summary['scope']['followers'], 'missing')
        self.assertEqual(summary['formatNotes'], {'elsewhere': ['connections/relationships']})

    def test_copies_elsewhere_are_not_reported_when_lists_were_found(self):
        summary = self.summary({FOLDER + 'followers_1.json': [follower('ada')],
                                'old_export/followers_1.json': [follower('ada')]})
        self.assertEqual(summary['formatNotes'], {})


if __name__ == '__main__':
    unittest.main()
