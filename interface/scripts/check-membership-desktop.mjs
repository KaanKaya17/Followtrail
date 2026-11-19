import {_electron as electron} from 'playwright-core';
import {execFileSync} from 'node:child_process';
import {mkdtemp,mkdir,readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
// Follow history in the packaged EXE: a member who came back, one who left, the
// person panel's timeline, four languages, and no request or network side effect.
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const python=process.env.TRACKER_BUILD_PYTHON || path.join(root,'interface/.cache/build-venv-314/Scripts/python.exe');
const version=JSON.parse(await readFile(path.join(root,'interface/package.json'),'utf8')).version;
const home=await mkdtemp(path.join(os.tmpdir(),'TrackerQA-'));
for(const script of ['interface/desktop/backend/seed_qa.py','interface/scripts/seed-membership-qa.py'])
 execFileSync(python,[path.join(root,script),'--home',home],{stdio:'pipe'});
const output=path.join(root,`artifacts/desktop-${version}-qa/membership`);await mkdir(output,{recursive:true});
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
const app=await electron.launch({executablePath:path.join(root,`artifacts/Followtrail-Desktop-${version}/Followtrail.exe`),args:[`--tracker-test-home=${home}`],env});
const page=await app.firstWindow();page.setDefaultTimeout(15000);
const errors=[],network=[],checks=[];
page.on('pageerror',error=>errors.push(error.message));page.on('request',request=>{if(/^https?:/.test(request.url()))network.push(request.url());});
const command=(operation,args={})=>page.evaluate(({operation,args})=>window.TrackerDesktop.command(operation,args),{operation,args});
const rows=()=>page.locator('#list-rows .list-row');
const optionText=value=>page.locator(`#relation-filter option[value="${value}"]`).innerText();
try {
 await page.locator('.account-switcher').waitFor();const before=(await command('state')).state;
 assert.deepEqual(before.lists['100:followers'].movement,{scans:4,baselineAt:before.lists['100:followers'].snapshots[0].finishedAt,
  appliedAt:before.lists['100:followers'].snapshots[3].finishedAt,returned:1,left:1});
 checks.push('An older vault without a ledger is backfilled from its four retained complete scans on open');
 await page.locator('.sidebar nav a[href="#lists"]').click();
 await page.waitForFunction(()=>document.querySelectorAll('#list-rows .list-row').length===4);
 assert.equal(await optionText('returned'),'Geri gelenler · 1');assert.equal(await optionText('left'),'Ayrılanlar · 1');
 assert.equal(await page.locator('#relation-filter option[value="returned"]').isDisabled(),false);
 const badge=name=>page.locator('#list-rows .list-row',{hasText:name}).locator('.list-state').innerText();
 assert.equal(await badge('@qa.200'),'Geri döndü');assert.equal(await badge('@qa.203'),'Yeni görüldü');
 checks.push('The full list marks a returning follower as Geri döndü, not as newly seen');
 await page.locator('#relation-filter').selectOption('returned');
 await page.waitForFunction(()=>document.querySelectorAll('#list-rows .list-row').length===1);
 const back=await rows().first().innerText();
 assert.match(back,/@qa\.200/);assert.match(back,/Geri döndü: /);assert.match(back,/Önceki çıkış: .+ arası/);assert.match(back,/İlk görülme: ilk tam listede vardı/);
 assert.match(await page.locator('.list-movement-note').innerText(),/kesin olay saati değildir/);
 await page.locator('#relation-filter').scrollIntoViewIfNeeded();await page.evaluate(()=>document.getElementById('relation-filter').scrollIntoView({block:'start'}));await page.screenshot({path:path.join(output,'returned.png')});
 checks.push('Geri gelenler lists the member with return time, previous exit window and first sighting');
 await page.locator('#relation-filter').selectOption('left');
 await page.waitForFunction(()=>document.querySelectorAll('#list-rows .list-row').length===1);
 const left=await rows().first().innerText();
 assert.match(left,/@qa\.205/);assert.match(left,/Ayrıldı/);assert.match(left,/Son görülme: /);assert.match(left,/Listeden çıktı: .+ arası/);
 await page.evaluate(()=>document.getElementById('relation-filter').scrollIntoView({block:'start'}));await page.screenshot({path:path.join(output,'left.png')});
 checks.push('Ayrılanlar keeps a departed member after it is gone from the latest scan, with last-seen and exit window');
 await page.locator('#relation-filter').selectOption('returned');
 await page.waitForFunction(()=>document.querySelectorAll('#list-rows .list-row').length===1);
 await rows().first().locator('.list-person').click();
 const panel=page.locator('#person-membership');await panel.waitFor({state:'visible'});
 const history=await panel.innerText();
 assert.match(history,/Takip geçmişi/);assert.match(history,/Takipçilerinde/);assert.match(history,/Geri döndü/);
 assert.match(history,/Önceki çıkış: /);assert.match(history,/Toplam 1 kez geri döndü/);
 await page.screenshot({path:path.join(output,'person-panel.png')});
 checks.push('The person panel shows the follow history from the local ledger');
 await page.keyboard.press('Escape');
 const expected={en:['Came back · 1','Left · 1'],es:['Regresaron · 1','Se fueron · 1'],'pt-BR':['Voltaram · 1','Saíram · 1'],tr:['Geri gelenler · 1','Ayrılanlar · 1']};
 for(const [language,[returned,gone]] of Object.entries(expected)) {
  await command('settings',{language});await page.waitForFunction(language=>document.documentElement.lang===language,language);
  await page.waitForFunction(()=>document.querySelectorAll('#list-rows .list-row').length===1);
  assert.equal(await optionText('returned'),returned);assert.equal(await optionText('left'),gone);
  assert.match(await rows().first().innerText(),{en:/Came back: .*Previous exit: between/,es:/Regresó: .*Salida anterior: entre/,
   'pt-BR':/Voltou: .*Saída anterior: entre/,tr:/Geri döndü: .*Önceki çıkış: .* arası/}[language]);
  if(language==='en'){await page.evaluate(()=>document.getElementById('relation-filter').scrollIntoView({block:'start'}));await page.screenshot({path:path.join(output,'returned-en.png')});}
 }
 checks.push('All four languages label both views and repaint the rows in the chosen language');
 const overflow=await page.evaluate(()=>[...document.querySelectorAll('#list-rows .list-row')].some(row=>row.scrollWidth>row.clientWidth+1));
 assert.equal(overflow,false);
 const after=(await command('state')).state;
 assert.equal(after.gate.attempts,before.gate.attempts);assert.equal(after.requestBudget.nextAt,before.requestBudget.nextAt);
 assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
 checks.push('No row overflow, request counter change, renderer network request or script error');
 await writeFile(path.join(output,'summary.json'),JSON.stringify({checks,errors,network},null,2));console.log(`${checks.length} packaged follow history checks passed.`);
}catch(error){await page.screenshot({path:path.join(output,'failure.png')}).catch(()=>{});throw error;}
finally{await app.close();}
