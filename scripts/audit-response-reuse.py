"""Offline producer/consumer audit. Synthetic responses, temporary vaults only."""
import json
from pathlib import Path
import sys
import tempfile
import threading
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'interface/desktop/backend'))
from engine import Engine
from network import parse_profile
from test_backend import profile
from test_scan_progress import ClockWait, PacedClient
from thumbnails import Thumbnails


def setup(home):
    now = [1800000000.0]
    engine = Engine(home, clock=lambda: now[0], request_delay=lambda: 8)
    engine.cancel = ClockWait(now)
    engine.record_profile(parse_profile(profile()), own=True)
    engine.data['session'] = {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC-SECRET'}}
    return engine, now


def projections():
    with tempfile.TemporaryDirectory(prefix='TrackerReuseQA-') as home:
        engine, now = setup(home)
        engine.configure_wayback({'id': '100', 'enabled': True})
        now[0] += 1
        client = PacedClient(engine, [profile(followers=8, following=5, media_count=7,
            biography='New bio', full_name='New name', is_private=True, is_verified=True)])
        with patch.object(engine, 'client', return_value=client), patch('engine.read_avatar'):
            state = engine.run('refresh', {})
        assert len(client.calls) == 1
        assert state['account']['followers'] == 8 and state['account']['bio'] == 'New bio'
        assert engine.data['history']['100'][-1]['bio'] == 'New bio'
        daily = engine.daily_store().query('100', 'followers', 7, int(now[0] * 1000))
        assert daily['points'][-1]['value'] == 8
        assert 'New bio' in json.dumps(engine.data['notificationLog'])
        assert engine.data['wayback']['profiles']['100']['entries'][-1]['bio'] == 'New bio'
        return {'apiRequests': len(client.calls), 'verifiedConsumers': [
            'account projection', 'change history', 'daily statistics', 'notification log', 'visual biography history'],
            'avatarDownload': 'suppressed synthetic test; separate CDN resource'}


def access_gap():
    with tempfile.TemporaryDirectory(prefix='TrackerReuseQA-') as home:
        engine, now = setup(home)
        engine.record_profile(parse_profile(profile('200', followers=2, biography='Old bio')))
        now[0] += 1
        class Client(PacedClient):
            def private_request(self, endpoint, **kwargs):
                result = super().private_request(endpoint, **kwargs)
                self.last_response = SimpleNamespace(status_code=200)
                return result
        target = {**profile('200', followers=77, biography='New bio'), 'status':'ok'}
        client = Client(engine, [profile(), target])
        with patch.object(engine, 'client', return_value=client):
            engine.run('check_access', {'id':'200'})
        assert len(client.calls) == 2
        assert engine.data['access']['200']['checks'][-1]['status'] == 'accessible'
        assert engine.data['people']['200']['followers'] == 2
        assert engine.data['people']['200']['bio'] == 'Old bio'
        return {'apiRequests': 2, 'returnedTargetFollowers':77, 'retainedTargetFollowers':2,
                'targetDetailsReused':False, 'healthRead':'fresh authenticated own-profile proof; not automatically redundant'}


def save_gap():
    with tempfile.TemporaryDirectory(prefix='TrackerReuseQA-') as home:
        engine, now = setup(home)
        engine.data['lists']['100:followers'] = {'snapshots':[{'id':'synthetic', 'complete':True,
            'finishedAt':now[0], 'members':{'200':{'id':'200','username':'qa.person','name':'QA'}}}]}
        client = PacedClient(engine, [profile('200', followers=77, biography='New bio')])
        with patch.object(engine, 'client', return_value=client):
            state = engine.run('profile_detail', {'accountId':'100','id':'200'})
        assert state['profileDetail']['profile']['followers'] == 77
        engine.run('save_person', {'id':'200','watching':True})
        stored = engine.data['people']['200']
        assert stored.get('followers') is None and stored.get('bio') is None
        assert len(client.calls) == 1
        return {'apiRequests':1, 'drawerReceivedFollowers':77, 'savedFollowers':stored.get('followers'),
                'savedPersonReceivesPreviouslyFetchedDetails':False,
                'scope':'backend transfer boundary; renderer may still display its own transient cache'}


def thumbnail_gap():
    release, entered, finished = threading.Event(), threading.Event(), threading.Event()
    lock = threading.Lock()
    counts = {'reads':0,'replies':0}
    def reader(image, context):
        with lock:
            counts['reads'] += 1
            if counts['reads'] == 2: entered.set()
        if not release.wait(3): raise TimeoutError('Synthetic release not delivered')
        image['avatar'] = 'data:image/jpeg;base64,QA=='
    def emit(event):
        with lock:
            counts['replies'] += 1
            if counts['replies'] >= 2: finished.set()
    pool = Thumbnails(emit, reader=reader)
    result, pictures = {'token':'synthetic'}, {'200':'https://cdninstagram.com/synthetic.jpg'}
    try:
        for number in (1,2):
            pool.request({'id':number,'args':{'token':'synthetic','id':'200'}}, result, pictures, namespace='100')
        assert entered.wait(2), 'Both identical requests should reach the fake reader in current code'
        release.set()
        assert finished.wait(2)
        pool.request({'id':3,'args':{'token':'synthetic','id':'200'}}, result, pictures, namespace='100')
        assert counts == {'reads':2,'replies':3}
        return {'simultaneousConsumers':2, 'syntheticCdnDownloads':counts['reads'],
                'thirdConsumerUsesCompletedCache':True, 'instagramApiRequests':0}
    finally:
        release.set()
        pool.close()


if __name__ == '__main__':
    print(json.dumps({'mode':'offline source-boundary audit, no real HTTP', 'sourceBase':'a33802a',
        'oneResponseManyConsumers':projections(), 'accessProbeGap':access_gap(),
        'savePersonGap':save_gap(), 'inFlightThumbnailGap':thumbnail_gap(),
        'limits':'Native Android source inspected; no emulator/device runtime. Forced synthetic overlaps do not '
                 'measure real-world frequency or whole-application savings. Production behavior unchanged.'}, indent=2))
