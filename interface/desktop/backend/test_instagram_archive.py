import csv
import json
import tempfile
import unittest
import unittest.mock
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile, ZipInfo

from instagram_archive import InvalidArchive, read_archive
from export_browser import ExportBrowser

FOLDER = 'connections/followers_and_following/'


def follower(name, timestamp=1700000000):
    return {'title': '', 'media_list_data': [], 'string_list_data': [
        {'href': f'https://www.instagram.com/{name}', 'value': name, 'timestamp': timestamp}]}


def following(name, timestamp=1690000000):
    return {'title': name, 'string_list_data': [{'href': f'https://www.instagram.com/_u/{name}', 'timestamp': timestamp}]}


def write_zip(path, entries):
    with ZipFile(path, 'w', compression=ZIP_DEFLATED) as archive:
        for name, content in entries.items():
            archive.writestr(name, content if isinstance(content, str) else json.dumps(content))
    return path


class InstagramArchiveTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.addCleanup(self.directory.cleanup)
        self.home = Path(self.directory.name)

    def open_zip(self, entries, name='instagram-export.zip'):
        return read_archive(write_zip(self.home / name, entries))

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
        self.assertEqual(found['followers'], [{'username': 'alice', 'timestamp': None}, {'username': 'carol', 'timestamp': 1700000001}])
        self.assertEqual(found['following'], [{'username': 'alice', 'timestamp': 1720000000}, {'username': 'bob', 'timestamp': None}])
        self.assertTrue(found['quality']['followers']['complete'] and found['quality']['following']['complete'])

    def test_reads_html_relationship_rows_and_ignores_navigation(self):
        found = self.open_zip({
            FOLDER + 'following.html': (
                '<html><body><header><a href="https://www.instagram.com/ignored/">Navigation</a></header>'
                '<main><div><h2>Alice</h2><a href="https://www.instagram.com/_u/Alice">Link</a></div>'
                '<div><a href="https://www.instagram.com/bob/">Link</a></div>'
                '<a href="https://help.instagram.com/123">Learn more</a></main></body></html>'
            ),
        })
        self.assertIsNone(found['followers'])
        self.assertEqual(found['following'], [{'username': name, 'timestamp': None} for name in ('alice', 'bob')])
        self.assertEqual((found['quality']['following']['format'], found['quality']['following']['complete']), ('html', True))

    def test_distinguishes_empty_list_from_missing_list(self):
        found = self.open_zip({FOLDER + 'followers_1.json': []})
        self.assertEqual(found['followers'], [])
        self.assertIsNone(found['following'])

    def test_bad_rows_and_files_degrade_one_list_and_say_so(self):
        found = self.open_zip({
            FOLDER + 'followers_1.json': [follower('ada'), {'title': '', 'string_list_data': []},
                                           {'title': 'alice', 'string_list_data': [{'value': 'bob'}]}],
            FOLDER + 'followers_3.json': 'not json',
            FOLDER + 'following.json': {'relationships_following': [following('kitap')]},
        })
        self.assertEqual([row['username'] for row in found['followers']], ['ada'])
        quality = found['quality']['followers']
        self.assertEqual((quality['skipped'], quality['missingParts'], quality['unreadableParts'], quality['complete']),
                         (2, [2], 1, False))
        self.assertTrue(found['quality']['following']['complete'])

    def test_unreadable_list_keeps_the_other_and_json_wins_over_stray_html(self):
        found = self.open_zip({
            FOLDER + 'followers_1.html': '<html></html>',
            FOLDER + 'following.json': {'relationships_following': [following('kitap')]},
            FOLDER + 'following.html': '<main><a href="https://www.instagram.com/x">x</a></main>',
        })
        self.assertIsNone(found['followers'])
        self.assertIn('followers', found['quality'])
        self.assertEqual(found['following'], [{'username': 'kitap', 'timestamp': 1690000000}])

    def test_two_exports_in_one_zip_use_the_fuller_folder_and_report_it(self):
        found = self.open_zip({
            FOLDER + 'followers_1.json': [follower('ada')], FOLDER + 'following.json': {'relationships_following': []},
            'old/' + FOLDER + 'followers_1.json': [follower('eski')],
        })
        self.assertEqual(found['followers'][0]['username'], 'ada')
        self.assertEqual(found['ignoredSources'], 1)

    def test_extra_relationship_lists_are_read_with_dates(self):
        found = self.open_zip({
            FOLDER + 'followers_1.json': [follower('ada')],
            FOLDER + 'recently_unfollowed_profiles.json': {'relationships_unfollowed_users': [following('eski', 1699000000)]},
            FOLDER + 'pending_follow_requests.json': {'relationships_follow_requests_sent': [following('gizli')]},
            FOLDER + 'close_friends.json': {'relationships_close_friends': [following('ada')]},
            FOLDER + 'blocked_profiles.json': {'relationships_blocked_users': [following('spam')]},
            FOLDER + 'unknown_list.json': {'relationships_other': [following('x')]},
        })
        self.assertEqual(sorted(found['extras']), ['blocked', 'closeFriends', 'pendingRequests', 'recentlyUnfollowed'])
        self.assertEqual(found['extras']['recentlyUnfollowed']['rows'], [{'username': 'eski', 'timestamp': 1699000000}])

    def test_label_value_rows_of_current_exports_are_read(self):
        # Shape of current Instagram exports for the extra lists (values synthetic).
        row = lambda name, ts: {'timestamp': ts, 'media': [], 'fbid': '17800000000000000', 'label_values': [
            {'label': 'URL', 'value': ''}, {'label': 'Name', 'value': 'QA ' + name}, {'label': 'Username', 'value': name}]}
        found = self.open_zip({
            FOLDER + 'followers_1.json': [follower('ada')],
            FOLDER + 'close_friends.json': [row('ada', 1695000000), row('kitap', 1695000000000)],
            FOLDER + 'blocked_profiles.json': [row('spam', 1696000000), {'timestamp': 1, 'label_values': [{'label': 'Name', 'value': 'x'}]}],
        })
        self.assertEqual(found['extras']['closeFriends']['rows'],
                         [{'username': 'ada', 'timestamp': 1695000000}, {'username': 'kitap', 'timestamp': 1695000000}])
        self.assertEqual((found['extras']['blocked']['quality']['skipped'], len(found['extras']['blocked']['rows'])), (1, 1))

    def test_rejects_archives_with_nothing_readable(self):
        for entries in ({FOLDER + 'followers_1.html': '<html></html>'}, {'other/file.json': []},
                        {FOLDER + 'followers_1.json': 'broken'}):
            with self.subTest(entries=entries), self.assertRaises(InvalidArchive):
                self.open_zip(entries)

    def test_full_export_over_512_mib_is_read(self):
        path = self.home / 'big.zip'
        with ZipFile(path, 'w', compression=ZIP_DEFLATED) as archive:
            archive.writestr(FOLDER + 'followers_1.json', json.dumps([follower('ada')]))
            info = ZipInfo('media/posts/big.mp4')
            info.compress_type = ZIP_STORED
            with archive.open(info, 'w', force_zip64=True) as out:
                chunk = b'\0' * (1 << 20)
                for _ in range(520):
                    out.write(chunk)
        self.assertEqual(read_archive(path)['followers'][0]['username'], 'ada')


class ArchiveBrowserTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.addCleanup(self.directory.cleanup)
        self.home = Path(self.directory.name)
        self.browser = ExportBrowser()
        self.addCleanup(self.browser.close)

    def zip(self, name, entries):
        return write_zip(self.home / name, entries)

    def test_summary_reports_partial_lists_extras_messages_comments_and_oversize(self):
        path = self.zip('instagram-qa.owner-2026-10-01-AbCdEf12.zip', {
            FOLDER + 'followers_1.json': [follower('ada'), {'string_list_data': []}],
            FOLDER + 'following.json': {'relationships_following': [following('ada')]},
            FOLDER + 'recently_unfollowed_profiles.json': {'relationships_unfollowed_users': [following('eski')]},
            'your_instagram_activity/messages/inbox/ada_1/message_1.json': {'messages': []},
            'your_instagram_activity/messages/inbox/ada_1/message_2.json': {'messages': []},
            'your_instagram_activity/messages/inbox/kitap_2/message_1.json': {'messages': []},
            'comments/post_comments_1.json': [{'string_map_data': {'Comment': {'value': 'a'}}}],
            'comments/reels_comments.json': {'comments_reels_comments': [{'string_map_data': {'Comment': {'value': 'b'}}}]},
            'likes/liked_posts.json': {'likes_media_likes': [{'title': 'ada', 'string_list_data': [{'timestamp': 1700000000}]}]},
        })
        opened = self.browser.open(path)
        self.assertEqual(opened['metadata']['account'], 'qa.owner')
        self.assertEqual(opened['metadata']['accountSource'], 'filename')
        summary = self.browser.read_summary(opened['token'])
        self.assertEqual(summary['scope']['followers'], 'partial')
        self.assertEqual((summary['metrics']['followers']['count'], summary['metrics']['followers']['skipped']), (1, 1))
        self.assertEqual(summary['metrics']['messages']['count'], 2)
        self.assertEqual(summary['metrics']['comments']['count'], 2)
        self.assertEqual(summary['metrics']['likes']['count'], 1)
        self.assertEqual(summary['relations']['recentlyUnfollowed']['count'], 1)
        self.assertIsNotNone(summary['relations']['recentlyUnfollowed']['index'])
        with unittest.mock.patch('export_browser.MAX_JSON', 10):
            self.browser.summary_data = None
            self.assertTrue(self.browser.read_summary(opened['token'])['metrics']['likes']['tooLarge'])

    def test_archive_account_wins_over_filename(self):
        path = self.zip('instagram-other.name-2026-10-01-AbCdEf12.zip', {
            'start_here.html': '<aside role="contentinfo">Generated by qa.owner on <time datetime="2026-10-01T12:00:00Z">x</time></aside>',
            FOLDER + 'followers_1.json': []})
        metadata = self.browser.open(path)['metadata']
        self.assertEqual(metadata['account'], 'qa.owner')
        self.assertNotIn('accountSource', metadata)

    def test_comparison_dates_renames_missing_lists_search_and_csv(self):
        old = self.zip('old.zip', {
            FOLDER + 'followers_1.json': [follower('ada', 1), follower('mert.eski', 5), follower('efe', 6)],
            FOLDER + 'following.json': {'relationships_following': [following('kitap', 9)]},
        })
        new = self.zip('new.zip', {
            FOLDER + 'followers_1.json': [follower('ada', 1), follower('mert.yeni', 5), follower('selin', 7)],
        })
        token = self.browser.open(old)['token']
        pair = self.browser.prepare_comparison(token, new)
        self.assertEqual(pair['first']['lists'], {'followers': {'count': 3, 'complete': True}, 'following': {'count': 1, 'complete': True}})
        self.assertEqual(pair['second']['lists'], {'followers': {'count': 3, 'complete': True}, 'following': None})
        self.assertTrue(pair['comparable'] and pair['unknownAccount'])
        result = self.browser.compare(token, pair['candidate'])
        self.assertEqual(result['missing'], {'following': 'second'})
        self.assertEqual(result['directions']['followers'], {'before': 3, 'after': 3, 'added': 1, 'removed': 1, 'renamed': 1, 'complete': True})
        page = lambda change, **kw: self.browser.comparison_page(token, 'followers', change, **kw)
        self.assertEqual(page('added')['rows'], [{'username': 'selin', 'timestamp': 7}])
        self.assertEqual(page('removed')['rows'], [{'username': 'efe', 'timestamp': 6}])
        self.assertEqual(page('renamed')['rows'], [{'from': 'mert.eski', 'to': 'mert.yeni', 'timestamp': 5}])
        self.assertEqual(page('renamed', query='@yeni')['total'], 1)
        self.assertEqual(page('added', query='zzz')['total'], 0)
        target = self.home / 'diff.csv'
        self.browser.comparison_csv(token, str(target))
        with open(target, encoding='utf-8-sig', newline='') as stream:
            rows = list(csv.reader(stream))
        self.assertEqual(rows[0], ['list', 'change', 'username', 'previous_username', 'follow_date_utc'])
        self.assertIn(['followers', 'renamed', 'mert.yeni', 'mert.eski', '1970-01-01'], rows)
        with self.assertRaises(ValueError):
            self.browser.comparison_csv(token, str(self.home / 'diff.txt'))

    def test_ambiguous_same_date_pairs_are_not_renames(self):
        old = self.zip('old.zip', {FOLDER + 'followers_1.json': [follower('a', 5), follower('b', 5)]})
        new = self.zip('new.zip', {FOLDER + 'followers_1.json': [follower('c', 5), follower('d', 5)]})
        token = self.browser.open(old)['token']
        pair = self.browser.prepare_comparison(token, new)
        result = self.browser.compare(token, pair['candidate'])
        self.assertEqual(result['directions']['followers']['renamed'], 0)
        self.assertEqual(result['directions']['followers']['added'], 2)

    def test_open_archive_lists_feed_the_relationship_view(self):
        token = self.browser.open(self.zip('a.zip', {FOLDER + 'followers_1.json': [follower('ada')]}))['token']
        self.assertEqual(self.browser.lists(token)['followers'][0]['username'], 'ada')
        with self.assertRaises(ValueError):
            self.browser.lists('wrong')


if __name__ == '__main__':
    unittest.main()
