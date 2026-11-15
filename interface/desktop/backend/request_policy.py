"""Fail-closed method/path contracts. Authentication is a scoped exception."""
import json
import re
from urllib.parse import parse_qs, urlsplit

READ_PATH = re.compile(r'/api/v1/(?:users/(?:[1-9][0-9]{0,29}/info|[A-Za-z0-9._]{1,30}/usernameinfo)|friendships/[1-9][0-9]{0,29}/(?:followers|following)|feed/user/[1-9][0-9]{0,29}/story)/')
PASSWORD_KEY_PATH = '/api/v1/launcher/sync/'
DEVICE_UUID = re.compile(r'[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}')
AUTH_ACTIONS = frozenset((
    'com.bloks.www.bloks.caa.login.process_client_data_and_redirect',
    'com.bloks.www.caa.login.oauth.token.fetch.async',
    'com.bloks.www.bloks.caa.login.async.send_login_request',
    'com.bloks.www.two_step_verification.method_picker.navigation.async',
    'com.bloks.www.two_step_verification.verify_code.async',
    'com.bloks.www.ap.two_step_verification.entrypoint_async',
    'com.bloks.www.ap.two_step_verification.code_entry_async'))
AUTH_APPS = frozenset((
    'com.bloks.www.two_step_verification.entrypoint',
    'com.bloks.www.two_step_verification.method_picker',
    'com.bloks.www.two_factor_login.enter_totp_code',
    'com.bloks.www.two_factor_login.enter_backup_code',
    'com.bloks.www.ap.two_step_verification.code_entry'))
REGISTRATION_ID = '124930351917786857261002920888'


def password_key_body(body):
    if not isinstance(body, (str, bytes)) or len(body) > 256:
        return False
    fields = parse_qs(body.decode('utf-8') if isinstance(body, bytes) else body, strict_parsing=True)
    signed = fields.get('signed_body', [])
    if set(fields) != {'signed_body'} or len(signed) != 1 or not signed[0].startswith('SIGNATURE.'):
        return False
    value = json.loads(signed[0].removeprefix('SIGNATURE.'))
    return (isinstance(value, dict) and set(value) == {'id', 'server_config_retrieval'} and
            isinstance(value['id'], str) and DEVICE_UUID.fullmatch(value['id']) is not None and
            value['server_config_retrieval'] == '1')


def allowed(request, authenticating=False):
    try:
        u = urlsplit(request.url)
        if (u.scheme != 'https' or u.port not in (None, 443) or u.username or u.password or
                u.fragment or '%' in u.path or '..' in u.path or '\\' in u.path):
            return False
        if request.method == 'GET' and not request.body and u.hostname == 'i.instagram.com' and (READ_PATH.fullmatch(u.path) or u.path == '/api/v1/users/search/'):
            return True
        if not authenticating:
            return False
        if request.method == 'GET' and not request.body and u.hostname == 'i.instagram.com':
            return u.path == '/api/v1/accounts/current_user/'
        if request.method != 'POST' or u.hostname not in ('i.instagram.com', 'b.i.instagram.com') or u.query:
            return False
        if u.path == PASSWORD_KEY_PATH:
            return u.hostname == 'i.instagram.com' and password_key_body(request.body)
        if u.path == '/graphql_www' and u.hostname == 'b.i.instagram.com':
            # This registration mutation creates installation context, never a
            # social action. Arbitrary GraphQL documents/queries are forbidden.
            body = request.body.decode('utf-8') if isinstance(request.body, bytes) else request.body
            fields = parse_qs(body or '', strict_parsing=True)
            permitted = {'method','pretty','format','server_timestamps','locale','fb_api_req_friendly_name',
                         'enable_canonical_naming','enable_canonical_variable_overrides',
                         'enable_canonical_naming_ambiguous_type_prefixing','variables','purpose','client_doc_id'}
            return (set(fields) <= permitted and all(len(v) == 1 for v in fields.values()) and
                    fields.get('client_doc_id') == [REGISTRATION_ID] and
                    fields.get('fb_api_req_friendly_name') == ['IGUSDIDRegistrationMutation'])
        if u.path == '/api/v1/attestation/create_android_keystore/':
            return True
        if u.path in ('/api/v1/accounts/login/', '/api/v1/accounts/two_factor_login/'):
            return u.hostname == 'i.instagram.com'
        return any(u.path == '/api/v1/bloks/async_action/' + action + '/' for action in AUTH_ACTIONS) or any(
            u.path == '/api/v1/bloks/apps/' + app + '/' for app in AUTH_APPS)
    except (ValueError, TypeError, UnicodeError):
        return False
