"""Overlapping server pages recover within one bounded scan, without old-list unions."""
import copy
import unittest
from unittest.mock import patch
import test_list_preparation as fixtures
from test_backend import page,profile
from test_scan_progress import ClockWait,PacedClient
from accounts import Accounts
from network import AppFailure
import list_preparation
import list_pages


class ListRecoveryTests(unittest.TestCase):
    setUp=fixtures.ListPreparationTests.setUp
    linked=fixtures.ListPreparationTests.linked
    def scan(self,*responses,one_page=False):
        client=PacedClient(self.e,responses)
        with patch.object(self.e,'client',return_value=client):
            self.e.scan({'id':'100','direction':'followers'},one_page=one_page,resume=True)
        return client

    def test_one_repair_completes_after_duplicates_and_fresh_counter(self):
        self.linked()
        client=self.scan(profile(followers=3),page(['200','201'],'first'),page(['201']),
                         page(['200','202'],'second'),page(['201']),profile(followers=3))
        result=self.e.data['lists']['100:followers']['snapshots'][-1]
        self.assertEqual(set(result['members']),{'200','201','202'})
        self.assertEqual(result['pages'],4);self.assertEqual(result['recoveryPass'],1)
        self.assertNotIn('max_id',client.calls[3][1]['params'])
        self.assertEqual(client.calls[-1][0],'users/100/info/')

    def test_pending_repair_survives_real_vault_restart_without_old_partial_union(self):
        self.linked()
        with self.assertRaises(AppFailure):self.scan(profile(followers=3),page(['999']))
        self.scan(profile(followers=3),page(['200','201'],'first'),one_page=True)
        self.scan(page(['201']),one_page=True)
        draft=self.e.data['lists']['100:followers']['draft']
        self.assertTrue(draft['restartPending']);self.assertTrue(list_preparation.resumable(draft,self.now[0]))
        self.e=Accounts(self.home.name,clock=lambda:self.now[0],request_delay=lambda:15);self.e.cancel=ClockWait(self.now)
        self.scan(page(['200','202']),profile(followers=3),one_page=True)
        result=self.e.data['lists']['100:followers']['snapshots'][-1]
        self.assertTrue(result['complete']);self.assertNotIn('999',result['members'])

    def test_incomplete_second_pass_stops_and_no_overlap_does_not_retry(self):
        self.linked()
        with self.assertRaises(AppFailure):
            self.scan(profile(followers=3),page(['200','201'],'first'),page(['201']),page(['200','201']))
        record=self.e.data['lists']['100:followers']
        self.assertEqual(record['snapshots'],[]);self.assertEqual(record['draft']['pages'],3)
        with self.assertRaises(AppFailure):self.scan(profile(followers=3),page(['200','201']))
        self.assertEqual(record['draft']['recoveryPass'],0)

    def test_changed_counter_and_rate_limit_never_certify_or_remove_prior_members(self):
        self.linked()
        with self.assertRaises(AppFailure):
            self.scan(profile(followers=3),page(['200','201'],'first'),page(['201']),page(['202']),profile(followers=4))
        record=self.e.data['lists']['100:followers'];self.assertEqual(record['snapshots'],[])
        with self.assertRaises(AppFailure):
            self.scan(profile(followers=3),page(['200','201'],'first'),page(['201']),429)
        self.assertEqual(record['snapshots'],[]);self.assertEqual(len(record['draft']['members']),2)
        self.assertEqual(self.e.data['gate']['status'],429)

    def test_repair_expiry_and_malformed_checkpoint_are_not_resumed(self):
        self.linked();self.scan(profile(followers=3),page(['200','201'],'first'),one_page=True)
        self.scan(page(['201']),one_page=True)
        draft=self.e.data['lists']['100:followers']['draft']
        self.assertFalse(list_preparation.resumable(draft,self.now[0]+601))
        bad=copy.deepcopy(self.e.data);bad['lists']['100:followers']['draft']['recoveryPass']=4
        with self.assertRaises(ValueError):list_preparation.validate(bad)

    def test_visible_partial_keeps_old_during_restart_then_publishes_greater_coverage(self):
        old={'id':'old','members':{'200':{}},'startedAt':1,'finishedAt':2}
        new={'id':'new','members':{},'startedAt':3,'finishedAt':3}
        record={'retainedPartial':old,'draft':new}
        self.assertIs(list_pages.visible_partial(record),old)
        new['members']={'200':{},'201':{}}
        self.assertIs(list_pages.visible_partial(record),new)
        legacy={'members':{'200':{}}}
        self.assertIs(list_pages.visible_partial({'retainedPartial':legacy}),legacy)

    def test_progressing_recovery_is_bounded_and_best_partial_survives_next_scan(self):
        self.linked()
        with self.assertRaises(AppFailure):
            self.scan(profile(followers=10),page(['200','201'],'first'),page(['201']),
                      page(['202']),page(['203']),page(['204']))
        record=self.e.data['lists']['100:followers']
        self.assertEqual(record['draft']['recoveryPass'],3);self.assertEqual(len(record['draft']['members']),5)
        with self.assertRaises(AppFailure):self.scan(profile(followers=10),page(['200']))
        self.assertEqual(len(record['retainedPartial']['members']),5)
