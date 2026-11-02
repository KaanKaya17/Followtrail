const {test}=require('node:test');
const assert=require('node:assert/strict');
require('../web/list-pages.js');
global.IntersectionObserver=class {constructor(callback){this.callback=callback;}observe(){}disconnect(){}};
const context=(query='')=>({accountId:'100',revision:'first',query});
const page=(args,items,nextOffset,total=150)=>({ok:true,page:{...args,items,total,nextOffset,added:1,removed:1}});
const flush=()=>new Promise(resolve=>setImmediate(resolve));

test('local page loads are serialized and completed pages stop requesting',async()=>{
  const calls=[];
  const pager=TrackerListPages.create({request:args=>new Promise(resolve=>calls.push({args,resolve})),change:()=>{}});
  pager.select(context());pager.mount({},true);pager.mount({},true);
  assert.equal(calls.length,1);
  calls[0].resolve(page(calls[0].args,Array.from({length:100},(_,id)=>({id})),100));await flush();
  assert.equal(pager.state.items.length,100);
  pager.retry();pager.retry();assert.equal(calls.length,2);
  calls[1].resolve(page(calls[1].args,Array.from({length:50},(_,id)=>({id:100+id})),null));await flush();
  pager.retry();assert.equal(calls.length,2);assert.equal(pager.state.items.length,150);
});

test('changing search or account discards pending replies and hidden pages do not start work',async()=>{
  const calls=[];
  const pager=TrackerListPages.create({request:args=>new Promise(resolve=>calls.push({args,resolve})),change:()=>{}});
  pager.select(context('old'));pager.mount({},false);assert.equal(calls.length,0);
  pager.mount({},true);pager.select(context('new'));pager.mount({},true);
  calls[0].resolve(page(calls[0].args,[{id:'old'}],null,1));await flush();assert.equal(pager.state.items.length,0);
  calls[1].resolve(page(calls[1].args,[{id:'new'}],null,1));await flush();assert.equal(pager.state.items[0].id,'new');
  pager.reset();assert.equal(pager.state.items.length,0);
});

test('an invalid or failed page requires explicit retry and never loops at the sentinel',async()=>{
  let calls=0;
  const pager=TrackerListPages.create({request:async args=>{calls++;return page({...args,accountId:'foreign'},[],0,0);},change:()=>{}});
  pager.select(context());pager.mount({},true);await flush();
  assert.ok(pager.state.error);assert.equal(pager.state.items.length,0);
  pager.mount({},true);await flush();assert.equal(calls,1);
  pager.retry();await flush();assert.equal(calls,2);
});
