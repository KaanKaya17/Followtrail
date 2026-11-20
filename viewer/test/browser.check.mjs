// End-to-end check of the viewer in a real Chrome: open a ZIP, read the lists,
// search, download CSV, compare two ZIPs, switch language, and prove that the
// page makes no request outside its own static files.
//
//   node viewer/test/browser.check.mjs [screenshot-dir]
// CHROME_PATH selects the browser (defaults to the usual install locations).
import {createRequire} from 'node:module';
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {existsSync, mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';
import {writeZip} from './zip-writer.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const require = createRequire(path.resolve(root, '../interface/package.json'));
const {chromium} = require('playwright-core');
const shots = process.argv[2];
if (shots) mkdirSync(shots, {recursive: true});

const chrome = process.env.CHROME_PATH || [
  'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  '/usr/bin/google-chrome', '/usr/bin/chromium',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
].find(existsSync);
assert.ok(chrome, 'Set CHROME_PATH to a Chrome or Chromium executable');

const TYPES = {'.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml'};
const server = createServer(async (request, response) => {
  const name = new URL(request.url, 'http://x').pathname.replace(/^\/$/, '/index.html');
  const file = path.join(root, path.normalize(name));
  if (!file.startsWith(root + path.sep) || name.includes('/test/') || !TYPES[path.extname(file)]) {
    response.writeHead(404).end();
    return;
  }
  try {
    response.writeHead(200, {'content-type': TYPES[path.extname(file)]}).end(await readFile(file));
  } catch {
    response.writeHead(404).end();
  }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const origin = `http://127.0.0.1:${server.address().port}`;

const F = 'connections/followers_and_following/';
const fr = (name, at) => ({title: '', string_list_data: [{href: `https://www.instagram.com/${name}`, value: name, timestamp: at}]});
const fg = (name, at) => ({title: name, string_list_data: [{href: `https://www.instagram.com/_u/${name}`, timestamp: at}]});
const older = writeZip([
  {name: F + 'followers_1.json', data: JSON.stringify([fr('ada', 1700000000), fr('efe', 1700086400), fr('mert.eski', 1700172800)])},
  {name: F + 'following.json', data: JSON.stringify({relationships_following: [fg('ada', 1690000000), fg('kitap', 1690086400)]})},
]);
const followers = Array.from({length: 150}, (_, i) => fr(`hesap${String(i).padStart(3, '0')}`, 1700003600 + i * 86400));
const newer = writeZip([
  {name: F + 'followers_1.json', data: JSON.stringify([fr('ada', 1700000000), fr('mert.yeni', 1700172800), fr('selin', 1700259200), ...followers])},
  {name: F + 'following.json', data: JSON.stringify({relationships_following: [fg('ada', 1690000000), fg('selin', 1690172800), fg('kitap', 1690172801)]})},
  {name: F + 'close_friends.json', data: JSON.stringify({relationships_close_friends: [fg('ada', 1695000000)]})},
]);
const file = (name, bytes) => ({name, mimeType: 'application/zip', buffer: Buffer.from(bytes)});

const browser = await chromium.launch({executablePath: chrome, headless: true});
const failures = [];
try {
  const context = await browser.newContext({locale: 'tr-TR', acceptDownloads: true, viewport: {width: 1200, height: 900}});
  const page = await context.newPage();
  const requests = [];
  page.on('request', request => requests.push(request.url()));
  page.on('pageerror', error => failures.push('page error: ' + error.message));
  page.on('console', message => { if (message.type() === 'error') failures.push('console: ' + message.text()); });
  await page.goto(origin + '/');
  await page.getByRole('heading', {name: 'Instagram arşivini tarayıcında incele'}).waitFor();
  assert.equal(await page.locator('#result').isVisible(), false, 'results stay hidden until a ZIP is read');

  await page.locator('#file').setInputFiles(file('instagram-ada-2026-11-01-AbCdEf12.zip', newer));
  await page.locator('#cards .card').first().waitFor();
  const cards = await page.locator('#cards').innerText();
  assert.match(cards, /Takipçiler\s+153/);
  assert.match(cards, /Takip ettiklerin\s+3/);
  assert.match(cards, /Seni geri takip etmeyenler\s+1/);
  assert.match(cards, /Yakın arkadaşlar\s+1/);
  assert.match(await page.locator('#file-line').innerText(), /@ada \(dosya adından\)/);
  assert.equal(await page.locator('#rows li').count(), 100, 'first page of rows');
  await page.getByRole('button', {name: 'Daha fazla göster'}).click();
  assert.equal(await page.locator('#rows li').count(), 153);
  await page.locator('#search').fill('mert');
  assert.equal(await page.locator('#rows li').count(), 1);
  assert.equal(await page.locator('#rows a').getAttribute('href'), 'https://www.instagram.com/mert.yeni/');
  await page.locator('#search').fill('');
  await page.locator('#cards .card', {hasText: 'Seni geri takip etmeyenler'}).click();
  assert.match(await page.locator('#rows').innerText(), /@kitap/);
  const [download] = await Promise.all([page.waitForEvent('download'), page.locator('#csv').click()]);
  const csv = await readFile(await download.path(), 'utf8');
  assert.equal(csv, '\ufeff"username","follow_date_utc"\r\n"kitap","2023-07-24"\r\n');
  if (shots) await page.screenshot({path: path.join(shots, 'viewer-lists.png'), fullPage: true});

  // The second ZIP is older by its file name, so it becomes the "before" side.
  await page.locator('#second').setInputFiles(file('instagram-ada-2026-08-01-GhIjKl34.zip', older));
  await page.locator('#compare .change').first().waitFor();
  const comparison = await page.locator('#compare').innerText();
  assert.match(comparison, /Eski\s+instagram-ada-2026-08-01/);
  assert.match(comparison, /Yeni takipçiler · 151/);
  assert.match(comparison, /Takipten çıkanlar · 1[\s\S]*@efe/);
  assert.match(comparison, /Kullanıcı adını değiştirmiş olabilir · 1[\s\S]*@mert\.eski → @mert\.yeni/);
  assert.match(comparison, /Takip etmeye başladıkların · 1[\s\S]*@selin/);
  if (shots) await page.screenshot({path: path.join(shots, 'viewer-compare.png'), fullPage: true});

  await page.getByRole('button', {name: 'EN', exact: true}).click();
  await page.getByRole('heading', {name: 'Explore your Instagram archive in your browser'}).waitFor();
  assert.match(await page.locator('#compare').innerText(), /New followers · 151/);

  await page.setViewportSize({width: 375, height: 800});
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  assert.equal(overflow, 0, 'no horizontal scroll at phone width');
  if (shots) await page.screenshot({path: path.join(shots, 'viewer-phone.png'), fullPage: false});

  // The CSP must stop any request, even one the page itself tries to make.
  const blocked = await page.evaluate(async () => {
    try { await fetch('https://example.com/'); return false; } catch { return true; }
  });
  assert.equal(blocked, true, 'fetch is blocked by connect-src none');
  failures.splice(0, failures.length, ...failures.filter(text => !/Content Security Policy|example\.com|Failed to fetch/.test(text)));

  const outside = requests.filter(url => !url.startsWith(origin + '/') && !url.startsWith('blob:') && !url.startsWith('data:'));
  assert.deepEqual(outside, [], 'no request outside the page origin');
  assert.deepEqual(failures, []);
  console.log('Viewer browser check passed:', requests.length, 'same-origin requests, 0 external.');
} finally {
  await browser.close();
  server.close();
}
