"""Profile-provided links. Parsing never visits sites or fetches favicons."""
from urllib.parse import urlsplit
import re

def web_url(value):
    if not isinstance(value,str) or len(value)>4096 or re.search(r'[\x00-\x20\x7f\\]',value):return None
    try:
        url=urlsplit(value)
        if url.scheme in ('http','https') and url.hostname and '.' in url.hostname and not url.username and not url.password:
            url.port
            return value
    except ValueError:pass
    return None

def parse(user):
    raw=user.get('bio_links')
    values=raw[:5] if isinstance(raw,list) else []
    values=[*values,{'url':user.get('external_url')}]
    result=[];seen=set()
    for item in values:
        url=web_url(item.get('url')) if isinstance(item,dict) else None
        if not url or url in seen:continue
        seen.add(url);result.append({'url':url})
        if len(result)==5:break
    return result

def validate(items):
    return isinstance(items,list) and len(items)<=5 and all(isinstance(item,dict) and set(item)=={'url'} and web_url(item['url']) for item in items)
