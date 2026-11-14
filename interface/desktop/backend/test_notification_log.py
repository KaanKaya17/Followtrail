"""Production observation grouping, per-account selection and encrypted log tests."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from engine import Engine
from network import AppFailure, parse_profile
from storage import StorageFailure, protect
from test_backend import FakeClient, profile
import notification_log


class NotificationLogTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.e=Engine(self.home.name,clock=lambda:1800000000)
        self.e.record_profile(parse_profile(profile()),own=True)
        self.e.record_profile(parse_profile(profile('200')))
        self.e.data['session']={'cookies':{'ds_user_id':'100','sessionid':'SYNTHETIC-SECRET'}}
        self.e.settings({'notifications':True})

    def tearDown(self):self.home.cleanup()

    def refresh(self,identity='200',**fields):
        client=FakeClient([profile(identity,**fields)])
        with patch.object(self.e,'client',return_value=client):
            return self.e.run('refresh',{'id':identity})

    def rules(self,identity='200',enabled=True,**fields):
        self.e.run('notification_rule',{'id':identity,'enabled':enabled,'fields':fields})

    def test_first_observation_and_no_change_create_no_notification(self):
        self.assertEqual(self.e.data['notificationLog'],[])
        self.refresh()
        self.assertEqual(self.e.data['notificationLog'],[])

    def test_one_refresh_groups_all_changes_with_original_values(self):
        state=self.refresh(followers=9,following=7,username='qa.renamed',biography='Changed biography')
        self.assertEqual(len(state['notificationLog']),1)
        entry=state['notificationLog'][0]
        self.assertEqual(len(entry['changes']),4)
        self.assertEqual(len(entry['selectedChangeIds']),4)
        self.assertTrue(entry['desktopEligible'])
        changed={item['category']:item for item in entry['changes']}
        self.assertEqual(changed['followers']['before'],{'followers':2})
        self.assertEqual(changed['followers']['after'],{'followers':9})
        self.assertEqual(changed['username']['after'],{'username':'qa.renamed'})
        self.assertIsNone(entry['readAt'])

    def test_muted_count_is_retained_in_log_but_not_selected_for_desktop(self):
        self.rules(followers=False,following=False)
        state=self.refresh(followers=9,following=7,username='qa.renamed')
        entry=state['notificationLog'][0]
        selected=[item['category'] for item in entry['changes'] if item['eventId'] in entry['selectedChangeIds']]
        self.assertEqual(selected,['username'])
        self.assertEqual(len(entry['changes']),3)
        self.assertTrue(entry['desktopEligible'])

    def test_muted_account_still_logs_and_does_not_mute_other_accounts(self):
        self.rules(enabled=False)
        state=self.refresh(followers=8)
        self.assertFalse(state['notificationLog'][0]['desktopEligible'])
        state=self.refresh(identity='100',followers=9)
        self.assertTrue(state['notificationLog'][0]['desktopEligible'])
        self.assertEqual(state['notificationLog'][0]['changes'][0]['personId'],'100')

    def test_global_off_keeps_history_and_does_not_retroactively_select_it(self):
        self.e.settings({'notifications':False})
        state=self.refresh(followers=8)
        entry=copy.deepcopy(state['notificationLog'][0])
        self.assertFalse(entry['desktopEligible'])
        self.e.settings({'notifications':True})
        self.assertEqual(self.e.data['notificationLog'][0],entry)

    def test_consecutive_reads_are_distinct_even_at_same_clock_time(self):
        self.refresh(followers=8);self.refresh(followers=9)
        logs=self.e.data['notificationLog']
        self.assertEqual(len(logs),2)
        self.assertNotEqual(logs[0]['id'],logs[1]['id'])

    def test_worker_operation_correlation_is_the_persisted_log_group_identity(self):
        with patch.object(self.e,'client',return_value=FakeClient([profile('200',followers=8)])):
            state=self.e.run('refresh',{'id':'200'},group_id='qa-worker-operation')
        self.assertEqual(state['notificationLog'][0]['id'],'qa-worker-operation')

    def test_partial_operation_preserves_committed_profile_changes_in_one_log(self):
        client=FakeClient([profile(followers=8),AppFailure('connection','Synthetic list failure')])
        with patch.object(self.e,'client',return_value=client),self.assertRaises(AppFailure):
            self.e.run('scan',{'direction':'followers'})
        state=Engine(self.home.name).snapshot()
        self.assertEqual(len(state['notificationLog']),1)
        self.assertEqual(state['notificationLog'][0]['changes'][0]['after'],{'followers':8})
        self.assertFalse(any(change['category']=='lists' for change in state['notificationLog'][0]['changes']))

    def test_rule_reopen_and_read_status_use_real_encrypted_store(self):
        self.rules(followers=False)
        state=self.refresh(followers=9,username='qa.changed')
        entry=state['notificationLog'][0]
        self.e.run('read_notification',{'id':entry['id']})
        reopened=Engine(self.home.name).snapshot()
        self.assertEqual(reopened['notificationLog'][0]['readAt'],1800000000)
        self.assertFalse(reopened['notificationRules']['200']['fields']['followers'])
        raw=self.e.vault.path.read_bytes()
        self.assertNotIn(b'qa.changed',raw)
        self.assertNotIn('SYNTHETIC-SECRET',json.dumps(reopened))

    def test_original_evidence_and_selection_do_not_change_with_preferences(self):
        self.rules(followers=False)
        self.refresh(followers=9,username='qa.changed')
        before=copy.deepcopy(self.e.data['notificationLog'][0])
        self.rules(followers=True,username=False)
        self.refresh(followers=10,username='qa.next')
        self.assertEqual(self.e.data['notificationLog'][1],before)

    def test_read_all_covers_offscreen_entries_and_keeps_new_notifications_unread(self):
        from history_pages import HistoryPages

        for index in range(55):
            self.e.event('profile', 'Synthetic count', '200', {'followers': index}, {'followers': index + 1})
        first = self.e.data['notificationLog'][0]
        first['readAt'] = 1799999999
        before = copy.deepcopy(self.e.data['notificationLog'])
        with patch.object(self.e.vault, 'save', wraps=self.e.vault.save) as save:
            self.e.run('read_all_notifications', {'accountId': '100'})
            self.e.run('read_all_notifications', {'accountId': '100'})
            self.assertEqual(save.call_count, 1)
        expected = [{**entry, 'readAt': entry['readAt'] or 1800000000} for entry in before]
        reopened = Engine(self.home.name)
        self.assertEqual(reopened.data['notificationLog'], expected)
        reopened.event('profile', 'Synthetic count', '200', {'followers': 55}, {'followers': 56})
        counts = HistoryPages().publish(reopened.snapshot())['historyMeta']['notificationCounts']
        self.assertEqual(counts['all']['all'], 56)
        self.assertEqual(counts['unread']['all'], 1)

    def test_old_vault_has_default_rules_and_no_fabricated_historical_notifications(self):
        self.refresh(followers=9)
        old=copy.deepcopy(self.e.data)
        old.pop('notificationRules');old.pop('notificationLog');self.e.vault.save(old)
        reopened=Engine(self.home.name)
        self.assertEqual(reopened.data['notificationLog'],[])
        self.assertTrue(notification_log.rule(reopened.data,'200')['enabled'])
        self.assertTrue(all(notification_log.rule(reopened.data,'200')['fields'].values()))
        self.assertTrue(any(item['type']=='profile' for item in reopened.data['events']))

    def test_unknown_fields_or_accounts_are_rejected_without_rule_changes(self):
        for args in ({'id':'200','enabled':True,'fields':{'password':True}},
                     {'id':'200','enabled':True,'fields':{'followers':'false'}},
                     {'id':'200','enabled':'false','fields':{}},
                     {'id':'999','enabled':True,'fields':{}}):
            with self.subTest(args=args),self.assertRaises(AppFailure):self.e.run('notification_rule',args)
        self.assertEqual(self.e.data['notificationRules'],{})

    def test_backup_restores_log_preferences_and_read_state_without_session(self):
        self.rules(followers=False);state=self.refresh(followers=9,username='qa.changed')
        self.e.run('read_notification',{'id':state['notificationLog'][0]['id']})
        file=Path(self.home.name)/'qa.trackerbackup';self.e.export_backup(file)
        before=copy.deepcopy(self.e.data['notificationLog'])
        self.e.import_backup(file)
        self.assertIsNone(self.e.data['session'])
        self.assertEqual(self.e.data['notificationLog'],before)
        self.assertFalse(self.e.data['notificationRules']['200']['fields']['followers'])

    def test_disk_failure_rolls_back_rule_edits(self):
        with patch.object(self.e.vault,'save',side_effect=StorageFailure('Synthetic disk error')),self.assertRaises(AppFailure):
            self.rules(followers=False)
        self.assertEqual(self.e.data['notificationRules'],{})

    def test_capacity_retains_latest_groups_and_read_does_not_add_an_entry(self):
        for index in range(505):
            self.e.event('profile','Synthetic count','200',{'followers':index},{'followers':index+1})
        self.assertEqual(len(self.e.data['notificationLog']),500)
        current=self.e.data['notificationLog'][0]
        self.assertEqual(current['changes'][0]['after'],{'followers':505})
        self.e.run('read_notification',{'id':current['id']})
        self.assertEqual(len(self.e.data['notificationLog']),500)

    def test_unsupported_missing_and_null_fields_create_no_false_changes(self):
        self.refresh(media_count=None)
        self.assertEqual(self.e.data['notificationLog'],[])
        self.rules(picture=False,access=False,lists=False)
        self.e.event('profile','Synthetic suspicion','200',None,{'status':'suspected'},topic='access')
        self.assertEqual(self.e.data['notificationLog'][0]['changes'][0]['category'],'access')
        self.assertFalse(self.e.data['notificationLog'][0]['desktopEligible'])


if __name__=='__main__':unittest.main(verbosity=2)
