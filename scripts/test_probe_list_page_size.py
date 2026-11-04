import contextlib
import importlib.util
import io
from pathlib import Path
from types import SimpleNamespace
import unittest

spec=importlib.util.spec_from_file_location('page_size_probe',Path(__file__).with_name('probe-list-page-size.py'))
probe=importlib.util.module_from_spec(spec);spec.loader.exec_module(probe)


def page(ids,cursor=''):
    return {'status':'ok','users':[{'pk':str(i),'username':f'qa.{i}'} for i in ids],'next_max_id':cursor}


class Client:
    rank_token='synthetic'
    def __init__(self,pages):self.pages=iter(pages);self.calls=[]
    def private_request(self,path,params):
        self.calls.append((path,params));value=next(self.pages)
        if isinstance(value,Exception):raise value
        return value


class ProbeTests(unittest.TestCase):
    def compare(self,client,**kwargs):
        with contextlib.redirect_stdout(io.StringIO()):
            return probe.compare(SimpleNamespace(data={'gate':{'status':200}}),client,{'id':'100','following':3},'following',**kwargs)
    def test_requested_sizes_start_fresh_and_follow_only_their_own_cursor(self):
        client=Client([page([1,2],'next50'),page([2,3]),page([1,2,3]),page([1,2,3])])
        rows,stopped=self.compare(client)
        self.assertIsNone(stopped)
        self.assertEqual([call[1]['count'] for call in client.calls],[50,50,100,200])
        self.assertEqual([call[1].get('max_id') for call in client.calls],[None,'next50',None,None])
        self.assertEqual(rows[1]['repeatedAcrossPages'],1)
        self.assertTrue(rows[1]['matchesExpectedAtEnd'])
        self.assertFalse(any('users' in row or 'cursor' in row for row in rows))
    def test_coverage_restriction_never_follows_a_cursor_or_certifies_a_full_list(self):
        client=Client([dict(page([1],'must-not-follow'),should_limit_list_of_followers=True) for _ in range(3)])
        rows,stopped=self.compare(client)
        self.assertEqual(stopped,'server_coverage_restriction');self.assertEqual(len(client.calls),3)
        self.assertTrue(all(call[1].get('max_id') is None for call in client.calls))
    def test_failure_preserves_completed_measurements_without_retry(self):
        client=Client([page([1,2,3]),probe.AppFailure('limited','synthetic')]);rows=[]
        with self.assertRaises(probe.AppFailure):self.compare(client,rows=rows)
        self.assertEqual(len(rows),1);self.assertEqual(len(client.calls),2)
    def test_invalid_probe_bounds_send_nothing(self):
        client=Client([])
        for kwargs in (dict(sizes=(1000,)),dict(pages=5),dict(sizes=(50,50))):
            with self.assertRaises(ValueError):self.compare(client,**kwargs)
        self.assertEqual(client.calls,[])


if __name__=='__main__':unittest.main()
