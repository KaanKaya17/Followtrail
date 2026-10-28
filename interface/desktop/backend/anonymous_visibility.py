"""One cookie-free public-page observation, never a claim about a block's cause."""
from html.parser import HTMLParser
import json
import math
import re
import time
from urllib.parse import urlsplit

import requests

from network import AppFailure
from storage import StorageFailure

MAX_BYTES = 2 * 1024 * 1024
MAX_NODES = 50000
USERNAME = re.compile(r'[A-Za-z0-9._]{1,30}')
IDENTITY = re.compile(r'[1-9][0-9]{0,29}')
STATUSES = {'visible', 'not_found', 'unknown'}
REASONS = {'identity_match', 'identity_mismatch', 'identity_unverified', 'http_not_found',
           'login_required', 'verification', 'limited', 'cooldown', 'connection',
           'server_error', 'access_denied', 'redirect', 'body_limit', 'content_type', 'unrecognized'}
METHOD = 'public_profile_page'


class ProfilePage(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.documents = []
        self.script = None
        self.meta = {}
        self.login = False
        self.challenge = False

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        if tag == 'script' and attrs.get('type') in ('application/json', 'application/ld+json'):
            self.script = []
        elif tag == 'meta':
            key = attrs.get('property') or attrs.get('name')
            if key in ('og:url', 'profile:username', 'profile:id'):
                self.meta[key] = attrs.get('content', '')
        elif tag == 'form':
            path = urlsplit(attrs.get('action', '')).path
            self.login |= path.startswith('/accounts/login')
            self.challenge |= path.startswith(('/challenge/', '/checkpoint/'))

    def handle_data(self, value):
        if self.script is not None:
            self.script.append(value)

    def handle_endtag(self, tag):
        if tag == 'script' and self.script is not None:
            self.documents.append(''.join(self.script))
            self.script = None


def profile_url_name(value):
    try:
        url = urlsplit(value)
        name = url.path.strip('/')
        if url.scheme == 'https' and url.hostname in ('instagram.com', 'www.instagram.com') and USERNAME.fullmatch(name):
            return name
    except ValueError:
        pass
    return None


def parse_page(body, expected_id, requested_name):
    """Read profile-owned JSON and metadata, not arbitrary suggested-user rows."""
    page = ProfilePage()
    try:
        page.feed(body.decode('utf-8', errors='replace'))
    except (ValueError, RecursionError):
        return {'status': 'unknown', 'reason': 'unrecognized'}
    if page.challenge:
        return {'status': 'unknown', 'reason': 'verification'}
    candidates = set()

    def candidate(user):
        if not isinstance(user, dict):
            return
        name, identity = user.get('username'), user.get('pk') or user.get('id')
        if isinstance(name, str) and USERNAME.fullmatch(name) and type(identity) in (int, str) and IDENTITY.fullmatch(str(identity)):
            canonical = profile_url_name(page.meta.get('og:url', ''))
            if name.casefold() == requested_name.casefold() or (str(identity) == expected_id and canonical and canonical.casefold() == name.casefold()):
                candidates.add((str(identity), name))

    canonical = profile_url_name(page.meta.get('og:url', ''))
    if canonical and canonical.casefold() == requested_name.casefold():
        candidate({'username': canonical, 'id': page.meta.get('profile:id')})
    examined = 0
    for source in page.documents:
        try:
            stack = [json.loads(source)]
        except (ValueError, RecursionError):
            continue
        while stack:
            examined += 1
            if examined > MAX_NODES:
                return {'status': 'unknown', 'reason': 'body_limit'}
            value = stack.pop()
            if isinstance(value, dict):
                # Current web/Relay profile envelopes. A user appearing only in
                # a search or recommendation list is not a profile observation.
                for key in ('data', 'graphql', 'xdt_api__v1__users__web_profile_info'):
                    container = value.get(key)
                    if isinstance(container, dict):
                        candidate(container.get('user'))
                if value.get('@type') == 'ProfilePage':
                    person = value.get('mainEntity')
                    if isinstance(person, dict):
                        name = person.get('alternateName')
                        name = name.lstrip('@') if isinstance(name, str) else profile_url_name(person.get('url', ''))
                        identity = person.get('identifier')
                        if isinstance(identity, dict):
                            identity = identity.get('value')
                        candidate({'username': name, 'id': identity})
                stack.extend(child for child in value.values() if isinstance(child, (dict, list)))
            elif isinstance(value, list):
                stack.extend(child for child in value if isinstance(child, (dict, list)))
    if len(candidates) == 1:
        identity, name = next(iter(candidates))
        matched = identity == expected_id
        return {'status': 'visible' if matched else 'unknown',
                'reason': 'identity_match' if matched else 'identity_mismatch',
                'observedId': identity, 'observedUsername': name, 'identityMatched': matched}
    if candidates:
        return {'status': 'unknown', 'reason': 'identity_unverified'}
    return {'status': 'unknown', 'reason': 'login_required' if page.login else 'identity_unverified'}


def probe(engine, identity, username):
    if not isinstance(username, str) or not USERNAME.fullmatch(username) or not IDENTITY.fullmatch(identity):
        raise AppFailure('input', 'Geçerli bir Instagram kullanıcı adı gir.')
    evidence = {'method': METHOD, 'requestedUsername': username, 'checkedAt': engine.clock(),
                'status': 'unknown', 'reason': 'unrecognized', 'http': None, 'identityMatched': None}
    try:
        # The account's existing pacing, cancellation and cooldown apply to this
        # extra observation too. This is never an alternate route around a 429.
        engine.before_request()
        evidence['checkedAt'] = engine.clock()
        with requests.Session() as session:
            session.trust_env = False
            session.cookies.clear()
            session.mount('https://', requests.adapters.HTTPAdapter(max_retries=0))
            request = session.prepare_request(requests.Request('GET', 'https://www.instagram.com/' + username + '/', headers={
                'Accept': 'text/html', 'Cache-Control': 'no-cache',
                'User-Agent': 'InstagramTracker/0.2.8 (anonymous profile visibility check)'}))
            if any(key.lower() in ('cookie', 'authorization', 'proxy-authorization') for key in request.headers):
                raise AppFailure('network_policy', 'İzin verilmeyen ağ hedefi durduruldu.')
            started = time.monotonic()
            with session.send(request, timeout=(5, 10), allow_redirects=False, stream=True, verify=True, proxies={}) as response:
                evidence['http'] = response.status_code
                engine.after_response(response)
                engine.check_cancel()
                status = response.status_code
                if status in (404, 410):
                    return {**evidence, 'status': 'not_found', 'reason': 'http_not_found'}
                if 300 <= status < 400:
                    try:
                        path = urlsplit(response.headers.get('Location', '')).path
                    except ValueError:
                        path = ''
                    reason = 'login_required' if path.startswith('/accounts/login') else 'verification' if path.startswith(('/challenge/', '/checkpoint/')) else 'redirect'
                    return {**evidence, 'reason': reason}
                if status != 200:
                    return {**evidence, 'reason': 'access_denied' if status in (401, 403) else 'server_error' if status >= 500 else 'unrecognized'}
                if response.headers.get('Content-Type', '').split(';')[0].strip().lower() != 'text/html':
                    return {**evidence, 'reason': 'content_type'}
                raw = bytearray()
                for chunk in response.iter_content(16384):
                    engine.check_cancel()
                    if len(raw) + len(chunk) > MAX_BYTES or time.monotonic() - started > 25:
                        return {**evidence, 'reason': 'body_limit'}
                    raw.extend(chunk)
                engine.check_cancel()
                return {**evidence, **parse_page(raw, identity, username)}
    except AppFailure as error:
        if error.code != 'limited':
            raise
        return {**evidence, 'reason': 'limited' if evidence['http'] == 429 else 'cooldown'}
    except StorageFailure:
        raise
    except requests.RequestException:
        return {**evidence, 'reason': 'connection'}


def validate(evidence, identity=None):
    if not isinstance(evidence, dict) or evidence.get('method') != METHOD or evidence.get('status') not in STATUSES or evidence.get('reason') not in REASONS:
        raise ValueError('anonymous visibility')
    if not isinstance(evidence.get('requestedUsername'), str) or not USERNAME.fullmatch(evidence['requestedUsername']):
        raise ValueError('anonymous username')
    at = evidence.get('checkedAt')
    if type(at) not in (int, float) or not math.isfinite(at) or at < 0:
        raise ValueError('anonymous time')
    http = evidence.get('http')
    if http is not None and (type(http) is not int or not 100 <= http <= 599):
        raise ValueError('anonymous HTTP')
    matched = evidence.get('identityMatched')
    if matched is not None and type(matched) is not bool:
        raise ValueError('anonymous identity match')
    for key, pattern in (('observedId', IDENTITY), ('observedUsername', USERNAME)):
        if key in evidence and (not isinstance(evidence[key], str) or not pattern.fullmatch(evidence[key])):
            raise ValueError('anonymous observed identity')
    if evidence['status'] == 'visible' and (matched is not True or evidence.get('observedId') != identity or http != 200 or evidence['reason'] != 'identity_match'):
        raise ValueError('anonymous visible evidence')
    if evidence['status'] == 'not_found' and (http not in (404, 410) or evidence['reason'] != 'http_not_found'):
        raise ValueError('anonymous absence evidence')
