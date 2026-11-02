"""Profile input and read-only search response boundary; no account selection by guesswork."""
import re
from urllib.parse import urlsplit
from network import AppFailure, parse_page

RESERVED = {'accounts', 'explore', 'reels', 'reel', 'stories', 'direct', 'p'}

def username(value):
    if not isinstance(value,str) or len(value)>2048:
        raise AppFailure('input','Geçerli bir Instagram kullanıcı adı gir.')
    name=value.strip()
    if name.startswith('https://'):
        try:
            url=urlsplit(name)
            if (url.hostname not in ('instagram.com','www.instagram.com') or url.username or url.password or
                    url.port not in (None,443) or '%' in url.path):
                raise ValueError()
            name=url.path.strip('/')
        except ValueError:
            raise AppFailure('input','Geçerli bir Instagram kullanıcı adı gir.')
    else:
        name=name.removeprefix('@')
    if not re.fullmatch(r'[A-Za-z0-9._]{1,30}',name) or name.lower() in RESERVED:
        raise AppFailure('input','Geçerli bir Instagram kullanıcı adı gir.')
    return name.lower()

def query(value):
    if not isinstance(value,str):
        raise AppFailure('input','Arama için 2–100 karakter yaz.')
    value=value.strip()
    if value.startswith('https://') or value.startswith('@'):
        value=username(value)
    if not 2<=len(value)<=100 or any(ord(c)<32 for c in value):
        raise AppFailure('input','Arama için 2–100 karakter yaz.')
    return value

def results(payload):
    # The same owner validates identities and display names for lists and search.
    members=parse_page(payload).members
    return list(members.values())[:20]
