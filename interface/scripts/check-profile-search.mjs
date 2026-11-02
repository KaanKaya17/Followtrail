import {chromium} from 'playwright-core';
import {execFileSync} from 'node:child_process';
import {mkdtemp,mkdir,writeFile} from 'node:fs/promises';
import {fileURLToPath,pathToFileURL} from 'node:url';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const backend=path.join(root,'interface/desktop/backend');
const python=path.resolve(root,'../InstagramCounter/.venv/Scripts/python.exe');
const home=await mkdtemp(path.join(os.tmpdir(),'TrackerQA-'));
execFileSync(python,[path.join(backend,'seed_qa.py'),'--home',home]);
const state=JSON.parse(execFileSync(python,['-c','import json,sys;from engine import Engine;print(json.dumps(Engine(sys.argv[1]).snapshot()))',home],{cwd:backend,encoding:'utf8'}));
state.gate={...state.gate,blockedUntil:0,manualOnly:false};state.activeAccountId='100';state.connected=true;
const output=path.join(root,'artifacts/profile-search-ui');await mkdir(output,{recursive:true});
const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
const errors=[],external=[],checks=[];
try {
 const page=await browser.newPage({viewport:{width:1440,height:1000}});
 page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(!r.url().startsWith('file:')&&!r.url().startsWith('data:'))external.push(r.url());});
 await page.addInitScript(({state})=>{
  const calls=[];window.qaCalls=calls;window.qaImageResolvers=[];
  window.TrackerDesktop={subscribe:()=>{},command:async(operation,args={})=>{
   calls.push({operation,args});
   if(operation==='search_people') {
    if(args.query==='late')await new Promise(resolve=>window.qaResolve=resolve);
    if(args.query==='rate limit')return {ok:false,state,error:{code:'limited',message:'Instagram isteği sınırladı. Otomatik tekrar durduruldu; mola bilgisini kontrol et.'}};
    state.searchResults={query:args.query,token:'qa-search-token',users:[{id:'900',username:'ipek.kaya',name:'İpek Kaya <img src=x onerror=window.qaInjected=true>'},{id:'901',username:'ipek.deniz',name:'İpek Deniz'}]};
   }
   if(operation==='search_avatar')return {ok:true,avatar:state.people.find(p=>p.avatar)?.avatar || null};
   if(operation==='list_avatar') {
    if(window.qaDelayListImages)return new Promise(resolve=>window.qaImageResolvers.push(resolve));
    return {ok:true,avatar:window.qaBadListImage ? 'https://example.com/not-allowed.jpg' : state.people.find(p=>p.avatar)?.avatar || null};
   }
   if(operation==='add_person') {
    state.people.push({id:args.id,username:args.username,name:'İpek Kaya',followers:1,following:1,observedAt:1800000000,watching:false});
    state.searchResults=null;
   }
   return {ok:true,state};
  }};
 },{state});
 await page.goto(pathToFileURL(path.join(root,'interface/web/desktop.html')).href+'#people');
 await page.locator('#people-search').waitFor();
 for(const query of ['@qa.den','QA den','arkadas','https://instagram.com/qa.deniz/?igsh=abc']) {
  await page.locator('#people-search').fill(query);assert.equal(await page.locator('.person-card').count(),1,query);
 }
 await page.locator('#people-search').fill('');
 assert.equal(await page.evaluate(()=>qaCalls.filter(c=>c.operation==='search_people').length),0);
 checks.push('Saved partial, @, URL, multiword and Turkish group searches remain local');
 await page.locator('[data-action="add-person"]').first().click();
 await page.locator('#new-username').fill('ipe');
 // No button: typed input must trigger the query while preserving the field.
 await page.evaluate(()=>{window.qaSearchField=document.getElementById('new-username')});
 await page.locator('[data-action="choose-profile"][data-id="900"]').waitFor();
 assert.equal(await page.evaluate(()=>qaCalls.filter(c=>c.operation==='add_person').length),0);
 assert.equal(await page.evaluate(()=>window.qaInjected),undefined);
 assert.equal(await page.evaluate(()=>window.qaSearchField===document.getElementById('new-username')),true);
 assert.equal(await page.locator('#new-username').isEnabled(),true);
 await page.locator('[data-action="choose-profile"][data-id="900"] .avatar img').waitFor();
 await page.screenshot({path:path.join(output,'instagram-search-results.png')});
 await page.locator('[data-action="choose-profile"][data-id="900"]').click();
 assert.deepEqual(await page.evaluate(()=>qaCalls.find(c=>c.operation==='add_person').args),{id:'900',username:'ipek.kaya'});
 checks.push('Debounced live query preserves input/focus and shows safe asynchronous avatars; only selected numeric identity is added');
 await page.locator('[data-action="add-person"]').first().click();
 await page.locator('#new-username').fill('rate limit');
 await page.getByRole('dialog').getByRole('button',{name:'Instagram’da ara',exact:true}).click();
 await page.getByRole('dialog').locator('.notice.warning').waitFor();
 assert.equal(await page.locator('#new-username').inputValue(),'rate limit');
 assert.equal(await page.evaluate(()=>qaCalls.filter(c=>c.operation==='search_people'&&c.args.query==='rate limit').length),1);
 checks.push('Rate-limit response preserves query and has no automatic replay');
 await page.keyboard.press('Escape');await page.locator('[data-action="add-person"]').first().click();
 await page.locator('#new-username').fill('late');
 await page.getByRole('dialog').getByRole('button',{name:'Instagram’da ara',exact:true}).click();
 await page.waitForFunction(()=>typeof window.qaResolve==='function');
 await page.keyboard.press('Escape');await page.evaluate(()=>window.qaResolve());
 await page.waitForFunction(()=>document.querySelector('[data-action="add-person"]')?.disabled===false);
 assert.equal(await page.getByRole('dialog').count(),0);
 checks.push('Closing a pending search prevents late results from reopening the dialog');
 await page.locator('.sidebar a[href="#lists"]').click();
 await page.locator('#list-owner').selectOption('300');
 await page.locator('#list-rows .avatar img').first().waitFor();
 const visible=await page.evaluate(()=>qaCalls.filter(c=>c.operation==='list_avatar'&&c.args.ownerId==='300'));
 assert.ok(visible.length>0 && visible.length<30,`Only visible rows should load, got ${visible.length}`);
 assert.ok(visible.every(call=>call.args.scanId==='large' && !Object.hasOwn(call.args,'url')));
 await page.locator('#list-search').fill('qa.449');
 await page.locator('#list-rows .avatar img').waitFor();
 assert.equal(await page.locator('#list-rows .list-row').count(),1);
 assert.equal(await page.locator('#list-search').evaluate(node=>node===document.activeElement),true);
 await page.screenshot({path:path.join(output,'list-avatar.png')});
 checks.push('List photos load only for visible rows, follow local filtering and use observation IDs instead of URLs');
 await page.evaluate(()=>window.qaBadListImage=true);
 await page.locator('#list-search').fill('qa.447');
 await page.waitForFunction(()=>qaCalls.some(c=>c.operation==='list_avatar'&&c.args.id==='447'));
 assert.equal(await page.locator('#list-rows .avatar img').count(),0);
 await page.evaluate(()=>{window.qaBadListImage=false;window.qaDelayListImages=true});
 await page.locator('#list-search').fill('qa.448');
 await page.waitForFunction(()=>qaImageResolvers.length>0);
 await page.evaluate(()=>window.qaOldAvatar=document.querySelector('#list-rows .avatar'));
 await page.locator('.sidebar a[href="#people"]').click();
 await page.evaluate(()=>{for(const resolve of qaImageResolvers)resolve({ok:true,avatar:'data:image/jpeg;base64,QA=='});window.qaDelayListImages=false;});
 await page.waitForFunction(()=>qaCalls.some(c=>c.operation==='cancel_list_images'));
 assert.equal(await page.evaluate(()=>!!qaOldAvatar.querySelector('img')),false);
 checks.push('Untrusted image sources keep initials and leaving the list ignores late thumbnail replies');
 assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
 await writeFile(path.join(output,'checks.json'),JSON.stringify({checks,errors,external,syntheticApi:true,liveInstagram:false},null,2));
 console.log(`${checks.length} profile search renderer checks passed; synthetic API, no external requests.`);
} finally {await browser.close();}
