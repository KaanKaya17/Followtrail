import tempfile
import unittest
from engine import Engine
from network import parse_profile
from test_backend import profile
import profile_links

class ProfileLinksTests(unittest.TestCase):
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
