const test=require('node:test'),assert=require('node:assert/strict');
const details=require('../web/profile-details.js');
function clock() {
 let time=1800000000000,id=0;const timers=new Map();
 return {now:()=>time,setTimer:(fn,delay)=>{timers.set(++id,{at:time+delay,fn});return id;},clearTimer:id=>timers.delete(id),
 async advance(ms) {const end=time+ms;for(;;){const task=[...timers].sort((a,b)=>a[1].at-b[1].at).find(x=>x[1].at<=end);if(!task)break;
 time=task[1].at;timers.delete(task[0]);task[1].fn();for(let i=0;i<8;i++)await Promise.resolve();}time=end;for(let i=0;i<8;i++)await Promise.resolve();}};
}
const result=(id,at)=>({ok:true,accountId:'100',profile:{id,followers:0,following:3,observedAt:at/1000}});
test('drawer cache expires from observation time, explicit refresh bypasses it',async()=>{
 const timer=clock(),calls=[],seen=[];
 const model=details.create({...timer,load:async args=>{calls.push(args);return result(args.id,timer.now());}});
 const open=()=>model.open({accountId:'100',id:'200'},s=>seen.push(s));
 open();await timer.advance(0);assert.equal(calls.length,1);assert.equal(seen.at(-1).profile.followers,0);
 model.close();await timer.advance(299999);open();assert.equal(calls.length,1);
 model.close();await timer.advance(1);open();await timer.advance(0);assert.equal(calls.length,2);
 model.retry();await timer.advance(0);assert.equal(calls.length,3);assert.equal(calls[2].manual,true);model.close();
});
test('reopening an in-flight identity shares its response and does not repeat the request',async()=>{
 const timer=clock(),calls=[],seen=[];let finish;
 const model=details.create({...timer,load:args=>{calls.push(args);return new Promise(r=>finish=r);}});
 model.open({accountId:'100',id:'200'},()=>{});await timer.advance(0);model.close();
 model.open({accountId:'100',id:'200'},s=>seen.push(s));await timer.advance(0);
 finish(result('200',timer.now()));await timer.advance(0);await timer.advance(1000);
 assert.equal(calls.length,1);assert.equal(seen.at(-1).phase,'ready');model.close();
});
test('waiting retains only latest identity and closing prevents a delayed request',async()=>{
 const timer=clock(),calls=[],until=timer.now()+8000;
 const model=details.create({...timer,availability:()=>({wait:until-timer.now()}),load:async args=>{calls.push(args);return result(args.id,timer.now());}});
 model.open({accountId:'100',id:'200'},()=>{});await timer.advance(4000);
 model.open({accountId:'100',id:'201'},()=>{});await timer.advance(4000);assert.deepEqual(calls.map(c=>c.id),['201']);
 model.open({accountId:'100',id:'202'},()=>{});model.close();await timer.advance(10000);assert.equal(calls.length,1);
});
test('late replies cannot update a closed drawer or cross account scope',async()=>{
 const timer=clock(),seen=[];let finish;
 const model=details.create({...timer,load:()=>new Promise(r=>finish=r)});
 model.open({accountId:'100',id:'200'},s=>seen.push(s));await timer.advance(0);model.reset();
 finish(result('200',timer.now()));await timer.advance(0);assert.deepEqual(seen.map(s=>s.phase),['loading']);
 model.open({accountId:'100',id:'200'},s=>seen.push(s));await timer.advance(0);
 assert.equal(seen.at(-1).phase,'loading');model.close();finish(result('200',timer.now()));await timer.advance(0);
});
test('failures and rate-limit stops are displayed once without automatic replay',async()=>{
 const timer=clock(),seen=[];let calls=0;
 const model=details.create({...timer,load:async()=>{calls++;return {ok:false,error:{code:'limited'}};}});
 model.open({accountId:'100',id:'200'},s=>seen.push(s));await timer.advance(60000);
 assert.equal(calls,1);assert.equal(seen.at(-1).error.code,'limited');model.close();
 const paused=details.create({...timer,availability:()=>({stop:{code:'cooldown'}}),load:async()=>{calls++;}});
 paused.open({accountId:'100',id:'200'},s=>seen.push(s));await timer.advance(60000);
 assert.equal(calls,1);assert.equal(seen.at(-1).error.code,'cooldown');paused.close();
});
test('finishing an older read does not fetch a newly opened profile whose saved data is already fresh',async()=>{
 const timer=clock(),calls=[],seen=[];let finish;
 const model=details.create({...timer,load:args=>{calls.push(args.id);return new Promise(r=>finish=r);}});
 model.open({accountId:'100',id:'200'},()=>{});await timer.advance(0);
 model.open({accountId:'100',id:'201',known:result('201',timer.now()).profile},s=>seen.push(s));
 finish(result('200',timer.now()));await timer.advance(1000);
 assert.deepEqual(calls,['200']);assert.equal(seen.at(-1).profile.id,'201');model.close();
});
