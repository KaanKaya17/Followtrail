// Offline packaged-app comparison. Never reads the production account vault.
import { _electron as electron } from 'playwright-core';
import { mkdtemp, mkdir, readFile, writeFile, access, unlink } from 'node:fs/promises';
import { execFileSync, spawn } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const version = process.argv[2];
if (!/^\d+\.\d+\.\d+$/.test(version || '')) throw Error('Pass packaged version');
const large = process.argv.includes('--large');
const accounts = process.argv.includes('--accounts');
const out = path.join(root, `artifacts/desktop-${version}-qa/performance${large ? '-large' : ''}${accounts ? '-accounts' : ''}`);
await mkdir(out, { recursive: true });
const home = await mkdtemp(path.join(os.tmpdir(), 'TrackerQA-'));
const python = process.env.TRACKER_BUILD_PYTHON || path.resolve(root, '../InstagramCounter/.venv/Scripts/python.exe');
execFileSync(python, [path.join(root, 'interface/desktop/backend/seed_qa.py'), '--home', home, ...(large ? ['--performance'] : []), ...(accounts ? ['--accounts'] : [])]);
const env = { ...process.env }; delete env.ELECTRON_RUN_AS_NODE;
const app = await electron.launch({ executablePath: path.join(root, `artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`), args: [`--tracker-test-home=${home}`], env });
const page = await app.firstWindow();
let changes = 0;
let sampleProcess;
try {
  await page.getByRole('heading', { name: 'Çevrende neler oluyor?' }).waitFor();
  await page.evaluate(() => {
    window.qaMutations = 0;
    new MutationObserver(records => { window.qaMutations += records.length; }).observe(document.getElementById('app'), { childList: true, subtree: true });
  });
  await delay(3000);
  const snapshotBytes=await page.evaluate(async()=>new TextEncoder().encode(JSON.stringify((await window.TrackerDesktop.command('state')).state)).length);
  if(process.argv.includes('--snapshot-only')) {
    await writeFile(path.join(out,'snapshot-size.json'),JSON.stringify({version,snapshotBytes,large}));
    console.log(JSON.stringify({version,snapshotBytes,large}));
  } else {
  const samples = {};
  for (const phase of ['visible-idle', 'visible-updates', 'hidden-idle', 'hidden-updates']) {
    if (phase === 'hidden-idle') await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].close());
    const output = path.join(out, phase + '.json');
    await unlink(output + '.ready').catch(error => { if (error.code !== 'ENOENT') throw error; });
    const monitor = spawn('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(root, 'interface/scripts/sample-desktop-resources.ps1'), '-RootProcessId', String(app.process().pid), '-OutputPath', output, '-Seconds', '20'], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    sampleProcess = monitor;
    let error = ''; monitor.stderr.on('data', b => { error += b; });
    const done = new Promise((resolve, reject) => { monitor.on('error', reject); monitor.on('exit', code => code === 0 ? resolve() : reject(Error(error || `Monitor exited ${code}`))); });
    // A monitor failure must not leave an unhandled rejected promise while waiting.
    done.catch(() => {});
    let ready = false;
    for (let n = 0; n < 100; n++) { try { await access(output + '.ready'); ready = true; break; } catch { if (monitor.exitCode != null) await done; await delay(100); } }
    if (!ready) throw Error('Resource monitor did not become ready');
    const before = await page.evaluate(() => window.qaMutations);
    const start = performance.now();
    if (phase.endsWith('-updates')) {
      for (let n = 0; n < 40; n++) {
        const result = await page.evaluate(n => window.TrackerDesktop.command('save_person', { id: '200', note: `Synthetic update ${n}` }).then(r => ({ ok: r.ok })), n);
        assert.equal(result.ok, true); changes++;
      }
    }
    const workMs = performance.now() - start;
    await done;
    samples[phase] = JSON.parse((await readFile(output, 'utf8')).replace(/^\uFEFF/, ''));
    samples[phase].domMutationRecords = await page.evaluate(() => window.qaMutations) - before;
    samples[phase].workMs = workMs;
    console.log(phase, JSON.stringify(samples[phase]));
  }
  assert.equal(await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].isVisible()), false);
  await app.evaluate(({ app }) => app.emit('activate'));
  await page.waitForFunction(async () => (await window.TrackerDesktop.command('state')).state.people.find(p => p.id === '200').note === 'Synthetic update 39');
  const result = { version, snapshotBytes, samples, changes, savedAccounts:accounts?2:1, fixture: large ? '24 profiles including owner, 20 with 60 history entries; two 10000-member snapshots; 2000 events and 500 log groups; synthetic offline data' : 'standard seed_qa (4 profiles including owner, lists and notification log); synthetic offline data', warmupSeconds: 3, liveInstagram: false, limitations: 'Short warm samples, not an hours-long leak or weak-device benchmark. Each update burst is 40 local encrypted note saves through real IPC, not Instagram request timing. The optional second account has one saved target and remains inactive.' };
  await writeFile(path.join(out, 'summary.json'), JSON.stringify(result, null, 2));
  }
} finally {
  try { await app.close(); }
  finally { if (sampleProcess && sampleProcess.exitCode == null) sampleProcess.kill(); }
}
