import {_electron as electron,chromium} from 'playwright-core';
import {mkdtemp,mkdir,readFile,writeFile} from 'node:fs/promises';
import {spawn,execFileSync} from 'node:child_process';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const version=JSON.parse(await readFile(path.join(root,'interface/package.json'),'utf8')).version;
const output=path.join(root,'artifacts/appearance-ui');await mkdir(output,{recursive:true});
const checks=[],errors=[],external=[];let app,browser,server;
const colors={light:'rgb(250, 249, 246)',dark:'rgb(17, 19, 27)',oled:'rgb(0, 0, 0)'};
async function background(page,expected) {
 await page.waitForFunction(expected=>getComputedStyle(document.documentElement).backgroundColor===expected,colors[expected]);
}
function observe(page,origin='file:') {
 page.on('pageerror',error=>errors.push(error.message));
 page.on('request',r=>{if(!r.url().startsWith(origin)&&!r.url().startsWith('data:'))external.push(r.url());});
}
async function contrast(page) {
 const ratios=await page.evaluate(()=>{
   const style=getComputedStyle(document.documentElement),color=key=>style.getPropertyValue('--'+key).trim();
   const luminance=value=>{
     const parts=value.slice(1).match(/../g).map(value=>parseInt(value,16)/255).map(value=>value<=.04045?value/12.92:((value+.055)/1.055)**2.4);
     return parts[0]*.2126+parts[1]*.7152+parts[2]*.0722;
   };
   return [['ink','background'],['ink','surface'],['muted','surface'],['violet','violet-soft'],['on-accent','accent-fill'],['added-text','added-bg'],['removed-text','removed-bg']].map(([fg,bg])=>{
     const a=luminance(color(fg)),b=luminance(color(bg));return {fg,bg,ratio:(Math.max(a,b)+.05)/(Math.min(a,b)+.05)};
   });
 });
 for(const pair of ratios)assert.ok(pair.ratio>=4.5,JSON.stringify(pair));return ratios;
}
try {
 const home=await mkdtemp(path.join(os.tmpdir(),'TrackerQA-'));
 execFileSync(path.resolve(root,'../InstagramCounter/.venv/Scripts/python.exe'),[path.join(root,'interface/desktop/backend/seed_qa.py'),'--home',home,'--keyboard','--hold','--wayback']);
 const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
 const launch=async()=>{
   app=await electron.launch({executablePath:path.join(root,`artifacts/Followtrail-Desktop-${version}/Followtrail.exe`),args:[`--tracker-test-home=${home}`],env});
   const page=await app.firstWindow();page.setDefaultTimeout(12000);observe(page);
   await page.getByRole('heading',{name:'Çevrende neler oluyor?'}).waitFor();return page;
 };
 let page=await launch();
 const before=(await page.evaluate(()=>window.TrackerDesktop.command('state'))).state;
 await page.locator('.sidebar nav a[href="#settings"]').click();
 const ratios={};
 for(const mode of ['dark','oled','light']) {
   await page.locator('#app-theme').selectOption(mode);await background(page,mode);
   const state=(await page.evaluate(()=>window.TrackerDesktop.command('state'))).state;
   assert.equal(state.settings.theme,mode);assert.deepEqual(state.gate,before.gate);assert.deepEqual(state.account,before.account);
   ratios[mode]=await contrast(page);
   assert.equal(await app.evaluate(({nativeTheme})=>nativeTheme.shouldUseDarkColors),mode!=='light');
 }
 checks.push('Real Windows selector persists all explicit themes in encrypted state, preserves 429/account data, and updates native theme');
 await page.locator('#app-theme').selectOption('dark');await background(page,'dark');
 for(const route of ['overview','people','lists','notifications','timeline','settings']) {
   await page.locator(`.sidebar nav a[href="#${route}"]`).click();
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
   assert.equal(await page.locator('.card').first().evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(27, 30, 42)');
   await page.screenshot({path:path.join(output,`desktop-dark-${route}.png`)});
 }
 await page.keyboard.press('Control+k');await page.locator('.command-palette').waitFor();
 assert.equal(await page.locator('.command-palette').evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(27, 30, 42)');
 await page.screenshot({path:path.join(output,'desktop-dark-palette.png')});await page.keyboard.press('Escape');
 await page.locator('#main').getByRole('button',{name:'Hesap bağlantısı',exact:true}).click();await page.locator('#overlay-root dialog').waitFor();
 assert.equal(await page.locator('#overlay-root dialog').evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(27, 30, 42)');
 await page.screenshot({path:path.join(output,'desktop-dark-dialog.png')});await page.keyboard.press('Escape');
 checks.push('All six Windows routes, command palette and account dialog use dark surfaces without horizontal overflow');
 await page.locator('.sidebar nav a[href="#people"]').click();await page.locator('.person-title[data-id="200"]').click();
 await page.locator('.profile-more > summary').click();
 await page.getByRole('button',{name:'Profil zaman tüneli',exact:true}).click();
 await page.getByRole('heading',{name:'Biyografi zaman tüneli'}).waitFor();
 assert.equal(await page.locator('.wayback-gallery img').count(),3);
 assert.equal(await page.locator('.wayback-gallery img').first().evaluate(el=>getComputedStyle(el).filter),'none');
 assert.equal(await page.locator('.bio-diff ins').first().evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(28, 56, 42)');
 assert.equal(await page.locator('.bio-diff del').first().evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(66, 39, 44)');
 await page.screenshot({path:path.join(output,'desktop-dark-archive.png')});await page.keyboard.press('Escape');
 await page.locator('.sidebar nav a[href="#settings"]').click();
 checks.push('Biography changes use readable dark insert/delete colors; original JPEG thumbnails are not inverted');
 await page.locator('#app-theme').selectOption('system');await page.emulateMedia({colorScheme:'dark'});await background(page,'dark');
 await page.emulateMedia({colorScheme:'light'});await background(page,'light');
 await page.locator('#app-theme').selectOption('oled');await background(page,'oled');await page.emulateMedia({colorScheme:'light'});await background(page,'oled');
 const restart=(await page.evaluate(()=>window.TrackerDesktop.command('state'))).state;
 await app.close();app=null;page=await launch();await background(page,'oled');
 const restored=(await page.evaluate(()=>window.TrackerDesktop.command('state'))).state;
 assert.deepEqual(restored.gate,restart.gate);assert.equal(restored.settings.theme,'oled');
 checks.push('Windows system preference follows media changes; explicit OLED overrides the system and survives EXE restart');
 await page.locator('.sidebar nav a[href="#settings"]').click();await page.locator('#app-language').selectOption('en');
 await page.locator('#app-theme').selectOption('dark');await background(page,'dark');
 await page.getByRole('combobox',{name:'Theme selection'}).waitFor();
 await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows()[0].setSize(960,760));
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await page.screenshot({path:path.join(output,'desktop-dark-settings-en.png')});
 checks.push('English appearance controls and minimum Windows width remain usable');
 await app.close();app=null;

 const origin='http://127.0.0.1:4197';
 server=spawn(process.execPath,[path.join(root,'interface/scripts/preview.mjs')],{env:{...process.env,TRACKER_UI_PORT:'4197'},windowsHide:true,stdio:['ignore','pipe','pipe']});
 await new Promise((resolve,reject)=>{server.stdout.on('data',data=>{if(String(data).includes('Local:'))resolve();});server.once('error',reject);server.once('exit',code=>reject(Error('Preview exited '+code)));});
 browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 page=await browser.newPage({viewport:{width:1440,height:1050},colorScheme:'dark'});observe(page,origin);
 await page.goto(origin);await background(page,'dark');
 await page.locator('.sidebar nav a[href="#settings"]').click();assert.equal(await page.locator('#app-theme').inputValue(),'system');
 await page.emulateMedia({colorScheme:'light'});await background(page,'light');
 for(const mode of ['light','dark','oled']) {
   await page.locator('#app-theme').selectOption(mode);await background(page,mode);await contrast(page);
   await page.reload();await background(page,mode);assert.equal(await page.locator('#app-theme').inputValue(),mode);
 }
 checks.push('Web defaults to the system, responds live to changes, and persists each override across reload');
 await page.locator('#app-theme').selectOption('dark');
 for(const viewport of [{width:1440,height:1050},{width:390,height:844}]) {
   await page.setViewportSize(viewport);
   for(const route of ['overview','people','lists','timeline','settings']) {
     await page.evaluate(route=>location.hash=route,route);await page.waitForFunction(route=>document.querySelector(`a[href="#${route}"][aria-current="page"]`),route);
     assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
     await page.screenshot({path:path.join(output,`web-dark-${viewport.width}-${route}.png`)});
   }
 }
 checks.push('Web dark mode covers five routes at desktop and phone widths without overflow');
 await page.locator('#app-language').selectOption('en');await page.getByRole('combobox',{name:'Theme selection'}).waitFor();
 await page.locator('#app-theme').selectOption('system');await page.emulateMedia({colorScheme:'dark'});await background(page,'dark');
 await page.evaluate(()=>localStorage.setItem('tracker-ui-theme','invalid'));await page.reload();assert.equal(await page.locator('#app-theme').inputValue(),'system');
 checks.push('Bilingual web settings and invalid local preference fallback work');
 assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
 await writeFile(path.join(output,'checks.json'),JSON.stringify({version,checks,ratios,errors,externalRequests:external.length,synthetic:true,liveInstagram:false},null,2));
 console.log(`${checks.length} appearance scenario groups passed; no external requests.`);
}finally {await app?.close();await browser?.close();server?.kill();}
