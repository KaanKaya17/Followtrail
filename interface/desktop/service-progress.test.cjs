const {test} = require('node:test');
const assert = require('node:assert/strict');
const {Backend} = require('./service.cjs');

test('Cancellation intent survives late worker progress until operation result',async()=>{
  const events=[], writes=[];
  const backend=new Backend({onEvent:event=>events.push(event)});
  backend.ready=true;backend.busy=true;
  backend.child={stdin:{write(line,_encoding,callback){writes.push(JSON.parse(line));callback();}}};
  const scan={ownerId:'100',direction:'followers',page:4,count:150};
  backend.message({type:'progress',phase:'waiting',until:1800000000,scan});
  const pending=backend.command('cancel',{});
  assert.equal(backend.progress.phase,'cancelling');
  assert.equal(backend.progress.scan,scan);
  backend.message({type:'progress',phase:'request',scan:{...scan,page:5}});
  assert.equal(backend.progress.phase,'cancelling');
  assert.equal(events.at(-1).phase,'cancelling');
  backend.message({type:'control',id:writes[0].id,ok:true});
  await pending;
  assert.equal(backend.progress.phase,'cancelling');
  backend.message({type:'result',ok:false,error:{code:'cancelled'}});
  assert.equal(backend.busy,false);
  assert.equal(backend.progress,null);
  backend.busy=true;backend.message({type:'progress',phase:'request'});
  assert.equal(backend.progress.phase,'request');
});

test('Fatal worker failure clears stale countdown from subsequent state and resume',async()=>{
  const backend=new Backend({onEvent:()=>{}});
  backend.ready=true;backend.busy=true;
  backend.message({type:'progress',phase:'waiting',until:1800000000});
  backend.fail('synthetic failure');
  const result=await backend.command('state');
  assert.equal(result.busy,false);
  assert.equal(result.progress,null);
  assert.equal(result.ok,false);
});
