"""Real local DPAPI lifecycle; no Instagram traffic or personal vaults."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import data_privacy
import daily_stats
import stories
import test_stories
from accounts import Accounts
from engine import Engine, validate_archive
from network import AppFailure, parse_profile
from storage import protect
from test_backend import profile, FakeClient


class PrivacyTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-');self.addCleanup(self.home.cleanup)
        self.now=1800000000
        self.e=Engine(self.home.name,clock=lambda:self.now)
        self.e.record_profile(parse_profile(profile()),own=True)
        self.e.record_profile(parse_profile(profile('200')))
        self.e.record_profile(parse_profile(profile('300',username='qa.other')))
        self.e.save()

    def scan(self,target,at):
        return dict(id=str(at),ownerId=target,direction='followers',startedAt=at,finishedAt=at,
                    members={'200':dict(id='200',username='qa.person',name='QA')},
                    complete=True,status='complete',pages=1,expected=1)

    def test_default_preserves_archives_and_migration_does_not_write_or_send(self):
        self.e.data['settings'].pop('historyRetentionDays');self.e.data['settings'].pop('dailyRetentionDays')
        self.e.data.pop('privacyMaintenance');self.e.save();before=self.e.vault.path.read_bytes()
        with patch('requests.Session.send',side_effect=AssertionError('No network')):
            reopened=Engine(self.home.name);reopened.run('state',{})
        self.assertEqual(reopened.data['settings']['historyRetentionDays'],0)
        self.assertEqual(reopened.vault.path.read_bytes(),before)

    def test_profile_erasure_covers_all_owned_archives_and_recovery_copy(self):
        self.e.run('wayback',dict(id='200',enabled=True))
        self.e.data['lists']={'200:followers':dict(snapshots=[self.scan('200',self.now)]),
                              '100:followers':dict(snapshots=[self.scan('100',self.now)])}
        self.now+=1;self.e.record_profile(parse_profile(profile('200',followers=9)))
        self.e.notification_rule(dict(id='200',enabled=True,fields={}))
        self.e.save();gate=copy.deepcopy(self.e.data['gate'])
        self.e.run('remove_person',dict(id='200'))
        validate_archive(self.e.data)
        for field in ('people','history','automaticAttempts','access','notificationRules'):
            self.assertNotIn('200',self.e.data[field])
        self.assertNotIn('200',self.e.data['wayback']['profiles'])
        self.assertNotIn('200:followers',self.e.data['lists'])
        self.assertIn('200',self.e.data['lists']['100:followers']['snapshots'][0]['members'])
        self.assertIn('300',self.e.data['people']);self.assertEqual(gate,self.e.data['gate'])
        self.assertFalse(any(e['personId']=='200' for e in self.e.data['events']))
        self.assertFalse(any(c['personId']=='200' for e in self.e.data['notificationLog'] for c in e['changes']))
        self.assertEqual(self.e.daily_store().documents('200'),[])
        previous=json.loads(protect(self.e.vault.backup.read_bytes(),decrypt=True))
        self.assertNotIn('200',previous['history']);self.assertNotIn('200',previous['people'])
        self.e.run('remove_person',dict(id='200'))  # Idempotent retry after a partial completion.
        self.assertIn('300',Engine(self.home.name).data['people'])

    def test_retention_preserves_baselines_but_not_old_events_or_old_visual_records(self):
        self.e.run('wayback',dict(id='200',enabled=True))
        old=self.now
        self.e.data['lists']={'200:followers':dict(snapshots=[self.scan('200',old),self.scan('200',old+1)])}
        self.now+=31*data_privacy.DAY
        self.e.settings(dict(historyRetentionDays=30,dailyRetentionDays=0,applyRetention=True))
        self.assertEqual(len(self.e.data['history']['200']),1)
        self.assertEqual(len(self.e.data['lists']['200:followers']['snapshots']),1)
        self.assertEqual(self.e.data['events'],[])
        self.assertEqual(self.e.data['wayback']['profiles']['200']['entries'],[])
        self.assertIn('200',self.e.data['people']);self.assertIn('300',self.e.data['people'])
        before=self.e.vault.path.read_bytes();self.e.run('state',{})
        self.assertEqual(before,self.e.vault.path.read_bytes())
        self.e.settings(dict(historyRetentionDays=0,dailyRetentionDays=0))
        self.assertEqual(self.e.data['events'],[])

    def test_daily_retention_watermark_prevents_backfill_after_period_is_extended(self):
        old=copy.deepcopy(self.e.data['history']['200'])
        store=self.e.daily_store();self.assertTrue(store.documents('200'))
        self.now+=31*data_privacy.DAY
        self.e.settings(dict(historyRetentionDays=0,dailyRetentionDays=30,applyRetention=True))
        self.assertEqual(store.documents('200'),[])
        self.e.settings(dict(dailyRetentionDays=0))
        store.record('200',old)
        self.assertEqual(store.documents('200'),[])
        self.e.record_profile(parse_profile(profile('200',followers=5)))
        self.assertEqual(store.query('200','followers',0,round(self.now*1000))['observedDays'],1)

    def test_invalid_mixed_settings_do_not_apply_retention_even_in_memory(self):
        before=copy.deepcopy(self.e.data['settings'])
        for args in (dict(historyRetentionDays=30,interval=2),dict(historyRetentionDays=True),dict(dailyRetentionDays=7),dict(applyRetention=False)):
            with self.assertRaises(AppFailure):self.e.settings(args)
            self.assertEqual(self.e.data['settings'],before)

    def test_failed_cleanup_keeps_registry_and_is_retryable(self):
        with patch.object(stories.StoryStore,'clear',side_effect=OSError('synthetic disk failure')):
            with self.assertRaises(AppFailure):self.e.run('remove_person',dict(id='200'))
        self.assertIn('200',self.e.data['people'])
        self.e.run('remove_person',dict(id='200'));self.assertNotIn('200',self.e.data['people'])

    def test_failed_retention_is_visible_and_explicit_retry_clears_flag(self):
        self.now+=31*data_privacy.DAY
        with patch.object(stories.StoryStore,'prune',side_effect=OSError('synthetic disk failure')):
            with self.assertRaises(AppFailure):self.e.settings(dict(historyRetentionDays=30))
        self.assertTrue(self.e.snapshot()['privacyMaintenance']['failed'])
        self.assertTrue(self.e.data['events'])
        self.e.settings(dict(applyRetention=True))
        self.assertFalse(self.e.snapshot()['privacyMaintenance']['failed'])
        self.assertEqual(self.e.data['events'],[])

    def test_preferences_are_account_local_and_erasure_does_not_touch_another_vault(self):
        app=Accounts(self.home.name,clock=lambda:self.now)
        app.settings(dict(historyRetentionDays=30))
        fake=FakeClient([profile('400',username='qa.second')]);fake.user_id=400
        fake.login=lambda *_:None
        fake.get_settings=lambda:dict(cookies=dict(ds_user_id='400',sessionid='SYNTHETIC-SECOND'))
        with patch.object(app, 'new_client',return_value=fake):app.run('login',dict(username='qa.second',password='SYNTHETIC'))
        app.record_profile(parse_profile(profile('200')))
        app.data['people']['200']['note']='SECOND-ACCOUNT-NOTE';app.save()
        self.assertEqual(app.data['settings']['historyRetentionDays'],0)
        second=app.vault.path;before=second.read_bytes()
        app.run('switch_account',dict(id='100'))
        app.run('remove_person',dict(id='200'))
        self.assertEqual(second.read_bytes(),before)
        self.assertEqual(Accounts(self.home.name,clock=lambda:self.now).data['settings']['historyRetentionDays'],30)
        app.run('switch_account',dict(id='400'))
        self.assertEqual(app.data['people']['200']['note'],'SECOND-ACCOUNT-NOTE')


class StoryPrivacyTests(unittest.TestCase):
    def test_retention_deletes_owned_media_and_metadata_without_network(self):
        case=test_stories.StoryTests();case.setUp();self.addCleanup(case.doCleanups)
        case.fetch();self.assertEqual(len(case.store.view('200')['items']),2)
        before=case.now[0]+1
        with patch('requests.Session.send',side_effect=AssertionError('No network')):
            case.store.prune(before)
        self.assertEqual(case.store.view('200')['items'],[])
        self.assertEqual([p for p in case.store.directory.iterdir() if p.name!='index.vault'],[])
