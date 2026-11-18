"""Synthetic follow history: one member leaves and comes back, one leaves for good.

Runs after desktop/backend/seed_qa.py. The ledger is removed so the packaged
app must backfill it from the retained complete scans when it opens.
"""
from pathlib import Path
import argparse
import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'desktop/backend'))
from accounts import Accounts

parser = argparse.ArgumentParser()
parser.add_argument('--home', type=Path, required=True)
args = parser.parse_args()
home = args.home.resolve()
if home.parent != Path(tempfile.gettempdir()).resolve() or not home.name.startswith('TrackerQA-'):
    raise SystemExit('Only isolated TrackerQA homes are accepted')
app = Accounts(home)
record = app.data['lists']['100:followers']
after = record['snapshots'][-1]
now = after['finishedAt']
members = {i: {'id': i, 'username': 'qa.' + i, 'name': 'QA ' + i} for i in ('200', '201', '202', '203', '205')}


def scan(name, ids, at):
    return {'id': name, 'ownerId': '100', 'direction': 'followers', 'members': {i: members[i] for i in ids},
            'startedAt': at - 30, 'finishedAt': at, 'complete': True, 'status': 'complete', 'pages': 1,
            'expected': len(ids)}


day = 86400
record['snapshots'] = [scan('history-1', ['200', '201', '202', '205'], now - 3 * day),
                       scan('history-2', ['201', '202', '205'], now - 2 * day),
                       scan('before', ['201', '202'], now - day),
                       scan('after', ['200', '201', '202', '203'], now)]
record.pop('membership', None)
app.save()
