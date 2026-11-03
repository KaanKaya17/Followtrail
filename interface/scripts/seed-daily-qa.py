"""Synthetic long-term archive for the packaged offline UI; caller supplies a test home."""
import argparse
import copy
from pathlib import Path
import sys
import time
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'desktop/backend'))
from accounts import Accounts

parser=argparse.ArgumentParser();parser.add_argument('--home',type=Path,required=True);args=parser.parse_args()
engine=Accounts(args.home)
engine.daily_store().clear_all()
now=int(time.time())//86400*86400+43200
for identity in ('100','200'):
    observations=[dict(observedAt=now-(799-n)*86400,followers=1000+n,following=200+n//4,posts=10+n//40)
                  for n in range(800) if n!=790]
    engine.daily_store().record(identity,observations)
    base=engine.data['account'] if identity=='100' else engine.data['people'][identity]
    base.update(observations[-1])
    engine.data['history'][identity]=[dict(row,username=base['username']) for row in observations[-3:]]
engine.save()
print('Synthetic 799-day archives created for two profiles; recent history contains only 3 points. No network.')
