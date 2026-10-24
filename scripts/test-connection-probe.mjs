/** Execute the shipped browser script with owned streams and a fake fetch. No network or account. */
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
const script=await readFile(new URL('../app/src/main/assets/connection_probe.js',import.meta.url),'utf8');
const valid={status:'ok',data:{user:{id:'123',username:'synthetic',edge_followed_by:{count:0},edge_follow:{count:7}}}};
let passed=0;
async function run({body=valid,status=200,origin='https://www.instagram.com',username='synthetic',bytes,retry=null,hold=false}={}) {
 const requests=[];let timer,aborted=false;
 const fetch=async(url,options)=>{
  requests.push({url,options});options.signal.addEventListener('abort',()=>{aborted=true;});
  if(hold) return new Promise((_,reject)=>options.signal.addEventListener('abort',()=>reject(Object.assign(Error(),{name:'AbortError'}))));
  return {status,headers:{get:key=>key==='Retry-After'?retry:null},body:new ReadableStream({start(controller){
   controller.enqueue(bytes??new TextEncoder().encode(JSON.stringify(body)));controller.close();
  }})};
 };
 const window={fetch,AbortController,TextDecoder};
 const context=vm.createContext({window,location:{origin},fetch,AbortController,TextDecoder,
  setTimeout:fn=>{timer=fn;return 1;},clearTimeout:()=>{},encodeURIComponent});
 const start=vm.runInContext(script,context)({slot:'owned_test',origin:'https://www.instagram.com',username});
 async function done(){for(let i=0;i<100;i++) {if(window.owned_test?.stage==='done')return window.owned_test;await new Promise(setImmediate);}throw Error('Probe did not settle');}
 return {start,requests,window,done,timeout:()=>timer(),aborted:()=>aborted};
}
{
 const p=await run();assert.equal(p.start,true);const result=await p.done();
 assert.equal(p.requests.length,1);assert.equal(result.payload.data.user.edge_followed_by.count,0);
 assert.equal(result.payload.data.user.edge_follow.count,7);assert.equal(result.payload.data.user.edge_owner_to_timeline_media,undefined);
 assert.equal(p.requests[0].options.credentials,'same-origin');assert.equal(p.requests[0].options.redirect,'error');
 assert.equal(p.requests[0].url,'/api/v1/users/web_profile_info/?username=synthetic');passed++;
}
for(const options of [{origin:'https://evil.invalid'},{username:'x;fetch(1)'}]){
 const p=await run(options);assert.equal(p.start,false);assert.equal(p.requests.length,0);passed++;
}
for(const mutate of [u=>delete u.edge_followed_by,u=>u.edge_follow.count='7',u=>u.username='other',u=>u.id=9007199254740992,u=>u.edge_follow.count=-1]){
 const body=structuredClone(valid);mutate(body.data.user);const p=await run({body});assert.equal((await p.done()).reason,'invalid');passed++;
}
{
 const p=await run({status:429,retry:'7200'});const result=await p.done();
 assert.equal(result.reason,'limited');assert.equal(result.retryAfter,'7200');assert.equal(p.requests.length,1);passed++;
}
{
 const p=await run({body:{message:'feedback_required',private_value:'not-to-return'}});const r=await p.done();
 assert.equal(r.reason,'feedback');assert.equal(JSON.stringify(r).includes('private_value'),false);passed++;
}
{
 const p=await run({bytes:new Uint8Array(1048577)});assert.equal((await p.done()).reason,'oversized');passed++;
}
for(const mode of ['cancel','timeout']){
 const p=await run({hold:true});if(mode==='cancel')p.window.owned_test.cancel();else p.timeout();
 assert.equal((await p.done()).reason,mode==='cancel'?'cancelled':'timeout');assert.equal(p.aborted(),true);passed++;
}
assert.equal(script.includes('document.cookie'),false);
console.log(`Browser probe: ${passed} offline cases passed; no network.`);
