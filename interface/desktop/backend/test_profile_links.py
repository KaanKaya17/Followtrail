import tempfile
import unittest
from engine import Engine
from network import parse_profile
from test_backend import profile
import profile_links

class ProfileLinksTests(unittest.TestCase):
    def test_threads_badge_uses_only_the_reported_handle_and_explicit_links_take_priority(self):
        user={'username':'ig.name','show_text_post_app_badge':True,'text_post_app_badge_label':'threads.name'}
        self.assertEqual(profile_links.parse(user),[{'url':'https://www.threads.com/@threads.name'}])
        user['bio_links']=[{'url':'https://threads.net/@explicit.name?from=ig'}]
        self.assertEqual(profile_links.parse(user),[{'url':'https://threads.net/@explicit.name?from=ig'}])
        for enabled in (False,None,'true'):
            self.assertEqual(profile_links.parse({'username':'ig.name','show_text_post_app_badge':enabled,'text_post_app_badge_label':'ig.name'}),[])
        for label in (None,[],42,'','two words','https://threads.com/@name','x/../other','a'*31):
            self.assertEqual(profile_links.parse({'username':'ig.name','show_text_post_app_badge':True,'text_post_app_badge_label':label}),[])
        self.assertEqual(profile_links.parse({'username':'ig.name','show_text_post_app_badge':True}),[])
        self.assertEqual(profile_links.parse({'username':'ig.name','text_post_app_badge_label':'ig.name'}),[])

    def test_badge_survives_five_websites_and_encrypted_profile_roundtrip(self):
        user=profile()['user']
        user.update(show_text_post_app_badge=True,text_post_app_badge_label='@badge.name',
                    bio_links=[{'url':f'https://example.com/{i}'} for i in range(5)])
        parsed=parse_profile({'user':user})
        self.assertEqual(len(parsed['links']),6)
        self.assertEqual(parsed['links'][-1],{'url':'https://www.threads.com/@badge.name'})
        self.assertTrue(profile_links.validate(parsed['links']))
        with tempfile.TemporaryDirectory(prefix='TrackerQA-') as home:
            engine=Engine(home);engine.record_profile(parsed,own=True);engine.save()
            self.assertEqual(Engine(home).snapshot()['account']['links'],parsed['links'])

    def test_profile_links_are_optional_bounded_and_backward_compatible(self):
        user=profile()['user'];user['external_url']='https://portfolio.example/about'
        user['bio_links']=[{'url':'javascript:alert(1)'},{'url':'https://www.youtube.com/@qa'},{'url':'https://portfolio.example/about'},None]
        parsed=parse_profile({'user':user});self.assertEqual(parsed['links'],[{'url':'https://www.youtube.com/@qa'},{'url':'https://portfolio.example/about'}])
        self.assertTrue(profile_links.validate(parsed['links']))
        self.assertEqual(profile_links.parse({'bio_links':'invalid'}),[])
        self.assertEqual(len(profile_links.parse({'bio_links':[{'url':f'https://example.com/{i}'} for i in range(8)]})),5)

    def test_old_vault_without_links_still_opens_and_new_links_roundtrip(self):
        with tempfile.TemporaryDirectory(prefix='TrackerQA-') as home:
            engine=Engine(home);old=parse_profile(profile());old.pop('links');engine.record_profile(old,own=True);engine.save()
            self.assertIsNone(Engine(home).snapshot()['account']['links'])
            current=parse_profile(profile(external_url='https://threads.net/@linked?source=profile'))
            engine.record_profile(current,own=True);engine.save()
            self.assertEqual(Engine(home).snapshot()['account']['links'],[{'url':'https://threads.net/@linked?source=profile'}])
