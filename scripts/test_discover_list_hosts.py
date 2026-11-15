"""CLI tests using synthetic HAR captures."""
import base64
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from urllib.parse import urlencode


SCRIPT = Path(__file__).with_name('discover-list-hosts.py')
TOKEN = 'qa-private-pagination-token'
MEMBER = {'pk': '999999001', 'username': 'qa-private-member'}
KEY = base64.b64encode(b'qa-private-key-fixture').decode()


def entry(url, body, form=None):
    request = {'url': url, 'method': 'POST' if form is not None else 'GET',
               'headers': [{'name': 'Authorization', 'value': 'qa-private-credential'}]}
    if form is not None:
        request['postData'] = {'mimeType': 'application/x-www-form-urlencoded', 'text': urlencode(form)}
    return {'request': request, 'response': {'status': 200, 'content': {'text': json.dumps(body)}}}


def mobile(host, *, target='99881234', direction='followers', next_page=False, limited=False):
    endpoint = f'https://{host}/api/v1/friendships/{target}/{direction}/'
    body = {'status': 'ok', 'users': [MEMBER], 'next_max_id': '' if next_page else TOKEN}
    if limited:
        body['should_limit_list_of_followers'] = True
    query = {'auth': 'qa-private-query'}
    if next_page:
        query['max_id'] = TOKEN
    return entry(endpoint + '?' + urlencode(query), body)


def graphql(doc, *, next_page=False, operation='edge_following'):
    variables = {'id': '99881234'}
    if next_page:
        variables['after'] = TOKEN
    body = {'data': {'user': {operation: {'edges': [{'node': MEMBER}],
            'page_info': {'has_next_page': not next_page, 'end_cursor': None if next_page else TOKEN}}}}}
    return entry('https://web-edge.instagram.com/api/graphql/', body,
                 {'doc_id': doc, 'variables': json.dumps(variables), 'csrf': 'qa-private-form'})


def key_request(path='/api/v1/launcher/sync/', *, status=200, headers=True, host='i.instagram.com', method='POST'):
    record = entry(f'https://{host}{path}?value=qa-private-query', {},
                   {'signed_body': 'qa-private-payload'} if method == 'POST' else None)
    record['response']['status'] = status
    if headers:
        record['response']['headers'] = [
            {'name': 'IG-Set-Password-Encryption-Key-ID', 'value': '123'},
            {'name': 'ig-set-password-encryption-pub-key', 'value': KEY},
            {'name': 'Set-Cookie', 'value': 'qa-private-cookie'},
        ]
    return record


class DiscoveryTests(unittest.TestCase):
    def run_capture(self, direction, first, following=None, previous=None):
        with tempfile.TemporaryDirectory() as directory:
            def archive(name, entries):
                path = Path(directory) / f'{name}.har'
                path.write_text(json.dumps({'log': {'entries': entries}}), encoding='utf-8')
                return str(path)

            args = [sys.executable, str(SCRIPT), direction, archive('initial', first)]
            if following is not None:
                args.append(archive('next', following))
            if previous:
                args += ['--previous', *(archive(f'old-{index}', capture) for index, capture in enumerate(previous))]
            result = subprocess.run(args, capture_output=True, text=True, check=True)
            for secret in ('qa-private', 'qa.private.member', '99881234', MEMBER['pk'], KEY):
                self.assertNotIn(secret, result.stdout)
            self.assertEqual(result.stderr, '')
            return json.loads(result.stdout)

    def test_mobile_discovers_new_host_and_excludes_different_target_and_domain(self):
        old = 'old-edge.instagram.com'
        new = 'new-edge.instagram.com'
        report = self.run_capture('followers', [mobile(new)],
            [mobile(new, next_page=True, target='99881235'),
             mobile('instagram.com.invalid', next_page=True), mobile(new, next_page=True)],
            previous=([mobile(old)], [mobile(old, next_page=True)]))
        self.assertEqual(report['current']['status'], 'observed')
        pair = report['current']['pairs'][0]
        self.assertTrue(pair['same_target'])
        self.assertEqual(pair['next']['entry'], 3)
        self.assertEqual(pair['next']['path_template'], '/api/v1/friendships/{id}/followers/')
        self.assertEqual(report['changes'], {stage: {'host': {'previous': old, 'current': new}}
                                              for stage in ('initial', 'next')})

    def test_graphql_reports_distinct_query_ids_without_parameter_values(self):
        report = self.run_capture('following', [graphql('1111111')],
            [graphql('3333333', next_page=True, operation='edge_followers'), graphql('2222222', next_page=True)])
        pair = report['current']['pairs'][0]
        self.assertEqual(pair['initial']['query_id'], '1111111')
        self.assertEqual(pair['next']['query_id'], '2222222')
        self.assertEqual(pair['next']['coverage_signal'], 'end-marker')

    def test_browser_har_can_mix_encoded_and_plain_json_bodies(self):
        host = 'edge.instagram.com'
        first = mobile(host)
        content = first['response']['content']
        content['encoding'] = 'base64'
        content['text'] = base64.b64encode(content['text'].encode('utf-8')).decode('ascii')
        later = mobile(host, next_page=True)
        self.assertEqual(self.run_capture('followers', [first], [later])['current']['status'], 'observed')
        content['text'] = 'invalid*base64'
        self.assertEqual(self.run_capture('followers', [first], [later])['current']['status'], 'insufficient')

    def test_unfamiliar_route_is_discovered_with_direction_from_capture_label(self):
        host = 'new-edge.instagram.com'
        first = mobile(host)
        later = mobile(host, next_page=True)
        for item in (first, later):
            item['request']['url'] = item['request']['url'].replace('/followers/', '/qa-private-route/')
        report = self.run_capture('followers', [first], [later])['current']
        self.assertEqual(report['status'], 'observed')
        self.assertEqual(report['pairs'][0]['initial']['host'], host)
        self.assertEqual(report['pairs'][0]['initial']['direction_evidence'], 'capture')
        self.assertTrue(report['pairs'][0]['initial']['path_template'].endswith('/{segment}/'))

    def test_limited_page_stays_limited_and_missing_second_page_stays_insufficient(self):
        host = 'edge.instagram.com'
        first = mobile(host)
        limited = mobile(host, next_page=True, limited=True)
        found = self.run_capture('followers', [first], [limited])['current']
        self.assertEqual(found['pairs'][0]['next']['coverage_signal'], 'limited')
        self.assertEqual(self.run_capture('followers', [first], [])['current']['status'], 'insufficient')

    def test_combined_capture_pairs_only_later_requests(self):
        first = mobile('edge.instagram.com')
        page = mobile('edge.instagram.com', next_page=True)
        report = self.run_capture('followers', [page, first, page])['current']
        self.assertEqual(report['status'], 'observed')
        self.assertEqual(report['pairs'][0]['initial']['entry'], 2)
        self.assertEqual(report['pairs'][0]['next']['entry'], 3)
        self.assertEqual(self.run_capture('followers', [page, first])['current']['status'], 'insufficient')

    def test_multiple_matching_requests_are_reported_as_ambiguous(self):
        host = 'edge.instagram.com'
        page = mobile(host, next_page=True)
        found = self.run_capture('followers', [mobile(host)], [page, page])['current']
        self.assertEqual(found['status'], 'ambiguous')
        self.assertEqual(found['matching_pairs'], 2)

    def test_auto_finds_both_list_directions_and_the_password_key_in_one_capture(self):
        host = 'edge.instagram.com'
        generic = [mobile(host), mobile(host, next_page=True)]
        for item in generic:
            item['request']['url'] = item['request']['url'].replace('/followers/', '/qa-private-route/')
        capture = [mobile(host), graphql('1111111', operation='edge_follow'), key_request(),
                   mobile(host, next_page=True), graphql('2222222', next_page=True, operation='edge_follow'),
                   *generic]
        report = self.run_capture('auto', capture)['current']
        self.assertEqual({mode: value['status'] for mode, value in report.items()},
                         {'followers': 'observed', 'following': 'observed', 'profile': 'insufficient',
                          'password-key': 'observed'})
        self.assertEqual(report['followers']['matching_pairs'], 1)
        self.assertEqual(report['following']['pairs'][0]['next']['query_id'], '2222222')
        self.assertEqual(report['password-key']['candidates'][0]['entry'], 3)

    def test_password_key_compares_single_capture_and_reports_old_http_failure(self):
        old = '/api/v1/qe/sync/'
        current = '/api/v1/launcher/sync/'
        report = self.run_capture('password-key', [key_request(old, method='GET', status=404, headers=False),
                                                   key_request(), key_request()],
                                  previous=([key_request(old, method='GET', status=405)],))
        self.assertEqual(report['current']['endpoint_candidates'], 1)
        self.assertEqual(report['current']['matching_requests'], 2)
        self.assertEqual(report['current']['failures'][0]['http_status'], 404)
        self.assertEqual(report['changes'], {'method': {'previous': 'GET', 'current': 'POST'},
                                             'path_template': {'previous': old, 'current': current}})

    def test_password_key_reports_multiple_routes_without_revealing_unknown_segments(self):
        capture = [key_request(), key_request('/api/v1/qa-private-route/'),
                   key_request(host='instagram.com.invalid'), key_request(status=429),
                   key_request(status=302)]
        report = self.run_capture('password-key', capture)['current']
        self.assertEqual(report['status'], 'ambiguous')
        self.assertEqual(report['endpoint_candidates'], 2)
        self.assertEqual([item['http_status'] for item in report['failures']], [429, 302])
        self.assertEqual(report['candidates'][1]['path_template'], '/api/v1/{segment}/')

    def test_password_key_rejects_bad_key_encoding_and_conflicting_headers(self):
        first = key_request()
        first['response']['headers'][1]['value'] = 'qa-private-bad-key'
        second = key_request()
        second['response']['headers'].append({'name': 'Ig-Set-Password-Encryption-Key-Id', 'value': '2'})
        report = self.run_capture('password-key', [first, second])['current']
        self.assertEqual(report['status'], 'insufficient')
        self.assertEqual([item['reason'] for item in report['failures']], ['missing-or-invalid-headers'] * 2)

    def test_graphql_handles_har_form_params_json_and_empty_terminal_alias(self):
        first = graphql('1111111', operation='edge_followed_by')
        first['request']['postData'] = {'mimeType': 'application/x-www-form-urlencoded', 'params': [
            {'name': 'doc_id', 'value': '1111111'}, {'name': 'variables', 'value': json.dumps({'id': '99881234'})}]}
        second = graphql('2222222', operation='edge_followed_by', next_page=True)
        second['request']['postData'] = {'mimeType': 'application/json; charset=UTF-8',
            'text': json.dumps({'doc_id': 2222222, 'variables': {'id': '99881234', 'after': TOKEN}})}
        body = json.loads(second['response']['content']['text'])
        body['data']['user']['edge_followed_by']['edges'] = []
        second['response']['content']['text'] = json.dumps(body)
        report = self.run_capture('followers', [first, second])['current']
        self.assertEqual(report['status'], 'observed')
        self.assertEqual(report['pairs'][0]['next']['member_rows'], 0)
        self.assertEqual(report['pairs'][0]['next']['coverage_signal'], 'end-marker')

    def test_mobile_supports_structured_har_query_and_empty_terminal_page(self):
        host = 'edge.instagram.com'
        first, second = mobile(host), mobile(host, next_page=True)
        second['request']['url'] = second['request']['url'].split('?', 1)[0]
        second['request']['queryString'] = [{'name': 'max_id', 'value': TOKEN}]
        second['response']['content']['text'] = json.dumps({'status': 'ok', 'users': [], 'has_more': False})
        report = self.run_capture('followers', [first, second], previous=([first, second],))
        self.assertEqual(report['current']['status'], 'observed')
        self.assertEqual(report['current']['pairs'][0]['next']['member_rows'], 0)
        self.assertEqual(report['changes'], {'initial': {}, 'next': {}})

    def test_profile_matches_id_before_grouping_multiple_profiles_as_one_route(self):
        def profile(target, returned):
            return entry(f'https://i.instagram.com/api/v1/users/{target}/info/',
                         {'user': {'pk': returned, 'username': 'qa.private.member',
                                   'follower_count': 4, 'following_count': 0}})

        report = self.run_capture('profile', [profile('99881234', '999999001'), profile('99881234', '99881234'),
                                               profile('999999001', '999999001')])['current']
        self.assertEqual(report['status'], 'observed')
        self.assertEqual(report['matching_requests'], 2)
        self.assertEqual(report['candidates'][0]['entry'], 2)
        self.assertEqual(report['candidates'][0]['path_template'], '/api/v1/users/{id}/info/')

    def test_profile_matches_graphql_username_and_masks_path_even_if_handle_is_a_known_word(self):
        user = {'id': '99881234', 'username': 'launcher',
                'edge_followed_by': {'count': 12}, 'edge_follow': {'count': 4}}
        old = entry('https://i.instagram.com/api/v1/users/launcher/usernameinfo/', {'user': user})
        current = entry('https://www.instagram.com/api/graphql/', {'data': {'user': user}},
                        {'doc_id': '123456', 'variables': json.dumps({'username': 'LAUNCHER'})})
        report = self.run_capture('profile', [current], previous=([old],))
        self.assertEqual(report['previous']['candidates'][0]['path_template'], '/api/v1/users/{username}/usernameinfo/')
        self.assertEqual(report['current']['candidates'][0]['response_shape'], 'user/edges-counts')
        self.assertEqual(report['changes']['query_id'], {'previous': None, 'current': '123456'})


if __name__ == '__main__':
    unittest.main()
