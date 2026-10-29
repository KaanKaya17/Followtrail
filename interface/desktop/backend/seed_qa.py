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
    parser.add_argument('--wayback',action='store_true')
    parser.add_argument('--anonymous',action='store_true')
    parser.add_argument('--keyboard',action='store_true')
    parser.add_argument('--hold',action='store_true')
    parser.add_argument('--accounts',action='store_true')
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
    if args.wayback:
        if args.empty: raise ValueError('Wayback fixture requires records')
        e.configure_wayback({'id':'200','enabled':True})
        for i,color in enumerate(['#bb4b60','#4b67bb']):
            now+=3600
            image=BytesIO();Image.new('RGB',(100,100),color).save(image,format='JPEG')
            p={**e.data['people']['200'],'bio': 'Coffee, books & Istanbul ☕' if i==0 else 'Coffee, travel & Ankara ☕ <img src=x onerror=evil>',
               'picture':'id:qa'+str(i),'avatar':'data:image/jpeg;base64,'+base64.b64encode(image.getvalue()).decode('ascii')}
            e.record_profile(p)
    if args.anonymous:
        if args.empty: raise ValueError('Anonymous fixture requires records')
        positive={'method':'public_profile_page','requestedUsername':'qa.deniz','checkedAt':now,
                  'status':'visible','reason':'identity_match','http':200,'identityMatched':True,
                  'observedId':'200','observedUsername':'qa.deniz'}
        e.data['access']['200']['checks'].append({'status':'suspected','reason':'anonymous_visible',
            'ownHTTP':200,'targetHTTP':404,'checkedAt':now,'absence':dict(e.data['access']['200']['absence']),
            'anonymous':positive})
        e.data['access']['200']['alertStrength']='strong'
        e.event('profile','Güçlü engellenme şüphesi · @qa.deniz: Aynı hesap oturumsuz görünür, bağlı oturumdan erişilemiyor. Kesin engelleme kanıtı değildir.',
                '200',None,e.data['access']['200']['checks'][-1],topic='access')
        e.data['access']['201']['checks'].append({'status':'unknown','reason':'anonymous_inconclusive',
            'ownHTTP':200,'targetHTTP':404,'checkedAt':now,'absence':None,
            'anonymous':{'method':'public_profile_page','requestedUsername':'qa.ada','checkedAt':now,
                         'status':'unknown','reason':'limited','http':429,'identityMatched':None}})
        closed=access_checks.ensure_case(e.data,e.data['people']['300'],now)
        closed['checks']=[{'status':'unavailable','reason':'both_unavailable','ownHTTP':200,'targetHTTP':404,
            'checkedAt':now,'absence':None,'anonymous':{'method':'public_profile_page','requestedUsername':'qa.large',
            'checkedAt':now,'status':'not_found','reason':'http_not_found','http':404,'identityMatched':None}}]
    if args.keyboard:
        if args.empty: raise ValueError('Keyboard fixture requires records')
        e.data['session']={'cookies':{'ds_user_id':'100','sessionid':'SYNTHETIC-SECRET'}}
        e.data['gate'].update(blockedUntil=0,nextAt=0,manualOnly=False,status=200)
        e.data['people']['201']['name']='QA Ada <img src=x onerror=window.paletteInjected=true>'
    if args.hold:
        if not args.keyboard: raise ValueError('Hold fixture requires keyboard records')
        e.data['gate'].update(blockedUntil=1800003600,manualOnly=True,status=429)
    e.save()
    if args.accounts:
        if args.empty: raise ValueError('Account fixture requires records')
        from accounts import Accounts
        accounts = Accounts(home, clock=lambda:now)
        accounts.select_connection_account(profile('400','qa.shop',70,40))
        accounts.record_profile(profile('400','qa.shop',70,40), own=True)
        accounts.data['session'] = {'cookies':{'ds_user_id':'400','sessionid':'SYNTHETIC-SHOP'}}
        accounts.record_profile(profile('200','qa.deniz',50,20))
        accounts.data['people']['200']['note'] = 'SHOP-ONLY-NOTE'
        accounts.save()
        accounts.run('switch_account', {'id':'100'})
    print('Synthetic vault prepared; no session or network.')


if __name__=='__main__':main()
