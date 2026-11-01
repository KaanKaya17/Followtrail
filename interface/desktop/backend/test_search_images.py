import threading
import unittest
from search_images import SearchImages


class SearchImagesTests(unittest.TestCase):
    def test_only_current_result_id_can_load_and_offline_never_downloads(self):
        events=[];reads=[]
        def reader(profile,context):
            reads.append(profile['avatarUrl']);profile['avatar']='data:image/jpeg;base64,QA=='
        pool=SearchImages(events.append,reader=reader)
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
        pool=SearchImages(emit,reader=reader)
        self.addCleanup(pool.close);self.addCleanup(release.set)
        for identity in range(20):
            pool.request({'id':identity,'args':{'token':'old','id':'200'}},{'token':'old'},{'200':'https://cdninstagram.com/a.jpg'})
        self.assertTrue(entered.wait(2));pool.cancel();release.set()
        self.assertTrue(all_done.wait(2));self.assertLessEqual(active[1],2)
        self.assertTrue(all(event['avatar'] is None for event in events))

if __name__=='__main__':unittest.main()
