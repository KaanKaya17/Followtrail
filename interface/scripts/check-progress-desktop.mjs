import {_electron as electron} from 'playwright-core';
import {mkdtemp,mkdir,readFile,writeFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const version=JSON.parse(await readFile(path.join(root,'interface/package.json'),'utf8')).version;
const home=await mkdtemp(path.join(os.tmpdir(),'TrackerQA-'));
const output=path.join(root,'artifacts/progress-ui');await mkdir(output,{recursive:true});
execFileSync(path.resolve(root,'../InstagramCounter/.venv/Scripts/python.exe'),[path.join(root,'interface/desktop/backend/seed_qa.py'),'--home',home,'--keyboard']);
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
const checks=[],errors=[],requests=[];let app,page;
const scan={ownerId:'100',username:'qa.owner',direction:'followers',step:'page',page:4,completedPages:3,count:150,expected:250};
async function progress(phase,extra={}) {
 await app.evaluate((_,event)=>{
   globalThis.qaBackend.busy=true;
   globalThis.qaBackend.message({type:'progress',...event});
 },{phase,...extra});
}
async function result(gate={}) {
 await app.evaluate((_,gate)=>{
   const backend=globalThis.qaBackend;
   backend.message({type:'result',ok:true,state:{...backend.state,gate}});
 },gate);
}
const timers=()=>page.evaluate(()=>window.qaProgressTimers.size);
const counts=()=>app.evaluate(()=>globalThis.qaCommands);
async function wait(seconds=15,context=scan) {
 const now=Date.now()/1000;
 await progress('waiting',{scan:context,until:now+seconds,waitStartedAt:now});
 await page.locator('[data-wait-countdown]').waitFor();
}
try {
 app=await electron.launch({executablePath:path.join(root,`artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`),args:[`--tracker-test-home=${home}`],env});
 page=await app.firstWindow();page.setDefaultTimeout(12000);
 page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(!/^(file:|data:)/.test(r.url()))requests.push(r.url());});
 await page.getByRole('heading',{name:'Çevrende neler oluyor?'}).waitFor();
 await app.evaluate(({app})=>{
   const {createRequire}=process.getBuiltinModule('module');
   const {Backend}=createRequire(app.getAppPath()+'/package.json')('./desktop/service.cjs');
   const original=Backend.prototype.command;globalThis.qaCommands=[];
   Backend.prototype.command=function(operation,args){globalThis.qaBackend=this;globalThis.qaCommands.push(operation);return original.call(this,operation,args);};
 });
 await page.evaluate(()=>window.TrackerDesktop.command('state'));
 await page.evaluate(()=>{
   const start=window.setInterval,stop=window.clearInterval;
   window.qaProgressTimers=new Set();window.qaProgressTicks=0;window.qaProgressMutations=0;
   window.setInterval=function(callback,ms,...args){
     const id=start.call(window,()=>{if(ms===1000)window.qaProgressTicks++;callback(...args);},ms);
     if(ms===1000)window.qaProgressTimers.add(id);return id;
   };
   window.clearInterval=function(id){window.qaProgressTimers.delete(id);return stop.call(window,id);};
   new MutationObserver(records=>window.qaProgressMutations+=records.length).observe(document.getElementById('operation-root'),{subtree:true,childList:true,characterData:true,attributes:true});
 });
 await progress('waiting',{scan:{...scan,step:'profile',page:null,expected:null},until:Date.now()/1000+15,waitStartedAt:Date.now()/1000});
 await page.locator('#operation-root').getByText('Liste için güncel profil sayısı okunuyor…',{exact:true}).waitFor();
 assert.doesNotMatch(await page.locator('#operation-root').innerText(),/Sayfa 1|250/);
 checks.push('Initial profile preparation is separate from list pagination');

 await wait(12);const text=await page.locator('#operation-root').innerText();
 assert.equal(Number(await page.locator('#operation-root progress').getAttribute('max')),12);
 assert.match(text,/@qa.owner/);assert.match(text,/Sayfa 4 sırada/);assert.match(text,/3 sayfa alındı · 150 hesap kaydedildi/);
 assert.match(text,/Son profil sayısı: 250/);assert.match(text,/Toplam sayfa sayısı henüz bilinmiyor/);assert.doesNotMatch(text,/4\/12|%/);
 assert.match(text,/Bu sırada diğer ekranları kullanabilirsin; yeni ağ işlemleri sıra bekler/);
 assert.equal(await timers(),1);
 const first=await page.locator('[data-wait-countdown]').innerText();
 await page.evaluate(()=>window.qaCancelNode=document.querySelector('#operation-root [data-action="cancel"]'));
 const commands=await counts();await page.waitForTimeout(2100);
 assert.notEqual(await page.locator('[data-wait-countdown]').innerText(),first);
 assert.equal(await page.evaluate(()=>window.qaCancelNode===document.querySelector('#operation-root [data-action="cancel"]')),true);
 assert.equal(await page.locator('[data-wait-countdown]').getAttribute('aria-live'),'off');
 assert.deepEqual(await counts(),commands);
 await page.screenshot({path:path.join(output,'scan-wait-tr.png')});
 checks.push('Page, deduplicated saved count and mutable profile count stay visible; one local timer updates only the countdown and bar');

 await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows()[0].hide());
 await page.waitForFunction(()=>window.qaProgressTimers.size===0);
 const hidden=await page.evaluate(()=>({ticks:window.qaProgressTicks,mutations:window.qaProgressMutations}));
 await progress('waiting',{scan:{...scan,page:5,completedPages:4,count:200},until:Date.now()/1000+15,waitStartedAt:Date.now()/1000});
 await page.waitForTimeout(1200);
 assert.deepEqual(await page.evaluate(()=>({ticks:window.qaProgressTicks,mutations:window.qaProgressMutations})),hidden);
 await app.evaluate(({BrowserWindow})=>{const window=BrowserWindow.getAllWindows()[0];window.show();window.focus();});
 await page.locator('#operation-root').getByText('Sayfa 5 sırada',{exact:true}).waitFor();
 assert.match(await page.locator('#operation-root').innerText(),/4 sayfa alındı · 200 hesap kaydedildi/);
 assert.equal(await timers(),1);
 checks.push('Tray hiding stops countdown work and DOM updates; reopening restores the current page and remaining time');

 await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows()[0].minimize());
 await page.waitForFunction(()=>window.qaProgressTimers.size===0);
 await app.evaluate(({BrowserWindow})=>{const window=BrowserWindow.getAllWindows()[0];window.restore();window.focus();});
 await page.waitForFunction(()=>window.qaProgressTimers.size===1);
 checks.push('Native minimize/restore also stops and resumes the single timer');

 await wait(1);const beforeZero=await counts();
 await page.locator('[data-wait-countdown]').getByText('İstek sırası hazırlanıyor…',{exact:true}).waitFor();
 assert.equal(await timers(),0);assert.deepEqual(await counts(),beforeZero);
 await progress('request',{scan});
 await page.getByText('Sayfa 4 için Instagram yanıtı bekleniyor…',{exact:true}).waitFor();
 assert.equal(await page.locator('#operation-root progress').getAttribute('value'),null);
 await progress('list_received',{scan});await page.getByText('Sayfa 4 alındı',{exact:true}).waitFor();
 checks.push('Countdown zero never sends a request; network wait and received-page phases follow real backend events');

 await wait();await page.locator('#operation-root [data-action="cancel"]').click();
 await page.getByText('İptal isteniyor…',{exact:true}).waitFor();
 assert.equal(await timers(),0);assert.equal(await page.locator('#operation-root [data-action="cancel"]').isDisabled(),true);
 await progress('request',{scan});await page.getByText('İptal isteniyor…',{exact:true}).waitFor();
 assert.equal(await timers(),0);await result();await page.locator('.operation-panel').waitFor({state:'hidden'});
 checks.push('Cancellation stops countdown immediately; late worker progress cannot undo the cancellation state');

 await wait();await result();assert.equal(await timers(),0);
 await wait();await app.evaluate(()=>globalThis.qaBackend.fail('Synthetic worker failure'));
 await page.locator('.operation-panel').waitFor({state:'hidden'});assert.equal(await timers(),0);
 await app.evaluate(()=>{
   const backend=globalThis.qaBackend;backend.failure=null;
   backend.message({type:'ready',state:backend.state});
 });
 await result();
 checks.push('Completion and worker failure clear timers and stale progress');

 const until=Date.now()/1000+3600;
 await progress('limited',{http:429,until,scan});
 await page.getByText('İstekler duraklatıldı',{exact:true}).waitFor();assert.equal(await timers(),0);
 await result({blockedUntil:until,manualOnly:true,status:429});
 assert.match(await page.locator('#operation-root').innerText(),/Yeni deneme otomatik başlatılmaz/);
 assert.equal(await page.locator('#operation-root [data-action="cancel"]').count(),0);
 assert.equal(await page.locator('#operation-root progress').count(),0);
 await page.locator('#operation-root [data-action="requests"]').click();
 await page.locator('#overlay-root dialog').waitFor();
 assert.match(await page.locator('#overlay-root dialog').innerText(),/12–18 saniyelik rastgele/);
 assert.match(await page.locator('#overlay-root dialog').innerText(),/en az 15 dakika/);
 await page.keyboard.press('Escape');
 await page.screenshot({path:path.join(output,'cooldown-tr.png')});
 checks.push('HTTP 429 stays a static stopped state with request details, separate from normal request spacing');

 await result();await page.evaluate(()=>window.TrackerDesktop.command('settings',{language:'en'}));
 await page.getByRole('heading',{name:'What is happening in your circle?'}).waitFor();
 await page.locator('#toast.visible').waitFor({state:'hidden'});
 await wait(18);await page.getByText('Page 4 queued',{exact:true}).waitFor();
 assert.equal(Number(await page.locator('#operation-root progress').getAttribute('max')),18);
 assert.match(await page.locator('#operation-root').innerText(),/150 accounts saved/);
 await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows()[0].setSize(960,760));
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 const bounds=await page.locator('.operation-panel').boundingBox();assert.ok(bounds.x>=0 && bounds.x+bounds.width<=await page.evaluate(()=>innerWidth));
 await page.screenshot({path:path.join(output,'scan-wait-en-small.png')});
 await progress('waiting',{until:Date.now()/1000+4});
 assert.doesNotMatch(await page.locator('#operation-root').innerText(),/Page 4|150 accounts/);
 await result();
 checks.push('English and minimum window width render correctly; non-list operations carry no stale scan counts');
 await page.locator('main [data-action="requests"]').first().click();
 assert.match(await page.locator('#overlay-root dialog').innerText(),/random 12–18 second spacing/);
 assert.match(await page.locator('#overlay-root dialog').innerText(),/at least 15 minutes/);
 assert.match(await page.locator('#overlay-root dialog').innerText(),/this account 120\/hour and 1,200\/24 hours; all accounts 240\/hour and 2,400\/24 hours/);
 assert.match(await page.locator('#overlay-root dialog').innerText(),/not Meta quotas/);
 await page.keyboard.press('Escape');
 checks.push('TR/EN request information shows backend-owned 12–18 second pacing; countdown bars accept both bounds and login spacing stays 15 minutes');
 const beforeBudget=await counts();
 await app.evaluate((_,nextAt)=>{
   const b=globalThis.qaBackend;
   b.message({type:'result',ok:true,state:{...b.state,requestBudget:{...b.state.requestBudget,nextAt}}});
 },Date.now()/1000+3600);
 await page.locator('main [data-action="requests"]').first().click();
 assert.match(await page.locator('#overlay-root dialog').innerText(),/Next available budget time:/);
 assert.deepEqual(await counts(),beforeBudget);
 assert.equal(await timers(),0);
 await page.keyboard.press('Escape');
 checks.push('Local budget limits and deadline render without a new request or countdown timer');
 assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);
 await writeFile(path.join(output,'checks.json'),JSON.stringify({checks,errors,externalRequests:requests.length,synthetic:true,liveInstagram:false},null,2));
 console.log(`${checks.length} packaged progress checks passed; no external requests.`);
} catch(error) {
 console.log(JSON.stringify({checks,text:page?await page.locator('#operation-root').innerText():null}));throw error;
} finally {await app?.close();}
