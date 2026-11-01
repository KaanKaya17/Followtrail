"""Packaged worker startup regression, isolated encrypted synthetic data only."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'desktop/backend'))
from storage import Vault

version = json.loads((root / 'package.json').read_text(encoding='utf-8'))['version']
worker = root.parent / f'artifacts/InstagramTracker-Desktop-{version}/resources/app/worker/tracker-worker.exe'
with tempfile.TemporaryDirectory(prefix='TrackerQA-') as home:
    subprocess.run([sys.executable, str(root / 'desktop/backend/seed_qa.py'), '--home', home], check=True, capture_output=True)
    vault = Vault(home)
    data = vault.load()
    record = data['lists']['100:followers']
    baseline = copy.deepcopy(record['snapshots'])
    now = data['account']['observedAt']
    members = {str(i): {'id': str(i), 'username': f'qa.{i}', 'name': 'Synthetic'} for i in range(1000, 1076)}
    record['draft'] = {'id': 'qa-legacy-partial', 'ownerId': '100', 'direction': 'followers',
                       'startedAt': now - 30, 'finishedAt': now, 'members': members, 'complete': False,
                       'status': 'partial', 'pages': 2, 'expected': 82, 'nextCursor': '', 'seenCursors': []}
    record['schedule'] = {'after': now + 1800, 'error': 'list_count', 'lastAt': now}
    gate = copy.deepcopy(data['gate'])
    vault.save(data)
    result = subprocess.run([str(worker), '--data-dir', home, '--offline'], input='',
                            capture_output=True, text=True, encoding='utf-8', timeout=45)
    assert result.returncode == 0, f'Worker exit: {result.returncode}'
    events = [json.loads(line) for line in result.stdout.splitlines()]
    assert [event['type'] for event in events] == ['ready']
    state = events[0]['state']
    reopened = state['lists']['100:followers']
    assert reopened['snapshots'] == baseline
    assert reopened['draft']['members'] == members
    assert reopened['draft']['complete'] is False
    assert reopened['draft']['expected'] == 82
    assert state['gate'] == gate
    assert state['version'] == version
    assert 'nextCursor' not in reopened['draft']
    print('PASS packaged startup: legacy 76/82 partial list, baseline and safety gate preserved; offline only')
