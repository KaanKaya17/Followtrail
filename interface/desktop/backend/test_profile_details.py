"""Scoped drawer reads through the production engine, using synthetic transport."""
import json
import tempfile
import unittest
from unittest.mock import patch
from engine import Engine
from test_scan_progress import ClockWait, PacedClient
from test_backend import profile
from network import AppFailure, parse_profile


class ProfileDetailTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-');self.addCleanup(self.home.cleanup)
        self.now=[1800000000.0]
        self.engine=Engine(self.home.name,clock=lambda:self.now[0],request_delay=lambda:8)
        self.engine.cancel=ClockWait(self.now)
        self.engine.record_profile(parse_profile(profile()),own=True)
        self.engine.data['session']={'cookies':{'ds_user_id':'100','sessionid':'SYNTHETIC'}}

    def test_member_details_are_transient_and_require_exact_account_and_identity(self):
        self.engine.data['lists']['100:followers']={'snapshots':[{'id':'s','complete':True,'finishedAt':self.now[0],
            'members':{'200':{'id':'200','username':'qa.person','name':'QA'}}}]}
        payload=profile('200',followers=0,following=3,profile_pic_url='https://cdninstagram.com/private.jpg')
        del payload['user']['media_count'];del payload['user']['is_private']
        client=PacedClient(self.engine,[payload])
        with patch.object(self.engine,'client',return_value=client),patch('engine.read_avatar') as image:
            state=self.engine.run('profile_detail',{'accountId':'100','id':'200'})
        self.assertEqual([call[0] for call in client.calls],['users/200/info/'])
        image.assert_not_called()
        result=state['profileDetail']['profile']
        self.assertEqual((result['followers'],result['following']),(0,3))
        self.assertIsNone(result['posts']);self.assertIsNone(result['private'])
        self.assertEqual(state['profileDetail']['accountId'],'100')
        self.assertNotIn('private.jpg',json.dumps(state))
        self.assertNotIn('200',self.engine.data['people']);self.assertNotIn('200',self.engine.data['history'])
        self.assertNotIn('profileDetail',self.engine.vault.load())
        self.assertIsNone(self.engine.run('state',{})['profileDetail'])
        for args in ({'accountId':'999','id':'200'},{'accountId':'100','id':'999'}):
            with patch.object(self.engine,'client') as factory:
                with self.assertRaises(AppFailure):self.engine.profile_detail(args)
                factory.assert_not_called()

    def test_saved_details_update_observation_without_resetting_pause(self):
        self.engine.record_profile(parse_profile(profile('200')))
        self.engine.data['gate']['manualOnly']=True
        self.engine.data['gate']['nextAt']=self.now[0]+8
        client=PacedClient(self.engine,[profile('200',followers=9)])
        with patch.object(self.engine,'client',return_value=client):
            state=self.engine.run('profile_detail',{'accountId':'100','id':'200','fresh':True})
        self.assertEqual(state['people'][0]['followers'],9)
        self.assertEqual(self.engine.cancel.waits,[8])
        self.assertTrue(state['gate']['manualOnly'])

    def test_cooldown_and_429_stop_without_replay(self):
        self.engine.data['gate']['blockedUntil']=self.now[0]+60
        client=PacedClient(self.engine,[profile()])
        with patch.object(self.engine,'client',return_value=client):
            with self.assertRaises(AppFailure):self.engine.run('profile_detail',{'accountId':'100','id':'100'})
            state=self.engine.snapshot()
        self.assertEqual(client.calls,[]);self.assertEqual(state['lastError']['code'],'cooldown')
        self.engine.data['gate']['blockedUntil']=0
        client=PacedClient(self.engine,[429])
        with patch.object(self.engine,'client',return_value=client):
            with self.assertRaises(AppFailure):self.engine.run('profile_detail',{'accountId':'100','id':'100'})
            state=self.engine.snapshot()
        self.assertEqual(len(client.calls),1);self.assertEqual(state['lastError']['code'],'limited')
        self.assertIsNone(state['profileDetail']);self.assertGreater(state['gate']['blockedUntil'],self.now[0])

    def test_offline_and_mismatched_response_cannot_publish_details(self):
        self.engine.offline=True
        with patch.object(self.engine,'client') as factory:
            with self.assertRaises(AppFailure):self.engine.run('profile_detail',{'accountId':'100','id':'100'})
            state=self.engine.snapshot()
            factory.assert_not_called()
        self.assertEqual(state['lastError']['code'],'offline')
        self.engine.offline=False
        client=PacedClient(self.engine,[profile('999')])
        with patch.object(self.engine,'client',return_value=client):
            with self.assertRaises(AppFailure):self.engine.run('profile_detail',{'accountId':'100','id':'100'})
            state=self.engine.snapshot()
        self.assertEqual(state['lastError']['code'],'identity');self.assertIsNone(state['profileDetail'])
