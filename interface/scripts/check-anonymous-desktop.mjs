import { _electron as electron } from 'playwright-core';
import { mkdtemp, mkdir, readFile, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const version=JSON.parse(await readFile(path.join(root,'interface/package.json'),'utf8')).version;
const home=await mkdtemp(path.join(os.tmpdir(),'TrackerQA-'));
const output=path.join(root,'artifacts/anonymous-ui');await mkdir(output,{recursive:true});
execFileSync(path.resolve(root,'../InstagramCounter/.venv/Scripts/python.exe'),[path.join(root,'interface/desktop/backend/seed_qa.py'),'--home',home,'--anonymous']);
const checks=[],errors=[],external=[];let app;
try {
 const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
 app=await electron.launch({executablePath:path.join(root,`artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`),args:[`--tracker-test-home=${home}`],env});
 const page=await app.firstWindow();page.setDefaultTimeout(12000);
 page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(!/^(file:|data:)/.test(r.url()))external.push(r.url());});
 await page.getByRole('heading',{name:'Çevrende neler oluyor?'}).waitFor();
 async function person(id) {
   await page.locator('.sidebar a[href="#people"]').click();await page.locator(`.person-title[data-id="${id}"]`).click();
 }
 async function close(){await page.getByRole('button',{name:'Kapat',exact:true}).click();}
 await person('200');
 const evidence=page.locator('.access-evidence');
 await evidence.getByRole('heading',{name:'Güçlü engellenme şüphesi',exact:true}).waitFor();
 assert.match(await evidence.innerText(),/Aynı sayısal hesap kimliği oturumsuz sayfada doğrulandı/);
 assert.match(await evidence.innerText(),/hesap çerezi ve Authorization taşımayan/);
 await page.locator('.anonymous-evidence').scrollIntoViewIfNeeded();await page.screenshot({path:path.join(output,'strong-tr.png')});checks.push('Verified numeric identity and comparison method are explained in the Turkish profile drawer');await close();
 await person('201');assert.match(await evidence.innerText(),/Anonim kontrol tamamlanamadı/);
 assert.match(await evidence.innerText(),/anonim isteği sınırladı/);assert.doesNotMatch(await evidence.innerText(),/Güçlü engellenme şüphesi/);
 await page.locator('.anonymous-evidence').scrollIntoViewIfNeeded();await page.screenshot({path:path.join(output,'limited-tr.png')});checks.push('Anonymous 429 is inconclusive, with cooldown and no-retry explanation');await close();
 await person('300');assert.match(await evidence.innerText(),/İki kontrolde de bulunamadı/);
 assert.match(await evidence.innerText(),/kullanıcı adı değiştirilmiş olabilir/);
 assert.match(await evidence.innerText(),/ad değişikliğiyle birlikte engelleme/);checks.push('Double absence explains deactivation, deletion and rename-plus-block uncertainty');await close();
 await page.locator('.sidebar a[href="#notifications"]').click();await page.locator('.notification-log-row').first().click();
 assert.match(await page.getByRole('dialog').innerText(),/Oturumsuz karşılaştırma/);checks.push('Notification log retains the exact comparison evidence');await close();
 await page.evaluate(()=>window.TrackerDesktop.command('settings',{language:'en'}));await app.close();app=null;
 app=await electron.launch({executablePath:path.join(root,`artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`),args:[`--tracker-test-home=${home}`],env});
 const english=await app.firstWindow();english.setDefaultTimeout(12000);english.on('pageerror',e=>errors.push(e.message));
 english.on('request',r=>{if(!/^(file:|data:)/.test(r.url()))external.push(r.url());});
 await english.getByRole('heading',{name:'What is happening in your circle?'}).waitFor();
 await english.locator('.sidebar a[href="#people"]').click();await english.locator('.person-title[data-id="200"]').click();
 await english.getByRole('heading',{name:'Strong suspicion of a block',exact:true}).waitFor();
 assert.match(await english.locator('.anonymous-evidence').innerText(),/same numeric account ID/);
 assert.equal(await english.getByRole('dialog').evaluate(el=>el.scrollWidth>el.clientWidth+1),false);
 await english.locator('.anonymous-evidence').scrollIntoViewIfNeeded();await english.screenshot({path:path.join(output,'strong-en.png')});checks.push('English explanations and frozen identity evidence survive process restart');
 const state=await english.evaluate(()=>window.TrackerDesktop.command('state'));
 assert.equal(state.state.version,version);
 assert.equal(state.state.gate.attempts,2);assert.equal(state.state.gate.manualOnly,true);
 assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
 await writeFile(path.join(output,'checks.json'),JSON.stringify({checks,errors,externalRequests:external.length,liveInstagram:false},null,2));
 console.log(`${checks.length} anonymous-comparison EXE checks passed; external requests ${external.length}.`);
} finally {await app?.close();}
