import { _electron as electron } from 'playwright-core';
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
for(const script of ['interface/desktop/backend/seed_qa.py','interface/scripts/seed-story-qa.py'])
 execFileSync(python,[path.join(root,script),'--home',home,...(script.includes('seed_qa.py')?['--accounts']:[])],{stdio:'pipe'});
const output=path.join(root,`artifacts/desktop-${version}-qa/stories`);await mkdir(output,{recursive:true});
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
const app=await electron.launch({executablePath:path.join(root,`artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`),args:[`--tracker-test-home=${home}`],env});
const page=await app.firstWindow();page.setDefaultTimeout(15000);
const errors=[],network=[],checks=[];
page.on('pageerror',error=>errors.push(error.message));
page.on('request',request=>{if(/^https?:/.test(request.url()))network.push(request.url());});
const command=(operation,args={})=>page.evaluate(({operation,args})=>window.TrackerDesktop.command(operation,args),{operation,args});
const expectMonth=month=>page.waitForFunction(month=>document.querySelector('.story-calendar')?.dataset.month===month,month);
const expectImage=()=>page.waitForFunction(()=>document.querySelector('#story-player img')?.naturalWidth===360);
async function openArchive(){
 await page.locator('[data-action="person"][data-id="200"]').first().click();
 await page.locator('[data-action="story-archive"]').click();
 await page.locator('.story-calendar').waitFor();
}
try {
 await page.locator('.account-switcher').waitFor();
 const before=(await command('state')).state;
 await page.locator('.sidebar a[href="#people"]').click();await openArchive();
 assert.equal(await page.locator('.story-calendar-day.has-stories small').innerText(),'2 hikâye');
 assert.equal(await page.locator('#story-player').count(),0);
 await page.screenshot({path:path.join(output,'story-calendar.png')});
 checks.push('Archive initially shows a full calendar with saved counts and no premature media load');
 await command('settings',{compact:true});
 assert.equal((await command('state')).state.storyDetail,null);
 for(const month of ['2026-12','2026-11']) {
  await page.locator('.story-calendar-heading button').first().click();await expectMonth(month);
 }
 for(const month of ['2026-12','2027-01']) {
  await page.locator('.story-calendar-heading button').last().click();await expectMonth(month);
 }
 checks.push('Real unrelated worker state clears transient storyDetail; previous and next month still work repeatedly');
 await page.evaluate(()=>{
  window.storyRevoked=[];const revoke=URL.revokeObjectURL.bind(URL);
  URL.revokeObjectURL=value=>{window.storyRevoked.push(value);revoke(value);};
 });
 await page.locator('.story-calendar-day.has-stories').click();await expectImage();
 assert.equal(await page.locator('.story-choice').count(),2);
 assert.match(await page.locator('.story-facts').innerText(),/qa.tagged/);
 assert.match(await page.locator('.story-facts').innerText(),/Hikâyede paylaşılan gönderi/);
 assert.match(await page.locator('.story-facts').innerText(),/kaynağı bu kayıtta doğrulanamadı/);
 const boxes=await Promise.all(['.story-calendar','#story-player','.story-facts'].map(selector=>page.locator(selector).boundingBox()));
 assert.ok(boxes[0].x+boxes[0].width<boxes[1].x);assert.ok(boxes[1].x+boxes[1].width<=boxes[2].x);
 const blob=await page.locator('#story-player img').getAttribute('src');
 await page.screenshot({path:path.join(output,'story-viewer.png')});
 checks.push('Selecting a day opens its saved image automatically; compact calendar, media and honest source/mention details appear side by side');
 await command('settings',{compact:false});
 assert.equal((await command('state')).state.storyDetail,null);
 await page.locator('.story-calendar-heading button').first().click();await expectMonth('2026-12');
 assert.equal(await page.locator('#story-player img').getAttribute('src'),blob);
 await page.locator('.story-calendar-heading button').last().click();await expectMonth('2027-01');
 assert.equal(await page.locator('#story-player img').getAttribute('src'),blob);
 checks.push('Month navigation preserves the selected story and its existing Blob after another worker state update');
 await page.locator('.story-choice[data-story-id="301"]').click();
 await page.getByText('Yalnız bilgiler kaydedildi',{exact:true}).waitFor();
 assert.equal(await page.locator('#story-player img').count(),0);
 assert.equal(await page.evaluate(url=>window.storyRevoked.includes(url),blob),true);
 await page.locator('.story-choice[data-story-id="300"]').click();await expectImage();
 await page.locator('[data-action="story-calendar"]').click();
 assert.equal(await page.locator('#story-player').count(),0);
 await page.locator('.story-calendar-day[data-day="2027-01-01"]').click();
 await page.getByText('Bu günde kayıtlı hikâye yok.',{exact:true}).waitFor();
 await page.locator('.story-calendar-day.has-stories').click();await expectImage();
 checks.push('Story selection, metadata-only rows, empty days and calendar return release previous media correctly');
 for(const [language,title] of [['en','Story archive · Experimental'],['es','Archivo de historias · Experimental'],['pt-BR','Arquivo de stories · Experimental'],['tr','Hikâye arşivi · Deneysel']]) {
  await page.keyboard.press('Escape');await command('settings',{language});
  await page.waitForFunction(language=>document.documentElement.lang===language,language);
  await openArchive();await page.locator('.story-calendar-day.has-stories').click();await expectImage();
  assert.equal(await page.locator('#dialog-title').innerText(),title);
 }
 await command('settings',{theme:'dark'});
 await page.screenshot({path:path.join(output,'story-viewer-dark.png')});
 const contentWidth=await app.evaluate(({BrowserWindow})=>{const window=BrowserWindow.getAllWindows()[0];window.setSize(960,680);return window.getContentSize()[0];});
 await page.waitForFunction(width=>window.innerWidth===width,contentWidth);
 assert.equal(await page.locator('#story-archive-root').evaluate(element=>element.scrollWidth<=element.clientWidth+1),true);
 const small=await Promise.all(['.story-calendar','#story-player','.story-facts'].map(selector=>page.locator(selector).boundingBox()));
 assert.ok(small[0].x+small[0].width<=small[1].x);
 await page.screenshot({path:path.join(output,'story-viewer-small.png')});
 checks.push('Four languages, dark theme and the 960×680 desktop minimum keep the viewer usable without horizontal overflow');
 await command('switch_account',{id:'400'});
 await page.waitForFunction(()=>!document.querySelector('#overlay-root dialog[open]'));
 const foreign=await command('story_media',{accountId:'100',id:'200',storyId:'300'});assert.equal(foreign.ok,false);
 await command('switch_account',{id:'100'});
 const offline=await command('story_fetch',{accountId:'100',id:'200'});assert.equal(offline.ok,false);assert.equal(offline.error.code,'offline');
 await command('story_clear',{accountId:'100',id:'200'});
 assert.equal((await command('story_view',{accountId:'100',id:'200'})).state.storyDetail.items.length,0);
 assert.deepEqual((await command('state')).state.gate,before.gate);
 checks.push('Account switch closes the viewer, foreign media is rejected, offline mode blocks live reads, clearing remains scoped and the request gate is unchanged');
 assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
 await writeFile(path.join(output,'summary.json'),JSON.stringify({checks,errors,network,home},null,2));
 console.log(JSON.stringify({checks:checks.length,errors,network,output}));
} catch(error) {
 await page.screenshot({path:path.join(output,'failure.png')}).catch(()=>{});throw error;
} finally {await app.close();}
