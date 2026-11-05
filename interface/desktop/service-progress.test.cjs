const {test} = require('node:test');
const assert = require('node:assert/strict');
const {Backend} = require('./service.cjs');

test('All published and requested states use the owning Electron package version',async()=>{
  const events=[];
  const backend=new Backend({appVersion:'0.2.52',onEvent:event=>events.push(event)});
  backend.message({type:'ready',state:{settings:{},version:'obsolete'}});
  assert.equal((await backend.command('state',{})).state.version,'0.2.52');
  backend.message({type:'state',state:{settings:{}}});
  assert.equal(events.at(-1).state.version,'0.2.52');
});

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

test('Specific worker failure survives the following generic process exit',async()=>{
  const events=[];
  const backend=new Backend({onEvent:event=>events.push(event)});
  backend.message({type:'fatal',message:'Specific encrypted-storage failure'});
  backend.fail('Generic worker exit');
  assert.equal((await backend.command('state')).error.message,'Specific encrypted-storage failure');
  assert.equal(events.length,1);
});

test('Thumbnail controls can finish independently without clearing a running account operation',async()=>{
  const backend=new Backend({onEvent:()=>{}}),writes=[];
  backend.ready=true;backend.busy=true;
  backend.child={stdin:{write(line,_encoding,callback){writes.push(JSON.parse(line));callback();}}};
  const result=backend.command('search_avatar',{token:'qa-token',id:'200'});
  assert.equal(writes.length,1);
  backend.message({type:'control',id:writes[0].id,ok:true,avatar:null});
  assert.equal((await result).avatar,null);assert.equal(backend.busy,true);
});


test('Local list pages finish without clearing an active scan or its progress',async()=>{
  const backend=new Backend({onEvent:()=>{}}),writes=[];
  backend.ready=true;backend.busy=true;backend.progress={phase:'waiting',until:123};
  backend.child={stdin:{write(line,_encoding,callback){writes.push(JSON.parse(line));callback();}}};
  const pending=backend.command('list_page',{accountId:'100'});
  assert.equal(writes.length,1);
  backend.message({type:'control',id:writes[0].id,ok:true,page:{items:[]}});
  assert.deepEqual((await pending).page,{items:[]});
  assert.equal(backend.busy,true);assert.equal(backend.progress.phase,'waiting');
});
