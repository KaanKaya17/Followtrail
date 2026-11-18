"""Offline tests for the list membership ledger (first/last seen, departures, returns)."""
import copy
import json
import tempfile
import unittest
from unittest.mock import patch

import membership
import data_privacy
from engine import Engine
from network import AppFailure, parse_profile
from test_backend import FakeClient, page, profile
from test_list_pages import query as list_page


def scan(identity, at, ids, complete=True):
    return {'id': identity, 'ownerId': '100', 'direction': 'followers', 'complete': complete,
            'startedAt': at - 1, 'finishedAt': at,
            'members': {str(i): {'id': str(i), 'username': f'qa.{i}', 'name': f'QA {i}'} for i in ids}}


class LedgerTests(unittest.TestCase):
    def test_departure_and_return_keep_both_boundaries(self):
        record = {'snapshots': []}
        self.assertEqual(membership.apply(record, scan('a', 10, [1, 2])), [])
        self.assertEqual(membership.apply(record, scan('b', 20, [2])), [])
        self.assertEqual(membership.apply(record, scan('c', 30, [1, 2, 3])), ['1'])
        ledger = record['membership']
        one = membership.movement(ledger['members']['1'], ledger)
        self.assertEqual(one, {'firstSeen': 10, 'baseline': True, 'lastSeen': 30, 'returns': 1, 'present': True,
                               'returnedAt': 30, 'previousLeftBetween': [10, 20]})
        three = membership.movement(ledger['members']['3'], ledger)
        self.assertFalse(three['baseline'])
        self.assertEqual(three['firstSeen'], 30)
        membership.apply(record, scan('d', 40, [2, 3]))
        gone = membership.movement(ledger['members']['1'], ledger)
        self.assertEqual((gone['present'], gone['leftBetween'], gone['previousReturnedAt'], gone['returns']),
                         (False, [30, 40], 30, 1))
        self.assertEqual(membership.counts(ledger), {'scans': 4, 'baselineAt': 10, 'appliedAt': 40,
                                                     'returned': 0, 'left': 1})
        membership.validate(record)

    def test_partial_duplicate_and_older_scans_are_not_evidence(self):
        record = {'snapshots': []}
        membership.apply(record, scan('a', 10, [1]))
        before = copy.deepcopy(record)
        membership.apply(record, scan('p', 20, [], complete=False))
        membership.apply(record, scan('a', 10, [1]))
        membership.apply(record, scan('old', 5, []))
        self.assertEqual(record, before)

    def test_entries_are_replaced_not_mutated(self):
        record = {'snapshots': []}
        membership.apply(record, scan('a', 10, [1]))
        frozen = dict(record['membership']['members'])
        membership.apply(record, scan('b', 20, []))
        self.assertIsNone(frozen['1']['spans'][-1]['gone'])
        self.assertEqual(record['membership']['members']['1']['spans'][-1]['gone'], 20)

    def test_backfill_uses_retained_complete_snapshots_in_order(self):
        data = {'lists': {'100:followers': {'snapshots': [scan('b', 20, [2]), scan('a', 10, [1, 2]),
                                                          scan('c', 30, [1, 2])]}}}
        membership.ensure(data)
        ledger = data['lists']['100:followers']['membership']
        self.assertEqual((ledger['scans'], ledger['appliedId']), (3, 'c'))
        self.assertEqual(membership.rows(ledger, 'returned')[0][0]['id'], '1')

    def test_restored_record_backfills_before_new_scan(self):
        record = {'snapshots': [scan('a', 10, [1]), scan('b', 20, [])]}
        latest = scan('c', 30, [1])
        record['snapshots'].append(latest)
        self.assertEqual(membership.apply(record, latest), ['1'])

    def test_order_newest_movement_first(self):
        record = {'snapshots': []}
        membership.apply(record, scan('a', 10, [1, 2, 3]))
        membership.apply(record, scan('b', 20, [2, 3]))
        membership.apply(record, scan('c', 30, [3]))
        self.assertEqual([p['id'] for p, _ in membership.rows(record['membership'], 'left')], ['2', '1'])

    def test_limits_keep_present_and_drop_oldest_departures(self):
        record = {'snapshots': []}
        with patch.object(membership, 'MAX_ENTRIES', 3):
            membership.apply(record, scan('a', 10, [1, 2]))
            membership.apply(record, scan('b', 20, [3]))
            membership.apply(record, scan('c', 30, [4]))
        self.assertEqual(sorted(record['membership']['members']), ['2', '3', '4'])
        record = {'snapshots': []}
        for index in range(2 * membership.MAX_SPANS + 4):
            membership.apply(record, scan(f's{index}', 10 + index, [1] if index % 2 == 0 else []))
        self.assertEqual(len(record['membership']['members']['1']['spans']), membership.MAX_SPANS)
        membership.validate(record)

    def test_retention_forgets_old_movement_but_keeps_current_members(self):
        record = {'snapshots': []}
        membership.apply(record, scan('a', 10, [1, 2]))
        membership.apply(record, scan('b', 20, [2]))
        membership.apply(record, scan('c', 30, [1, 2]))
        membership.apply(record, scan('d', 40, [2]))
        membership.prune(record, 35)
        members = record['membership']['members']
        self.assertEqual(sorted(members), ['1', '2'])
        self.assertEqual(len(members['1']['spans']), 1)
        membership.prune(record, 50)
        self.assertEqual(sorted(record['membership']['members']), ['2'])
        membership.validate(record)

    def test_validation_rejects_malformed_ledgers(self):
        record = {'snapshots': []}
        membership.apply(record, scan('a', 10, [1]))
        membership.apply(record, scan('b', 20, []))
        for change in (lambda l: l.update(extra=1),
                       lambda l: l['members']['1'].update(username='bad name'),
                       lambda l: l['members']['1']['spans'][0].update(gone=5),
                       lambda l: l['members']['1']['spans'].append({'from': 15, 'to': 15, 'gone': None}),
                       lambda l: l['members'].update({'01': l['members']['1']}),
                       lambda l: l['members']['1'].update(spans=[])):
            broken = copy.deepcopy(record)
            change(broken['membership'])
            with self.subTest(change=change), self.assertRaises(ValueError):
                membership.validate(broken)


class EngineLedgerTests(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.TemporaryDirectory(prefix='TrackerQA-')
        self.addCleanup(self.home.cleanup)
        self.now = [1800000000]
        self.engine = Engine(self.home.name, clock=lambda: self.now[0])
        self.engine.record_profile(parse_profile(profile()), own=True)
        self.engine.data['session'] = {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC-SECRET'}}
        self.engine.save()

    def scan(self, ids):
        self.now[0] += 3600
        fake = FakeClient([profile(followers=len(ids)), page(ids)])
        with patch.object(self.engine, 'client', return_value=fake):
            self.engine.run('scan', {'direction': 'followers'})

    def test_scans_record_returns_in_views_events_and_reopen(self):
        t1 = self.now[0] + 3600
        self.scan(['200', '201'])
        self.scan(['201'])
        self.scan(['200', '201', '202'])
        e = self.engine
        state = e.snapshot()
        self.assertEqual(state['lists']['100:followers']['movement']['returned'], 1)
        normal = list_page(e)
        self.assertEqual({p['id']: p['change'] for p in normal['items']}, {'200': 'returned', '201': '', '202': 'added'})
        back = list_page(e, relation='returned')
        self.assertEqual(back['total'], 1)
        item = back['items'][0]
        self.assertEqual((item['id'], item['change'], item['photoScan']), ('200', 'returned', state['lists']['100:followers']['snapshots'][-1]['id']))
        self.assertEqual(item['movement']['previousLeftBetween'], [t1, t1 + 3600])
        self.assertEqual(item['movement']['returnedAt'], t1 + 7200)
        self.assertTrue(item['movement']['baseline'])
        self.assertEqual(list_page(e, relation='left')['total'], 0)
        event = next(ev for ev in e.data['events'] if ev['type'] == 'list')
        self.assertEqual((event['new']['returned'], event['new']['returnedIds']), (1, ['200']))
        self.assertNotIn('SYNTHETIC-SECRET', json.dumps([state, back]))
        reopened = Engine(self.home.name, clock=lambda: self.now[0])
        self.assertEqual(reopened.data['lists']['100:followers']['membership'], e.data['lists']['100:followers']['membership'])

    def test_left_view_survives_snapshot_rotation_and_person_lookup(self):
        self.scan(['200', '201'])
        self.scan(['201'])
        for _ in range(6):
            self.scan(['201'])
        e = self.engine
        self.assertNotIn('200', {i for s in e.data['lists']['100:followers']['snapshots'] for i in s['members']})
        left = list_page(e, relation='left')
        self.assertEqual([(p['id'], p['username'], p['photoScan']) for p in left['items']], [('200', 'qa.200', '')])
        self.assertFalse(left['items'][0]['movement']['present'])
        history = e.list_pages.membership({'accountId': '100', 'id': '200'})
        self.assertEqual(history['followers']['leftBetween'][1] - history['followers']['leftBetween'][0], 3600)
        self.assertIsNone(history['following'])
        self.assertEqual(e.list_pages.membership({'accountId': '100', 'id': '999'})['followers'],
                         {'present': False, 'scans': 8, 'appliedAt': self.now[0]})
        for bad in ({}, {'accountId': '101', 'id': '200'}, {'accountId': '100', 'id': 200},
                    {'accountId': '100', 'id': '200', 'x': 1}):
            with self.subTest(bad=bad), self.assertRaises(AppFailure):
                e.list_pages.membership(bad)

    def test_movement_views_need_latest_complete_scan(self):
        self.scan(['200'])
        self.scan(['201'])
        e = self.engine
        scans = e.snapshot()['lists']['100:followers']['snapshots']
        with self.assertRaises(AppFailure):
            list_page(e, relation='left', scanId=scans[0]['id'], previousScanId='')
        with self.assertRaises(AppFailure):
            list_page(e, relation='returned', comparison=True)

    def test_older_vault_is_backfilled_on_open(self):
        self.scan(['200'])
        self.scan(['201'])
        self.scan(['200'])
        e = self.engine
        for record in e.data['lists'].values():
            record.pop('membership', None)
        e.save()
        reopened = Engine(self.home.name, clock=lambda: self.now[0])
        self.assertEqual(reopened.snapshot()['lists']['100:followers']['movement']['returned'], 1)

    def test_retention_and_profile_erase_cover_the_ledger(self):
        self.scan(['200', '201'])
        self.scan(['201'])
        e = self.engine
        data_privacy.prune(e.data, self.now[0] + 1)
        self.assertEqual(sorted(e.data['lists']['100:followers']['membership']['members']), ['201'])
        e.data['people']['300'] = {'id': '300', 'username': 'qa.person'}
        e.data['lists']['300:followers'] = {'snapshots': [], 'membership': membership._blank()}
        data_privacy.erase(e.data, '300')
        self.assertNotIn('300:followers', e.data['lists'])

    def test_corrupt_ledger_or_returns_fail_validation(self):
        self.scan(['200'])
        self.scan(['201'])
        self.scan(['200'])
        e = self.engine
        from engine import validate_archive
        broken = copy.deepcopy(e.data)
        broken['lists']['100:followers']['membership']['members']['200']['spans'][0]['from'] = 'x'
        with self.assertRaises(ValueError):
            validate_archive(broken)
        broken = copy.deepcopy(e.data)
        event = next(ev for ev in broken['events'] if ev['type'] == 'list')
        event['new']['returnedIds'] = ['999']
        with self.assertRaises(ValueError):
            validate_archive(broken)


if __name__ == '__main__':
    unittest.main()
