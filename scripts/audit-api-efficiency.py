"""Offline request-count audit; production Engine, synthetic transport, temporary vault.

Run using the desktop backend's Python environment. No credentials or network are
used. The comparison exercises the explicit scan and automatic reuse paths with
identical synthetic data, not real network latency or server quota behavior.
"""
import ctypes
import json
from pathlib import Path
import sys
import tempfile
import time
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'interface/desktop/backend'))
from engine import Engine
from network import parse_profile
from test_backend import FakeClient, page, profile
from test_scan_progress import ClockWait


def peak_working_set():
    if sys.platform != 'win32':
        return None
    class Counters(ctypes.Structure):
        _fields_ = [('cb', ctypes.c_ulong), ('PageFaultCount', ctypes.c_ulong)] + [
            (name, ctypes.c_size_t) for name in ('PeakWorkingSetSize', 'WorkingSetSize',
            'QuotaPeakPagedPoolUsage', 'QuotaPagedPoolUsage', 'QuotaPeakNonPagedPoolUsage',
            'QuotaNonPagedPoolUsage', 'PagefileUsage', 'PeakPagefileUsage')]
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.GetCurrentProcess.restype = ctypes.c_void_p
    psapi = ctypes.WinDLL('psapi', use_last_error=True)
    psapi.GetProcessMemoryInfo.argtypes = [ctypes.c_void_p, ctypes.POINTER(Counters), ctypes.c_ulong]
    counters = Counters()
    counters.cb = ctypes.sizeof(counters)
    if not psapi.GetProcessMemoryInfo(kernel.GetCurrentProcess(), ctypes.byref(counters), counters.cb):
        raise ctypes.WinError(ctypes.get_last_error())
    return counters.PeakWorkingSetSize


def scenario(followers, following, reuse):
    now = [1800000000.0]
    owner = profile(followers=followers, following=following)
    class Client(FakeClient):
        def private_request(self, endpoint, **kwargs):
            engine.before_request()
            self.calls.append((endpoint, kwargs))
            if endpoint == 'users/100/info/':
                result = owner
            else:
                direction = endpoint.split('/')[2]
                assert direction in ('followers', 'following')
                count = followers if direction == 'followers' else following
                offset = int(kwargs['params'].get('max_id', 0))
                ids = [str(200 + n) for n in range(offset, min(offset + 50, count))]
                result = page(ids, str(offset + 50) if offset + 50 < count else '')
            engine.after_response(SimpleNamespace(status_code=200, headers={}))
            return result
    with tempfile.TemporaryDirectory(prefix='TrackerEfficiencyQA-') as home:
        engine = Engine(home, clock=lambda: now[0], request_delay=lambda: 8)
        engine.cancel = ClockWait(now)
        engine.record_profile(parse_profile(owner), own=True)
        engine.data['session'] = {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC-SECRET'}}
        client = Client([])
        wall, cpu = time.perf_counter(), time.process_time()
        with patch.object(engine, 'client', return_value=client), patch('engine.read_avatar'):
            engine.refresh({})
            for direction in ('followers', 'following'):
                engine.scan({'direction': direction}, reuse_profile=reuse)
        endpoints = [endpoint for endpoint, _ in client.calls]
        profile_reads = endpoints.count('users/100/info/')
        list_reads = len(endpoints) - profile_reads
        expected_reads = (2 if followers == 3000 else 1) if reuse else 3
        assert profile_reads == expected_reads
        assert list_reads == (followers + 49) // 50 + (following + 49) // 50
        for direction, count in (('followers', followers), ('following', following)):
            snapshot = engine.data['lists']['100:' + direction]['snapshots'][-1]
            assert snapshot['complete'] and len(snapshot['members']) == count
        return {'followers': followers, 'following': following, 'automaticReuse': reuse,
                'measuredProfileRequests': profile_reads, 'measuredListRequests': list_reads,
                'measuredTotalRequests': len(endpoints), 'simulatedPacingSeconds': sum(engine.cancel.waits),
                'wallSeconds': round(time.perf_counter() - wall, 4),
                'cpuSeconds': round(time.process_time() - cpu, 4)}


if __name__ == '__main__':
    wall = time.perf_counter()
    cpu = time.process_time()
    cases = [scenario(followers, following, reuse) for followers, following in
             ((50, 50), (82, 50), (3000, 2000)) for reuse in (False, True)]
    print(json.dumps({'mode': 'offline synthetic production-boundary audit',
                     'pageSize': 50, 'fixedSyntheticDelaySeconds': 8, 'cases': cases,
                     'wallSeconds': round(time.perf_counter() - wall, 4),
                     'cpuSeconds': round(time.process_time() - cpu, 4),
                     'peakProcessWorkingSetBytes': peak_working_set(),
                     'limits': 'Sequential first-pass baseline/reuse pairs; warm order not randomized. '
                               'No real HTTP, TLS, API quota, Android runtime or agent token measurement. '
                               'Memory is this Python process only.'}, indent=2))
