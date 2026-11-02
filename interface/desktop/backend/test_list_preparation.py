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
        self.e.data['gate']['requestTimes']=[self.now[0]]*480;self.e.save()
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
        self.assertEqual(reopened.catalog['accounts']['100']['monitor']['listPlanVersion'],list_preparation.PLAN_VERSION)

    def test_zero_member_lists_are_valid_baselines_not_missing_data(self):
        self.linked()
        self.work(profile(followers=0,following=0),page([]))
        self.work(profile(followers=0,following=0),page([]))
        state=self.e.snapshot()
        self.assertTrue(state['listPreparation']['ready'])
        self.assertEqual([d['count'] for d in state['listPreparation']['directions']],[0,0])
        self.assertIsNone(state['lists']['100:followers']['comparison'])

    def test_manual_recheck_resumes_current_pages_instead_of_erasing_them(self):
        self.linked();self.work(profile(followers=3,following=1),page(['200','201'],'cursor-one'))
        original=self.e.data['lists']['100:followers']['draft']['id']
        self.assertTrue(self.e.snapshot()['lists']['100:followers']['canResume'])
        client=PacedClient(self.e,[page(['202'])])
        with patch.object(self.e,'client',return_value=client):self.e.run('scan',{'direction':'followers'})
        self.assertEqual(client.calls[0][1]['params']['max_id'],'cursor-one')
        self.assertEqual(len(client.calls),1)
        completed=self.e.data['lists']['100:followers']['snapshots'][-1]
        self.assertEqual(completed['id'],original);self.assertEqual(len(completed['members']),3)

    def test_fresh_failed_scans_keep_prior_partial_visible_until_full_completion(self):
        self.linked()
        with self.assertRaises(AppFailure):self.work(profile(followers=3),page(['200','201']))
        key='100:followers';partial=copy.deepcopy(self.e.data['lists'][key]['draft'])
        self.now[0]+=1800
        client=PacedClient(self.e,[AppFailure('network','Synthetic failure')])
        with patch.object(self.e,'client',return_value=client),self.assertRaises(AppFailure):self.e.run('scan',{'direction':'followers'})
        state=self.e.snapshot()['lists'][key]
        self.assertEqual(state['retainedPartial']['members'],partial['members'])
        self.assertEqual(state['draft']['members'],{})
        self.assertNotIn('nextCursor',state['retainedPartial'])
        reopened=Accounts(self.home.name,clock=lambda:self.now[0],request_delay=lambda:15)
        self.assertEqual(reopened.snapshot()['lists'][key]['retainedPartial'],state['retainedPartial'])
        client=PacedClient(self.e,[AppFailure('network','Another synthetic failure')])
        with patch.object(self.e,'client',return_value=client),self.assertRaises(AppFailure):self.e.run('scan',{'direction':'followers'})
        self.assertEqual(self.e.snapshot()['lists'][key]['retainedPartial'],state['retainedPartial'])
        from engine import validate_archive
        malformed=copy.deepcopy(self.e.data)
        malformed['lists'][key]['retainedPartial']['ownerId']='999'
        with self.assertRaises(ValueError):validate_archive(malformed)
        client=PacedClient(self.e,[profile(followers=3),page(['200','202','203'])])
        with patch.object(self.e,'client',return_value=client):self.e.run('scan',{'direction':'followers'})
        record=self.e.data['lists'][key]
        self.assertNotIn('retainedPartial',record)
        self.assertEqual(set(record['snapshots'][-1]['members']),{'200','202','203'})
        self.assertIsNone(self.e.snapshot()['lists'][key]['comparison'])

    def test_profile_count_changes_in_both_directions_queue_scan_but_unchanged_counts_keep_daily_check(self):
        self.linked()
        self.work(profile(followers=3,following=1),page(['200','201','202']))
        self.work(profile(followers=3,following=1),page(['300']))
        self.now[0]+=61
        def plan():return list_preparation.plan(self.e.data,self.now[0])
        original=plan()
        self.assertTrue(all(p['dueAt']>self.now[0] for p in original))
        self.e.data['account']['followers']=4
        self.e.data['account']['observedAt']=self.now[0]
        changed=plan();self.assertLessEqual(changed[0]['dueAt'],self.now[0]);self.assertGreater(changed[1]['dueAt'],self.now[0])
        self.e.data['account']['followers']=2
        self.assertLessEqual(plan()[0]['dueAt'],self.now[0])
        self.e.data['account']['followers']=3
        self.assertEqual(plan(),original)

    def test_legacy_empty_end_cursor_reopens_without_losing_partial_list_or_gate(self):
        self.linked()
        with self.assertRaises(AppFailure):
            self.work(profile(followers=3),page(['200','201']))
        key='100:followers'
        expected=copy.deepcopy(self.e.data['lists'][key])
        gate=copy.deepcopy(self.e.data['gate'])
        legacy=copy.deepcopy(self.e.data)
        legacy['lists'][key]['draft']['nextCursor']=''
        self.e.vault.save(legacy)
        with patch('requests.Session.send') as send:
            reopened=Accounts(self.home.name,clock=lambda:self.now[0])
        send.assert_not_called()
        self.assertEqual(reopened.data['lists'][key],expected)
        self.assertEqual(reopened.data['gate'],gate)
        self.assertIsNone(reopened.snapshot()['lists'][key]['comparison'])
        malformed=copy.deepcopy(legacy)
        malformed['lists'][key]['draft']['nextCursor']=42
        self.e.vault.save(malformed)
        with self.assertRaises(ValueError):Accounts(self.home.name,clock=lambda:self.now[0])

    def watched(self):
        self.e.record_profile(parse_profile(profile('200',followers=1,following=1)))
        self.e.run('save_person',{'id':'200','watching':True})

    def test_automatic_watched_baseline_change_names_and_unwatch(self):
        self.linked()
        self.work(profile(followers=3,following=1),page(['200','201','202']))
        self.work(profile(followers=3,following=1),page(['300']))
        self.watched()
        client=self.work(profile('200',followers=1,following=1),page(['301']))
        self.assertEqual(client.calls[-1][0],'friendships/200/followers/')
        self.work(profile('200',followers=1,following=1),page(['400']))
        self.assertFalse(any(c['category']=='lists' for log in self.e.data['notificationLog'] for c in log['changes']))
        self.now[0]+=901
        self.e.record_profile(parse_profile(profile('200',followers=2,following=1)))
        self.e.save()
        self.work(profile('200',followers=2,following=1),page(['301','302']))
        change=self.e.data['notificationLog'][0]['changes'][0]
        self.assertEqual(change['category'],'lists')
        self.assertEqual(change['after']['membersAdded'],[{'id':'302','username':'qa.302','name':'QA 302'}])
        self.assertEqual(change['after']['membersRemoved'],[])
        # Identity evidence is historical, independent of the rotating scans.
        self.e.data['lists']['200:followers']['snapshots']=[];self.e.save()
        reopened=Accounts(self.home.name,clock=lambda:self.now[0])
        self.assertEqual(reopened.data['notificationLog'][0]['changes'][0]['after'],change['after'])
        reopened.run('save_person',{'id':'200','watching':False})
        self.assertNotEqual(list_preparation.next_job(reopened.data,self.now[0])['ownerId'],'200')

    def test_equal_count_daily_scan_records_added_and_missing_identities(self):
        self.linked()
        self.work(profile(followers=3,following=1),page(['200','201','202']))
        self.work(profile(followers=3,following=1),page(['300']))
        self.now[0]+=86401
        self.e.data['lastAutomaticTask']='profile';self.e.save()
        self.work(profile(followers=3,following=1),page(['200','201','203']))
        event=self.e.data['events'][0]
        self.assertEqual(event['new']['membersAdded'][0]['id'],'203')
        self.assertEqual(event['new']['membersRemoved'][0]['id'],'202')
        from engine import validate_archive
        malformed=copy.deepcopy(self.e.data)
        malformed['events'][0]['new']['added']=2
        with self.assertRaises(ValueError):validate_archive(malformed)
        malformed=copy.deepcopy(self.e.data)
        malformed['notificationLog'][0]['changes'][0]['after']['membersRemoved'][0]['id']='0'
        with self.assertRaises(ValueError):validate_archive(malformed)

    def test_watched_partial_list_and_limit_keep_baseline_and_gate(self):
        self.linked();self.e.settings({'automaticLists':False})
        self.watched()
        self.e.settings({'automaticLists':True})
        # Owner baselines exist; only the watched profile is due.
        for direction,ids in [('followers',['200','201','202']),('following',['300'])]:
            client=PacedClient(self.e,[profile(followers=3,following=1),page(ids)])
            with patch.object(self.e,'client',return_value=client):self.e.run('scan',{'direction':direction})
        self.work(profile('200',followers=2,following=1),page(['301'],'qa-next'))
        with self.assertRaises(AppFailure):self.work(429)
        before=copy.deepcopy(self.e.data['gate'])
        with patch.object(self.e,'client') as client:self.e.run('automatic',{})
        client.assert_not_called();self.assertEqual(self.e.data['gate'],before)
        record=self.e.data['lists']['200:followers']
        self.assertEqual(record['snapshots'],[])
        self.assertEqual(len(record['draft']['members']),1)

    def test_one_minute_profile_changes_are_committed_by_automatic_without_refresh_command(self):
        self.linked();self.e.settings({'automaticLists':False,'interval':1})
        self.watched();self.now[0]+=60
        self.work(profile(followers=3,following=1))
        self.work(profile('200',followers=2,following=1))
        state=self.e.snapshot()
        self.assertEqual(next(p for p in state['people'] if p['id']=='200')['followers'],2)
        self.assertTrue(any(c['category']=='followers' for log in state['notificationLog'] for c in log['changes']))

    def test_old_catalog_plan_is_upgraded_for_watched_baselines(self):
        self.linked()
        self.work(profile(followers=3,following=1),page(['200','201','202']))
        self.work(profile(followers=3,following=1),page(['300']))
        self.watched()
        self.e.catalog['accounts']['100']['monitor'].update(listPlanVersion=1,nextAt=self.now[0]+86400)
        self.e.persist_catalog()
        reopened=Accounts(self.home.name,clock=lambda:self.now[0])
        self.assertEqual(reopened.active_id,'100')
        self.assertEqual(reopened.catalog['accounts']['100']['monitor']['listPlanVersion'],list_preparation.PLAN_VERSION)
        self.assertEqual(reopened.background_status()['nextAt'],max(self.now[0],reopened.data['gate']['nextAt']))

    def test_cancel_watched_scan_pauses_automatic_lists_without_erasing_its_page(self):
        self.linked();self.watched();engine=self.e
        class CancelPage(PacedClient):
            def private_request(self,endpoint,**kwargs):
                response=super().private_request(endpoint,**kwargs)
                if endpoint.startswith('friendships/'):engine.cancel.set()
                return response
        client=CancelPage(self.e,[profile('200',followers=2,following=1),page(['301'],'qa-next')])
        with patch.object(self.e,'client',return_value=client),self.assertRaises(AppFailure):
            self.e.run('scan',{'id':'200','direction':'followers'})
        self.e.cancel.clear()
        self.assertFalse(self.e.data['settings']['automaticLists'])
        self.assertEqual(self.e.data['lists']['200:followers']['draft']['pages'],1)
        self.assertIsNone(list_preparation.next_job(self.e.data,self.now[0]))

    def test_82_expected_76_returned_is_partial_for_owner_and_watched_profile_until_fresh_complete_scan(self):
        self.linked();self.watched()
        ids=[str(i) for i in range(1000,1082)]
        for identity in ('100','200'):
            with self.subTest(identity=identity):
                def scan(*responses):
                    client=PacedClient(self.e,responses)
                    with patch.object(self.e,'client',return_value=client):
                        self.e.run('scan',{'id':identity,'direction':'followers'})
                    return client
                scan(profile(identity,followers=82),page(ids))
                key=identity+':followers'
                baseline=copy.deepcopy(self.e.data['lists'][key]['snapshots'][-1])
                with self.assertRaises(AppFailure) as result:
                    scan(profile(identity,followers=82),page(ids[:50],'qa-next'),page(ids[50:76]))
                self.assertEqual(result.exception.code,'list_count')
                record=self.e.data['lists'][key]
                self.assertEqual(record['snapshots'],[baseline])
                self.assertEqual(len(record['draft']['members']),76)
                self.assertEqual(record['draft']['expected'],82)
                self.assertFalse(record['draft']['complete'])
                self.assertIsNone(record['draft']['nextCursor'])
                self.assertEqual(record['schedule']['error'],'list_count')
                self.assertEqual(record['schedule']['after'],self.now[0]+1800)
                self.assertIsNone(self.e.snapshot()['lists'][key]['comparison'])
                # Incomplete reads never claim the six missing identities left.
                self.assertFalse(any(c['category']=='lists' for entry in self.e.data['notificationLog'] for c in entry['changes']))
                # Reopening a count-mismatched scan must retain the baseline,
                # partial members and retry deadline without another request.
                self.e=Accounts(self.home.name,clock=lambda:self.now[0],request_delay=lambda:15)
                self.e.cancel=ClockWait(self.now)
                self.assertEqual(self.e.data['lists'][key],record)
                self.now[0]+=1800
                # The next complete scan owns a fresh snapshot, not a union of
                # identities observed during incompatible partial attempts.
                scan(profile(identity,followers=82),page(ids[:76],'qa-last'),page(ids[76:]))
                self.assertNotIn('draft',self.e.data['lists'][key])
                self.assertEqual(len(self.e.data['lists'][key]['snapshots'][-1]['members']),82)
                self.assertEqual(self.e.snapshot()['lists'][key]['comparison']['removed'],[])

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
