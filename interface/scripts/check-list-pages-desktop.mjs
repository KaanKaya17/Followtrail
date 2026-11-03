import {waitForAccount} from './wait-for-account.mjs';
// Production EXE, pipe, DPAPI and 10,000-member synthetic lists. No account access.
import {_electron as electron} from 'playwright-core';
import {execFileSync} from 'node:child_process';
import {mkdtemp,mkdir,readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const version=JSON.parse(await readFile(path.join(root,'interface/package.json'),'utf8')).version;
const home=await mkdtemp(path.join(os.tmpdir(),'TrackerQA-'));
const output=path.join(root,`artifacts/desktop-${version}-qa/list-pages`);await mkdir(output,{recursive:true});
execFileSync(process.env.TRACKER_BUILD_PYTHON || path.resolve(root,'../InstagramCounter/.venv/Scripts/python.exe'),
  [path.join(root,'interface/desktop/backend/seed_qa.py'),'--home',home,'--performance','--accounts']);
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
const app=await electron.launch({executablePath:path.join(root,`artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`),args:[`--tracker-test-home=${home}`],env});
const page=await app.firstWindow();page.setDefaultTimeout(15000);
const errors=[],external=[],checks=[];
page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(!/^(file:|data:)/.test(r.url()))external.push(r.url());});
const state=async()=>(await page.evaluate(()=>window.TrackerDesktop.command('state'))).state;
const ready=()=>page.waitForFunction(()=>document.getElementById('list-rows')?.getAttribute('aria-busy')==='false');
try {
  await page.locator('.account-switcher').waitFor();
  const before=await state();
  assert.equal(before.lists['100:followers'].snapshots.at(-1).count,10000);
  assert.equal(Object.hasOwn(before.lists['100:followers'].snapshots.at(-1),'members'),false);
  checks.push('Initial state carries list metadata instead of 20,000 archived members');
  await page.locator('.sidebar a[href="#lists"]').click();await ready();
  assert.equal(await page.locator('#list-rows .list-row').count(),100);
  assert.equal(await page.locator('[data-action="more-list"]').count(),0);
  for(const target of [200,300]) {
    await page.locator('#list-pagination').scrollIntoViewIfNeeded();
    await page.waitForFunction(target=>document.querySelectorAll('#list-rows .list-row').length===target,target);
  }
  const ids=await page.locator('#list-rows .list-person').evaluateAll(rows=>rows.map(r=>r.dataset.id));
  assert.equal(new Set(ids).size,300);
  assert.deepEqual(ids,Array.from({length:300},(_,i)=>String(2000+i)));
  await page.screenshot({path:path.join(output,'infinite-scroll.png')});
  checks.push('Scrolling loads the next two local pages in order without duplicate rows or a pagination button');
  await page.locator('#list-search').fill('qa.11999');await ready();
  assert.equal(await page.locator('#list-rows .list-row').count(),1);
  assert.equal(await page.locator('#list-rows .list-person').getAttribute('data-id'),'11999');
  await page.locator('#list-search').fill('');await ready();
  assert.equal(await page.locator('#list-rows .list-row').count(),100);
  assert.deepEqual((await state()).gate,before.gate);
  checks.push('Search finds a member beyond the loaded pages and clearing it starts at the first page without quota or gate changes');
  const context={accountId:'100',revision:before.listsRevision,ownerId:'100',direction:'followers',scanId:'after',previousScanId:'before',comparison:false,relation:'all',query:'',offset:9900};
  const tail=await page.evaluate(args=>window.TrackerDesktop.command('list_page',args),context);
  assert.equal(tail.ok,true);assert.equal(tail.page.items.length,100);assert.equal(tail.page.nextOffset,null);
  assert.ok(Buffer.byteLength(JSON.stringify(tail))<20000);
  const foreign=await page.evaluate(args=>window.TrackerDesktop.command('list_page',{...args,accountId:'400'}),context);
  assert.equal(foreign.ok,false);assert.equal(foreign.page,undefined);
  await page.locator('.account-switcher').click();await page.locator('.saved-account[data-id="400"]').click();
  await waitForAccount(page,'400');
  const stale=await page.evaluate(args=>window.TrackerDesktop.command('list_page',args),context);
  assert.equal(stale.ok,false);assert.equal(stale.page,undefined);
  assert.equal(await page.locator('#list-rows').count(),0);
  checks.push('Actual pipe bounds page payloads and refuses foreign accounts or stale account views after switching');
  assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
  await writeFile(path.join(output,'checks.json'),JSON.stringify({checks,errors,external,liveInstagram:false},null,2));
  console.log(`${checks.length} large-list packaged checks passed; no external requests.`);
} catch(error) {
  await page.screenshot({path:path.join(output,'failure.png')}).catch(()=>{});throw error;
} finally {await app.close();}
