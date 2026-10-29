import { _electron as electron } from 'playwright-core';
import { mkdtemp,mkdir,readFile,writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const version=JSON.parse(await readFile(path.join(root,'interface/package.json'),'utf8')).version;
const output=path.join(root,'artifacts/navigation-ui');await mkdir(output,{recursive:true});
const checks=[],errors=[],requests=[];let app,page;
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
async function launch(flags) {
 const home=await mkdtemp(path.join(os.tmpdir(),'TrackerQA-'));
 execFileSync(path.resolve(root,'../InstagramCounter/.venv/Scripts/python.exe'),[path.join(root,'interface/desktop/backend/seed_qa.py'),'--home',home,...flags]);
 app=await electron.launch({executablePath:path.join(root,`artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`),args:[`--tracker-test-home=${home}`],env});
 page=await app.firstWindow();page.setDefaultTimeout(12000);
 page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(!/^(file:|data:)/.test(r.url()))requests.push(r.url());});
 await page.getByRole('heading',{name:'Çevrende neler oluyor?'}).waitFor();
 assert.equal((await page.evaluate(()=>window.TrackerDesktop.command('state'))).state.version,version);
 await app.evaluate(({app})=>{
   const {createRequire}=process.getBuiltinModule('module');
   const {Backend}=createRequire(app.getAppPath()+'/package.json')('./desktop/service.cjs');
   const original=Backend.prototype.command;globalThis.qaRefreshes=[];
   Backend.prototype.command=function(operation,args){if(operation==='refresh')globalThis.qaRefreshes.push({id:args.id});return original.call(this,operation,args);};
 });
 await page.evaluate(()=>{
   window.qaReloadMarker='preserved';window.qaKeyTrace=[];
   for(const type of ['keydown','close','cancel']) document.addEventListener(type,event=>{
     window.qaKeyTrace.push({type,key:event.key,ctrl:event.ctrlKey,active:document.activeElement?.id || document.activeElement?.tagName,
       dialogs:[...document.querySelectorAll('dialog[open]')].map(n=>n.className)});
   },true);
 });
}
const state=()=>page.evaluate(()=>window.TrackerDesktop.command('state'));
const refreshes=()=>app.evaluate(()=>globalThis.qaRefreshes);
async function route(key,id){await page.locator('#main').focus();await page.keyboard.press(key);await page.waitForFunction(id=>location.hash==='#'+id,id);}
async function search(query){await page.keyboard.press('Control+k');await page.locator('.command-palette').getByRole('combobox').fill(query);}
async function person(id='200'){await search(id==='200'?'@qa.deniz':'@qa.owner');await page.keyboard.press('Enter');await page.locator('#overlay-root dialog.drawer').waitFor();}
try {
 await launch(['--keyboard']);
 for(const [index,id] of ['overview','people','lists','notifications','settings'].entries()) await route(String(index+1),id);
 assert.equal((await state()).state.gate.attempts,2);checks.push('1–5 navigate the requested five screens with no account requests');
 await page.keyboard.press('/');await page.locator('.command-palette').getByRole('combobox').fill('KAYDEDILEN PROFILLER');
 assert.equal(await page.locator('.command-palette').getByRole('option').count(),1);await page.keyboard.press('Enter');await page.waitForFunction(()=>location.hash==='#people');
 checks.push('Slash opens the local palette; Turkish names also match an ASCII query');
 await search('@qa.ada');assert.match(await page.locator('.command-palette').innerText(),/<img src=x onerror=/);
 assert.equal(await page.evaluate(()=>window.paletteInjected),undefined);await page.keyboard.press('Escape');
 await person();const note=page.locator('#person-note');await note.fill('draft: ');await page.keyboard.type('12345/');
 assert.equal(await note.inputValue(),'draft: 12345/');assert.match(await page.url(),/#people$/);
 await page.keyboard.press('Control+r');await page.getByText('Yazı alanındayken yenileme kısayolu çalışmaz.',{exact:true}).waitFor();
 assert.deepEqual(await refreshes(),[]);assert.equal(await page.evaluate(()=>window.qaReloadMarker),'preserved');
 await page.keyboard.press('Control+k');await page.locator('.command-palette').getByRole('combobox').waitFor();assert.equal(await note.inputValue(),'draft: 12345/');
 await page.keyboard.press('Escape');await page.locator('.command-palette').waitFor({state:'hidden'});assert.equal(await note.inputValue(),'draft: 12345/');
 await page.keyboard.press('Escape');await page.locator('#overlay-root dialog').waitFor({state:'hidden'});
 checks.push('Typing is preserved; Ctrl K stacks over a draft; Esc closes only the top dialog; Ctrl R cannot reload an edit');
 await person();await page.getByRole('button',{name:'Kapat',exact:true}).focus();
 await app.evaluate(({BrowserWindow})=>{
   const window=BrowserWindow.getAllWindows()[0];window.focus();window.webContents.focus();
   window.webContents.sendInputEvent({type:'keyDown',keyCode:'R',modifiers:['control']});
   window.webContents.sendInputEvent({type:'keyUp',keyCode:'R',modifiers:['control']});
 });
 await page.getByText('@qa.deniz için yenileme tamamlanamadı.',{exact:true}).waitFor();
 assert.deepEqual(await refreshes(),[{id:'200'}]);assert.equal(await page.evaluate(()=>window.qaReloadMarker),'preserved');await page.keyboard.press('Escape');
 await route('3','lists');await page.locator('#list-owner').selectOption('200');await page.locator('#main').focus();await page.keyboard.press('Control+r');
 await page.getByRole('dialog',{name:'İşlem tamamlanamadı',exact:true}).waitFor();
 await page.getByText('@qa.deniz için yenileme tamamlanamadı.',{exact:true}).waitFor();assert.deepEqual(await refreshes(),[{id:'200'},{id:'200'}]);await page.keyboard.press('Escape');
 await route('1','overview');await page.keyboard.down('Control');await page.keyboard.down('r');await page.keyboard.down('r');await page.keyboard.up('r');await page.keyboard.up('Control');
 await page.getByText('@qa.owner için yenileme tamamlanamadı.',{exact:true}).waitFor();
 assert.deepEqual(await refreshes(),[{id:'200'},{id:'200'},{id:'100'}]);await page.keyboard.press('Escape');
 checks.push('Native Ctrl R refreshes the drawer, selected list owner and own profile once; real offline IPC prevents internet access');
 const before=(await refreshes()).length,current=(await state()).state;
 await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows()[0].webContents.send('tracker:event',{type:'progress',phase:'waiting',until:1800000000}));
 await page.locator('.operation-panel').waitFor();await page.keyboard.press('Control+r');
 await page.getByText('Önce devam eden işlemi bitir veya iptal et.',{exact:true}).waitFor();assert.equal((await refreshes()).length,before);
 await app.evaluate(({BrowserWindow},state)=>BrowserWindow.getAllWindows()[0].webContents.send('tracker:event',{type:'result',state}),current);
 checks.push('A busy progress state prevents a second refresh command');
 await route('2','people');await page.locator('#people-search').fill('nobody_matches_this');
 await page.getByRole('button',{name:'Arama ve filtreleri temizle',exact:true}).click();assert.equal(await page.locator('.person-card').count(),3);
 await route('4','notifications');for(const entry of (await state()).state.notificationLog) await page.evaluate(id=>window.TrackerDesktop.command('read_notification',{id}),entry.id);
 await page.getByRole('button',{name:/^Okunmamış/}).click();await page.getByRole('heading',{name:'Okunmamış bildirim yok.',exact:true}).waitFor();
 await page.getByRole('button',{name:'Tüm bildirimleri göster',exact:true}).click();assert.ok(await page.locator('.notification-log-row').count()>0);
 checks.push('Search-zero and unread-zero states offer working reset actions rather than first-use instructions');
 await page.locator('#main').focus();await search('qa');await page.screenshot({path:path.join(output,'palette-tr.png')});await page.keyboard.press('Escape');
 await page.evaluate(()=>window.TrackerDesktop.command('settings',{language:'en'}));await page.waitForFunction(()=>document.documentElement.lang==='en');
 await page.locator('#main').focus();await search('Saved Profiles');await page.getByRole('heading',{name:'Search profiles and screens'}).waitFor();
 assert.equal(await page.locator('.command-palette').evaluate(el=>el.scrollWidth>el.clientWidth+1),false);
 await page.screenshot({path:path.join(output,'palette-en.png')});await page.keyboard.press('Escape');
 checks.push('Palette labels, selection hints and refresh scope are bilingual without horizontal overflow');
 for(const id of ['200','201','300']) await page.evaluate(id=>window.TrackerDesktop.command('remove_person',{id}),id);
 await route('2','people');
 assert.match(await page.locator('.empty-guide').innerText(),/followers are not automatically added here/);
 const beforeLists=(await state()).state;
 await page.getByRole('button',{name:'Open my follower list',exact:true}).click();
 assert.equal(await page.locator('#list-owner').inputValue(),beforeLists.account.id);
 assert.equal((await state()).state.gate.attempts,beforeLists.gate.attempts);
 await page.getByRole('heading',{name:'Followers and following',exact:true}).waitFor();
 checks.push('Saved Profiles is distinct from followers; its shortcut opens the own follower list without a request');
 await route('2','people');await page.locator('.empty-guide').getByRole('button',{name:'Add profile',exact:true}).click();
 await page.locator('#new-username').fill('draft12345/');await page.keyboard.press('Escape');
 checks.push('The connected first-person guide opens the existing add form without submitting a request');
 await app.close();app=null;
 await launch(['--keyboard','--hold']);await page.locator('#main').focus();await page.keyboard.press('Control+r');
 await page.getByText('Kontroller için mola sürüyor. Yeniden deneme saatini İstek bilgileri bölümünden görebilirsin.',{exact:true}).waitFor();assert.deepEqual(await refreshes(),[]);
 assert.equal((await state()).state.gate.attempts,2);checks.push('An active cooldown blocks shortcut refresh before IPC');await app.close();app=null;
 await launch(['--empty']);await route('2','people');await page.getByRole('heading',{name:'Henüz profil kaydetmedin.',exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'Takipçi listemi aç',exact:true}).isDisabled(),true);
 await page.screenshot({path:path.join(output,'people-empty-tr.png')});await page.locator('.empty-guide').getByRole('button',{name:'Hesabını bağla',exact:true}).click();
 await page.locator('#login-username').fill('draft12345/');await page.keyboard.press('Escape');
 await route('4','notifications');await page.getByRole('heading',{name:'Değişiklik kayıtların burada birikecek.',exact:true}).waitFor();
 await page.screenshot({path:path.join(output,'notifications-empty-tr.png')});await page.getByRole('button',{name:'Kaydedilen profilleri aç',exact:true}).click();
 await page.getByRole('heading',{name:'Henüz profil kaydetmedin.',exact:true}).waitFor();
 assert.deepEqual(await refreshes(),[]);assert.equal((await state()).state.gate.attempts || 0,0);
 checks.push('Fresh-install People and Notifications show actionable guides without starting checks or enabling notifications');
 assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);
 await writeFile(path.join(output,'checks.json'),JSON.stringify({checks,errors,externalRequests:requests.length,synthetic:true},null,2));
 console.log(`${checks.length} keyboard/onboarding EXE checks passed; no external requests.`);
} catch(error) {
 if(page) console.log(JSON.stringify({checks,url:page.url(),trace:await page.evaluate(()=>window.qaKeyTrace?.slice(-35)),refreshes:await refreshes()}));
 throw error;
} finally {await app?.close();}
