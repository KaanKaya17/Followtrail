"""Recent, small, synthetic account history for the offline overview."""
import argparse
from pathlib import Path
import sys
import tempfile
import time
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'desktop/backend'))
from accounts import Accounts

parser=argparse.ArgumentParser();parser.add_argument('--home',type=Path,required=True);args=parser.parse_args()
home=args.home.resolve()
if home.parent!=Path(tempfile.gettempdir()).resolve() or not home.name.startswith('TrackerQA-'):
    raise SystemExit('Only isolated TrackerQA homes are accepted')
engine=Accounts(home)
now=int(time.time())
own=engine.data['account']
own.update(name='Örnek hesabım',followers=82,following=50,observedAt=now,avatar=engine.data['people']['200']['avatar'])
rows=[dict(observedAt=now-(6-i)*86400,followers=count,following=50,posts=1,username=own['username'])
      for i,count in enumerate([80,80,81,81,80,81,82])]
engine.data['history']['100']=rows
engine.daily_store().clear_all();engine.daily_store().record('100',rows)
for index,event in enumerate(engine.data['events']):event['at']=now-index*60
engine.data['gate'].update(blockedUntil=0,nextAt=0,manualOnly=False,status=200)
engine.data['settings']['automatic']=False
engine.save()
print('Synthetic small-count overview prepared; no network.')
