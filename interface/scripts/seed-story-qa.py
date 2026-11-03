"""Synthetic story archive for an explicitly isolated, offline test home."""
import argparse
from io import BytesIO
import json
from pathlib import Path
import sys
import tempfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'desktop/backend'))
from PIL import Image, ImageDraw
from accounts import Accounts
from stories import StoryStore, parse

parser=argparse.ArgumentParser();parser.add_argument('--home',type=Path,required=True);args=parser.parse_args()
home=args.home.resolve()
if home.parent != Path(tempfile.gettempdir()).resolve() or not home.name.startswith('TrackerQA-'):
    raise SystemExit('Only isolated TrackerQA homes are accepted')
engine=Accounts(home)
target='200';store=StoryStore(engine.vault.path.parent,engine.active_id)
payload=json.loads((Path(__file__).resolve().parents[2]/'test-fixtures/stories.json').read_text())
image=Image.new('RGB',(360,640),'#284c72');draw=ImageDraw.Draw(image)
draw.rounded_rectangle((28,40,332,600),radius=28,fill='#4777a0')
draw.text((65,270),'STORY ARCHIVE',fill='white');draw.text((65,310),'OFFLINE TEST',fill='white')
output=BytesIO();image.save(output,format='JPEG')
for item in parse(payload,target):
    store.record(target,item,1800000200,(output.getvalue(),'image/jpeg') if item['type']=='image' else None)
store.checked(target,1800000200,{'returned':2,'saved':1,'existing':0,'metadata':1})
print('Two synthetic story records seeded; one encrypted JPEG. No network.')
