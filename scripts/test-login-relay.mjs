// Isolated browser shim: executes the shipped bridge with synthetic messages, never a network.
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import test from 'node:test';

const source=await readFile(new URL('../app/src/main/assets/web_login_relay.js',import.meta.url),'utf8');
function browser(path='/accounts/login/') {
  const messages=[],fallback=[];
  class XHR extends EventTarget {
    open(){} setRequestHeader(){} abort(){} getResponseHeader(){return null;} getAllResponseHeaders(){return '';}
    send(){fallback.push('xhr');}
  }
  const timers=new Set();
  const bridge={postMessage(raw){messages.push(JSON.parse(raw));}};
  const window={XMLHttpRequest:XHR,trackerLoginRelay:bridge,fetch(...args){fallback.push('fetch');return Promise.resolve(new Response('{}'));}};
  window.top=window;
  vm.runInNewContext(source,{window,location:{origin:'https://www.instagram.com',href:'https://www.instagram.com'+path,pathname:path},
    URL,Request,Response,URLSearchParams,DOMException,Event,
    setTimeout(fn,ms){const timer=setTimeout(fn,ms);timers.add(timer);return timer;},
    clearTimeout(timer){clearTimeout(timer);timers.delete(timer);}});
  function reply(message,error=true){bridge.onmessage({data:JSON.stringify({id:message.id,error,...(!error?{status:200,body:'{}',headers:{}}:{})})});}
  return {window,messages,fallback,reply,close(){for(const timer of timers)clearTimeout(timer);}};
}
async function tick(){await new Promise(resolve=>setImmediate(resolve));}

test('all known same-origin GraphQL paths are delivered with their original path',async()=>{
  const b=browser('/auth_platform/synthetic/');
  try {
    const paths=['/api/graphql','/api/graphql/','/graphql/query/','/graphql_www'];
    const pending=paths.map(path=>b.window.fetch('https://www.instagram.com'+path,{method:'POST',body:'doc_id=123'}).catch(()=>null));
    await tick();assert.equal(b.messages.length,4);assert.deepEqual(b.messages.map(m=>m.path),paths);assert.equal(b.fallback.length,0);
    b.messages.forEach(m=>b.reply(m));await Promise.all(pending);
  } finally {b.close();}
});
test('security observation is bounded to four concurrent messages',async()=>{
  const b=browser('/auth_platform/synthetic/');
  try {
    const pending=Array.from({length:5},()=>b.window.fetch('https://www.instagram.com/api/graphql',{method:'POST',body:'doc_id=123'}).catch(()=>null));
    await tick();assert.equal(b.messages.length,4);b.messages.forEach(m=>b.reply(m));await Promise.all(pending);
  } finally {b.close();}
});
test('initial login still admits only one pending bridge message',async()=>{
  const b=browser();
  try {
    const pending=Array.from({length:2},()=>b.window.fetch('https://www.instagram.com/api/graphql',{method:'POST',body:'synthetic'}).catch(()=>null));
    await tick();assert.equal(b.messages.length,1);b.reply(b.messages[0]);await Promise.all(pending);
  } finally {b.close();}
});
test('foreign URLs, queries, fragments and GET never reach the native bridge',async()=>{
  const b=browser('/auth_platform/synthetic/');
  try {
    for(const url of ['https://other.invalid/api/graphql','https://www.instagram.com/api/graphql?x=1','https://www.instagram.com/api/graphql#x'])
      await b.window.fetch(url,{method:'POST',body:'synthetic'});
    await b.window.fetch('https://www.instagram.com/api/graphql');
    assert.equal(b.messages.length,0);assert.equal(b.fallback.length,4);
    // Fallback here is the browser shim. Real WebView resource policy independently denies unapproved traffic.
  } finally {b.close();}
});
test('XHR keeps its actual GraphQL path and response lifecycle',async()=>{
  const b=browser('/auth_platform/synthetic/');
  try {
    const xhr=new b.window.XMLHttpRequest();let ended=0;xhr.addEventListener('loadend',()=>ended++);
    xhr.open('POST','https://www.instagram.com/graphql/query/');xhr.send('doc_id=123');
    assert.equal(b.messages[0].path,'/graphql/query/');b.reply(b.messages[0],false);await tick();
    assert.equal(xhr.responseURL,'https://www.instagram.com/graphql/query/');assert.equal(xhr.status,200);assert.equal(ended,1);
    assert.equal(b.fallback.length,0);
  } finally {b.close();}
});
