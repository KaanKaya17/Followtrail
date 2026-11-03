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
for(const [script,flags] of [['interface/desktop/backend/seed_qa.py',['--accounts']],['interface/scripts/seed-notifications-qa.py',[]]])
 execFileSync(python,[path.join(root,script),'--home',home,...flags],{stdio:'pipe'});
const output=path.join(root,`artifacts/desktop-${version}-qa/notifications`);await mkdir(output,{recursive:true});
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
let app,page;const errors=[],network=[],checks=[];
const command=(operation,args={})=>page.evaluate(({operation,args})=>window.TrackerDesktop.command(operation,args),{operation,args});
const row=id=>page.locator(`.notification-card[data-id="${id}"]`);
const type=key=>page.locator(`[data-action="notification-type"][data-value="${key}"]`);
async function launch() {
 app=await electron.launch({executablePath:path.join(root,`artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`),args:[`--tracker-test-home=${home}`],env});
 page=await app.firstWindow();page.setDefaultTimeout(15000);
 page.on('pageerror',error=>errors.push(error.message));page.on('request',request=>{if(/^https?:/.test(request.url()))network.push(request.url());});
 await page.locator('.account-switcher').waitFor();await page.locator('.sidebar nav a[href="#notifications"]').click();await row('qa-multi-change').waitFor();
}
try {
 await launch();await command('settings',{theme:'light'});const before=(await command('state')).state;
 const main=row('qa-multi-change');await main.locator('.avatar img').waitFor();
 assert.equal(await main.locator('.notification-card-heading strong').innerText(),'@qa.deniz · 3 değişiklik');
 assert.deepEqual(await main.locator('.notification-change').first().locator('.notification-pair > .notification-before, .notification-pair > strong').allTextContents(),['81','82']);
 assert.equal(await main.locator('.notification-delta').innerText(),'+1');
 assert.equal(await main.locator('img').count(),1);assert.equal(await page.evaluate(()=>window.notificationInjected),undefined);
 assert.equal(await row('qa-zero-notification').locator('.notification-pair > strong').innerText(),'0');
 assert.equal(await row('qa-unknown-notification').locator('.notification-pair').count(),0);
 assert.equal(await row('qa-legacy-notification').locator('.notification-member-names').count(),0);
 await page.screenshot({path:path.join(output,'notifications-light.png'),fullPage:true});
 await main.screenshot({path:path.join(output,'profile-change-card.png')});
 checks.push('Local avatars use identity while headings retain frozen usernames; numeric deltas preserve zero, escape text, and never turn unknown history into zero');
 await app.evaluate(({BrowserWindow},state)=>BrowserWindow.getAllWindows()[0].webContents.send('tracker:event',{type:'result',state:{...state,lastError:{code:'limited',message:'HTTP 429'},gate:{...state.gate,status:429,manualOnly:true,blockedUntil:Date.now()/1000+3600}}}),before);
 await page.locator('#notifications-operation .operation-panel.paused').waitFor();
 assert.equal(await page.locator('#notifications-operation .operation-panel').evaluate(node=>getComputedStyle(node).position),'static');
 assert.equal(await page.locator('main > .notice').count(),0);
 await app.evaluate(({BrowserWindow},state)=>BrowserWindow.getAllWindows()[0].webContents.send('tracker:event',{type:'result',state}),before);
 const mixed=row('qa-mixed-notification');assert.equal(await mixed.locator('.notification-change').count(),3);
 assert.match(await mixed.locator('.notification-more').innerText(),/1/);
 await type('lists').click();assert.equal(await main.count(),0);assert.equal(await mixed.count(),1);
 assert.equal(await mixed.locator('.notification-change').first().locator('.notification-list-preview').count(),1);
 assert.match(await mixed.innerText(),/@qa.new700, @qa.new701/);assert.doesNotMatch(await mixed.innerText(),/@qa.new702/);
 assert.match(await mixed.innerText(),/ve 1 hesap daha/);
 await mixed.screenshot({path:path.join(output,'mixed-list-card.png')});
 await mixed.click();await page.locator('.change-log-details > li').first().waitFor();assert.equal(await page.locator('.change-log-details > li').count(),4);
 await page.keyboard.press('Escape');await type('profile').click();assert.equal(await mixed.count(),1);
 assert.equal((await command('state')).state.notificationLog.find(entry=>entry.id==='qa-mixed-notification').readAt!=null,true);
 checks.push('Type filters keep mixed observations grouped; bounded frozen-name previews open the complete four-change log and persist its read state');
 await page.locator('[data-action="notification-filter"][data-value="unread"]').click();await type('access').click();
 const access=before.notificationLog.filter(entry=>entry.changes.some(change=>change.category==='access'));
 assert.ok(access.length);for(const entry of access)await command('read_notification',{id:entry.id});
 await page.getByRole('heading',{name:'Bu filtrelere uygun kayıt yok.',exact:true}).waitFor();
 await page.getByRole('button',{name:'Tüm bildirimleri göster',exact:true}).click();
 assert.equal(await type('all').getAttribute('aria-pressed'),'true');assert.equal(await main.count(),1);
 assert.equal(await page.locator('[data-action="notification-filter"][data-value="all"]').getAttribute('aria-pressed'),'true');
 const after=(await command('state')).state;
 assert.equal(after.notificationLog.find(entry=>entry.id==='qa-multi-change').readAt,null);
 assert.deepEqual(after.gate,before.gate);assert.deepEqual(after.notificationRules,before.notificationRules);
 checks.push('Unread plus type filters show an accurate empty state; reset clears both filters and filtering never reads entries or changes requests/preferences');
 for(const language of ['en','es','pt-BR','tr']) {
  await command('settings',{language});await page.waitForFunction(language=>document.documentElement.lang===language,language);
  assert.match(await row('qa-multi-change').locator('.notification-card-heading strong').innerText(),/@qa.deniz/);
  assert.equal(await page.locator('.notifications-page').evaluate(node=>node.scrollWidth<=node.clientWidth+1),true);
 }
 for(const theme of ['dark','oled']) {await command('settings',{theme});await page.screenshot({path:path.join(output,`notifications-${theme}.png`),fullPage:true});}
 const width=await app.evaluate(({BrowserWindow})=>{const window=BrowserWindow.getAllWindows()[0];window.setSize(960,680);return window.getContentSize()[0];});
 await page.waitForFunction(width=>innerWidth===width,width);
 for(const language of ['pt-BR','es','en','tr']) {
  await command('settings',{language});await page.waitForFunction(language=>document.documentElement.lang===language,language);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 }
 await page.screenshot({path:path.join(output,'notifications-small.png')});
 checks.push('Four languages and light/dark/OLED themes preserve source text and fit the 960×680 native window');
 await type('lists').click();await page.locator('[data-action="notification-filter"][data-value="unread"]').click();
 await command('switch_account',{id:'400'});await page.waitForFunction(()=>location.hash==='#overview');
 await page.locator('.sidebar nav a[href="#notifications"]').click();assert.equal(await type('all').getAttribute('aria-pressed'),'true');assert.equal(await main.count(),0);
 await command('switch_account',{id:'100'});await page.waitForFunction(()=>location.hash==='#overview');
 await page.locator('.sidebar nav a[href="#notifications"]').click();assert.equal(await row('qa-mixed-notification').locator('.notification-unread-dot').count(),0);
 await app.close();app=null;await launch();assert.equal(await row('qa-mixed-notification').locator('.notification-unread-dot').count(),0);
 assert.equal((await command('state')).state.gate.attempts,before.gate.attempts);
 checks.push('Account switching resets filters and removes foreign records; read state survives a real process restart');
 assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
 await writeFile(path.join(output,'summary.json'),JSON.stringify({checks,errors,network,home},null,2));console.log(JSON.stringify({checks:checks.length,errors,network,output}));
}catch(error){await page?.screenshot({path:path.join(output,'failure.png'),fullPage:true}).catch(()=>{});throw error;}
finally{await app?.close();}
