import {_electron as electron} from 'playwright-core';
import {execFileSync} from 'node:child_process';
import {mkdtemp,mkdir,readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const python=path.resolve(root,'../InstagramCounter/.venv/Scripts/python.exe');
const version=JSON.parse(await readFile(path.join(root,'interface/package.json'),'utf8')).version;
const home=await mkdtemp(path.join(os.tmpdir(),'TrackerQA-'));
for(const [script,flags] of [['interface/desktop/backend/seed_qa.py',['--keyboard','--accounts']],['interface/scripts/seed-overview-qa.py',[]]])
 execFileSync(python,[path.join(root,script),'--home',home,...flags],{stdio:'pipe'});
const output=path.join(root,`artifacts/desktop-${version}-qa/overview`);await mkdir(output,{recursive:true});
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
const app=await electron.launch({executablePath:path.join(root,`artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`),args:[`--tracker-test-home=${home}`],env});
const page=await app.firstWindow();page.setDefaultTimeout(15000);
const checks=[],errors=[],network=[];
page.on('pageerror',error=>errors.push(error.message));page.on('request',request=>{if(/^https?:/.test(request.url()))network.push(request.url());});
const command=(operation,args={})=>page.evaluate(({operation,args})=>window.TrackerDesktop.command(operation,args),{operation,args});
const project=state=>app.evaluate(({BrowserWindow},state)=>BrowserWindow.getAllWindows()[0].webContents.send('tracker:event',{type:'result',state}),state);
try {
 await page.locator('.overview-account').waitFor();await page.locator('#daily-chart-root .chart-point').first().waitFor();
 const before=(await command('state')).state;
 assert.deepEqual(await page.locator('.stat-value').allTextContents(),['82','50',String(before.historyMeta.profileCount)]);
 assert.match(await page.locator('.stat-card').first().innerText(),/\+1 son kontrole göre/);
 assert.match(await page.locator('.stat-card').nth(1).innerText(),/Son kontrole göre aynı/);
 assert.equal(await page.locator('#overview-operation #operation-root').count(),1);
 assert.equal(await page.locator('.overview-monitor-title').innerText(),'Otomatik kontrol kapalı');
 assert.match(await page.locator('.monitor-times').innerText(),/Planlanmadı/);
 assert.equal(await page.locator('.snapshot-card').count(),0);
 const labels=await page.locator('#daily-chart-root .axis-label').allTextContents();
 assert.equal(new Set(labels.slice(0,-2)).size,labels.length-2);
 assert.match(await page.locator('#daily-chart-root .chart-trend').innerText(),/\+2/);
 await page.screenshot({path:path.join(output,'overview-light.png')});
 checks.push('Own account, honest per-check deltas and a readable seven-day graph lead; control times use the selected account schedule');
 await project({...before,monitor:{...before.monitor,enabled:true,held:false,reason:null,nextAt:Date.now()/1000+300}});
 await page.getByRole('heading',{name:'Otomatik kontrol açık',exact:true}).waitFor();
 assert.doesNotMatch(await page.locator('.monitor-times').innerText(),/Planlanmadı/);
 const minute=Math.floor(Date.now()/60000)*60+120;
 await project({...before,account:{...before.account,observedAt:minute+5},
   monitor:{...before.monitor,enabled:true,held:false,reason:null,nextAt:minute+15}});
 await page.waitForFunction(()=>document.querySelector('.monitor-times dd')?.textContent.includes(':05'));
 assert.deepEqual(await page.locator('.monitor-times dt').allTextContents(),['Hesabının son kontrolü','Sıradaki otomatik işlem']);
 const expected=await page.evaluate(minute=>[5,15].map(seconds=>window.TrackerI18n.formats('tr').preciseDate.format(new Date((minute+seconds)*1000))),minute);
 assert.deepEqual(await page.locator('.monitor-times dd').allTextContents(),expected);
 checks.push('Account observation and next queue task have distinct labels and retain seconds within the same minute');
 await project({...before,account:{...before.account,name:'<img src=x onerror=window.overviewInjected=true>'},history:{...before.history,'100':[]}});
 await page.getByText('<img src=x onerror=window.overviewInjected=true>',{exact:true}).waitFor();
 assert.equal(await page.evaluate(()=>window.overviewInjected),undefined);
 assert.match(await page.locator('.stat-card').first().innerText(),/İlk kayıt bir başlangıçtır/);
 await project(before);
 await project({...before,events:[{id:'no-owner-error',type:'error',title:'Synthetic diagnostic',at:Date.now()/1000}]});
 await page.locator('.overview-event[data-id="no-owner-error"]').waitFor();
 await project(before);
 const event=page.locator('.overview-event').filter({has:page.locator('.overview-event-values')}).first();
 assert.ok(await event.count());await event.click();await page.locator('.change-values').waitFor();await page.keyboard.press('Escape');
 const list=page.locator('.overview-event').filter({has:page.locator('.overview-list-diff')}).first();await list.click();
 await page.getByRole('heading',{name:'Liste değişiklikleri',exact:true}).waitFor();await page.keyboard.press('Escape');
 await page.locator('.stat-card[data-value="followers"]').click();await page.waitForFunction(()=>location.hash==='#lists');
 assert.equal(await page.locator('#list-owner').inputValue(),'100');assert.equal(await page.locator('#operation-root.inline-operation').count(),0);
 await page.locator('.sidebar nav a[href="#overview"]').click();
 checks.push('Inline old/new profile values and list differences open their existing details; count cards navigate to the correct account list');
 const blocked={...before,gate:{...before.gate,status:429,manualOnly:true,blockedUntil:Date.now()/1000+3600},lastError:{code:'limited',message:'HTTP 429'}};
 await project(blocked);await page.locator('.overview-monitor .operation-panel.paused').waitFor();
 assert.equal(await page.locator('main > .notice').count(),0);
 assert.equal(await page.locator('.operation-panel').evaluate(node=>getComputedStyle(node).position),'static');
 assert.match(await page.locator('.overview-monitor .operation-panel').innerText(),/Yeni deneme kendiliğinden başlamayacak/);
 await page.locator('.overview-monitor .operation-panel [data-action="requests"]').click();await page.getByRole('dialog').getByText('429',{exact:true}).waitFor();await page.keyboard.press('Escape');
 await page.screenshot({path:path.join(output,'overview-paused.png')});
 await project(before);
 await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows()[0].webContents.send('tracker:event',{type:'progress',phase:'waiting',until:Date.now()/1000+10,waitStartedAt:Date.now()/1000}));
 await page.locator('.overview-monitor [data-wait-countdown]').waitFor();assert.equal(await page.locator('.overview-monitor-title').innerText(),'Kontrol sürüyor');
 await page.locator('.sidebar a[href="#people"]').click();assert.equal(await page.locator('.operation-panel').evaluate(node=>getComputedStyle(node).position),'fixed');
 await page.locator('.sidebar nav a[href="#overview"]').click();assert.equal(await page.locator('.overview-monitor #operation-root').count(),1);
 await project(before);
 checks.push('Restriction and active progress stay inside one overview card; other routes retain progress, cancellation and diagnostic access');
 for(const language of ['en','es','pt-BR','tr']) {
  await command('settings',{language});await page.waitForFunction(language=>document.documentElement.lang===language,language);
  assert.equal(await page.locator('.overview-account strong').innerText(),'Örnek hesabım');
  assert.equal(await page.locator('.overview-page').evaluate(node=>node.scrollWidth<=node.clientWidth+1),true);
 }
 for(const theme of ['dark','oled']) {
  await command('settings',{theme});await page.screenshot({path:path.join(output,`overview-${theme}.png`)});
 }
 const width=await app.evaluate(({BrowserWindow})=>{const window=BrowserWindow.getAllWindows()[0];window.setSize(960,680);return window.getContentSize()[0];});
 await page.waitForFunction(width=>innerWidth===width,width);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 for(const language of ['en','es','pt-BR','tr']) {
  await command('settings',{language});await page.waitForFunction(language=>document.documentElement.lang===language,language);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  assert.equal(await page.locator('.overview-monitor').evaluate(node=>node.scrollWidth<=node.clientWidth+1),true);
 }
 await page.screenshot({path:path.join(output,'overview-small.png')});
 checks.push('Four languages preserve source names; light/dark/OLED and the minimum native window render without horizontal overflow');
 await command('switch_account',{id:'400'});await page.waitForFunction(()=>document.querySelector('.overview-account')?.textContent.includes('@qa.shop'));
 assert.equal(await page.locator('.stat-value').first().innerText(),'70');
 const after=(await command('state')).state;assert.equal(after.gate.attempts,before.gate.attempts);
 await project({...after,account:null,connected:false,events:[],history:{},listPreparation:null});
 await page.locator('.connect-banner').waitFor();assert.equal(await page.locator('.overview-account').count(),0);
 assert.equal(await page.locator('.stat-value').first().innerText(),'—');assert.equal(await page.locator('.stat-card[data-value="followers"]').isDisabled(),true);
 checks.push('Account switching replaces metrics; a disconnected empty workspace never invents zero counts or comparisons');
 assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
 await writeFile(path.join(output,'summary.json'),JSON.stringify({checks,errors,network,home},null,2));
 console.log(JSON.stringify({checks:checks.length,errors,network,output}));
} catch(error){await page.screenshot({path:path.join(output,'failure.png')}).catch(()=>{});throw error;}
finally{await app.close();}
