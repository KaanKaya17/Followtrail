const {test}=require('node:test');
const assert=require('node:assert/strict');
global.TrackerProfileLinks=require('../web/profile-links.js');
global.TrackerIcon=require('../web/icons.js');
const stories=require('../web/stories.js');
const {Backend}=require('./service.cjs');
const esc=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const options={esc,t:(key,...values)=>key.replace(/\{(\d+)\}/g,(_,index)=>String(values[Number(index)])),date:value=>String(value)};
const row={id:'300',ownerId:'200',publishedAt:1800000000,capturedAt:1800000200,type:'image',status:'saved',mentions:[{id:'900',username:'qa.tagged'}],hashtags:['<unsafe>'],links:['https://example.com/event'],sharedMedia:[{id:'700',code:'QAsource'}]};

test('Story rendering escapes source text and keeps mention and source-post identities separate',()=>{
 const html=stories.render({id:'200',items:[row],summary:{returned:1},checkedAt:1800000200},options,{mode:'detail',storyId:'300'});
 assert.ok(html.includes('&lt;unsafe&gt;'));assert.ok(!html.includes('<unsafe>'));
 assert.ok(html.includes('instagram.com/qa.tagged/'));assert.ok(html.includes('instagram.com/p/QAsource/'));
 assert.ok(html.includes('Yeniden paylaşımın kaynağı bu kayıtta doğrulanamadı.'));assert.ok(!html.includes('cdninstagram.com'));
});
test('Story date filter uses publication time, falling back only when its source time is unknown',()=>{
 const archive={id:'200',items:[row]};
 assert.equal(stories.day(row),new Date(row.publishedAt*1000).toISOString().slice(0,10));
 assert.ok(stories.render(archive,options,{mode:'detail',month:'2000-01',selected:'2000-01-01'}).includes('Bu günde kayıtlı hikâye yok.'));
 assert.ok(!stories.render(archive,options,{mode:'detail',month:'2000-01',selected:'2000-01-01'}).includes('data-action="story-select"'));
 const unknown={...row,publishedAt:null};
 assert.ok(stories.render({id:'200',items:[unknown]},options,{mode:'detail'}).includes('Kaydedilme zamanı'));
});
const archive=()=>({accountId:'100',id:'200',items:[row]});
const asset=args=>({accountId:args.accountId,id:args.id,storyId:args.storyId,mime:'image/jpeg',base64:'QA'});
test('An open archive owns its data and media choice while other state updates and months change',async()=>{
 const changes=[],calls=[];let busy=true;
 const controller=stories.create({availability:()=>({busy}),setTimer:()=>1,clearTimer:()=>{},
  change:(view,media)=>changes.push({phase:view.phase,month:view.month,media}),
  load:async args=>{calls.push(args);return {ok:true,storyMedia:asset(args)};}});
 controller.open(archive());assert.equal(calls.length,0);
 controller.selectDay(stories.day(row));assert.equal(controller.view.phase,'waiting');
 busy=false;controller.wake();await new Promise(setImmediate);
 assert.equal(controller.view.phase,'ready');assert.equal(calls.length,1);
 controller.month('2026-12');controller.month('2026-11');
 assert.equal(controller.view.month,'2026-11');assert.equal(controller.view.storyId,'300');assert.equal(calls.length,1);
 assert.equal(changes.filter(change=>change.media).length,1);
 controller.close();
});
test('Closing and switching accounts discards an in-flight media result and loads only the new owner',async()=>{
 let finish;const published=[];let calls=0;
 const controller=stories.create({setTimer:()=>1,clearTimer:()=>{},change:(_view,media)=>{if(media)published.push(media);},
  load:args=>++calls===1?new Promise(resolve=>{finish=()=>resolve({ok:true,storyMedia:asset(args)});}):Promise.resolve({ok:true,storyMedia:asset(args)})});
 controller.open(archive());controller.selectDay(stories.day(row));controller.close();
 controller.open({...archive(),accountId:'400'});controller.selectDay(stories.day(row));finish();
 await new Promise(setImmediate);
 assert.equal(published.length,1);assert.equal(published[0].accountId,'400');controller.close();
});
test('Media from an unexpected identity fails without a retry loop',async()=>{
 let calls=0;const controller=stories.create({change:()=>{},load:async args=>{calls++;return {ok:true,storyMedia:{...asset(args),storyId:'999'}};}});
 controller.open(archive());controller.selectDay(stories.day(row));await new Promise(setImmediate);
 assert.equal(controller.view.phase,'error');controller.wake();assert.equal(calls,1);controller.close();
});
test('Calendar shows daily counts with Monday alignment, leap days and explicit empty results',()=>{
 const feb=Date.parse('2024-02-29T23:59:00Z')/1000;
 const archive={id:'200',items:[{...row,publishedAt:feb},{...row,id:'301',publishedAt:feb+20}],checkedAt:1,summary:{returned:0}};
 const model=stories.calendar(archive,{month:'2024-02'},new Date('2024-03-01T00:00:00Z'));
 assert.equal(model.offset,3);assert.equal(model.days,29);assert.equal(model.selected,'2024-02-29');assert.equal(model.counts.get('2024-02-29'),2);
 assert.equal(stories.shiftMonth('2024-12',1),'2025-01');assert.equal(stories.shiftMonth('1970-01',-1),null);
 const html=stories.render(archive,options,{month:'2024-02'});
 assert.ok(html.includes('2 hikâye'));assert.ok(html.includes('Instagram bu kontrolde hikâye verisi döndürmedi.'));
 assert.ok(!html.includes('type="date"'));
 const empty=stories.calendar({items:[]},{},new Date('2026-11-03T00:00:00Z'));
 assert.equal(empty.month,'2026-11');assert.equal(empty.selected,'2026-11-03');
});
test('On-demand story bytes resolve to the caller but never enter state or renderer event broadcasts',async()=>{
 const events=[],backend=new Backend({onEvent:event=>events.push(event)});
 backend.ready=true;backend.state={account:{id:'100'}};
 let id;backend.child={stdin:{write(line,_encoding,callback){id=JSON.parse(line).id;callback();}}};
 const pending=backend.command('story_media',{accountId:'100',id:'200',storyId:'300'});
 const asset={base64:'SYNTHETIC-MEDIA'};
 backend.message({type:'result',id,ok:true,storyMedia:asset});
 assert.equal((await pending).storyMedia,asset);assert.equal(backend.busy,false);
 assert.equal(backend.state.storyMedia,undefined);assert.equal(events.at(-1).storyMedia,undefined);
});
