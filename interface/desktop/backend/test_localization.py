"""Presentation language must not rewrite evidence or change request policy."""
import copy
import json
from pathlib import Path
from unittest.mock import patch
import unittest
import test_backend as fixtures
from engine import Engine
from network import AppFailure
from storage import StorageFailure, protect
import localization


class LanguageTests(unittest.TestCase):
    setUp = fixtures.BackendTests.setUp
    tearDown = fixtures.BackendTests.tearDown
    connected = fixtures.BackendTests.connected

    def test_language_reopen_changes_only_presentation_setting(self):
        e = self.connected()
        e.data['account']['bio'] = 'Özet · Kaydet · İstanbul <img src=x>'
        before = copy.deepcopy(e.data)
        e.run('settings', {'language': 'en'})
        expected = copy.deepcopy(before); expected['settings']['language'] = 'en'
        self.assertEqual(e.data, expected)
        reopened = Engine(self.home.name)
        self.assertEqual(reopened.snapshot()['settings']['language'], 'en')
        self.assertEqual(reopened.snapshot()['notificationTypes']['followers'], 'Follower count')
        self.assertEqual(reopened.snapshot()['account']['bio'], before['account']['bio'])
        self.assertEqual(reopened.data, expected)

    def test_last_error_projection_and_backup_preserve_original_evidence(self):
        e = self.connected(); message = 'Önce hesabını bağla.'
        e.data['lastError'] = {'code': 'session', 'message': message}
        e.event('error', message)
        e.run('settings', {'language': 'en'})
        self.assertEqual(e.snapshot()['lastError']['message'], 'Connect your account first.')
        self.assertEqual(e.data['lastError']['message'], message)
        self.assertEqual(e.data['events'][0]['title'], message)
        backup = Path(self.home.name) / 'language.trackerbackup'
        e.export_backup(backup)
        saved = json.loads(protect(backup.read_bytes(), decrypt=True))['data']
        self.assertEqual(saved['settings']['language'], 'en')
        self.assertEqual(saved['events'][0]['title'], message)
        e.import_backup(backup)
        self.assertEqual(e.data['settings']['language'], 'en')
        self.assertIsNone(e.data['session'])
        self.assertFalse(e.data['settings']['automatic'])

    def test_old_vault_defaults_to_turkish_and_disk_failure_uses_same_migration(self):
        e = self.connected(); e.data['settings'].pop('language'); e.save()
        reopened = Engine(self.home.name)
        self.assertEqual(reopened.data['settings']['language'], 'tr')
        with patch.object(reopened.vault, 'save', side_effect=StorageFailure('Synthetic')):
            with self.assertRaises(AppFailure): reopened.run('settings', {'language': 'en'})
        self.assertEqual(reopened.data['settings']['language'], 'tr')

    def test_invalid_languages_do_not_change_other_settings(self):
        e = self.connected(); before = copy.deepcopy(e.data['settings'])
        for value in ('de', 'EN', '', None, True, 1, []):
            with self.subTest(value=value), self.assertRaises(AppFailure):
                e.settings({'language': value, 'automatic': True})
            self.assertEqual(e.data['settings'], before)
        self.assertEqual(localization.translate('En fazla 200 profil kaydedilebilir.', 'en'), 'You can save up to 200 profiles.')

    def test_spanish_and_brazilian_portuguese_are_full_local_choices(self):
        e = self.connected()
        for language, expected in (('es', 'Resumen'), ('pt-BR', 'Resumo')):
            with self.subTest(language=language):
                before = copy.deepcopy(e.data)
                e.run('settings', {'language': language})
                self.assertEqual(e.data['settings']['language'], language)
                self.assertEqual(e.snapshot()['settings']['language'], language)
                self.assertEqual(localization.translate('Özet', language), expected)
                self.assertIn('qa.person', localization.translate('@{0} profili kontrol edildi', language, ('qa.person',)))
                expected_data = copy.deepcopy(before); expected_data['settings']['language'] = language
                self.assertEqual(e.data, expected_data)
