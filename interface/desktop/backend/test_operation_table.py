"""Every worker operation still reaches the same engine method; offline only.

These tests pin the routing inside ``Engine.run`` and the surface ``worker.py``
and ``seed_qa.py`` use, so splitting the engine into services cannot drop or
rewire an operation without a failing test.
"""
import tempfile
import unittest
from unittest.mock import patch

from accounts import Accounts
from engine import Engine
from network import AppFailure, parse_profile
from test_backend import profile

ARGS = {'id': '200', 'path': 'synthetic.backup'}

# operation -> (method, positional arguments, keyword arguments) as run() calls it.
ROUTES = {
    'story_view': ('story_archive', (ARGS,), {}),
    'story_fetch': ('story_archive', (ARGS,), {'fetch': True}),
    'story_clear': ('story_archive', (ARGS,), {'clear': True}),
    'daily_clear': ('clear_daily', (ARGS,), {}),
    'wayback': ('configure_wayback', (ARGS,), {}),
    'login': ('connect', (ARGS,), {}),
    'import_counter': ('connect', (ARGS, True), {}),
    'refresh': ('refresh', (ARGS,), {}),
    'profile_detail': ('profile_detail', (ARGS,), {}),
    'add_person': ('add_person', (ARGS,), {}),
    'scan': ('scan', (ARGS,), {'resume': True}),
    'search_people': ('search_people', (ARGS,), {}),
    'save_person': ('save_person', (ARGS,), {}),
    'save_profile_about': ('save_profile_about', (ARGS,), {}),
    'settings': ('settings', (ARGS,), {}),
    'automatic': ('automatic', (), {}),
    'check_access': ('check_access', (ARGS,), {}),
    'notification_rule': ('notification_rule', (ARGS,), {}),
    'read_notification': ('read_notification', (ARGS,), {}),
    'read_all_notifications': ('read_all_notifications', (ARGS,), {}),
    'export_list': ('export_list', (ARGS,), {}),
    'export_backup': ('export_backup', ('synthetic.backup',), {}),
    'import_backup': ('import_backup', ('synthetic.backup',), {}),
}

# Operations that make an Instagram request must be refused in offline QA mode.
NETWORK = {'story_fetch', 'login', 'import_counter', 'refresh', 'scan', 'add_person', 'search_people',
           'profile_detail', 'automatic', 'check_access'}

# The attributes worker.py and seed_qa.py read from the engine object.
WORKER_SURFACE = ('active_id', 'cancel', 'catalog', 'daily_query', 'data', 'list_pages', 'notifications',
                  'offline', 'run', 'search_cache', 'snapshot', 'story_media', 'submit_code',
                  'configure_wayback', 'event', 'notification_rule', 'record_profile', 'save', 'settings')


class OperationTableTests(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.addCleanup(self.home.cleanup)

    def engines(self):
        plain = Engine(self.home.name + '/plain', clock=lambda: 1800000000)
        app = Accounts(self.home.name + '/accounts', clock=lambda: 1800000000)
        app.run('state', {})
        return {'Engine': plain, 'Accounts': app}

    def test_each_operation_calls_its_method_once_with_the_same_arguments(self):
        for kind, engine in self.engines().items():
            for operation, (method, args, kwargs) in ROUTES.items():
                with self.subTest(kind=kind, operation=operation), patch.object(engine, method) as handler:
                    if kind == 'Accounts' and operation == 'automatic':
                        # The coordinator first decides whether any account is due.
                        continue
                    sent = dict(ARGS)
                    engine.run(operation, sent)
                    expected = tuple(sent if a is ARGS else a for a in args)
                    handler.assert_called_once_with(*expected, **kwargs)

    def test_routes_cover_exactly_the_operations_run_accepts(self):
        engine = self.engines()['Engine']
        special = {'state', 'remove_person', 'disconnect', 'delete_all', 'list_exception'}
        for operation in sorted(set(ROUTES) | special):
            with self.subTest(operation=operation):
                try:
                    with patch.object(engine, ROUTES[operation][0]) if operation in ROUTES else _null():
                        engine.run(operation, dict(ARGS))
                except AppFailure as exc:
                    self.assertNotEqual(exc.code, 'operation')
        with self.assertRaises(AppFailure) as caught:
            engine.run('no_such_operation', {})
        self.assertEqual(caught.exception.code, 'operation')

    def test_offline_mode_refuses_exactly_the_network_operations(self):
        engine = self.engines()['Engine']
        engine.offline = True
        for operation, (method, _, _) in ROUTES.items():
            with self.subTest(operation=operation), patch.object(engine, method) as handler:
                if operation in NETWORK:
                    with self.assertRaises(AppFailure) as caught:
                        engine.run(operation, dict(ARGS))
                    self.assertEqual(caught.exception.code, 'offline')
                    handler.assert_not_called()
                else:
                    engine.run(operation, dict(ARGS))
                    handler.assert_called_once()

    def test_password_is_removed_from_arguments_even_when_the_handler_fails(self):
        engine = self.engines()['Engine']
        args = {'username': 'qa.owner', 'password': 'SYNTHETIC'}
        with patch.object(engine, 'connect', side_effect=AppFailure('input', 'synthetic')):
            with self.assertRaises(AppFailure):
                engine.run('login', args)
        self.assertNotIn('password', args)

    def test_storage_failure_reloads_the_saved_vault_instead_of_keeping_edits(self):
        from storage import StorageFailure
        engine = self.engines()['Engine']
        engine.record_profile(parse_profile(profile()), own=True)
        engine.save()
        def failing(_args):
            engine.data['settings']['interval'] = 999
            raise StorageFailure('synthetic disk failure')
        with patch.object(engine, 'settings', side_effect=failing), self.assertRaises(AppFailure):
            engine.run('settings', {'interval': 999})
        self.assertNotEqual(engine.data['settings']['interval'], 999)

    def test_worker_and_seed_surface_exists_on_the_coordinator(self):
        app = self.engines()['Accounts']
        for name in WORKER_SURFACE:
            with self.subTest(name=name):
                self.assertTrue(hasattr(app, name))
        for name in ('page', 'membership', 'picture'):
            self.assertTrue(callable(getattr(app.list_pages, name)))
        self.assertTrue(callable(app.search_cache.picture))


class _null:
    def __enter__(self):
        return None

    def __exit__(self, *_):
        return False


if __name__ == '__main__':
    unittest.main()
