"""Opt-in, manual story experiments. No social writes or automatic fallback.

Only normalized facts and complete, encrypted media are retained. Account API
responses and expiring CDN URLs never enter the renderer or persisted index.
"""
import base64
import copy
import hashlib
import json
import math
import re
from io import BytesIO
from pathlib import Path

import requests
from PIL import Image
from network import AppFailure, avatar_url
from profile_links import web_url
from storage import StorageFailure, atomic_write, protect

MAX_MEDIA = 16 * 1024 * 1024
MAX_BYTES = 256 * 1024 * 1024
MAX_ITEMS = 200
MAX_INDEX = 2 * 1024 * 1024


def identity(value):
    return type(value) is str and re.fullmatch(r'[1-9][0-9]{0,29}', value) is not None


def source_id(value):
    return str(value) if type(value) in (str, int) and identity(str(value)) else None


def timestamp(value):
    return value if type(value) in (int, float) and math.isfinite(value) and 0 < value < 253402300800 else None


def text(value, limit=2048):
    return value if type(value) is str and len(value) <= limit else None


def collection(item, key, parse, incomplete):
    raw = item.get(key)
    if raw is None:
        return None
    if not isinstance(raw, list) or len(raw) > 200:
        incomplete.append(key)
        return None
    parsed = [parse(entry) if isinstance(entry,dict) else None for entry in raw]
    if any(entry is None for entry in parsed):
        incomplete.append(key)
    # Preserve known entries without pretending that coverage is complete.
    return [entry for entry in parsed if entry is not None]


def mention(entry):
    user = entry.get('user')
    if not isinstance(user, dict):
        return None
    name = user.get('username')
    pk = source_id(user.get('pk'))
    if not pk or not isinstance(name, str) or not re.fullmatch(r'[A-Za-z0-9._]{1,30}', name):
        return None
    return {'id':pk, 'username':name}


def shared(entry):
    pk = source_id(entry.get('media_id'))
    if not pk:
        return None
    code = entry.get('media_code')
    return {'id':pk, 'ownerId':source_id(entry.get('user_id')),
            'code':code if isinstance(code,str) and re.fullmatch(r'[A-Za-z0-9_-]{1,64}',code) else None,
            'type':entry.get('product_type') if entry.get('product_type') in ('feed','clips','story') else None}


def parse(payload, target):
    if not isinstance(payload, dict) or payload.get('status') != 'ok' or 'reel' not in payload:
        raise AppFailure('story_response', 'Hikâye yanıtı okunamadı. Önceki kayıtlar korundu.')
    reel = payload['reel']
    if reel is None:
        return []  # No accessible stories returned; not proof that none were posted.
    if not isinstance(reel,dict) or not isinstance(reel.get('user'),dict) or source_id(reel['user'].get('pk')) != target:
        raise AppFailure('identity', 'Hikâyeyi paylaşan hesap doğrulanamadı.')
    items = reel.get('items')
    if not isinstance(items,list) or len(items) > MAX_ITEMS:
        raise AppFailure('story_response', 'Hikâye yanıtı okunamadı. Önceki kayıtlar korundu.')
    result = []
    seen = set()
    for item in items:
        if not isinstance(item,dict) or not source_id(item.get('pk')):
            raise AppFailure('story_response', 'Hikâye yanıtı okunamadı. Önceki kayıtlar korundu.')
        pk = source_id(item['pk'])
        if pk in seen:
            continue
        seen.add(pk)
        if isinstance(item.get('user'),dict) and source_id(item['user'].get('pk')) != target:
            raise AppFailure('identity', 'Hikâyeyi paylaşan hesap doğrulanamadı.')
        kind = {1:'image',2:'video'}.get(item.get('media_type'),'unsupported') if type(item.get('media_type')) is int else 'unsupported'
        if kind == 'video':
            candidates = item.get('video_versions')
        elif kind == 'image' and isinstance(item.get('image_versions2'),dict):
            candidates = item['image_versions2'].get('candidates')
        else:
            candidates = []
        candidates = candidates if isinstance(candidates,list) else []
        usable = [c for c in candidates if isinstance(c,dict) and avatar_url(c.get('url')) and
                  type(c.get('width')) is int and type(c.get('height')) is int and 0 < c['width'] <= 8192 and 0 < c['height'] <= 8192]
        url = max(usable,key=lambda c:c['width']*c['height'])['url'] if usable else None
        incomplete = []
        links = collection(item,'story_link_stickers',lambda e:web_url((e.get('story_link') or {}).get('url')) if isinstance(e.get('story_link'),dict) else None,incomplete)
        cta = collection(item,'story_cta',lambda e:e,incomplete)
        if cta is not None:
            links = links or []
            for entry in cta:
                for link in entry.get('links',[]) if isinstance(entry.get('links'),list) else []:
                    if isinstance(link,dict) and (value := web_url(link.get('webUri'))):
                        links.append(value)
            links = list(dict.fromkeys(links))[:200]
        result.append({'id':pk,'ownerId':target,'publishedAt':timestamp(item.get('taken_at')),
                       'expiresAt':timestamp(item.get('expiring_at')),'type':kind,
                       'mentions':collection(item,'reel_mentions',mention,incomplete), 'links':links,
                       'hashtags':collection(item,'story_hashtags',lambda e:text((e.get('hashtag') or {}).get('name'),100) if isinstance(e.get('hashtag'),dict) else None,incomplete),
                       'locations':collection(item,'story_locations',lambda e:text((e.get('location') or {}).get('name'),256) if isinstance(e.get('location'),dict) else None,incomplete),
                       'sharedMedia':collection(item,'story_feed_media',shared,incomplete), 'incompleteFields':incomplete,
                       # A mention is never interpreted as the original story author.
                       'resharedFrom':None, 'mediaUrl':url})
    return result


def media_mime(raw, kind):
    if kind == 'video' and len(raw) >= 12 and raw[4:8] == b'ftyp':
        return 'video/mp4'
    if kind == 'image':
        try:
            with Image.open(BytesIO(raw)) as picture:
                if picture.width * picture.height > 40_000_000:
                    raise ValueError('image size')
                picture.verify()
                if picture.format in ('JPEG','PNG','WEBP'):
                    return {'JPEG':'image/jpeg','PNG':'image/png','WEBP':'image/webp'}[picture.format]
        except Exception:
            pass
    raise AppFailure('story_media', 'Hikâye dosyası desteklenmiyor veya tamamlanamadı.')


def download(url, kind, engine):
    if not avatar_url(url):
        raise AppFailure('network_policy', 'İzin verilmeyen ağ hedefi durduruldu.')
    engine.check_cancel()
    # CDN requests also reserve the common queue/budget. No cookies/auth headers.
    engine.before_request()
    with requests.Session() as session:
        session.trust_env = False
        # The API's curl adapter buffers upstream.content even with stream=True.
        # Native requests streaming lets this media owner enforce its byte limit
        # before allocating the complete body. The authenticated API is unchanged.
        with session.get(url, stream=True, allow_redirects=False, timeout=(5,15),
                         headers={'Accept':'video/mp4,image/*','Accept-Encoding':'identity'}, verify=True) as response:
            engine.after_response(response)
            if response.status_code != 200:
                raise AppFailure('story_media', 'Hikâye dosyası desteklenmiyor veya tamamlanamadı.')
            length = response.headers.get('Content-Length')
            if length is not None and (not length.isdigit() or int(length) > MAX_MEDIA):
                raise AppFailure('story_capacity', 'Hikâye arşivi sınırına ulaşıldı. Yer açıp yeniden deneyebilirsin.')
            raw = bytearray()
            for chunk in response.iter_content(65536):
                engine.check_cancel()
                if len(raw) + len(chunk) > MAX_MEDIA:
                    raise AppFailure('story_capacity', 'Hikâye arşivi sınırına ulaşıldı. Yer açıp yeniden deneyebilirsin.')
                raw.extend(chunk)
            if not raw or length is not None and len(raw) != int(length):
                raise AppFailure('story_media', 'Hikâye dosyası desteklenmiyor veya tamamlanamadı.')
    return bytes(raw), media_mime(raw,kind)


class StoryStore:
    def __init__(self, directory, owner):
        if not identity(owner):
            raise AppFailure('account','Kayıtlı hesap bulunamadı.')
        self.directory = Path(directory) / 'stories'
        self.path = self.directory / 'index.vault'
        self.owner = owner

    def load(self):
        if not self.path.exists():
            return {'version':1,'owner':self.owner,'targets':{}}
        try:
            if self.path.stat().st_size > MAX_INDEX:
                raise ValueError('index size')
            value = json.loads(protect(self.path.read_bytes(),decrypt=True))
            if (value.get('version') != 1 or value.get('owner') != self.owner or not isinstance(value.get('targets'),dict) or
                    any(not identity(k) or not isinstance(v,dict) or not isinstance(v.get('items'),dict) for k,v in value['targets'].items()) or
                    sum(len(v['items']) for v in value['targets'].values()) > MAX_ITEMS):
                raise ValueError('story index')
            for target,entry in value['targets'].items():
                for pk,row in entry['items'].items():
                    if not identity(pk) or row.get('id') != pk or row.get('ownerId') != target or row.get('status') not in ('saved','metadata'):
                        raise ValueError('story identity')
            return value
        except Exception as exc:
            raise StorageFailure('Hikâye arşivi okunamadı. Mevcut dosyalar korundu.') from exc

    def save(self, value):
        raw = json.dumps(value,ensure_ascii=False,allow_nan=False,separators=(',',':')).encode()
        if len(raw) > MAX_INDEX-4096:
            raise StorageFailure('Hikâye arşivi sınırına ulaşıldı. Yer açıp yeniden deneyebilirsin.')
        atomic_write(self.path,protect(raw))

    def asset_path(self, pk):
        if not identity(pk):
            raise AppFailure('input','Geçersiz hikâye isteği.')
        return self.directory / (pk+'.media.vault')

    def prefix(self, target, pk):
        return ('ITS1/'+self.owner+'/'+target+'/'+pk+'\0').encode()

    def contains(self, target, pk):
        row = self.load()['targets'].get(target,{}).get('items',{}).get(pk)
        return bool(row and row['status']=='saved' and self.asset_path(pk).is_file())

    def record(self, target, item, now, media=None):
        value = self.load()
        entry = value['targets'].setdefault(target,{'items':{},'checkedAt':None})
        if item.get('ownerId') != target or not identity(target) or not identity(item.get('id')):
            raise AppFailure('identity','Hikâyeyi paylaşan hesap doğrulanamadı.')
        if item['id'] not in entry['items'] and sum(len(e['items']) for e in value['targets'].values()) >= MAX_ITEMS:
            raise AppFailure('story_capacity', 'Hikâye arşivi sınırına ulaşıldı. Yer açıp yeniden deneyebilirsin.')
        row = {k:copy.deepcopy(v) for k,v in item.items() if k != 'mediaUrl'}
        row.update(capturedAt=now,status='metadata',bytes=None,mime=None,digest=None)
        asset = self.asset_path(item['id'])
        existed = asset.exists()
        if media:
            raw,mime = media
            if len(raw)>MAX_MEDIA or media_mime(raw,item['type']) != mime:
                raise AppFailure('story_media', 'Hikâye dosyası desteklenmiyor veya tamamlanamadı.')
            encrypted = protect(self.prefix(target,item['id'])+raw)
            used = sum(p.stat().st_size for p in self.directory.glob('*.media.vault'))
            if used + len(encrypted) > MAX_BYTES:
                raise AppFailure('story_capacity', 'Hikâye arşivi sınırına ulaşıldı. Yer açıp yeniden deneyebilirsin.')
            if existed:
                raise StorageFailure('Hikâye dosyası zaten var. Mevcut kayıt korundu.')
            atomic_write(asset,encrypted)
            row.update(status='saved',bytes=len(raw),mime=mime,digest=hashlib.sha256(raw).hexdigest())
        entry['items'][item['id']] = row
        try:
            self.save(value)
        except Exception:
            if media and not existed:
                asset.unlink(missing_ok=True)
            raise

    def checked(self,target,now,summary):
        value = self.load()
        entry = value['targets'].setdefault(target,{'items':{}})
        entry.update(checkedAt=now,summary=summary)
        self.save(value)

    def view(self,target):
        entry = self.load()['targets'].get(target,{})
        return {'accountId':self.owner,'id':target,'checkedAt':entry.get('checkedAt'),'summary':entry.get('summary'),
                'items':sorted(entry.get('items',{}).values(),key=lambda r:r.get('publishedAt') or r['capturedAt'],reverse=True),
                'maxItems':MAX_ITEMS,'maxBytes':MAX_BYTES,'maxMediaBytes':MAX_MEDIA}

    def media(self,target,pk):
        row = self.load()['targets'].get(target,{}).get('items',{}).get(pk)
        path = self.asset_path(pk)
        if not row or row['status'] != 'saved' or not path.is_file() or path.stat().st_size > MAX_MEDIA+4096:
            raise AppFailure('story_media','Hikâye dosyası desteklenmiyor veya tamamlanamadı.')
        raw = protect(path.read_bytes(),decrypt=True)
        prefix = self.prefix(target,pk)
        if not raw.startswith(prefix):
            raise StorageFailure('Hikâyeyi paylaşan hesap doğrulanamadı.')
        raw = raw[len(prefix):]
        if len(raw) != row['bytes'] or hashlib.sha256(raw).hexdigest() != row['digest'] or media_mime(raw,row['type']) != row['mime']:
            raise StorageFailure('Hikâye dosyası desteklenmiyor veya tamamlanamadı.')
        return {'accountId':self.owner,'id':target,'storyId':pk,'mime':row['mime'],'base64':base64.b64encode(raw).decode('ascii')}

    def clear(self,target):
        value = self.load()
        entry = value['targets'].get(target,{})
        # Keep metadata while cleanup fails, so retry still owns the files.
        for pk in entry.get('items',{}):
            self.asset_path(pk).unlink(missing_ok=True)
        value['targets'].pop(target,None)
        self.save(value)

    def clear_all(self):
        # Account deletion also owns encrypted incomplete/orphan files here.
        for path in self.directory.glob('*.media.vault'):
            if identity(path.name.removesuffix('.media.vault')):
                path.unlink()
        self.path.unlink(missing_ok=True)
