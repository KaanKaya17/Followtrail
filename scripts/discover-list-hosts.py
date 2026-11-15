"""Identify observed Instagram profile, list, and password-key requests in local HAR captures."""
import argparse
import base64
import binascii
from dataclasses import dataclass
import json
from pathlib import Path
import re
from urllib.parse import parse_qsl, urlsplit


MAX_HAR_BYTES = 32 * 1024 * 1024
MAX_ENTRIES = 4000
MAX_BODY_CHARS = 2 * 1024 * 1024
PAGE_KEYS = ('after', 'cursor', 'max_id')
TARGET_KEYS = ('target_user_id', 'user_id', 'id')
PATH_WORDS = {'api', 'graphql', 'query', 'ig', 'web', 'users', 'friendships',
              'followers', 'following', 'follow_list', 'list', 'page', 'connection',
              'launcher', 'qe', 'sync', 'info', 'usernameinfo', 'web_profile_info'}
HOST = re.compile(r'(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)*instagram\.com')
KEY_ROUTES = {'/api/v1/qe/sync/', '/api/v1/launcher/sync/'}
LIST_MODES = ('followers', 'following')
MODES = (*LIST_MODES, 'profile', 'password-key')
REQUEST_FIELDS = ('host', 'path_template', 'method', 'query_id')
HANDLE = re.compile(r'[A-Za-z0-9._]{1,30}')
PROFILE_PATH = re.compile(r'/api/v[0-9]{1,2}/users/([A-Za-z0-9._]{1,30})/(info|usernameinfo)/?')


def object_or_empty(value):
    return value if isinstance(value, dict) else {}


def json_object(value):
    if not isinstance(value, str) or len(value) > MAX_BODY_CHARS:
        return {}
    try:
        return object_or_empty(json.loads(value))
    except (ValueError, RecursionError):
        return {}


def har_response_body(content):
    value = content.get('text')
    encoding = content.get('encoding')
    if encoding in (None, ''):
        return json_object(value)
    if (encoding != 'base64' or not isinstance(value, str)
            or len(value) > 4 * ((MAX_BODY_CHARS + 2) // 3)):
        return {}
    try:
        decoded = base64.b64decode(value, validate=True)
        if len(decoded) <= MAX_BODY_CHARS:
            return json_object(decoded.decode('utf-8'))
    except (binascii.Error, ValueError):
        pass
    return {}


def har_entries(path):
    with path.open('rb') as stream:
        raw = stream.read(MAX_HAR_BYTES + 1)
    if len(raw) > MAX_HAR_BYTES:
        raise ValueError('HAR exceeds the 32 MiB input limit.')
    try:
        data = object_or_empty(json.loads(raw))
    except (ValueError, RecursionError):
        raise ValueError('HAR is not valid JSON.') from None
    entries = object_or_empty(data.get('log')).get('entries')
    if not isinstance(entries, list) or len(entries) > MAX_ENTRIES:
        raise ValueError('HAR needs a log.entries array with at most 4000 entries.')
    return entries


def read_pairs(value):
    if not isinstance(value, str) or len(value) > MAX_BODY_CHARS:
        return {}
    try:
        return dict(parse_qsl(value, max_num_fields=128))
    except ValueError:
        return {}


def har_pairs(pairs):
    if not isinstance(pairs, list) or len(pairs) > 128:
        return {}
    return {item['name']: item['value'] for item in pairs if isinstance(item, dict)
            and isinstance(item.get('name'), str) and isinstance(item.get('value'), str)}


def request_params(request, parsed):
    params = read_pairs(parsed.query) if parsed.query else har_pairs(request.get('queryString'))
    if request.get('method') != 'POST':
        return params
    post = object_or_empty(request.get('postData'))
    mime = str(post.get('mimeType', '')).partition(';')[0].strip().lower()
    if mime == 'application/x-www-form-urlencoded':
        params.update(read_pairs(post.get('text')) if isinstance(post.get('text'), str) and post['text']
                      else har_pairs(post.get('params')))
    elif mime == 'application/json':
        params.update(json_object(post.get('text')))
    return params


def request_variables(params):
    value = params.get('variables')
    return value if isinstance(value, dict) else json_object(value)


def with_query_id(request, path, params):
    doc = params.get('doc_id')
    if type(doc) is int:
        doc = str(doc)
    if 'graphql' in path.lower().split('/') and isinstance(doc, str) and re.fullmatch(r'[0-9]{6,30}', doc):
        return {**request, 'query_id': doc}
    return request


def path_template(path):
    if (match := PROFILE_PATH.fullmatch(path)) and match[2] == 'usernameinfo':
        return path[:match.start(1)] + '{username}' + path[match.end(1):]

    def segment(part):
        if part in PATH_WORDS or re.fullmatch(r'v[0-9]{1,2}', part):
            return part
        return '{id}' if part.isascii() and part.isdigit() else '{segment}'
    return '/' + '/'.join(segment(part) if part else '' for part in path[1:].split('/'))


def is_member(value):
    item = object_or_empty(value)
    identity = next((item[key] for key in ('pk', 'id') if key in item), None)
    return (type(identity) in (str, int) and bool(re.fullmatch(r'[0-9]{1,30}', str(identity)))
            and isinstance(item.get('username'), str) and bool(item['username']))


def cursor(value):
    if type(value) is int and 0 < value < 10 ** 40:
        value = str(value)
    return value if isinstance(value, str) and 0 < len(value) <= 4096 else None


def users_page(body):
    users = body.get('users')
    if not isinstance(users, list) or not all(is_member(user) for user in users):
        return None
    next_cursor = cursor(body.get('next_max_id'))
    more = body.get('has_more')
    if body.get('should_limit_list_of_followers') is True:
        coverage = 'limited'
    elif next_cursor and (more is True or more is None):
        coverage = 'more'
    elif not next_cursor and (more is False or (more is None and 'next_max_id' in body
                                               and body['next_max_id'] in (None, '', 0))):
        coverage = 'end-marker'
    else:
        coverage = 'unknown'
    if users or coverage == 'end-marker':
        return len(users), coverage, next_cursor
    return None


def response_pages(body, direction):
    if body.get('errors') or body.get('status') not in (None, 'ok'):
        return
    if body.get('status') == 'ok' and (page := users_page(body)):
        yield 'users', *page

    # GraphQL aliases vary. Walk only bounded object branches beneath data,
    # and require a named list direction plus a member and page_info shape.
    pending = [(object_or_empty(body.get('data')), False, 0)]
    for _ in range(256):
        if not pending:
            break
        item, matches_direction, depth = pending.pop()
        edges, info = item.get('edges'), item.get('page_info')
        if matches_direction and (page := users_page(item)):
            yield 'data/users', *page
        if (matches_direction and isinstance(edges, list) and isinstance(info, dict)
                and type(info.get('has_next_page')) is bool
                and (edges or not info['has_next_page'])
                and all(is_member(object_or_empty(edge).get('node')) for edge in edges)):
            next_cursor = cursor(info.get('end_cursor')) if info['has_next_page'] else None
            coverage = ('more' if next_cursor else 'unknown') if info['has_next_page'] else 'end-marker'
            yield 'edges/page_info', len(edges), coverage, next_cursor
        if depth < 7:
            for key, value in item.items():
                if not isinstance(value, dict):
                    continue
                key_direction = list_direction(key)
                if key_direction is None or key_direction == direction:
                    pending.append((value, matches_direction or key_direction == direction, depth + 1))


def list_direction(key):
    key = key.lower()
    if key == 'edge_followed_by':
        return 'followers'
    if key == 'edge_follow':
        return 'following'
    directions = {'followers', 'following'} & set(re.split(r'[^a-z]+', key))
    if len(directions) > 1:
        return ''
    return next(iter(directions), None)


@dataclass
class Observation:
    request: dict
    shape: str
    count: int
    coverage: str
    sent: set
    returned: str | None
    target: str | None

    def report(self):
        return {**self.request, 'response_shape': self.shape,
                'member_rows': self.count, 'coverage_signal': self.coverage}


def requests_in(entries):
    for index, value in enumerate(entries, 1):
        entry = object_or_empty(value)
        req = object_or_empty(entry.get('request'))
        res = object_or_empty(entry.get('response'))
        url, method = req.get('url'), req.get('method')
        if method not in ('GET', 'POST') or not isinstance(url, str) or len(url) > 8192:
            continue
        try:
            parsed = urlsplit(url)
            host = parsed.hostname
            if (parsed.scheme != 'https' or parsed.port not in (None, 443) or parsed.username or parsed.password
                    or parsed.fragment or '\\' in parsed.path
                    or not host or len(host) > 253 or not HOST.fullmatch(host) or not parsed.path.startswith('/')):
                continue
        except ValueError:
            continue
        descriptor = {'entry': index, 'host': host, 'path_template': path_template(parsed.path), 'method': method}
        yield descriptor, parsed, req, res


def observations(entries, direction, allow_capture_direction):
    for descriptor, parsed, req, res in requests_in(entries):
        if res.get('status') != 200:
            continue
        parts = set(parsed.path.lower().strip('/').split('/'))
        opposite = 'following' if direction == 'followers' else 'followers'
        if opposite in parts:
            continue
        params = request_params(req, parsed)
        variables = request_variables(params)
        sent = {token for key in PAGE_KEYS for source in (params, variables) if (token := cursor(source.get(key)))}
        target_path = re.search(r'/(?:friendships|users)/([0-9]{1,30})/(?:followers|following)/?$', parsed.path)
        target = target_path.group(1) if target_path else next(
            (token for source in (variables, params) for key in TARGET_KEYS
             if (token := cursor(source.get(key))) and re.fullmatch(r'[0-9]{1,30}', token)), None)
        descriptor = with_query_id(descriptor, parsed.path, params)
        content = object_or_empty(res.get('content'))
        for shape, count, coverage, returned in response_pages(har_response_body(content), direction):
            direction_evidence = 'response' if shape != 'users' else 'path' if direction in parts else 'capture'
            if direction_evidence == 'capture' and not allow_capture_direction:
                continue
            yield Observation({**descriptor, 'direction_evidence': direction_evidence},
                              shape, count, coverage, sent, returned, target)


def password_key_headers(response):
    headers = response.get('headers')
    if not isinstance(headers, list) or len(headers) > 256:
        return False
    fields = {'ig-set-password-encryption-key-id': set(), 'ig-set-password-encryption-pub-key': set()}
    for item in headers:
        header = object_or_empty(item)
        name, value = header.get('name'), header.get('value')
        if isinstance(name, str) and name.lower() in fields and isinstance(value, str) and len(value) <= 4096:
            fields[name.lower()].add(value.strip())
    ids, keys = fields.values()
    if len(ids) != 1 or len(keys) != 1:
        return False
    key_id, key = next(iter(ids)), next(iter(keys))
    if not re.fullmatch(r'[0-9]{1,3}', key_id) or int(key_id) > 255:
        return False
    try:
        return bool(base64.b64decode(key, validate=True))
    except (ValueError, binascii.Error):
        return False


def profile_evidence(path, params, response):
    if response.get('status') != 200:
        return None
    variables = request_variables(params)
    ids, usernames = set(), set()
    for source in (variables, params):
        for key in TARGET_KEYS:
            value = cursor(source.get(key))
            if value and re.fullmatch(r'[0-9]{1,30}', value):
                ids.add(value)
        name = source.get('username')
        if isinstance(name, str) and HANDLE.fullmatch(name):
            usernames.add(name.lower())
    if match := PROFILE_PATH.fullmatch(path):
        if match[2] == 'usernameinfo':
            usernames.add(match[1].lower())
        elif match[1].isdigit():
            ids.add(match[1])
    if not (ids or usernames) or len(ids) > 1 or len(usernames) > 1:
        return None
    body = har_response_body(object_or_empty(response.get('content')))
    if body.get('status') not in (None, 'ok') or body.get('errors'):
        return None
    data = object_or_empty(body.get('data'))
    for container in (body, data, object_or_empty(body.get('graphql')),
                      object_or_empty(data.get('xdt_api__v1__users__web_profile_info'))):
        user = object_or_empty(container.get('user'))
        identity = cursor(user.get('pk', user.get('id')))
        name = user.get('username')
        if not identity or not re.fullmatch(r'[0-9]{1,30}', identity) or not isinstance(name, str) or not HANDLE.fullmatch(name):
            continue
        if (ids and identity not in ids) or (usernames and name.lower() not in usernames):
            continue
        for first, second, shape in (
            (user.get('follower_count'), user.get('following_count'), 'user/counts'),
            (object_or_empty(user.get('edge_followed_by')).get('count'),
             object_or_empty(user.get('edge_follow')).get('count'), 'user/edges-counts'),
        ):
            if type(first) is int and first >= 0 and type(second) is int and second >= 0:
                return shape
    return None


def inspect_endpoints(first, following, mode):
    endpoints = {}
    matched = 0
    failures = []
    failure_count = 0
    captures = [('combined', first)] if following is None else [('initial', first), ('next', following)]
    for name, entries in captures:
        for request, parsed, raw, response in requests_in(entries):
            status = response.get('status')
            if type(status) is not int or not 0 <= status <= 599:
                continue
            evidence = {**request, 'capture': name, 'http_status': status}
            supported = 200 <= status < 300 or status in (405, 408)
            if mode == 'profile':
                params = request_params(raw, parsed)
                shape = profile_evidence(parsed.path, params, response)
                if shape:
                    evidence = {**with_query_id(evidence, parsed.path, params), 'response_shape': shape}
            else:
                shape = supported and password_key_headers(response)
            if shape:
                matched += 1
                path = request['path_template'] if mode == 'profile' and PROFILE_PATH.fullmatch(parsed.path) else parsed.path
                endpoint = (request['method'], request['host'], path, evidence.get('query_id'))
                endpoints.setdefault(endpoint, evidence)
            elif mode == 'password-key' and parsed.path in KEY_ROUTES:
                failure_count += 1
                if len(failures) < 8:
                    failures.append({**evidence, 'reason': 'missing-or-invalid-headers' if supported else 'http-status'})
    count = len(endpoints)
    return {'status': 'observed' if count == 1 else 'ambiguous' if count else 'insufficient',
            'matching_requests': matched, 'endpoint_candidates': count,
            'candidates': list(endpoints.values())[:8], 'failed_requests': failure_count, 'failures': failures}


def inspect(first, following, direction, *, allow_capture_direction=True):
    initial = list(observations(first, direction, allow_capture_direction))
    combined = following is None
    if combined:
        later = [item for item in initial if item.sent]
        initial = [item for item in initial if not item.sent]
    else:
        later = list(observations(following, direction, allow_capture_direction))
    by_cursor = {}
    for item in later:
        for token in item.sent:
            by_cursor.setdefault((item.shape, token), []).append(item)
    pairs = []
    match_count = 0
    for item in initial:
        if item.sent or item.coverage != 'more' or not item.returned:
            continue
        for match in by_cursor.get((item.shape, item.returned), ()):
            if combined and match.request['entry'] <= item.request['entry']:
                continue
            if item.target is not None and match.target is not None and item.target != match.target:
                continue
            match_count += 1
            if len(pairs) < 8:
                pairs.append({'initial': item.report(), 'next': match.report(),
                              'same_target': True if item.target is not None and match.target is not None else None})
    return {'status': 'observed' if match_count == 1 else 'ambiguous' if match_count else 'insufficient',
            'initial_candidates': len(initial), 'next_candidates': len(later),
            'matching_pairs': match_count, 'pairs': pairs}


def changes_between(old, new):
    return {field: {'previous': old.get(field), 'current': new.get(field)}
            for field in REQUEST_FIELDS if old.get(field) != new.get(field)}


def compare(previous, current, mode):
    if previous['status'] != 'observed' or current['status'] != 'observed':
        return None
    if mode not in LIST_MODES:
        return changes_between(previous['candidates'][0], current['candidates'][0])
    old, new = previous['pairs'][0], current['pairs'][0]
    return {stage: changes_between(old[stage], new[stage]) for stage in ('initial', 'next')}


def inspect_mode(first, following, mode, *, auto=False):
    if mode in LIST_MODES:
        return inspect(first, following, mode, allow_capture_direction=not auto)
    return inspect_endpoints(first, following, mode)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=(*MODES, 'auto'))
    parser.add_argument('initial_har', type=Path)
    parser.add_argument('next_har', type=Path, nargs='?', help='Omit when both pages are in the first HAR.')
    parser.add_argument('--previous', type=Path, nargs='+', metavar='HAR',
                        help='One combined HAR or two HARs in page order.')
    args = parser.parse_args()
    if args.previous and len(args.previous) > 2:
        parser.error('--previous accepts one or two HAR files.')
    try:
        next_entries = har_entries(args.next_har) if args.next_har else None
        first_entries = har_entries(args.initial_har)
        auto = args.mode == 'auto'
        modes = MODES if auto else (args.mode,)
        current = {mode: inspect_mode(first_entries, next_entries, mode, auto=auto) for mode in modes}
        report = {'direction' if args.mode in LIST_MODES else 'mode': args.mode,
                  'current': current if auto else current[args.mode]}
        if args.previous:
            old_first = har_entries(args.previous[0])
            old_next = har_entries(args.previous[1]) if len(args.previous) == 2 else None
            previous = {mode: inspect_mode(old_first, old_next, mode, auto=auto) for mode in modes}
            changes = {mode: compare(previous[mode], current[mode], mode) for mode in modes}
            report.update(previous=previous if auto else previous[args.mode], changes=changes if auto else changes[args.mode])
    except OSError:
        parser.exit(2, 'Could not read a local HAR file.\n')
    except ValueError as error:
        parser.exit(2, f'{error}\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
