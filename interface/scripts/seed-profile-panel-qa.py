"""Readable synthetic profiles; never read or modify a real app home."""
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
for profile in [engine.data['account'],*engine.data['people'].values()]:profile['observedAt']=time.time()
engine.data['people']['200'].update(name='Deniz Kaya',followers=82,following=50,posts=12,private=True,verified=True,
    bio='Fotoğraf, tasarım ve küçük keşifler.\nİstanbul · Yalnız örnek veriler',group='Arkadaşlar',note='Yalnız cihazımda saklanan örnek not.')
engine.save()
print('Synthetic profile-panel fixtures prepared; no network.')
