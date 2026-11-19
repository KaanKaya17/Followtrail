"""Synthetic paused follower reader: Instagram returned list rows without `pk` three times.

Runs after desktop/backend/seed_qa.py. Only the diagnosis is written; the
retained lists stay as they were, as they do after a real pause.
"""
from pathlib import Path
import argparse
import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'desktop/backend'))
from accounts import Accounts
import read_health

parser = argparse.ArgumentParser()
parser.add_argument('--home', type=Path, required=True)
args = parser.parse_args()
home = args.home.resolve()
if home.parent != Path(tempfile.gettempdir()).resolve() or not home.name.startswith('TrackerQA-'):
    raise SystemExit('Only isolated TrackerQA homes are accepted')
app = Accounts(home)
app.data['readHealth'] = {'followers': {'reason': 'format', 'http': 200, 'at': app.data['account']['observedAt'],
                                        'streak': read_health.PAUSE_AFTER, 'paused': True, 'missing': ['users.pk']}}
read_health.validate(app.data)
app.save()
