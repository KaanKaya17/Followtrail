const test=require('node:test'),assert=require('node:assert/strict');
const search=require('../web/profile-search.js');
test('partial saved profile search handles Turkish letters, separated words, @names and shared links',()=>{
 const text='İpek Çağrı Kaya ipek.kaya Yakın Arkadaşlar';
 for(const query of ['ipe','@ipek','IPeK','cagri ka','kaya ipek','yakin','https://instagram.com/ipek.kaya/?igsh=abc'])assert.equal(search.matches(text,query),true,query);
 assert.equal(search.matches(text,'deniz'),false);
 assert.equal(search.matches(text,''),true);
});
test('profile input accepts only an exact Instagram username or profile URL',()=>{
 for(const input of ['@Ipek.Kaya','https://instagram.com/Ipek.Kaya/?igsh=abc'])assert.equal(search.username(input),'ipek.kaya');
 for(const input of ['İpek Kaya','https://instagram.com.evil/a/','https://x@instagram.com/a/','https://instagram.com/p/','https://instagram.com/a/b/'])assert.equal(search.username(input),null);
});

function clock() {
 let time=0,id=0;const timers=new Map();
 return {now:()=>time,setTimer:(fn,delay)=>{timers.set(++id,{at:time+delay,fn});return id;},clearTimer:id=>timers.delete(id),
 async advance(ms) {const end=time+ms;for(;;) {const task=[...timers].sort((a,b)=>a[1].at-b[1].at).find(x=>x[1].at<=end);if(!task)break;
 time=task[1].at;timers.delete(task[0]);task[1].fn();for(let i=0;i<8;i++)await Promise.resolve();}time=end;for(let i=0;i<8;i++)await Promise.resolve();}};
}
test('live search debounces rapid typing and reuses recent results without another request',async()=>{
 const timer=clock(),calls=[],seen=[];
 const live=search.live({...timer,local:()=>{},status:()=>{},results:r=>seen.push(r),search:async q=>{calls.push(q);return {ok:true,users:[{id:q}]};}});
 live.input('i');live.input('ip');live.input('ipe');await timer.advance(649);assert.deepEqual(calls,[]);
 await timer.advance(1);assert.deepEqual(calls,['ipe']);assert.equal(seen.length,1);
 live.input('ip');live.input('ipe');await timer.advance(650);assert.deepEqual(calls,['ipe']);assert.equal(seen.length,2);
 live.close();
});
test('live search retains only the newest query while respecting the shared request reservation',async()=>{
 const timer=clock(),calls=[];
 const live=search.live({...timer,local:()=>{},status:()=>{},results:()=>{},availability:()=>({wait:15000-timer.now()}),
 search:async q=>{calls.push(q);return {ok:true,users:[]};}});
 live.input('ipe');await timer.advance(1000);live.input('ipek kaya');await timer.advance(13999);assert.deepEqual(calls,[]);
 await timer.advance(1);assert.deepEqual(calls,['ipek kaya']);live.close();
});
test('late replies cannot replace current input results or reopen a closed search',async()=>{
 const timer=clock(),seen=[],calls=[];let finish;
 const live=search.live({...timer,local:()=>{},status:()=>{},results:r=>seen.push(r.users[0]),search:q=>{
 calls.push(q);return q==='first' ? new Promise(resolve=>finish=resolve) : Promise.resolve({ok:true,users:[q]});}});
 live.input('first');await timer.advance(650);live.input('second');await timer.advance(650);
 assert.deepEqual(calls,['first']);finish({ok:true,users:['first']});await timer.advance(0);await timer.advance(250);
 assert.deepEqual(seen,['second']);live.input('third');live.close();await timer.advance(1000);assert.deepEqual(calls,['first','second']);
});
test('rate-limit failures stop automatic searching even if the input changes',async()=>{
 const timer=clock(),calls=[];
 const live=search.live({...timer,local:()=>{},status:()=>{},results:()=>{},search:async q=>{calls.push(q);return {ok:false,error:{code:'limited'}};}});
 live.input('first');await timer.advance(650);live.input('second');await timer.advance(60000);assert.deepEqual(calls,['first']);live.close();
});
test('editing spaces while the same normalized query is in flight does not replay it',async()=>{
 const timer=clock(),calls=[],seen=[];let finish;
 const live=search.live({...timer,local:()=>{},status:()=>{},results:r=>seen.push(r.users[0]),search:q=>{
 calls.push(q);return new Promise(resolve=>finish=resolve);}});
 live.input('first');await timer.advance(650);live.input(' first ');finish({ok:true,users:['first']});await timer.advance(15000);
 assert.deepEqual(calls,['first']);assert.deepEqual(seen,['first']);live.close();
});

test('shared dialog cache survives close, expires at sixty seconds and stores no whole state',async()=>{
 const timer=clock(),cache=search.createCache({now:timer.now}),calls=[],seen=[];
 const open=()=>search.live({...timer,cache,owner:'100',local:()=>{},status:()=>{},results:r=>seen.push(r),
  search:async q=>{calls.push(q);return {ok:true,token:'synthetic',users:[{id:'200',username:'qa.person',name:'QA'}],state:{private:'never retain'}};}});
 let live=open();live.input('qa');await timer.advance(650);live.close();
 live=open();live.input('qa');assert.equal(seen.length,2);assert.equal(calls.length,1);
 assert.equal('state' in seen[1],false);await timer.advance(60000);live.close();
 live=open();live.input('qa');assert.equal(calls.length,1);await timer.advance(650);assert.equal(calls.length,2);live.close();
});

test('cache bounds empty results, account isolation and delayed replies after invalidation',()=>{
 let now=0;const cache=search.createCache({now:()=>now});const first=cache.bind('100');
 cache.put('100','empty',{ok:true,users:[]},first);assert.deepEqual(cache.get('100','empty').users,[]);
 for(let i=0;i<6;i++)cache.put('100','q'+i,{ok:true,users:[]},first);
 assert.equal(cache.get('100','empty'),null);
 cache.clear();cache.bind('300');cache.put('100','late',{ok:true,users:[]},first);
 assert.equal(cache.get('100','late'),null);assert.equal(cache.get('300','q1'),null);
});

test('remaining backend lifetime bounds renderer cache and cache hits bypass a network pause',async()=>{
 const timer=clock(),cache=search.createCache({now:timer.now});const ticket=cache.bind('100');
 cache.put('100','qa',{ok:true,users:[],remainingMs:500},ticket);
 const seen=[];const live=search.live({...timer,cache,owner:'100',local:()=>{},status:()=>{},results:r=>seen.push(r),
  availability:()=>({stop:{code:'cooldown'}}),search:async()=>{throw Error('No network');}});
 live.input('qa');assert.equal(seen.length,1);await timer.advance(500);assert.equal(cache.get('100','qa'),null);live.close();
});
