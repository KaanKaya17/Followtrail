"""Opt-in visual history, independent from rolling count observations."""
import base64
import copy
import hashlib
import re

from network import AppFailure

MAX_ENTRIES = 200
MAX_IMAGES = 24
MAX_IMAGE_BYTES = 64 * 1024
MAX_TOTAL_BYTES = 8 * 1024 * 1024
PREFIX = 'data:image/jpeg;base64,'


def initialize(data):
    data.setdefault('wayback', {'profiles': {}, 'images': {}})


def image_bytes(value):
    if not isinstance(value, str) or not value.startswith(PREFIX) or len(value) > len(PREFIX) + 4 * ((MAX_IMAGE_BYTES + 2) // 3):
        return None
    try:
        raw = base64.b64decode(value[len(PREFIX):], validate=True)
    except (ValueError, TypeError):
        return None
    return raw if 0 < len(raw) <= MAX_IMAGE_BYTES and raw.startswith(b'\xff\xd8') else None


def collect(data):
    archive = data['wayback']
    used = {entry['image'] for record in archive['profiles'].values() for entry in record['entries'] if entry.get('image')}
    archive['images'] = {key: value for key, value in archive['images'].items() if key in used}


def metadata(data):
    return {identity: {'enabled': record['enabled'], 'full': record['full'], 'entries': len(record['entries']),
                       'images': len({e['image'] for e in record['entries'] if e.get('image')})}
            for identity, record in data['wayback']['profiles'].items()}


def view(data, identity):
    record = data['wayback']['profiles'].get(identity, {'enabled': False, 'full': False, 'entries': []})
    keys = {e['image'] for e in record['entries'] if e.get('image')}
    images = {key: data['wayback']['images'][key] for key in keys}
    return {'id': identity, **copy.deepcopy(record), 'images': images,
            'bytes': sum(len(image_bytes(value) or b'') for value in images.values()),
            'maxEntries': MAX_ENTRIES, 'maxImages': MAX_IMAGES}


def record(data, profile):
    archive = data['wayback']
    target = archive['profiles'].get(profile['id'])
    if not target or not target['enabled']:
        return
    entries = target['entries']
    previous = entries[-1] if entries else {}
    bio = profile.get('bio') if isinstance(profile.get('bio'), str) else previous.get('bio')
    picture = profile.get('picture') if isinstance(profile.get('picture'), str) else previous.get('picture')
    # A failed thumbnail read never makes an older image look like the new one.
    image = previous.get('image') if picture == previous.get('picture') else None
    raw = image_bytes(profile.get('avatar'))
    pending = None
    if raw is not None and picture != 'anonymous':
        key = hashlib.sha256(raw).hexdigest()
        known = {e['image'] for e in entries if e.get('image')}
        total = sum(len(value) for value in archive['images'].values()) * 3 // 4
        if key in archive['images'] or (len(known) < MAX_IMAGES and total + len(raw) <= MAX_TOTAL_BYTES):
            image = key
            if key not in archive['images']: pending = (key, profile['avatar'])
        else:
            target['full'] = True
    if picture == 'anonymous': image = None
    state = {'bio': bio, 'picture': picture, 'image': image}
    if not entries or any(previous.get(key) != value for key, value in state.items()):
        if len(entries) >= MAX_ENTRIES:
            target['full'] = True
            return
        if pending: archive['images'][pending[0]] = pending[1]
        entries.append({'at': profile['observedAt'], 'lastSeen': profile['observedAt'], **state})
    else:
        previous['lastSeen'] = max(previous['lastSeen'], profile['observedAt'])


def configure(data, profile, args):
    identity = profile['id']
    if set(args) - {'id', 'enabled', 'clear'} or ('enabled' in args and type(args['enabled']) is not bool) or ('clear' in args and args['clear'] is not True):
        raise AppFailure('input', 'Arşiv tercihi geçersiz.')
    if args.get('clear'):
        # Clear also stops capture; the next ordinary refresh cannot recreate it.
        data['wayback']['profiles'].pop(identity, None)
        collect(data)
    elif 'enabled' in args:
        profiles = data['wayback']['profiles']
        if identity not in profiles and len(profiles) >= 201:
            raise AppFailure('capacity', 'Arşiv sınırına ulaşıldı. Eski kayıtlar korunur; yer açmak için arşivi temizleyebilirsin.')
        target = profiles.setdefault(identity, {'enabled': False, 'full': False, 'entries': []})
        target['enabled'] = args['enabled']
        if target['enabled'] and profile.get('observedAt'):
            record(data, profile)


def validate(data):
    archive = data['wayback']
    if not isinstance(archive, dict) or set(archive) != {'profiles', 'images'}:
        raise ValueError('wayback')
    if not isinstance(archive['profiles'], dict) or len(archive['profiles']) > 201 or not isinstance(archive['images'], dict):
        raise ValueError('wayback capacity')
    total = 0
    for key, value in archive['images'].items():
        raw = image_bytes(value)
        if raw is None or key != hashlib.sha256(raw).hexdigest(): raise ValueError('wayback image')
        total += len(raw)
    if total > MAX_TOTAL_BYTES: raise ValueError('wayback image capacity')
    for identity, target in archive['profiles'].items():
        if not re.fullmatch(r'[1-9][0-9]{0,29}', identity) or not isinstance(target, dict) or type(target.get('enabled')) is not bool or type(target.get('full')) is not bool:
            raise ValueError('wayback profile')
        entries = target.get('entries')
        if not isinstance(entries, list) or len(entries) > MAX_ENTRIES: raise ValueError('wayback entries')
        previous = 0
        for entry in entries:
            at, last = entry.get('at'), entry.get('lastSeen')
            if type(at) not in (int, float) or type(last) not in (int, float) or not 0 <= previous <= at <= last < 1e15:
                raise ValueError('wayback time')
            previous = at
            if any(entry.get(key) is not None and not isinstance(entry[key], str) for key in ('bio', 'picture', 'image')):
                raise ValueError('wayback text')
            if entry.get('image') is not None and entry['image'] not in archive['images']: raise ValueError('wayback image reference')
        if len({e['image'] for e in entries if e.get('image')}) > MAX_IMAGES: raise ValueError('wayback profile images')
