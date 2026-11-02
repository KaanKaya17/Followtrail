"""One explicit, cookie-free Threads profile-page check. Never infer absence from a login wall."""
import json
import re
import time
from html.parser import HTMLParser
from urllib.parse import urlsplit
import requests

MAX_BYTES=2*1024*1024

def username(value):
    if not isinstance(value,str):return None
    try:
        url=urlsplit(value)
        if url.scheme!='https' or url.hostname not in ('threads.com','www.threads.com','threads.net','www.threads.net') or url.username is not None or url.password is not None or url.port not in (None,443):return None
        match=re.fullmatch(r'/@([A-Za-z0-9._]{1,30})/?',url.path)
        return match[1] if match else None
    except ValueError:return None

class Page(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True);self.documents=[];self.script=None
    def handle_starttag(self,tag,attrs):
        if tag=='script' and dict(attrs).get('type') in ('application/json','application/ld+json'):self.script=[]
    def handle_data(self,value):
        if self.script is not None:self.script.append(value)
    def handle_endtag(self,tag):
        if tag=='script' and self.script is not None:self.documents.append(''.join(self.script));self.script=None

def parse(body,name):
    page=Page()
    try:page.feed(body.decode('utf-8',errors='replace'))
    except (ValueError,RecursionError):return False
    examined=0
    for source in page.documents:
        try:stack=[json.loads(source)]
        except (ValueError,RecursionError):continue
        while stack:
            examined+=1
            if examined>30000:return False
            node=stack.pop()
            if isinstance(node,dict):
                if node.get('@type')=='ProfilePage':
                    person=node.get('mainEntity')
                    if isinstance(person,dict):
                        found=username(person.get('url'))
                        alternate=person.get('alternateName')
                        if found and found.casefold()==name.casefold() and isinstance(alternate,str) and alternate.lstrip('@').casefold()==name.casefold():return True
                stack.extend(v for v in node.values() if isinstance(v,(dict,list)))
            elif isinstance(node,list):stack.extend(v for v in node if isinstance(v,(dict,list)))
    return False

def probe(engine,url):
    name=username(url)
    if not name:raise ValueError('Invalid Threads profile link')
    target='https://www.threads.com/@'+name
    result={'url':target,'username':name,'status':'unknown','reason':'unrecognized','http':None,'checkedAt':engine.clock()}
    engine.before_request();result['checkedAt']=engine.clock()
    try:
        with requests.Session() as session:
            session.trust_env=False;session.cookies.clear()
            session.mount('https://',requests.adapters.HTTPAdapter(max_retries=0))
            request=session.prepare_request(requests.Request('GET',target,headers={'Accept':'text/html','User-Agent':'InstagramTracker (Threads profile check)'}))
            if any(key.lower() in ('cookie','authorization','proxy-authorization') for key in request.headers):raise ValueError('Unexpected authentication')
            started=time.monotonic()
            with session.send(request,timeout=(5,10),allow_redirects=False,stream=True,verify=True,proxies={}) as response:
                engine.check_cancel();result['http']=response.status_code
                engine.data['gate']['status']=response.status_code;engine.save()
                if response.status_code==429:
                    engine.limited(response.headers.get('Retry-After'));return {**result,'reason':'limited'}
                if response.status_code in (404,410):return {**result,'status':'not_found','reason':'http_not_found'}
                if response.status_code!=200:return {**result,'reason':'access'}
                if 'text/html' not in response.headers.get('Content-Type','').lower():return {**result,'reason':'content_type'}
                body=bytearray()
                for chunk in response.iter_content(65536):
                    engine.check_cancel()
                    if len(body)+len(chunk)>MAX_BYTES or time.monotonic()-started>15:return {**result,'reason':'body_limit'}
                    body.extend(chunk)
                return {**result,'status':'visible' if parse(body,name) else 'unknown'}
    except requests.RequestException:return {**result,'reason':'connection'}
