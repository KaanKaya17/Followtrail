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
for(const script of ['interface/desktop/backend/seed_qa.py','interface/scripts/seed-list-recovery-qa.py'])
 execFileSync(python,[path.join(root,script),'--home',home],{stdio:'pipe'});
const output=path.join(root,`artifacts/desktop-${version}-qa/list-recovery`);await mkdir(output,{recursive:true});
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
const app=await electron.launch({executablePath:path.join(root,`artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`),args:[`--tracker-test-home=${home}`],env});
const page=await app.firstWindow();page.setDefaultTimeout(15000);
const errors=[],network=[],checks=[];
page.on('pageerror',error=>errors.push(error.message));page.on('request',request=>{if(/^https?:/.test(request.url()))network.push(request.url());});
const command=(operation,args={})=>page.evaluate(({operation,args})=>window.TrackerDesktop.command(operation,args),{operation,args});
try {
 await page.locator('.account-switcher').waitFor();const before=(await command('state')).state;
 await page.locator('.sidebar nav a[href="#lists"]').click();await page.locator('#list-owner').selectOption('200');
 await page.waitForFunction(()=>document.querySelectorAll('#list-rows .list-row').length===78);
 assert.equal(await page.getByRole('button',{name:'Farkları göster',exact:true}).isDisabled(),true);
 assert.match(await page.locator('.list-coverage').innerText(),/78 hesap/);
 assert.equal(await page.getByText(/Önceki yarım listeden 76 hesap/).count(),0);
 await page.locator('#list-search').fill('qa.578');await page.locator('#list-rows .list-row').waitFor();
 assert.equal(await page.locator('#list-rows .list-row').count(),1);await page.locator('#list-search').fill('');
 await page.waitForFunction(()=>document.querySelectorAll('#list-rows .list-row').length===78);
 await page.screenshot({path:path.join(output,'newer-partial.png')});
 checks.push('A newer 78-member partial is published instead of the retained 76, including new names/search; comparison remains disabled');
 await page.locator('#list-owner').selectOption('100');
 await page.getByRole('button',{name:'Takip edilen',exact:true}).click();
 await page.getByText('Bu liste tek tarama içindeki tamamlama turuyla oluşturuldu.',{exact:true}).waitFor();
 checks.push('A completed repaired list explains its collection method');
 for(const language of ['en','es','pt-BR','tr']) {
  await command('settings',{language});await page.waitForFunction(language=>document.documentElement.lang===language,language);
  assert.equal(await page.locator('#list-rows .list-row').count(),2);
 }
 assert.equal((await command('state')).state.gate.attempts,before.gate.attempts);
 assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
 checks.push('All four languages preserve rows and request counters; no renderer network or script error');
 await writeFile(path.join(output,'summary.json'),JSON.stringify({checks,errors,network},null,2));console.log(`${checks.length} packaged list recovery checks passed.`);
}catch(error){await page.screenshot({path:path.join(output,'failure.png')}).catch(()=>{});throw error;}
finally{await app.close();}
