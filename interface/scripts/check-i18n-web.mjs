import { chromium } from 'playwright-core';
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const output = path.join(root, 'artifacts/ui-i18n-0.2.6');
await mkdir(output, { recursive: true });
const origin = 'http://127.0.0.1:4196';
const server = spawn(process.execPath, [path.join(root, 'interface/scripts/preview.mjs')], { env: { ...process.env, TRACKER_UI_PORT: '4196' }, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
const ready = new Promise((resolve, reject) => {
  server.stdout.on('data', data => { if (String(data).includes('Local:')) resolve(); });
  server.once('error', reject); server.once('exit', code => reject(Error(`Preview exited ${code}`)));
});
let browser;
const errors = [], external = [], checks = [];
try {
  await ready;
  browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1050 }, reducedMotion: 'reduce' });
  page.on('pageerror', error => errors.push(error.message));
  page.on('request', request => { if (!request.url().startsWith(origin) && !request.url().startsWith('data:')) external.push(request.url()); });
  await page.goto(origin);
  await page.getByRole('heading', { name: 'Çevrende neler oluyor?' }).waitFor();
  checks.push('Turkish default');
  await page.locator('.sidebar nav a[href="#settings"]').click();
  await page.locator('#app-language').selectOption('en');
  await page.waitForFunction(() => document.documentElement.lang === 'en');
  await page.getByRole('heading', { name: 'Small settings, a smoother experience.', exact: true }).waitFor();
  checks.push('English language selector and headings');
  for (const route of ['overview', 'people', 'lists', 'timeline', 'settings']) {
    await page.locator(`.sidebar nav a[href="#${route}"]`).click();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, route);
  }
  checks.push('Five English routes without desktop overflow');
  await page.locator('.sidebar nav a[href="#people"]').click();
  await page.locator('[data-action="person"][data-id="200"]').first().click();
  assert.match(await page.getByRole('dialog').innerText(), /Kahve, kitaplar ve yeni yollar/);
  checks.push('User biography preserved in its original language');
  await page.getByRole('dialog').getByRole('button', { name: 'Close', exact: true }).click();
  await page.locator('.sidebar nav a[href="#settings"]').click();
  await page.screenshot({ path: path.join(output, 'settings-en.png'), fullPage: true });
  assert.equal(await page.evaluate(() => window.TrackerI18n.formats('en').number.format(1200)), '1,200');
  await page.reload();
  await page.waitForFunction(() => document.documentElement.lang === 'en');
  checks.push('English preference persists across reload');
  await page.setViewportSize({ width: 390, height: 844 });
  const titles = { overview: 'Overview', people: 'People', lists: 'Lists', timeline: 'Timeline', settings: 'Settings' };
  for (const route of ['overview', 'people', 'lists', 'timeline', 'settings']) {
    await page.evaluate(route => { location.hash = '#' + route; }, route);
    await page.waitForFunction(title => document.title === title + ' — Tracker', titles[route]);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, route);
  }
  checks.push('Five English routes without mobile overflow');
  assert.deepEqual((await page.locator('.mobile-nav a').allTextContents()).map(text => text.trim()), ['Overview', 'People', 'Lists', 'History', 'Settings']);
  checks.push('Mobile navigation labels use the selected language');
  await page.screenshot({ path: path.join(output, 'settings-en-mobile.png'), fullPage: true });
  await page.locator('#app-language').selectOption('tr');
  await page.waitForFunction(() => document.documentElement.lang === 'tr');
  assert.equal(await page.evaluate(() => window.TrackerI18n.formats('tr').number.format(1200)), '1.200');
  checks.push('Switching back restores Turkish formatting');
  assert.deepEqual(errors, []); assert.deepEqual(external, []);
  await writeFile(path.join(output, 'checks.json'), JSON.stringify({ checks, errors, externalRequests: external.length, synthetic: true }, null, 2));
  console.log(`${checks.length} bilingual browser checks passed; external requests 0.`);
} finally {
  try { await browser?.close(); } finally { server.kill(); }
}
