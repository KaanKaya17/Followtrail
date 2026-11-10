"""Identify observed Instagram list requests in two local browser HAR captures."""
import argparse
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
              'followers', 'following', 'follow_list', 'list', 'page', 'connection'}
HOST = re.compile(r'(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)*instagram\.com')


def object_or_empty(value):
    return value if isinstance(value, dict) else {}


def json_object(value):
    if not isinstance(value, str) or len(value) > MAX_BODY_CHARS:
        return {}
    try:
        return object_or_empty(json.loads(value))
    except (ValueError, RecursionError):
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


def path_template(path):
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
    if not isinstance(users, list) or not users or not all(is_member(user) for user in users):
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
    return len(users), coverage, next_cursor


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
        if (matches_direction and isinstance(edges, list) and edges and isinstance(info, dict)
                and type(info.get('has_next_page')) is bool
                and all(is_member(object_or_empty(edge).get('node')) for edge in edges)):
            next_cursor = cursor(info.get('end_cursor')) if info['has_next_page'] else None
            coverage = ('more' if next_cursor else 'unknown') if info['has_next_page'] else 'end-marker'
            yield 'edges/page_info', len(edges), coverage, next_cursor
        if depth < 7:
            for key, value in item.items():
                if not isinstance(value, dict):
                    continue
                words = set(re.split(r'[^a-z]+', key.lower()))
                opposite = 'following' if direction == 'followers' else 'followers'
                if opposite not in words:
                    pending.append((value, matches_direction or direction in words, depth + 1))


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


def observations(entries, direction):
    for index, value in enumerate(entries, 1):
        entry = object_or_empty(value)
        req = object_or_empty(entry.get('request'))
        res = object_or_empty(entry.get('response'))
        url, method = req.get('url'), req.get('method')
        if res.get('status') != 200 or method not in ('GET', 'POST') or not isinstance(url, str) or len(url) > 8192:
            continue
        try:
            parsed = urlsplit(url)
            host = parsed.hostname
            if (parsed.scheme != 'https' or parsed.port not in (None, 443) or parsed.username or parsed.password
                    or not host or len(host) > 253 or not HOST.fullmatch(host) or not parsed.path.startswith('/')):
                continue
        except ValueError:
            continue
        parts = set(parsed.path.lower().strip('/').split('/'))
        opposite = 'following' if direction == 'followers' else 'followers'
        if opposite in parts:
            continue
        post = object_or_empty(req.get('postData'))
        params = read_pairs(parsed.query)
        if method == 'POST' and str(post.get('mimeType', '')).split(';', 1)[0] == 'application/x-www-form-urlencoded':
            params.update(read_pairs(post.get('text')))
        variables = json_object(params.get('variables'))
        sent = {token for key in PAGE_KEYS for source in (params, variables) if (token := cursor(source.get(key)))}
        target_path = re.search(r'/(?:friendships|users)/([0-9]{1,30})/(?:followers|following)/?$', parsed.path)
        target = target_path.group(1) if target_path else next(
            (token for key in TARGET_KEYS if (token := cursor(variables.get(key)))), None)
        doc = params.get('doc_id')
        descriptor = {'entry': index, 'host': host, 'path_template': path_template(parsed.path), 'method': method}
        if 'graphql' in parts and isinstance(doc, str) and re.fullmatch(r'[0-9]{6,30}', doc):
            descriptor['query_id'] = doc
        content = object_or_empty(res.get('content'))
        if content.get('encoding') not in (None, ''):
            continue
        for shape, count, coverage, returned in response_pages(json_object(content.get('text')), direction):
            direction_evidence = 'response' if shape != 'users' else 'path' if direction in parts else 'capture'
            yield Observation({**descriptor, 'direction_evidence': direction_evidence},
                              shape, count, coverage, sent, returned, target)


def inspect(first, following, direction):
    initial = list(observations(first, direction))
    later = list(observations(following, direction))
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
            if item.target is not None and match.target is not None and item.target != match.target:
                continue
            match_count += 1
            if len(pairs) < 8:
                pairs.append({'initial': item.report(), 'next': match.report(),
                              'same_target': True if item.target is not None and match.target is not None else None})
    return {'status': 'observed' if match_count == 1 else 'ambiguous' if match_count else 'insufficient',
            'initial_candidates': len(initial), 'next_candidates': len(later),
            'matching_pairs': match_count, 'pairs': pairs}


def compare(previous, current):
    if previous['status'] != 'observed' or current['status'] != 'observed':
        return None
    old, new = previous['pairs'][0], current['pairs'][0]
    return {stage: {field: {'previous': old[stage].get(field), 'current': new[stage].get(field)}
                    for field in ('host', 'path_template', 'method', 'query_id')
                    if old[stage].get(field) != new[stage].get(field)} for stage in ('initial', 'next')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('direction', choices=('followers', 'following'))
    parser.add_argument('initial_har', type=Path)
    parser.add_argument('next_har', type=Path)
    parser.add_argument('--previous', type=Path, nargs=2, metavar=('INITIAL_HAR', 'NEXT_HAR'))
    args = parser.parse_args()
    try:
        current = inspect(har_entries(args.initial_har), har_entries(args.next_har), args.direction)
        report = {'direction': args.direction, 'current': current}
        if args.previous:
            previous = inspect(har_entries(args.previous[0]), har_entries(args.previous[1]), args.direction)
            report.update(previous=previous, changes=compare(previous, current))
    except OSError:
        parser.exit(2, 'Could not read a local HAR file.\n')
    except ValueError as error:
        parser.exit(2, f'{error}\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
