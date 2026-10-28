"""Appearance preference migration and encrypted persistence; no account reads."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from engine import Engine, validate_archive
from network import AppFailure
from storage import protect


class AppearanceTests(unittest.TestCase):
    def test_legacy_vault_defaults_to_system_without_modifying_account_or_gate(self):
        with tempfile.TemporaryDirectory(prefix='TrackerQA-') as home:
            engine=Engine(home)
            engine.data['settings'].pop('theme')
            engine.data['gate']={'status':429,'blockedUntil':1800000000,'manualOnly':True}
            engine.data['lastError']={'code':'limited','message':'synthetic'}
            engine.save()
            restored=Engine(home)
            self.assertEqual(restored.data['settings']['theme'],'system')
            self.assertEqual(restored.data['gate'],engine.data['gate'])
            self.assertEqual(restored.data['lastError'],engine.data['lastError'])

    def test_each_theme_persists_preserving_restrictions_schedule_and_last_error(self):
        with tempfile.TemporaryDirectory(prefix='TrackerQA-') as home:
            engine=Engine(home)
            engine.data['gate']={'status':429,'blockedUntil':1800000000,'manualOnly':True}
            engine.data['lastError']={'code':'limited','message':'synthetic'}
            baseline=copy.deepcopy(engine.data)
            for mode in ('system','light','dark','oled'):
                engine.run('settings',{'theme':mode})
                restored=Engine(home)
                self.assertEqual(restored.data['settings']['theme'],mode)
                actual=copy.deepcopy(restored.data);actual['settings']['theme']=baseline['settings']['theme']
                self.assertEqual(actual,baseline)

    def test_unknown_theme_is_rejected_at_settings_and_archive_boundaries(self):
        with tempfile.TemporaryDirectory(prefix='TrackerQA-') as home:
            engine=Engine(home)
            for invalid in ('sepia',None,True,{},['dark']):
                with self.assertRaises(AppFailure):engine.settings({'theme':invalid})
                data=copy.deepcopy(engine.data);data['settings']['theme']=invalid
                with self.assertRaises((ValueError,TypeError)):validate_archive(data)

    def test_old_encrypted_backup_is_normalized_before_its_first_snapshot(self):
        with tempfile.TemporaryDirectory(prefix='TrackerQA-') as home:
            engine=Engine(home)
            legacy=copy.deepcopy(engine.data);legacy['settings'].pop('theme')
            backup=Path(home)/'legacy.backup'
            backup.write_bytes(protect(json.dumps({'format':'InstagramTracker-observations','version':1,'data':legacy}).encode()))
            engine.import_backup(backup)
            self.assertEqual(engine.snapshot()['settings']['theme'],'system')
            self.assertEqual(Engine(home).data['settings']['theme'],'system')
