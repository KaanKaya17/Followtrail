"""Timeline-only synthetic history in an isolated offline QA vault."""
from pathlib import Path
from datetime import datetime, timedelta
import argparse
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'desktop/backend'))
from accounts import Accounts
from engine import validate_archive

parser = argparse.ArgumentParser()
parser.add_argument('--home', type=Path, required=True)
home = parser.parse_args().home.resolve()
if home.parent != Path(tempfile.gettempdir()).resolve() or not home.name.startswith('TrackerQA-'):
    raise SystemExit('Only isolated TrackerQA homes are accepted')
app = Accounts(home)
today = datetime.now().replace(hour=12, minute=0, second=0, microsecond=0)
events = []


def event(identity, kind, person, before=None, after=None, days=0, minutes=0, **extra):
    events.append(dict(id=identity, type=kind, personId=person, old=before, new=after,
                       at=(today-timedelta(days=days, minutes=minutes)).timestamp(),
                       title=identity, **extra))


event('qa-followers', 'profile', '200', {'followers': 81}, {'followers': 82})
event('qa-username', 'profile', '200', {'username': '<img src=x onerror=window.timelineInjected=true>'}, {'username': 'qa.new'}, minutes=1)
event('qa-zero', 'profile', '201', {'posts': 1}, {'posts': 0}, minutes=2)
event('qa-unknown', 'profile', '201', None, {'followers': 5}, days=1)
event('qa-list', 'list', '200', None, {'direction': 'followers', 'count': 82, 'added': 3, 'removed': 1,
      'ownerUsername': 'qa.deniz', 'previousAt': (today-timedelta(days=2)).timestamp(), 'membersAdded': [{'id': str(i), 'username': 'qa.new'+str(i)} for i in range(700, 703)],
      'membersRemoved': [{'id': '800', 'username': 'qa.missing'}]}, days=1, minutes=1)
event('qa-baseline', 'list', '100', None, {'direction': 'following', 'count': 2}, days=2)
event('qa-error', 'error', None, minutes=3)
events[-1]['title'] = 'HTTP 429 <img src=x onerror=window.timelineErrorInjected=true>'
event('qa-deleted', 'profile', '999', {'username': 'old.username'}, {'username': 'archived.username'}, days=2, minutes=1)
event('qa-multiple', 'profile', '200', {'bio': 'Old bio', 'private': False}, {'bio': 'New bio', 'private': True}, days=1, minutes=2)
event('qa-access', 'check', '201', None, {'status': 'accessible', 'reason': 'profile_visible'}, days=1, minutes=3, topic='access')
events[-1]['title'] = 'Profil açılıyor'
for i in range(120):
    event('qa-check-'+str(i), 'check', '200' if i % 2 == 0 else '201', minutes=10+i)
events.sort(key=lambda item: item['at'], reverse=True)
app.data['events'] = events
app.data['gate'].update(blockedUntil=0, manualOnly=False)
validate_archive(app.data)
app.save()
