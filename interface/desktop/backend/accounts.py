"""One worker, independent DPAPI vaults and a shared, durable request gate.

Only the selected vault is retained. The small encrypted catalog owns identity,
device preferences and scheduling metadata; the worker's operation lock owns
switching, so an in-flight operation cannot change its destination.
"""
import copy
from pathlib import Path
import re
import time
import uuid

from engine import Engine, blank, validate_archive
from network import AppFailure
from storage import Vault, StorageFailure
from login_identity import device_settings
import request_budget
import localization
import list_preparation
import stories

DEVICE_SETTINGS = ('language', 'theme', 'compact')


class Accounts(Engine):
    def __init__(self, directory, emit=lambda event: None, clock=time.time, *, request_delay=None):
        self.directory = Path(directory)
        self.catalog_vault = Vault(directory, catalog=True)
        self.catalog = self.catalog_vault.load()
        self.persisted_catalog = copy.deepcopy(self.catalog)
        self.active_id = None
        self.notifications = []
        self.initializing = True
        if self.catalog is not None:
            self.validate_catalog()
            self.active_id = self.catalog['active']
            selected = self.account_directory(self.active_id)
            if self.active_id and not Vault(selected).path.is_file():
                raise StorageFailure('Hesap kasası bulunamadı. Kayıtlar otomatik sıfırlanmadı.')
        else:
            selected = directory
        super().__init__(selected, emit, clock, request_delay=request_delay)
        self.initializing = False
        if self.catalog is not None and self.data.get('removedAccountId') == self.active_id and self.active_id:
            self.finish_removal()
        if self.catalog is None:
            # Keep the existing primary and previous vault in place. Migration
            # only adds an encrypted catalog; it never rewrites the old session.
            self.catalog = {'version': 1, 'accounts': {}, 'active': None,
                            'gate': copy.deepcopy(self.data['gate']),
                            'limitedHold': bool(self.data['gate'].get('manualOnly') and
                                                self.data['gate'].get('limitedAt')),
                            'preferences': {k: self.data['settings'][k] for k in DEVICE_SETTINGS}}
            if self.data['account']:
                self.active_id = self.data['account']['id']
                self.catalog['active'] = self.active_id
                self.catalog['accounts'][self.active_id] = {'slot': 'legacy'}
                self.update_entry()
            self.catalog['gate'].pop('manualOnly', None)
            self.persist_catalog()
        self.bind_shared_state()
        self.recover_interrupted()
        # Upgrade scheduling metadata once, including unselected accounts. Read
        # their own vaults; never infer pending preparation from the catalog.
        selected_id = self.active_id
        try:
            for identity, entry in list(self.catalog['accounts'].items()):
                if (entry['monitor'].get('listPlanVersion') != list_preparation.PLAN_VERSION or
                        entry['monitor'].get('budgetPolicy') != request_budget.policy()):
                    self.activate(identity)
                    self.update_entry()
            self.persist_catalog()
        finally:
            if self.active_id != selected_id:
                self.activate(selected_id)

    def recover_interrupted(self):
        # Bind the shared gate before any startup repair can write the vault.
        if not self.initializing:
            super().recover_interrupted()

    def persist_catalog(self, value=None, *, replace_previous=False):
        value = self.catalog if value is None else value
        # This worker owns all writes. Local note edits do not alter the small
        # catalog; avoid encrypting/writing identical metadata on every save.
        if value == self.persisted_catalog and not replace_previous:
            return
        self.catalog_vault.save(value, replace_previous=replace_previous)
        self.persisted_catalog = copy.deepcopy(value)

    def validate_catalog(self):
        c = self.catalog
        if (type(c.get('limitedHold')) is not bool or
                c.get('active') is not None and c.get('active') not in c['accounts'] or
                any(c['preferences'].get(k) not in values for k, values in
                    (('language', localization.LANGUAGES), ('theme', ('system', 'light', 'dark', 'oled')))) or
                type(c['preferences'].get('compact')) is not bool):
            raise StorageFailure('Hesap dizini geçersiz. Mevcut dosyalar korundu.')
        slots = set()
        for identity, entry in c['accounts'].items():
            slot = entry.get('slot') if isinstance(entry, dict) else None
            if (not re.fullmatch(r'[1-9][0-9]{0,29}', identity) or not isinstance(slot, str) or
                    not (slot == 'legacy' or re.fullmatch(r'[a-f0-9]{32}', slot)) or slot in slots or
                    not isinstance(entry.get('monitor'), dict) or
                    not isinstance(entry.get('username'), str) or
                    not re.fullmatch(r'[A-Za-z0-9._]{1,30}', entry['username']) or
                    type(entry.get('connected')) is not bool):
                raise StorageFailure('Hesap dizini geçersiz. Mevcut dosyalar korundu.')
            monitor = entry['monitor']
            if (type(monitor.get('enabled')) is not bool or type(monitor.get('held')) is not bool or
                    monitor.get('nextAt') is not None and
                    (type(monitor['nextAt']) not in (int, float) or
                     not 0 <= monitor['nextAt'] < float('inf'))):
                raise StorageFailure('Hesap dizini geçersiz. Mevcut dosyalar korundu.')
            slots.add(slot)
        probe = blank(copy.deepcopy(c['gate']))
        probe['settings'].update(c['preferences'])
        try:
            validate_archive(probe)
        except ValueError as exc:
            raise StorageFailure('Hesap dizini geçersiz. Mevcut dosyalar korundu.') from exc

    def account_directory(self, identity):
        if identity is None:
            return self.directory / 'unlinked'
        slot = self.catalog['accounts'][identity]['slot']
        return self.directory if slot == 'legacy' else self.directory / 'accounts' / slot

    def bind_shared_state(self):
        self.data['gate'].update(self.catalog['gate'])
        self.data['settings'].update(self.catalog['preferences'])
        validate_archive(self.data)
        if self.active_id and (self.data['account'] or {}).get('id') != self.active_id:
            raise StorageFailure('Hesap kasasının kimliği uyuşmuyor. Dosyalar korundu.')

    def update_entry(self):
        if not self.active_id or not self.data['account']:
            return
        p = self.data['account']
        self.catalog['accounts'][self.active_id].update(
            username=p['username'], name=p.get('name'), connected=bool(self.data['session']),
            monitor=super().monitor_status(), notifications=self.data['settings']['notifications'],
            lastError=(self.data['lastError'] or {}).get('code'))

    def save(self, *, replace_previous=False):
        # Engine initialization can repair an interrupted scan before migration.
        if self.catalog is None:
            return super().save(replace_previous=replace_previous)
        self.catalog['gate'] = {k: v for k, v in self.data['gate'].items() if k != 'manualOnly'}
        self.catalog['preferences'] = {k: self.data['settings'][k] for k in DEVICE_SETTINGS}
        # A request reservation/429 survives even if writing the larger account
        # vault fails. Do not publish new-account metadata until its vault exists.
        committed = copy.deepcopy(self.catalog)
        if self.active_id and 'monitor' not in committed['accounts'][self.active_id]:
            del committed['accounts'][self.active_id]
            previous_active = (self.persisted_catalog or {}).get('active')
            committed['active'] = previous_active if previous_active in committed['accounts'] else None
        self.persist_catalog(committed)
        if self.active_id and not self.data['account']:
            return
        super().save(replace_previous=replace_previous)
        self.update_entry()
        self.persist_catalog()

    def activate(self, identity):
        if identity is not None and identity not in self.catalog['accounts']:
            raise AppFailure('account', 'Kayıtlı hesap bulunamadı.')
        vault = Vault(self.account_directory(identity))
        if identity and not vault.path.is_file():
            raise StorageFailure('Hesap kasası bulunamadı. Mevcut kayıtlar korundu.')
        old_vault, old_data, old_id = self.vault, self.data, self.active_id
        if identity != old_id:
            self.profile_facts.clear()
        try:
            self.vault = vault
            self.data = self.load_vault()
            self.active_id = identity
            self.bind_shared_state()
            self.wayback_id = None
            self.search_results = None
            self.profile_detail_result = None
            self.story_detail = None
            self.recover_interrupted()
        except Exception:
            self.vault, self.data, self.active_id = old_vault, old_data, old_id
            raise

    def finish_removal(self):
        catalog = copy.deepcopy(self.catalog)
        catalog['accounts'].pop(self.active_id, None)
        catalog['active'] = next(iter(catalog['accounts']), None)
        self.persist_catalog(catalog, replace_previous=True)
        self.catalog = catalog
        self.activate(catalog['active'])

    def check_connection_identity(self, identity):
        # A different numeric ID selects a different vault after own-profile
        # verification, rather than replacing the existing account.
        pass

    def login_device_settings(self, username):
        for identity, entry in self.catalog['accounts'].items():
            if entry['username'].lower() == username.lower():
                data = Vault(self.account_directory(identity)).load()
                if data and data['session']:
                    return device_settings(data['session'])
        return super().login_device_settings(username)

    def select_connection_account(self, profile):
        identity = profile['id']
        if identity == self.active_id:
            return
        if identity in self.catalog['accounts']:
            self.activate(identity)
        else:
            slot = uuid.uuid4().hex
            self.catalog['accounts'][identity] = {'slot': slot}
            self.vault = Vault(self.directory / 'accounts' / slot)
            self.data = blank(copy.deepcopy(self.catalog['gate']))
            self.data['settings'].update(self.catalog['preferences'])
            self.active_id = identity
        self.catalog['active'] = identity

    def limited(self, retry=None):
        self.catalog['limitedHold'] = True
        super().limited(retry)

    def progress(self, phase, **details):
        owner = self.data['account'] or {}
        super().progress(phase, ownerId=self.active_id, ownerUsername=owner.get('username'), **details)

    def prepare_restore(self, restored):
        super().prepare_restore(restored)
        if self.active_id is None and restored['account']:
            if restored['account']['id'] in self.catalog['accounts']:
                raise AppFailure('backup', 'Yedek başka bir hesaba ait. Önce ilgili hesabı seç.')
            self.select_connection_account(restored['account'])
        restored['settings'].update(self.catalog['preferences'])

    def snapshot(self):
        state = super().snapshot()
        state['activeAccountId'] = self.active_id
        state['accounts'] = [dict(id=identity, **{k: e.get(k) for k in
                             ('username', 'name', 'connected', 'monitor', 'lastError')})
                             for identity, e in self.catalog['accounts'].items()]
        state['backgroundMonitor'] = self.background_status()
        if self.catalog['limitedHold']:
            state['gate'] = {**state['gate'], 'manualOnly': True}
            state['monitor'] = {**state['monitor'], 'held': True, 'reason': 'restricted', 'nextAt': None}
        return state

    def background_status(self):
        monitors = [e['monitor'] for e in self.catalog['accounts'].values()]
        now = self.clock()
        due = [max(e['monitor']['nextAt'], request_budget.next_at(self.catalog['gate'], identity, now))
               for identity,e in self.catalog['accounts'].items() if e['monitor'].get('enabled') and not e['monitor'].get('held') and e['monitor'].get('nextAt') is not None]
        return {'enabled': any(m.get('enabled') for m in monitors),
                'held': self.catalog['limitedHold'] or not due,
                'nextAt': max(min(due), self.catalog['gate'].get('nextAt', 0),
                              self.catalog['gate'].get('blockedUntil', 0)) if due else None}

    def run(self, operation, args, *, group_id=None):
        self.notifications = []
        original = self.active_id
        detail = None
        group_id = group_id or uuid.uuid4().hex
        background = operation == 'automatic'
        try:
            if operation == 'switch_account':
                self.activate(str(args.get('id', '')))
                if self.catalog['active'] != self.active_id:self.search_cache.clear()
                self.catalog['active'] = self.active_id
                self.persist_catalog()
            elif operation == 'delete_all':
                self.search_cache.clear()
                if self.data['account']:
                    self.daily_store().clear_all(revoke=True)
                    stories.StoryStore(self.vault.path.parent,self.data['account']['id']).clear_all()
                # This command removes only the selected account and its backup.
                cleared = blank(copy.deepcopy(self.catalog['gate']))
                cleared['removedAccountId'] = original
                self.vault.save(cleared, replace_previous=True)
                self.vault.backup.unlink(missing_ok=True)
                self.data = cleared
                self.finish_removal()
            else:
                if background:
                    status = self.background_status()
                    if status['held'] or status['nextAt'] is None or status['nextAt'] > self.clock():
                        return self.snapshot()
                    due = [(max(e['monitor']['nextAt'], request_budget.next_at(self.catalog['gate'], identity, self.clock())), identity) for identity, e in self.catalog['accounts'].items()
                           if e['monitor'].get('enabled') and not e['monitor'].get('held') and
                           e['monitor'].get('nextAt') is not None]
                    self.activate(min(due)[1])
                # Backups belong to this viewing account. A foreign backup must
                # be imported from an unlinked workspace, never over another ID.
                # The explicit archive response owns its transient thumbnail
                # projection before Engine clears its detail selector.
                result = super().run(operation, args, group_id=group_id, project=operation == 'wayback')
                if operation == 'wayback':
                    detail = result['waybackDetail']
                if operation in ('login', 'import_counter', 'refresh'):
                    self.catalog['limitedHold'] = False
        except Exception:
            if operation in ('login', 'import_counter', 'import_backup') and self.active_id != original:
                selected = self.active_id
                if selected and not (self.data['account'] or {}).get('id'):
                    self.catalog['accounts'].pop(selected, None)
                self.activate(original)
                self.catalog['active'] = original
                self.persist_catalog()
            raise
        finally:
            try:
                if self.active_id and self.data['account'] and operation != 'state':
                    owner = self.data['account']
                    self.notifications = [{**entry, 'ownerId': self.active_id, 'ownerUsername': owner['username']}
                                          for entry in self.data['notificationLog'] if
                                          entry['id'] == group_id and entry.get('desktopEligible')]
                    self.update_entry()
                    self.persist_catalog()
            finally:
                if background and self.active_id != original:
                    self.activate(original)
        state = self.snapshot()
        state['waybackDetail'] = detail
        return state
