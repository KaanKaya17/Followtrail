import json
import tempfile
import unittest
from unittest.mock import patch, Mock
from engine import Engine
from network import AppFailure, parse_profile
from test_backend import profile
import profile_links
import threads_profile

def document(name='qa.person'):
    return ('<script type="application/ld+json">'+json.dumps({'@type':'ProfilePage','mainEntity':{'@type':'Person','alternateName':'@'+name,'url':'https://www.threads.com/@'+name}})+'</script>').encode()

class ProfileLinksTests(unittest.TestCase):
    def test_profile_links_are_optional_bounded_and_backward_compatible(self):
        user=profile()['user'];user['external_url']='https://portfolio.example/about'
        user['bio_links']=[{'url':'javascript:alert(1)'},{'url':'https://www.youtube.com/@qa'},{'url':'https://portfolio.example/about'},None]
        parsed=parse_profile({'user':user});self.assertEqual(parsed['links'],[{'url':'https://www.youtube.com/@qa'},{'url':'https://portfolio.example/about'}])
        self.assertTrue(profile_links.validate(parsed['links']))
        self.assertEqual(profile_links.parse({'bio_links':'invalid'}),[])
        self.assertEqual(len(profile_links.parse({'bio_links':[{'url':f'https://example.com/{i}'} for i in range(8)]})),5)

    def test_threads_parser_requires_a_profile_owned_matching_name_and_url(self):
        self.assertTrue(threads_profile.parse(document(),'qa.person'))
        for body in [document('other'),b'<html>Log in to Threads</html>',b'<script type="application/json">{"recommendations":[{"username":"qa.person"}]}</script>',b'<script type="application/json">{bad}</script>']:
            self.assertFalse(threads_profile.parse(body,'qa.person'))
        for url in ['https://threads.com.evil.example/@qa','https://u:p@threads.com/@qa','http://threads.com/@qa','https://threads.com/@qa/post/123']:
            self.assertIsNone(threads_profile.username(url))

    def test_threads_reader_is_cookie_free_bounded_and_never_retries_redirects_or_login_walls(self):
        engine=Mock();engine.clock.return_value=1800000000;engine.data={'gate':{}}
        for code,body,status in [(200,document(),'visible'),(200,b'<html>Log in</html>','unknown'),(404,b'','not_found'),(302,b'','unknown'),(429,b'','unknown'),(200,b'x'*(threads_profile.MAX_BYTES+1),'unknown')]:
            with self.subTest(code=code,size=len(body)),patch('threads_profile.requests.Session',wraps=threads_profile.requests.Session) as factory:
                response=Mock(status_code=code,headers={'Content-Type':'text/html','Retry-After':'90'})
                response.iter_content.return_value=[body];response.__enter__=Mock(return_value=response);response.__exit__=Mock(return_value=False)
                session=threads_profile.requests.Session()
                with patch.object(session,'send',return_value=response) as send,patch('threads_profile.requests.Session',return_value=session):
                    result=threads_profile.probe(engine,'https://www.threads.com/@qa.person')
                self.assertEqual(result['status'],status);self.assertEqual(send.call_count,1)
                request=send.call_args.args[0];self.assertEqual(request.method,'GET')
                self.assertFalse(any(key.lower() in ('cookie','authorization','proxy-authorization') for key in request.headers))
                self.assertFalse(send.call_args.kwargs['allow_redirects']);self.assertFalse(session.trust_env)
        engine.limited.assert_called_once_with('90')

    def test_engine_scopes_and_caches_threads_checks_and_preserves_old_vaults(self):
        with tempfile.TemporaryDirectory(prefix='TrackerQA-') as home:
            now=[1800000000.0];engine=Engine(home,clock=lambda:now[0]);engine.record_profile(parse_profile(profile()),own=True);engine.save()
            outcome={'url':'https://www.threads.com/@qa.owner','username':'qa.owner','checkedAt':now[0],'status':'visible','http':200}
            args={'accountId':'100','id':'100','url':outcome['url']}
            with patch('threads_profile.probe',return_value=outcome) as probe:
                state=engine.run('check_threads',args);self.assertEqual(state['threadsProfile']['status'],'visible')
                engine.run('check_threads',args);self.assertEqual(probe.call_count,1)
                with self.assertRaises(AppFailure):engine.run('check_threads',{**args,'accountId':'999'})
                self.assertEqual(probe.call_count,1)
            engine.offline=True
            with patch('threads_profile.probe') as probe:
                with self.assertRaises(AppFailure):engine.run('check_threads',args)
                probe.assert_not_called()
            self.assertNotIn('threadsProfile',engine.vault.load())
            self.assertEqual(Engine(home).snapshot()['account']['links'],[])
