import threading
import unittest
from thumbnails import Thumbnails


class ThumbnailTests(unittest.TestCase):
    def test_only_current_result_id_can_load_and_offline_never_downloads(self):
        events=[];reads=[]
        def reader(profile,context):
            reads.append(profile['avatarUrl']);profile['avatar']='data:image/jpeg;base64,QA=='
        pool=Thumbnails(events.append,reader=reader)
        self.addCleanup(pool.close)
        result={'token':'current'};pictures={'200':'https://cdninstagram.com/a.jpg'}
        for args,offline in [({'token':'old','id':'200'},False),({'token':'current','id':'999'},False),({'token':'current','id':'200'},True)]:
            pool.request({'id':len(events)+1,'args':args},result,pictures,offline=offline)
        self.assertEqual(reads,[]);self.assertEqual(len(events),3)
        done=threading.Event()
        pool.emit=lambda event:(events.append(event),done.set())
        pool.request({'id':4,'args':{'token':'current','id':'200'}},result,pictures)
        self.assertTrue(done.wait(2));self.assertEqual(len(reads),1)
        self.assertEqual(events[-1]['avatar'],'data:image/jpeg;base64,QA==')

    def test_cancelled_generation_drains_every_reply_and_bounds_active_downloads(self):
        entered=threading.Event();release=threading.Event();all_done=threading.Event()
        events=[];active=[0,0];lock=threading.Lock()
        def reader(profile,context):
            with lock:
                active[0]+=1;active[1]=max(active)
                if active[0]==2:entered.set()
            release.wait(2)
            with lock:active[0]-=1
            profile['avatar']='stale'
        def emit(event):
            with lock:
                events.append(event)
                if len(events)==20:all_done.set()
        pool=Thumbnails(emit,reader=reader)
        self.addCleanup(pool.close);self.addCleanup(release.set)
        for identity in range(20):
            pool.request({'id':identity,'args':{'token':'old','id':'200'}},{'token':'old'},{'200':'https://cdninstagram.com/a.jpg'})
        self.assertTrue(entered.wait(2));pool.cancel();release.set()
        self.assertTrue(all_done.wait(2));self.assertLessEqual(active[1],2)
        self.assertTrue(all(event['avatar'] is None for event in events))

    def test_cache_is_bounded_to_owner_and_url_and_later_visible_rows_get_a_fresh_deadline(self):
        from unittest.mock import patch
        clock=[0.0];events=[];reads=[];done=threading.Event()
        def reader(profile,context):
            context.check_cancel();reads.append(profile['avatarUrl']);profile['avatar']='data:image/jpeg;base64,QA=='
        pool=Thumbnails(lambda event:(events.append(event),done.set()),reader=reader);self.addCleanup(pool.close)
        def request(identity,namespace='100',token='list'):
            done.clear();pool.request({'id':len(events)+1,'args':{'token':token,'id':identity}},
                {'token':token},{identity:'https://cdninstagram.com/'+identity+'.jpg'},namespace=namespace,group='list')
            self.assertTrue(done.wait(2));self.assertIsNotNone(events[-1]['avatar'])
        with patch('thumbnails.time.monotonic',side_effect=lambda:clock[0]):
            request('200');request('200',token='another-scan')
            self.assertEqual(len(reads),1)
            clock[0]=30
            request('201',token='another-scan')
            request('200',namespace='300',token='another-scan')
            self.assertEqual(len(reads),3)
        pool.cancel(clear_cache=True);self.assertEqual(len(pool.cache),0)

    def test_cancelling_search_does_not_cancel_visible_list_photos(self):
        release=threading.Event();entered=threading.Event();done=threading.Event();events=[];count=[0];lock=threading.Lock()
        def reader(profile,context):
            with lock:
                count[0]+=1
                if count[0]==2:entered.set()
            release.wait(2);profile['avatar']='data:image/jpeg;base64,QA=='
        def emit(event):
            with lock:
                events.append(event)
                if len(events)==2:done.set()
        pool=Thumbnails(emit,reader=reader);self.addCleanup(pool.close);self.addCleanup(release.set)
        for identity,group in enumerate(('search','list')):
            pool.request({'id':identity,'args':{'token':group,'id':'200'}},{'token':group},
                         {'200':'https://cdninstagram.com/a.jpg'},namespace='100',group=group)
        self.assertTrue(entered.wait(2));pool.cancel('search');release.set();self.assertTrue(done.wait(2))
        by_id={event['id']:event['avatar'] for event in events}
        self.assertIsNone(by_id[0]);self.assertIsNotNone(by_id[1])

if __name__=='__main__':unittest.main()
