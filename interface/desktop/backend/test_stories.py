"""Synthetic story contracts through the real transport, queue and DPAPI store."""
import base64
import copy
from io import BytesIO
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image
import requests
import stories
from accounts import Accounts
from engine import Engine
from network import AppFailure, new_client, close_client, parse_profile
from storage import StorageFailure, protect
from test_backend import profile, FakeClient
from test_scan_progress import ClockWait

FIXTURE = Path(__file__).resolve().parents[3] / 'test-fixtures/stories.json'


def picture():
    output=BytesIO();Image.new('RGB',(36,64),'#385a81').save(output,format='JPEG');return output.getvalue()


class StoryTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-');self.addCleanup(self.home.cleanup)
        self.now=[1800000100.0]
        self.e=Engine(self.home.name,clock=lambda:self.now[0],request_delay=lambda:6)
        self.e.cancel=ClockWait(self.now)
        self.e.record_profile(parse_profile(profile()),own=True)
        self.e.data['people']['200']=parse_profile(profile('200'))
        self.e.data['session']={'cookies':{'ds_user_id':'100','sessionid':'SYNTHETIC-SECRET'}}
        self.e.save()
        self.args={'accountId':'100','id':'200'}
        self.payload=json.loads(FIXTURE.read_text())
        self.store=stories.StoryStore(self.home.name,'100')
        self.raw=picture()

    def response(self,request,body=None,code=200,headers=None):
        r=requests.Response();r.status_code=code;r.url=request.url;r.request=request;r.headers.update(headers or {})
        r._content=body if isinstance(body,bytes) else json.dumps(body).encode();r._content_consumed=True
        return r

    def transport(self,request,**kwargs):
        self.calls.append(request)
        self.assertFalse(kwargs['allow_redirects']);self.assertTrue(kwargs['verify'])
        if '/feed/user/200/story/' in request.url:return self.response(request,self.payload)
        self.assertNotIn('Cookie',request.headers);self.assertNotIn('Authorization',request.headers)
        raw=self.raw if '.jpg' in request.url else b'\x00\x00\x00\x18ftypisom'+b'\x00'*40
        return self.response(request,raw,headers={'Content-Length':str(len(raw))})

    def fetch(self):
        self.calls=[]
        with patch('requests.Session.send',side_effect=self.transport):
            return self.e.run('story_fetch',dict(self.args))

    def test_manual_read_archives_both_media_and_structured_facts_without_social_writes(self):
        state=self.fetch();archive=state['storyDetail']
        self.assertEqual(len(self.calls),3)
        self.assertTrue(all(r.method=='GET' for r in self.calls))
        self.assertEqual(self.e.data['gate']['attempts'],3)
        self.assertEqual(archive['summary'],{'returned':2,'saved':2,'existing':0,'metadata':0})
        row=next(r for r in archive['items'] if r['id']=='300')
        self.assertEqual(row['mentions'],[{'id':'900','username':'qa.tagged'}])
        self.assertEqual(row['sharedMedia'][0]['id'],'777');self.assertIsNone(row['resharedFrom'])
        self.assertEqual(row['links'],['https://example.com/event'])
        self.assertNotIn('mediaUrl',json.dumps(archive));self.assertNotIn('SYNTHETIC-SECRET',json.dumps(archive))
        for path in self.store.directory.iterdir():
            content=path.read_bytes();self.assertNotIn(self.raw,content);self.assertNotIn(b'qa.tagged',content)
        media=self.e.story_media({**self.args,'storyId':'300'})
        self.assertEqual(base64.b64decode(media['base64']),self.raw)
        self.assertNotIn('base64',json.dumps(self.e.snapshot()))
        second=self.fetch()['storyDetail'];self.assertEqual(len(self.calls),1)
        self.assertEqual(second['summary']['existing'],2)

    def test_empty_expired_result_retains_archived_media_and_local_view_uses_no_network(self):
        self.fetch();before=self.e.data['gate']['attempts']
        self.payload={'status':'ok','reel':None}
        state=self.fetch();self.assertEqual(len(state['storyDetail']['items']),2)
        self.assertEqual(state['storyDetail']['summary']['returned'],0)
        with patch('requests.Session.send') as send:
            reopened=Engine(self.home.name).run('story_view',dict(self.args))
            self.assertEqual(len(reopened['storyDetail']['items']),2);send.assert_not_called()
        self.assertEqual(self.e.data['gate']['attempts'],before+1)

    def test_unknown_fields_are_not_invented_and_mentions_do_not_imply_resharing(self):
        rows=stories.parse(self.payload,'200')
        self.assertIsNone(rows[0]['resharedFrom']);self.assertIsNone(rows[1]['expiresAt']);self.assertIsNone(rows[1]['links'])
        self.assertEqual(rows[1]['mentions'],[])
        self.payload['reel']['items'][0]['reel_mentions'].append({'user':{}})
        partial=stories.parse(self.payload,'200')[0]
        self.assertEqual(len(partial['mentions']),1);self.assertIn('reel_mentions',partial['incompleteFields'])
        self.payload['reel']['items'][0]['media_type']=999
        self.assertEqual(stories.parse(self.payload,'200')[0]['type'],'unsupported')

    def test_wrong_owner_malformed_envelopes_and_cross_account_media_fail_closed(self):
        for payload in ({}, {'status':'ok'}, {'status':'fail','reel':None}, {'status':'ok','reel':{}}):
            with self.assertRaises(AppFailure):stories.parse(payload,'200')
        with self.assertRaises(AppFailure):stories.parse(self.payload,'201')
        self.fetch()
        for args in ({**self.args,'accountId':'101'}, {**self.args,'id':'999'}, {**self.args,'storyId':'../300'}):
            with self.assertRaises(AppFailure):self.e.story_media(args)
        with self.assertRaises(StorageFailure):stories.StoryStore(self.home.name,'101').load()

    def test_network_policy_rejects_seen_likes_replies_and_unknown_story_paths_before_reservation(self):
        client=new_client(self.e)
        self.addCleanup(close_client,client)
        with patch('requests.Session.send') as send:
            for method,path in [('POST','media/seen/'),('POST','media/300/story_like/'),('POST','direct_v2/threads/broadcast/reel_share/'),
                                ('GET','feed/reels_media/'),('POST','feed/user/200/story/'),('GET','feed/user/200/story/unknown/')]:
                with self.subTest(path=path),self.assertRaises(AppFailure):client.private.request(method,'https://i.instagram.com/api/v1/'+path)
            send.assert_not_called()
        self.assertEqual(self.e.data['gate'].get('attempts',0),0)

    def test_429_stops_without_fallback_and_preserves_existing_archive(self):
        self.fetch();before=self.store.view('200');calls=[]
        def limited(request,**kwargs):
            calls.append(request);return self.response(request,{'status':'fail'},429,{'Retry-After':'7200'})
        with patch('requests.Session.send',side_effect=limited),self.assertRaises(AppFailure) as caught:
            self.e.run('story_fetch',dict(self.args))
        self.assertEqual(caught.exception.code,'limited');self.assertEqual(len(calls),1)
        self.assertGreater(self.e.data['gate']['blockedUntil'],self.now[0]);self.assertEqual(before,self.store.view('200'))

    def test_cancel_after_one_asset_preserves_completed_files_and_metadata(self):
        self.calls=[]
        def stop(request,**kwargs):
            if request.url.endswith('.mp4'):
                self.e.cancel.set();self.e.check_cancel()
            return self.transport(request,**kwargs)
        with patch('requests.Session.send',side_effect=stop),self.assertRaises(AppFailure):self.e.run('story_fetch',dict(self.args))
        self.assertEqual(self.store.view('200')['items'][0]['status'],'metadata')
        self.assertTrue(self.store.contains('200','300'))

    def test_bounded_cdn_redirect_truncation_content_and_index_failure(self):
        for code,raw,headers in [(302,b'',{}),(200,b'html',{}),(200,self.raw,{'Content-Length':str(len(self.raw)+1)}),
                                 (200,b'',{'Content-Length':str(stories.MAX_MEDIA+1)})]:
            def send(request,**kwargs):return self.response(request,raw,code,headers)
            with patch('requests.Session.send',side_effect=send),self.assertRaises(AppFailure):
                stories.download('https://x.cdninstagram.com/story.jpg','image',self.e)
        with patch('requests.Session.send') as send,self.assertRaises(AppFailure):stories.download('https://example.com/steal','image',self.e)
        send.assert_not_called()
        row=stories.parse(self.payload,'200')[0]
        with patch.object(self.store,'save',side_effect=StorageFailure('full disk')),self.assertRaises(StorageFailure):
            self.store.record('200',row,self.now[0],(self.raw,'image/jpeg'))
        self.assertFalse(self.store.asset_path('300').exists())
        with patch.object(stories,'MAX_BYTES',1),self.assertRaises(AppFailure):self.store.record('200',row,self.now[0],(self.raw,'image/jpeg'))

    def test_clear_account_deletion_and_offline_mode(self):
        self.fetch();self.e.run('story_clear',dict(self.args));self.assertEqual(self.store.view('200')['items'],[])
        self.assertEqual(list(self.store.directory.glob('*.media.vault')),[])
        self.fetch();self.e.run('delete_all',{});self.assertFalse(self.store.path.exists())
        e=Engine(self.home.name);e.offline=True
        with patch('requests.Session.send') as send,self.assertRaises(AppFailure):e.run('story_fetch',self.args)
        send.assert_not_called()

    def test_corrupt_swapped_files_and_failed_cleanup_remain_retryable(self):
        self.fetch()
        path=self.store.asset_path('300');old=path.read_bytes();path.write_bytes(self.store.asset_path('301').read_bytes())
        with self.assertRaises(StorageFailure):self.store.media('200','300')
        path.write_bytes(old)
        original=Path.unlink
        def denied(path,*args,**kwargs):
            if path.name=='300.media.vault':raise PermissionError('synthetic lock')
            return original(path,*args,**kwargs)
        with patch.object(Path,'unlink',denied),self.assertRaises(PermissionError):self.store.clear('200')
        self.assertEqual(len(self.store.view('200')['items']),2)
        self.store.clear('200');self.assertEqual(self.store.view('200')['items'],[])

    def test_accounts_switch_without_crossing_archives_and_delete_only_selected_media(self):
        self.fetch()
        e=Accounts(self.home.name,clock=lambda:self.now[0],request_delay=lambda:6)
        fake=FakeClient([profile('400',username='qa.second')]);fake.user_id=400;fake.login=lambda *_:None
        fake.get_settings=lambda:{'cookies':{'ds_user_id':'400','sessionid':'SYNTHETIC-SECOND'}}
        with patch('engine.new_client',return_value=fake):e.run('login',{'username':'qa.second','password':'SYNTHETIC'})
        e.data['people']['200']=parse_profile(profile('200'));e.save()
        second=stories.StoryStore(e.vault.path.parent,'400')
        second.record('200',stories.parse(self.payload,'200')[0],self.now[0],(self.raw,'image/jpeg'))
        self.assertEqual(len(e.run('story_view',{'accountId':'400','id':'200'})['storyDetail']['items']),1)
        with self.assertRaises(AppFailure):e.story_media({**self.args,'storyId':'300'})
        e.run('delete_all',{})
        self.assertFalse(second.path.exists());self.assertTrue(self.store.asset_path('300').exists())
        self.assertEqual(e.active_id,'100');self.assertIsNone(e.snapshot()['storyDetail'])
        self.assertEqual(len(e.run('story_view',self.args)['storyDetail']['items']),2)
        e.run('delete_all',{});self.assertFalse(self.store.path.exists());self.assertFalse(self.store.asset_path('300').exists())


    def test_metadata_retry_preserves_first_capture_and_saved_asset(self):
        item=stories.parse(self.payload,'200')[0]
        self.store.record('200',item,self.now[0])
        self.store.record('200',item,self.now[0]+100,media=(self.raw,'image/jpeg'))
        self.assertEqual(self.store.load()['targets']['200']['items']['300']['capturedAt'],self.now[0])
        self.store.record('200',item,self.now[0]+200)
        self.assertEqual(self.store.load()['targets']['200']['items']['300']['status'],'saved')

    def test_duplicate_story_cannot_hide_foreign_owner_and_oversized_candidate_is_metadata_only(self):
        value=copy.deepcopy(self.payload)
        foreign=copy.deepcopy(value['reel']['items'][0]);foreign['user']={'pk':'999'}
        value['reel']['items'].append(foreign)
        with self.assertRaises(AppFailure):stories.parse(value,'200')
        value=copy.deepcopy(self.payload)
        value['reel']['items'][0]['image_versions2']['candidates'][0]['width']=stories.MAX_SIDE+1
        self.assertIsNone(stories.parse(value,'200')[0]['mediaUrl'])


if __name__=='__main__':unittest.main()
