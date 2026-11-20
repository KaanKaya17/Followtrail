"""Seeded fuzz tests for the Instagram ZIP readers.

Exports are user-selected files whose layout Instagram changes without
notice. A malformed row or file must never take down a whole view: summary,
lists, content calendar, insights, search and comparison stay available and
report what they skipped. Corrupt archives must fail with a handled error.

Runs are deterministic. Set ZIP_FUZZ_ITERATIONS to run longer locally, and
ZIP_FUZZ_SEED to reproduce a single failing case printed by a failure.
"""
import io
import json
import os
import random
import string
import tempfile
import unittest
from pathlib import Path
from zipfile import BadZipFile, ZIP_DEFLATED, ZipFile

from export_browser import ARCHIVE_ERRORS, ExportBrowser
from instagram_archive import InvalidArchive, read_archive

ITERATIONS = int(os.environ.get('ZIP_FUZZ_ITERATIONS', '60'))
FIXED_SEED = os.environ.get('ZIP_FUZZ_SEED')
FOLDER = 'connections/followers_and_following/'
# Paths every reader looks at, in current and older layouts.
TARGETS = [
    FOLDER + 'followers_1.json', FOLDER + 'followers_2.json', FOLDER + 'following.json',
    FOLDER + 'close_friends.json', FOLDER + 'recently_unfollowed_profiles.json',
    FOLDER + "follow_requests_you've_received.json", FOLDER + 'followers_1.html', FOLDER + 'following.html',
    'your_instagram_activity/likes/liked_posts.json', 'likes/liked_posts.json',
    'your_instagram_activity/likes/liked_comments.json',
    'your_instagram_activity/comments/post_comments_1.json', 'your_instagram_activity/comments/reels_comments.json',
    'your_instagram_activity/story_interactions/story_likes.json', 'your_instagram_activity/saved/saved_posts.json',
    'your_instagram_activity/media/posts_1.json', 'your_instagram_activity/media/stories.json',
    'your_instagram_activity/media/reels.json', 'logged_information/past_instagram_insights/posts.json',
    'logged_information/past_instagram_insights/audience_insights.json',
    'your_instagram_activity/messages/inbox/a_1/message_1.json',
    'personal_information/personal_information/personal_information.json',
    'personal_information/personal_information/profile_changes.json',
    'security_and_login_information/login_and_profile_creation/login_activity.json',
    'security_and_login_information/login_and_profile_creation/password_change_activity.json',
    'personal_information/device_information/devices.json',
    'ads_information/instagram_ads_and_businesses/advertisers_using_your_activity_or_information.json',
    'ads_information/ads_and_topics/ads_viewed.json', 'preferences/your_topics/your_topics.json',
    'connections/contacts/synced_contacts.json', 'start_here.html', 'media/posts/1.jpg',
]
LABELS = ['Username', 'Media Owner', 'Owner', 'Time', 'IP Address', 'User Agent', 'Name', 'Comment',
          'Caption', 'Creation Timestamp', 'Instagram Likes', 'Followers', 'Date Range', 'Author', 'Changed']
KEYS = ['title', 'string_list_data', 'string_map_data', 'label_values', 'media_list_data', 'media', 'timestamp',
        'timestamp_ms', 'creation_timestamp', 'value', 'href', 'uri', 'dict', 'vec', 'label', 'messages',
        'sender_name', 'content', 'advertiser_name', 'relationships_following', 'likes_media_likes',
        'profile_user', 'participants']


class Shapes:
    """Random JSON values biased towards the shapes Instagram exports use."""

    def __init__(self, rng):
        self.rng = rng

    def text(self):
        pool = [
            '', 'ada', 'Ada.Lovelace_1', '@efe', 'x' * self.rng.choice([31, 300, 5000]),
            'https://www.instagram.com/_u/ada/', 'https://www.instagram.com/p/x/?y=1', '../../etc',
            'Ã§Ã¼Ã¶ mixed ğ', '\u0000‮퟿', '日本語のテキスト', '😀' * 10, '1700000000',
            'Mozilla/5.0 (Windows NT 10.0) Chrome/120', 'Instagram 300 Android (33/13; 420dpi; 1080x2220; s; m; d; c; tr_TR; 1)',
            ''.join(self.rng.choice(string.printable) for _ in range(self.rng.randint(1, 40))),
        ]
        return self.rng.choice(pool)

    def number(self):
        return self.rng.choice([0, -1, 1, 946684799, 1700000000, 1700000000000, 2**31, 2**63, 10**30,
                                1.5, float('1e308'), True, False])

    def scalar(self):
        return self.rng.choice([self.text, self.number, lambda: None])()

    def value(self, depth=0):
        roll = self.rng.random()
        if depth > 5 or roll < 0.35:
            return self.scalar()
        if roll < 0.6:
            return [self.value(depth + 1) for _ in range(self.rng.randint(0, 4))]
        return self.row(depth + 1)

    def row(self, depth=0):
        row = {}
        for _ in range(self.rng.randint(0, 6)):
            key = self.rng.choice(KEYS)
            if key == 'string_map_data':
                row[key] = {self.rng.choice(LABELS): self.rng.choice([
                    {'value': self.scalar(), 'timestamp': self.number(), 'href': self.text()}, self.scalar(), []])
                    for _ in range(self.rng.randint(0, 4))}
            elif key in ('string_list_data', 'label_values', 'media', 'media_list_data', 'dict', 'vec'):
                row[key] = [self.rng.choice([
                    {'label': self.rng.choice(LABELS), 'value': self.scalar(), 'href': self.text(),
                     'timestamp': self.number(), 'uri': self.text(), 'dict': [self.value(depth + 1)]},
                    self.value(depth + 1)]) for _ in range(self.rng.randint(0, 4))]
            else:
                row[key] = self.value(depth + 1)
        return row

    def document(self):
        rows = [self.row() for _ in range(self.rng.randint(0, 12))]
        return self.rng.choice([
            rows, {self.rng.choice(KEYS): rows}, {'a': rows, 'b': rows[:1]}, self.value(), self.row(),
            {'messages': rows, 'participants': self.value()}, {'profile_user': rows},
        ])

    def body(self, name):
        roll = self.rng.random()
        if name.endswith('.jpg'):
            return bytes(self.rng.randrange(256) for _ in range(self.rng.randint(0, 64)))
        if name.endswith('.html'):
            return self.rng.choice([
                '<html><body><main><div><a href="https://www.instagram.com/ada/">ada</a></div></main>',
                '<aside role="contentinfo">Generated by qa.owner on <time datetime="2026-10-01T12:00:00Z">x</time>',
                '<div><a href="javascript:alert(1)">x</a><<<>>>', '﻿' + self.text(), '',
            ]).encode('utf-8', 'surrogatepass')
        if roll < 0.08:
            return b'\xff\xfe\x00 not json'
        if roll < 0.14:
            return ('[' * 5000 + ']' * 5000).encode()
        if roll < 0.2:
            return json.dumps(self.document())[:self.rng.randint(0, 200)].encode()
        if roll < 0.24:
            return b'\xef\xbb\xbf' + json.dumps(self.document()).encode()
        return json.dumps(self.document(), ensure_ascii=self.rng.random() < 0.5).encode('utf-8', 'surrogatepass')


class ZipFuzzTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.addCleanup(directory.cleanup)
        self.home = Path(directory.name)

    def seeds(self):
        return [int(FIXED_SEED)] if FIXED_SEED else range(ITERATIONS)

    def build(self, seed):
        rng = random.Random(seed)
        shapes = Shapes(rng)
        path = self.home / f'instagram-qa.owner-2026-10-01-Seed{seed:04d}.zip'
        with ZipFile(path, 'w', compression=ZIP_DEFLATED) as archive:
            # Always keep one valid follower so most runs reach the deeper views.
            archive.writestr(FOLDER + 'followers_1.json', json.dumps(
                [{'string_list_data': [{'value': 'ada', 'timestamp': 1700000000}]}]) if rng.random() < 0.5
                else shapes.body('followers_1.json'))
            for name in rng.sample(TARGETS, rng.randint(3, len(TARGETS))):
                if name != FOLDER + 'followers_1.json':
                    archive.writestr(name, shapes.body(name))
        return path, rng

    def exercise(self, path, rng):
        browser = ExportBrowser()
        try:
            opened = browser.open(path)
            token = opened['token']
            summary = browser.read_summary(token)
            self.assertIsInstance(summary['metrics'], dict)
            self.assertIsInstance(summary['scope'], dict)
            insights = browser.insights(token, rng.choice([0, -180, 330, 900, -900]))
            for key in ('people', 'activity', 'security', 'ads', 'years', 'skipped'):
                self.assertIn(key, insights)
            self.assertEqual(len(insights['activity']['grid']), 7)
            content = browser.content(token)
            if content['latest']:
                month = content['latest'][:7]
                browser.content(token, month, content['latest'])
            browser.search(token, rng.choice(['ada', 'ğü', '..', 'https']))
            for file in opened['files'][:40]:
                if file['kind'] == 'json':
                    try:
                        browser.browse(token, file['id'], query=rng.choice(['', 'a']), order='newest')
                        browser.text(token, file['id'], 0, 0)
                    except ValueError:
                        pass  # An unreadable single file is reported as archive_entry.
            try:
                browser.lists(token)
            except ValueError:
                pass  # No readable list at all is a handled "no lists" state.
            try:
                pair = browser.prepare_comparison(token, path)
                if pair['comparable']:
                    browser.compare(token, pair['candidate'])
            except ValueError:
                pass
        finally:
            browser.close()
        try:
            read_archive(path)
        except InvalidArchive:
            pass

    def test_malformed_contents_never_break_a_view(self):
        for seed in self.seeds():
            with self.subTest(seed=seed):
                path, rng = self.build(seed)
                try:
                    self.exercise(path, rng)
                except Exception as error:  # pragma: no cover - the message is the point
                    self.fail(f'ZIP_FUZZ_SEED={seed}: {type(error).__name__}: {error}')

    def test_corrupt_archives_fail_with_handled_errors(self):
        rng = random.Random(int(FIXED_SEED) if FIXED_SEED else 7)
        good = io.BytesIO()
        with ZipFile(good, 'w', compression=ZIP_DEFLATED) as archive:
            archive.writestr(FOLDER + 'followers_1.json', json.dumps([{'string_list_data': [{'value': 'ada'}]}]))
            archive.writestr('your_instagram_activity/likes/liked_posts.json', json.dumps({'likes_media_likes': []}))
        data = good.getvalue()
        variants = [b'', b'PK', bytes(rng.randrange(256) for _ in range(512)), data[:len(data) // 2], data[:-10],
                    data[:30] + bytes(40) + data[70:]]
        for _ in range(max(10, ITERATIONS // 3)):
            mutated = bytearray(data)
            for _ in range(rng.randint(1, 8)):
                mutated[rng.randrange(len(mutated))] = rng.randrange(256)
            variants.append(bytes(mutated))
        for index, raw in enumerate(variants):
            with self.subTest(variant=index):
                path = self.home / f'corrupt-{index}.zip'
                path.write_bytes(raw)
                browser = ExportBrowser()
                try:
                    token = browser.open(path)['token']
                except ARCHIVE_ERRORS + (BadZipFile,):
                    continue
                try:
                    # Opened despite damage: readers must still degrade, not raise.
                    browser.read_summary(token)
                    browser.insights(token)
                    browser.content(token)
                except Exception as error:  # pragma: no cover
                    self.fail(f'variant {index}: {type(error).__name__}: {error}')
                finally:
                    browser.close()
                with _maybe(InvalidArchive):
                    read_archive(path)

    def test_one_damaged_compressed_member_is_skipped(self):
        # Regression: corrupt deflate data raised zlib.error, which the readers
        # did not catch, so one damaged file took down the summary and insights.
        likes = 'your_instagram_activity/likes/liked_posts.json'
        buffer = io.BytesIO()
        with ZipFile(buffer, 'w', compression=ZIP_DEFLATED) as archive:
            archive.writestr(FOLDER + 'followers_1.json', json.dumps([{'string_list_data': [{'value': 'ada', 'timestamp': 1700000000}]}]))
            archive.writestr(likes, json.dumps({'likes_media_likes': [
                {'title': f'user{index}', 'string_list_data': [{'timestamp': 1700000000 + index}]} for index in range(400)]}))
        raw = bytearray(buffer.getvalue())
        with ZipFile(io.BytesIO(bytes(raw))) as archive:
            info = archive.getinfo(likes)
        start = info.header_offset + 30 + len(info.filename.encode())
        for offset in range(start + 20, start + 60):
            raw[offset] ^= 0x5A
        path = self.home / 'damaged.zip'
        path.write_bytes(bytes(raw))
        browser = ExportBrowser()
        try:
            token = browser.open(path)['token']
            summary = browser.read_summary(token)
            self.assertEqual(summary['metrics']['followers']['count'], 1)
            self.assertNotIn('likes', summary['metrics'])
            insights = browser.insights(token)
            self.assertIn('likes', insights['skipped'])
        finally:
            browser.close()
        self.assertEqual(read_archive(path)['followers'][0]['username'], 'ada')

    def test_hostile_entry_names_are_not_followed(self):
        path = self.home / 'names.zip'
        with ZipFile(path, 'w') as archive:
            for name in ('../outside.json', '/abs/followers_1.json', 'a\\b\\followers_1.json',
                         FOLDER + 'x' * 300 + '.json', FOLDER + 'followers_1.json', 'ünïcödé/çok/derin/' * 20 + 'a.json'):
                archive.writestr(name, json.dumps([{'string_list_data': [{'value': 'ada', 'timestamp': 1700000000}]}]))
        browser = ExportBrowser()
        try:
            token = browser.open(path)['token']
            summary = browser.read_summary(token)
            self.assertEqual(summary['metrics']['followers']['count'], 1)
            browser.insights(token)
            target = self.home / 'saved.json'
            browser.save(token, 0, str(target))
            self.assertTrue(target.exists())
            self.assertFalse((self.home.parent / 'outside.json').exists())
        finally:
            browser.close()


class _maybe:
    """Context manager that accepts either success or the given exception."""

    def __init__(self, error):
        self.error = error

    def __enter__(self):
        return self

    def __exit__(self, kind, value, traceback):
        return kind is None or issubclass(kind, self.error)


if __name__ == '__main__':
    unittest.main()
