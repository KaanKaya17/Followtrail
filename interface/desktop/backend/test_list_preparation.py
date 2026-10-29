"""Automatic baseline, encrypted resume and fail-closed comparisons; offline only."""
import copy
import json
import tempfile
import unittest
from unittest.mock import patch

from accounts import Accounts
from engine import Engine
from network import AppFailure, parse_profile
from storage import protect
from test_backend import profile, page, FakeClient
from test_scan_progress import ClockWait, PacedClient
import list_preparation


class ListPreparationTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-');self.addCleanup(self.home.cleanup)
        self.now=[1800000000.0]
        self.e=Accounts(self.home.name,clock=lambda:self.now[0],request_delay=lambda:15)
        self.e.cancel=ClockWait(self.now)

    def linked(self):
        client=FakeClient([profile(followers=3,following=1)])
        client.login=lambda *_:True
        with patch('engine.new_client',return_value=client):self.e.run('login',{'username':'qa.owner','password':'SYNTHETIC'})
        self.assertTrue(self.e.data['settings']['automatic']);self.assertTrue(self.e.data['settings']['automaticLists'])

    def work(self,*responses):
        self.now[0]=max(self.now[0],self.e.data['gate'].get('nextAt',0))
        client=PacedClient(self.e,responses)
        with patch.object(self.e,'client',return_value=client):self.e.run('automatic',{})
        return client

    def test_link_schedules_baseline_and_restarts_resume_only_next_page(self):
        self.linked()
        first=self.work(profile(followers=3,following=1),page(['200','201'],'cursor-one'))
        self.assertEqual(len(first.calls),2)
        draft=self.e.data['lists']['100:followers']['draft']
        self.assertEqual(draft['pages'],1);self.assertEqual(draft['nextCursor'],'cursor-one')
        self.assertNotIn('cursor-one',json.dumps(self.e.snapshot()))
        self.e=Accounts(self.home.name,clock=lambda:self.now[0],request_delay=lambda:15);self.e.cancel=ClockWait(self.now)
        second=self.work(page(['202']))
        self.assertEqual(second.calls[0][1]['params']['max_id'],'cursor-one')
        self.assertEqual(len(second.calls),1)
        self.assertEqual(len(self.e.data['lists']['100:followers']['snapshots']),1)
        self.work(profile(followers=3,following=1),page(['300']))
        self.assertTrue(self.e.snapshot()['listPreparation']['ready'])
        self.assertIsNone(self.e.snapshot()['lists']['100:followers']['comparison'])
        self.assertEqual(self.e.data['access'],{})

    def test_rescan_same_count_waits_day_and_does_not_starve_profile(self):
        self.linked()
        self.work(profile(followers=3,following=1),page(['200','201','202']))
        self.work(profile(followers=3,following=1),page(['300']))
        self.now[0]+=61
        self.assertGreater(list_preparation.next_job(self.e.data,self.now[0])['dueAt'],self.now[0])
        self.now[0]+=86400
        self.e.data['lastAutomaticTask']='profile'
        self.e.save()
        self.work(profile(followers=3,following=1),page(['200','201','203'],'cursor-two'))
        self.assertEqual(self.e.data['lastAutomaticTask'],'lists')
        self.now[0]+=3601
        with patch.object(self.e,'refresh') as refresh:self.e.run('automatic',{})
        refresh.assert_called_once()

    def test_budget_and_rate_limit_preserve_pages_without_early_retry(self):
        self.linked();self.work(profile(followers=3,following=1),page(['200'],'cursor-one'))
        self.e.data['gate']['requestTimes']=[self.now[0]]*240;self.e.save()
        with patch.object(self.e,'client') as client:self.e.run('automatic',{})
        client.assert_not_called()
        self.assertEqual(self.e.data['lists']['100:followers']['draft']['pages'],1)
        self.now[0]+=3601
        with self.assertRaises(AppFailure):self.work(429)
        before=copy.deepcopy(self.e.data['gate'])
        with patch.object(self.e,'client') as client:self.e.run('automatic',{})
        client.assert_not_called();self.assertEqual(self.e.data['gate'],before)

    def test_cancel_pauses_and_partial_or_mismatched_lists_never_compare(self):
        self.linked();self.work(profile(followers=3,following=1),page(['200'],'cursor-one'))
        self.e.cancel.set()
        with self.assertRaises(AppFailure):self.e.scan({'direction':'followers'},one_page=True,resume=True)
        self.assertFalse(self.e.data['settings']['automaticLists'])
        self.e.cancel.clear();self.e.settings({'automaticLists':True})
        with self.assertRaises(AppFailure):self.work(page(['201']))
        record=self.e.data['lists']['100:followers']
        self.assertEqual(record['snapshots'],[]);self.assertFalse(record['draft']['complete'])
        self.assertFalse(self.e.snapshot()['listPreparation']['ready'])

    def test_expired_cursor_restarts_and_bad_cursor_loop_is_attention(self):
        self.linked();self.work(profile(followers=3,following=1),page(['200'],'cursor-one'))
        self.now[0]+=86401;self.e.data['automaticAttempts']['100']=self.now[0]
        self.e.save()
        call=self.work(profile(followers=3,following=1),page(['201'],'cursor-new'))
        self.assertNotIn('max_id',call.calls[1][1]['params'])
        with self.assertRaises(AppFailure):self.work(page(['202'],'cursor-new'))
        self.assertEqual(self.e.data['lists']['100:followers']['schedule']['error'],'pagination')
        self.assertIsNone(list_preparation.plan(self.e.data,self.now[0])[0]['dueAt'])

    def test_existing_connection_preferences_and_backup_resume_are_safe(self):
        self.linked();self.e.run('settings',{'automatic':False,'automaticLists':False})
        self.now[0]+=901
        fake=FakeClient([profile()]);fake.login=lambda *_:True
        with patch('engine.new_client',return_value=fake):self.e.run('login',{'username':'qa.owner','password':'SYNTHETIC'})
        self.assertFalse(self.e.data['settings']['automatic'])
        self.assertFalse(self.e.data['settings']['automaticLists'])
        self.e.data['settings']['automaticLists']=True
        self.e.data['lists']={}
        self.e.settings({'automatic':True})
        self.work(profile(followers=3,following=1),page(['200'],'cursor-one'))
        path=self.home.name+'/backup';self.e.export_backup(path)
        from pathlib import Path
        restored=json.loads(protect(Path(path).read_bytes(),decrypt=True))['data']
        self.assertIsNone(restored['session'])
        # Backup observations must not revive cursors tied to a removed session.
        self.assertNotIn('nextCursor',restored['lists']['100:followers']['draft'])

    def test_two_accounts_prepare_in_turn_without_switching_visible_account(self):
        self.linked();self.work(profile(followers=3,following=1),page(['200'],'cursor-one'))
        self.now[0]+=901
        client=FakeClient([profile('300',username='qa.second',followers=2,following=1)])
        client.user_id=300;client.login=lambda *_:True
        client.get_settings=lambda:{'cookies':{'ds_user_id':'300','sessionid':'SYNTHETIC-SECOND'}}
        with patch('engine.new_client',return_value=client):self.e.run('login',{'username':'qa.second','password':'SYNTHETIC'})
        first=self.work(page(['201','202']))
        self.assertTrue(first.calls[0][0].startswith('friendships/100/'))
        self.assertEqual(self.e.active_id,'300')
        second=self.work(profile('300',username='qa.second',followers=2,following=1),page(['400'],'second-cursor'))
        self.assertTrue(second.calls[-1][0].startswith('friendships/300/'))
        self.assertEqual(self.e.active_id,'300')
        self.assertEqual(list(self.e.data['lists']['300:followers']['draft']['members']),['400'])
        self.e.run('switch_account',{'id':'100'})
        self.assertEqual(set(self.e.data['lists']['100:followers']['snapshots'][-1]['members']),{'200','201','202'})
        self.assertNotIn('300:followers',self.e.data['lists'])

    def test_resume_metadata_validates_and_old_scheduling_migrates(self):
        from engine import validate_archive
        self.linked();self.work(profile(followers=3,following=1),page(['200'],'cursor-one'))
        malformed=copy.deepcopy(self.e.data)
        malformed['lists']['100:followers']['draft']['pages']=-1
        with self.assertRaises(ValueError):validate_archive(malformed)
        old=copy.deepcopy(self.e.data);old['settings'].pop('automaticLists')
        self.e.vault.save(old)
        self.e.catalog['accounts']['100']['monitor'].pop('listPlanVersion');self.e.persist_catalog()
        reopened=Accounts(self.home.name,clock=lambda:self.now[0])
        self.assertTrue(reopened.data['settings']['automaticLists'])
        self.assertEqual(reopened.catalog['accounts']['100']['monitor']['listPlanVersion'],1)

    def test_zero_member_lists_are_valid_baselines_not_missing_data(self):
        self.linked()
        self.work(profile(followers=0,following=0),page([]))
        self.work(profile(followers=0,following=0),page([]))
        state=self.e.snapshot()
        self.assertTrue(state['listPreparation']['ready'])
        self.assertEqual([d['count'] for d in state['listPreparation']['directions']],[0,0])
        self.assertIsNone(state['lists']['100:followers']['comparison'])

    def test_cancel_during_single_page_response_keeps_partial_and_stops_automatic_lists(self):
        self.linked()
        engine=self.e
        class LatePage(PacedClient):
            def private_request(self,endpoint,**kwargs):
                response=super().private_request(endpoint,**kwargs)
                if endpoint.startswith('friendships/'):
                    engine.cancel.set()
                return response
        client=LatePage(self.e,[profile(followers=3,following=1),page(['200'],'next')])
        with patch.object(self.e,'client',return_value=client),self.assertRaises(AppFailure) as result:
            self.e.run('automatic',{})
        self.assertEqual(result.exception.code,'cancelled')
        self.assertFalse(self.e.data['settings']['automaticLists'])
        self.assertEqual(self.e.data['lists']['100:followers']['draft']['pages'],1)
        self.assertFalse(self.e.data['lists']['100:followers']['draft']['complete'])


if __name__=='__main__':unittest.main()
