// Packaged real IPC/DPAPI, offline fixture; preparation states are UI projections.
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
const output=path.join(root,'artifacts/preparation-ui');await mkdir(output,{recursive:true});
execFileSync(path.resolve(root,'../InstagramCounter/.venv/Scripts/python.exe'),['interface/desktop/backend/seed_qa.py','--home',home,'--keyboard'],{cwd:root});
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
const checks=[],errors=[],requests=[];let app,page;
async function projected(preparation,extra={}) {
 await app.evaluate((_,{preparation,extra})=>{
  const b=globalThis.qaPreparation;
  b.message({type:'result',ok:true,state:{...b.state,settings:{...b.state.settings,automatic:true,automaticLists:true},listPreparation:preparation,...extra}});
 },{preparation,extra});
}
const direction=(direction,extra={})=>({direction,ready:false,pages:0,count:0,scanning:false,error:null,...extra});
try {
 app=await electron.launch({executablePath:path.join(root,`artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`),args:[`--tracker-test-home=${home}`],env});
 page=await app.firstWindow();page.setDefaultTimeout(12000);
 page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(!/^(file:|data:)/.test(r.url()))requests.push(r.url());});
 await page.locator('.preparation-card').waitFor();
 await app.evaluate(({app})=>{
  const require=process.getBuiltinModule('module').createRequire(app.getAppPath()+'/package.json');
  // Production scheduling is exercised by backend tests; this UI fixture must stay idle.
  require('./desktop/background.cjs').Background.prototype.tick=()=>{};
  const {Backend}=require('./desktop/service.cjs'),original=Backend.prototype.command;
  Backend.prototype.command=function(...args){globalThis.qaPreparation=this;return original.apply(this,args);};
 });
 const initial=(await page.evaluate(()=>window.TrackerDesktop.command('state'))).state;
 await page.getByRole('heading',{name:'Liste hazırlığı duraklatıldı'}).waitFor();
 assert.match(await page.locator('.preparation-card').innerText(),/İlk kayıt bir başlangıçtır/);
 checks.push('Existing explicit pause is visible and past unfollows are not invented');
 const state={enabled:true,ready:false,directions:[direction('followers'),direction('following')]};
 await projected(state);
 await page.getByRole('heading',{name:'Hesabın bağlandı, listelerin hazırlanıyor'}).waitFor();
 assert.match(await page.locator('.preparation-card').innerText(),/başlamadan önceki takipten çıkışlar bilinmiyor/);
 assert.equal(await page.locator('.preparation-card li').count(),2);
 checks.push('New connection shows automatic preparation for both directions without a start-scan button');
 await projected({...state,directions:[direction('followers',{pages:4,count:180,scanning:true}),direction('following')]});
 assert.match(await page.locator('.preparation-card').innerText(),/4 sayfa · 180 hesap/);
 await page.screenshot({path:path.join(output,'preparing-tr.png')});
 checks.push('Committed page and member counts remain visible on the overview');
 await page.getByRole('button',{name:'Liste taramalarını duraklat',exact:true}).click();
 await page.getByRole('heading',{name:'Liste hazırlığı duraklatıldı'}).waitFor();
 assert.equal((await page.evaluate(()=>window.TrackerDesktop.command('state'))).state.settings.automaticLists,false);
 await page.getByRole('button',{name:'Hazırlığa devam et',exact:true}).click();
 await page.getByRole('button',{name:'Liste taramalarını duraklat',exact:true}).waitFor();
 checks.push('Pause and resume update the actual encrypted preference through native IPC');
 const ready={...state,ready:true,directions:[direction('followers',{ready:true,count:250,pages:5}),direction('following',{ready:true,count:40,pages:1})]};
 await projected(ready);
 await page.getByRole('heading',{name:'Başlangıç listelerin hazır'}).waitFor();
 assert.match(await page.locator('.preparation-card').innerText(),/Sonraki otomatik taramalarda/);
 await page.screenshot({path:path.join(output,'ready-tr.png')});
 checks.push('First baseline explains that differences require a later scan');
 await page.getByRole('button',{name:'Listeleri aç',exact:true}).click();
 await page.waitForFunction(()=>location.hash==='#lists');
 await page.locator('.sidebar nav a[href="#overview"]').click();
 checks.push('Preparation action opens the own-account followers list');
 await page.evaluate(()=>window.TrackerDesktop.command('settings',{language:'en'}));
 await projected(state,{gate:{...initial.gate,status:429,manualOnly:true,blockedUntil:Date.now()/1000+3600}});
 await page.getByRole('heading',{name:'List preparation is waiting'}).waitFor();
 assert.match(await page.locator('.preparation-card').innerText(),/No automatic retry/);
 await page.screenshot({path:path.join(output,'held-en.png')});
 checks.push('English restriction state explains the hold and offers Request information');
 await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows()[0].setSize(960,680));
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await projected(state,{account:null,connected:false});
 assert.equal(await page.locator('.preparation-card').count(),0);
 checks.push('Minimum window width fits; disconnected workspace has no misleading preparation card');
 assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);
 await writeFile(path.join(output,'checks.json'),JSON.stringify({version,checks,errors,requests,synthetic:true,liveInstagram:false},null,2));
 console.log(`${checks.length} packaged preparation checks passed; no external requests.`);
}finally{await app?.close();}
