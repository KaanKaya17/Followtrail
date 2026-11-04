"""Bounded, explicit live page-size comparison through the production read gate.

Does not log cookies, cursors, response bodies or list members; does not replace list baselines.
Stop the desktop app before running: the diagnostic persists the real request budget and pauses.
"""
import argparse
import csv
from datetime import datetime, timezone
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import time

BACKEND=Path(__file__).resolve().parents[1]/'interface/desktop/backend'
sys.path.insert(0,str(BACKEND))
from accounts import Accounts
from network import AppFailure, close_client, failure, parse_page, parse_profile
import profile_search


def require_stopped():
    for name in ('InstagramTracker.exe','tracker-worker.exe'):
        result=subprocess.run(['tasklist','/FI',f'IMAGENAME eq {name}','/FO','CSV','/NH'],capture_output=True,text=True,check=True)
        if any(row and row[0].lower()==name.lower() for row in csv.reader(io.StringIO(result.stdout))):
            raise AppFailure('busy','Close the desktop app and its worker before this diagnostic.')


def compare(engine,client,target,direction,sizes=(50,100,200),pages=2,rows=None):
    if direction not in ('followers','following') or not 1<=pages<=4 or not sizes or len(set(sizes))!=len(sizes) or any(n not in (50,100,200) for n in sizes):
        raise ValueError('probe bounds')
    rows=[] if rows is None else rows
    restricted=False
    for size in sizes:
        cursor=None;seen=set();cursors=set()
        for number in range(1,pages+1):
            params=dict(count=size,rank_token=client.rank_token,search_surface='follow_list_page',query='',enable_groups='true')
            if cursor:params['max_id']=cursor
            at=time.perf_counter()
            payload=client.private_request(f"friendships/{target['id']}/{direction}/",params=params)
            elapsed=time.perf_counter()-at
            parsed=parse_page(payload)
            overlap=len(seen & parsed.members.keys());seen.update(parsed.members)
            row=dict(requested=size,page=number,http=engine.data['gate'].get('status'),rows=len(payload['users']),
                     uniqueOnPage=len(parsed.members),newUnique=len(parsed.members)-overlap,uniqueTotal=len(seen),
                     repeatedAcrossPages=overlap,coverage=parsed.coverage,hasNext=bool(parsed.cursor),
                     elapsedIncludingPacingSeconds=round(elapsed,3),decodedJsonBytes=len(json.dumps(payload,ensure_ascii=False).encode()),
                     expected=target[direction],matchesExpectedAtEnd=(len(seen)==target[direction]) if not parsed.cursor else None)
            rows.append(row)
            print(json.dumps({'page':row}),flush=True)
            if parsed.coverage=='limited':
                # A data-coverage flag is not HTTP throttling. Never follow its cursor or
                # certify a full list; only compare the next explicitly selected first page.
                restricted=True;return_status='server_coverage_restriction';break
            if parsed.coverage not in ('end','more'):return rows,'unknown_coverage'
            if not parsed.cursor:return_status='end';break
            if parsed.cursor in cursors or not parsed.members:return rows,'pagination_stalled'
            cursors.add(parsed.cursor);cursor=parsed.cursor
        else:return_status='page_cap'
        print(json.dumps({'sizeCompleted':size,'reason':return_status}),flush=True)
    return rows,'server_coverage_restriction' if restricted else None


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--account',required=True)
    parser.add_argument('--target',required=True)
    parser.add_argument('--direction',choices=('followers','following'),required=True)
    parser.add_argument('--pages',type=int,choices=range(1,5),default=2)
    parser.add_argument('--sizes',type=int,nargs='+',choices=(50,100,200),default=[50,100,200])
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    name=profile_search.username(args.target)
    if not name:parser.error('Invalid profile username')
    require_stopped()
    home=Path(os.environ['LOCALAPPDATA'])/'InstagramTracker-Desktop'
    engine=Accounts(home)
    entry=next((key for key,value in engine.catalog['accounts'].items() if value['username'].lower()==args.account.lower()),None)
    if entry is None:parser.error('Requested account is not saved; no new login is performed')
    engine.activate(entry)
    report={'at':datetime.now(timezone.utc).isoformat(),'account':args.account,'target':name,'direction':args.direction,
            'sizes':args.sizes,'pageCapPerSize':args.pages,'live':True,'rows':[],'stopped':None}
    client=None
    def emit(event):
        if event.get('type')=='progress' and event.get('phase') in ('waiting','limited'):
            print(json.dumps({key:event[key] for key in ('phase','until') if key in event}),flush=True)
    engine.emit=emit
    try:
        client=engine.client()
        engine.read_profile(client,engine.data['account']['id'],avatar=False)
        print(json.dumps({'ownSessionVerified':True}),flush=True)
        known=next((p for p in [engine.data['account'],*engine.data['people'].values()] if p['username'].lower()==name),None)
        target=engine.read_profile(client,known['id'],avatar=False) if known else parse_profile(client.private_request(f'users/{name}/usernameinfo/'))
        if target['username'].lower()!=name:raise AppFailure('identity','Target username changed; choose the current username explicitly')
        report['expectedBefore']=target[args.direction]
        report['rows'],report['stopped']=compare(engine,client,target,args.direction,sizes=args.sizes,pages=args.pages,rows=report['rows'])
        if not report['stopped']:
            after=engine.read_profile(client,target['id'],avatar=False)
            report['expectedAfter']=after[args.direction]
            report['countStable']=report['expectedBefore']==report['expectedAfter']
    except Exception as exc:
        error=failure(exc)
        if error['code']=='limited' and not engine.data['gate'].get('manualOnly'):engine.limited()
        if error['code'] in ('session','verification'):engine.data['gate']['manualOnly']=True
        engine.data['lastError']={**error,'at':engine.clock()}
        engine.save()
        report['stopped']=error['code'];report['error']=error['message']
        print(json.dumps({'stopped':error['code'],'http':engine.data['gate'].get('status')}),flush=True)
    finally:
        if client:
            engine.data['session']=client.get_settings();engine.save()
            close_client(client)
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'report':str(args.output),'stopped':report['stopped']}),flush=True)
    return 1 if report['stopped'] else 0


if __name__=='__main__':raise SystemExit(main())
