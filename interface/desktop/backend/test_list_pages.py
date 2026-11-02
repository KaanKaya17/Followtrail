import copy
import csv
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from engine import Engine
from network import AppFailure


def query(engine, **changes):
    state = engine.snapshot()
    record = state['lists'].get(changes.get('ownerId','100') + ':' + changes.get('direction','followers'), state['lists']['100:followers'])
    scans = record['snapshots']
    shown = scans[-1] if scans else record['retainedPartial'] or record['draft']
    args = dict(accountId='100', revision=state['listsRevision'], ownerId='100', direction='followers',
                scanId=shown['id'], previousScanId=scans[-2]['id'] if len(scans)>1 else '',
                comparison=False, relation='all', query='', offset=0)
    args.update(changes)
    return engine.list_pages.page(args)


class ListPagesTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-');self.addCleanup(self.home.cleanup)
        self.engine=Engine(Path(self.home.name))
        self.engine.data['account']={'id':'100','username':'qa.owner','observedAt':1}
        self.engine.offline=True
        def scan(identity, ids, complete=True):
            return {'id':identity,'ownerId':'100','direction':'followers','complete':complete,
                    'status':'complete' if complete else 'partial','pages':1,'startedAt':1,'finishedAt':2,
                    'members':{str(i):{'id':str(i),'username':f'qa.{i}','name':f'QA {i}',
                    'pictureUrl':'https://cdninstagram.com/PRIVATE.jpg'} for i in ids}}
        self.scan=scan
        self.engine.data['lists']={'100:followers':{'snapshots':[scan('before',range(250)),scan('after',range(1,251))]},
                                   '100:following':{'snapshots':[scan('following',range(0,101))]}}

    def test_complete_paging_and_global_search_are_local_without_gate_or_storage_changes(self):
        before=copy.deepcopy(self.engine.data['gate'])
        with patch.object(self.engine,'client',side_effect=AssertionError('No network')), patch.object(self.engine,'save',side_effect=AssertionError('No save')):
            pages=[query(self.engine,offset=n) for n in (0,100,200)]
            self.assertEqual([len(p['items']) for p in pages],[100,100,50])
            self.assertEqual([p['nextOffset'] for p in pages],[100,200,None])
            self.assertEqual([p['id'] for page in pages for p in page['items']],[str(i) for i in range(1,251)])
            self.assertEqual(query(self.engine,query='qa.250')['items'][0]['id'],'250')
        self.assertEqual(before,self.engine.data['gate'])
        state=self.engine.snapshot()
        self.assertNotIn('members',state['lists']['100:followers']['snapshots'][-1])
        self.assertNotIn('PRIVATE',json.dumps([state,pages]))
        self.assertEqual(state['lists']['100:followers']['snapshots'][-1]['count'],250)

    def test_comparison_and_relationship_filters_use_full_record(self):
        diff=query(self.engine,comparison=True)
        self.assertEqual([(p['id'],p['change']) for p in diff['items']],[('250','added'),('0','removed')])
        self.assertEqual((diff['added'],diff['removed']),(1,1))
        self.assertEqual(query(self.engine,relation='mutual')['total'],100)
        self.assertEqual(query(self.engine,relation='oneway')['total'],150)
        self.assertEqual(query(self.engine,query='qa.0',comparison=True)['items'][0]['photoScan'],'before')

    def test_partial_view_is_stable_until_published_and_cannot_compare(self):
        partial=self.scan('partial',[5,6],False)
        self.engine.data['lists']['100:followers']={'snapshots':[],'draft':partial}
        state=self.engine.snapshot()
        args=dict(accountId='100',revision=state['listsRevision'],ownerId='100',direction='followers',
                  scanId='partial',previousScanId='',comparison=False,relation='all',query='',offset=0)
        partial['members']['7']=self.scan('other',[7])['members']['7']
        self.assertEqual(self.engine.list_pages.page(args)['total'],2)
        with self.assertRaises(AppFailure):self.engine.list_pages.page({**args,'comparison':True})
        self.engine.snapshot()
        with self.assertRaises(AppFailure):self.engine.list_pages.page(args)
        self.assertEqual(query(self.engine)['total'],3)

    def test_scope_revision_and_request_shape_fail_closed(self):
        for change in ({'accountId':'200'},{'revision':'old'},{'offset':True},{'offset':-1},
                       {'offset':40001},{'query':'x'*257},{'relation':'bad'},{'direction':'bad'},
                       {'scanId':'missing'},{'path':'secret'}):
            with self.subTest(change=change), self.assertRaises(AppFailure):query(self.engine,**change)

    def test_unrelated_changes_reuse_view_and_scan_replacement_invalidates_it(self):
        old=self.engine.snapshot()['listsRevision']
        self.engine.data['settings']['compact']=True
        self.assertEqual(self.engine.snapshot()['listsRevision'],old)
        self.engine.data['lists']['100:followers']['snapshots'][-1]=self.scan('after',range(500,750))
        self.assertNotEqual(self.engine.snapshot()['listsRevision'],old)
        self.assertEqual(query(self.engine)['items'][0]['id'],'500')

    def test_csv_exports_every_member_and_sanitizes_formula_cells(self):
        scan=self.engine.data['lists']['100:followers']['snapshots'][-1]
        scan['members']['250']['name']='=SUM(1,2)'
        target=Path(self.home.name)/'out.csv'
        self.engine.export_list({'path':str(target),'accountId':'100','id':'100','direction':'followers','snapshotId':'after'})
        with target.open(encoding='utf-8-sig',newline='') as stream:rows=list(csv.reader(stream))
        self.assertEqual(len(rows),251);self.assertEqual(rows[-1][2],"'=SUM(1,2)")
        with self.assertRaises(AppFailure):self.engine.export_list({'accountId':'200','path':str(target)})

    def test_case_and_diacritics_in_names_can_be_searched(self):
        self.engine.data['lists']['100:followers']['snapshots'][-1]['members']['250']['name']='İpek Şahin'
        self.assertEqual(query(self.engine,query='ipek sahin')['items'][0]['id'],'250')
