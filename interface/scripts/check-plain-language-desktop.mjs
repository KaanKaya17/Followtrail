// Actual packaged IPC/DPAPI fixture; server restrictions are projected, never requested.
import {_electron as electron} from 'playwright-core';
import {mkdtemp,mkdir,readFile,writeFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const version=JSON.parse(await readFile(path.join(root,'interface/package.json'),'utf8')).version;
const home=await mkdtemp(path.join(os.tmpdir(),'TrackerQA-'));
const output=path.join(root,'artifacts/plain-language-ui');await mkdir(output,{recursive:true});
execFileSync(path.resolve(root,'../InstagramCounter/.venv/Scripts/python.exe'),['interface/desktop/backend/seed_qa.py','--home',home,'--keyboard'],{cwd:root});
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
const checks=[],errors=[],requests=[];let app,page;
try {
 app=await electron.launch({executablePath:path.join(root,`artifacts/Followtrail-Desktop-${version}/Followtrail.exe`),args:[`--tracker-test-home=${home}`],env});
 page=await app.firstWindow();page.setDefaultTimeout(12000);
 page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(!/^(file:|data:)/.test(r.url()))requests.push(r.url());});
 await page.getByRole('button',{name:'Şimdi kontrol et',exact:true}).waitFor();
 await app.evaluate(({app})=>{
  const require=process.getBuiltinModule('module').createRequire(app.getAppPath()+'/package.json');
  require('./desktop/background.cjs').Background.prototype.tick=()=>{};
  const {Backend}=require('./desktop/service.cjs'),original=Backend.prototype.command;
  Backend.prototype.command=function(...args){globalThis.qaLanguage=this;return original.apply(this,args);};
 });
 const initial=(await page.evaluate(()=>window.TrackerDesktop.command('state'))).state;
 assert.match(await page.locator('.monitor-times').innerText(),/Son kontrol/);
 checks.push('Overview uses check language and a clear Check now action');
 const raw='Instagram HTTP 429 döndürdü. Tarama durdu; otomatik tekrar yapılmayacak.';
 async function restriction(language) {
  await app.evaluate((_,{initial,raw,language})=>{
   const b=globalThis.qaLanguage;
   b.message({type:'result',ok:false,state:{...initial,settings:{...initial.settings,language},lastError:{code:'limited',message:raw},
    events:[{id:'plain-language-error',type:'error',title:raw,at:Date.now()/1000}],
    gate:{...initial.gate,status:429,manualOnly:true,blockedUntil:Date.now()/1000+3600}}});
  },{initial,raw,language});
 }
 await restriction('tr');
 const notice=page.locator('.overview-monitor .operation-panel');
 assert.match(await notice.innerText(),/Instagram istekleri sınırladı/);
 assert.doesNotMatch(await notice.innerText(),/HTTP|429/);
 assert.doesNotMatch(await page.locator('main').innerText(),/HTTP|429/);
 assert.match(await page.locator('#operation-root').innerText(),/Instagram istekleri sınırladı/);
 checks.push('Ordinary restriction feedback explains the pause without exposing HTTP codes or claiming safety');
 await notice.getByRole('button',{name:'İstek bilgileri',exact:true}).click();
 await page.getByText('Son hatanın teknik ayrıntıları',{exact:true}).click();
 const dialog=page.getByRole('dialog');assert.match(await dialog.innerText(),/429/);
 assert.ok((await dialog.innerText()).includes(raw));
 assert.equal(await app.evaluate(()=>globalThis.qaLanguage.state.lastError.message),raw);
 await page.screenshot({path:path.join(output,'technical-details-tr.png')});
 await page.keyboard.press('Escape');
 checks.push('Request information retains the original diagnostic and HTTP 429 without modifying evidence');
 await page.locator('[data-action="event"][data-id="plain-language-error"]').click();
 await page.getByRole('dialog').getByText('Teknik ayrıntılar',{exact:true}).click();
 assert.ok((await page.getByRole('dialog').innerText()).includes(raw));
 await page.keyboard.press('Escape');
 checks.push('Recent activity uses a simple error heading; the event still opens its original diagnostic');
 await restriction('en');
 assert.match(await notice.innerText(),/Instagram limited requests/);assert.doesNotMatch(await notice.innerText(),/HTTP|429/);
 await notice.getByRole('button',{name:'Request information',exact:true}).click();
 await page.getByText('Technical details of the last error',{exact:true}).click();
 assert.ok((await page.getByRole('dialog').innerText()).includes(raw));
 await page.keyboard.press('Escape');
 await page.screenshot({path:path.join(output,'restriction-en.png')});
 checks.push('English feedback is plain language while diagnostic source text remains intact');
 assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);
 await writeFile(path.join(output,'checks.json'),JSON.stringify({version,checks,errors,requests,synthetic:true,liveInstagram:false},null,2));
 console.log(`${checks.length} packaged plain-language checks passed; no external requests.`);
}catch(error){
 if(page){await page.screenshot({path:path.join(output,'failure.png'),fullPage:true}).catch(()=>{});
  console.log(JSON.stringify({errors,url:page.url(),text:await page.locator('main').innerText(),progress:await page.locator('#operation-root').innerText()}));}
 throw error;
}finally{await app?.close();}
