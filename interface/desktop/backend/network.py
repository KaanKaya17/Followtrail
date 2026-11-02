"""Pinned Instagram client with a single guarded transmission boundary."""
import logging
import base64
from io import BytesIO
from dataclasses import dataclass
import re
import time
from email.utils import parsedate_to_datetime
from urllib.parse import urlsplit

from instagrapi import Client
from instagrapi.exceptions import TwoFactorRequired
from instagrapi.transports import create_curl_h2_adapter
import requests
import request_policy
from PIL import Image


class AppFailure(Exception):
    def __init__(self, code, message):
        super().__init__(message)
        self.code = code


def deadline(header, now):
    try:
        if str(header).isdigit():
            return now + min(int(header), 365 * 86400)
        return parsedate_to_datetime(header).timestamp()
    except (ValueError, TypeError, OverflowError):
        return 0


class TrackerClient(Client):
    _tracker_authenticating = False
    def login_flow(self):
        return True

    def challenge_resolve(self, last_json):
        raise AppFailure('verification', 'Instagram ek kontrol istiyor. Resmî Instagram uygulamasında doğrula.')

    def challenge_code_or_raised(self, choice, wait_seconds=5, attempts=24):
        return self.challenge_code_handler(self.username, choice)

    def login(self, *args, **kwargs):
        self._tracker_authenticating = True
        try:
            try:
                return super().login(*args, **kwargs)
            except TwoFactorRequired as exc:
                context = vars(exc)
                if not self._extract_two_step_verification_context(context):
                    context = self.last_json
                if not self._extract_two_step_verification_context(context):
                    raise AppFailure('verification', 'Bu doğrulama biçimini resmî Instagram uygulamasında tamamla.') from exc
                code = self.challenge_code_or_raised(None)
                return self._login_with_bloks_two_factor(code, context, exc)
        finally:
            self._tracker_authenticating = False

    def private_request(self, endpoint, data=None, params=None, login=False,
                        with_signature=True, headers=None, extra_sig=None, domain=None):
        # The upstream helper retries timeouts and incomplete reads. Own the
        # one-transmission path here, including for credential-bearing POSTs.
        headers = dict(headers or {})
        if self.authorization:
            headers.setdefault('Authorization', self.authorization)
        self._send_private_request(endpoint, data=data, params=params, login=login,
                                   with_signature=with_signature, headers=headers,
                                   extra_sig=extra_sig, domain=domain)
        return self.last_json


def new_client(engine, settings=None):
    logger = logging.getLogger('tracker-network')
    logger.handlers = [logging.NullHandler()]
    logger.propagate = False
    client = TrackerClient(logger=logger, session_retry_total=0,
                           public_request_retries_count=0, request_timeout=0)
    if settings:
        # Restored transport settings must not weaken TLS, retry or proxy policy.
        settings = dict(settings)
        settings.update(tls_verify=True, session_retry_total=0,
                        public_request_retries_count=0, request_timeout=0,
                        private_transport='curl', public_transport='requests')
        client.set_settings(settings)
    client.set_tls_verify(True)
    client.set_retry_config(session_retry_total=0, public_request_retries_count=0,
                            request_timeout=0, private_transport='curl', public_transport='requests')
    client.read_timeout = 25
    client.challenge_code_handler = engine.request_code
    for session in (client.private, client.public, client.graphql):
        session.trust_env = False
        session.proxies.clear()
        original = session.send

        def send(request, _send=original, **kwargs):
            if not request_policy.allowed(request, client._tracker_authenticating):
                raise AppFailure('network_policy', 'İzin verilmeyen ağ hedefi durduruldu.')
            if client._tracker_authenticating:
                engine.persist_login_identity(client)
            engine.before_request()
            kwargs.update(timeout=25, allow_redirects=False, verify=True, proxies={})
            response = _send(request, **kwargs)
            engine.after_response(response)
            if 300 <= response.status_code < 400:
                raise AppFailure('redirect', 'Instagram yönlendirme istedi; otomatik izlenmedi.')
            if len(response.content) > 4 * 1024 * 1024:
                raise AppFailure('response_size', 'Instagram yanıtı beklenen boyutu aşıyor.')
            return response
        session.send = send
    return client


def close_client(client):
    if client:
        client.password = None
        for name in ('private', 'public', 'graphql'):
            session = getattr(client, name, None)
            if session:
                session.close()


def parse_profile(payload, expected_id=None):
    user = payload.get('user') if isinstance(payload, dict) else None
    if not isinstance(user, dict):
        raise AppFailure('profile', 'Profil yanıtı eksik; eski gözlem korundu.')
    identity = user.get('pk')
    if type(identity) not in (int, str) or not re.fullmatch(r'[1-9][0-9]{0,29}', str(identity)):
        raise AppFailure('identity', 'Profil kimliği doğrulanamadı.')
    identity = str(identity)
    if expected_id and identity != str(expected_id):
        raise AppFailure('identity', 'Beklenen hesap ile yanıt kimliği farklı; kayıt değiştirilmedi.')
    username = user.get('username')
    if not isinstance(username, str) or not re.fullmatch(r'[A-Za-z0-9._]{1,30}', username):
        raise AppFailure('profile', 'Profil kullanıcı adı geçersiz.')
    profile = {'id': identity, 'username': username,
               'name': user.get('full_name') if isinstance(user.get('full_name'), str) else username}
    for source, target in (('follower_count', 'followers'), ('following_count', 'following'), ('media_count', 'posts')):
        value = user.get(source)
        if type(value) is not int or value < 0:
            if target == 'posts' and value is None:
                profile[target] = None
                continue
            raise AppFailure('profile', 'Instagram sayaçları eksik döndürdü; eksik değer sıfır sayılmadı.')
        profile[target] = value
    # Missing optional values remain unknown, rather than creating false changes.
    for source, target in (('biography', 'bio'), ('external_url', 'url'),
                           ('is_private', 'private'), ('is_verified', 'verified')):
        value = user.get(source)
        profile[target] = value if type(value) is (bool if target in ('private', 'verified') else str) else None
    picture_id = user.get('profile_pic_id')
    profile['picture'] = ('anonymous' if user.get('has_anonymous_profile_picture') is True else
                          'id:' + str(picture_id) if type(picture_id) in (str, int) and 0 < len(str(picture_id)) <= 128 else None)
    profile['avatarUrl'] = user.get('profile_pic_url') if isinstance(user.get('profile_pic_url'), str) else None
    return profile


def avatar_url(value):
    if not isinstance(value, str) or len(value) > 8192:
        return False
    try:
        url = urlsplit(value or '')
        return bool(url.scheme == 'https' and url.port in (None, 443) and not url.username and not url.password
                    and url.hostname and any(url.hostname == domain or url.hostname.endswith('.' + domain)
                                             for domain in ('cdninstagram.com', 'fbcdn.net')))
    except ValueError:
        return False


def read_avatar(profile, engine):
    """Public Meta CDN only, independent cookie-free transport and bounded decode."""
    profile['avatar'] = None
    if not avatar_url(profile.get('avatarUrl')):
        return
    try:
        engine.progress('avatar', status='loading')
        engine.check_cancel()
        with requests.Session() as session:
            session.trust_env = False
            # Reuse the packaged HTTP/2 implementation with a separate, empty
            # cookie jar. Never fall back to the authenticated account session.
            session.mount('https://', create_curl_h2_adapter())
            with session.get(profile['avatarUrl'], timeout=(5, 8), allow_redirects=False,
                             stream=True, headers={'Accept': 'image/*'}, verify=True) as response:
                if response.status_code != 200:
                    engine.progress('avatar', status='unavailable', http=response.status_code)
                    return
                raw = bytearray()
                for chunk in response.iter_content(16384):
                    engine.check_cancel()
                    raw.extend(chunk)
                    if len(raw) > 1024 * 1024:
                        return
                with Image.open(BytesIO(raw)) as image:
                    if image.width * image.height > 4_000_000 or image.width > 4096 or image.height > 4096:
                        return
                    image.thumbnail((160, 160))
                    output = BytesIO()
                    image.convert('RGB').save(output, format='JPEG', quality=82)
                    if output.tell() <= 64 * 1024:
                        profile['avatar'] = 'data:image/jpeg;base64,' + base64.b64encode(output.getvalue()).decode('ascii')
    except AppFailure:
        raise
    except Exception as exc:
        engine.progress('avatar', status='unavailable', errorClass=type(exc).__name__)
        # A CDN or image failure cannot turn known profile counts into a failure.
        return


@dataclass(frozen=True)
class FollowPage:
    members: dict
    cursor: str
    coverage: str


def parse_page(payload):
    if not isinstance(payload, dict) or payload.get('status') != 'ok' or not isinstance(payload.get('users'), list):
        raise AppFailure('list', 'Liste yanıtı eksik; tarama tamamlanmış sayılmadı.')
    if len(payload['users']) > 1000:
        raise AppFailure('list', 'Bir liste sayfası beklenen kapsamı aşıyor.')
    members = {}
    for user in payload['users']:
        identity = user.get('pk') if isinstance(user, dict) else None
        username = user.get('username') if isinstance(user, dict) else None
        if (type(identity) not in (str, int) or not re.fullmatch(r'[1-9][0-9]{0,29}', str(identity))
                or not isinstance(username, str) or not re.fullmatch(r'[A-Za-z0-9._]{1,30}', username)):
            raise AppFailure('list', 'Liste üyesinin kimliği geçersiz; sayfa kaydedilmedi.')
        identity = str(identity)
        members[identity] = {'id': identity, 'username': username,
                             'name': user.get('full_name') if isinstance(user.get('full_name'), str) else username}
        picture = user.get('profile_pic_url')
        if avatar_url(picture):
            members[identity]['pictureUrl'] = picture
    cursor = payload.get('next_max_id')
    if cursor is not None and (type(cursor) not in (str, int) or len(str(cursor)) > 2048):
        raise AppFailure('list', 'Liste sayfalama bilgisi geçersiz.')
    cursor = str(cursor) if cursor not in (None, '', 0) else ''
    for field in ('has_more', 'should_limit_list_of_followers'):
        if payload.get(field) is not None and type(payload[field]) is not bool:
            raise AppFailure('list', 'Liste kapsamı alanı geçersiz.')
    if payload.get('should_limit_list_of_followers') is True:
        coverage = 'limited'
    elif ((cursor and payload.get('has_more') is False)
            or (not cursor and payload.get('has_more') is True)
            or (not cursor and 'next_max_id' not in payload and payload.get('has_more') is not False)):
        coverage = 'unknown'
    else:
        coverage = 'more' if cursor else 'end'
    return FollowPage(members, cursor, coverage)


def failure(exc):
    if isinstance(exc, AppFailure):
        return {'code': exc.code, 'message': str(exc)}
    name = type(exc).__name__
    messages = {
        'StorageFailure': ('storage', 'Şifreli kayıt okunamadı veya yazılamadı. Dosyalar korundu.'),
        'BadPassword': ('login', 'Instagram girişi reddetti. Şifre ve giriş bağlamını resmî uygulamada kontrol et.'),
        'TwoFactorRequired': ('verification', 'Instagram doğrulama istiyor.'),
        'UserNotFound': ('not_found', 'Bu kullanıcı bulunamadı.'),
        'PrivateAccount': ('private', 'Bu hesaba veya listeye erişim iznin yok.'),
        'LoginRequired': ('session', 'Instagram oturumu reddetti. Hesabı yeniden bağla.'),
        'ClientLoginRequired': ('session', 'Instagram oturumu reddetti. Hesabı yeniden bağla.'),
        'ClientUnauthorizedError': ('session', 'Oturum kabul edilmedi. Hesabı yeniden bağla.'),
    }
    if name in {'PleaseWaitFewMinutes', 'RateLimitError', 'ClientThrottledError', 'FeedbackRequired', 'SentryBlock'}:
        code, message = 'limited', 'Instagram isteği sınırladı. Otomatik tekrar durduruldu; mola bilgisini kontrol et.'
    elif 'Challenge' in name or name in {'CheckpointRequired', 'ConsentRequired'}:
        code, message = 'verification', 'Instagram ek kontrol istiyor. Resmî uygulamada tamamla.'
    else:
        code, message = messages.get(name, ('connection', 'İşlem tamamlanamadı. Bağlantıyı kontrol et; otomatik tekrar yapılmadı.'))
    return {'code': code, 'message': message}
