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
                args += ['--previous', archive('old-initial', previous[0]), archive('old-next', previous[1])]
            result = subprocess.run(args, capture_output=True, text=True, check=True)
            for secret in ('qa-private', '99881234', MEMBER['pk']):
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


if __name__ == '__main__':
    unittest.main()
