"""Create public documentation examples in memory; never open a real account vault."""
from pathlib import Path
import json
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'interface/desktop/backend'))
from engine import Engine
from network import parse_profile


def main():
    now = 1793631600  # 2 November 2026, 18:00 Europe/Istanbul; a fixed example, not live data.
    with tempfile.TemporaryDirectory(prefix='TrackerDocs-') as temporary:
        engine = Engine(Path(temporary), clock=lambda: now)
        def profile(identity, username, name, followers, following, bio=''):
            return parse_profile({'user': {'pk':identity, 'username':username, 'full_name':name,
                'follower_count':followers, 'following_count':following, 'media_count':24,
                'biography':bio, 'is_private':False, 'is_verified':False}})
        for day in range(14):
            engine.clock = lambda day=day: now - (13-day)*86400
            engine.record_profile(profile('100','demo.hesabim','Örnek Hesap',1200+day*3+(9 if day==13 else 0),420),own=True)
        engine.clock = lambda: now
        deniz = profile('200','demo.deniz','Deniz Örnek',1250,420,'Tasarım, fotoğraf ve günlük notlar.\nYeni çalışmalar burada.')
        deniz['links'] = [{'url':'https://example.com/portfolyo'}, {'url':'https://www.threads.com/@demo.deniz'}]
        engine.record_profile(deniz)
        engine.record_profile(profile('201','demo.ada','Ada Örnek',840,215,'Çizim, doğa ve küçük keşifler.'))
        engine.data['people']['200'].update(favorite=True,watching=True,group='İçerik üreticileri',note='Yeni çalışmalarını takip ediyorum.')
        engine.data['people']['201'].update(watching=True,group='Arkadaşlar')
        def members(size):
            return {str(1000+i): {'id':str(1000+i),'username':f'demo.uye{i+1}','name':f'Örnek Üye {i+1}'} for i in range(size)}
        for direction,size in (('followers',1248),('following',420)):
            def scan(count,at,suffix):
                return {'id':f'doc-{direction}-{suffix}','ownerId':'100','direction':direction,
                    'members':members(count),'startedAt':at-180,'finishedAt':at,'complete':True,
                    'status':'complete','pages':(count+49)//50,'expected':count}
            engine.data['lists'][f'100:{direction}']={'snapshots':[scan(size-1,now-86400,'before'),scan(size,now,'after')]}
        engine.data['notificationLog'] = []
        engine.data['events'] = []
        engine.observation_group = 'doc-notification'
        engine.event('profile','@demo.deniz · username','200',{'username':'demo.deniz.eski'},{'username':'demo.deniz'})
        engine.event('profile','@demo.deniz · followers','200',{'followers':1247},{'followers':1250})
        engine.event('profile','@demo.deniz · bio','200',{'bio':'Tasarım ve fotoğraf.'},{'bio':deniz['bio']})
        engine.data['settings'].update(language='tr',theme='light',notifications=True,automatic=True,automaticLists=True,interval=5)
        engine.data['gate'].update(status=200,blockedUntil=0,nextAt=0,manualOnly=False,attempts=18,lastAt=now)
        # Only truthiness is used by snapshot(). No account client is constructed and no save is made.
        engine.data['session'] = {'documentation_example':True}
        state=engine.snapshot()
        state.update(activeAccountId='100',accounts=[{'id':'100','username':'demo.hesabim','active':True}],storagePath='',counterAvailable=False)
        # ASCII JSON makes the producer independent of the Windows console code page.
        print(json.dumps(state,ensure_ascii=True))


if __name__ == '__main__':
    main()
