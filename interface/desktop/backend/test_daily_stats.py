import copy
from datetime import datetime, timezone
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import daily_stats as daily
from accounts import Accounts
from engine import Engine
from network import parse_profile, AppFailure
from storage import StorageFailure
from test_backend import profile

FIXTURE=Path(__file__).resolve().parents[3]/'test-fixtures/daily-stats.json'


class DailyTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-');self.addCleanup(self.home.cleanup)
        self.store=daily.DailyStore(self.home.name,'100')
        self.fixture=json.loads(FIXTURE.read_text())
    def samples(self):
        return [dict(observedAt=row['at']/1000,**{k:v for k,v in row.items() if k!='at'}) for row in self.fixture['observations']]
    def test_shared_utc_leap_day_duplicates_unknown_counts_and_year_rollover(self):
        self.store.record('200',self.samples())
        documents=self.store.documents('200')
        rows=sorted([r for d in documents for r in d['days'].values()],key=lambda r:r['day'])
        self.assertEqual(rows,self.fixture['expectedRows'])
        self.assertEqual([d['year'] for d in documents],[2024,2025])
        self.store.record('200',self.samples())
        self.assertEqual(self.store.documents('200'),documents)
        self.assertEqual(self.store.query('200','followers',0,self.fixture['observations'][-1]['at'])['observedDays'],4)
        for path in self.store.directory.iterdir():self.assertNotIn(b'followers',path.read_bytes())
    def test_unknown_is_not_zero_and_gaps_are_not_filled(self):
        days={};at=1709168400000
        daily.observe(days,{'observedAt':at/1000,'followers':None,'following':4})
        self.assertNotIn('followers',next(iter(days.values())))
        self.assertEqual(daily.series(list(days.values()),'followers',0,at)['points'],[])
        self.store.record('200',self.samples())
        result=self.store.query('200','followers',0,at)
        self.assertTrue(any(b['bucket']-a['bucket']>1 for a,b in zip(result['points'],result['points'][1:])))
    def test_long_period_output_is_bounded_and_retains_total_coverage(self):
        start=1709164800000
        rows=[]
        for i in range(1440):
            day=start+i*daily.DAY;days={};daily.observe(days,dict(observedAt=(day+1)/1000,followers=i));rows+=list(days.values())
        result=daily.series(rows,'followers',0,start+1440*daily.DAY)
        self.assertLessEqual(len(result['points']),720);self.assertEqual(result['observedDays'],1440)
        self.assertEqual(result['points'][0]['first'],0);self.assertEqual(result['points'][-1]['value'],1439)
    def test_clear_watermark_prevents_recent_history_and_old_backups_reappearing(self):
        self.store.record('200',self.samples());backup=self.store.export()
        cutoff=self.fixture['observations'][-1]['at']+1000
        self.store.clear('200',cutoff)
        self.store.record('200',self.samples());self.store.restore(backup)
        self.assertEqual(self.store.query('200','followers',0,cutoff)['points'],[])
        self.store.record('200',[dict(observedAt=(cutoff+1000)/1000,followers=9)])
        result=self.store.query('200','followers',0,cutoff+1000)
        self.assertEqual(result['samples'],1);self.assertEqual(result['points'][0]['minimum'],9)
    def test_clear_also_covers_recorded_timestamps_ahead_of_current_clock(self):
        self.store.record('200',self.samples())
        self.store.clear('200',1)
        self.store.record('200',self.samples())
        self.assertEqual(self.store.query('200','followers',0,1)['points'],[])

    def test_partial_profile_without_known_observation_time_has_no_invented_stats(self):
        e=Engine(self.home.name,clock=lambda:1800000000)
        e.record_profile(parse_profile(profile()),own=True)
        e.data['people']['200']={'id':'200','username':'qa.partial'}
        e.snapshot()
        self.assertEqual(e.daily_query(dict(accountId='100',id='200',metric='followers',range=0))['points'],[])
        for change in ({'accountId':'300'},{'id':'999'},{'range':True},{'metric':'invalid'}):
            with self.assertRaises(AppFailure):e.daily_query(dict(dict(accountId='100',id='200',metric='followers',range=0),**change))

    def test_atomic_write_failure_does_not_erase_previous_file(self):
        self.store.record('200',self.samples())
        before=self.store.export()
        value=dict(observedAt=(self.fixture['observations'][-1]['at']+1000)/1000,followers=999)
        with patch('daily_stats.atomic_write',side_effect=OSError('synthetic full disk')),self.assertRaises(OSError):self.store.record('200',[value])
        self.assertEqual(self.store.export(),before)
        self.store.record('200',[value]);self.assertEqual(self.store.query('200','followers',0,0)['points'][-1]['value'],999)
    def test_restore_validates_all_before_writing_and_preserves_newer_rows(self):
        self.store.record('200',self.samples());old=self.store.export()
        self.store.record('200',[dict(observedAt=(self.fixture['observations'][-1]['at']+1)/1000,followers=500)])
        self.store.restore(old);self.assertEqual(self.store.query('200','followers',0,0)['points'][-1]['value'],500)
        invalid=copy.deepcopy(old);invalid[-1]['owner']='999'
        before=self.store.export()
        with self.assertRaises(ValueError):self.store.restore(invalid)
        self.assertEqual(self.store.export(),before)
    def test_two_instances_share_serialization_and_read_each_others_newest_file(self):
        self.store.record('200',self.samples())
        other=daily.DailyStore(self.home.name,'100')
        self.assertIs(self.store.lock,other.lock)
        at=self.fixture['observations'][-1]['at']
        other.record('200',[dict(observedAt=(at+1000)/1000,followers=200)])
        self.store.record('200',[dict(observedAt=(at+2000)/1000,followers=300)])
        point=other.query('200','followers',0,at+2000)['points'][-1]
        self.assertEqual(point['value'],300);self.assertEqual(point['maximum'],300)

    def test_corrupt_wrong_owner_and_size_limited_files_are_preserved(self):
        self.store.record('200',self.samples());path=next(self.store.directory.glob('*.stats'));original=path.read_bytes()
        with self.assertRaises(ValueError):daily.DailyStore(self.home.name,'300').documents()
        self.assertEqual(path.read_bytes(),original)
        path.write_bytes(b'invalid')
        with self.assertRaises(Exception):self.store.query('200','followers',0,0)
        self.assertEqual(path.read_bytes(),b'invalid')
    def test_engine_migrates_retained_observations_before_rolling_trim_and_reopens(self):
        clock=[1709168400.0];e=Engine(self.home.name,clock=lambda:clock[0])
        e.record_profile(parse_profile(profile()),own=True)
        start=clock[0];template=e.data['history']['100'][0]
        e.data['history']['100']=[dict(template,observedAt=start+n*86400,followers=n if n else 2) for n in range(366)]
        for n in range(366,401):
            clock[0]=start+n*86400;e.record_profile(parse_profile(profile(followers=n)),own=True)
        e.save();self.assertEqual(len(e.data['history']['100']),366)
        reopened=Engine(self.home.name,clock=lambda:clock[0]);state=reopened.snapshot()
        with patch.object(reopened,'client',side_effect=AssertionError('No network')):
            result=reopened.daily_query(dict(accountId='100',id='100',metric='followers',range=0))
        self.assertEqual(result['observedDays'],401);self.assertEqual(state['dailyWarning'],False)
        self.assertNotIn('daily',state)
    def test_legacy_backfill_backup_restore_and_account_erasure(self):
        e=Engine(self.home.name,clock=lambda:1800000000)
        e.record_profile(parse_profile(profile()),own=True);e.save()
        e.daily_store().clear_all()
        self.assertTrue(e.snapshot()['account'])
        self.assertEqual(e.daily_query(dict(accountId='100',id='100',metric='followers',range=0))['observedDays'],1)
        backup=Path(self.home.name)/'backup'
        e.export_backup(backup)
        from storage import protect
        self.assertEqual(len(json.loads(protect(backup.read_bytes(),decrypt=True))['daily']),1)
        e.import_backup(backup)
        e.run('delete_all',{})
        self.assertEqual(list((Path(self.home.name)/'daily').iterdir()),[])
    def test_range_does_not_read_years_outside_requested_period(self):
        self.store.record('200',self.samples())
        original=self.store.read
        def read(path):
            if path.name.endswith('-2024.stats'):raise AssertionError('Old year should not be read')
            return original(path)
        now=int(datetime(2025,3,2,tzinfo=timezone.utc).timestamp()*1000)
        with patch.object(self.store,'read',side_effect=read):
            self.assertEqual(self.store.query('200','followers',30,now)['points'],[])

    def test_bounded_backup_refuses_large_archive_without_touching_old_destination(self):
        e=Engine(self.home.name,clock=lambda:1800000000)
        e.record_profile(parse_profile(profile()),own=True)
        e.daily_store().record('100',[dict(observedAt=1704067200+n*86400,followers=n,following=5,posts=10) for n in range(200)])
        target=Path(self.home.name)/'old.backup';target.write_bytes(b'SYNTHETIC-PREVIOUS')
        with patch('engine.MAX_VAULT',32768),self.assertRaises(AppFailure):e.export_backup(target)
        self.assertEqual(target.read_bytes(),b'SYNTHETIC-PREVIOUS')

    def test_deleted_account_revokes_inflight_archive_migration(self):
        e=Engine(self.home.name,clock=lambda:1800000000)
        e.record_profile(parse_profile(profile()),own=True);e.snapshot()
        old=e.daily_store();seeds=tuple(e.data['history']['100'])
        e.run('delete_all',{})
        with self.assertRaises(StorageFailure):old.record('100',seeds)
        self.assertEqual(list(old.directory.iterdir()),[])
        e.record_profile(parse_profile(profile()),own=True)
        self.assertIsNot(e.daily_store(),old)

    def test_daily_failure_does_not_prevent_profile_reads_and_is_visible(self):
        e=Engine(self.home.name,clock=lambda:1800000000)
        with patch('daily_stats.atomic_write',side_effect=StorageFailure('synthetic disk failure')):
            e.record_profile(parse_profile(profile()),own=True)
        self.assertTrue(e.snapshot()['dailyWarning']);self.assertEqual(e.data['account']['followers'],2)
        e.daily_query(dict(accountId='100',id='100',metric='followers',range=0))
        self.assertFalse(e.snapshot()['dailyWarning'])
