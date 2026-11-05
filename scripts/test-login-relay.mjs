// Isolated browser shim: executes the shipped bridge with synthetic messages, never a network.
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import test from 'node:test';

const captchaIdentity=await readFile(new URL('../app/src/main/java/dev/kaankaya/instagramtracker/AuthPlatformCaptchaContent.kt',import.meta.url),'utf8');
const captchaDocument=captchaIdentity.match(/const val DOCUMENT = "([0-9]+)"/)[1];
const captchaOperation=captchaIdentity.match(/const val OPERATION = "([A-Za-z0-9_]+)"/)[1];
const captchaInspector=(await readFile(new URL('../app/src/main/assets/web_security_schema.js',import.meta.url),'utf8'))
  .replaceAll('__DOCUMENT__',captchaDocument).replaceAll('__OPERATION__',captchaOperation).replaceAll('__KIND__','query');
const source=(await readFile(new URL('../app/src/main/assets/web_login_relay.js',import.meta.url),'utf8'))
  .replaceAll('__CAPTCHA_SCHEMA__',captchaInspector).replaceAll('__CAPTCHA_DOCUMENT__',captchaDocument).replaceAll('__CAPTCHA_OPERATION__',captchaOperation);
const schemaSource=(await readFile(new URL('../app/src/main/assets/web_security_schema.js',import.meta.url),'utf8'))
  .replaceAll('__DOCUMENT__','34414353874878894').replaceAll('__OPERATION__','AuthPlatformCodeEntryViewQuery').replaceAll('__KIND__','query');
test('security schema reads static names and never argument values',()=>{
  const argument={name:'context',get defaultValue(){throw Error('Private value was inspected');}};
  const doc={params:{id:'34414353874878894',name:'AuthPlatformCodeEntryViewQuery',operationKind:'query'},
    operation:{argumentDefinitions:[argument],selections:[{kind:'LinkedField',name:'security_view'}]}};
  const result=JSON.parse(JSON.stringify(vm.runInNewContext(schemaSource,{require:()=>doc})));
  assert.deepEqual(result.arguments,['context']);assert.deepEqual(result.rootFields,['security_view']);assert.equal(result.incomplete,false);
});
test('unknown module or mutation cannot be reported as a known query schema',()=>{
  for(const requireFn of [()=>{throw Error('unknown module');},()=>({params:{id:'34414353874878894',name:'AuthPlatformCodeEntryViewQuery',operationKind:'mutation'}})])
    assert.equal(vm.runInNewContext(schemaSource,{require:requireFn}).available,false);
});
test('observed submit-code schema reads only static mutation names without input values',async()=>{
  const raw=await readFile(new URL('../app/src/main/assets/web_security_schema.js',import.meta.url),'utf8');
  const script=raw.replaceAll('__DOCUMENT__','25017097917894476').replaceAll('__OPERATION__','useAuthPlatformSubmitCodeMutation').replaceAll('__KIND__','mutation');
  const argument={name:'input',get defaultValue(){throw Error('Private code was inspected');}};
  const doc={params:{id:'25017097917894476',name:'useAuthPlatformSubmitCodeMutation',operationKind:'mutation'},
    operation:{argumentDefinitions:[argument],selections:[{kind:'LinkedField',name:'synthetic_verification'}]}};
  const result=JSON.parse(JSON.stringify(vm.runInNewContext(script,{require:()=>doc})));
  assert.deepEqual(result.arguments,['input']);assert.deepEqual(result.rootFields,['synthetic_verification']);assert.equal(result.kind,'mutation');
  doc.params.id='123';assert.equal(vm.runInNewContext(script,{require:()=>doc}).available,false);
});
test('partial schema coverage stays explicit and bounded',()=>{
  const doc={params:{id:'34414353874878894',name:'AuthPlatformCodeEntryViewQuery',operationKind:'query'},
    operation:{argumentDefinitions:Array.from({length:12},(_,i)=>({name:'argument'+i})),selections:[{kind:'Condition',name:'condition'}]}};
  const result=vm.runInNewContext(schemaSource,{require:()=>doc});assert.equal(result.arguments.length,8);assert.equal(result.rootFields.length,0);assert.equal(result.incomplete,true);
});
function browser(path='/accounts/login/',requireModule=undefined) {
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
    URL,Request,Response,URLSearchParams,DOMException,Event,require:requireModule,
    setTimeout(fn,ms){const timer=setTimeout(fn,ms);timers.add(timer);return timer;},
    clearTimeout(timer){clearTimeout(timer);timers.delete(timer);}});
  function reply(message,error=true){bridge.onmessage({data:JSON.stringify({id:message.id,error,...(!error?{status:200,body:'{}',headers:{}}:{})})});}
  return {window,messages,fallback,reply,close(){for(const timer of timers)clearTimeout(timer);}};
}
async function tick(){await new Promise(resolve=>setImmediate(resolve));}

test('CAPTCHA module evidence is lazy, query-specific and contains no argument defaults',async()=>{
  let loads=0;
  const b=browser('/auth_platform/synthetic/',name=>{
    assert.equal(name,captchaOperation+'.graphql');loads++;
    return {params:{id:captchaDocument,name:captchaOperation,operationKind:'query'},operation:{
      argumentDefinitions:[{name:'apc',get defaultValue(){throw Error('Private context read');}}],
      selections:[{kind:'LinkedField',name:'xfb_auth_platform_synthetic_recaptcha_content'}]}};
  });
  try {
    assert.equal(loads,0);
    const pending=b.window.fetch('https://www.instagram.com/api/graphql',{method:'POST',
      body:new URLSearchParams({doc_id:captchaDocument,fb_api_req_friendly_name:captchaOperation,
        variables:JSON.stringify({apc:'private-context'})}).toString()});
    await tick();assert.equal(loads,1);assert.equal(b.messages.length,1);
    const schema=b.messages[0].securitySchema;
    assert.equal(schema.kind,'query');assert.deepEqual(schema.arguments,['apc']);
    assert.equal(JSON.stringify(schema).includes('private-context'),false);
    b.reply(b.messages[0],false);await pending;
  }finally{b.close();}
});

test('A CAPTCHA module reported as a mutation supplies no usable query evidence',async()=>{
  const b=browser('/auth_platform/synthetic/',()=>({params:{id:captchaDocument,name:captchaOperation,operationKind:'mutation'}}));
  try {
    const pending=b.window.fetch('https://www.instagram.com/graphql/query/',{method:'POST',
      body:new URLSearchParams({doc_id:captchaDocument,fb_api_req_friendly_name:captchaOperation}).toString()}).catch(()=>null);
    await tick();assert.equal(b.messages[0].securitySchema.available,false);
    b.reply(b.messages[0]);await pending;
  }finally{b.close();}
});

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
