"""Offline production-boundary tests. Never use a real account or session."""
import copy
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import threading
import time
import unittest
from unittest.mock import patch, Mock

import requests
from engine import Engine, blank, compare
from network import AppFailure, new_client, close_client, parse_page, parse_profile, avatar_url, read_avatar
from instagrapi.mixins.auth import LoginMixin
from instagrapi.mixins.challenge import ChallengeChoice
from instagrapi.exceptions import TwoFactorRequired
from PIL import Image
from storage import Vault, StorageFailure, protect


def profile(identity='100', followers=2, following=1, **fields):
    return {'user': {'pk': identity, 'username': 'qa.owner' if identity=='100' else 'qa.person',
                     'full_name': 'QA Person', 'follower_count': followers, 'following_count': following,
                     'media_count': 0, 'biography': 'Only synthetic data', 'is_private': False, **fields}}


def page(ids, cursor='', **fields):
    return {'status': 'ok', 'users': [{'pk': identity, 'username': 'qa.'+identity, 'full_name': 'QA '+identity} for identity in ids],
            'next_max_id': cursor, **fields}


class FakeClient:
    user_id = 100
    rank_token = '100_synthetic'
    password = None
    private = public = graphql = None

    def __init__(self, responses):
        self.responses = iter(responses)
        self.calls = []

    def private_request(self, endpoint, **kwargs):
        self.calls.append((endpoint, kwargs))
        value = next(self.responses)
        if isinstance(value, Exception):
            raise value
        return value

    def get_settings(self):
        return {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC-SECRET'}}


class BackendTests(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.engine = Engine(self.home.name, clock=lambda: 1800000000)

    def tearDown(self):
        self.home.cleanup()

    def connected(self):
        e = self.engine
        e.record_profile(parse_profile(profile()), own=True)
        e.data['session'] = {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC-SECRET'}}
        e.save()
        return e

    def scan(self, responses, direction='followers'):
        fake = FakeClient(responses)
        with patch.object(self.engine, 'client', return_value=fake):
            self.engine.run('scan', {'direction': direction})
        return fake

    def test_dpapi_roundtrip_and_no_plaintext(self):
        e = self.connected()
        e.data['people']['200'] = {'id':'200','username':'qa.person','note':'PRIVATE-SYNTHETIC-NOTE'}
        e.save()
        raw = e.vault.path.read_bytes()
        self.assertNotIn(b'SYNTHETIC-SECRET', raw)
        self.assertNotIn(b'PRIVATE-SYNTHETIC-NOTE', raw)
        other = Engine(self.home.name)
        self.assertEqual(other.data['people']['200']['note'], 'PRIVATE-SYNTHETIC-NOTE')

    def test_corrupt_vault_preserved(self):
        path = Path(self.home.name) / 'tracker.vault'
        path.write_bytes(b'broken')
        with self.assertRaises(StorageFailure):
            Engine(self.home.name)
        self.assertEqual(path.read_bytes(), b'broken')

    def test_public_projection_excludes_auth(self):
        e = self.connected()
        raw = json.dumps(e.snapshot())
        self.assertNotIn('SYNTHETIC-SECRET', raw)
        self.assertNotIn('cookies', raw)
        self.assertNotIn('authorization_data', raw)

    def test_qa_offline_mode_rejects_before_any_session_read_or_network(self):
        e=self.engine;e.offline=True
        with patch('engine.new_client') as factory,patch('engine.protect') as decrypt:
            with self.assertRaises(AppFailure):e.run('import_counter',{})
            factory.assert_not_called();decrypt.assert_not_called()
        self.assertEqual(e.data['gate'].get('attempts',0),0)

    def test_profile_identity_and_explicit_counts(self):
        self.assertEqual(parse_profile(profile(), '100')['followers'], 2)
        for value in (None, True, -1, '3'):
            with self.subTest(value=value), self.assertRaises(AppFailure):
                parse_profile(profile(follower_count=value))
        with self.assertRaises(AppFailure):
            parse_profile(profile(), '101')
        with self.assertRaises(AppFailure):
            parse_profile(profile(identity=True))

    def test_optional_profile_unknown_not_zero(self):
        p = profile()
        del p['user']['is_private']
        del p['user']['media_count']
        result = parse_profile(p)
        self.assertIsNone(result['private'])
        self.assertIsNone(result['posts'])

    def test_profile_changes_use_identity_and_known_fields(self):
        e=self.connected()
        e.record_profile(parse_profile(profile(followers=4, biography='New bio')), own=True)
        changes=[v for v in e.data['events'] if v['type']=='profile']
        self.assertEqual({next(iter(v['new'])) for v in changes}, {'followers','bio'})
        self.assertEqual(len(e.data['history']['100']),2)

    def test_picture_compares_id_not_rotating_cdn_url(self):
        e=self.engine
        e.record_profile(parse_profile(profile(profile_pic_id='123',profile_pic_url='https://a.cdninstagram.com/a?x=1')),True)
        e.record_profile(parse_profile(profile(profile_pic_id='123',profile_pic_url='https://b.cdninstagram.com/a?x=2')),True)
        self.assertEqual(len([v for v in e.data['events'] if v['type']=='profile']),0)
        e.record_profile(parse_profile(profile()),True)
        e.record_profile(parse_profile(profile(profile_pic_id='456')),True)
        changes=[v for v in e.data['events'] if v['type']=='profile']
        self.assertEqual(changes[0]['old'],{'picture':'id:123'})
        self.assertEqual(changes[0]['new'],{'picture':'id:456'})

    def test_public_avatar_transport_has_no_session_or_cookie(self):
        for url in ('http://a.cdninstagram.com/a','https://cdninstagram.com.evil.invalid/a','https://user@a.fbcdn.net/a','https://a.fbcdn.net:444/a'):
            self.assertFalse(avatar_url(url))
        p=parse_profile(profile(profile_pic_url='https://a.cdninstagram.com/a'))
        raw=io.BytesIO();Image.new('RGB',(20,20)).save(raw,format='PNG')
        session=Mock();response=Mock();response.status_code=200;response.iter_content.return_value=[raw.getvalue()]
        session.get.return_value.__enter__=Mock(return_value=response)
        session.get.return_value.__exit__=Mock(return_value=False)
        with patch('network.requests.Session') as factory:
            factory.return_value.__enter__.return_value=session
            read_avatar(p,self.engine)
        self.assertFalse(session.trust_env)
        self.assertTrue(p['avatar'].startswith('data:image/jpeg;base64,'))
        self.assertEqual(session.get.call_args.kwargs['headers'],{'Accept':'image/*'})
        self.assertFalse(session.get.call_args.kwargs['allow_redirects'])

    def test_actual_caa_code_path_never_prints_or_uses_console(self):
        e=self.engine;e.request_code=Mock(return_value='654321')
        client=new_client(e)
        try:
            client.bloks_extract_context_data=Mock(return_value='context')
            for name in ('bloks_ap_two_step_verification_entrypoint','bloks_ap_two_step_verification_code_entry','bloks_ap_two_step_verification_submit_code'):
                setattr(client,name,Mock(return_value={'result':'test'}))
            client.bloks_apply_login_response=Mock(return_value=True)
            output=io.StringIO()
            with patch('builtins.input',side_effect=AssertionError('No console')),contextlib.redirect_stdout(output):
                result=client.bloks_caa_resolve_two_step_verification({})
            self.assertTrue(result['logged_in'])
            self.assertNotIn('654321',output.getvalue())
            e.request_code.assert_called_once_with(client.username,ChallengeChoice.EMAIL)
        finally:close_client(client)

    def test_two_factor_continues_existing_context(self):
        e=self.engine;e.request_code=Mock(return_value='654321')
        client=new_client(e)
        try:
            exc=TwoFactorRequired('QA',two_step_verification_context='existing-QA-context')
            client._login_with_bloks_two_factor=Mock(return_value=True)
            with patch.object(LoginMixin,'login',side_effect=exc) as initial:
                self.assertTrue(client.login('qa','QA-only-password'))
            self.assertEqual(initial.call_count,1)
            self.assertEqual(client._login_with_bloks_two_factor.call_args.args[1]['two_step_verification_context'],'existing-QA-context')
        finally:close_client(client)

    def test_credentials_not_in_saved_settings_and_old_account_preserved_on_failure(self):
        e=self.connected();before=copy.deepcopy(e.data['account'])
        client=Mock();client.login.side_effect=AppFailure('login','Synthetic failure')
        args={'username':'qa.owner','password':'QA-never-persist-password'}
        with patch('engine.new_client',return_value=client),self.assertRaises(AppFailure):e.run('login',args)
        self.assertEqual(e.data['account'],before)
        self.assertNotIn('password',args)
        self.assertIsNone(client.password)
        decrypted=protect(e.vault.path.read_bytes(),decrypt=True)
        self.assertNotIn(b'QA-never-persist-password',decrypted)

    def test_storage_failure_rolls_back_volatile_edit(self):
        e=self.connected();e.data['people']['200']={'id':'200','username':'qa.200','note':'before'};e.save()
        with patch.object(e.vault,'save',side_effect=StorageFailure('Synthetic disk full')),self.assertRaises(AppFailure):
            e.run('save_person',{'id':'200','note':'after'})
        self.assertEqual(e.data['people']['200']['note'],'before')

    def test_page_end_must_be_observed(self):
        self.assertEqual(parse_page(page(['200'])).coverage,'end')
        missing=page(['200']); missing.pop('next_max_id')
        self.assertEqual(parse_page(missing).coverage,'unknown')
        missing['has_more']=False
        self.assertEqual(parse_page(missing).coverage,'end')

    def test_page_limit_and_inconsistent_more(self):
        for p in (page(['200'], should_limit_list_of_followers=True), page(['200'],has_more=True),
                  page(['200'],'next',has_more=False)):
            with self.subTest(p=p):self.assertIn(parse_page(p).coverage,('limited','unknown'))
        for p in (page(['200'],has_more='false'), {'status':'fail','users':[]}):
            with self.subTest(p=p),self.assertRaises(AppFailure):parse_page(p)
        # big_list is metadata, not proof of a limited response.
        self.assertEqual(len(parse_page(page(['200'],big_list=True)).members),1)

    def test_server_limited_page_keeps_observed_members(self):
        e=self.connected()
        with self.assertRaises(AppFailure):self.scan([profile(),page(['200'],should_limit_list_of_followers=True)])
        record=e.data['lists']['100:followers']
        self.assertEqual(set(record['draft']['members']),{'200'})
        self.assertFalse(record['draft']['complete'])
        self.assertEqual(record['snapshots'],[])

    def test_paged_scan_and_identity_dedup(self):
        e=self.connected()
        fake=self.scan([profile(followers=3),page(['200','201'],'next'),page(['201','202'])])
        record=e.data['lists']['100:followers']
        self.assertEqual(len(record['snapshots']),1)
        self.assertEqual(set(record['snapshots'][0]['members']),{'200','201','202'})
        self.assertEqual(fake.calls[2][1]['params']['max_id'],'next')
        self.assertNotIn('draft',record)

    def test_distinct_following_scan(self):
        e=self.connected()
        fake=self.scan([profile(following=1),page(['200'])],'following')
        self.assertIn('100:following',e.data['lists'])
        self.assertEqual(fake.calls[1][0],'friendships/100/following/')

    def test_partial_does_not_replace_baseline(self):
        e=self.connected()
        self.scan([profile(),page(['200','201'])])
        with self.assertRaises(AppFailure):
            self.scan([profile(),page(['200'],'next'),AppFailure('connection','Synthetic interruption')])
        record=e.data['lists']['100:followers']
        self.assertEqual(len(record['snapshots']),1)
        self.assertEqual(record['draft']['status'],'partial')
        self.assertEqual(set(record['draft']['members']),{'200'})
        self.assertIsNone(e.snapshot()['lists']['100:followers']['comparison'])

    def test_count_mismatch_is_partial(self):
        e=self.connected()
        with self.assertRaises(AppFailure):self.scan([profile(followers=20),page(['200'])])
        record=e.data['lists']['100:followers']
        self.assertEqual(record['snapshots'],[])
        self.assertFalse(record['draft']['complete'])

    def test_cursor_loop_stops(self):
        self.connected()
        fake=FakeClient([profile(followers=8),page(['200'],'repeat'),page(['201'],'repeat')])
        with patch.object(self.engine,'client',return_value=fake),self.assertRaises(AppFailure):
            self.engine.run('scan',{'direction':'followers'})
        self.assertEqual(len(fake.calls),3)

    def test_comparison_complete_only(self):
        e=self.connected()
        self.scan([profile(),page(['200','201'])])
        self.scan([profile(),page(['201','202'])])
        diff=e.snapshot()['lists']['100:followers']['comparison']
        self.assertEqual(diff['added'],['202'])
        self.assertEqual(diff['removed'],['200'])
        self.assertIsNone(compare({'complete':False},{'complete':True}))

    def test_cancel_keeps_draft(self):
        e=self.connected()
        fake=FakeClient([profile()])
        def read(*args,**kwargs):
            e.cancel.set()
            return parse_profile(profile())
        with patch.object(e,'client',return_value=fake),patch.object(e,'read_profile',side_effect=read),self.assertRaises(AppFailure):
            e.run('scan',{'direction':'followers'})
        self.assertEqual(e.data['lists']['100:followers']['draft']['status'],'cancelled')

    def test_interrupted_scan_recovery(self):
        e=self.connected()
        e.data['lists']['100:followers']={'snapshots':[],'draft':{'status':'running','members':{},'complete':False}}
        e.save()
        reopened=Engine(self.home.name)
        self.assertEqual(reopened.data['lists']['100:followers']['draft']['status'],'interrupted')

    def test_saved_member_notes_survive_reopen(self):
        e=self.connected();self.scan([profile(),page(['200','201'])])
        e.run('save_person',{'id':'200','favorite':True,'watching':True,'note':'<script>private note</script>','group':'QA'})
        p=Engine(self.home.name).data['people']['200']
        self.assertEqual(p['note'],'<script>private note</script>')
        self.assertTrue(p['watching'])

    def test_429_gate_survives_restart_and_delete(self):
        e=self.connected()
        e.limited('7200')
        end=e.data['gate']['blockedUntil']
        self.assertEqual(end,1800007200)
        e.run('delete_all',{})
        reopened=Engine(self.home.name,clock=lambda:1800000001)
        self.assertEqual(reopened.data['gate']['blockedUntil'],end)
        with self.assertRaises(AppFailure):reopened.before_request()
        self.assertEqual(reopened.data['gate'].get('attempts',0),0)
        self.assertFalse(reopened.vault.backup.exists())

    def test_disconnect_removes_session_backup(self):
        e=self.connected();e.run('disconnect',{})
        self.assertIsNone(Engine(self.home.name).data['session'])
        self.assertFalse(e.vault.backup.exists())
        self.assertIsNotNone(e.data['account'])

    def test_backup_no_session_and_preserves_longer_mola(self):
        e=self.connected()
        path=Path(self.home.name)/'qa.trackerbackup'
        e.export_backup(path)
        restored=json.loads(protect(path.read_bytes(),decrypt=True))
        self.assertIsNone(restored['data']['session'])
        self.assertNotIn('SYNTHETIC-SECRET',json.dumps(restored))
        e.limited('9000');end=e.data['gate']['blockedUntil']
        e.import_backup(path)
        self.assertEqual(e.data['gate']['blockedUntil'],end)
        self.assertIsNone(e.data['session'])

    def test_invalid_backup_leaves_current_unchanged(self):
        e=self.connected();before=copy.deepcopy(e.data)
        path=Path(self.home.name)/'broken.trackerbackup';path.write_bytes(b'broken')
        with self.assertRaises(AppFailure):e.import_backup(path)
        self.assertEqual(e.data,before)

    def test_automatic_disabled_or_paused_makes_no_request(self):
        e=self.connected()
        with patch.object(e,'refresh') as refresh:
            e.automatic();refresh.assert_not_called()
            e.data['settings']['automatic']=True
            e.data['gate']['manualOnly']=True
            e.automatic();refresh.assert_not_called()

    def test_automatic_reads_due_profiles_without_waiting_an_interval_between_them(self):
        e=self.connected()
        e.data['settings']['automatic']=True
        e.data['people']['200']={'id':'200','username':'qa.200','watching':True,'observedAt':100}
        with patch.object(e,'refresh') as refresh,patch.object(e,'scan') as scan:
            e.automatic();e.automatic()
            refresh.assert_called_once_with({'id':'200'});scan.assert_not_called()

    def test_one_and_five_minute_settings_survive_encrypted_reopen(self):
        e=self.connected()
        for interval in (1,5,15,30,60,180,360):
            e.run('settings',{'interval':interval})
            self.assertEqual(Engine(self.home.name).data['settings']['interval'],interval)
        for invalid in (True,1.0,0,2,-1,'1'):
            with self.subTest(interval=invalid),self.assertRaises(AppFailure):e.run('settings',{'interval':invalid})

    def test_monitor_is_the_owner_of_due_time_and_cycle_estimate(self):
        e=self.connected();e.settings({'automatic':True,'interval':1})
        e.data['lastAutomatic']=1800000000
        e.data['people']['200']={'id':'200','username':'qa.200','watching':True,'observedAt':1800000000}
        status=e.snapshot()['monitor']
        self.assertEqual(status['nextAt'],1800000060)
        self.assertEqual(status['profileCount'],2)
        self.assertEqual(status['estimatedCycleSeconds'],60)
        e.data['gate']['nextAt']=1800000100
        self.assertEqual(e.monitor_status()['nextAt'],1800000100)
        e.data['gate']['manualOnly']=True
        self.assertTrue(e.monitor_status()['held'])
        self.assertIsNone(e.monitor_status()['nextAt'])

    def test_short_interval_does_not_run_early_or_replay_missed_ticks(self):
        e=self.connected();e.settings({'automatic':True,'interval':1})
        e.data['lastAutomatic']=1800000000
        with patch.object(e,'refresh') as refresh:
            e.clock=lambda:1800000059.9;e.automatic();refresh.assert_not_called()
            e.clock=lambda:1800000060;e.automatic();refresh.assert_called_once()
            e.clock=lambda:1800000600;e.automatic()
            self.assertEqual(refresh.call_count,2)
            e.automatic();self.assertEqual(refresh.call_count,2)

    def test_code_cancel_does_not_restart_login(self):
        e=self.engine;result=[]
        def wait():
            try:e.request_code()
            except AppFailure as exc:result.append(exc.code)
        thread=threading.Thread(target=wait);thread.start()
        for _ in range(100):
            if e.waiting_code:break
            time.sleep(.005)
        e.cancel.set();thread.join(1)
        self.assertEqual(result,['cancelled'])
        self.assertFalse(e.waiting_code)

    def test_code_bound_to_waiting_context(self):
        e=self.engine
        with self.assertRaises(AppFailure):e.submit_code('123456')
        e.waiting_code=True;e.submit_code('123456')
        self.assertEqual(e.codes.get_nowait(),'123456')
        with self.assertRaises(AppFailure):e.submit_code('not-a-code')

    def test_transport_policy_single_send_timeout_tls_and_redirect(self):
        e=self.engine
        response=requests.Response();response.status_code=302;response._content=b'';response.headers['Location']='https://example.invalid/'
        with patch('requests.Session.send',return_value=response) as send:
            client=new_client(e)
            try:
                with self.assertRaises(AppFailure):client.private.get('https://example.invalid/')
                send.assert_not_called()
                with self.assertRaises(AppFailure):client.private.get('https://i.instagram.com/api/v1/test/')
                self.assertEqual(send.call_count,1)
                kwargs=send.call_args.kwargs
                self.assertIs(kwargs['verify'],True);self.assertFalse(kwargs['allow_redirects'])
                self.assertEqual(kwargs['timeout'],25)
            finally:close_client(client)

    def test_transport_timeout_never_replays(self):
        e=self.engine
        with patch('requests.Session.send',side_effect=requests.ReadTimeout()) as send:
            client=new_client(e)
            try:
                with self.assertRaises(Exception):client.private_request('users/100/info/')
                self.assertEqual(send.call_count,1)
            finally:close_client(client)

    def test_http429_blocks_next_transmission(self):
        e=self.engine
        response=requests.Response();response.status_code=429;response._content=b'{}';response.headers['Retry-After']='4000'
        with patch('requests.Session.send',return_value=response) as send:
            client=new_client(e)
            try:
                with self.assertRaises(AppFailure):client.private.get('https://i.instagram.com/api/v1/test/')
                with self.assertRaises(AppFailure):client.private.get('https://i.instagram.com/api/v1/test/')
                self.assertEqual(send.call_count,1)
                self.assertEqual(e.data['gate']['status'],429)
                self.assertEqual(e.data['gate']['blockedUntil'],1800004000)
            finally:close_client(client)


if __name__=='__main__':
    unittest.main(verbosity=2)
