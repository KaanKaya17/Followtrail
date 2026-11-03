"""A newer partial overtakes an older partial, without merging their members."""
from pathlib import Path
import argparse
import sys
import tempfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'desktop/backend'))
from accounts import Accounts

parser=argparse.ArgumentParser();parser.add_argument('--home',type=Path,required=True);args=parser.parse_args()
home=args.home.resolve()
if home.parent!=Path(tempfile.gettempdir()).resolve() or not home.name.startswith('TrackerQA-'):
    raise SystemExit('Only isolated TrackerQA homes are accepted')
app=Accounts(home)
record=app.data['lists']['200:followers']
record['draft'].update(members={str(i):{'id':str(i),'username':'qa.'+str(i),'name':'QA '+str(i)} for i in range(501,579)},
                       pages=4,status='partial',nextCursor=None)
app.data['people']['200']['followers']=82
app.data['lists']['100:following']['snapshots'][-1].update(recoveryPass=1,overlapRows=1,restartPending=False)
app.save()
