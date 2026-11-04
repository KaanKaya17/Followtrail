import copy
import tempfile
import unittest
from unittest.mock import patch
from engine import Engine
from network import AppFailure, parse_profile
from test_backend import FakeClient, profile, page
from test_scan_progress import PacedClient, ClockWait
import profile_search
from accounts import Accounts

class ProfileSearchTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-');self.addCleanup(self.home.cleanup)
        self.now=[1800000000.0]
        self.e=Engine(self.home.name,clock=lambda:self.now[0],request_delay=lambda:15)
        self.e.cancel=ClockWait(self.now)
        self.e.record_profile(parse_profile(profile()),own=True)
        self.e.data['session']={'cookies':{'ds_user_id':'100','sessionid':'SYNTHETIC-SECRET'}}
        self.e.save()

    def test_search_by_display_name_is_one_read_and_does_not_add_or_change_people(self):
        people=copy.deepcopy(self.e.data['people'])
        client=PacedClient(self.e,[page(['200','201'])])
        with patch.object(self.e,'client',return_value=client):state=self.e.run('search_people',{'query':'İpek Kaya'})
        self.assertEqual(client.calls,[('users/search/',{'params':{'query':'İpek Kaya','count':20}})])
        self.assertEqual(len(state['searchResults']['users']),2)
        self.assertEqual(self.e.data['people'],people)
        self.assertNotIn('SYNTHETIC-SECRET',str(state))
        self.assertNotIn('searchResults',self.e.vault.load())
        self.assertIsNone(self.e.run('settings',{'compact':True})['searchResults'])

    def test_selected_search_identity_survives_username_change(self):
        client=PacedClient(self.e,[profile('200',username='renamed.person')])
        with patch.object(self.e,'client',return_value=client):self.e.run('add_person',{'id':'200','username':'old.person'})
        self.assertEqual(client.calls[0][0],'users/200/info/')
        self.assertEqual(self.e.data['people']['200']['username'],'renamed.person')

    def test_search_thumbnail_sources_stay_private_and_use_only_meta_cdn(self):
        payload=page(['200','201'])
        payload['users'][0]['profile_pic_url']='https://scontent.cdninstagram.com/avatar.jpg'
        payload['users'][1]['profile_pic_url']='https://cdninstagram.com.evil.test/avatar.jpg'
        with patch.object(self.e,'client',return_value=PacedClient(self.e,[payload])):
            state=self.e.run('search_people',{'query':'deniz'})
        token=state['searchResults']['token']
        self.assertEqual(self.e.search_cache.picture('100',token,'200')[1]['200'],'https://scontent.cdninstagram.com/avatar.jpg')
        self.assertIsNone(self.e.search_cache.picture('100',token,'201'))
        self.assertNotIn('avatar.jpg',str(state))
        self.assertEqual(len(state['searchResults']['token']),32)
        self.assertNotIn('search_pictures',self.e.vault.load())

    def test_catalog_projects_search_results_only_for_the_active_context(self):
        account=Accounts(self.home.name,clock=lambda:self.now[0],request_delay=lambda:15)
        account.cancel=ClockWait(self.now)
        with patch.object(account,'client',return_value=PacedClient(account,[page(['200'])])):
            state=account.run('search_people',{'query':'deniz'})
        self.assertEqual(state['searchResults']['users'][0]['id'],'200')
        account.run('switch_account',{'id':'100'})
        self.assertIsNone(account.snapshot()['searchResults'])

    def test_links_at_names_and_shared_query_are_normalized_before_transport(self):
        for value in ['@QA.Person','https://www.instagram.com/QA.Person/','https://instagram.com/QA.Person?igsh=abc']:
            self.assertEqual(profile_search.username(value),'qa.person')
        for value in ['https://instagram.com.evil/qa.person/','https://u@instagram.com/qa.person/','https://instagram.com/p/','https://instagram.com/a/b/','https://instagram.com/%61/']:
            with self.subTest(value=value),self.assertRaises(AppFailure):profile_search.username(value)
        client=PacedClient(self.e,[profile('200',username='qa.person')])
        with patch.object(self.e,'client',return_value=client):self.e.run('add_person',{'username':'https://instagram.com/QA.Person/?igsh=abc'})
        self.assertEqual(client.calls[0][0],'users/qa.person/usernameinfo/')

    def test_search_limit_invalid_input_and_rate_limit_do_not_create_people_or_replay(self):
        for value in ['', 'a', 'x'*101, 'a\nb',None]:
            with self.subTest(query=value),patch.object(self.e,'client') as client,self.assertRaises(AppFailure):
                self.e.run('search_people',{'query':value})
            client.assert_not_called()
        self.assertEqual(len(profile_search.results(page([str(n) for n in range(200,230)]))),20)
        with self.assertRaises(AppFailure):profile_search.results(page(['0']))
        client=PacedClient(self.e,[429])
        with patch.object(self.e,'client',return_value=client),self.assertRaises(AppFailure):self.e.run('search_people',{'query':'deniz'})
        self.assertEqual(len(client.calls),1);self.assertEqual(self.e.data['people'],{})
        self.assertIsNone(self.e.snapshot()['searchResults'])

if __name__=='__main__':unittest.main()
