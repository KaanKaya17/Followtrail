/** Exercise the shipped browser contract, bounded response and privacy without a real session. */
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
const script=await readFile(new URL('../app/src/main/assets/web_connection_probe.js',import.meta.url),'utf8');
const slot='__tracker_connection_'+'a'.repeat(32);
const valid={status:'ok',user:{pk:'123',username:'synthetic',follower_count:0,following_count:7,biography:'DO-NOT-EXPORT',email:'DO-NOT-EXPORT'}};
let cases=0;
async function run({body=valid,status=200,origin='https://www.instagram.com',owner='123',path='/api/v1/users/123/info/',bytes,retry=null,hold=false}={}) {
 const requests=[];let timer,aborted=false;
 const fetch=async(url,options)=>{
  requests.push({url,options});options.signal.addEventListener('abort',()=>{aborted=true;});
  if(hold)return new Promise((_,reject)=>options.signal.addEventListener('abort',()=>reject(Error('aborted'))));
  const data=bytes || new TextEncoder().encode(JSON.stringify(body));
  return {status,headers:new Headers(retry?{'Retry-After':retry}:{}),body:new ReadableStream({start(c){c.enqueue(data);c.close();}})};
 };
 const window={fetch,AbortController,TextDecoder};
 const context=vm.createContext({window,location:{origin},fetch,AbortController,TextDecoder,setTimeout(callback){timer=callback;return 1;},clearTimeout(){}});
 const start=vm.runInContext(script+'('+JSON.stringify({slot,path,owner,headers:{Accept:'application/json','X-IG-App-ID':'936619743392459','X-IG-WWW-Claim':'0'}})+')',context);
 return {start,window,requests,aborted:()=>aborted,timeout:()=>timer(),async done(){for(let i=0;i<30;i++){await new Promise(resolve=>setImmediate(resolve));if(window[slot]?.stage==='done')return window[slot];}throw Error('Probe did not finish');}};
}
{
 const p=await run();const result=await p.done();assert.equal(p.start,true);assert.equal(result.reason,'ok');
 assert.equal(result.payload.user.follower_count,0);assert.equal(p.requests.length,1);
 assert.equal(JSON.stringify(result).includes('DO-NOT-EXPORT'),false);
 assert.equal(p.requests[0].url,'/api/v1/users/123/info/');assert.equal(p.requests[0].options.credentials,'same-origin');
 assert.equal(p.requests[0].options.redirect,'error');assert.equal(p.requests[0].options.headers.Cookie,undefined);cases++;
}
for(const options of [{origin:'https://evil.invalid'},{path:'/api/v1/users/456/info/'},{path:'https://evil.invalid/'},{owner:'123/../456'}]) {
 const p=await run(options);assert.equal(p.start,false);assert.equal(p.requests.length,0);cases++;
}
for(const status of [401,403,404,429]) {
 const p=await run({status,retry:'7200'});const result=await p.done();assert.equal(result.status,status);
 assert.equal(result.reason,{401:'login_required',403:'verification',404:'not_found',429:'limited'}[status]);
 assert.equal(p.requests.length,1);assert.equal(result.payload,undefined);cases++;
}
for(const message of ['feedback_required','please_wait_a_few_minutes','challenge_required','login_required']) {
 const p=await run({body:{message,private:'DO-NOT-EXPORT'}});const result=await p.done();assert.notEqual(result.reason,'ok');assert.equal(JSON.stringify(result).includes('DO-NOT-EXPORT'),false);cases++;
}
for(const mutate of [u=>delete u.follower_count,u=>u.following_count='7',u=>u.pk=9007199254740992,u=>u.username='<script>',u=>u.follower_count=-1]) {
 const body=structuredClone(valid);mutate(body.user);assert.equal((await(await run({body})).done()).reason,'invalid');cases++;
}
{
 const p=await run({bytes:new Uint8Array(1048577)});assert.equal((await p.done()).reason,'oversized');cases++;
}
for(const kind of ['cancel','timeout']) {
 const p=await run({hold:true});if(kind==='cancel')p.window[slot].cancel();else p.timeout();
 assert.equal((await p.done()).reason,kind==='cancel'?'cancelled':'timeout');assert.equal(p.aborted(),true);cases++;
}
assert.equal(script.includes('document.cookie'),false);
console.log(`Matched web connection probe: ${cases} offline cases passed; no network.`);
