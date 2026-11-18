"""Offline measurement: how a story check spends the API queue/budget.

Runs the real Windows Engine, story parser, DPAPI story store and request gate
against a synthetic transport (no DNS/socket). Reports, for N story items:
API budget entries, media lane entries and virtual waiting time.

    python scripts/audit-media-lane.py [--items 10]
"""
import argparse
import json
from pathlib import Path
import socket
import sys
import tempfile
from io import BytesIO
from unittest.mock import patch

root = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(root / 'interface/desktop/backend'))

import requests  # noqa: E402
from PIL import Image  # noqa: E402
from engine import Engine  # noqa: E402
from network import parse_profile  # noqa: E402
from test_backend import profile  # noqa: E402


class VirtualWait:
    """Cancellation event whose wait() advances a virtual clock instead of sleeping."""
    def __init__(self, now):
        self.now, self.flag, self.waited = now, False, 0.0
    def is_set(self): return self.flag
    def set(self): self.flag = True
    def clear(self): self.flag = False
    def wait(self, seconds):
        self.now[0] += seconds; self.waited += seconds; return self.flag


def payload(items):
    base = json.loads((root / 'test-fixtures/stories.json').read_text())
    template = base['reel']['items'][0]
    rows = []
    for index in range(items):
        row = json.loads(json.dumps(template))
        row['pk'] = row['id'] = str(5000 + index)
        row['taken_at'] = template['taken_at'] + index
        for candidate in row.get('image_versions2', {}).get('candidates', []):
            candidate['url'] = f'https://scontent.cdninstagram.com/s{index}.jpg'
        rows.append(row)
    base['reel']['items'] = rows
    return base


def measure(items):
    with tempfile.TemporaryDirectory(prefix='TrackerQA-') as home:
        now = [1800000100.0]
        engine = Engine(home, clock=lambda: now[0], request_delay=lambda: 8, media_delay=lambda: 2)
        engine.cancel = VirtualWait(now)
        engine.record_profile(parse_profile(profile()), own=True)
        engine.data['people']['200'] = parse_profile(profile('200'))
        engine.data['session'] = {'cookies': {'ds_user_id': '100', 'sessionid': 'SYNTHETIC'}}
        engine.save()
        body = payload(items)
        output = BytesIO(); Image.new('RGB', (36, 64), '#385a81').save(output, format='JPEG'); raw = output.getvalue()
        calls = {'api': 0, 'cdn': 0}

        def send(request, **kwargs):
            response = requests.Response(); response.status_code = 200; response.url = request.url; response.request = request
            if '/feed/user/200/story/' in request.url:
                calls['api'] += 1; response._content = json.dumps(body).encode()
            else:
                calls['cdn'] += 1; response._content = raw; response.headers['Content-Length'] = str(len(raw))
            response._content_consumed = True
            return response

        before = dict(engine.data['gate'])
        with patch('requests.Session.send', side_effect=send), \
                patch.object(socket.socket, 'connect', side_effect=AssertionError('real connection')), \
                patch.object(socket, 'getaddrinfo', side_effect=AssertionError('real DNS')):
            state = engine.run('story_fetch', {'accountId': '100', 'id': '200'})
        gate = engine.data['gate']
        account_entries = len(gate.get('accountRequestTimes', {}).get('100', []))
        return {
            'items': items, 'apiRequests': calls['api'], 'cdnDownloads': calls['cdn'],
            'saved': state['storyDetail']['summary']['saved'],
            'apiBudgetEntries': account_entries,
            'deviceApiBudgetEntries': len(gate.get('requestTimes', [])) - len(before.get('requestTimes', [])),
            'mediaLaneEntries': len(gate.get('mediaTimes', [])),
            'virtualWaitSeconds': round(engine.cancel.waited, 1),
            'apiNextAtAfter': round(gate.get('nextAt', 0) - now[0], 1),
        }


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--items', type=int, nargs='*', default=[2, 10, 50])
    args = parser.parse_args()
    print(json.dumps([measure(n) for n in args.items], indent=2))
