const {test}=require('node:test');
const assert=require('node:assert/strict');
require('../web/history-pages.js');
const context=revision=>({accountId:'100',revision,kind:'events',type:'all',personId:'',section:'all',read:'all'});
const reply=(args,items,total=items.length)=>({ok:true,page:{...args,items,total,nextOffset:args.offset+items.length<total?args.offset+items.length:null}});
test('History paging ignores late foreign revisions and keeps failures until explicit retry',async()=>{
  const pending=[];const pager=TrackerHistoryPages.create({request:args=>new Promise(resolve=>pending.push({args,resolve})),change:()=>{}});
  pager.select(context('old'));const old=pager.load();pager.select(context('new'));const next=pager.load();
  pending[0].resolve(reply(pending[0].args,[{id:'old'}]));await old;assert.equal(pager.state.items.length,0);
  pending[1].resolve(reply({...pending[1].args,accountId:'foreign'},[]));await next;assert.ok(pager.state.error);
  const retry=pager.load();pending[2].resolve(reply(pending[2].args,[{id:'new'}]));await retry;
  assert.equal(pager.state.items[0].id,'new');assert.equal(pager.state.nextOffset,null);
  pager.reset();assert.deepEqual(pager.state.items,[]);
});
test('History page limit and forward offsets prevent duplicate or unbounded accumulation',async()=>{
  const pager=TrackerHistoryPages.create({request:async args=>reply(args,Array(101).fill({id:'bad'})),change:()=>{}});
  pager.select(context('rev'));await pager.load();assert.ok(pager.state.error);assert.equal(pager.state.items.length,0);
});
test('Refreshing the same history scope keeps the already opened page range',async()=>{
  const pager=TrackerHistoryPages.create({request:async args=>reply(args,Array.from({length:100},(_,i)=>({id:String(args.offset+i)})),205),change:()=>{}});
  pager.select(context('first'));await pager.load();await pager.load();assert.equal(pager.state.items.length,200);
  pager.select(context('next'));assert.equal(pager.state.items.length,200);
  await pager.load();assert.equal(pager.state.items.length,200);assert.equal(pager.state.nextOffset,200);
  pager.select({...context('next'),accountId:'400'});assert.equal(pager.state.items.length,0);
});
