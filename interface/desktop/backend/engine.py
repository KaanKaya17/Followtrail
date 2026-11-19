"""Local observations, paging, comparison and guarded account operations."""
import copy
import csv
from datetime import date
import daily_stats
import stories
import list_pages
import membership
import read_health
from list_pages import public_member
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
import profile_search
import profile_links
import profile_about
from person_text import GROUP_CHARACTERS, NOTE_CHARACTERS
import hashlib
import data_privacy
from search_cache import SearchCache
from profile_facts import ProfileFacts, PROFILE_DETAILS_SECONDS
from story_limits import CAPABILITIES_JSON
from login_identity import LoginIdentity, device_settings

from storage import Vault, StorageFailure, protect, MAX_VAULT
from network import (AppFailure, new_client, close_client, parse_profile,
                     parse_page, failure, deadline)
from network import read_avatar, avatar_url

from archive_schema import MAX_MEMBERS, blank, initialize_schedule, validate_archive
from backup_service import BackupService
MAX_PAGES = 400
from request_defaults import CONTROL_INTERVALS, REQUEST_SPACING, DEFAULT_INTERVAL_MINUTES, MEDIA_SPACING
PROFILE_FIELDS = ('username', 'name', 'bio', 'url', 'private', 'verified', 'followers', 'following', 'posts', 'picture')


def compare(previous, current):
    if not previous or not current or not previous.get('complete') or not current.get('complete'):
        return None
    old, new = set(previous['members']), set(current['members'])
    return {'added': sorted(new - old), 'removed': sorted(old - new),
            'from': previous['finishedAt'], 'to': current['finishedAt']}


def public_person(value):
    # Explicit projection: saved session/auth never crosses into the renderer.
    keys = ('id', 'username', 'name', 'followers', 'following', 'posts', 'bio', 'url',
            'private', 'verified', 'observedAt', 'favorite', 'watching', 'group', 'note', 'avatar', 'picture', 'links', 'about')
    return {key: value.get(key) for key in keys}


class Engine:
    def __init__(self, directory, emit=lambda event: None, clock=time.time, *, request_delay=None, media_delay=None):
        self.vault = Vault(directory)
        self._daily_current = None
        self._daily_view = None
        self.data = self.load_vault()
        self.emit = emit
        self.clock = clock
        self.request_delay = request_delay or (lambda: random.uniform(*REQUEST_SPACING))
        self.media_delay = media_delay or (lambda: random.uniform(*MEDIA_SPACING))
        self.login_identity = LoginIdentity(getattr(self, 'directory', directory))
        self.cancel = threading.Event()
        self.codes = queue.Queue(maxsize=1)
        self.waiting_code = False
        self.operation = None
        self.login_stage = None
        self.login_http = None
        self.login_request = 0
        self.observation_group = None
        self.offline = False
        self.list_scan_active = False
        self.scan_progress = None
        self.wayback_id = None
        self.search_results = None
        self.search_cache = SearchCache()
        self.profile_facts = ProfileFacts()
        self.profile_detail_result = None
        self.story_detail = None
        self.list_pages = list_pages.ListPages()
        self.recover_interrupted()

    # Outside effects go through these methods so tests can replace them on
    # one engine object. Code moved out of this module keeps calling them
    # through the engine, so those replacements still apply after the move.
    max_vault = MAX_VAULT

    def new_client(self, settings):
        return new_client(self, settings)

    def close_client(self, client):
        close_client(client)

    def read_avatar(self, profile):
        read_avatar(profile, self)

    def protect(self, value, *, decrypt=False):
        return protect(value, decrypt=decrypt)

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

    def save(self, *, replace_previous=False):
        self.vault.save(self.data, replace_previous=replace_previous)

    def load_vault(self):
        data = self.vault.load() or blank()
        access_checks.initialize(data)
        notification_log.initialize(data)
        localization.initialize(data)
        data['settings'].setdefault('theme', 'system')
        initialize_schedule(data)
        list_preparation.initialize(data)
        wayback.initialize(data)
        data_privacy.initialize(data)
        validate_archive(data)
        membership.ensure(data)
        return data

    def daily_store(self, owner=None):
        owner = owner or (self.data['account'] or {}).get('id')
        key = (self.vault.path.parent, owner)
        if self._daily_current is None or self._daily_current[0] != key or self._daily_current[1].closed:
            self._daily_current = (key, daily_stats.DailyStore(key[0], owner))
        days=self.data['settings']['dailyRetentionDays']
        self._daily_current[1].retention_after=max(0,(int(self.clock()*1000)//daily_stats.DAY-days+1)*daily_stats.DAY-1) if days else 0
        return self._daily_current[1]

    def maintain_privacy(self, *, force=False):
        owner=(self.data['account'] or {}).get('id')
        settings=self.data['settings']
        if not owner or not any(settings[key] for key in data_privacy.FIELDS):
            return
        now=self.clock()
        if not force and now-self.data['privacyMaintenance']['at'] < data_privacy.DAY:
            return
        self.data['privacyMaintenance']={'at':now,'failed':False}
        try:
            archive_before=data_privacy.cutoff(settings['historyRetentionDays'],now)
            daily_before=self.daily_store().retention_after+1 if settings['dailyRetentionDays'] else 0
            # External owners finish their own erasure before the account publishes trimmed facts.
            self.daily_store().prune(daily_before)
            stories.StoryStore(self.vault.path.parent,owner).prune(archive_before)
            self.data=data_privacy.retained(self.data,archive_before)
            self.save(replace_previous=True)
        except (OSError,ValueError,StorageFailure,AppFailure):
            self.data['privacyMaintenance']['failed']=True
            self.save()

    def erase_person(self, identity):
        if not re.fullmatch(r'[1-9][0-9]{0,29}',identity) or identity==(self.data['account'] or {}).get('id') or not self.data['account']:
            raise AppFailure('input','Silmek için kayıtlı bir profil seç.')
        # Explicit erasure may be retried even if an earlier attempt already removed part of the data.
        times=[p['observedAt'] for p in self.data['history'].get(identity,[])]
        self.daily_store().clear(identity,max([round(self.clock()*1000),*[round(at*1000) for at in times]]))
        stories.StoryStore(self.vault.path.parent,self.data['account']['id']).clear(identity)
        data_privacy.erase(self.data,identity)
        if self.wayback_id==identity:self.wayback_id=None
        self.profile_detail_result=None
        self.story_detail=None
        self.search_results=None
        self.search_cache.clear()
        self.save(replace_previous=True)

    def daily_query(self, args):
        view = self._daily_view
        if (not isinstance(args,dict) or set(args) != {'accountId','id','metric','range'} or
                not view or args.get('accountId') != view[0] or args.get('id') not in view[2] or
                args.get('metric') not in daily_stats.METRICS or type(args.get('range')) is not int or args['range'] not in (0,7,30,365)):
            raise AppFailure('input', 'Geçersiz günlük geçmiş isteği.')
        store = view[1]
        with store.lock:
            store.record(args['id'], view[2][args['id']])
            result = store.query(args['id'],args['metric'],args['range'],round(self.clock()*1000))
            store.warning = False
        return dict(result,accountId=view[0],id=args['id'],metric=args['metric'],range=args['range'])

    def clear_daily(self, args):
        own = (self.data['account'] or {}).get('id')
        if args.get('accountId') != own or args.get('id') not in {own,*self.data['people']}:
            raise AppFailure('input', 'Geçersiz günlük geçmiş isteği.')
        times = [p['observedAt'] for p in self.data['history'].get(args['id'], [])]
        self.daily_store().clear(args['id'],max([round(self.clock()*1000),*[round(t*1000) for t in times]]))

    def snapshot(self):
        d = self.data
        if d['account']:
            store = self.daily_store()
            seeds = {identity:tuple(p for p in d['history'].get(identity, [profile]) if
                     type(p.get('observedAt')) in (int,float) and math.isfinite(p['observedAt']) and p['observedAt']>0) for identity,profile in
                     [(d['account']['id'],d['account']), *d['people'].items()]}
            self._daily_view = (d['account']['id'], store, seeds)
        else:
            store = None
            self._daily_view = None
        published = self.list_pages.publish(d)
        own_following = published['records'].get((d['account'] or {}).get('id', '') + ':following', {})
        last_following = next((scan for scan in reversed(own_following.get('snapshots', []))
                               if scan.get('complete')), None)
        people = []
        for profile in d['people'].values():
            person = public_person(profile)
            if (profile.get('private') is True and last_following is not None and
                    len(last_following['members']) == d['account'].get('following')):
                person['inLastFollowingList'] = profile['id'] in last_following['members']
            people.append(person)
        records = {key: {'snapshots': [list_pages.metadata(s) for s in record.get('snapshots', [])],
                         'draft': list_pages.metadata(record.get('draft')),
                         'retainedPartial': list_pages.metadata(record.get('retainedPartial')),
                         'visiblePartialId': (list_pages.visible_partial(record) or {}).get('id'),
                         'initialPending': d['lists'][key].get('schedule', {}).get('initial') is not None,
                         'canResume': list_preparation.resumable(record.get('draft'), self.clock()),
                         'movement': membership.counts(record.get('membership'))}
                   for key, record in published['records'].items()}
        return {'account': public_person(d['account']) if d['account'] else None,
                'connected': bool(d['session']), 'people': people,
                'history': d['history'], 'lists': records, 'listsRevision': published['revision'], 'events': d['events'],
                'dailyRevision': {identity:store.revisions.get(identity,store.revision) for identity in seeds} if store else {}, 'dailyWarning': store.warning if store else False,
                'gate': d['gate'], 'settings': d['settings'], 'privacyMaintenance': d['privacyMaintenance'], 'lastError': localization.error(d['lastError'], d['settings']['language']),
                'readHealth': d.get('readHealth', {}),
                'requestPacing': {'minSeconds': REQUEST_SPACING[0], 'maxSeconds': REQUEST_SPACING[1]},
                'controlIntervals': list(CONTROL_INTERVALS),
                'lastLoginError': localization.error(d.get('lastLoginError'), d['settings']['language']),
                'requestBudget': {**request_budget.policy(), 'nextAt':self.budget_available_at(), **request_budget.usage(d['gate'], (d['account'] or {}).get('id'), self.clock())},
                'monitor': self.monitor_status(),
                'listPreparation': list_preparation.view(d, self.clock()),
                'access': access_checks.cases_view(d), 'accessPlan': d['accessPlan'],
                'notificationRules': d['notificationRules'], 'notificationLog': d['notificationLog'],
                'notificationTypes': {key: localization.translate(value, d['settings']['language']) for key, value in notification_log.TYPES.items()},
                'wayback': wayback.metadata(d),
                'waybackDetail': wayback.view(d, self.wayback_id) if self.wayback_id else None,
                'searchResults': self.search_results, 'searchCacheRevision': self.search_cache.revision,
                'profileDetail': self.profile_detail_result,
                'counterAvailable': (Path(os.environ.get('LOCALAPPDATA', '')) / 'InstagramCounter/session.dat').is_file(),
                'storyDetail': self.story_detail,
                'storagePath': str(self.vault.path.parent)}

    def story_target(self, args):
        owner = (self.data['account'] or {}).get('id')
        target = args.get('id')
        if (not owner or args.get('accountId') != owner or not stories.identity(target) or
                target not in {owner,*self.data['people']}):
            raise AppFailure('input', 'Geçersiz hikâye isteği.')
        return stories.StoryStore(self.vault.path.parent,owner), target

    def story_archive(self, args, *, fetch=False, clear=False):
        store,target = self.story_target(args)
        try:
            if clear:
                store.clear(target)
            if fetch:
                client = self.client()
                try:
                    # One chosen mobile GET; the library's automatic public
                    # fallback, seen events and media-info helpers are not used.
                    payload = client.private_request('feed/user/'+target+'/story/', params={
                        'supported_capabilities_new':CAPABILITIES_JSON})
                    items = stories.parse(payload,target)
                    summary = {'returned':len(items),'saved':0,'existing':0,'metadata':0}
                    for index,item in enumerate(items):
                        self.check_cancel()
                        self.progress('stories',current=index+1,total=len(items))
                        if store.contains(target,item['id']):
                            summary['existing'] += 1
                            continue
                        # Keep known metadata even if a later download stops.
                        store.record(target,item,self.clock())
                        if item['mediaUrl'] and item['type'] in ('image','video'):
                            media = stories.download(item['mediaUrl'],item['type'],self)
                            self.check_cancel()
                            store.record(target,item,self.clock(),media)
                            summary['saved'] += 1
                        else:
                            summary['metadata'] += 1
                    self.data['session'] = client.get_settings()
                    self.save()
                    store.checked(target,self.clock(),summary)
                finally:
                    self.close_client(client)
        finally:
            self.story_detail = store.view(target)

    def story_media(self, args):
        store,target = self.story_target(args)
        if not stories.identity(args.get('storyId')):
            raise AppFailure('input', 'Geçersiz hikâye isteği.')
        return store.media(target,args['storyId'])

    def progress(self, phase, **details):
        context = {'scan': dict(self.scan_progress)} if self.scan_progress is not None else {}
        if self.login_stage is not None:
            context.update(loginStage=self.login_stage, loginHttp=self.login_http)
        self.emit({'type': 'progress', 'phase': phase, **context, **details})

    def set_login_stage(self, stage):
        self.login_stage = stage
        self.login_http = None
        self.progress('login')

    def check_cancel(self):
        if self.cancel.is_set():
            raise AppFailure('cancelled', 'İşlem iptal edildi. Tamamlanan gözlemler ve kısmi liste korundu.')

    def before_request(self):
        if self.login_stage is not None:
            self.login_http = None
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
        if self.login_stage is not None:
            self.login_request += 1
        self.progress('request', attempts=gate['attempts'])

    def before_media_request(self):
        """Pace a cookieless CDN download on its own lane.

        Media never spends the account/device API budget or moves the API
        reservation. Any API restriction (429 cooldown, manual stop) still
        stops media, and a CDN 429 applies the same shared restriction.
        """
        self.check_cancel()
        gate = self.data['gate']
        now = self.clock()
        if gate.get('blockedUntil', 0) > now:
            raise AppFailure('cooldown', 'Yerel mola sürüyor. Bitiş saati İstek bilgileri bölümünde.')
        if request_budget.media_next_at(gate, now) > now:
            raise AppFailure('budget', 'Yerel medya indirme sınırı doldu. Kalan hikâyeler sonraki kontrolde indirilebilir.')
        remaining = gate.get('mediaNextAt', 0) - now
        if remaining > 0:
            if self.cancel.wait(remaining):
                self.check_cancel()
        self.check_cancel()
        now = self.clock()
        if gate.get('blockedUntil', 0) > now:
            raise AppFailure('cooldown', 'Yerel mola sürüyor. Bitiş saati İstek bilgileri bölümünde.')
        gate['mediaNextAt'] = now + self.media_delay()
        request_budget.record_media(gate, now)
        self.save()

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
        if self.login_stage is not None:
            self.login_http = response.status_code
        self.save()
        if response.status_code == 429:
            self.limited(response.headers.get('Retry-After'))
            self.progress('limited', until=self.data['gate']['blockedUntil'], http=429)
            message = ('Instagram HTTP 429 döndürdü. Bağlantı kurulamadı; otomatik tekrar yapılmayacak.'
                       if self.login_stage else 'Instagram HTTP 429 döndürdü. Tarama durdu; otomatik tekrar yapılmayacak.')
            raise AppFailure('limited', message, http=429)

    def request_code(self, username=None, choice=None):
        self.waiting_code = True
        if self.login_stage is not None:
            self.login_stage = 'verification'
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
        client = self.new_client(self.data['session'])
        if str(client.user_id or '') != self.data['account']['id']:
            self.close_client(client)
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

    def record_profile(self, profile, own=False, *, observed_at=None, emit_events=True):
        identity = profile['id']
        previous = self.data['account'] if own else self.data['people'].get(identity)
        previous = previous or {}
        history = self.data['history'].setdefault(identity, [])
        at = self.clock() if observed_at is None else observed_at
        if not 0 <= at <= self.clock() or at < (previous.get('observedAt') or 0):
            raise AppFailure('cancelled', 'Daha yeni profil gözlemi korundu.')
        profile = {**profile, 'observedAt': at,
                   **{k: previous.get(k, default) for k, default in
                      (('favorite', False), ('watching', False), ('group', ''), ('note', ''))}}
        if previous.get('about') is not None:
            profile['about'] = previous['about']
        group_id = self.observation_group or uuid.uuid4().hex
        if 'avatar' not in profile and profile.get('picture') == previous.get('picture'):
            profile['avatar'] = previous.get('avatar')
        for key in PROFILE_FIELDS:
            old = next((h[key] for h in reversed(history) if h.get(key) is not None), None)
            new = profile.get(key)
            if emit_events and old is not None and new is not None and old != new:
                self.event('profile', f"@{profile['username']} · {key}", identity, {key: old}, {key: new}, group_id=group_id)
        owner = identity if own else (self.data['account'] or {}).get('id')
        if owner:
            store = self.daily_store(owner)
            try:
                store.record(identity,[*history,profile])
                store.warning = False
            except Exception:
                store.warning = True
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
        if emit_events:
            self.event('check', f"@{profile['username']} profili kontrol edildi", identity)
        self.profile_facts.record(self.data, profile)

    def diagnose_read(self, reader, error, client, identity, *, continuation=False):
        status, paused = read_health.observe(self.data, reader, error, getattr(client, 'last_response', None), self.clock(),
                                             own=identity == (self.data['account'] or {}).get('id'), continuation=continuation)
        if paused and self.data['settings']['notifications']:  # one Windows notification per pause
            self.read_alerts.append({'reader': reader, 'reason': self.data['readHealth'][reader]['reason']})
        return status

    def read_profile(self, client, identity, avatar=True):
        if self.operation == 'automatic' and read_health.paused(self.data, 'profile'):
            raise AppFailure('read_paused', 'Profil okuması duraklatıldı. İstek bilgilerini kontrol et.')
        self.progress('profile', personId=identity)
        monitor = self.operation not in ('login', 'import_counter')
        try:
            payload = client.private_request(f'users/{identity}/info/', params={
                'is_prefetch': 'false', 'entry_point': 'self_profile' if identity == str(client.user_id) else 'profile',
                'from_module': 'self_profile' if identity == str(client.user_id) else 'profile', 'is_app_start': False})
            result = parse_profile(payload, identity)
        except Exception as exc:
            if monitor:
                self.diagnose_read('profile', exc, client, identity)
            raise
        if monitor:
            read_health.success(self.data, 'profile')
        if avatar:
            previous = self.data['account'] if (self.data['account'] or {}).get('id') == identity else self.data['people'].get(identity)
            if result.get('picture') is not None and previous and result['picture'] == previous.get('picture') and previous.get('avatar'):
                result['avatar'] = previous['avatar']
            else:
                self.read_avatar(result)
        return result

    def connect(self, args, imported=False):
        client = None
        try:
            self.set_login_stage('session_import' if imported else 'login_setup')
            if imported:
                path = Path(os.environ['LOCALAPPDATA']) / 'InstagramCounter/session.dat'
                if not path.is_file() or path.stat().st_size > 1024 * 1024:
                    raise AppFailure('import', 'Önceki Windows prototipinde aktarılacak oturum bulunamadı.')
                payload = json.loads(self.protect(path.read_bytes(), decrypt=True))
                if (not isinstance(payload, dict) or payload.get('version') != 1
                        or not isinstance(payload.get('settings'), dict)
                        or not isinstance(payload.get('account_id'), str)):
                    raise AppFailure('import', 'Eski oturum dosyası desteklenmiyor.')
                client = self.new_client(payload['settings'])
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
                client = self.new_client(self.login_device_settings(username))
                try:
                    self.persist_login_identity(client)
                    self.set_login_stage('authentication')
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
            self.set_login_stage('account_profile')
            profile = self.read_profile(client, identity)
            self.check_cancel()
            self.set_login_stage('session_save')
            self.select_connection_account(profile)
            first_connection = self.data['account'] is None
            read_health.success(self.data, 'profile')
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
            self.data.pop('lastLoginError', None)
            self.save()
        finally:
            args.pop('password', None)
            self.close_client(client)

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
            self.close_client(client)

    def profile_detail(self, args):
        identity=args.get('id')
        owner=(self.data['account'] or {}).get('id')
        if args.get('accountId') != owner or not isinstance(identity,str) or not (identity==owner or self.known_person(identity)):
            raise AppFailure('input','Profil kaydı bulunamadı.')
        if type(args.get('fresh', False)) is not bool:
            raise AppFailure('input', 'Profil yenileme tercihi geçersiz.')
        cached = None if args.get('fresh') else self.profile_facts.recent(self.data, identity, self.clock(), max_age=PROFILE_DETAILS_SECONDS)
        if cached is not None:
            known = self.data['account'] if identity == owner else self.data['people'].get(identity)
            profile = known if known and (known.get('observedAt') or 0) >= cached['observedAt'] else cached
            self.profile_detail_result = {'accountId':owner, 'profile':public_person(profile)}
            return
        client=self.client()
        try:
            profile=self.read_profile(client,identity,avatar=False)
            self.check_cancel()
            profile.pop('avatarUrl',None)
            self.data['session']=client.get_settings()
            if identity==owner or identity in self.data['people']:
                self.record_profile(profile,own=identity==owner)
                profile=self.data['account'] if identity==owner else self.data['people'][identity]
            else:
                profile['observedAt']=self.clock()
                self.profile_facts.record(self.data, profile)
            self.save()
            self.profile_detail_result={'accountId':owner,'profile':public_person(profile)}
        finally:
            self.close_client(client)

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
        username = profile_search.username(args.get('username', ''))
        identity=args.get('id')
        if identity is not None and (not isinstance(identity,str) or not re.fullmatch(r'[1-9][0-9]{0,29}',identity)):
            raise AppFailure('input','Geçerli bir Instagram kullanıcı adı gir.')
        if len(self.data['people']) >= 200:
            raise AppFailure('capacity', 'En fazla 200 profil kaydedilebilir.')
        client = self.client()
        try:
            self.progress('profile')
            # One explicit mobile lookup. No public fallback or endpoint rotation.
            payload = client.private_request(f'users/{identity}/info/' if identity else f'users/{username}/usernameinfo/')
            profile = parse_profile(payload,identity)
            if identity is None and profile['username'].lower() != username.lower():
                raise AppFailure('identity', 'Aranan kullanıcı adı ile yanıt uyuşmadı.')
            if profile['id'] == self.data['account']['id']:
                raise AppFailure('input', 'Kendi hesabın zaten Özet bölümünde.')
            self.read_avatar(profile)
            self.check_cancel()
            adding = profile['id'] not in self.data['people']
            self.record_profile(profile)
            if adding:
                list_preparation.queue_initial(self.data, profile['id'])
            self.data['session'] = client.get_settings()
            self.save()
        finally:
            self.close_client(client)

    def search_people(self, args):
        query=profile_search.query(args.get('query'))
        owner=(self.data['account'] or {}).get('id')
        if not owner or not self.data['session']:raise AppFailure('session','Önce hesabını bağla.')
        self.search_cache.bind(owner)
        cached=self.search_cache.get(owner,query)
        if cached is not None:
            self.search_results=cached;return
        client=self.client()
        try:
            self.progress('profile')
            payload=client.private_request('users/search/',params={'query':query,'count':20})
            self.check_cancel()
            users=profile_search.results(payload)
            pictures={user['id']:user.pop('pictureUrl',None) for user in users}
            self.data['session']=client.get_settings()
            self.save()
            self.search_results=self.search_cache.put(owner,query,users,pictures)
        finally:
            self.close_client(client)

    def scan(self, args, *, one_page=False, resume=False, reuse_profile=False):
        direction = args.get('direction')
        if direction not in ('followers', 'following'):
            raise AppFailure('input', 'Liste yönü geçersiz.')
        identity = str(args.get('id') or (self.data['account'] or {}).get('id', ''))
        owner = self.data['account'] if self.data['account'] and identity == self.data['account']['id'] else self.data['people'].get(identity)
        if not owner:
            raise AppFailure('input', 'Liste sahibi kayıtlı değil.')
        client = self.client()
        record = self.data['lists'].setdefault(identity + ':' + direction, {'snapshots': []})
        draft, continuing = self.scan_draft(record, identity, direction, resume)
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
                profile = self.profile_facts.recent(self.data, identity, self.clock()) if reuse_profile else None
                if profile is None:
                    profile = self.read_profile(client, identity, avatar=False)
                    self.record_profile(profile, own=identity == self.data['account']['id'])
                owner = profile
                draft['expected'] = profile[direction]
                self.scan_progress.update(username=profile['username'], expected=draft['expected'], step='page')
            cursor = draft.get('nextCursor') or ''
            for _ in range(1 if one_page else MAX_PAGES):
                self.check_cancel()
                if draft['pages'] >= MAX_PAGES:
                    raise AppFailure('capacity', 'Tarama sayfa sınırına ulaştı; kısmi kayıt korundu.')
                self.scan_progress.update(page=draft['pages'] + 1)
                self.progress('list')
                parsed = self.read_list_page(client, draft, identity, direction, cursor)
                members, next_cursor = parsed.members, parsed.cursor
                if len(set(draft['members']) | set(members)) > MAX_MEMBERS:
                    raise AppFailure('capacity', 'Tarama 20.000 hesap sınırına ulaştı; kısmi kayıt korundu.')
                if not draft.get('recoveryPass'):
                    draft['overlapRows'] = draft.get('overlapRows', 0) + len(set(draft['members']) & set(members))
                draft['restartPending'] = False
                draft['members'].update(members)
                draft['pages'] += 1
                draft['finishedAt'] = self.clock()
                schedule['lastAt'] = self.clock()
                self.data['session'] = client.get_settings()
                self.scan_progress.update(completedPages=draft['pages'], count=len(draft['members']))
                if parsed.coverage in ('limited', 'unknown'):
                    raise AppFailure('list_coverage', 'Instagram liste kapsamını kısıtlı veya belirsiz döndürdü. Görülen üyeler kısmi kayıt olarak korundu; fark hesabı yapılmadı.')
                digest = hashlib.sha256(next_cursor.encode()).hexdigest() if next_cursor else None
                if next_cursor and (digest in draft['seenCursors'] or not members):
                    raise AppFailure('pagination', 'Liste sayfalaması ilerlemiyor. Döngü durduruldu; kısmi kayıt korundu.')
                read_health.success(self.data, direction, complete=False)
                draft['nextCursor'] = next_cursor or None
                if digest:
                    draft['seenCursors'].append(digest)
                if not next_cursor:
                    if len(draft['members']) != draft['expected']:
                        # Bounded repair of overlapping server pages, within
                        # this scan only. Never merge a previous partial/baseline.
                        if (draft.get('recoveryPass', 0) < list_preparation.RECOVERY_PASSES and draft.get('overlapRows', 0) > 0 and
                                len(draft['members']) > draft.get('recoveryStartCount', 0) and
                                len(draft['members']) < draft['expected'] and
                                0 <= self.clock() - draft['startedAt'] < list_preparation.RECOVERY_AGE):
                            draft.update(recoveryPass=draft.get('recoveryPass', 0)+1, restartPending=True,
                                         recoveryStartCount=len(draft['members']),seenCursors=[], status='queued')
                            self.save(); self.progress('list_received')
                            if one_page: return
                            cursor = ''
                            continue
                        raise AppFailure('list_count', 'Liste sayısı profil sayısıyla uyuşmadı. Kısmi kayıt korundu; fark hesabı yapılmadı.')
                    if draft.get('recoveryPass'):
                        # Confirm the profile count after the repair; an old
                        # counter must not certify a changed list.
                        profile = self.read_profile(client, identity, avatar=False)
                        self.record_profile(profile, own=identity == self.data['account']['id'])
                        if (profile[direction] != draft['expected'] or
                                not 0 <= self.clock() - draft['startedAt'] < list_preparation.RECOVERY_AGE):
                            raise AppFailure('list_count', 'Liste sayısı profil sayısıyla uyuşmadı. Kısmi kayıt korundu; fark hesabı yapılmadı.')
                        owner = profile
                    self.check_cancel()
                    self.publish_scan(record, draft, schedule, owner, identity, direction)
                    return
                draft['status'] = 'queued' if one_page else 'running'
                self.save()
                self.progress('list_received')
                self.check_cancel()
                cursor = next_cursor
            if not one_page:
                raise AppFailure('capacity', 'Tarama sayfa sınırına ulaştı; kısmi kayıt korundu.')
        except Exception as exc:
            self.scan_failed(exc, draft, schedule, client, identity, direction)
            raise
        finally:
            self.list_scan_active = False
            self.scan_progress = None
            self.close_client(client)

    def scan_draft(self, record, identity, direction, resume):
        """Continue a resumable draft or start a fresh one for this list."""
        draft = record.get('draft')
        continuing = resume and list_preparation.resumable(draft, self.clock())
        if not continuing:
            # A new partial read is independent evidence, never a union with
            # the old page. Preserve the widest partial across fresh attempts;
            # publication can select a newer scan once its coverage catches up.
            prior = list_pages.visible_partial(record)
            if not record['snapshots'] and prior and prior.get('members'):
                record['retainedPartial'] = {k:v for k,v in prior.items() if k not in ('nextCursor','seenCursors')}
            draft = {'id': uuid.uuid4().hex, 'ownerId': identity, 'direction': direction,
                     'startedAt': self.clock(), 'finishedAt': None, 'members': {}, 'complete': False,
                     'status': 'running', 'pages': 0, 'expected': None, 'nextCursor': None, 'seenCursors': [],
                     'recoveryPass': 0, 'restartPending': False, 'overlapRows': 0, 'recoveryStartCount': 0}
            record['draft'] = draft
        return draft, continuing

    def read_list_page(self, client, draft, identity, direction, cursor):
        params = {'count': 50, 'rank_token': client.rank_token,
                  'search_surface': 'follow_list_page', 'query': '', 'enable_groups': 'true'}
        if cursor:
            params['max_id'] = cursor
        try:
            return parse_page(client.private_request(f'friendships/{identity}/{direction}/', params=params))
        except Exception as exc:
            status = self.diagnose_read(direction, exc, client, identity, continuation=bool(cursor))
            if cursor and status in (400, 404):
                draft['nextCursor'] = None
                draft['seenCursors'] = []
            raise

    def publish_scan(self, record, draft, schedule, owner, identity, direction):
        """Store a complete list, compare it with the previous one and announce it."""
        read_health.success(self.data, direction)
        draft.update(complete=True, status='complete')
        record['snapshots'].append(copy.deepcopy({k:v for k,v in draft.items() if k not in ('nextCursor','seenCursors')}))
        returned = set(membership.apply(record, record['snapshots'][-1]))
        del record['snapshots'][:-6]
        record.pop('draft', None)
        record.pop('retainedPartial', None)
        schedule.pop('initial', None)
        schedule.update(after=self.clock() + list_preparation.MIN_RESCAN, error=None)
        diff = compare(record['snapshots'][-2], record['snapshots'][-1]) if len(record['snapshots']) > 1 else None
        changes = {'direction': direction, 'count': len(draft['members']),
                   'added': len(diff['added']) if diff else None, 'removed': len(diff['removed']) if diff else None}
        if diff is not None:
            before = record['snapshots'][-2]
            # Freeze observed identities and names, not current profile
            # lookups. Evidence survives the six-snapshot rotation.
            changes.update(previousAt=before['finishedAt'], ownerUsername=owner['username'],
                           membersAdded=[public_member(draft['members'][key]) for key in diff['added']],
                           membersRemoved=[public_member(before['members'][key]) for key in diff['removed']])
            # A return is a member seen before, absent in between and back now.
            back = [key for key in diff['added'] if key in returned]
            changes.update(returned=len(back), returnedIds=back)
        self.event('list', f"@{owner['username']} · {direction} listesi kaydedildi", identity, None, changes)
        if identity == self.data['account']['id'] and direction == 'followers':
            access_checks.discover(self.data, record, self.clock())
            self.data['accessPlan'] = {'status': 'idle'}
        self.save()
        self.progress('list_received')

    def scan_failed(self, exc, draft, schedule, client, identity, direction):
        """Keep a failed read as a partial draft and schedule the retry."""
        code = failure(exc)['code']
        if code in ('list_coverage', 'list_count', 'pagination', 'capacity'):
            self.diagnose_read(direction, exc, client, identity)
        draft['status'] = 'cancelled' if self.cancel.is_set() else 'partial'
        draft['finishedAt'] = self.clock()
        if code == 'cancelled':
            self.data['settings']['automaticLists'] = False
        delay = (0 if code in ('budget', 'cooldown', 'cancelled') else
                 list_preparation.count_retry_delay(schedule) if code == 'list_count' else 1800)
        schedule.update(error=code, after=self.clock() + delay)
        self.save()

    def known_person(self, identity):
        person = self.data['people'].get(identity)
        if person:
            return person
        for record in self.data['lists'].values():
            for scan in list(reversed(record.get('snapshots', []))) + [s for s in (record.get('draft'),record.get('retainedPartial')) if s]:
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
            probe = access_checks.probe(client, identity)
            self.check_cancel()
            outcome = probe.visibility
            self.data['session'] = client.get_settings()
            if probe.profile is not None:
                if identity in self.data['people']:
                    self.record_profile(probe.profile)
                else:
                    self.profile_facts.record(self.data, {**probe.profile, 'observedAt':self.clock()})
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
            self.close_client(client)

    def save_profile_about(self, args):
        own = self.data.get('account')
        if not own or args.get('accountId') != own['id']:
            raise AppFailure('input', 'Geçerli bir hesap seç.')
        identity = args.get('id')
        if not isinstance(identity, str):
            raise AppFailure('input', 'Hesap bilgilerini kontrol et.')
        profile = own if identity == own['id'] else self.data['people'].get(identity)
        value = args.get('about')
        if not profile or set(args) != {'accountId', 'id', 'about'} or (value != {} and not profile_about.validate(value, date.today().isoformat()[:7])):
            raise AppFailure('input', 'Hesap bilgilerini kontrol et.')
        previous = profile.pop('about', None)
        if value:
            profile['about'] = dict(value)
        try:
            self.save()
        except Exception:
            profile.pop('about', None)
            if previous is not None:
                profile['about'] = previous
            raise

    def save_person(self, args):
        identity = str(args.get('id', ''))
        updates = {}
        for field, limit in (('note', NOTE_CHARACTERS), ('group', GROUP_CHARACTERS)):
            if field in args:
                if not isinstance(args[field], str) or len(args[field]) > limit:
                    raise AppFailure('input', 'Not veya grup uzunluğu geçersiz.')
                updates[field] = args[field]
        for field in ('favorite', 'watching'):
            if field in args:
                if type(args[field]) is not bool:
                    raise AppFailure('input', 'Tercih değeri geçersiz.')
                updates[field] = args[field]
        p = self.data['people'].get(identity)
        if not p:
            # A list member can be locally saved without a profile request.
            known = self.known_person(identity)
            p = dict(known) if known else None
            if not p or len(self.data['people']) >= 200:
                raise AppFailure('input', 'Kişi bulunamadı veya kişi kapasitesi doldu.')
            cached = self.profile_facts.recent(self.data, identity, self.clock(), max_age=PROFILE_DETAILS_SECONDS)
            if cached is not None:
                self.record_profile(cached, observed_at=cached['observedAt'], emit_events=False)
                p = self.data['people'][identity]
            else:
                self.data['people'][identity] = p
            list_preparation.queue_initial(self.data, identity)
        p.update(updates)
        self.save()

    def settings(self, args):
        settings=copy.deepcopy(self.data['settings'])
        for key in data_privacy.FIELDS:
            if key in args and (type(args[key]) is not int or args[key] not in data_privacy.CHOICES):
                raise AppFailure('input','Saklama süresi geçersiz.')
        if 'applyRetention' in args and args['applyRetention'] is not True:
            raise AppFailure('input','Saklama tercihi geçersiz.')
        for key in data_privacy.FIELDS:
            if key in args:settings[key]=args[key]
        if 'theme' in args:
            if type(args['theme']) is not str or args['theme'] not in ('system', 'light', 'dark', 'oled'):
                raise AppFailure('input', 'Ayar değeri geçersiz.')
            settings['theme'] = args['theme']
        if 'language' in args:
            if type(args['language']) is not str or args['language'] not in localization.LANGUAGES:
                raise AppFailure('input', 'Dil değeri geçersiz.')
            settings['language'] = args['language']
        for key in ('compact', 'notifications', 'automatic', 'automaticLists', 'investigateMissing'):
            if key in args:
                if type(args[key]) is not bool:
                    raise AppFailure('input', 'Ayar değeri geçersiz.')
                settings[key] = args[key]
        if 'interval' in args:
            if type(args['interval']) is not int or args['interval'] not in CONTROL_INTERVALS:
                raise AppFailure('input', 'Kontrol aralığı geçersiz.')
            settings['interval'] = args['interval']
        self.data['settings']=settings
        self.save()

        if args.get('applyRetention') or any(key in args for key in data_privacy.FIELDS):
            self.data['privacyMaintenance']={'at':0,'failed':False}
            self.maintain_privacy(force=True)
            self.save()
            if self.data['privacyMaintenance']['failed']:
                raise AppFailure('storage','Bazı arşivler temizlenemedi. Tercihler kaydedildi; temizlemeyi yeniden deneyebilirsin.')

    def notification_rule(self, args):
        identity = str(args.get('id', ''))
        if identity != (self.data['account'] or {}).get('id') and not self.known_person(identity):
            raise AppFailure('input', 'Bildirim tercihi için kayıtlı bir hesap seç.')
        notification_log.set_rule(self.data, identity, args)
        self.save()

    def read_notification(self, args):
        notification_log.mark_read(self.data, args.get('id'), self.clock())
        self.save()

    def read_all_notifications(self, args):
        account_id = (self.data['account'] or {}).get('id')
        if account_id is None or args.get('accountId') != account_id:
            raise AppFailure('input', 'Bildirimler için seçili hesabı kontrol et.')
        unread = [entry for entry in self.data['notificationLog'] if entry['readAt'] is None]
        if unread:
            now = self.clock()
            for entry in unread:
                entry['readAt'] = now
            self.save()

    def automatic_plan(self):
        """One owner for per-profile deadlines and optional investigation cadence."""
        d = self.data
        candidates = [] if read_health.paused(d, 'profile') else (
            ([d['account']] if d['account'] else []) + [p for p in d['people'].values() if p.get('watching')])
        interval = d['settings']['interval'] * 60
        budget_at = request_budget.profile_next_at(d['gate'], self.budget_owner(), self.clock())
        profiles = sorted([(max(max(p.get('observedAt') or 0, d['automaticAttempts'].get(p['id'], 0)) + interval, budget_at), p['id'])
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
        return {'enabled': d['settings']['automatic'], 'held': reason is not None, 'reason': reason, 'listPlanVersion': list_preparation.PLAN_VERSION,
                'budgetPolicy': request_budget.policy(), 'profileCount': len(profiles), 'intervalSeconds': interval,
                'profileDemandDaily': len(profiles) * 86400 / interval,
                'profileBudgetDaily': request_budget.PROFILE_ACCOUNT[1],
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
            self.scan({'id':lists['ownerId'], 'direction':lists['direction']}, one_page=True, resume=True, reuse_profile=True)
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
        BackupService(self).export(path)

    def import_backup(self, path):
        BackupService(self).restore(path)

    def export_list(self, args):
        if args.get('accountId') != (self.data['account'] or {}).get('id'):
            raise AppFailure('input', 'Dosya seçimi sırasında hesap değişti. İlgili hesabı seçip yeniden dene.')
        record = self.data['lists'].get(str(args.get('id', '')) + ':' + str(args.get('direction', '')), {})
        scan = next((s for s in record.get('snapshots', []) if s['id'] == args.get('snapshotId')), None)
        if scan is None:
            raise AppFailure('input', 'Önce tamamlanmış bir liste kaydı oluştur.')
        from datetime import datetime, timezone
        observed = datetime.fromtimestamp(scan['finishedAt'], timezone.utc).isoformat(timespec='milliseconds').replace('+00:00','Z')
        def cell(value):
            value = str(value or '')
            return "'" + value if value.startswith(('=','+','-','@','\t','\r','\n')) else value
        with open(args['path'], 'w', encoding='utf-8-sig', newline='') as stream:
            writer = csv.writer(stream, quoting=csv.QUOTE_ALL)
            writer.writerow(['account_id','username','name','observed_at'])
            writer.writerows([cell(p.get(k)) for k in ('id','username','name')] + [observed] for p in scan['members'].values())

    def prepare_restore(self, restored):
        if (self.data['account'] and
                self.data['account']['id'] != (restored['account'] or {}).get('id')):
            raise AppFailure('backup', 'Yedek başka bir hesaba ait. Önce ilgili hesabı seç.')

    # Operations that read from Instagram; refused in the isolated QA window.
    NETWORK_OPERATIONS = frozenset({'story_fetch', 'login', 'import_counter', 'refresh', 'scan', 'add_person',
                                    'search_people', 'profile_detail', 'automatic', 'check_access'})
    CLEARS_SEARCH_CACHE = frozenset({'login', 'import_counter', 'disconnect', 'delete_all', 'import_backup'})
    CLEARS_PROFILE_FACTS = CLEARS_SEARCH_CACHE | {'remove_person'}
    # Handlers are looked up on the engine at call time, so a subclass or a
    # test that replaces a method on one engine object is still used.
    OPERATIONS = {
        'story_view': lambda self, args: self.story_archive(args),
        'story_fetch': lambda self, args: self.story_archive(args, fetch=True),
        'story_clear': lambda self, args: self.story_archive(args, clear=True),
        'daily_clear': lambda self, args: self.clear_daily(args),
        'wayback': lambda self, args: self.configure_wayback(args),
        'login': lambda self, args: self.connect(args),
        'import_counter': lambda self, args: self.connect(args, True),
        'refresh': lambda self, args: self.refresh(args),
        'profile_detail': lambda self, args: self.profile_detail(args),
        'add_person': lambda self, args: self.add_person(args),
        'scan': lambda self, args: self.scan(args, resume=True),
        'search_people': lambda self, args: self.search_people(args),
        'save_person': lambda self, args: self.save_person(args),
        'save_profile_about': lambda self, args: self.save_profile_about(args),
        'settings': lambda self, args: self.settings(args),
        'automatic': lambda self, args: self.automatic(),
        'check_access': lambda self, args: self.check_access(args),
        'notification_rule': lambda self, args: self.notification_rule(args),
        'read_notification': lambda self, args: self.read_notification(args),
        'read_all_notifications': lambda self, args: self.read_all_notifications(args),
        'export_list': lambda self, args: self.export_list(args),
        'export_backup': lambda self, args: self.export_backup(args['path']),
        'import_backup': lambda self, args: self.import_backup(args['path'])}
    # Local housekeeping keeps the previous error on screen.
    LOCAL_OPERATIONS = {
        'remove_person': lambda self, args: self.erase_person(str(args.get('id', ''))),
        'disconnect': lambda self, args: self.disconnect(),
        'delete_all': lambda self, args: self.delete_all(),
        'list_exception': lambda self, args: self.set_list_exception(args)}

    def disconnect(self):
        self.data['session'] = None
        self.data['settings']['automatic'] = False
        self.save()
        # Session removal also purges the immediately preceding backup.
        self.vault.backup.unlink(missing_ok=True)

    def delete_all(self):
        if self.data['account']:
            self.daily_store().clear_all(revoke=True)
            stories.StoryStore(self.vault.path.parent,self.data['account']['id']).clear_all()
        self.data = blank(dict(self.data['gate']))
        self.save()
        self.vault.backup.unlink(missing_ok=True)

    def set_list_exception(self, args):
        if self.list_pages.set_exception(self.data, args):
            self.save()

    def run(self, operation, args, *, group_id=None, project=True):
        self.begin_operation(operation, group_id)
        try:
            if self.offline and operation in self.NETWORK_OPERATIONS:
                raise AppFailure('offline', 'Bu yalıtılmış test penceresinde Instagram bağlantısı kapalı.')
            if operation == 'state':
                return self.snapshot() if project else None
            if operation in self.LOCAL_OPERATIONS:
                self.LOCAL_OPERATIONS[operation](self, args)
            elif operation in self.OPERATIONS:
                # Changing language is not a new account check; retain the
                # existing error so its current-language projection can update.
                if operation != 'read_all_notifications' and (
                        operation != 'settings' or not set(args).issubset({'language', 'theme'})):
                    self.data['lastError'] = None
                self.OPERATIONS[operation](self, args)
            else:
                raise AppFailure('operation', 'Desteklenmeyen işlem.')
            return self.snapshot() if project else None
        except Exception as exc:
            raise self.operation_failed(exc) from exc
        finally:
            self.end_operation(args)

    def begin_operation(self, operation, group_id):
        self.maintain_privacy()
        if operation in self.CLEARS_SEARCH_CACHE:
            self.search_cache.clear()
        self.operation = operation
        if operation in self.CLEARS_PROFILE_FACTS:
            self.profile_facts.clear()
        self.search_results = None
        self.profile_detail_result = None
        self.story_detail = None
        self.read_alerts = []
        self.observation_group = group_id or uuid.uuid4().hex
        while not self.codes.empty():
            self.codes.get_nowait()

    def operation_failed(self, exc):
        """Record a failed operation and return the error to raise."""
        if isinstance(exc, StorageFailure):
            # Never report volatile edits as successfully persisted records.
            self.data = self.load_vault()
        error = failure(exc)
        if self.login_stage is not None:
            error['loginStage'] = ('verification' if self.login_stage == 'authentication'
                                   and error['code'] == 'verification' else self.login_stage)
            if self.login_request:
                error['loginRequest'] = self.login_request
            if self.login_http is not None:
                error['http'] = self.login_http
                if error['code'] == 'connection':
                    if self.login_http >= 500:
                        error.update(code='server', message='Instagram isteği sunucu hatasıyla sonuçlandı.')
                    else:
                        error.update(code='response', message='Instagram yanıt verdi ancak bu adım tamamlanamadı.')
        if error['code'] == 'limited' and not self.data['gate'].get('manualOnly'):
            self.limited()
        if error['code'] in ('session', 'verification'):
            self.data['gate']['manualOnly'] = True
        self.data['lastError'] = {**error, 'at': self.clock()}
        if self.login_stage is not None:
            self.data['lastLoginError'] = self.data['lastError']
        self.event('error', error['message'])
        try:
            self.save()
        except StorageFailure:
            pass
        return AppFailure(error['code'], error['message'], http=error.get('http'),
                          login_stage=error.get('loginStage'), request_number=error.get('loginRequest'),
                          transport_code=error.get('transportCode'))

    def end_operation(self, args):
        args.pop('password', None)
        self.operation = None
        self.login_stage = None
        self.login_http = None
        self.login_request = 0
        self.observation_group = None
        # Only the explicit archive operation carries thumbnail details.
        # Later automatic checks must not serialize the album repeatedly.
        self.wayback_id = None
