"""Real DPAPI/storage boundaries; Instagram transport remains synthetic."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from accounts import Accounts
from engine import Engine
from network import AppFailure, parse_profile
from storage import StorageFailure, Vault
from test_backend import FakeClient, profile


class AccountsTests(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.now = 1800000000
        old = Engine(self.home.name, clock=lambda: self.now)
        old.record_profile(parse_profile(profile()), own=True)
        old.data['session'] = {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC-FIRST'}}
        old.data['people']['200'] = {'id':'200', 'username':'qa.person', 'note':'FIRST-NOTE'}
        old.save()
        self.original_bytes = old.vault.path.read_bytes()
        self.e = Accounts(self.home.name, clock=lambda: self.now, request_delay=lambda:15)

    def tearDown(self):
        self.home.cleanup()

    def add(self, identity='300', username='qa.second'):
        fake = FakeClient([profile(identity, username=username)])
        fake.user_id = int(identity)
        fake.login = lambda *_: None
        fake.get_settings = lambda: {'cookies': {'ds_user_id':identity, 'sessionid':'SYNTHETIC-'+identity}}
        with patch.object(self.e, 'new_client', return_value=fake):
            self.e.run('login', {'username':username,'password':'SYNTHETIC-PASSWORD'})
        return fake

    def test_migration_preserves_session_and_existing_primary_bytes(self):
        self.assertEqual(self.e.vault.path.read_bytes(), self.original_bytes)
        self.assertEqual(self.e.active_id, '100')
        self.assertEqual(self.e.snapshot()['accounts'][0]['username'], 'qa.owner')
        self.assertEqual(self.e.catalog['accounts']['100']['slot'], 'legacy')

    def test_only_successful_user_account_switch_clears_search_cache(self):
        self.add()
        self.e.run('switch_account',{'id':'100'})
        self.e.search_cache.bind('100')
        result=self.e.search_cache.put('100','qa',[{'id':'200','username':'qa.person'}],{'200':'https://cdninstagram.com/synthetic.jpg'})
        with self.assertRaises(AppFailure):
            self.e.run('switch_account',{'id':'999'})
        self.assertIsNotNone(self.e.search_cache.get('100','qa'))
        self.e.run('switch_account',{'id':'100'})
        self.assertIsNotNone(self.e.search_cache.picture('100',result['token'],'200'))
        self.e.run('switch_account',{'id':'300'})
        self.assertIsNone(self.e.search_cache.get('100','qa'))
        self.assertIsNone(self.e.search_cache.picture('100',result['token'],'200'))

    def test_new_account_independent_vault_and_local_switch(self):
        self.add()
        self.assertEqual(self.e.active_id, '300')
        self.assertFalse(self.e.data['people'])
        self.e.data['people']['200'] = {'id':'200', 'username':'qa.person', 'note':'SECOND-NOTE'}
        self.e.save()
        with patch.object(self.e, 'new_client', side_effect=AssertionError('switch must be local')):
            self.e.run('switch_account', {'id':'100'})
        self.assertEqual(self.e.data['people']['200']['note'], 'FIRST-NOTE')
        self.assertEqual(self.e.data['session']['cookies']['sessionid'], 'SYNTHETIC-FIRST')
        self.e.run('switch_account', {'id':'300'})
        self.assertEqual(self.e.data['people']['200']['note'], 'SECOND-NOTE')
        self.assertEqual(len(self.e.snapshot()['accounts']), 2)

    def test_catalog_and_vaults_encrypted_no_auth_projection(self):
        self.add()
        for path in Path(self.home.name).rglob('*.vault'):
            raw = path.read_bytes()
            for text in (b'qa.owner', b'qa.second', b'SYNTHETIC-', b'FIRST-NOTE'):
                self.assertNotIn(text, raw)
        snapshot = json.dumps(self.e.snapshot())
        self.assertNotIn('sessionid', snapshot)
        self.assertNotIn('SYNTHETIC-', snapshot)
        self.assertNotIn('slot', json.dumps(self.e.snapshot()['accounts']))

    def test_same_numeric_identity_renamed_keeps_observations(self):
        self.add()
        self.now += 901
        self.add('100', 'qa.renamed')
        self.assertEqual(len(self.e.catalog['accounts']), 2)
        self.assertEqual(self.e.data['people']['200']['note'], 'FIRST-NOTE')
        self.assertEqual(self.e.data['account']['username'], 'qa.renamed')

    def test_restart_keeps_active_account_preferences_and_independent_settings(self):
        self.e.run('settings', {'automatic':True, 'notifications':True, 'interval':5})
        self.add()
        self.e.run('settings', {'language':'en', 'theme':'oled', 'compact':True})
        e = Accounts(self.home.name, clock=lambda:self.now)
        self.assertEqual(e.active_id, '300')
        self.assertTrue(e.data['settings']['automatic'])
        self.assertTrue(e.data['settings']['automaticLists'])
        e.run('switch_account', {'id':'100'})
        self.assertTrue(e.data['settings']['automatic'])
        self.assertTrue(e.data['settings']['notifications'])
        self.assertEqual(e.data['settings']['interval'], 5)
        self.assertEqual(e.data['settings']['language'], 'en')
        self.assertEqual(e.data['settings']['theme'], 'oled')

    def test_429_and_pacing_shared_without_account_rotation(self):
        self.add()
        self.e.data['gate']['nextAt'] = self.now + 15
        self.e.limited()
        hold = copy.deepcopy(self.e.data['gate'])
        self.e.run('switch_account', {'id':'100'})
        self.assertEqual(self.e.data['gate']['blockedUntil'], hold['blockedUntil'])
        self.assertEqual(self.e.data['gate']['loginAfter'], hold['loginAfter'])
        self.assertTrue(self.e.snapshot()['backgroundMonitor']['held'])
        with self.assertRaises(AppFailure):
            self.e.before_request()
        restart = Accounts(self.home.name, clock=lambda:self.now)
        self.assertTrue(restart.catalog['limitedHold'])
        restart.run('delete_all', {})
        self.assertEqual(restart.data['gate']['blockedUntil'], hold['blockedUntil'])

    def test_background_services_other_account_then_restores_ui_and_owns_notification(self):
        self.e.run('settings', {'automatic':True, 'automaticLists':False, 'notifications':True, 'interval':5})
        self.add()
        self.now += 301
        operations = []
        self.e.run('settings', {'automaticLists':False})
        self.e.search_cache.bind('300')
        cached=self.e.search_cache.put('300','qa',[{'id':'200','username':'qa.person'}],{'200':'https://cdninstagram.com/synthetic.jpg'})
        def refresh(engine, args):
            operations.append((engine.active_id, args['id']))
            self.assertEqual(engine.catalog['active'],'300')
            self.assertIsNotNone(engine.search_cache.picture(engine.catalog['active'],cached['token'],'200'))
            p = parse_profile(profile(followers=15))
            engine.record_profile(p, own=True)
            engine.save()
        with patch.object(Engine, 'refresh', refresh):
            state = self.e.run('automatic', {}, group_id='background-qa')
        self.assertEqual(operations, [('100','100')])
        self.assertEqual(state['activeAccountId'], '300')
        self.assertEqual(state['account']['id'], '300')
        self.assertIsNotNone(self.e.search_cache.get('300','qa'))
        self.assertEqual(len(self.e.notifications), 1)
        self.assertEqual(self.e.notifications[0]['ownerId'], '100')
        self.assertEqual(self.e.notifications[0]['ownerUsername'], 'qa.owner')
        self.e.run('switch_account', {'id':'100'})
        self.assertEqual(self.e.data['account']['followers'], 15)
        self.assertEqual(self.e.data['notificationLog'][0]['id'], 'background-qa')

    def test_background_auth_failure_only_holds_own_account_and_restores_selection(self):
        self.e.run('settings', {'automatic':True, 'automaticLists':False, 'interval':5})
        self.add()
        self.e.run('settings', {'automatic':True, 'automaticLists':False, 'interval':5})
        self.now += 301
        with patch.object(Engine, 'refresh', side_effect=AppFailure('session', 'Synthetic expired session')):
            with self.assertRaises(AppFailure):
                self.e.run('automatic', {})
        self.assertEqual(self.e.active_id, '300')
        self.assertFalse(self.e.catalog['limitedHold'])
        self.assertFalse(self.e.snapshot()['backgroundMonitor']['held'])
        self.assertEqual(self.e.catalog['accounts']['100']['monitor']['reason'], 'restricted')

    def test_failed_login_preserves_current_account_and_scrubs_password(self):
        args = {'username':'qa.invalid','password':'SYNTHETIC-PASSWORD'}
        fake = FakeClient([])
        fake.login = lambda *_: (_ for _ in ()).throw(AppFailure('verification', 'Synthetic challenge'))
        with patch.object(self.e, 'new_client', return_value=fake):
            with self.assertRaises(AppFailure):
                self.e.run('login', args)
        self.assertNotIn('password', args)
        self.assertEqual(self.e.active_id, '100')
        self.assertEqual(self.e.data['session']['cookies']['sessionid'], 'SYNTHETIC-FIRST')
        self.assertEqual(len(self.e.catalog['accounts']), 1)

    def test_delete_removes_only_current_account_and_its_backup(self):
        self.add()
        other_path = self.e.vault.path
        other_backup = self.e.vault.backup
        self.e.run('delete_all', {})
        self.assertEqual(self.e.active_id, '100')
        self.assertEqual(self.e.data['people']['200']['note'], 'FIRST-NOTE')
        self.assertIsNone(Vault(other_path.parent).load()['account'])
        self.assertFalse(other_backup.exists())
        previous = Vault(self.home.name, catalog=True)
        previous.path = previous.backup
        self.assertNotIn('300', previous.load()['accounts'])
        self.e.run('delete_all', {})
        self.assertIsNone(self.e.active_id)
        self.assertEqual(self.e.snapshot()['accounts'], [])
        self.assertEqual(Accounts(self.home.name).snapshot()['accounts'], [])

    def test_foreign_backup_cannot_overwrite_selected_account(self):
        backup = Path(self.home.name)/'qa.trackerbackup'
        self.e.export_backup(backup)
        self.add()
        with self.assertRaises(AppFailure):
            self.e.run('import_backup', {'path':str(backup)})
        self.assertEqual(self.e.active_id, '300')
        self.assertEqual(self.e.data['account']['id'], '300')

    def test_missing_corrupt_or_foreign_vault_fails_without_switching_or_reset(self):
        self.add()
        self.e.run('switch_account', {'id':'100'})
        path = Vault(self.e.account_directory('300')).path
        raw = path.read_bytes()
        path.write_bytes(b'broken')
        with self.assertRaises(StorageFailure):
            self.e.run('switch_account', {'id':'300'})
        self.assertEqual(self.e.active_id, '100')
        self.assertEqual(path.read_bytes(), b'broken')
        path.write_bytes(raw)
        path.unlink()
        with self.assertRaises(StorageFailure):
            self.e.run('switch_account', {'id':'300'})
        self.assertFalse(path.exists())

    def test_unsafe_catalog_slot_fails_closed(self):
        c = copy.deepcopy(self.e.catalog)
        c['accounts']['100']['slot'] = '../foreign'
        self.e.catalog_vault.save(c)
        with self.assertRaises(StorageFailure):
            Accounts(self.home.name)

    def test_first_backup_import_uses_own_vault_without_unlinked_archive_copy(self):
        backup = Path(self.home.name)/'qa.trackerbackup'
        self.e.export_backup(backup)
        with tempfile.TemporaryDirectory(prefix='TrackerQA-') as fresh:
            e = Accounts(fresh, clock=lambda:self.now)
            e.run('settings', {'theme':'oled','language':'en'})
            e.run('import_backup', {'path':str(backup)})
            self.assertEqual(e.active_id, '100')
            self.assertIsNone(e.data['session'])
            self.assertEqual(e.data['settings']['theme'],'oled')
            self.assertEqual(e.data['settings']['language'],'en')
            unlinked = Vault(Path(fresh)/'unlinked').load()
            self.assertTrue(unlinked is None or not unlinked['people'] and unlinked['account'] is None)
            self.assertEqual(Accounts(fresh).data['people']['200']['note'],'FIRST-NOTE')

    def test_interrupted_deletion_finishes_after_restart_without_touching_other_account(self):
        self.add()
        real_save = self.e.catalog_vault.save
        def fail_catalog(value, **kwargs):
            if '300' not in value['accounts']:
                raise StorageFailure('Synthetic disk failure')
            return real_save(value, **kwargs)
        with patch.object(self.e.catalog_vault, 'save', side_effect=fail_catalog):
            with self.assertRaises(StorageFailure):
                self.e.run('delete_all', {})
        restart = Accounts(self.home.name)
        self.assertEqual(restart.active_id, '100')
        self.assertNotIn('300', restart.catalog['accounts'])
        self.assertEqual(restart.data['people']['200']['note'],'FIRST-NOTE')

    def test_startup_repair_does_not_shorten_shared_hold_from_other_account(self):
        self.add()
        self.e.limited()
        hold = self.e.data['gate']['blockedUntil']
        self.e.run('switch_account', {'id':'100'})
        # Simulate a prior interrupted list whose vault predates account B's 429.
        raw = self.e.data
        raw['gate'] = {}
        raw['lists']['100:followers'] = {'snapshots':[], 'draft':{
            'members':{},'complete':False,'status':'running'}}
        self.e.vault.save(raw)
        restart = Accounts(self.home.name, clock=lambda:self.now)
        self.assertEqual(restart.data['gate']['blockedUntil'],hold)
        self.assertTrue(restart.catalog['limitedHold'])
        self.assertEqual(restart.data['lists']['100:followers']['draft']['status'],'interrupted')

    def test_request_reservation_survives_failure_writing_large_account_vault(self):
        with patch.object(self.e.vault,'save',side_effect=StorageFailure('Synthetic disk failure')):
            with self.assertRaises(StorageFailure):
                self.e.before_request()
        restart = Accounts(self.home.name, clock=lambda:self.now)
        self.assertEqual(restart.data['gate']['attempts'],1)
        self.assertEqual(restart.data['gate']['nextAt'],self.now+15)

    def test_failed_new_vault_commit_retains_previous_account_and_valid_catalog(self):
        real_save = Vault.save
        def fail_new(vault, data, **kwargs):
            if (data.get('account') or {}).get('id') == '300':
                raise StorageFailure('Synthetic full disk')
            return real_save(vault, data, **kwargs)
        with patch.object(Vault,'save',autospec=True,side_effect=fail_new):
            with self.assertRaises(AppFailure):
                self.add()
        self.assertEqual(self.e.active_id,'100')
        restart = Accounts(self.home.name)
        self.assertEqual(restart.active_id,'100')
        self.assertEqual(len(restart.catalog['accounts']),1)
        self.assertEqual(restart.data['session']['cookies']['sessionid'],'SYNTHETIC-FIRST')

    def test_explicit_wayback_detail_survives_coordinator_but_is_not_replayed(self):
        state = self.e.run('wayback', {'id':'100','enabled':True})
        self.assertIsNotNone(state['waybackDetail'])
        self.assertTrue(state['waybackDetail']['enabled'])
        self.assertIsNone(self.e.run('state', {})['waybackDetail'])
        self.add()
        self.assertIsNone(self.e.snapshot()['waybackDetail'])
        self.assertNotIn('100', self.e.data['wayback']['profiles'])

    def test_local_note_edit_does_not_rewrite_unchanged_encrypted_catalog(self):
        with patch.object(self.e.catalog_vault,'save',wraps=self.e.catalog_vault.save) as writes:
            self.e.run('save_person', {'id':'200','note':'Local edit'})
        self.assertEqual(writes.call_count,0)
        self.assertEqual(Accounts(self.home.name).data['people']['200']['note'],'Local edit')

    def test_local_hold_after_switch_does_not_invent_new_429_or_extend_deadline(self):
        self.add()
        self.e.limited()
        self.e.run('switch_account', {'id':'100'})
        before = copy.deepcopy(self.e.catalog['gate'])
        fake = FakeClient([])
        def gated_profile(client, identity):
            self.e.before_request()
            raise AssertionError('Local hold must prevent transport')
        with patch.object(self.e,'client',return_value=fake), patch.object(self.e,'read_profile',side_effect=gated_profile):
            for _ in range(2):
                with self.assertRaises(AppFailure) as caught:
                    self.e.run('refresh', {})
                self.assertEqual(caught.exception.code,'cooldown')
        self.assertEqual(self.e.catalog['gate'],before)
        self.assertEqual(fake.calls,[])
        restart = Accounts(self.home.name,clock=lambda:self.now)
        self.assertEqual(restart.catalog['gate'],before)


if __name__ == '__main__':
    unittest.main()
