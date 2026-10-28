"""Create isolated synthetic QA records; never used by the packaged worker."""
import argparse
from pathlib import Path
import tempfile
import base64
from io import BytesIO
from PIL import Image
from engine import Engine
from network import parse_profile
import access_checks


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--home',required=True,type=Path)
    parser.add_argument('--empty',action='store_true')
    parser.add_argument('--performance',action='store_true')
    args=parser.parse_args()
    home=args.home.resolve()
    if home.parent!=Path(tempfile.gettempdir()).resolve() or not home.name.startswith('TrackerQA-'):
        raise ValueError('Only an isolated QA home may be seeded')
    if (home/'tracker.vault').exists():
        raise ValueError('QA home already has a vault')
    e=Engine(home)
    if not args.empty:
        now=1800000000
        e.clock=lambda:now
        def profile(identity,username,followers,following):
            return parse_profile({'user':{'pk':identity,'username':username,'full_name':'QA '+username,
                'follower_count':followers,'following_count':following,'biography':'Synthetic profile',
                'media_count':1,'is_private':False,'is_verified':False}})
        e.record_profile(profile('100','qa.owner',2,2),True)
        now+=60
        e.record_profile(profile('100','qa.owner',3,2),True)
        e.record_profile(profile('200','qa.deniz',10,4))
        e.record_profile(profile('201','qa.ada',20,5))
        e.record_profile(profile('300','qa.large',150,0))
        image=BytesIO();Image.new('RGB',(20,20),'#674bd8').save(image,format='JPEG')
        e.data['people']['200']['avatar']='data:image/jpeg;base64,'+base64.b64encode(image.getvalue()).decode('ascii')
        e.data['people']['200'].update(favorite=True,watching=True,note='PRIVATE-QA-NOTE <img src=x onerror=alert(1)>',group='Arkadaşlar')
        members={identity:{'id':identity,'username':'qa.'+identity,'name':'QA '+identity} for identity in ('200','201','202','203','204')}
        def scan(name,ids,at):
            return {'id':name,'ownerId':'100','direction':'followers','members':{i:members[i] for i in ids},
                'startedAt':at-30,'finishedAt':at,'complete':True,'status':'complete','pages':1,'expected':len(ids)}
        e.data['lists']['100:followers']={'snapshots':[scan('before',['200','201','202'],now-50),scan('after',['201','202','203'],now)],
            'draft':{'id':'unfinished','ownerId':'100','direction':'followers','members':{'204':members['204']},
                'startedAt':now,'finishedAt':now,'complete':False,'status':'running','pages':1,'expected':3}}
        e.data['lists']['100:following']={'snapshots':[dict(scan('following',['201','204'],now),direction='following')]}
        case=access_checks.ensure_case(e.data,e.data['people']['200'],now)
        case.update(absence=access_checks.absence_evidence(e.data,'200'),alertRecorded=True)
        case['checks']=[{'status':'suspected','reason':'missing_and_unavailable','ownHTTP':200,
                         'targetHTTP':404,'checkedAt':now,'ownCheckedAt':now-15,'absence':dict(case['absence'])}]
        e.event('profile','Engellenme şüphesi · @qa.deniz: Tam takipçi kaydında yok ve profiline erişilemiyor. Hesap kapanmış da olabilir.',
                '200',None,case['checks'][-1],topic='access')
        renamed=access_checks.ensure_case(e.data,e.data['people']['201'],now)
        renamed['checks']=[{'status':'accessible','reason':'profile_visible','ownHTTP':200,'targetHTTP':200,
                            'checkedAt':now,'username':'qa.ada.renamed','usernameChanged':True}]
        large={str(i):{'id':str(i),'username':'qa.'+str(i),'name':'QA '+str(i)} for i in range(301,451)}
        e.data['lists']['300:followers']={'snapshots':[{'id':'large','ownerId':'300','direction':'followers',
            'startedAt':now-30,'finishedAt':now,'complete':True,'status':'complete','pages':3,'expected':150,'members':large}]}
        e.event('list','QA list', '100',None,{'direction':'followers','count':3,'added':1,'removed':1})
        e.settings({'notifications':True})
        e.notification_rule({'id':'200','enabled':True,'fields':{'followers':False}})
        e.observation_group='qa-multi-change'
        e.event('profile','@qa.deniz · username','200',{'username':'qa.deniz.old'},{'username':'qa.deniz'})
        e.event('profile','@qa.deniz · followers','200',{'followers':9},{'followers':10})
        e.event('profile','@qa.deniz · bio','200',{'bio':'Before'},{'bio':'After <script>window.logInjected=true</script>'})
        e.observation_group=None
        e.settings({'notifications':False})
        e.data['gate'].update(status=429,blockedUntil=now+3600,manualOnly=True,attempts=2)
    if args.performance:
        if args.empty:
            raise ValueError('Performance fixture requires records')
        # Representative larger archive, still well below the 64 MiB budget.
        for identity in map(str,range(1000,1020)):
            e.record_profile(profile(identity,'qa.'+identity,10000,1000))
            e.data['people'][identity]['watching']=True
            h=e.data['history'][identity][-1]
            e.data['history'][identity]=[{**h,'observedAt':now-n*300} for n in reversed(range(60))]
        members={str(i):{'id':str(i),'username':'qa.'+str(i),'name':'QA '+str(i)} for i in range(2000,12000)}
        for snapshot in e.data['lists']['100:followers']['snapshots']:
            snapshot.update(members=members,expected=len(members),pages=200)
        for n in range(2000):
            e.event('profile','@qa.owner · followers','100',{'followers':n},{'followers':n+1})
    e.save()
    print('Synthetic vault prepared; no session or network.')


if __name__=='__main__':main()
