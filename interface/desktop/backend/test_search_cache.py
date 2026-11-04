import copy
import tempfile
import unittest
from unittest.mock import patch
from engine import Engine
from network import AppFailure,parse_profile
from search_cache import SearchCache
from test_backend import profile,page
from test_scan_progress import PacedClient,ClockWait


class CachePolicyTests(unittest.TestCase):
    def setUp(self):self.now=0;self.cache=SearchCache(lambda:self.now);self.cache.bind('100')
    def test_expiry_empty_results_and_reads_do_not_extend_lifetime(self):
        self.cache.put('100','empty',[],{})
        self.assertEqual(self.cache.get('100','empty')['users'],[])
        self.now=59.999;self.assertIsNotNone(self.cache.get('100','empty'))
        self.now=60;self.assertIsNone(self.cache.get('100','empty'))
    def test_last_six_queries_and_facts_are_copied(self):
        for i in range(7):self.cache.put('100',str(i),[{'id':'200','username':'qa.person'}],{})
        self.assertIsNone(self.cache.get('100','0'))
        value=self.cache.get('100','6');value['users'].clear()
        self.assertEqual(len(self.cache.get('100','6')['users']),1)
    def test_owner_switch_and_clear_retire_old_thumbnail_tokens(self):
        value=self.cache.put('100','qa',[{'id':'200'}],{'200':'https://cdninstagram.com/synthetic'})
        self.assertIsNotNone(self.cache.picture('100',value['token'],'200'))
        self.assertIsNone(self.cache.picture('300',value['token'],'200'))
        self.cache.bind('300');self.assertIsNone(self.cache.get('100','qa'))
        self.assertIsNone(self.cache.picture('100',value['token'],'200'))
    def test_clock_rollback_expires_results(self):
        self.now=10;self.cache.put('100','qa',[],{});self.now=9
        self.assertIsNone(self.cache.get('100','qa'))


class SearchFlowTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-');self.addCleanup(self.home.cleanup)
        self.now=[1800000000.0];self.e=Engine(self.home.name,clock=lambda:self.now[0])
        self.e.cancel=ClockWait(self.now);self.e.record_profile(parse_profile(profile()),own=True)
        self.e.data['session']={'cookies':{'ds_user_id':'100','sessionid':'SYNTHETIC'}};self.e.save()
        self.cacheNow=0;self.e.search_cache.clock=lambda:self.cacheNow
    def search(self,q='qa',payload=None):
        client=PacedClient(self.e,[payload or page(['200'])])
        with patch.object(self.e,'client',return_value=client):result=self.e.run('search_people',{'query':q})
        return result['searchResults'],client
    def test_same_query_reuses_actual_production_result_without_gate_or_disk_write(self):
        first,client=self.search();gate=copy.deepcopy(self.e.data['gate']);raw=self.e.vault.path.read_bytes()
        self.e.run('settings',{'compact':True})
        raw=self.e.vault.path.read_bytes()
        with patch.object(self.e,'client',side_effect=AssertionError('No client for cache hit')):
            second=self.e.run('search_people',{'query':'qa'})['searchResults']
        self.assertEqual(first['token'],second['token']);self.assertEqual(gate,self.e.data['gate'])
        self.assertEqual(raw,self.e.vault.path.read_bytes());self.assertEqual(len(client.calls),1)
    def test_images_stay_available_after_other_operations_and_other_queries(self):
        payload=page(['200']);payload['users'][0]['profile_pic_url']='https://cdninstagram.com/synthetic.jpg'
        first,_=self.search(payload=payload);self.e.run('settings',{'compact':True});self.search('other')
        self.assertIsNotNone(self.e.search_cache.picture('100',first['token'],'200'))
        self.assertNotIn('synthetic.jpg',str(first));self.cacheNow=60
        self.assertIsNone(self.e.search_cache.picture('100',first['token'],'200'))
    def test_expiration_and_errors_do_not_create_a_success_cache_entry(self):
        self.search();self.cacheNow=60
        _,client=self.search();self.assertEqual(len(client.calls),1)
        with patch.object(self.e,'client',return_value=PacedClient(self.e,[429])),self.assertRaises(AppFailure):
            self.e.run('search_people',{'query':'limited'})
        self.assertIsNone(self.e.search_cache.get('100','limited'))
    def test_profile_erasure_disconnect_and_restart_do_not_keep_searches(self):
        self.search();self.e.record_profile(parse_profile(profile('200')))
        self.e.run('remove_person',{'id':'200'});self.assertIsNone(self.e.search_cache.get('100','qa'))
        self.search();self.e.run('disconnect',{});self.assertIsNone(self.e.search_cache.get('100','qa'))
        self.assertIsNone(Engine(self.home.name).search_cache.get('100','qa'))
