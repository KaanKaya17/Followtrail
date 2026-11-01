import { chromium } from 'playwright-core';
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const output = path.join(root, 'artifacts/navigation-clarity-web');
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
  for (const [lang, label] of [['tr', 'Kullanım notları'], ['en', 'Usage notes'], ['es', 'Notas de uso'], ['pt-BR', 'Notas de uso']]) {
    await page.evaluate(() => { location.hash = '#settings'; });
    await page.locator('#app-language').selectOption(lang);
    await page.locator('.page-footer [data-action="usage-notes"]').click();
    await page.getByRole('dialog').getByRole('heading', {name:label,exact:true}).waitFor();
    assert.equal(await page.getByRole('dialog').locator('.usage-notes section').count(), 3);
    await page.keyboard.press('Escape');
    assert.equal(await page.getByRole('dialog').count(), 0);
  }
  await page.locator('#app-language').selectOption('tr');
  checks.push('Footer help opens in all four languages and closes with Escape');
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
  const titles = { overview: 'Overview', people: 'Saved Profiles', lists: 'Follow Lists', timeline: 'Timeline', settings: 'Settings' };
  for (const route of ['overview', 'people', 'lists', 'timeline', 'settings']) {
    await page.evaluate(route => { location.hash = '#' + route; }, route);
    await page.waitForFunction(title => document.title === title + ' — Tracker', titles[route]);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, route);
  }
  checks.push('Five English routes without mobile overflow');
  assert.deepEqual((await page.locator('.mobile-nav a').allTextContents()).map(text => text.trim()), ['Overview', 'Saved Profiles', 'Follow Lists', 'History', 'Settings']);
  checks.push('Mobile navigation labels use the selected language');
  await page.screenshot({ path: path.join(output, 'settings-en-mobile.png'), fullPage: true });
  await page.locator('#app-language').selectOption('tr');
  await page.waitForFunction(() => document.documentElement.lang === 'tr');
  assert.equal(await page.evaluate(() => window.TrackerI18n.formats('tr').number.format(1200)), '1.200');
  checks.push('Switching back restores Turkish formatting');
  for (const [language, heading, overview, formatted] of [
    ['es', 'Pequeños ajustes, experiencia más cómoda.', 'Resumen', '1200'],
    ['pt-BR', 'Pequenos ajustes, experiência mais confortável.', 'Resumo', '1.200'],
  ]) {
    await page.evaluate(() => { location.hash = '#settings'; });
    await page.locator('#app-language').selectOption(language);
    await page.waitForFunction(value => document.documentElement.lang === value, language);
    await page.getByRole('heading', { name: heading, exact: true }).waitFor();
    assert.equal(await page.evaluate(value => window.TrackerI18n.formats(value).number.format(1200), language), formatted);
    await page.evaluate(() => { location.hash = '#overview'; });
    await page.waitForFunction(value => document.title === value + ' — Tracker', overview);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false);
  }
  checks.push('Spanish and Brazilian Portuguese settings, number formats and overview render without overflow');
  const emptyPage = await browser.newPage({viewport:{width:390,height:844}});
  emptyPage.on('pageerror', error => errors.push(error.message));
  emptyPage.on('request', request => {if(!request.url().startsWith(origin) && !request.url().startsWith('data:')) external.push(request.url());});
  await emptyPage.route('**/app.js', async route => {
    const response = await route.fetch();
    const source = await response.text(), marker = 'people: structuredClone(original),';
    assert.equal(source.split(marker).length, 2);
    await route.fulfill({response,body:source.replace(marker,'people: [],')});
  });
  await emptyPage.goto(origin+'/#people');
  await emptyPage.getByRole('heading',{name:'Henüz profil kaydetmedin.',exact:true}).waitFor();
  assert.match(await emptyPage.locator('.empty-guide').innerText(),/Takipçilerin bu bölüme otomatik eklenmez/);
  for(const width of [390,320]) {
    await emptyPage.setViewportSize({width,height:844});
    assert.equal(await emptyPage.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
    assert.equal(await emptyPage.locator('.mobile-nav a').evaluateAll(nodes=>nodes.some(n=>n.scrollWidth>n.clientWidth+1)),false);
  }
  await emptyPage.screenshot({path:path.join(output,'empty-saved-profiles-tr-mobile.png'),fullPage:true});
  await emptyPage.getByRole('button',{name:'Takipçi listemi aç',exact:true}).click();
  await emptyPage.getByRole('heading',{name:'Takipçiler ve takip edilenler',exact:true}).waitFor();
  checks.push('Empty Saved Profiles explains the distinction, fits 320/390 px and opens sample followers');
  await emptyPage.evaluate(()=>{location.hash='#settings';});
  await emptyPage.locator('#app-language').selectOption('en');
  await emptyPage.evaluate(()=>{location.hash='#people';});
  await emptyPage.getByRole('heading',{name:'You have not saved a profile yet.',exact:true}).waitFor();
  await emptyPage.getByRole('button',{name:'Open my follower list',exact:true}).click();
  await emptyPage.getByRole('heading',{name:'Followers and following',exact:true}).waitFor();
  checks.push('English empty-state action opens follower records with no account connection');
  await emptyPage.close();
  assert.deepEqual(errors, []); assert.deepEqual(external, []);
  await writeFile(path.join(output, 'checks.json'), JSON.stringify({ checks, errors, externalRequests: external.length, synthetic: true }, null, 2));
  console.log(`${checks.length} four-language browser checks passed; external requests 0.`);
} finally {
  try { await browser?.close(); } finally { server.kill(); }
}
