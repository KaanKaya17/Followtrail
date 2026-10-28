"""Public-page parsing and the real guarded transport, without internet access."""
import copy
import json
import tempfile
import unittest
from unittest.mock import patch

import requests

import anonymous_visibility as public
import access_checks
from engine import Engine
from network import AppFailure, parse_profile
from storage import StorageFailure


def document(identity='200', name='qa.person'):
    return ('<script type="application/json">'+json.dumps({'require': [['Relay', {'__bbox': {'result': {'data': {'user': {'id': identity, 'username': name}}}}}]]})+'</script>').encode()


def response(status=200, body=None, **headers):
    r=requests.Response();r.status_code=status;r._content=document() if body is None else body;r._content_consumed=True
    r.headers.update({'Content-Type':'text/html; charset=utf-8',**headers})
    return r


class AnonymousTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-');self.addCleanup(self.home.cleanup)
        self.e=Engine(self.home.name,clock=lambda:1800000000)

    def test_only_profile_owned_identity_is_a_positive_result(self):
        self.assertEqual(public.parse_page(document(),'200','qa.person')['status'],'visible')
        other=public.parse_page(document('201'),'200','qa.person')
        self.assertEqual(other['reason'],'identity_mismatch');self.assertFalse(other['identityMatched'])
        recommendations=b'<script type="application/json">{"users":[{"id":"200","username":"qa.person"}]}</script>'
        self.assertEqual(public.parse_page(recommendations,'200','qa.person')['status'],'unknown')
        self.assertEqual(public.parse_page(b'<title>Instagram</title>','200','qa.person')['reason'],'identity_unverified')

    def test_structured_metadata_and_profile_schema_preserve_identity(self):
        meta=b'<meta property="og:url" content="https://www.instagram.com/qa.person/"><meta property="profile:id" content="200">'
        self.assertEqual(public.parse_page(meta,'200','qa.person')['status'],'visible')
        schema={'@type':'ProfilePage','mainEntity':{'@type':'Person','alternateName':'@qa.person','identifier':{'value':'200'}}}
        body=('<script type="application/ld+json">'+json.dumps(schema)+'</script>').encode()
        self.assertEqual(public.parse_page(body,'200','qa.person')['observedId'],'200')
        self.assertEqual(public.parse_page(meta.replace(b'/qa.person/',b'/other/'),'200','qa.person')['status'],'unknown')

    def test_challenge_and_conflicting_profiles_do_not_establish_visibility(self):
        body=document()+b'<form action="/challenge/123/"></form>'
        self.assertEqual(public.parse_page(body,'200','qa.person')['reason'],'verification')
        self.assertEqual(public.parse_page(document()+document('201'),'200','qa.person')['status'],'unknown')
        login=b'<form action="/accounts/login/"></form>'
        self.assertEqual(public.parse_page(login,'200','qa.person')['reason'],'login_required')
        malformed=b'<script type="application/json">{broken}</script>'
        self.assertEqual(public.parse_page(malformed,'200','qa.person')['status'],'unknown')

    def test_real_transport_has_no_account_cookie_proxy_or_automatic_replay(self):
        # A saved secret exists in the engine but is never passed to the reader's session.
        self.e.record_profile(parse_profile({'user':{'pk':'100','username':'qa.owner','follower_count':1,'following_count':1}}),True)
        self.e.data['session']={'cookies':{'sessionid':'SYNTHETIC-SECRET'}}
        def send(session, prepared, **kwargs):
            self.assertEqual(prepared.method,'GET');self.assertEqual(prepared.url,'https://www.instagram.com/qa.person/')
            for key in ('Cookie','Authorization','Proxy-Authorization'):self.assertNotIn(key,prepared.headers)
            self.assertFalse(session.trust_env);self.assertEqual(kwargs['proxies'],{})
            self.assertTrue(kwargs['verify']);self.assertFalse(kwargs['allow_redirects'])
            self.assertEqual(session.get_adapter(prepared.url).max_retries.total,0)
            return response(**{'Set-Cookie':'sessionid=SHOULD-NOT-BE-REUSED'})
        with patch('requests.sessions.Session.send',autospec=True,side_effect=send) as transport:
            result=public.probe(self.e,'200','qa.person')
        self.assertEqual(transport.call_count,1);self.assertEqual(result['status'],'visible')
        self.assertEqual(self.e.data['gate']['attempts'],1)
        public.validate(result,'200')
        self.assertNotIn('SYNTHETIC-SECRET',json.dumps(result))

    def test_429_preserves_shared_cooldown_and_sends_no_second_request(self):
        with patch('requests.sessions.Session.send',return_value=response(429,**{'Retry-After':'7200'})) as transport:
            limited=public.probe(self.e,'200','qa.person')
            retry=public.probe(self.e,'200','qa.person')
        self.assertEqual(limited['reason'],'limited');self.assertEqual(limited['http'],429)
        self.assertEqual(retry['reason'],'cooldown');self.assertIsNone(retry['http'])
        self.assertEqual(transport.call_count,1);self.assertTrue(self.e.data['gate']['manualOnly'])
        self.assertGreaterEqual(Engine(self.home.name).data['gate']['blockedUntil'],1800007200)

    def test_status_redirect_and_oversize_responses_are_not_confused(self):
        for r,status,reason in [(response(404),'not_found','http_not_found'),(response(410),'not_found','http_not_found'),
                                (response(302,Location='/accounts/login/?next=SECRET'),'unknown','login_required'),
                                (response(403),'unknown','access_denied'),(response(503),'unknown','server_error'),
                                (response(body=b'x'*(public.MAX_BYTES+1)),'unknown','body_limit')]:
            self.e.data['gate']['nextAt']=0
            with patch('requests.sessions.Session.send',return_value=r) as transport:
                result=public.probe(self.e,'200','qa.person')
            self.assertEqual((result['status'],result['reason']),(status,reason));self.assertEqual(transport.call_count,1)
            self.assertNotIn('SECRET',json.dumps(result))

    def test_cancellation_storage_and_timeout_keep_their_meaning(self):
        self.e.cancel.set()
        with patch('requests.sessions.Session.send') as transport,self.assertRaises(AppFailure):public.probe(self.e,'200','qa.person')
        transport.assert_not_called();self.e.cancel.clear()
        with patch('requests.sessions.Session.send',side_effect=requests.ReadTimeout()) as transport:
            self.assertEqual(public.probe(self.e,'200','qa.person')['reason'],'connection')
        self.assertEqual(transport.call_count,1)
        with patch.object(self.e,'before_request',side_effect=StorageFailure('synthetic')),self.assertRaises(StorageFailure):
            public.probe(self.e,'200','qa.person')

    def test_invalid_username_cannot_escape_the_fixed_origin(self):
        for name in ('../accounts/logout','qa.person/?next=evil','@qa.person','https://evil.example'):
            with patch('requests.sessions.Session.send') as transport,self.assertRaises(AppFailure):public.probe(self.e,'200',name)
            transport.assert_not_called()

    def test_assessment_and_import_validation_reject_unverified_positive_claims(self):
        target={'status':'unavailable','reason':'profile_not_found','targetHTTP':404}
        known={'method':public.METHOD,'requestedUsername':'qa.person','checkedAt':1800000000,'http':200,**public.parse_page(document(),'200','qa.person')}
        strong=access_checks.assess(target,{'previousAt':1},known)
        self.assertEqual(strong['reason'],'anonymous_visible');self.assertEqual(strong['status'],'suspected')
        absent={**known,'status':'not_found','reason':'http_not_found','http':404,'identityMatched':None}
        self.assertEqual(access_checks.assess(target,{'previousAt':1},absent)['reason'],'both_unavailable')
        generic={**known,'status':'unknown','reason':'identity_unverified','identityMatched':None}
        self.assertEqual(access_checks.assess(target,{'previousAt':1},generic)['status'],'unknown')
        for field,value in [('observedId','201'),('identityMatched',False),('http',302)]:
            bad=copy.deepcopy(known);bad[field]=value
            with self.assertRaises(ValueError):public.validate(bad,'200')


if __name__=='__main__':unittest.main()
