"""Offline request-count audit; production Engine, synthetic transport, temporary vault.

Run using the desktop backend's Python environment. No credentials or network are
used. Proposed reuse counts are arithmetic lower bounds, not implemented behavior.
"""
import ctypes
import json
from pathlib import Path
import sys
import tempfile
import time
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'interface/desktop/backend'))
from engine import Engine
from network import parse_profile
from test_backend import page, profile
from test_scan_progress import ClockWait, PacedClient


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


def scenario(followers, following):
    now = [1800000000.0]
    owner = profile(followers=followers, following=following)
    responses = [owner]
    for count in (followers, following):
        responses.append(owner)
        for offset in range(0, count, 50):
            ids = [str(200 + n) for n in range(offset, min(offset + 50, count))]
            responses.append(page(ids, str(offset + 50) if offset + 50 < count else ''))
    with tempfile.TemporaryDirectory(prefix='TrackerEfficiencyQA-') as home:
        engine = Engine(home, clock=lambda: now[0], request_delay=lambda: 8)
        engine.cancel = ClockWait(now)
        engine.record_profile(parse_profile(owner), own=True)
        engine.data['session'] = {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC'}}
        client = PacedClient(engine, responses)
        with patch.object(engine, 'client', return_value=client), patch('engine.read_avatar'):
            engine.refresh({})
            for direction in ('followers', 'following'):
                engine.scan({'direction': direction})
        endpoints = [endpoint for endpoint, _ in client.calls]
        profile_reads = endpoints.count('users/100/info/')
        list_reads = len(endpoints) - profile_reads
        assert profile_reads == 3
        assert list_reads == (followers + 49) // 50 + (following + 49) // 50
        for direction, count in (('followers', followers), ('following', following)):
            snapshot = engine.data['lists']['100:' + direction]['snapshots'][-1]
            assert snapshot['complete'] and len(snapshot['members']) == count
        return {'followers': followers, 'following': following,
                'measuredProfileRequests': profile_reads, 'measuredListRequests': list_reads,
                'measuredTotalRequests': len(endpoints), 'simulatedPacingSeconds': sum(engine.cancel.waits),
                'idealReuseLowerBoundRequests': 1 + list_reads,
                'lowerBoundAssumption': 'One profile basis remains usable for both directions; not implemented. '
                    'A short freshness window can expire during long scans, reducing achievable savings.'}


if __name__ == '__main__':
    wall = time.perf_counter()
    cpu = time.process_time()
    cases = [scenario(50, 50), scenario(82, 50), scenario(3000, 2000)]
    print(json.dumps({'mode': 'offline synthetic production-boundary audit',
                     'pageSize': 50, 'fixedSyntheticDelaySeconds': 8, 'cases': cases,
                     'wallSeconds': round(time.perf_counter() - wall, 4),
                     'cpuSeconds': round(time.process_time() - cpu, 4),
                     'peakProcessWorkingSetBytes': peak_working_set(),
                     'limits': 'No real HTTP, TLS, API quota, Android runtime, cold/warm optimization comparison '
                               'or agent token measurement. Memory is this Python process only.'}, indent=2))
