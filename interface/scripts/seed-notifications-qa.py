"""Frozen notification examples in an isolated offline vault."""
from pathlib import Path
import argparse
import sys
import tempfile
import time
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'desktop/backend'))
from accounts import Accounts

parser=argparse.ArgumentParser();parser.add_argument('--home',type=Path,required=True);args=parser.parse_args()
home=args.home.resolve()
if home.parent!=Path(tempfile.gettempdir()).resolve() or not home.name.startswith('TrackerQA-'):
    raise SystemExit('Only isolated TrackerQA homes are accepted')
app=Accounts(home)
now=time.time()
for i,entry in enumerate(app.data['notificationLog']):entry['at']=now-i*120
main=next(entry for entry in app.data['notificationLog'] if entry['id']=='qa-multi-change')
for change in main['changes']:
    if change['category']=='followers':change.update(before={'followers':81},after={'followers':82})
    if change['category']=='username':change['before']={'username':'<img src=x onerror=window.notificationInjected=true>'}
app.data['people']['200'].update(followers=82,posts=0,name='Deniz Kaya')
def event(kind,before,after,identity='200',group='qa-mixed-notification'):
    app.event('profile',kind,identity,before,after,group_id=group)
event('followers',{'followers':81},{'followers':82})
event('private',{'private':False},{'private':True},identity='201')
event('bio',{'bio':'Before'},{'bio':'After'})
app.event('list','Synthetic list change','200',None,
    {'direction':'followers','count':82,'added':3,'removed':1,'previousAt':now-3600,'ownerUsername':'qa.deniz',
     'membersAdded':[{'id':str(i),'username':'qa.new'+str(i),'name':'New '+str(i)} for i in range(700,703)],
     'membersRemoved':[{'id':'800','username':'qa.missing','name':'Missing'}]},group_id='qa-mixed-notification')
event('posts',{'posts':1},{'posts':0},group='qa-zero-notification')
event('followers',None,{'followers':5},group='qa-unknown-notification')
app.event('list','Legacy count-only list change','200',None,{'direction':'following','count':5,'added':2,'removed':1},group_id='qa-legacy-notification')
app.data['people']['200']['username']='qa.deniz.current'
app.data['gate'].update(blockedUntil=0,manualOnly=False)
app.save()
