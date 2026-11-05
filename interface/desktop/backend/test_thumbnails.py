import threading
import unittest
from thumbnails import Thumbnails


class ThumbnailTests(unittest.TestCase):
    def test_cancelled_last_subscriber_and_late_completion_do_not_remove_new_flight(self):
        first_entered=threading.Event();fresh_entered=threading.Event();old_release=threading.Event();fresh_release=threading.Event()
        done=threading.Event();events=[];calls=[0];lock=threading.Lock()
        def reader(image,context):
            with lock: calls[0]+=1;number=calls[0]
            (first_entered if number==1 else fresh_entered).set()
            (old_release if number==1 else fresh_release).wait(3)
            image['avatar']='old' if number==1 else 'fresh'
        def emit(event):
            with lock:
                events.append(event)
                if len(events)==3:done.set()
        pool=Thumbnails(emit,reader=reader);self.addCleanup(pool.close)
        self.addCleanup(old_release.set);self.addCleanup(fresh_release.set)
        def request(number):
            pool.request({'id':number,'args':{'token':'same','id':'200'}},{'token':'same'},
                         {'200':'https://cdninstagram.com/a.jpg'},namespace='100')
        request(1);self.assertTrue(first_entered.wait(2))
        old=next(iter(pool.jobs));pool.cancel('search')
        request(2);self.assertTrue(fresh_entered.wait(2))
        old_release.set();old.result(timeout=2)
        request(3);fresh_release.set();self.assertTrue(done.wait(2))
        self.assertEqual(calls[0],2)
        self.assertEqual({e['id']:e['avatar'] for e in events},{1:None,2:'fresh',3:'fresh'})

    def test_coalesced_waiters_are_bounded_and_each_cancelled_reply_is_drained_once(self):
        entered=threading.Event();release=threading.Event();events=[];reads=[]
        def reader(image,context):
            reads.append(True);entered.set();release.wait(3);image['avatar']='image'
        pool=Thumbnails(events.append,reader=reader);self.addCleanup(pool.close);self.addCleanup(release.set)
        for number in range(21):
            pool.request({'id':number,'args':{'token':'same','id':'200'}},{'token':'same'},
                         {'200':'https://cdninstagram.com/a.jpg'},namespace='100')
        self.assertTrue(entered.wait(2));self.assertEqual(len(reads),1)
        self.assertEqual(len(events),1);self.assertIsNone(events[0]['avatar'])
        pool.cancel();release.set();pool.close()
        self.assertEqual(len(events),21);self.assertEqual(len({e['id'] for e in events}),21)
        self.assertTrue(all(e['avatar'] is None for e in events))
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
            target=str(200+identity)
            pool.request({'id':identity,'args':{'token':'old','id':target}},{'token':'old'},{target:'https://cdninstagram.com/'+target+'.jpg'})
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
                if count[0]==1:entered.set()
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
        self.assertEqual(count[0],1)
        by_id={event['id']:event['avatar'] for event in events}
        self.assertIsNone(by_id[0]);self.assertIsNotNone(by_id[1])

if __name__=='__main__':unittest.main()
