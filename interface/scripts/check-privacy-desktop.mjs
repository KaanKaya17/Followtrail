import {_electron as electron} from 'playwright-core';
import {waitForState} from './wait-for-account.mjs';
import {execFileSync} from 'node:child_process';
import {mkdtemp,mkdir,readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const python=process.env.TRACKER_BUILD_PYTHON || path.resolve(root,'../InstagramCounter/.venv/Scripts/python.exe');
const version=JSON.parse(await readFile(path.join(root,'interface/package.json'),'utf8')).version;
const home=await mkdtemp(path.join(os.tmpdir(),'TrackerQA-'));
execFileSync(python,[path.join(root,'interface/desktop/backend/seed_qa.py'),'--home',home],{stdio:'pipe'});
const output=path.join(root,`artifacts/desktop-${version}-qa/privacy`);await mkdir(output,{recursive:true});
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
const errors=[],network=[],checks=[];let app,page;
const command=(operation,args={})=>page.evaluate(({operation,args})=>window.TrackerDesktop.command(operation,args),{operation,args});
try {
  app=await electron.launch({executablePath:path.join(root,`artifacts/Followtrail-Desktop-${version}/Followtrail.exe`),args:[`--tracker-test-home=${home}`],env});
  page=await app.firstWindow();page.setDefaultTimeout(15000);
  page.on('pageerror',error=>errors.push(error.message));page.on('request',request=>{if(/^https?:/.test(request.url()))network.push(request.url());});
  await page.locator('.account-switcher').waitFor();
  const before=(await command('state')).state;
  assert.equal(before.version,version);
  assert.equal(before.settings.historyRetentionDays,0);assert.equal(before.settings.dailyRetentionDays,0);
  checks.push('Existing account defaults preserve both kinds of archives');
  await page.locator('.sidebar nav a[href="#settings"]').click();
  for(const [language,title] of [['tr','Veri saklama'],['en','Data retention'],['es','Conservación de datos'],['pt-BR','Retenção de dados']]) {
    await page.locator('#app-language').selectOption(language);
    await waitForState(page,result=>!result.busy && result.state.settings.language===language);
    await page.locator('[data-action="retention-settings"]').click();
    await page.getByRole('dialog').getByRole('heading',{name:title,exact:true}).waitFor();
    assert.deepEqual(await page.locator('#retention-history option').evaluateAll(nodes=>nodes.map(node=>node.value)),['0','30','90','365']);
    assert.equal(await page.locator('#retention-daily').inputValue(),'0');
    assert.equal(await page.getByRole('dialog').evaluate(node=>node.scrollWidth>node.clientWidth+1),false);
    await page.screenshot({path:path.join(output,`retention-${language}.png`),fullPage:true});
    await page.keyboard.press('Escape');
  }
  checks.push('Four languages expose separate selectors, a destructive-action explanation and bounded dialog layout');
  await page.locator('#app-language').selectOption('tr');
  await waitForState(page,result=>!result.busy && result.state.settings.language==='tr');
  await page.locator('[data-action="retention-settings"]').click();
  await page.locator('#retention-history').selectOption('30');await page.locator('#retention-daily').selectOption('90');
  assert.equal(await page.locator('#retention-history').inputValue(),'30');assert.equal(await page.locator('#retention-daily').inputValue(),'90');
  await page.locator('[data-action="retention-apply"]').click();
  const configured=(await waitForState(page,result=>!result.busy && result.state?.settings.historyRetentionDays===30)).state;
  assert.equal(configured.settings.dailyRetentionDays,90);assert.equal(configured.privacyMaintenance.failed,false);
  assert.deepEqual(configured.gate,before.gate);
  checks.push('IPC applies independent policies without changing network pacing or limits');
  await page.locator('.sidebar nav a[href="#people"]').click();
  await page.locator('[data-action="person"][data-id="200"]').first().click();
  await page.locator('.profile-more > summary').click();
  await page.locator('[data-action="remove-person"][data-id="200"]').click();
  assert.match(await page.getByRole('dialog').innerText(),/Başka hesapların listelerindeki üyelik bilgisi/);
  await page.locator('[data-action="remove-confirm"]').click();
  await waitForState(page,result=>!result.busy && !result.state.people.some(person=>person.id==='200'));
  assert.equal((await command('state')).state.people.some(person=>person.id==='201'),true);
  checks.push('Profile removal explains archive scope and keeps other saved profiles');
  await app.close();app=null;
  execFileSync(python,['-c',[
    'import sys',`sys.path.insert(0,${JSON.stringify(path.join(root,'interface/desktop/backend'))})`,
    'from engine import Engine',`e=Engine(${JSON.stringify(home)})`,
    "assert '200' not in e.data['people'] and '200' not in e.data['history']",
    "assert '200:followers' not in e.data['lists']",
    "assert '200' in e.data['lists']['100:followers']['snapshots'][0]['members']",
    "assert all(row.get('personId')!='200' for row in e.data['events'])",
    "assert e.data['settings']['historyRetentionDays']==30 and e.data['settings']['dailyRetentionDays']==90"
  ].join('\n')],{stdio:'pipe'});
  checks.push('Real encrypted store restart confirms erasure, retained membership facts and saved preferences');
  assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
  await writeFile(path.join(output,'result.json'),JSON.stringify({checks,errors,network,home,liveInstagram:false,version},null,2));
  console.log(`${checks.length} privacy checks passed; isolated synthetic records, no Instagram requests.`);
} catch(error) {
  if(page)await page.screenshot({path:path.join(output,'failure.png'),fullPage:true}).catch(()=>{});
  if(page)await writeFile(path.join(output,'failure.json'),JSON.stringify({home,checks,error:String(error),state:(await command('state').catch(()=>({}))).state},null,2));
  throw error;
} finally {if(app)await app.close();}
