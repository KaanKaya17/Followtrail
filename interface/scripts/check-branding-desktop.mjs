import {_electron as electron} from 'playwright-core';
import {readFile,mkdtemp} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {Data,NtExecutable,NtExecutableResource,Resource} from 'resedit';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const {version}=JSON.parse(await readFile(path.join(root,'package.json'),'utf8'));
const packagePath=path.resolve(root,`../artifacts/InstagramTracker-Desktop-${version}`);
const executablePath=path.join(packagePath,'InstagramTracker.exe');
const resources=NtExecutableResource.from(NtExecutable.from(await readFile(executablePath)));
const info=Resource.VersionInfo.fromEntries(resources.entries)[0];
for (const lang of info.getAllLanguagesForStringValues()) {
  const strings=info.getStringValues(lang);
  assert.equal(strings.ProductName,'InstagramTracker');
  assert.equal(strings.FileDescription,'InstagramTracker');
  assert.equal(strings.OriginalFilename,'InstagramTracker.exe');
  assert.equal(strings.ProductVersion,version);
}
const icons=Resource.IconGroupEntry.fromEntries(resources.entries)[0].getIconItemsFromEntries(resources.entries);
const expected=Data.IconFile.from(await readFile(path.join(packagePath,'resources/app/desktop/app-icon.ico')));
const bytes=item=>Buffer.from(item.isRaw() ? item.bin : item.generate());
assert.deepEqual(icons.map(bytes),expected.icons.map(item=>bytes(item.data)));
assert.equal(icons.length,7);
console.log('PASS packaged EXE has Tracker name, current version and seven embedded brand-icon sizes');

const home=await mkdtemp(path.join(os.tmpdir(),'TrackerQA-'));
const python=process.env.TRACKER_BUILD_PYTHON || path.resolve(root,'../..','InstagramCounter/.venv/Scripts/python.exe');
execFileSync(python,[path.join(root,'desktop/backend/seed_qa.py'),'--home',home,'--empty'],{stdio:'pipe'});
const env={...process.env};delete env.ELECTRON_RUN_AS_NODE;
let desktop;
try {
  desktop=await electron.launch({executablePath,args:[`--tracker-test-home=${home}`],env});
  const page=await desktop.firstWindow();
  await page.getByRole('heading',{name:'Çevrende neler oluyor?',exact:true}).waitFor();
  const state=await page.evaluate(()=>window.TrackerDesktop.command('state'));
  assert.equal(state.state.settings.notifications,false);
  const result=await desktop.evaluate(({app,shell},{home,executablePath})=>{
    const require=process.getBuiltinModule('module').createRequire(app.getAppPath()+'/package.json');
    const path=require('node:path'), fs=require('node:fs');
    const {ensureShortcut,APP_ID}=require(path.join(app.getAppPath(),'desktop/notifications.cjs'));
    const link=path.join(home,'shortcuts/InstagramTracker Desktop.lnk');
    const initial=shell.readShortcutLink(link);
    const options={app,shell,testHome:home,dataDir:home};
    const before=fs.readFileSync(link);
    ensureShortcut(options);
    const idempotent=before.equals(fs.readFileSync(link));
    shell.writeShortcutLink(link,{...initial,icon:'',iconIndex:0});
    const legacy=fs.readFileSync(link);
    ensureShortcut(options);
    const updated=shell.readShortcutLink(link);
    const backedUp=legacy.equals(fs.readFileSync(path.join(home,'notification-shortcut.previous.lnk')));
    shell.writeShortcutLink(link,{...updated,appUserModelId:'foreign.qa.app'});
    const foreign=fs.readFileSync(link);
    let foreignRejected=false;
    try { ensureShortcut(options); } catch { foreignRejected=true; }
    const foreignUnchanged=foreign.equals(fs.readFileSync(link));
    shell.writeShortcutLink(link,updated);
    return {initial,updated,idempotent,backedUp,foreignRejected,foreignUnchanged,appId:APP_ID,executablePath};
  },{home,executablePath});
  assert.equal(result.initial.target,executablePath);
  assert.equal(result.initial.icon,executablePath);
  assert.equal(result.initial.iconIndex,0);
  assert.equal(result.initial.appUserModelId,result.appId);
  assert.equal(result.updated.icon,executablePath);
  for (const key of ['idempotent','backedUp','foreignRejected','foreignUnchanged']) assert.equal(result[key],true,key);
  console.log('PASS native Start shortcut is branded with notifications off; updates preserve backup and respect ownership');
} finally {
  if (desktop) await desktop.close();
}
