// Records the README demo: open a ZIP, compare two exports, look at insights.
// Uses synthetic exports only (accounts named demo.*); no Instagram request is made.
//
//   node scripts/capture-readme-demo.mjs <Followtrail.exe | electron.exe> <output.gif> [tr|en]
//
// With electron.exe the current source tree is launched; with a packaged
// Followtrail.exe that build is recorded. Frames are joined by
// scripts/make-demo-gif.py (Pillow from the build venv, TRACKER_BUILD_PYTHON).
import {_electron as electron} from 'playwright-core';
import {execFileSync} from 'node:child_process';
import {mkdtemp, readFile, rm} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import os from 'node:os';

const [executable, output, language = 'tr'] = process.argv.slice(2);
if (!executable || !output?.endsWith('.gif') || !['tr', 'en'].includes(language)) {
  console.error('Usage: node scripts/capture-readme-demo.mjs <Followtrail.exe|electron.exe> <output.gif> [tr|en]');
  process.exit(2);
}
const interfaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const python = process.env.TRACKER_BUILD_PYTHON || path.join(interfaceRoot, '.cache/build-venv-314/Scripts/python.exe');
const catalog = language === 'tr' ? {} : JSON.parse(await readFile(path.join(interfaceRoot, `locales/${language}.json`), 'utf8'));
const L = key => (typeof catalog[key] === 'string' ? catalog[key] : key);

const home = await mkdtemp(path.join(os.tmpdir(), 'TrackerQA-'));
const frames = path.join(home, 'frames');
const older = path.join(home, 'instagram-demo.account-2026-08-01-AbCdEf12.zip');
const newer = path.join(home, 'instagram-demo.account-2026-11-01-GhIjKl34.zip');
execFileSync(python, ['-X', 'utf8', path.join(interfaceRoot, 'scripts/make-demo-gif.py'), 'exports', older, newer]);

const env = {...process.env};
delete env.ELECTRON_RUN_AS_NODE;
const packaged = path.basename(executable).toLowerCase() === 'followtrail.exe';
const app = await electron.launch({executablePath: executable,
  args: [...(packaged ? [] : [interfaceRoot]), `--tracker-test-home=${home}`], env});
const durations = [];
try {
  const page = await app.firstWindow();
  page.setDefaultTimeout(20000);
  await page.setViewportSize({width: 1280, height: 800});
  const network = [];
  page.on('request', request => { if (/^https?:/.test(request.url())) network.push(request.url()); });
  if (language !== 'tr') {
    await page.locator('.sidebar nav a[href="#settings"]').click();
    await page.locator('#app-language').selectOption(language);
    await page.waitForFunction(value => document.documentElement.lang === value, language);
    await page.locator('#toast.visible').waitFor({state: 'hidden'});
    await page.locator('.sidebar nav a[href="#overview"]').click();
  }
  await app.evaluate(({dialog}, files) => {
    const queue = [...files];
    dialog.showOpenDialog = () => Promise.resolve({canceled: false, filePaths: [queue.shift()]});
  }, [newer, older]);
  let index = 0;
  const top = locator => locator.evaluate(node => node.scrollIntoView({block: 'start'}));
  const frame = async (milliseconds) => {
    await page.mouse.move(2, 2);
    await page.waitForTimeout(350);
    await page.screenshot({path: path.join(frames, `${String(index++).padStart(2, '0')}.png`)});
    durations.push(milliseconds);
  };
  const archive = page.locator('#archive-dashboard-root');
  const body = archive.locator('.archive-dashboard-body');

  await page.locator('.overview-start').waitFor();
  await frame(2200);
  await page.locator('.overview-start').getByRole('button', {name: L('Instagram ZIP’ini aç')}).click();
  await archive.locator('.archive-dashboard-overview').getByRole('heading', {name: L('Arşiv kapsamı')}).waitFor();
  await frame(2600);

  await archive.getByRole('button', {name: L('İki ZIP karşılaştır')}).click();
  await body.getByRole('button', {name: L('İkinci ZIP’i seç')}).click();
  // The open ZIP is the newer one; put the older export first so changes read forwards.
  await body.getByRole('button', {name: L('Yer değiştir')}).click();
  await body.getByRole('button', {name: L('Karşılaştırmayı başlat')}).click();
  await body.locator('.archive-dashboard-compare-count').first().waitFor();
  const results = body.locator('.archive-dashboard-rows');
  const details = results.locator('details');
  for (let item = 0; item < Math.min(3, await details.count()); item++) await details.nth(item).locator('summary').click();
  await top(body.locator('.archive-dashboard-compare-sources'));
  await frame(3200);

  await archive.getByRole('button', {name: L('İçgörüler'), exact: true}).click();
  const panel = archive.locator('.archive-insights-panel');
  await panel.locator('.archive-insights-person').first().waitFor();
  await top(body);
  await frame(3000);
  await archive.locator('.archive-insights-tabs').getByRole('button', {name: L('Etkinlik saatleri')}).click();
  await panel.locator('.archive-insights-heatmap').waitFor();
  await top(body);
  await frame(2600);
  await archive.locator('.archive-insights-tabs').getByRole('button', {name: L('Yıl özeti')}).click();
  await panel.locator('.archive-insights-card').waitFor();
  await top(panel.locator('.archive-insights-year .archive-dashboard-metric-grid'));
  await frame(3400);
  if (network.length) throw new Error('Unexpected network requests: ' + network.join(', '));
} finally {
  await app.close();
}
execFileSync(python, ['-X', 'utf8', path.join(interfaceRoot, 'scripts/make-demo-gif.py'), 'gif', frames, output,
  durations.join(',')], {stdio: 'inherit'});
await rm(home, {recursive: true, force: true});
