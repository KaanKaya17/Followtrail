"""Offline evidence, queue and false-positive regressions. No account mutations."""
import copy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import requests
from instagrapi.exceptions import ClientNotFoundError, ChallengeRequired, ClientUnauthorizedError, ClientForbiddenError
from engine import Engine
from network import AppFailure, parse_profile
from storage import StorageFailure
import access_checks
from test_backend import profile, page, FakeClient


def error(code=404, kind=ClientNotFoundError):
    response=requests.Response();response.status_code=code;response._content=b'{"status":"fail"}'
    return kind('Synthetic response',response=response)


class ProbeClient(FakeClient):
    def private_request(self,endpoint,**kwargs):
        try:
            result=super().private_request(endpoint,**kwargs)
            self.last_response=requests.Response();self.last_response.status_code=200
            return result
        except Exception as exc:
            self.last_response=getattr(exc,'response',None)
            raise


class AccessTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.engine=Engine(self.home.name,clock=lambda:1800000000)
        self.e=self.engine
        self.e.record_profile(parse_profile(profile()),own=True)
        self.e.data['session']={'cookies':{'ds_user_id':'100','sessionid':'SYNTHETIC-SECRET'}}
        self.e.record_profile(parse_profile(profile('200')))
        self.e.save()

    def tearDown(self):self.home.cleanup()

    def scan(self,ids,expected=None,direction='followers',owner=None):
        fake=ProbeClient([profile(followers=len(ids) if expected is None else expected,following=len(ids)),page(ids)])
        with patch.object(self.e,'client',return_value=fake):
            self.e.run('scan',{'direction':direction,**({'id':owner} if owner else {})})

    def missing(self):
        self.scan(['200','201']);self.scan(['201'])
        return self.e.data['access']['200']

    def check(self,outcome,health=None):
        client=ProbeClient([health or profile(),outcome])
        with patch.object(self.e,'client',return_value=client):self.e.run('check_access',{'id':'200'})
        return self.e.data['access']['200'],client

    def test_only_complete_own_followers_can_queue_a_saved_missing_identity(self):
        self.scan(['200','201'])
        self.assertEqual(self.e.data['access'],{})
        self.scan(['201'])
        case=self.e.data['access']['200']
        self.assertEqual(case['stage'],'pending')
        self.assertEqual(case['absence']['ownerId'],'100')
        self.assertEqual(case['checks'],[])
        self.assertFalse(any(e.get('topic')=='access' and e['type']=='profile' for e in self.e.data['events']))

    def test_partial_scan_never_creates_a_suspicion(self):
        self.scan(['200','201'])
        with self.assertRaises(AppFailure):self.scan(['201'],expected=2)
        self.assertEqual(self.e.data['access'],{})

    def test_following_direction_and_unsaved_members_do_not_queue(self):
        self.scan(['200','201'],direction='following');self.scan(['201'],direction='following')
        self.assertEqual(self.e.data['access'],{})
        self.scan(['201','202']);self.scan(['201'])
        self.assertEqual(self.e.data['access'],{})

    def test_missing_id_and_fresh_healthy_session_make_suspicion_not_certainty(self):
        self.missing();case,client=self.check(error())
        result=case['checks'][-1]
        self.assertEqual(result['status'],'suspected')
        self.assertEqual(result['ownHTTP'],200);self.assertEqual(result['targetHTTP'],404)
        self.assertEqual([c[0] for c in client.calls],['users/100/info/','users/200/info/'])
        alerts=[e for e in self.e.data['events'] if e.get('topic')=='access' and e['type']=='profile']
        self.assertEqual(len(alerts),1)
        self.assertIn('Engellenme şüphesi',alerts[0]['title'])
        self.assertIn('Hesap kapanmış da olabilir',alerts[0]['title'])
        self.assertNotIn('engelledi',alerts[0]['title'])

    def test_repeated_absence_does_not_repeat_the_alert(self):
        self.missing();self.check(error());self.check(error())
        alerts=[e for e in self.e.data['events'] if e.get('topic')=='access' and e['type']=='profile']
        self.assertEqual(len(alerts),1)

    def test_unavailable_without_list_absence_is_not_a_block_suspicion(self):
        case,_=self.check(error())
        self.assertEqual(case['checks'][-1]['status'],'unavailable')
        self.assertFalse(case['alertRecorded'])

    def test_username_change_is_visible_by_stable_id_even_without_counts(self):
        self.missing()
        case,_=self.check({'status':'ok','user':{'pk':'200','username':'qa.renamed'}})
        result=case['checks'][-1]
        self.assertEqual(result['status'],'accessible');self.assertTrue(result['usernameChanged'])
        self.assertFalse(case['alertRecorded'])

    def test_wrong_identity_and_malformed_payload_are_unknown(self):
        self.missing()
        for payload in ({'status':'ok','user':{'pk':'201','username':'qa.other'}},{'status':'ok','user':{}},{'status':'fail'}):
            case,_=self.check(payload)
            self.assertEqual(case['checks'][-1]['status'],'unknown')
            self.assertFalse(case['alertRecorded'])

    def test_you_blocked_direction_is_not_reversed(self):
        self.missing()
        case,_=self.check({'status':'ok','user':{'pk':'200','username':'qa.person','friendship_status':{'blocking':True}}})
        self.assertEqual(case['checks'][-1]['status'],'you_blocked')
        self.assertFalse(case['alertRecorded'])

    def test_unknown_blocked_by_wire_field_is_not_assumed_authoritative(self):
        result=access_checks.parse_visibility({'status':'ok','user':{'pk':'200','username':'qa.person','blocked_by':True}},'200')
        self.assertEqual(result['status'],'accessible')

    def test_403_timeout_and_5xx_never_create_suspicion(self):
        self.missing()
        for exc in (error(403,ClientForbiddenError),error(500,ClientNotFoundError),requests.ReadTimeout()):
            case,_=self.check(exc)
            self.assertEqual(case['checks'][-1]['status'],'unknown')
            self.assertFalse(case['alertRecorded'])

    def test_failed_own_health_does_not_probe_target(self):
        self.missing()
        client=ProbeClient([error(401,ClientUnauthorizedError)])
        with patch.object(self.e,'client',return_value=client),self.assertRaises(AppFailure):
            self.e.run('check_access',{'id':'200'})
        self.assertEqual(len(client.calls),1)
        self.assertEqual(self.e.data['access']['200']['checks'][-1]['status'],'unknown')
        self.assertTrue(self.e.data['gate']['manualOnly'])

    def test_target_challenge_and_429_interrupt_without_suspicion(self):
        self.missing()
        for exc in (ChallengeRequired('Synthetic masked challenge'),AppFailure('limited','Synthetic 429')):
            with self.subTest(exc=type(exc).__name__),self.assertRaises(AppFailure):self.check(exc)
            case=self.e.data['access']['200']
            self.assertEqual(case['checks'][-1]['status'],'unknown')
            self.assertFalse(case['alertRecorded']);self.assertEqual(case['stage'],'done')

    def test_cancellation_arriving_with_target_reply_does_not_emit_suspicion(self):
        self.missing()
        with patch.object(self.e,'client',return_value=ProbeClient([profile()])):
            def cancelled(*args):
                self.e.cancel.set();return {'status':'unavailable','reason':'profile_not_found','targetHTTP':404}
            with patch('access_checks.probe',side_effect=cancelled),self.assertRaises(AppFailure):
                self.e.run('check_access',{'id':'200'})
        self.assertFalse(self.e.data['access']['200']['alertRecorded'])
        self.assertEqual(self.e.data['access']['200']['checks'][-1]['status'],'unknown')

    def test_removed_saved_person_is_no_longer_automatically_probed(self):
        self.missing();self.e.run('remove_person',{'id':'200'})
        self.e.settings({'automatic':True,'investigateMissing':True})
        with patch.object(self.e,'check_access') as check,patch.object(self.e,'refresh'):
            self.e.automatic();check.assert_not_called()
        self.assertEqual(self.e.monitor_status()['accessPending'],0)

    def test_cancel_between_own_health_and_target_sends_no_target_request(self):
        self.missing();client=ProbeClient([])
        def health(*args,**kwargs):
            client.last_response=requests.Response();client.last_response.status_code=200
            self.e.cancel.set();return parse_profile(profile())
        with patch.object(self.e,'client',return_value=client),patch.object(self.e,'read_profile',side_effect=health),self.assertRaises(AppFailure):
            self.e.run('check_access',{'id':'200'})
        self.assertEqual(client.calls,[])
        self.assertFalse(self.e.data['access']['200']['alertRecorded'])

    def test_return_to_list_clears_absence_and_allows_a_new_episode(self):
        self.missing();self.check(error())
        event=next(e for e in self.e.data['events'] if e.get('topic')=='access' and e['type']=='profile')
        old_evidence=copy.deepcopy(event['new']['absence'])
        self.scan(['200','201'])
        case=self.e.data['access']['200'];self.assertIsNone(case['absence'])
        self.assertEqual(case['checks'][-1]['status'],'present')
        self.assertIsNotNone(old_evidence)
        self.assertEqual(event['new']['absence'],old_evidence)
        self.scan(['201']);self.check(error())
        alerts=[e for e in self.e.data['events'] if e.get('topic')=='access' and e['type']=='profile']
        self.assertEqual(len(alerts),2)

    def test_count_drop_requires_opt_in_and_baseline_and_only_queues_a_scan(self):
        self.scan(['200','201'])
        self.e.record_profile(parse_profile(profile(followers=1)),own=True)
        self.assertEqual(self.e.data['accessPlan']['status'],'idle')
        self.e.settings({'investigateMissing':True})
        self.e.record_profile(parse_profile(profile(followers=0)),own=True)
        self.assertEqual(self.e.data['accessPlan']['status'],'pending')
        self.assertEqual(self.e.data['access'],{})
        self.e.data['lists'].clear()
        self.e.record_profile(parse_profile(profile(followers=2)),own=True)
        self.e.record_profile(parse_profile(profile(followers=1)),own=True)
        self.assertEqual(self.e.data['accessPlan']['status'],'baseline_needed')

    def test_access_jobs_alternate_with_profiles_and_failed_scan_is_not_retried(self):
        self.missing();self.e.settings({'automatic':True,'investigateMissing':True,'interval':1})
        with patch.object(self.e,'check_access') as check,patch.object(self.e,'refresh') as refresh:
            self.e.automatic();check.assert_called_once()
            self.e.clock=lambda:1800000061;self.e.automatic();refresh.assert_called_once()
        self.e.data['accessPlan']={'status':'pending'}
        self.e.clock=lambda:1800000122
        with patch.object(self.e,'scan',side_effect=AppFailure('connection','Synthetic failure')) as scan:
            with self.assertRaises(AppFailure):self.e.automatic()
            self.assertEqual(self.e.data['accessPlan']['status'],'needs_manual_scan')
            self.e.clock=lambda:1800000183
            with patch.object(self.e,'check_access'),patch.object(self.e,'refresh'):self.e.automatic()
            self.assertEqual(scan.call_count,1)

    def test_production_automatic_pipeline_count_drop_scan_identity_probe_and_alert(self):
        self.scan(['200','201'])
        self.e.settings({'automatic':True,'investigateMissing':True,'interval':1})
        observations=[([profile(followers=1)],'pending'),
                      ([profile(followers=1),page(['201'])],'idle'),
                      ([profile(followers=1),error()],'idle')]
        for index,(responses,plan) in enumerate(observations):
            self.e.clock=lambda index=index:1800000000+61*(index+1)
            client=ProbeClient(responses)
            with patch.object(self.e,'client',return_value=client):self.e.run('automatic',{})
            self.assertEqual(self.e.data['accessPlan']['status'],plan)
        case=self.e.data['access']['200']
        self.assertEqual(case['checks'][-1]['status'],'suspected')
        self.assertEqual(case['stage'],'done')
        alerts=[e for e in self.e.data['events'] if e.get('topic')=='access' and e['type']=='profile']
        self.assertEqual(len(alerts),1)
        self.assertIsNotNone(alerts[0]['new']['absence'])

    def test_old_vault_migration_backup_and_interrupted_probe_recovery(self):
        old=copy.deepcopy(self.e.data)
        for key in ('access','accessPlan','lastAutomaticTask'):old.pop(key)
        old['settings'].pop('investigateMissing');self.e.vault.save(old)
        reopened=Engine(self.home.name)
        self.assertEqual(reopened.data['access'],{})
        self.assertFalse(reopened.data['settings']['investigateMissing'])
        self.missing();self.e.data['access']['200']['stage']='checking';self.e.save()
        reopened=Engine(self.home.name)
        self.assertEqual(reopened.data['access']['200']['checks'][-1]['reason'],'interrupted')
        self.assertEqual(reopened.data['access']['200']['stage'],'done')
        backup=Path(self.home.name)/'qa.trackerbackup';reopened.export_backup(backup);reopened.import_backup(backup)
        self.assertIsNone(reopened.data['session']);self.assertIn('200',reopened.data['access'])

    def test_qa_mode_blocks_access_before_any_session_or_network(self):
        self.e.offline=True
        with patch.object(self.e,'client') as client,self.assertRaises(AppFailure):
            self.e.run('check_access',{'id':'200'})
        client.assert_not_called()

    def test_storage_error_rolls_back_through_the_same_old_vault_migration_boundary(self):
        old=copy.deepcopy(self.e.data)
        for key in ('access','accessPlan','lastAutomaticTask'):old.pop(key)
        old['settings'].pop('investigateMissing');self.e.vault.save(old)
        with patch.object(self.e.vault,'save',side_effect=StorageFailure('Synthetic disk failure')),self.assertRaises(AppFailure):
            self.e.run('settings',{'investigateMissing':True})
        state=self.e.snapshot()
        self.assertEqual(state['access'],[])
        self.assertFalse(state['settings']['investigateMissing'])


if __name__=='__main__':unittest.main(verbosity=2)
