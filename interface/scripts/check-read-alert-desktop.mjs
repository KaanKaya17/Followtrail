import {_electron as electron} from 'playwright-core';
import {execFileSync} from 'node:child_process';
import {mkdtemp,mkdir,readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
// A reader paused by a changed Instagram response, in the packaged EXE: the
// overview notice, request details, the value-free report, copy and GitHub
// link, the notification target, English labels and no network side effect.
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const python=process.env.TRACKER_BUILD_PYTHON || path.join(root,'interface/.cache/build-venv-314/Scripts/python.exe');
const version=JSON.parse(await readFile(path.join(root,'interface/package.json'),'utf8')).version;
const home=await mkdtemp(path.join(os.tmpdir(),'TrackerQA-'));
for(const script of ['interface/desktop/backend/seed_qa.py','interface/scripts/seed-read-alert-qa.py'])
 execFileSync(python,[path.join(root,script),'--home',home],{stdio:'pipe'});
const output=path.join(root,`artifacts/desktop-${version}-qa/read-alert`);await mkdir(output,{recursive:true});
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
const app=await electron.launch({executablePath:path.join(root,`artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`),args:[`--tracker-test-home=${home}`],env});
const page=await app.firstWindow();page.setDefaultTimeout(15000);
const errors=[],network=[],checks=[];
page.on('pageerror',error=>errors.push(error.message));page.on('request',request=>{if(/^https?:/.test(request.url()))network.push(request.url());});
const command=(operation,args={})=>page.evaluate(({operation,args})=>window.TrackerDesktop.command(operation,args),{operation,args});
const notice=page.locator('.notice.read-pause');
const modal=page.locator('#overlay-root dialog');
try {
 await page.setViewportSize({width:1280,height:900});
 await page.locator('.account-switcher').waitFor();const before=(await command('state')).state;
 const username=before.account.username;
 await notice.waitFor();
 const text=await notice.innerText();
 assert.match(text,/Otomatik okuma duraklatıldı/);assert.match(text,/Takipçiler · Instagram beklenen biçimde veri döndürmedi/);
 assert.match(text,/Eski kayıtların korunuyor\. Aynı hatalı istek tekrar gönderilmez/);
 await page.screenshot({path:path.join(output,'overview-notice.png')});
 checks.push('The overview says which reader paused, why, and that earlier records are kept');
 await notice.getByRole('button',{name:'Geliştiriciye bildir'}).click();
 const report=modal.locator('pre.read-report');await report.waitFor();
 const body=await report.innerText();
 for(const line of [`App: Windows ${version}`,'Reader: followers','Request: GET i.instagram.com/api/v1/friendships/{id}/followers/',
  'Reason: format','HTTP: 200','Missing fields: users.pk','Consecutive failures: 3','Automatic reads paused: yes'])
  assert.ok(body.split('\n').includes(line),line);
 assert.ok(!body.includes(username) && !body.includes(before.account.id),'report leaks the account');
 assert.match(await modal.innerText(),/GitHub hesabı gerekir/);
 await page.screenshot({path:path.join(output,'report-dialog.png')});
 checks.push('The report dialog shows the route template, reason, HTTP, missing field and streak without the username or ID');
 await modal.getByRole('button',{name:'Metni kopyala'}).click();
 await page.waitForFunction(()=>document.getElementById('toast').textContent.includes('Rapor panoya kopyalandı.'));
 assert.equal(await app.evaluate(({clipboard})=>clipboard.readText()),body);
 checks.push('Metni kopyala puts exactly the shown text on the clipboard');
 const opened=await page.evaluate(()=>window.TrackerDesktop.readReport('followers','open'));
 const url=new URL(opened.url);
 assert.equal(url.origin+url.pathname,'https://github.com/KaanKaya17/InstagramTracker/issues/new');
 assert.equal(url.searchParams.get('body'),body);assert.equal(opened.opened,false);
 assert.equal((await page.evaluate(()=>window.TrackerDesktop.readReport('profile','open'))).ok,false);
 await modal.getByRole('button',{name:'GitHub’da bildir'}).click();
 checks.push('GitHub’da bildir builds a new-issue link from the worker state, refuses a reader without a diagnosis, and opens nothing in the test home');
 await page.keyboard.press('Escape');
 await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows()[0].webContents.send('tracker:event',{type:'open_requests'}));
 await modal.getByRole('heading',{name:'İstek bilgileri'}).waitFor();
 const details=await modal.innerText();
 assert.match(details,/Okuma tanısı/);assert.match(details,/Yanıtta eksik alanlar: users\.pk/);assert.match(details,/HTTP 200/);
 assert.equal(await modal.getByRole('button',{name:'Geliştiriciye bildir'}).count(),1);
 await page.screenshot({path:path.join(output,'request-details.png')});
 checks.push('A notification click target opens request details with the missing field and a report button');
 await page.keyboard.press('Escape');
 await command('settings',{language:'en'});await page.waitForFunction(()=>document.documentElement.lang==='en');
 await notice.waitFor();
 const english=await notice.innerText();
 assert.match(english,/Your earlier records are kept/);
 await notice.getByRole('button',{name:'Report to the developer'}).click();
 assert.match(await modal.innerText(),/Report on GitHub/);
 await page.keyboard.press('Escape');
 checks.push('English labels the notice and the report dialog');
 await page.setViewportSize({width:800,height:900});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>document.documentElement.clientWidth),false);
 await page.screenshot({path:path.join(output,'overview-800.png')});
 const after=(await command('state')).state;
 assert.equal(after.gate.attempts,before.gate.attempts);assert.deepEqual(after.readHealth,before.readHealth);
 assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
 checks.push('No overflow at 800 px, request counter change, diagnosis change, renderer network request or script error');
 await writeFile(path.join(output,'summary.json'),JSON.stringify({checks,errors,network},null,2));console.log(`${checks.length} packaged read alert checks passed.`);
}catch(error){await page.screenshot({path:path.join(output,'failure.png')}).catch(()=>{});throw error;}
finally{await app.close();}
