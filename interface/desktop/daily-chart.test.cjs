const {test}=require('node:test');
const assert=require('node:assert/strict');
require('../web/daily-chart.js');
const context=(id='100')=>({accountId:'100',id,metric:'followers',range:30});
const reply=(args,points=[])=>({ok:true,daily:{...args,points,bucketDays:1,observedDays:points.length,samples:points.length}});
const flush=()=>new Promise(resolve=>setImmediate(resolve));
test('Daily chart ignores late responses after switching target or account',async()=>{
  const pending=[];const chart=TrackerDailyChart.create({request:args=>new Promise(resolve=>pending.push({args,resolve})),change:()=>{}});
  chart.load(context(),'first');chart.load(context('200'),'second');
  pending[0].resolve(reply(pending[0].args,[{value:999}]));await flush();assert.equal(chart.state.result,null);
  pending[1].resolve(reply(pending[1].args,[{value:2}]));await flush();assert.equal(chart.state.result.id,'200');
  chart.load(context('200'),'second');assert.equal(pending.length,2);
  chart.reset();assert.equal(chart.context,null);
});
test('Mismatched or oversized daily replies remain an error until explicit retry',async()=>{
  let count=0;const chart=TrackerDailyChart.create({request:async args=>{count++;return reply({...args,accountId:'foreign'});},change:()=>{}});
  await chart.load(context(),'rev');assert.ok(chart.state.error);
  await chart.load(context(),'rev');assert.equal(count,1);
  await chart.load(context(),'rev',true);assert.equal(count,2);
});
test('Observed daily gaps break the graph path and empty days are not rendered as zero',()=>{
  const points=[1,2,4].map((day,i)=>({bucket:day,day:day*86400000,at:day*86400000+1,first:10+i,value:10+i,minimum:9,maximum:15,observedDays:1,samples:1}));
  const html=TrackerDailyChart.render({result:{points,bucketDays:1,observedDays:3,samples:3}},{t:x=>x,num:String,esc:String,locale:'en'});
  const path=html.match(/<path d="([^"]+)"/)[1];
  assert.equal((path.match(/M/g)||[]).length,2);assert.equal((path.match(/L/g)||[]).length,1);
  assert.equal((html.match(/<circle /g)||[]).length,3);
});
