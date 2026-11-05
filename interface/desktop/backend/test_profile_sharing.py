"""One parsed profile crosses access, drawer and registration without replay."""
import copy
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch
from engine import Engine
from network import AppFailure, parse_profile
from test_backend import profile
from test_scan_progress import ClockWait, PacedClient


class StatusClient(PacedClient):
    def private_request(self, endpoint, **kwargs):
        value = super().private_request(endpoint, **kwargs)
        self.last_response = SimpleNamespace(status_code=200)
        return value


class ProfileSharingTests(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory(prefix='TrackerQA-'); self.addCleanup(self.home.cleanup)
        self.now = [1800000000.0]
        self.e = Engine(self.home.name, clock=lambda:self.now[0], request_delay=lambda:8)
        self.e.client = lambda: self.fail('No live transport is permitted in this fixture')
        self.e.cancel = ClockWait(self.now)
        self.e.record_profile(parse_profile(profile()), own=True)
        self.e.data['session'] = {'cookies':{'ds_user_id':'100','sessionid':'SYNTHETIC-SECRET'}}
        self.e.data['lists']['100:followers'] = {'snapshots':[{'id':'synthetic','complete':True,
            'finishedAt':self.now[0], 'members':{'200':{'id':'200','username':'qa.person','name':'QA'}}}]}

    def detail(self, payload):
        client = StatusClient(self.e,[payload])
        with patch.object(self.e,'client',return_value=client):
            result = self.e.run('profile_detail',{'accountId':'100','id':'200'})
        return client,result['profileDetail']['profile']

    def test_access_read_updates_saved_profile_and_drawer_reuses_it(self):
        self.e.record_profile(parse_profile(profile('200',followers=2,biography='Old')))
        self.now[0] += 1
        target = {**profile('200',followers=77,biography='New'), 'status':'ok'}
        client = StatusClient(self.e,[profile(),target])
        with patch.object(self.e,'client',return_value=client):
            self.e.run('check_access',{'id':'200'})
            observed = self.e.data['people']['200']['observedAt']
            self.now[0] += 1
            state = self.e.run('profile_detail',{'accountId':'100','id':'200'})
        self.assertEqual(len(client.calls),2)
        self.assertEqual(state['profileDetail']['profile']['followers'],77)
        self.assertEqual(self.e.data['people']['200']['bio'],'New')
        self.assertEqual(self.e.data['people']['200']['observedAt'],observed)
        check = self.e.data['access']['200']['checks'][-1]
        self.assertEqual(check['status'],'accessible'); self.assertNotIn('profile',check)

    def test_visibility_only_or_invalid_full_profile_does_not_erase_saved_fields(self):
        self.e.record_profile(parse_profile(profile('200',followers=2,biography='Old')))
        for user in ({'pk':'200','username':'qa.person'}, {'pk':'200','username':'qa.person','follower_count':'bad'}):
            self.now[0] += 60
            client = StatusClient(self.e,[profile(),{'status':'ok','user':user}])
            with patch.object(self.e,'client',return_value=client): self.e.run('check_access',{'id':'200'})
            self.assertEqual(self.e.data['access']['200']['checks'][-1]['status'],'accessible')
            self.assertEqual(self.e.data['people']['200']['followers'],2)
            self.assertEqual(self.e.data['people']['200']['bio'],'Old')

    def test_untracked_access_result_is_transient_until_explicit_save(self):
        client = StatusClient(self.e,[profile(),{**profile('200',followers=77),'status':'ok'}])
        with patch.object(self.e,'client',return_value=client): self.e.run('check_access',{'id':'200'})
        self.assertNotIn('200',self.e.data['people']); self.assertNotIn('200',self.e.data['history'])
        with patch.object(self.e,'client') as factory:
            state = self.e.run('profile_detail',{'accountId':'100','id':'200'})
            self.e.run('save_person',{'id':'200','watching':True})
            factory.assert_not_called()
        self.assertEqual(state['profileDetail']['profile']['followers'],77)
        self.assertEqual(self.e.data['people']['200']['followers'],77)

    def test_drawer_to_save_preserves_original_time_and_creates_no_network_check(self):
        client, shown = self.detail(profile('200',followers=77,biography='New'))
        checks = sum(e['type']=='check' for e in self.e.data['events'])
        events = len(self.e.data['events'])
        self.now[0] += 120
        with patch.object(self.e,'client') as factory:
            self.e.run('save_person',{'id':'200','watching':True,'note':'local'})
            factory.assert_not_called()
        saved = self.e.data['people']['200']
        self.assertEqual(len(client.calls),1)
        self.assertEqual((saved['followers'],saved['bio'],saved['observedAt']),(77,'New',shown['observedAt']))
        self.assertEqual(saved['note'],'local');self.assertTrue(saved['watching'])
        self.assertEqual(sum(e['type']=='check' for e in self.e.data['events']),checks)
        self.assertEqual(len(self.e.data['events']),events)
        self.e.data['settings'].update(automatic=True, automaticLists=False)
        with patch.object(self.e,'client') as factory:
            self.e.automatic()
            factory.assert_not_called()

    def test_explicit_refresh_expiry_and_connection_change_cannot_reuse_old_details(self):
        self.detail(profile('200',followers=7))
        client = StatusClient(self.e,[profile('200',followers=8)])
        with patch.object(self.e,'client',return_value=client):
            state = self.e.run('profile_detail',{'accountId':'100','id':'200','fresh':True})
        self.assertEqual(state['profileDetail']['profile']['followers'],8);self.assertEqual(len(client.calls),1)
        self.now[0] += 300
        _, shown = self.detail(profile('200',followers=9));self.assertEqual(shown['followers'],9)
        self.e.data['session']['cookies']['sessionid']='ANOTHER-SYNTHETIC'
        self.e.run('save_person',{'id':'200','watching':True})
        self.assertIsNone(self.e.data['people']['200'].get('followers'))

    def test_invalid_save_cannot_publish_cached_facts_and_mutating_a_returned_copy_is_local(self):
        self.detail(profile('200',followers=77))
        before = copy.deepcopy(self.e.data)
        with self.assertRaises(AppFailure): self.e.run('save_person',{'id':'200','watching':'bad'})
        self.assertNotIn('200',self.e.data['people']);self.assertEqual(before['history'],self.e.data['history'])
        facts = self.e.profile_facts.recent(self.e.data,'200',self.now[0]);facts['links'].append({'synthetic':'change'})
        self.assertEqual(self.e.profile_facts.recent(self.e.data,'200',self.now[0])['links'],[])


if __name__=='__main__':unittest.main()
