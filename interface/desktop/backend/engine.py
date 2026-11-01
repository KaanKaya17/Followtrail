"""Local observations, paging, comparison and guarded account operations."""
import copy
import json
import math
import os
from pathlib import Path
import queue
import random
import re
import threading
import time
import uuid
import access_checks
import anonymous_visibility
import notification_log
import localization
import wayback
import request_budget
import list_preparation
import hashlib
from login_identity import LoginIdentity, device_settings

from storage import Vault, StorageFailure, protect, atomic_write, MAX_VAULT
from network import (AppFailure, new_client, close_client, parse_profile,
                     parse_page, failure, deadline)
from network import read_avatar

MAX_MEMBERS = 20000
MAX_PAGES = 400
CONTROL_INTERVALS = (1, 5, 15, 30, 60, 180, 360)
REQUEST_SPACING = (12, 18)
PROFILE_FIELDS = ('username', 'name', 'bio', 'url', 'private', 'verified', 'followers', 'following', 'posts', 'picture')


def blank(gate=None):
    return {'version': 1, 'account': None, 'session': None, 'people': {},
            'history': {}, 'lists': {}, 'events': [], 'gate': gate or {},
            'settings': {'compact': False, 'notifications': False, 'automatic': False, 'interval': 60,
                         'investigateMissing': False, 'language': 'tr', 'theme': 'system', 'automaticLists': False},
            'access': {}, 'accessPlan': {'status': 'idle'}, 'lastAutomaticTask': 'profile',
            'notificationRules': {}, 'notificationLog': [],
            'wayback': {'profiles': {}, 'images': {}},
            'automaticAttempts': {}, 'lastInvestigationAt': 0,
            'lastError': None, 'lastAutomatic': 0}


def compare(previous, current):
    if not previous or not current or not previous.get('complete') or not current.get('complete'):
        return None
    old, new = set(previous['members']), set(current['members'])
    return {'added': sorted(new - old), 'removed': sorted(old - new),
            'from': previous['finishedAt'], 'to': current['finishedAt']}


def public_person(value):
    # Explicit projection: saved session/auth never crosses into the renderer.
    keys = ('id', 'username', 'name', 'followers', 'following', 'posts', 'bio', 'url',
            'private', 'verified', 'observedAt', 'favorite', 'watching', 'group', 'note', 'avatar', 'picture')
    return {key: value.get(key) for key in keys}


class Engine:
    def __init__(self, directory, emit=lambda event: None, clock=time.time, *, request_delay=None):
        self.vault = Vault(directory)
        self.data = self.load_vault()
        self.emit = emit
        self.clock = clock
        self.request_delay = request_delay or (lambda: random.uniform(*REQUEST_SPACING))
        self.login_identity = LoginIdentity(getattr(self, 'directory', directory))
        self.cancel = threading.Event()
        self.codes = queue.Queue(maxsize=1)
        self.waiting_code = False
        self.operation = None
        self.observation_group = None
        self.offline = False
        self.list_scan_active = False
        self.scan_progress = None
        self.wayback_id = None
        self.recover_interrupted()

    def recover_interrupted(self):
        # Interrupted scans remain explicitly partial after a restart or switch.
        changed = False
        for record in self.data['lists'].values():
            if record.get('draft', {}).get('status') == 'running':
                record['draft']['status'] = 'interrupted'
                changed = True
        for case in self.data['access'].values():
            if case['stage'] == 'checking':
                case['checks'].append({'status': 'unknown', 'reason': 'interrupted', 'checkedAt': self.clock()})
                del case['checks'][:-10]
                case['stage'] = 'done'
                changed = True
        if changed:
            self.save()

    def save(self):
        self.vault.save(self.data)

    def load_vault(self):
        data = self.vault.load() or blank()
        access_checks.initialize(data)
        notification_log.initialize(data)
        localization.initialize(data)
        data['settings'].setdefault('theme', 'system')
        initialize_schedule(data)
        list_preparation.initialize(data)
        wayback.initialize(data)
        validate_archive(data)
        return data

    def snapshot(self):
        d = self.data
        records = {}
        for key, record in d['lists'].items():
            snapshots = record.get('snapshots', [])
            draft = record.get('draft')
            records[key] = {'snapshots': snapshots, 'draft': {k:v for k,v in draft.items() if k not in ('nextCursor','seenCursors')} if draft else None,
                            'comparison': compare(snapshots[-2], snapshots[-1]) if len(snapshots) > 1 else None}
        return {'account': public_person(d['account']) if d['account'] else None,
                'connected': bool(d['session']), 'people': [public_person(p) for p in d['people'].values()],
                'history': d['history'], 'lists': records, 'events': d['events'],
                'gate': d['gate'], 'settings': d['settings'], 'lastError': localization.error(d['lastError'], d['settings']['language']),
                'requestPacing': {'minSeconds': REQUEST_SPACING[0], 'maxSeconds': REQUEST_SPACING[1]},
                'requestBudget': {'sharedHourly':request_budget.SHARED[0], 'sharedDaily':request_budget.SHARED[1],
                                  'accountHourly':request_budget.ACCOUNT[0], 'accountDaily':request_budget.ACCOUNT[1],
                                  'nextAt':self.budget_available_at()},
                'monitor': self.monitor_status(),
                'listPreparation': list_preparation.view(d, self.clock()),
                'access': access_checks.cases_view(d), 'accessPlan': d['accessPlan'],
                'notificationRules': d['notificationRules'], 'notificationLog': d['notificationLog'],
                'notificationTypes': {key: localization.translate(value, d['settings']['language']) for key, value in notification_log.TYPES.items()},
                'wayback': wayback.metadata(d),
                'waybackDetail': wayback.view(d, self.wayback_id) if self.wayback_id else None,
                'counterAvailable': (Path(os.environ.get('LOCALAPPDATA', '')) / 'InstagramCounter/session.dat').is_file(),
                'storagePath': str(self.vault.path.parent), 'version': '0.2.21'}

    def progress(self, phase, **details):
        context = {'scan': dict(self.scan_progress)} if self.scan_progress is not None else {}
        self.emit({'type': 'progress', 'phase': phase, **context, **details})

    def check_cancel(self):
        if self.cancel.is_set():
            raise AppFailure('cancelled', 'İşlem iptal edildi. Tamamlanan gözlemler ve kısmi liste korundu.')

    def before_request(self):
        self.check_cancel()
        gate = self.data['gate']
        now = self.clock()
        if gate.get('blockedUntil', 0) > now:
            raise AppFailure('cooldown', 'Yerel mola sürüyor. Bitiş saati İstek bilgileri bölümünde.')
        if self.budget_available_at() > now:
            raise AppFailure('budget', 'Yerel istek bütçesi doldu. Sonraki zaman İstek bilgileri bölümünde; otomatik tekrar gönderilmedi.')
        remaining = gate.get('nextAt', 0) - now
        if remaining > 0:
            self.progress('waiting', until=gate['nextAt'], waitStartedAt=now)
            # Cancellation interrupts pacing and code waits; an in-flight HTTP
            # request has a bounded transport timeout and is never replayed.
            if self.cancel.wait(remaining):
                self.check_cancel()
        self.check_cancel()
        now = self.clock()
        if self.budget_available_at() > now:
            raise AppFailure('budget', 'Yerel istek bütçesi doldu. Sonraki zaman İstek bilgileri bölümünde; otomatik tekrar gönderilmedi.')
        # Draw once per outgoing request. Persist the absolute reservation so
        # polling, restart, cancellation and account switching cannot redraw it.
        gate.update(nextAt=now + self.request_delay(), attempts=gate.get('attempts', 0) + 1, lastAt=now, status=None)
        request_budget.record(gate, self.budget_owner(), now)
        self.save()
        self.progress('request', attempts=gate['attempts'])

    def budget_available_at(self):
        return request_budget.next_at(self.data['gate'], self.budget_owner(), self.clock())

    def budget_owner(self):
        return (self.data['account'] or {}).get('id') if self.operation not in ('login', 'import_counter') else None

    def login_device_settings(self, username):
        current = self.data['account']
        if current and current['username'].lower() == username.lower() and self.data['session']:
            return device_settings(self.data['session'])
        return self.login_identity.load()

    def persist_login_identity(self, client):
        self.login_identity.save(client)

    def limited(self, retry=None):
        now, gate = self.clock(), self.data['gate']
        streak = min(gate.get('streak', 0) + 1, 6)
        server = deadline(retry, now) if retry else 0
        gate.update(blockedUntil=max(gate.get('blockedUntil', 0), now + min(86400, 3600 * 2 ** (streak - 1)), server),
                    streak=streak, serverRetryAt=server or None, limitedAt=now, manualOnly=True)
        self.save()

    def after_response(self, response):
        self.data['gate']['status'] = response.status_code
        self.save()
        if response.status_code == 429:
            self.limited(response.headers.get('Retry-After'))
            self.progress('limited', until=self.data['gate']['blockedUntil'], http=429)
            raise AppFailure('limited', 'Instagram HTTP 429 döndürdü. Tarama durdu; otomatik tekrar yapılmayacak.')

    def request_code(self, username=None, choice=None):
        self.waiting_code = True
        self.progress('code', method=getattr(choice, 'name', '2FA') if choice else '2FA')
        end = time.monotonic() + 300
        try:
            while time.monotonic() < end:
                self.check_cancel()
                try:
                    return self.codes.get(timeout=.25)
                except queue.Empty:
                    pass
            raise AppFailure('code_timeout', 'Doğrulama beklemesi sona erdi. Yeni giriş başlatılmadı.')
        finally:
            self.waiting_code = False

    def submit_code(self, code):
        if not self.waiting_code or not isinstance(code, str) or not re.fullmatch(r'[0-9]{6,10}', code):
            raise AppFailure('code', 'Bekleyen doğrulama için 6–10 rakamlı kod gir.')
        try:
            self.codes.put_nowait(code)
        except queue.Full:
            raise AppFailure('code', 'Kod zaten gönderildi.')

    def client(self):
        if not self.data['session']:
            raise AppFailure('session', 'Önce hesabını bağla.')
        client = new_client(self, self.data['session'])
        if str(client.user_id or '') != self.data['account']['id']:
            close_client(client)
            raise AppFailure('identity', 'Kayıtlı oturumun hesap kimliği uyuşmuyor.')
        return client

    def event(self, kind, title, identity=None, old=None, new=None, topic=None, group_id=None):
        record = {'id': uuid.uuid4().hex, 'at': self.clock(), 'type': kind,
                  'title': title, 'personId': identity, 'old': old, 'new': new}
        if topic:
            record['topic'] = topic
        self.data['events'].insert(0, record)
        del self.data['events'][2000:]
        notification_log.record(self.data, record, group_id or self.observation_group or uuid.uuid4().hex)

    def record_profile(self, profile, own=False):
        identity = profile['id']
        previous = self.data['account'] if own else self.data['people'].get(identity)
        previous = previous or {}
        history = self.data['history'].setdefault(identity, [])
        profile = {**profile, 'observedAt': self.clock(),
                   **{k: previous.get(k, default) for k, default in
                      (('favorite', False), ('watching', False), ('group', ''), ('note', ''))}}
        group_id = self.observation_group or uuid.uuid4().hex
        if 'avatar' not in profile and profile.get('picture') == previous.get('picture'):
            profile['avatar'] = previous.get('avatar')
        for key in PROFILE_FIELDS:
            old = next((h[key] for h in reversed(history) if h.get(key) is not None), None)
            new = profile.get(key)
            if old is not None and new is not None and old != new:
                self.event('profile', f"@{profile['username']} · {key}", identity, {key: old}, {key: new}, group_id=group_id)
        history.append({key: profile.get(key) for key in ('observedAt', *PROFILE_FIELDS)})
        wayback.record(self.data, profile)
        del history[:-366]
        if own:
            self.data['account'] = profile
            if (self.data['settings']['investigateMissing'] and self.data['people'] and not self.list_scan_active
                    and type(previous.get('followers')) is int and profile['followers'] < previous['followers']):
                baseline = self.data['lists'].get(identity + ':followers', {}).get('snapshots', [])
                self.data['accessPlan'] = {'status': 'pending' if baseline and baseline[-1]['complete'] else 'baseline_needed',
                                           'triggerAt': self.clock(), 'followers': profile['followers']}
        else:
            self.data['people'][identity] = profile
        self.event('check', f"@{profile['username']} profili kontrol edildi", identity)

    def read_profile(self, client, identity, avatar=True):
        self.progress('profile', personId=identity)
        payload = client.private_request(f'users/{identity}/info/', params={
            'is_prefetch': 'false', 'entry_point': 'self_profile' if identity == str(client.user_id) else 'profile',
            'from_module': 'self_profile' if identity == str(client.user_id) else 'profile', 'is_app_start': False})
        result = parse_profile(payload, identity)
        if avatar:
            read_avatar(result, self)
        return result

    def connect(self, args, imported=False):
        client = None
        try:
            if imported:
                path = Path(os.environ['LOCALAPPDATA']) / 'InstagramCounter/session.dat'
                if not path.is_file() or path.stat().st_size > 1024 * 1024:
                    raise AppFailure('import', 'Önceki Windows prototipinde aktarılacak oturum bulunamadı.')
                payload = json.loads(protect(path.read_bytes(), decrypt=True))
                if (not isinstance(payload, dict) or payload.get('version') != 1
                        or not isinstance(payload.get('settings'), dict)
                        or not isinstance(payload.get('account_id'), str)):
                    raise AppFailure('import', 'Eski oturum dosyası desteklenmiyor.')
                client = new_client(self, payload['settings'])
                if str(client.user_id or '') != payload['account_id']:
                    raise AppFailure('identity', 'Aktarılan oturumun hesap kimliği tutarsız.')
            else:
                username, password = args.get('username', '').strip().lstrip('@'), args.get('password', '')
                if not re.fullmatch(r'[A-Za-z0-9._]{1,30}', username) or not isinstance(password, str) or not 1 <= len(password) <= 1024:
                    raise AppFailure('input', 'Geçerli kullanıcı adı ve şifre gir.')
                now = self.clock()
                if self.data['gate'].get('loginAfter', 0) > now:
                    raise AppFailure('login_wait', 'Giriş denemeleri arasında en az 15 dakika beklenir.')
                self.data['gate']['loginAfter'] = now + 900
                self.save()
                self.progress('login')
                client = new_client(self, self.login_device_settings(username))
                try:
                    self.persist_login_identity(client)
                    client.login(username, password)
                finally:
                    client.password = None
                    args.pop('password', None)
                    password = None
                    self.persist_login_identity(client)
            identity = str(client.user_id or '')
            if not identity:
                raise AppFailure('identity', 'Giriş yanıtında hesap kimliği bulunamadı.')
            self.check_connection_identity(identity)
            profile = self.read_profile(client, identity)
            self.check_cancel()
            self.select_connection_account(profile)
            first_connection = self.data['account'] is None
            # Commit a replacement only after the authenticated own profile agrees.
            self.record_profile(profile, own=True)
            self.data['session'] = client.get_settings()
            if first_connection:
                self.data['settings'].update(automatic=True, automaticLists=True)
            for record in self.data['lists'].values():
                if record.get('draft'):
                    record['draft'].pop('nextCursor', None)
                    record['draft'].pop('seenCursors', None)
            self.data['gate']['manualOnly'] = False
            self.data['lastError'] = None
            self.save()
        finally:
            args.pop('password', None)
            close_client(client)

    def check_connection_identity(self, identity):
        if self.data['account'] and self.data['account']['id'] != identity:
            raise AppFailure('account', 'Bu depoda farklı bir hesap var. Önce yedek alıp yerel verileri kaldır.')

    def select_connection_account(self, profile):
        """Called only after the authenticated own profile has been validated."""

    def refresh(self, args):
        client = self.client()
        try:
            identity = str(args.get('id') or self.data['account']['id'])
            if identity != self.data['account']['id'] and identity not in self.data['people']:
                raise AppFailure('input', 'Önce profili Kişiler bölümüne ekle.')
            profile = self.read_profile(client, identity)
            self.check_cancel()
            self.record_profile(profile, own=identity == self.data['account']['id'])
            self.data['session'] = client.get_settings()
            self.data['gate']['manualOnly'] = False
            self.save()
        finally:
            close_client(client)

    def configure_wayback(self, args):
        identity = str(args.get('id') or '')
        profile = self.data['account'] if self.data['account'] and self.data['account']['id'] == identity else self.data['people'].get(identity)
        if not profile:
            raise AppFailure('input', 'Önce profili Kişiler bölümüne ekle.')
        self.wayback_id = identity
        if set(args) != {'id'}:
            wayback.configure(self.data, profile, args)
            self.vault.save(self.data, replace_previous=bool(args.get('clear')))

    def add_person(self, args):
        username = str(args.get('username', '')).strip().lstrip('@')
        if not re.fullmatch(r'[A-Za-z0-9._]{1,30}', username):
            raise AppFailure('input', 'Geçerli bir Instagram kullanıcı adı gir.')
        if len(self.data['people']) >= 200:
            raise AppFailure('capacity', 'En fazla 200 profil kaydedilebilir.')
        client = self.client()
        try:
            self.progress('profile')
            # One explicit mobile lookup. No public fallback or endpoint rotation.
            payload = client.private_request(f'users/{username}/usernameinfo/')
            profile = parse_profile(payload)
            if profile['username'].lower() != username.lower():
                raise AppFailure('identity', 'Aranan kullanıcı adı ile yanıt uyuşmadı.')
            if profile['id'] == self.data['account']['id']:
                raise AppFailure('input', 'Kendi hesabın zaten Özet bölümünde.')
            read_avatar(profile, self)
            self.check_cancel()
            self.record_profile(profile)
            self.data['session'] = client.get_settings()
            self.save()
        finally:
            close_client(client)

    def scan(self, args, *, one_page=False, resume=False):
        direction = args.get('direction')
        if direction not in ('followers', 'following'):
            raise AppFailure('input', 'Liste yönü geçersiz.')
        identity = str(args.get('id') or (self.data['account'] or {}).get('id', ''))
        owner = self.data['account'] if self.data['account'] and identity == self.data['account']['id'] else self.data['people'].get(identity)
        if not owner:
            raise AppFailure('input', 'Liste sahibi kayıtlı değil.')
        client = self.client()
        record = self.data['lists'].setdefault(identity + ':' + direction, {'snapshots': []})
        draft = record.get('draft')
        continuing = resume and list_preparation.resumable(draft, self.clock())
        if not continuing:
            draft = {'id': uuid.uuid4().hex, 'ownerId': identity, 'direction': direction,
                     'startedAt': self.clock(), 'finishedAt': None, 'members': {}, 'complete': False,
                     'status': 'running', 'pages': 0, 'expected': None, 'nextCursor': None, 'seenCursors': []}
            record['draft'] = draft
        schedule = record.setdefault('schedule', {})
        try:
            self.list_scan_active = True
            self.scan_progress = {'ownerId': identity, 'username': owner['username'], 'direction': direction,
                                  'step': 'page' if continuing else 'profile', 'page': None,
                                  'completedPages': draft['pages'], 'count': len(draft['members']), 'expected': draft['expected']}
            draft['status'] = 'running'
            schedule.update(after=0, error=None)
            self.save()
            if not continuing:
                profile = self.read_profile(client, identity, avatar=False)
                self.record_profile(profile, own=identity == self.data['account']['id'])
                draft['expected'] = profile[direction]
                self.scan_progress.update(username=profile['username'], expected=draft['expected'], step='page')
            cursor = draft.get('nextCursor') or ''
            for _ in range(1 if one_page else MAX_PAGES):
                self.check_cancel()
                if draft['pages'] >= MAX_PAGES:
                    raise AppFailure('capacity', 'Tarama sayfa sınırına ulaştı; kısmi kayıt korundu.')
                self.scan_progress.update(page=draft['pages'] + 1)
                self.progress('list')
                params = {'count': 50, 'rank_token': client.rank_token,
                          'search_surface': 'follow_list_page', 'query': '', 'enable_groups': 'true'}
                if cursor:
                    params['max_id'] = cursor
                parsed = parse_page(client.private_request(f'friendships/{identity}/{direction}/', params=params))
                members, next_cursor = parsed.members, parsed.cursor
                if len(set(draft['members']) | set(members)) > MAX_MEMBERS:
                    raise AppFailure('capacity', 'Tarama 20.000 hesap sınırına ulaştı; kısmi kayıt korundu.')
                draft['members'].update(members)
                draft['pages'] += 1
                draft['finishedAt'] = self.clock()
                self.data['session'] = client.get_settings()
                self.scan_progress.update(completedPages=draft['pages'], count=len(draft['members']))
                if parsed.coverage in ('limited', 'unknown'):
                    raise AppFailure('list_coverage', 'Instagram liste kapsamını kısıtlı veya belirsiz döndürdü. Görülen üyeler kısmi kayıt olarak korundu; fark hesabı yapılmadı.')
                digest = hashlib.sha256(next_cursor.encode()).hexdigest() if next_cursor else None
                if next_cursor and (digest in draft['seenCursors'] or not members):
                    raise AppFailure('pagination', 'Liste sayfalaması ilerlemiyor. Döngü durduruldu; kısmi kayıt korundu.')
                draft['nextCursor'] = next_cursor
                if digest:
                    draft['seenCursors'].append(digest)
                if not next_cursor:
                    if len(draft['members']) != draft['expected']:
                        raise AppFailure('list_count', 'Liste sayısı profil sayısıyla uyuşmadı. Kısmi kayıt korundu; fark hesabı yapılmadı.')
                    self.check_cancel()
                    draft.update(complete=True, status='complete')
                    record['snapshots'].append(copy.deepcopy({k:v for k,v in draft.items() if k not in ('nextCursor','seenCursors')}))
                    del record['snapshots'][:-6]
                    record.pop('draft', None)
                    schedule.update(after=self.clock() + list_preparation.MIN_RESCAN, error=None)
                    diff = compare(record['snapshots'][-2], record['snapshots'][-1]) if len(record['snapshots']) > 1 else None
                    self.event('list', f"@{owner['username']} · {direction} listesi kaydedildi", identity,
                               None, {'direction': direction, 'count': len(draft['members']),
                                      'added': len(diff['added']) if diff else None, 'removed': len(diff['removed']) if diff else None})
                    if identity == self.data['account']['id'] and direction == 'followers':
                        access_checks.discover(self.data, record, self.clock())
                        self.data['accessPlan'] = {'status': 'idle'}
                    self.save()
                    self.progress('list_received')
                    return
                draft['status'] = 'queued' if one_page else 'running'
                self.save()
                self.progress('list_received')
                self.check_cancel()
                cursor = next_cursor
            if not one_page:
                raise AppFailure('capacity', 'Tarama sayfa sınırına ulaştı; kısmi kayıt korundu.')
        except Exception as exc:
            code = failure(exc)['code']
            if continuing and getattr(getattr(exc, 'response', None), 'status_code', None) in (400, 404):
                draft['nextCursor'] = None
                draft['seenCursors'] = []
            draft['status'] = 'cancelled' if self.cancel.is_set() else 'partial'
            draft['finishedAt'] = self.clock()
            if code == 'cancelled' and identity == (self.data['account'] or {}).get('id'):
                self.data['settings']['automaticLists'] = False
            schedule.update(error=code, after=self.clock() + (0 if code in ('budget','cooldown','cancelled') else 1800))
            self.save()
            raise
        finally:
            self.list_scan_active = False
            self.scan_progress = None
            close_client(client)

    def known_person(self, identity):
        person = self.data['people'].get(identity)
        if person:
            return person
        for record in self.data['lists'].values():
            for scan in list(reversed(record.get('snapshots', []))) + ([record['draft']] if record.get('draft') else []):
                if identity in scan['members']:
                    return scan['members'][identity]
        case = self.data['access'].get(identity)
        return {key: case[key] for key in ('id', 'username', 'name')} if case else None

    def check_access(self, args):
        identity = str(args.get('id', ''))
        member = self.known_person(identity)
        if not member or identity == (self.data['account'] or {}).get('id'):
            raise AppFailure('input', 'Erişim kontrolü için kayıtlı başka bir hesap seç.')
        client = self.client()
        case = None
        health = {}
        try:
            case = access_checks.ensure_case(self.data, member, self.clock())
            case.update(stage='checking', absence=access_checks.absence_evidence(self.data, identity))
            self.save()
            previous = case['checks'][-1] if case['checks'] else None
            self.progress('access_health')
            own = self.read_profile(client, self.data['account']['id'], avatar=False)
            if access_checks.response_status(client) != 200:
                raise AppFailure('access_unknown', 'Kendi profilinin başarılı HTTP 200 yanıtı doğrulanamadı. Hedef profil kontrol edilmedi.')
            self.record_profile(own, own=True)
            health = {'ownCheckedAt': self.clock(), 'ownHTTP': access_checks.response_status(client)}
            self.check_cancel()
            self.progress('access_target', personId=identity)
            outcome = access_checks.probe(client, identity)
            self.check_cancel()
            anonymous = None
            if outcome['status'] == 'unavailable':
                self.progress('access_anonymous', personId=identity)
                # A separate fresh session: the authenticated client is never
                # supplied to the public-page reader.
                anonymous = anonymous_visibility.probe(self, identity, access_checks.last_username(self.data, identity, member))
                self.check_cancel()
            result = {**access_checks.assess(outcome, case['absence'], anonymous), **health, 'checkedAt': self.clock(),
                      'absence': copy.deepcopy(case['absence'])}
            if outcome.get('username'):
                result['usernameChanged'] = outcome['username'] != case['username']
            case['checks'].append(result)
            del case['checks'][:-10]
            case['stage'] = 'done'
            self.data['session'] = client.get_settings()
            strength = 'strong' if result['reason'] == 'anonymous_visible' else 'limited'
            suspected = result['status'] == 'suspected' and (not case['alertRecorded'] or
                         strength == 'strong' and case.get('alertStrength') != 'strong')
            if suspected:
                case['alertRecorded'] = True
                case['alertStrength'] = strength
            title = (f"Güçlü engellenme şüphesi · @{member['username']}: Aynı hesap oturumsuz görünür, bağlı oturumdan erişilemiyor. Kesin engelleme kanıtı değildir."
                     if suspected else f"@{case['username']} · Erişim kontrolü kaydedildi")
            self.event('profile' if suspected else 'check', title, identity, previous, result, topic='access')
            self.save()
        except Exception as exc:
            if case is not None and not isinstance(exc, StorageFailure):
                case['stage'] = 'done'
                case['checks'].append({'status': 'unknown', 'reason': failure(exc)['code'],
                                       **health, 'checkedAt': self.clock()})
                del case['checks'][:-10]
                self.save()
            raise
        finally:
            close_client(client)

    def save_person(self, args):
        identity = str(args.get('id', ''))
        p = self.data['people'].get(identity)
        if not p:
            # A list member can be locally saved without a profile request.
            known = self.known_person(identity)
            p = dict(known) if known else None
            if not p or len(self.data['people']) >= 200:
                raise AppFailure('input', 'Kişi bulunamadı veya kişi kapasitesi doldu.')
            self.data['people'][identity] = p
        for field, limit in (('note', 2000), ('group', 80)):
            if field in args:
                if not isinstance(args[field], str) or len(args[field]) > limit:
                    raise AppFailure('input', 'Not veya grup uzunluğu geçersiz.')
                p[field] = args[field]
        for field in ('favorite', 'watching'):
            if field in args:
                if type(args[field]) is not bool:
                    raise AppFailure('input', 'Tercih değeri geçersiz.')
                p[field] = args[field]
        self.save()

    def settings(self, args):
        if 'theme' in args:
            if type(args['theme']) is not str or args['theme'] not in ('system', 'light', 'dark', 'oled'):
                raise AppFailure('input', 'Ayar değeri geçersiz.')
            self.data['settings']['theme'] = args['theme']
        if 'language' in args:
            if type(args['language']) is not str or args['language'] not in localization.LANGUAGES:
                raise AppFailure('input', 'Dil değeri geçersiz.')
            self.data['settings']['language'] = args['language']
        for key in ('compact', 'notifications', 'automatic', 'automaticLists', 'investigateMissing'):
            if key in args:
                if type(args[key]) is not bool:
                    raise AppFailure('input', 'Ayar değeri geçersiz.')
                self.data['settings'][key] = args[key]
        if 'interval' in args:
            if type(args['interval']) is not int or args['interval'] not in CONTROL_INTERVALS:
                raise AppFailure('input', 'Kontrol aralığı geçersiz.')
            self.data['settings']['interval'] = args['interval']
        self.save()

    def notification_rule(self, args):
        identity = str(args.get('id', ''))
        if identity != (self.data['account'] or {}).get('id') and not self.known_person(identity):
            raise AppFailure('input', 'Bildirim tercihi için kayıtlı bir hesap seç.')
        notification_log.set_rule(self.data, identity, args)
        self.save()

    def read_notification(self, args):
        notification_log.mark_read(self.data, args.get('id'), self.clock())
        self.save()

    def automatic_plan(self):
        """One owner for per-profile deadlines and optional investigation cadence."""
        d = self.data
        candidates = ([d['account']] if d['account'] else []) + [p for p in d['people'].values() if p.get('watching')]
        interval = d['settings']['interval'] * 60
        profiles = sorted([(max(p.get('observedAt') or 0, d['automaticAttempts'].get(p['id'], 0)) + interval, p['id'])
                           for p in candidates])
        pending = [c for c in d['access'].values() if c['stage'] == 'pending' and c['id'] in d['people']] if d['settings']['investigateMissing'] else []
        investigation = ('access', min(pending, key=lambda c: c['detectedAt'])['id']) if pending else None
        return profiles, pending, investigation

    def monitor_status(self):
        d = self.data
        profiles, pending, investigation = self.automatic_plan()
        interval = d['settings']['interval'] * 60
        deadlines = [due for due, _ in profiles]
        lists = list_preparation.next_job(d, self.clock())
        if lists:
            deadlines.append(lists['dueAt'])
        if investigation:
            deadlines.append(d['lastInvestigationAt'] + interval)
        reason = ('disabled' if not d['settings']['automatic'] else 'session' if not d['session'] else
                  'restricted' if d['gate'].get('manualOnly') else None)
        return {'enabled': d['settings']['automatic'], 'held': reason is not None, 'reason': reason, 'listPlanVersion': 1,
                'profileCount': len(profiles), 'intervalSeconds': interval,
                'estimatedCycleSeconds': max(interval, sum(REQUEST_SPACING) / 2 * len(profiles)),
                'accessPending': len(pending), 'followerScanPending': bool(investigation and investigation[0] == 'followers_scan'),
                'nextAt': None if reason or not deadlines else max(min(deadlines), self.budget_available_at(),
                                                   d['gate'].get('blockedUntil', 0), d['gate'].get('nextAt', 0))}

    def automatic(self):
        now, d = self.clock(), self.data
        status = self.monitor_status()
        if status['held'] or status['nextAt'] is None or status['nextAt'] > now:
            return
        profiles, _, investigation = self.automatic_plan()
        profile_due = profiles and profiles[0][0] <= now
        lists = list_preparation.next_job(d, now)
        if lists and lists['dueAt'] <= now and (not profile_due or d['lastAutomaticTask'] != 'lists'):
            d['lastAutomatic'] = now
            d['lastAutomaticTask'] = 'lists'
            self.scan({'direction':lists['direction']}, one_page=True, resume=True)
            return
        investigation_due = investigation and d['lastInvestigationAt'] + d['settings']['interval'] * 60 <= now
        d['lastAutomatic'] = now
        # Optional work may take a slot, but cannot monopolize due profiles.
        if investigation_due and (not profile_due or d['lastAutomaticTask'] != 'access'):
            task, identity = investigation
            d['lastInvestigationAt'] = now
            d['lastAutomaticTask'] = task
            self.save()
            self.check_access({'id': identity})
            return
        if not profile_due:
            return
        d['lastAutomaticTask'] = 'profile'
        identity = profiles[0][1]
        # Reserve before I/O: transient failure, cancellation and restart must
        # not immediately retry this profile or delay other due identities.
        d['automaticAttempts'][identity] = now
        self.save()
        self.refresh({'id': identity})

    def export_backup(self, path):
        value = copy.deepcopy(self.data)
        own = (value['account'] or {}).get('id')
        value['gate']['accountRequestTimes'] = {key:times for key,times in value['gate'].get('accountRequestTimes', {}).items() if key == own}
        value['session'] = None
        for record in value['lists'].values():
            if record.get('draft'):
                record['draft'].pop('nextCursor', None)
                record['draft'].pop('seenCursors', None)
        value['settings']['automatic'] = False
        value['lastError'] = None
        raw = json.dumps({'format': 'InstagramTracker-observations', 'version': 1, 'data': value},
                         ensure_ascii=False, allow_nan=False).encode()
        atomic_write(Path(path), protect(raw))

    def import_backup(self, path):
        path = Path(path)
        if path.stat().st_size > MAX_VAULT:
            raise AppFailure('backup', 'Yedek dosyası çok büyük.')
        try:
            value = json.loads(protect(path.read_bytes(), decrypt=True))
            restored = value['data']
            if value.get('format') != 'InstagramTracker-observations' or value.get('version') != 1:
                raise ValueError()
            # Round-trip through the exact vault schema validator before replace.
            access_checks.initialize(restored)
            notification_log.initialize(restored)
            localization.initialize(restored)
            restored['settings'].setdefault('theme', 'system')
            initialize_schedule(restored)
            list_preparation.initialize(restored)
            wayback.initialize(restored)
            validate_archive(restored)
        except Exception as exc:
            raise AppFailure('backup', 'Yedek geçersiz veya başka bir Windows kullanıcısına ait. Mevcut kayıtlar korundu.') from exc
        self.prepare_restore(restored)
        old_gate = self.data['gate']
        gate = restored['gate']
        for key in ('blockedUntil', 'nextAt', 'loginAfter', 'attempts', 'streak'):
            gate[key] = max(old_gate.get(key, 0), gate.get(key, 0))
        gate['manualOnly'] = True
        gate['requestTimes'] = old_gate.get('requestTimes', [])
        gate['accountRequestTimes'] = old_gate.get('accountRequestTimes', {})
        restored['session'] = None
        restored['settings']['automatic'] = False
        self.data = restored
        self.save()

    def prepare_restore(self, restored):
        if (self.data['account'] and
                self.data['account']['id'] != (restored['account'] or {}).get('id')):
            raise AppFailure('backup', 'Yedek başka bir hesaba ait. Önce ilgili hesabı seç.')

    def run(self, operation, args, *, group_id=None, project=True):
        self.operation = operation
        self.observation_group = group_id or uuid.uuid4().hex
        while not self.codes.empty():
            self.codes.get_nowait()
        try:
            if self.offline and operation in ('login', 'import_counter', 'refresh', 'scan', 'add_person', 'automatic', 'check_access'):
                raise AppFailure('offline', 'Bu yalıtılmış test penceresinde Instagram bağlantısı kapalı.')
            handlers = {'wayback': lambda: self.configure_wayback(args), 'login': lambda: self.connect(args), 'import_counter': lambda: self.connect(args, True),
                        'refresh': lambda: self.refresh(args), 'add_person': lambda: self.add_person(args),
                        'scan': lambda: self.scan(args), 'save_person': lambda: self.save_person(args),
                        'settings': lambda: self.settings(args), 'automatic': self.automatic,
                        'check_access': lambda: self.check_access(args),
                        'notification_rule': lambda: self.notification_rule(args),
                        'read_notification': lambda: self.read_notification(args),
                        'export_backup': lambda: self.export_backup(args['path']),
                        'import_backup': lambda: self.import_backup(args['path'])}
            if operation == 'state':
                return self.snapshot() if project else None
            if operation == 'remove_person':
                identity = str(args.get('id', ''))
                self.data['people'].pop(identity, None)
                self.data['automaticAttempts'].pop(identity, None)
                self.save()
            elif operation == 'disconnect':
                self.data['session'] = None
                self.data['settings']['automatic'] = False
                self.save()
                # Session removal also purges the immediately preceding backup.
                self.vault.backup.unlink(missing_ok=True)
            elif operation == 'delete_all':
                self.data = blank(dict(self.data['gate']))
                self.save()
                self.vault.backup.unlink(missing_ok=True)
            elif operation in handlers:
                # Changing language is not a new account check; retain the
                # existing error so its current-language projection can update.
                if operation != 'settings' or not set(args).issubset({'language', 'theme'}):
                    self.data['lastError'] = None
                handlers[operation]()
            else:
                raise AppFailure('operation', 'Desteklenmeyen işlem.')
            return self.snapshot() if project else None
        except Exception as exc:
            if isinstance(exc, StorageFailure):
                # Never report volatile edits as successfully persisted records.
                self.data = self.load_vault()
            error = failure(exc)
            if error['code'] == 'limited' and not self.data['gate'].get('manualOnly'):
                self.limited()
            if error['code'] in ('session', 'verification'):
                self.data['gate']['manualOnly'] = True
            self.data['lastError'] = {**error, 'at': self.clock()}
            self.event('error', error['message'])
            try:
                self.save()
            except StorageFailure:
                pass
            raise AppFailure(error['code'], error['message']) from exc
        finally:
            args.pop('password', None)
            self.operation = None
            self.observation_group = None
            # Only the explicit archive operation carries thumbnail details.
            # Later automatic checks must not serialize the album repeatedly.
            self.wayback_id = None


def initialize_schedule(d):
    # Existing observations already carry per-profile successful read times.
    # Preserve the old background timestamp only as the auxiliary job cadence.
    d.setdefault('automaticAttempts', {})
    d.setdefault('lastAutomaticTask', 'profile')
    d.setdefault('lastInvestigationAt', d.get('lastAutomatic', 0))


def validate_archive(d):
    """Validate the owning representation before it reaches UI or scheduling."""
    if (not isinstance(d, dict) or d.get('version') != 1 or not isinstance(d.get('people'), dict)
            or len(d['people']) > 200 or not isinstance(d.get('lists'), dict)
            or not isinstance(d.get('history'), dict) or not isinstance(d.get('events'), list)
            or len(d['events']) > 2000 or not isinstance(d.get('gate'), dict)
            or not isinstance(d.get('settings'), dict)):
        raise ValueError('schema')
    request_budget.validate_gate(d['gate'])
    list_preparation.validate(d)
    if d.get('session') is not None and (not isinstance(d['session'], dict) or d.get('account') is None):
        raise ValueError('session')
    def identity(value):
        return type(value) is str and re.fullmatch(r'[1-9][0-9]{0,29}', value)
    def timestamp(value):
        return type(value) in (int, float) and math.isfinite(value) and value >= 0
    def member(p):
        if (not isinstance(p, dict) or not identity(p.get('id'))
                or type(p.get('username')) is not str or not re.fullmatch(r'[A-Za-z0-9._]{1,30}', p['username'])
                or (p.get('name') is not None and type(p['name']) is not str)):
            raise ValueError('identity')
    for p in [d.get('account'), *d['people'].values()]:
        if p is None:
            continue
        member(p)
        for key in ('followers', 'following', 'posts'):
            if p.get(key) is not None and (type(p[key]) is not int or p[key] < 0):
                raise ValueError('count')
        for key in ('note', 'group', 'bio', 'url', 'picture', 'avatar'):
            if p.get(key) is not None and type(p[key]) is not str:
                raise ValueError('text')
    if any(key != p['id'] for key, p in d['people'].items()):
        raise ValueError('person key')
    for key in ('blockedUntil', 'nextAt', 'loginAfter', 'attempts', 'streak'):
        if key in d['gate'] and not timestamp(d['gate'][key]):
            raise ValueError('gate')
    if type(d['settings'].get('interval')) is not int or d['settings']['interval'] not in CONTROL_INTERVALS:
        raise ValueError('settings')
    for key in ('automatic', 'notifications', 'compact'):
        if type(d['settings'].get(key)) is not bool:
            raise ValueError('settings')
    if d['settings'].get('language') not in localization.LANGUAGES:
        raise ValueError('language')
    if d['settings'].get('theme', 'system') not in ('system', 'light', 'dark', 'oled'):
        raise ValueError('theme')
    for key, record in d['lists'].items():
        owner, separator, direction = key.partition(':')
        if not separator or not identity(owner) or direction not in ('followers', 'following'):
            raise ValueError('list identity')
        if not isinstance(record, dict) or not isinstance(record.get('snapshots'), list) or len(record['snapshots']) > 6:
            raise ValueError('lists')
        for snapshot in record['snapshots'] + ([record['draft']] if record.get('draft') else []):
            if not isinstance(snapshot, dict) or not isinstance(snapshot.get('members'), dict) or len(snapshot['members']) > MAX_MEMBERS:
                raise ValueError('members')
            if type(snapshot.get('complete')) is not bool:
                raise ValueError('complete')
            for identity_key, p in snapshot['members'].items():
                member(p)
                if identity_key != p['id']:
                    raise ValueError('member key')
    for key, history in d['history'].items():
        if not identity(key) or type(history) is not list or len(history) > 366:
            raise ValueError('history')
        for h in history:
            if not isinstance(h, dict) or not timestamp(h.get('observedAt')):
                raise ValueError('history')
    for event in d['events']:
        if (not isinstance(event, dict) or type(event.get('id')) is not str
                or type(event.get('title')) is not str or not timestamp(event.get('at'))
                or event.get('type') not in ('profile', 'list', 'check', 'error')):
            raise ValueError('event')
    if (not timestamp(d.get('lastAutomatic')) or not timestamp(d.get('lastInvestigationAt'))
            or not isinstance(d.get('automaticAttempts'), dict) or len(d['automaticAttempts']) > 201
            or any(not identity(key) or not timestamp(value) for key, value in d['automaticAttempts'].items())):
        raise ValueError('automatic')
    for p in [d.get('account'), *d['people'].values()]:
        if p is not None and p.get('observedAt') is not None and not timestamp(p['observedAt']):
            raise ValueError('profile observation time')
    if d.get('lastAutomaticTask') not in ('profile', 'access', 'followers_scan', 'lists'):
        raise ValueError('automatic task')
    access_checks.validate(d)
    notification_log.validate(d)
    wayback.validate(d)
