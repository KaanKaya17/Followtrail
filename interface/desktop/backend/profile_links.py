"""Profile-provided links. Parsing never visits sites or fetches favicons."""
from urllib.parse import urlsplit
import re

MAX_SITES = 5
MAX_LINKS = MAX_SITES + 1  # The native Threads badge is separate from the website links.

def threads_name(value):
    valid=web_url(value)
    if not valid:return None
    parsed=urlsplit(valid)
    if parsed.scheme!='https' or parsed.hostname not in ('threads.com','www.threads.com','threads.net','www.threads.net') or parsed.port not in (None,443):return None
    match=re.fullmatch(r'/@([A-Za-z0-9._]{1,30})/?',parsed.path)
    return match.group(1) if match else None

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
    values=raw[:MAX_SITES] if isinstance(raw,list) else []
    values=[*values,{'url':user.get('external_url')}]
    result=[];seen=set()
    for item in values:
        url=web_url(item.get('url')) if isinstance(item,dict) else None
        if not url or url in seen:continue
        seen.add(url);result.append({'url':url})
    sites=result[:MAX_SITES]
    explicit=next((item['url'] for item in result if threads_name(item['url'])),None)
    label=user.get('text_post_app_badge_label')
    name=label.removeprefix('@') if isinstance(label,str) else None
    badge=('https://www.threads.com/@'+name if user.get('show_text_post_app_badge') is True and
           name and re.fullmatch(r'[A-Za-z0-9._]{1,30}',name) else None)
    linked=explicit or badge
    if linked and all(item['url']!=linked for item in sites):sites.append({'url':linked})
    return sites

def validate(items):
    return isinstance(items,list) and len(items)<=MAX_LINKS and all(isinstance(item,dict) and set(item)=={'url'} and web_url(item['url']) for item in items)
