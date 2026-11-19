import tempfile
import unittest
import unittest.mock
from datetime import datetime, timezone
from pathlib import Path

import archive_insights
from archive_insights import device, target
from export_browser import ExportBrowser
from test_instagram_archive import FOLDER, follower, following, write_zip

DAY = 86400
# 2025-03-03 is a Monday; 09:00 UTC.
MONDAY = int(datetime(2025, 3, 3, 9, tzinfo=timezone.utc).timestamp())
ANDROID = 'Instagram 300.0.0.0 Android (33/13; 420dpi; 1080x2220; samsung; SM-G991B; o1s; exynos2100; tr_TR; 123)'
WINDOWS = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36'


def liked(owner, at):
    return {'title': owner, 'string_list_data': [{'href': 'https://www.instagram.com/p/x/', 'value': '👍', 'timestamp': at}]}


def labelled_like(owner, at):
    # Current exports nest the owner in label_values → dict → dict.
    return {'timestamp': at, 'label_values': [
        {'label': 'URL', 'value': 'https://www.instagram.com/p/y/'},
        {'label': 'Owner', 'dict': [{'dict': [{'label': 'Name', 'value': 'Görünen Ad'},
                                               {'label': 'Username', 'value': owner}], 'title': ''}]}]}


def comment(owner, at):
    return {'string_map_data': {'Comment': {'value': 'harika'}, 'Media Owner': {'value': owner}, 'Time': {'timestamp': at}}}


def login(at, agent, ip):
    return {'title': datetime.fromtimestamp(at, timezone.utc).isoformat(), 'string_map_data': {
        'IP Address': {'value': ip}, 'Language Code': {'value': 'tr'}, 'Time': {'timestamp': at},
        'User Agent': {'value': agent}}}


class ArchiveInsightsTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.addCleanup(self.directory.cleanup)
        self.home = Path(self.directory.name)
        self.browser = ExportBrowser()
        self.addCleanup(self.browser.close)

    def open(self, entries, name='instagram-qa.owner-2025-12-01-AbCdEf12.zip'):
        return self.browser.open(write_zip(self.home / name, entries))['token']

    def full_export(self):
        return self.open({
            FOLDER + 'followers_1.json': [follower('ada', MONDAY - 400 * DAY), follower('efe', MONDAY)],
            FOLDER + 'following.json': {'relationships_following': [
                following('ada', MONDAY - 500 * DAY), following('kitap', MONDAY - 900 * DAY),
                following('sessiz', MONDAY - 300 * DAY), following('eski.dost', MONDAY - 800 * DAY)]},
            'your_instagram_activity/likes/liked_posts.json': {'likes_media_likes': [
                liked('ada', MONDAY), liked('ada', MONDAY + DAY), labelled_like('kitap', MONDAY + 2 * DAY),
                liked('yabanci', MONDAY + 3 * DAY), {'title': '', 'string_list_data': []}]},
            'your_instagram_activity/comments/post_comments_1.json': [comment('ada', MONDAY + 3600)],
            'your_instagram_activity/story_interactions/story_likes.json': {'story_activities_story_likes': [
                {'title': 'eski.dost', 'string_list_data': [{'timestamp': MONDAY - 701 * DAY}]}]},
            'security_and_login_information/login_and_profile_creation/login_activity.json': {
                'account_history_login_history': [login(MONDAY - 100 * DAY, WINDOWS, '10.0.0.1'),
                                                  login(MONDAY - 5 * DAY, ANDROID, '10.0.0.2'),
                                                  login(MONDAY, ANDROID, '10.0.0.2')]},
            'security_and_login_information/login_and_profile_creation/password_change_activity.json': {
                'account_history_password_change_history': [{'title': '', 'string_map_data': {'Time': {'timestamp': MONDAY - 50 * DAY}}}]},
            'ads_information/instagram_ads_and_businesses/advertisers_using_your_activity_or_information.json': {
                'ig_custom_audiences_all_types': [
                    {'advertiser_name': 'Kahve Dükkanı', 'has_data_file_custom_audience': True, 'has_remarketing_custom_audience': False},
                    {'advertiser_name': 'Kitapçı', 'has_data_file_custom_audience': False, 'has_remarketing_custom_audience': True},
                    {'advertiser_name': 'Kahve Dükkanı', 'has_data_file_custom_audience': True}]},
            'ads_information/ads_and_topics/ads_viewed.json': {'impressions_history_ads_seen': [
                {'string_map_data': {'Author': {'value': 'Kitapçı'}, 'Time': {'timestamp': MONDAY}}},
                {'string_map_data': {'Author': {'value': 'Kitapçı'}, 'Time': {'timestamp': MONDAY + 1}}}]},
            'preferences/your_topics/your_topics.json': {'topics_your_topics': [
                {'string_map_data': {'Name': {'value': 'Kitaplar'}}}, {'string_map_data': {'Name': {'value': 'Kahve'}}}]},
        })

    def test_people_find_silent_follows_top_accounts_and_follow_back(self):
        token = self.full_export()
        people = self.browser.insights(token)['people']
        rows = {row['username']: row for row in people['following']}
        self.assertEqual(rows['ada']['count'], 3)
        self.assertEqual(rows['ada']['kinds'], {'likes': 2, 'comments': 1})
        self.assertEqual(rows['kitap']['count'], 1, 'owner nested in label_values')
        self.assertEqual(rows['sessiz']['count'], 0)
        self.assertIsNone(rows['sessiz']['last'])
        self.assertEqual(rows['eski.dost']['last'], MONDAY - 701 * DAY)
        self.assertTrue(rows['ada']['followsYou'])
        self.assertFalse(rows['kitap']['followsYou'])
        self.assertEqual(people['top'][0]['username'], 'ada')
        stranger = next(row for row in people['top'] if row['username'] == 'yabanci')
        self.assertFalse(stranger['youFollow'])
        self.assertEqual(people['sources'], {'likes': 5, 'comments': 1, 'stories': 1, 'saved': None})

    def test_activity_grid_uses_viewer_offset(self):
        token = self.full_export()
        utc = self.browser.insights(token)['activity']
        self.assertEqual(utc['grid'][0][9], 1, 'Monday 09:00 UTC like')
        self.assertEqual(utc['total'], 6)
        istanbul = self.browser.insights(token, -180)['activity']
        self.assertEqual(istanbul['grid'][0][12], 1)
        self.assertEqual(istanbul['grid'][0][9], 0)
        with self.assertRaises(ValueError):
            self.browser.insights(token, 5000)
        with self.assertRaises(ValueError):
            self.browser.insights(token, '0')

    def test_security_summarises_logins_devices_and_password_changes(self):
        token = self.full_export()
        security = self.browser.insights(token)['security']
        self.assertTrue(security['present'])
        self.assertEqual((security['loginCount'], security['ipCount']), (3, 2))
        self.assertEqual(security['logins'][0]['device'], 'Android 13 · samsung SM-G991B')
        devices = {row['device']: row for row in security['devices']}
        self.assertEqual(devices['Windows · Chrome']['count'], 1)
        self.assertTrue(devices['Android 13 · samsung SM-G991B']['recent'])
        self.assertFalse(devices['Windows · Chrome']['recent'])
        self.assertEqual(security['passwordChanges'], [MONDAY - 50 * DAY])

    def test_ads_list_advertisers_flags_views_and_topics(self):
        ads = self.browser.insights(self.full_export())['ads']
        self.assertEqual(ads['advertisers'], ['Kahve Dükkanı', 'Kitapçı'])
        self.assertEqual(ads['flags'], {'has_data_file_custom_audience': 2, 'has_remarketing_custom_audience': 1})
        self.assertEqual((ads['viewed'], ads['clicked']), (2, None))
        self.assertEqual(ads['topAuthors'], [{'name': 'Kitapçı', 'count': 2}])
        self.assertEqual(ads['topics'], ['Kahve', 'Kitaplar'])

    def test_years_count_activity_follows_and_shared_content(self):
        entries = {
            FOLDER + 'followers_1.json': [follower('ada', MONDAY), follower('efe', MONDAY - 365 * DAY)],
            'your_instagram_activity/likes/liked_posts.json': {'likes_media_likes': [liked('ada', MONDAY), liked('ada', MONDAY + 7200)]},
            'your_instagram_activity/media/posts_1.json': [{'media': [{'uri': 'media/posts/1.jpg', 'creation_timestamp': MONDAY}]}],
        }
        years = self.browser.insights(self.open(entries))['years']
        self.assertEqual(years['2025']['likes'], 2)
        self.assertEqual(years['2025']['newFollowers'], 1)
        self.assertEqual(years['2025']['posts'], 1)
        self.assertEqual((years['2025']['busiestMonth'], years['2025']['busiestHour'], years['2025']['activeDays']), (3, 9, 1))
        self.assertEqual(years['2024']['newFollowers'], 1)

    def test_missing_and_oversized_sources_are_reported(self):
        token = self.open({FOLDER + 'followers_1.json': [follower('ada')],
                           'your_instagram_activity/likes/liked_posts.json': {'likes_media_likes': [liked('ada', MONDAY)]}})
        with unittest.mock.patch('archive_insights.MAX_FILE', 10):
            result = self.browser.insights(token)
        self.assertEqual(result['skipped'], ['likes'])
        self.assertFalse(result['security']['present'])
        self.assertFalse(result['ads']['present'])
        self.assertEqual(result['people']['lists'], {'followers': True, 'following': False})
        self.assertEqual(result['people']['following'], [])
        with self.assertRaises(ValueError):
            self.browser.insights('wrong')

    def test_result_is_cached_and_cleared_on_close(self):
        token = self.full_export()
        with unittest.mock.patch('archive_insights.build', wraps=archive_insights.build) as build:
            self.browser.insights(token)
            self.browser.insights(token)
            self.assertEqual(build.call_count, 1)
        self.browser.close()
        self.assertIsNone(self.browser.insights_data)

    def test_helpers(self):
        self.assertEqual(target({'title': '@Ada.Lovelace'}), 'ada.lovelace')
        self.assertIsNone(target({'title': 'Bir gönderi başlığı'}))
        self.assertEqual(target({'string_map_data': {'Media Owner': {'value': 'https://www.instagram.com/_u/efe/'}}}), 'efe')
        self.assertEqual(device('Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) Instagram 300 (iPhone14,2; iOS 17_1; tr_TR)'), 'iPhone · iOS 17.1')
        self.assertEqual(device('Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605 (KHTML) Version/17 Safari/605'), 'Mac · Safari')
        self.assertIsNone(device(None))


if __name__ == '__main__':
    unittest.main()
