"""Real auth-library and transport policy boundaries, synthetic HTTP only."""
import base64
import copy
import json
import tempfile
import unittest
from unittest.mock import patch
from urllib.parse import urlsplit

from Cryptodome.PublicKey import RSA
import requests
from accounts import Accounts
from engine import Engine
from login_identity import LoginIdentity
from network import AppFailure, new_client, close_client, parse_profile
from storage import protect, StorageFailure
from test_backend import profile
from test_scan_progress import ClockWait
import request_budget


class AccountSafetyTests(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.addCleanup(self.home.cleanup)
        self.now = [1800000000.0]
        self.e = Accounts(self.home.name, clock=lambda:self.now[0], request_delay=lambda:15)
        self.e.cancel = ClockWait(self.now)

    def fake_reply(self, request, body=None, code=200, headers=None):
        r = requests.Response(); r.status_code = code; r._content = json.dumps(body or {'status':'ok'}).encode()
        r.request = request; r.url = request.url; r.headers.update(headers or {})
        return r

    def test_mutations_unknown_paths_methods_and_generic_graphql_never_reach_send(self):
        with patch('requests.Session.send') as send:
            client = new_client(self.e)
            try:
                for method,path in [('POST','friendships/create/200/'),('POST','friendships/destroy/200/'),
                                    ('POST','media/123/like/'),('POST','direct_v2/threads/broadcast/text/'),
                                    ('GET','accounts/logout/'),('DELETE','users/100/info/'),('GET','unknown/'),
                                    ('GET','users/100%2Finfo/'),('POST','users/100/info/')]:
                    with self.subTest(path=path,method=method),self.assertRaises(AppFailure):
                        client.private.request(method,'https://i.instagram.com/api/v1/'+path)
                with self.assertRaises(AppFailure): client.graphql.post('https://www.instagram.com/api/graphql',data={'doc_id':'unknown'})
                send.assert_not_called()
                self.assertEqual(self.e.data['gate'].get('attempts',0),0)
            finally: close_client(client)

    def test_auth_exceptions_unavailable_to_read_workflows_and_all_sessions_guarded(self):
        with patch('requests.Session.send') as send:
            client = new_client(self.e)
            try:
                for session in (client.private,client.public,client.graphql):
                    self.assertFalse(session.trust_env)
                    with self.assertRaises(AppFailure):session.post('https://i.instagram.com/api/v1/accounts/login/',data={'synthetic':1})
                    with self.assertRaises(AppFailure):session.get('https://example.invalid/')
                send.assert_not_called()
            finally:close_client(client)

    def test_full_fresh_login_uses_real_library_contract_and_commits_validated_account(self):
        key=base64.b64encode(RSA.generate(2048).public_key().export_key(format='DER')).decode()
        auth='Bearer IGT:2:'+base64.b64encode(json.dumps({'ds_user_id':'100','sessionid':'SYNTHETIC-SESSION'}).encode()).decode()
        envelope={'login_response':json.dumps({'logged_in_user':{'pk':100,'username':'qa.owner'}}),
                  'headers':json.dumps({'ig-set-authorization':auth}),
                  'cookies':'sessionid=SYNTHETIC-SESSION; ds_user_id=100;'}
        calls=[]
        def send(request,**kwargs):
            calls.append((request.method,urlsplit(request.url).hostname,urlsplit(request.url).path))
            path=urlsplit(request.url).path
            if path=='/graphql_www': body={'data':{'usdid_registration':{'success':True}}}
            elif path.endswith('process_client_data_and_redirect/'):
                body={'layout':{'bloks_payload':{'data':[{'data':{'key':'CAA_ACCOUNT_ACCESS_CONTEXT:aac','initial':'server-aac'}}]}}}
            elif path.endswith('attestation/create_android_keystore/'):body={'challenge_nonce':'server-nonce'}
            elif path=='/api/v1/qe/sync/':return self.fake_reply(request,headers={'ig-set-password-encryption-key-id':'7','ig-set-password-encryption-pub-key':key})
            elif path.endswith('send_login_request/'):body={'layout':{'bloks_payload':{'action':'(f4i '+json.dumps(json.dumps(envelope))+')'}}}
            elif path=='/api/v1/users/100/info/':body=profile()
            elif path.endswith('oauth.token.fetch.async/'):body={'status':'ok'}
            else:raise AssertionError('Unexpected request contract: '+path)
            return self.fake_reply(request,body)
        with patch('requests.Session.send',side_effect=send):
            state=self.e.run('login',{'username':'qa.owner','password':'SYNTHETIC-PASSWORD'})
        self.assertEqual(state['account']['id'],'100')
        self.assertTrue(state['connected'])
        self.assertIn(('POST','b.i.instagram.com','/graphql_www'),calls)
        self.assertEqual(calls[-1],('GET','i.instagram.com','/api/v1/users/100/info/'))
        client = new_client(self.e)
        try:self.assertFalse(client._tracker_authenticating)
        finally:close_client(client)
        device=self.e.login_identity.load()
        self.assertNotIn('cookies',device);self.assertNotIn('authorization_data',device)
        self.assertNotIn('SYNTHETIC-SESSION',json.dumps(device))
        self.assertNotIn('SYNTHETIC-PASSWORD',json.dumps(device))

    def test_failed_login_reuses_encrypted_device_context(self):
        clients=[]
        def rejected(client,*_args,**_kwargs):
            clients.append(client.uuid)
            raise AppFailure('login','Synthetic credential rejection')
        with patch('network.Client.login',rejected):
            for _ in range(2):
                with self.assertRaises(AppFailure):self.e.run('login',{'username':'qa.owner','password':'SYNTHETIC-PASSWORD'})
                self.now[0]+=901
        self.assertEqual(clients[0],clients[1])
        self.assertNotIn(b'SYNTHETIC-PASSWORD',protect(self.e.login_identity.path.read_bytes(),decrypt=True))
        reopened=Accounts(self.home.name)
        client=new_client(reopened,reopened.login_device_settings('qa.owner'))
        try:self.assertEqual(client.uuid,clients[0])
        finally:close_client(client)

    def test_hourly_account_budget_shared_with_restart_delete_and_other_accounts(self):
        p=parse_profile(profile());self.e.select_connection_account(p);self.e.record_profile(p,own=True)
        self.e.data['gate']['accountRequestTimes']={'100':[self.now[0]]*request_budget.ACCOUNT[0]}
        self.e.save()
        end=self.e.budget_available_at()
        with self.assertRaises(AppFailure) as caught:self.e.before_request()
        self.assertEqual(caught.exception.code,'budget');self.assertEqual(end,self.now[0]+3600)
        restart=Accounts(self.home.name,clock=lambda:self.now[0])
        restart.run('delete_all',{})
        p=parse_profile(profile());restart.select_connection_account(p);restart.record_profile(p,own=True);restart.save()
        self.assertEqual(restart.budget_available_at(),end)
        p=parse_profile(profile('300'));restart.select_connection_account(p);restart.record_profile(p,own=True);restart.save()
        self.assertEqual(restart.budget_available_at(),0)
        restart.data['gate']['requestTimes']=[self.now[0]]*request_budget.SHARED[0];restart.save()
        self.assertEqual(restart.budget_available_at(),end)

    def test_corrupt_device_context_is_preserved_and_auth_scope_closes_on_failure(self):
        self.e.login_identity.path.write_bytes(b'SYNTHETIC-CORRUPT-DPAPI')
        with self.assertRaises(StorageFailure):self.e.login_identity.load()
        self.assertEqual(self.e.login_identity.path.read_bytes(),b'SYNTHETIC-CORRUPT-DPAPI')
        self.e.login_identity.path.unlink()
        with patch('network.Client.login',side_effect=AppFailure('login','Synthetic failure')),patch('requests.Session.send') as send:
            client=new_client(self.e)
            try:
                with self.assertRaises(AppFailure):client.login('qa.owner','SYNTHETIC-PASSWORD')
                self.assertFalse(client._tracker_authenticating)
                with self.assertRaises(AppFailure):client.private.post('https://i.instagram.com/api/v1/accounts/login/')
                send.assert_not_called()
            finally:close_client(client)

    def test_budget_deadlines_rolling_day_clock_rollback_and_pruning(self):
        now=self.now[0]
        self.assertEqual(request_budget.available_at([now-7200]*request_budget.ACCOUNT[1],now,request_budget.ACCOUNT),now-7200+86400)
        self.assertEqual(request_budget.available_at([now]*240,now-1,request_budget.ACCOUNT),now+3600)
        self.assertEqual(request_budget.available_at([now-3600]*240,now,request_budget.ACCOUNT),0)
        gate={'requestTimes':[now-86400],'accountRequestTimes':{'100':[now-86400]}}
        request_budget.record(gate,'300',now)
        self.assertEqual(gate,{'requestTimes':[now],'accountRequestTimes':{'300':[now]}})

    def test_budget_exhaustion_does_not_mark_429_or_reset_with_manual_success(self):
        self.e.data['gate']['requestTimes']=[self.now[0]]*request_budget.SHARED[0]
        self.e.save();before=copy.deepcopy(self.e.data['gate'])
        with self.assertRaises(AppFailure):self.e.before_request()
        self.assertEqual(self.e.data['gate'],before)
        self.assertFalse(self.e.catalog['limitedHold'])
        self.assertEqual(self.e.data['gate'].get('attempts',0),0)

    def test_lower_shared_policy_keeps_old_history_and_server_pause(self):
        p=parse_profile(profile());self.e.select_connection_account(p);self.e.record_profile(p,own=True)
        self.e.data['gate'].update(requestTimes=[self.now[0]]*120,accountRequestTimes={'100':[self.now[0]]*120},
                                   blockedUntil=self.now[0]+1800,nextAt=self.now[0]+17,attempts=120)
        self.e.save()
        reopened=Accounts(self.home.name,clock=lambda:self.now[0])
        self.assertEqual(reopened.budget_available_at(),self.now[0]+3600)
        self.assertEqual(len(reopened.data['gate']['accountRequestTimes']['100']),120)
        before=copy.deepcopy(reopened.data['gate'])
        with self.assertRaises(AppFailure) as caught:reopened.before_request()
        self.assertEqual(caught.exception.code,'cooldown')
        self.assertEqual(reopened.data['gate'],before)
        reopened.data['gate']['accountRequestTimes']['100'] += [self.now[0]]*120
        self.assertEqual(reopened.budget_available_at(),self.now[0]+3600)

    def test_changed_budget_recalculates_cached_account_deadlines_without_resetting_counts(self):
        p=parse_profile(profile());self.e.select_connection_account(p);self.e.record_profile(p,own=True)
        self.e.data['session']={'cookies':{'ds_user_id':'100','sessionid':'SYNTHETIC'}}
        self.e.data['settings'].update(automatic=True,automaticLists=False,interval=1)
        self.e.data['gate'].update(requestTimes=[self.now[0]]*60,accountRequestTimes={'100':[self.now[0]]*60})
        self.e.save()
        self.e.catalog['accounts']['100']['monitor'].update(nextAt=self.now[0]+3600,
            budgetPolicy={'sharedHourly':120,'sharedDaily':1200,'accountHourly':60,'accountDaily':600})
        self.e.persist_catalog()
        counts=copy.deepcopy(self.e.data['gate'])
        with patch('requests.Session.send') as send:
            reopened=Accounts(self.home.name,clock=lambda:self.now[0]+61)
        send.assert_not_called()
        self.assertEqual(reopened.data['gate'],counts)
        self.assertEqual(reopened.catalog['accounts']['100']['monitor']['budgetPolicy'],request_budget.policy())
        self.assertLessEqual(reopened.background_status()['nextAt'],self.now[0]+61)


if __name__=='__main__':unittest.main()
