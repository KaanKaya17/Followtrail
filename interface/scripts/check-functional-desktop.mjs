import {historyDetail,waitHistory,readHistory} from './read-history.mjs';
import {waitForState} from './wait-for-account.mjs';
import { _electron as electron } from "playwright-core";
import { mkdtemp, mkdir, readFile, writeFile } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import path from "node:path";
import os from "node:os";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const home = await mkdtemp(path.join(os.tmpdir(), "TrackerQA-"));
const version = JSON.parse(
  await readFile(path.join(root, "interface/package.json"), "utf8"),
).version;
const requestPolicy=JSON.parse(await readFile(path.join(root,'design/request-policy.json'),'utf8'));
const output = path.join(root, `artifacts/desktop-${version}-qa`);
await mkdir(output, { recursive: true });
const python =
  process.env.TRACKER_BUILD_PYTHON ||
  path.resolve(root, "../InstagramCounter/.venv/Scripts/python.exe");
execFileSync(
  python,
  [path.join(root, "interface/desktop/backend/seed_qa.py"), "--home", home],
  { stdio: "pipe" },
);
execFileSync(python,[path.join(root,"interface/scripts/seed-story-qa.py"),"--home",home,"--own"],{stdio:"pipe"});
const env = { ...process.env };
delete env.ELECTRON_RUN_AS_NODE;
const executablePath = path.join(
  root,
  `artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`,
);
const checks = [],
  errors = [],
  requests = [];
let desktop, page;
async function launch(language = 'tr') {
  desktop = await electron.launch({
    executablePath,
    args: [`--tracker-test-home=${home}`],
    env,
  });
  page = await desktop.firstWindow();
  page.setDefaultTimeout(12000);
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("request", (r) => {
    if (!r.url().startsWith("file:") && !r.url().startsWith("data:") && !r.url().startsWith("blob:file:"))
      requests.push(r.url());
  });
  await page.getByRole("heading", { name: language === 'en' ? 'What is happening in your circle?' : "Çevrende neler oluyor?" }).waitFor();
}
async function listReady() { await page.waitForFunction(()=>document.getElementById("list-rows")?.getAttribute("aria-busy")==="false"); }
async function check(name, work) {
  await work();
  checks.push(name);
  console.log("PASS " + name);
}
try {
  await launch();
  await check('Thumbnail pipe controls cannot choose a URL and offline mode preserves the request gate',async()=>{
    const before=await page.evaluate(()=>window.TrackerDesktop.command('state'));
    const result=await page.evaluate(()=>window.TrackerDesktop.command('search_avatar',{token:'forged',id:'200'}));
    assert.equal(result.ok,true);assert.equal(result.avatar,null);
    const listImage=await page.evaluate(()=>window.TrackerDesktop.command('list_avatar',{accountId:'100',ownerId:'100',direction:'followers',scanId:'after',id:'201'}));
    assert.equal(listImage.ok,true);assert.equal(listImage.avatar,null);
    assert.equal((await page.evaluate(()=>window.TrackerDesktop.command('cancel_list_images'))).ok,true);
    assert.equal((await page.evaluate(()=>window.TrackerDesktop.command('cancel_search_images'))).ok,true);
    const rejected=await page.evaluate(async()=>{try {await window.TrackerDesktop.command('search_avatar',{token:'forged',id:'200',url:'https://example.com/'});return false;}catch{return true;}});
    assert.equal(rejected,true);
    const listRejected=await page.evaluate(async()=>{try {
      await window.TrackerDesktop.command('list_avatar',{accountId:'100',ownerId:'100',direction:'followers',scanId:'after',id:'201',url:'https://example.com/'});
      return false;
    }catch{return true;}});
    assert.equal(listRejected,true);
    const after=await page.evaluate(()=>window.TrackerDesktop.command('state'));
    assert.deepEqual(after.state.gate,before.state.gate);
    const web=await page.evaluate(()=>window.TrackerDesktop.openLink('https://www.instagram.com/qa.owner/'));
    assert.deepEqual(web,{ok:true,url:'https://www.instagram.com/qa.owner/',opened:false});
    for(const url of ['javascript:alert(1)','file:///C:/secret','https://u:p@example.com/']) {
      assert.equal(await page.evaluate(async url=>{try{await window.TrackerDesktop.openLink(url);return false;}catch{return true;}},url),true);
    }
  });
  await check('Instagram ZIP opens saved relationships without touching the active account', async () => {
    const archivePath = path.join(home, 'test-instagram-export.zip');
    execFileSync(python, ['-c', [
      'import json, sys, zipfile',
      'folder = "connections/followers_and_following/"',
      'def user(name, timestamp): return {"string_list_data": [{"value": name, "timestamp": timestamp}]}',
      'with zipfile.ZipFile(sys.argv[1], "w") as archive:',
      '    archive.writestr(folder + "followers_1.json", json.dumps([user("common", 1707048000), user("follower_only", None)]))',
      '    archive.writestr(folder + "following.json", json.dumps({"relationships_following": [user("common", 1707048000), user("following_only", 1710000000)]}))',
    ].join('\n'), archivePath]);
    const before = await page.evaluate(() => window.TrackerDesktop.command('state'));
    await desktop.evaluate(({dialog}, archivePath) => {
      const open = dialog.showOpenDialog;
      dialog.showOpenDialog = (...args) => {
        dialog.showOpenDialog = open;
        return Promise.resolve({canceled: false, filePaths: [archivePath]});
      };
    }, archivePath);
    await page.locator('.sidebar nav a[href="#lists"]').click();
    assert.equal(await page.getByRole('button', {name: 'Instagram ZIP yükle'}).count(), 0);
    await page.locator('.sidebar nav a[href="#archive"]').click();
    await page.getByRole('button', {name: 'ZIP takip karşılaştırması'}).click();
    await page.getByRole('heading', {name: 'Instagram ZIP arşivi'}).waitFor();
    await page.locator('#instagram-archive-filter').selectOption({label: 'Seni geri takip etmeyenler · 1'});
    const rows = page.locator('#instagram-archive-rows li');
    assert.equal(await rows.count(), 1);
    assert.match(await rows.first().textContent(), /^@following_onlyTakip etmeye başladın/);
    await page.locator('#instagram-archive-search').fill('missing');
    assert.equal(await page.locator('#instagram-archive-rows li').count(), 0);
    await page.locator('#instagram-archive-search').fill('');
    await page.locator('#instagram-archive-filter').selectOption({label: 'Takip edilen · 2'});
    await page.locator('#instagram-archive-sort').selectOption('newest');
    assert.match(await rows.first().textContent(), /^@following_only/);
    const date = await page.evaluate(() => {
      const local = new Date(1707048000 * 1000);
      return [local.getFullYear(), String(local.getMonth() + 1).padStart(2, '0'), String(local.getDate()).padStart(2, '0')].join('-');
    });
    await page.locator('#instagram-archive-from').fill(date);
    await page.locator('#instagram-archive-to').fill(date);
    assert.equal(await rows.count(), 1);
    assert.match(await rows.first().textContent(), /^@common/);
    await page.getByRole('dialog').getByRole('button', {name: 'Kapat'}).click();
    const after = await page.evaluate(() => window.TrackerDesktop.command('state'));
    assert.deepEqual(after.state.gate, before.state.gate);
    assert.deepEqual(after.state.lists, before.state.lists);
  });
  await check('Full Instagram archive summarizes, searches and compares locally without publishing contents to renderer events', async () => {
    const archivePath = path.join(home, 'test-full-export.zip');
    const comparisonPath = path.join(home, 'test-new-export.zip');
    const differentAccountPath = path.join(home, 'test-different-account.zip');
    execFileSync(python, ['-c', [
      'import base64, json, sys, zipfile',
      'with zipfile.ZipFile(sys.argv[1], "w") as archive:',
      '    archive.writestr("connections/example.json", json.dumps({"records": [{"name": "<b>example</b>"}, {"name": "archive-test-private-value"}]}))',
      '    archive.writestr("start_here.html", \'<aside role="contentinfo">Generated by qa.owner on <time datetime="2024-03-01T12:00Z">March 1</time></aside>\')',
      '    archive.writestr("connections/followers_and_following/followers_1.json", json.dumps([{"string_list_data":[{"value":"archive_old","timestamp":1704103200}]},{"string_list_data":[{"value":"archive_new","timestamp":1704276000}]}]))',
      '    archive.writestr("connections/contacts/synced_contacts.json", json.dumps({"contacts_contact_info": [{"string_map_data": {"First Name": {"value": "Işıl".encode().decode("latin1")}}}]}))',
      '    archive.writestr("your_instagram_activity/messages/inbox/example/message_1.json", json.dumps({"messages": [{"content": "sample message"}], "title": "sample conversation"}))',
      '    archive.writestr("your_instagram_activity/messages/inbox/example/message_2.json", json.dumps({"messages": [{"content": "older message"}, {"content": "Long message start " + "ß" * 8200 + " unique_tail_marker"}], "title": "sample conversation"}))',
      '    archive.writestr("your_instagram_activity/likes/liked_posts.json", json.dumps([{"title":"example post","timestamp":stamp,"label_values":[{"label":"URL","value":"https://www.instagram.com/p/example/"}]} for stamp in (1704103200,1709287200)]))',
      '    archive.writestr("personal_information/personal_information/profile_changes.json", json.dumps([{"timestamp":1704103200,"string_map_data":{"Changed":{"value":"Bio"},"Previous value":{"value":"old line"},"New value":{"value":"new line"}}}]))',
      '    archive.writestr("logged_information/past_instagram_insights/audience_insights.json", json.dumps([{"timestamp":1704103200,"Followers":10},{"timestamp":1704276000,"Followers":12}]))',
      '    archive.writestr("media/picture.png", base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/gNUAAAAASUVORK5CYII="))',
      '    archive.writestr("your_instagram_activity/media/posts_1.json", json.dumps([{"media":[{"uri":"media/picture.png","title":"Calendar post","creation_timestamp":1709287200}]}]))',
      '    archive.writestr("your_instagram_activity/media/reels.json", json.dumps({"ig_reels_media":[{"media":[{"uri":"media/reel.mp4","title":"Calendar reel","creation_timestamp":1709287200}]}]}))',
      '    archive.writestr("logged_information/past_instagram_insights/reels.json", json.dumps({"organic_insights_reels":[{"media_map_data":{"Media Thumbnail":{"uri":"media/reel.mp4"}},"string_map_data":{"Upload Timestamp":{"timestamp":1709287200},"Instagram Likes":{"value":"7"},"Instagram Plays":{"value":"100"}}}]}))',
      '    archive.writestr("connections/search_boundary.json", json.dumps([{"name": "search_boundary_" + str(i)} for i in range(27)]))',
      'with zipfile.ZipFile(sys.argv[2], "w") as archive:',
      '    archive.writestr("start_here.html", \'<aside role="contentinfo">Generated by qa.owner on <time datetime="2024-02-01T12:00Z">February 1</time><div>Contains data you requested from <time datetime="2023-02-01T12:00Z">February 1</time> to <time datetime="2024-02-01T12:00Z">February 1</time></div></aside>\')',
      '    archive.writestr("connections/followers_and_following/followers_1.json", json.dumps([{"string_list_data":[{"value": name}]} for name in ("archive_new", "archive_added")]))',
      'with zipfile.ZipFile(sys.argv[3], "w") as archive:',
      '    archive.writestr("start_here.html", \'<aside role="contentinfo">Generated by other_account on <time datetime="2024-02-01T12:00Z">February 1</time></aside>\')',
      '    archive.writestr("connections/followers_and_following/followers_1.json", "[]")',
    ].join('\n'), archivePath, comparisonPath, differentAccountPath]);
    await desktop.evaluate(({dialog}, archivePath) => {
      const open = dialog.showOpenDialog;
      dialog.showOpenDialog = () => {
        dialog.showOpenDialog = open;
        return Promise.resolve({canceled:false,filePaths:[archivePath]});
      };
    }, archivePath);
    const events = [];
    await page.exposeFunction("collectExportEvent", value => events.push(value));
    await page.evaluate(() => window.TrackerDesktop.subscribe(event => {
      if (event.archiveBrowser) window.collectExportEvent(event.archiveBrowser);
    }));
    await page.locator('.sidebar nav a[href="#archive"]').click();
    const guide = page.locator('.archive-dashboard-guide');
    assert.equal(await guide.evaluate(element => element.open), true);
    assert.match(await guide.innerText(), /JSON.*Tüm zamanlar/);
    assert.equal(await guide.getByRole('link', {name:'Hesaplar Merkezi’ni aç'}).getAttribute('href'),
      'https://accountscenter.instagram.com/info_and_permissions/');
    await page.getByRole('button', {name:'Instagram ZIP yükle'}).click();
    const dashboard = page.locator('#archive-dashboard-root');
    await dashboard.getByRole('link', {name:'@archive_new'}).waitFor();
    const overview = dashboard.locator('.archive-dashboard-overview');
    const coverage = overview.locator('.archive-dashboard-coverage');
    const fileInfo = await overview.locator('.archive-dashboard-file-info').innerText();
    assert.ok(fileInfo.startsWith(path.basename(archivePath)));
    assert.match(fileInfo, /@qa\.owner.*Dışa aktarım.*2024/);
    assert.deepEqual(await coverage.locator('li').allTextContents(), ['Takipçiler2', 'Takip edilenBu ZIP’te yok', 'MesajlarVar']);
    const months = overview.locator('.archive-dashboard-months');
    const february = months.getByText('ZIP’te kayıt yok',{exact:true}).locator('..');
    await february.waitFor();
    assert.match(await february.innerText(), /Şub.*2024/i);
    assert.equal(await february.locator('meter,strong').count(), 0);
    assert.deepEqual(await months.locator('meter').evaluateAll(meters => meters.map(meter => meter.value)), [1,1]);
    assert.match(await overview.getByRole('button', {name:/Takipçiler/}).innerText(), /2/);
    assert.equal(await overview.getByRole('button', {name:/Takip edilen/}).count(), 0);
    await dashboard.getByRole('button', {name:'İçerik ve etkileşim',exact:true}).click();
    const content = dashboard.locator('.content-calendar-results');
    const reel = content.getByRole('article').filter({hasText:'Calendar reel'});
    await reel.getByText('Oynatma',{exact:true}).waitFor();
    assert.equal(await reel.getByText('100',{exact:true}).count(), 1);
    assert.equal(await reel.getByText('7',{exact:true}).count(), 1);
    assert.equal(await reel.getByText('Yorumlar',{exact:true}).count(), 0);
    await content.getByRole('article').filter({hasText:'Calendar post'}).locator('img').waitFor();
    const story = content.getByRole('article').filter({hasText:'Yerel hikâye arşivi'}).filter({has:page.getByRole('button',{name:'Yerel önizlemeyi aç'})});
    await story.getByRole('button',{name:'Yerel önizlemeyi aç'}).click();
    await story.locator('img').waitFor();
    for (const name of ['Önceki ay','Sonraki ay']) {
      const centered = await dashboard.getByRole('button',{name,exact:true}).evaluate(button => {
        const outer = button.getBoundingClientRect();
        const arrow = button.querySelector('path').getBoundingClientRect();
        return Math.abs(outer.x + outer.width / 2 - arrow.x - arrow.width / 2) <= 1 &&
          Math.abs(outer.y + outer.height / 2 - arrow.y - arrow.height / 2) <= 1;
      });
      assert.ok(centered, name);
    }
    await dashboard.getByRole('button',{name:'Önceki ay',exact:true}).click();
    await content.getByText('Bu günde içerik kaydı yok.',{exact:true}).waitFor();
    await overview.getByRole('button', {name:/Profil değişiklikleri/}).click();
    await dashboard.locator('.archive-dashboard-rows ins').waitFor();
    await overview.getByRole('button', {name:/Takipçiler/}).click();
    await dashboard.getByRole('link', {name:'@archive_new'}).waitFor();
    assert.equal(await guide.evaluate(element => element.open), false);
    await guide.locator('summary').click();
    assert.equal(await guide.evaluate(element => element.open), true);
    assert.equal(await dashboard.getByRole('link', {name:'@archive_new'}).getAttribute('href'), 'https://www.instagram.com/archive_new/');
    await dashboard.getByLabel('Bitiş tarihi').fill('2024-01-02');
    await dashboard.getByRole('link', {name:'@archive_new'}).waitFor({state:'detached'});
    await dashboard.getByRole('link', {name:'@archive_old'}).waitFor();
    await dashboard.getByRole('button', {name:/Etkinliklerin/}).click();
    assert.equal(await dashboard.getByRole('link', {name:'Bağlantıyı aç'}).first().getAttribute('href'), 'https://www.instagram.com/p/example/');
    await dashboard.getByRole('button', {name:/Profil geçmişi/}).click();
    assert.match(await dashboard.locator('.archive-dashboard-rows ins').textContent(), /new/);
    await dashboard.getByRole('button', {name:/İstatistikler/}).click();
    const insights = dashboard.locator('.archive-dashboard-rows .archive-dashboard-trend meter');
    await insights.first().waitFor();
    assert.equal(await insights.count(), 2);
    await page.getByRole('button', {name:/Rehber/}).click();
    await page.getByText('Işıl', {exact:true}).waitFor();
    await page.getByRole('button', {name:/Mesajlar/}).click();
    await page.locator('.archive-dashboard-rows').getByText('sample message', {exact:true}).waitFor();
    assert.equal(await dashboard.locator('.archive-dashboard-chat-buttons button').count(), 1);
    await dashboard.getByLabel('Veri türü').selectOption({label:'example · 2'});
    await dashboard.locator('.archive-dashboard-message').getByText('older message', {exact:true}).waitFor();
    const search = dashboard.getByRole('searchbox', {name:'Tüm arşivde ara'});
    await search.fill('older message');
    await search.press('Enter');
    const found = dashboard.locator('.archive-dashboard-search-results');
    await found.getByText('older message', {exact:true}).waitFor();
    await found.locator('summary').click();
    await found.locator('.archive-dashboard-message').waitFor();
    await found.getByRole('button', {name:'Veri kaynağını aç'}).click();
    assert.equal(await dashboard.getByLabel('Veri türü').locator(':checked').textContent(), 'example · 2');
    const inFile = dashboard.getByRole('searchbox', {name:'Kayıtlarda ara'});
    await inFile.fill('unique_tail_marker');
    const matchedMessage = dashboard.locator('.archive-dashboard-rows .archive-dashboard-message');
    await matchedMessage.getByText('older message', {exact:true}).waitFor({state:'detached'});
    await matchedMessage.getByRole('heading', {name:/^Long message start /}).waitFor();
    assert.equal(await matchedMessage.count(), 1);
    assert.doesNotMatch(await matchedMessage.innerText(), /unique_tail_marker/);
    await search.fill('unique_tail_marker');
    await search.press('Enter');
    await found.getByText(/unique_tail_marker/).waitFor();
    await search.fill('search_boundary');
    await search.press('Enter');
    const moreSearch = dashboard.getByRole('button', {name:'Daha fazla göster'});
    await moreSearch.waitFor();
    assert.equal(await found.locator('article').count(), 24);
    await moreSearch.click();
    await found.getByText('search_boundary_26', {exact:true}).waitFor();
    assert.equal(await found.locator('article').count(), 28);
    await desktop.evaluate(({dialog}, filenames) => {
      const open = dialog.showOpenDialog;
      dialog.showOpenDialog = () => {
        const filename = filenames.shift();
        if (!filenames.length) dialog.showOpenDialog = open;
        return Promise.resolve({canceled:false,filePaths:[filename]});
      };
    }, [differentAccountPath, comparisonPath]);
    await dashboard.getByRole('button', {name:'İki ZIP karşılaştır'}).click();
    const comparison = dashboard.locator('.archive-dashboard-compare-sources');
    await comparison.getByText('@qa.owner',{exact:true}).waitFor();
    await dashboard.getByRole('button', {name:'İkinci ZIP’i seç'}).click();
    await comparison.getByText('@other_account',{exact:true}).waitFor();
    const compare = dashboard.getByRole('button', {name:'Karşılaştırmayı başlat'});
    assert.equal(await compare.count(), 0);
    await dashboard.getByRole('button', {name:'Yine de karşılaştır'}).waitFor();
    assert.equal(await dashboard.locator('.archive-dashboard-compare-count').count(), 0);
    await dashboard.getByText(/ZIP’ler farklı kullanıcı adlarına ait/).waitFor();
    await dashboard.getByRole('button', {name:'İkinci ZIP’i seç'}).click();
    const reversal = dashboard.getByText(/İlk ZIP daha yeni görünüyor/);
    await reversal.waitFor();
    assert.match(await comparison.getByText(/Tarih aralığı/).innerText(), /2023.*2024/);
    assert.equal(await dashboard.locator('.archive-dashboard-compare-count').count(), 0);
    await dashboard.getByRole('button', {name:'Yer değiştir'}).click();
    assert.equal(await reversal.count(), 0);
    assert.equal(await comparison.locator('section').first().getByText(path.basename(comparisonPath),{exact:true}).count(), 1);
    await compare.click();
    await dashboard.getByText('Yalnız ilk ZIP’te · 1', {exact:true}).click();
    await dashboard.getByRole('link', {name:'@archive_added'}).waitFor();
    await dashboard.getByText('Yalnız ikinci ZIP’te · 1', {exact:true}).click();
    await dashboard.getByRole('link', {name:'@archive_old'}).waitFor();
    await dashboard.getByText('Bu liste iki ZIP’te de bulunmadığı için karşılaştırılamadı.', {exact:true}).waitFor();
    await page.getByRole('button', {name:'Dosya tarayıcısı'}).click();
    const modal = page.getByRole('dialog', {name:'Instagram Veri Arşivi'});
    await modal.getByRole('button', {name:'example.json'}).click();
    await modal.getByText('<b>example</b>', {exact:true}).waitFor();
    await modal.getByRole('button', {name:'Sonraki kayıt'}).click();
    await modal.getByText('archive-test-private-value', {exact:true}).waitFor();
    await modal.getByRole('searchbox').fill('message_1');
    await modal.getByRole('button', {name:'message 1.json'}).click();
    await modal.getByText('sample message', {exact:true}).waitFor();
    await modal.getByRole('button', {name:'Konuşma bilgileri'}).click();
    await modal.getByText('sample conversation', {exact:true}).waitFor();
    await modal.getByRole('searchbox').fill('picture');
    await modal.getByRole('button', {name:'picture.png'}).click();
    await modal.getByRole('button', {name:'Yerel önizlemeyi aç'}).click();
    assert.match(await modal.getByRole('img').getAttribute('src'), /^blob:/);
    assert.deepEqual(events, []);
    await modal.getByRole('button', {name:'Kapat'}).click();
  });
  await check('Account transparency entries save and clear locally without a profile request',async()=>{
    await page.locator('.sidebar nav a[href="#people"]').click();
    await page.locator('.person-title[data-id="200"]').click();
    const panel=page.locator('.profile-about');
    await panel.locator('summary').click();
    const before=await page.evaluate(()=>window.TrackerDesktop.command('state'));
    await panel.locator('#profile-about-joined').fill('2019-04');
    await panel.locator('#profile-about-country').fill('Türkiye');
    await panel.locator('#profile-about-changes').fill('0');
    await panel.getByRole('button',{name:'Hesap bilgilerini kaydet'}).click();
    await panel.getByText('Nisan 2019',{exact:true}).last().waitFor();
    await panel.getByText('Kaynak · Elle girilen yerel kayıt').waitFor();
    const after=await page.evaluate(()=>window.TrackerDesktop.command('state'));
    assert.deepEqual(after.state.people.find(person=>person.id==='200').about,{joined:'2019-04',country:'Türkiye',usernameChanges:0});
    assert.deepEqual(after.state.gate,before.state.gate);
    await panel.locator('#profile-about-joined').fill('');
    await panel.locator('#profile-about-country').fill('');
    await panel.locator('#profile-about-changes').fill('');
    await panel.getByRole('button',{name:'Hesap bilgilerini kaydet'}).click();
    await page.waitForFunction(()=>!document.querySelector('.profile-about-facts'));
    await page.keyboard.press('Escape');
  });
  await check('Partial saved search accepts @, shared URLs, multiple words and Turkish spelling without requests',async()=>{
    await page.locator('.sidebar nav a[href="#people"]').click();
    for(const query of ['@qa.den','QA den','arkadas','https://instagram.com/qa.deniz/?igsh=abc']) {
      await page.locator('#people-search').fill(query);
      assert.equal(await page.locator('.person-card').count(),1,query);
    }
    await page.locator('#people-search').fill('');
    await page.locator('.sidebar nav a[href="#overview"]').click();
  });
  await check('Footer help contains disclosure and retention details without starting network work', async () => {
    const before = await page.evaluate(() => window.TrackerDesktop.command('state'));
    await page.locator('.page-footer [data-action="usage-notes"]').click();
    await page.getByRole('dialog').getByRole('heading', {name:'Kullanım notları',exact:true}).waitFor();
    assert.match(await page.getByRole('dialog').innerText(), /2\.000 olay/);
    assert.match(await page.getByRole('dialog').innerText(), /Değişikliğin kesin zamanı/);
    await page.screenshot({path:path.join(output,'usage-notes-tr.png')});
    await page.keyboard.press('Escape');
    assert.equal(await page.getByRole('dialog').count(), 0);
    const after = await page.evaluate(() => window.TrackerDesktop.command('state'));
    assert.deepEqual(after.gate, before.gate);
    assert.deepEqual(after.account, before.account);
    await page.evaluate(() => window.scrollTo(0,0));
    await page.screenshot({path:path.join(output,'overview-tr.png'),fullPage:true});
    await page.locator('.sidebar nav a[href="#timeline"]').click();
    assert.equal(await page.locator('.timeline-explainer').count(), 0);
    await page.screenshot({path:path.join(output,'timeline-tr.png'),fullPage:true});
    await page.locator('.sidebar nav a[href="#overview"]').click();
  });
  await check('Own and watched list logs show escaped frozen names with bounded pagination and local profile navigation', async () => {
    await page.locator('.sidebar nav a[href="#notifications"]').click();
    await page.locator('[data-id="qa-named-own"]').click();
    await page.getByRole('dialog').getByRole('button',{name:'Değişen hesapları gör',exact:true}).click();
    assert.equal(await page.locator('.member-change-list li').count(),2);
    assert.match(await page.getByRole('dialog').innerText(),/@qa\.203/);
    assert.match(await page.getByRole('dialog').innerText(),/@qa\.200/);
    await page.keyboard.press('Escape');
    await page.locator('[data-id="qa-named-watched"]').click();
    await page.getByRole('dialog').getByRole('button',{name:'Değişen hesapları gör',exact:true}).click();
    assert.equal(await page.locator('.member-change-list li').count(),100);
    assert.match(await page.getByRole('dialog').innerText(),/Unsafe <img/);
    assert.equal(await page.evaluate(()=>window.memberInjected),undefined);
    await page.screenshot({path:path.join(output,'watched-list-changes.png')});
    await page.getByRole('dialog').getByRole('button',{name:'Sonraki sayfa',exact:true}).click();
    assert.equal(await page.locator('.member-change-list li').count(),4);
    assert.match(await page.getByRole('dialog').innerText(),/@qa\.400/);
    await page.getByRole('dialog').getByRole('button',{name:'Önceki sayfa',exact:true}).click();
    assert.equal(await page.locator('.member-change-list li').count(),100);
    await page.locator('[data-action="list-change-filter"][data-value="removed"]').click();
    assert.equal(await page.locator('.member-change-list li').count(),1);
    assert.match(await page.locator('.member-change-list').innerText(),/@qa\.400/);
    await page.screenshot({path:path.join(output,'missing-list-member.png')});
    await page.locator('[data-action="list-change-filter"][data-value="all"]').click();
    assert.equal(await page.locator('.member-change-list li').count(),100);
    await page.keyboard.press('Escape');
    const viewport=await page.evaluate(()=>({width:innerWidth,height:innerHeight}));
    for(const language of ['en','es','pt-BR','tr']) {
      await page.locator('.sidebar nav a[href="#settings"]').click();
      await page.locator('#app-language').selectOption(language);
      await page.locator('.sidebar nav a[href="#notifications"]').click();
      await page.locator('[data-id="qa-named-watched"]').click();
      await page.getByRole('dialog').locator('[data-action="list-changes"]').click();
      await page.setViewportSize({width:390,height:844});
      assert.equal(await page.getByRole('dialog').evaluate(node=>node.scrollWidth>node.clientWidth+1),false);
      assert.match(await page.getByRole('dialog').innerText(),/@qa\.500/);
      await page.keyboard.press('Escape');
      await page.setViewportSize(viewport);
    }
    await page.locator('.sidebar nav a[href="#people"]').click();
    await page.locator('[data-action="person"][data-id="200"]').first().click();
    await page.getByRole('dialog').getByRole('button',{name:'Takip listelerini aç',exact:true}).click();
    assert.equal(await page.locator('#list-owner').inputValue(),'200');
    await page.locator('.sidebar nav a[href="#overview"]').click();
  });
  await check(
    "Closing the actual window keeps the renderer and local worker alive in the tray",
    async () => {
      const before = await page.evaluate(() =>
        window.TrackerDesktop.command("state"),
      );
      await desktop.evaluate(({ BrowserWindow }) =>
        BrowserWindow.getAllWindows()[0].close(),
      );
      assert.equal(
        await desktop.evaluate(
          ({ BrowserWindow }) => BrowserWindow.getAllWindows().length,
        ),
        1,
      );
      assert.equal(
        await desktop.evaluate(({ BrowserWindow }) =>
          BrowserWindow.getAllWindows()[0].isVisible(),
        ),
        false,
      );
      const hidden = await page.evaluate(() =>
        window.TrackerDesktop.command("state"),
      );
      assert.equal(hidden.ok, true);
      assert.equal(hidden.state.account.id, before.state.account.id);
      await desktop.evaluate(({ app }) => app.emit("activate"));
      assert.equal(
        await desktop.evaluate(({ BrowserWindow }) =>
          BrowserWindow.getAllWindows()[0].isVisible(),
        ),
        true,
      );
    },
  );
  await check("Hidden and minimized windows skip updates and resume the latest state once", async () => {
    await page.evaluate(() => {
      window.qaDOMUpdates = 0;
      window.qaResumeCount = 0;
      window.qaObserver = new MutationObserver(records => { window.qaDOMUpdates += records.length; });
      window.qaObserver.observe(document.getElementById('app'), { childList: true, subtree: true });
      window.qaUnsubscribe = window.TrackerDesktop.subscribe(event => { if (event.type === 'resume') window.qaResumeCount++; });
    });
    for (const mode of ['hide', 'minimize']) {
      await page.evaluate(() => { window.qaDOMUpdates = 0; window.qaResumeCount = 0; });
      await desktop.evaluate(({ BrowserWindow }, mode) => BrowserWindow.getAllWindows()[0][mode](), mode);
      for (const compact of [false, true, false, true]) {
        const result = await page.evaluate(compact => window.TrackerDesktop.command('settings', { compact }), compact);
        assert.equal(result.ok, true);
      }
      assert.equal(await page.evaluate(() => window.qaDOMUpdates), 0);
      await desktop.evaluate(({ app }) => app.emit('activate'));
      await page.waitForFunction(() => document.body.dataset.density === 'compact');
      assert.equal(await page.evaluate(() => window.qaResumeCount), 1);
      await page.evaluate(() => window.TrackerDesktop.command('settings', { compact: false }));
      await page.waitForFunction(() => document.body.dataset.density === 'comfortable');
    }
    await page.evaluate(() => { window.qaObserver.disconnect(); window.qaUnsubscribe(); });
  });
  await check("Resuming a code wait or failed service restores actionable UI", async () => {
    const state = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
    await desktop.evaluate(({ BrowserWindow }, state) => BrowserWindow.getAllWindows()[0].webContents.send('tracker:event', { type: 'resume', visible:true, state, busy: true, progress: { phase: 'code' }, error: null }), state);
    await page.locator('#code-form').waitFor();
    await desktop.evaluate(({ BrowserWindow }, state) => BrowserWindow.getAllWindows()[0].webContents.send('tracker:event', { type: 'resume', visible:true, state, busy: false, progress: null, error: { message: 'Synthetic service failure' } }), state);
    await page.waitForFunction(() => !document.querySelector('#code-form')?.closest('dialog')?.open);
    await page.getByText('Synthetic service failure', { exact: true }).waitFor();
    await desktop.evaluate(({ BrowserWindow }, state) => BrowserWindow.getAllWindows()[0].webContents.send('tracker:event', { type: 'resume', visible:true, state, busy: false, progress: null, error: null }), state);
  });
  await check("Renderer isolation and no Node access", async () => {
    const prefs = await desktop.evaluate(({ BrowserWindow }) => {
      const p =
        BrowserWindow.getAllWindows()[0].webContents.getLastWebPreferences();
      return {
        nodeIntegration: p.nodeIntegration,
        sandbox: p.sandbox,
        contextIsolation: p.contextIsolation,
        webSecurity: p.webSecurity,
      };
    });
    assert.deepEqual(prefs, {
      nodeIntegration: false,
      sandbox: true,
      contextIsolation: true,
      webSecurity: true,
    });
    assert.equal(await page.evaluate(() => typeof require), "undefined");
    assert.equal(await page.evaluate(() => typeof process), "undefined");
  });
  await check("Secrets absent from renderer state", async () => {
    const state = await page.evaluate(() =>
      window.TrackerDesktop.command("state"),
    );
    assert.equal(state.state.connected, false);
    assert.equal("session" in state.state, false);
    assert.equal("cookies" in state.state, false);
  });
  await check("Unknown IPC operation and file path rejected", async () => {
    const result = await page.evaluate(async () => {
      try {
        await window.TrackerDesktop.command("export_backup", {
          path: "invalid",
        });
        return false;
      } catch {
        return true;
      }
    });
    assert.equal(result, true);
  });
  await check("Subframe cannot invoke privileged IPC", async () => {
    await page.evaluate(() => {
      const f = document.createElement("iframe");
      f.src = location.href;
      f.id = "ipc-test-frame";
      document.body.append(f);
    });
    const frame = page.frameLocator("#ipc-test-frame");
    await frame.locator("#app").waitFor();
    const inner = page.frames().find((f) => f !== page.mainFrame());
    assert.equal(
      await inner.evaluate(async () => {
        if (!window.TrackerDesktop) return true;
        try {
          await window.TrackerDesktop.command("state");
          return false;
        } catch {
          return true;
        }
      }),
      true,
    );
    await page.locator("#ipc-test-frame").evaluate((n) => n.remove());
  });
  await check(
    "Lists show partial coverage without replacing completed snapshot",
    async () => {
      await page.locator('.sidebar a[href="#lists"]').click();
      await page.locator('#list-owner').selectOption('100');
      await listReady();
      await page
        .getByText("Tamamlanan kayıt: 3 hesap", { exact: true })
        .waitFor();
      assert.equal(
        await page
          .getByText("1 / 3 hesap okundu", { exact: true })
          .isVisible(),
        true,
      );
      assert.equal(await page.locator("#list-rows .list-row").count(), 3);
      assert.equal(
        await page
          .getByRole("button", { name: "Listeyi yeniden kontrol et", exact: true })
          .isDisabled(),
        true,
      );
    },
  );
  await check('Restarting a partial watched list keeps the previous 76 rows visible without producing a comparison',async()=>{
    await page.locator('#list-owner').selectOption('200');
      await listReady();
    assert.equal(await page.locator('#list-rows .list-row').count(),76);
    await page.getByText('İlk tam liste henüz hazır değil',{exact:true}).waitFor();
    await page.getByText(/Bu hesap gizli\. Son takip edilen listene göre/).waitFor();
    await page.getByText('0 / 82 hesap okundu',{exact:true}).waitFor();
    await page.getByText('Önceki yarım listeden 76 hesap gösteriliyor; yeni tarama tamamlanınca güncellenecek.',{exact:true}).waitFor();
    assert.equal(await page.getByRole('button',{name:'Farkları göster',exact:true}).isDisabled(),true);
    assert.equal(await page.locator('#list-rows .badge').count(),0);
    await page.locator('#list-owner').selectOption('100');
      await listReady();
  });
  await check('Common accounts use saved lists in either direction and label incomplete sources', async () => {
    await page.locator('#list-peer').selectOption('200');
    await page.getByText('Okunan hesaplar arasında 0 ortak kişi bulundu.', {exact:true}).waitFor();
    await page.getByRole('button', {name:'Ortak takip edilenler',exact:true}).click();
    await page.getByText('Kayıtlı listelerde 2 ortak kişi bulundu.', {exact:true}).waitFor();
    await page.locator('#list-rows').getByText('@qa.201', {exact:true}).waitFor();
    const known = page.locator('#list-rows .list-person[data-id="201"]');
    assert.equal(await known.getAttribute('data-photo-owner'), '200');
    assert.equal(await known.locator('.common-list-counts').textContent(), '20 takipçi · 5 takip edilen');
    assert.equal(await page.locator('#list-rows .list-person[data-id="204"] .common-list-counts').isVisible(), false);
    assert.equal(await page.locator('#list-rows .list-followers').count(), 0);
    await page.locator('#list-peer').selectOption('201');
    await page.getByText('Karşılaştırma için iki profilin de kayıtlı listesi gerekiyor.', {exact:true}).waitFor();
    await page.getByRole('button', {name:'Listeye dön',exact:true}).click();
    await page.getByRole('button', {name:'Takipçiler',exact:true}).click();
    await listReady();
  });
  await check(
    "Snapshot comparison uses identities and names both directions",
    async () => {
      await page
        .getByRole("button", { name: "Farkları göster", exact: true })
        .click();
      assert.equal(
        await page
          .locator(".compare-stat")
          .first()
          .locator("strong")
          .textContent(),
        "1",
      );
      assert.equal(
        await page
          .locator(".compare-stat")
          .nth(1)
          .locator("strong")
          .textContent(),
        "1",
      );
      assert.equal(await page.locator("#list-rows .list-row").count(), 2);
      assert.equal(
        await page
          .locator("#list-rows")
          .getByText("Son kayıtta yok", { exact: true })
          .count(),
        1,
      );
      await page.screenshot({
        path: path.join(output, "comparison.png"),
        fullPage: true,
      });
      await page
        .getByRole("button", { name: "Listeye dön", exact: true })
        .click();
    },
  );
  await check(
    "Two-direction relationship filters and local search",
    async () => {
      await page.locator("#relation-filter").selectOption("mutual");
      await listReady();
      assert.equal(await page.locator("#list-rows .list-row").count(), 1);
      await page.locator("#relation-filter").selectOption("oneway");
      await listReady();
      assert.equal(await page.locator("#list-rows .list-row").count(), 2);
      await page.locator("#relation-filter").selectOption("all");
      await listReady();
      await page.locator("#list-search").fill("qa.203");
      await listReady();
      assert.equal(await page.locator("#list-rows .list-row").count(), 1);
    },
  );
  await check(
    "List pagination follows search results after clearing a narrow query",
    async () => {
      await page.locator("#list-owner").selectOption("300");
      await listReady();
      await page.locator("#list-search").fill("");
      await listReady();
      assert.equal(await page.locator("#list-rows .list-row").count(), 100);
      await page.locator("#list-search").fill("qa.301");
      await listReady();
      assert.equal(await page.locator("#list-rows .list-row").count(), 1);
      assert.equal(
        await page
          .getByRole("button", { name: "Daha fazla göster", exact: true })
          .count(),
        0,
      );
      await page.locator("#list-search").fill("");
      await listReady();
      await page.locator("#list-pagination").scrollIntoViewIfNeeded();
      await page.waitForFunction(()=>document.querySelectorAll("#list-rows .list-row").length===150);
      assert.equal(await page.locator("#list-rows .list-row").count(), 150);
      await page.locator("#list-owner").selectOption("100");
      await listReady();
    },
  );
  await check("Own non-followers remain visible as local exceptions below the main count", async () => {
    await page.locator("#list-owner").selectOption("100");
    await page.locator("#list-search").fill("");
    await page.locator('[data-action="list-tab"][data-value="following"]').click();
    await page.locator("#relation-filter").selectOption("oneway");
    const before = (await page.evaluate(() => window.TrackerDesktop.command("state"))).state;
    const main = page.locator("#list-rows");
    const exceptions = page.locator("#list-exceptions");
    await main.getByText("@qa.204", {exact:true}).waitFor();
    await main.locator(".list-exception-menu summary").click();
    await main.getByRole("button", {name:"İstisnalara taşı"}).click();
    await exceptions.getByText("@qa.204", {exact:true}).waitFor();
    assert.equal(await main.getByText("@qa.204", {exact:true}).count(), 0);
    assert.equal(await page.locator("#list-result-count").textContent(), "0 / 0 hesap gösteriliyor");
    await page.locator("#relation-filter").selectOption("all");
    await main.getByText("@qa.204", {exact:true}).waitFor();
    assert.equal(await exceptions.isVisible(), false);
    await page.locator("#relation-filter").selectOption("oneway");
    await exceptions.getByText("@qa.204", {exact:true}).waitFor();
    await exceptions.locator(".list-exception-menu summary").click();
    await exceptions.getByRole("button", {name:"Ana listeye geri al"}).click();
    await main.getByText("@qa.204", {exact:true}).waitFor();
    assert.equal(await exceptions.isVisible(), false);
    const after = (await page.evaluate(() => window.TrackerDesktop.command("state"))).state;
    assert.deepEqual(after.gate, before.gate);
    assert.deepEqual(after.lists, before.lists);
    await page.locator('[data-action="list-tab"][data-value="followers"]').click();
  });
  await check("Persistent notes and group; text is escaped", async () => {
    await page.locator('.sidebar a[href="#people"]').click();
    await page
      .getByRole("button", { name: "QA qa.deniz kaydını aç", exact: true })
      .click();
    await page.getByRole("dialog").locator(".avatar img").waitFor();
    await page.waitForFunction(
      () => document.querySelector("dialog .avatar img")?.naturalWidth > 0,
    );
    assert.equal(
      await page.locator("#person-note").inputValue(),
      "PRIVATE-QA-NOTE <img src=x onerror=alert(1)>",
    );
    assert.equal(await page.getByRole("dialog").locator("img").count(), 1);
    await page.locator('.profile-personal > summary').click();
    await page
      .locator("#person-note")
      .fill("Saved QA note <script>window.hacked=true</script>");
    await page.locator("#person-group").fill("QA saved group");
    await page
      .getByRole("button", { name: "Notu ve grubu kaydet", exact: true })
      .click();
    await page
      .getByText("Not ve grup şifreli olarak kaydedildi.", { exact: true })
      .waitFor();
    await page.getByRole("button", { name: "Kapat", exact: true }).click();
  });
  await check("Favorite toggle persists", async () => {
    await page
      .getByRole("button", {
        name: "qa.deniz favori durumunu değiştir",
        exact: true,
      })
      .click();
    await page
      .getByText("Favori tercihi kaydedildi.", { exact: true })
      .waitFor();
    await page.getByRole("button", { name: "Favoriler", exact: true }).click();
    assert.equal(await page.locator(".person-card").count(), 0);
  });
  await check("Compact preference saved in encrypted store", async () => {
    await page.locator('.sidebar a[href="#settings"]').click();
    await page
      .getByRole("switch", { name: "Kompakt görünüm", exact: true })
      .click();
    await page.waitForFunction(
      () => document.body.dataset.density === "compact",
    );
  });
  await check(
    "One- and five-minute intervals persist through the real IPC and encrypted vault",
    async () => {
      const options = await page
        .locator("#auto-interval option")
        .evaluateAll((nodes) => nodes.map((n) => n.value));
      assert.deepEqual(options, requestPolicy.intervalMinutes.map(String));
      for (const value of ["1", "5"]) {
        await page.locator("#auto-interval").selectOption(value);
        await page.waitForFunction(
          async (value) =>
            (await window.TrackerDesktop.command("state")).state.settings
              .interval === Number(value),
          value,
        );
        // A persisted worker result precedes the renderer's IPC completion.
        // Observe the public UI before selecting the next value.
        await page.waitForFunction(
          (value) => document.querySelector('.monitor-summary')?.textContent.includes(`Her profil kendi son kontrolünden yaklaşık ${value} dakika`) && !document.querySelector('#auto-interval')?.disabled,
          value,
        );
      }
      assert.equal(await page.locator(".monitor-summary").isVisible(), true);
      assert.match(await page.locator(".monitor-summary").innerText(), /Her profil kendi son kontrolünden yaklaşık 5 dakika/);
      const state = (await page.evaluate(() => window.TrackerDesktop.command("state"))).state;
      assert.equal(state.version,version);
      assert.deepEqual(state.controlIntervals,requestPolicy.intervalMinutes);
      assert.equal(state.requestBudget.accountHourly,requestPolicy.accountHourly);
      assert.equal(state.requestBudget.accountDaily,requestPolicy.accountDaily);
      assert.equal(state.requestBudget.sharedHourly,requestPolicy.deviceHourly);
      assert.equal(state.requestBudget.sharedDaily,requestPolicy.deviceDaily);
      const monitor = state.monitor;
      assert.equal(monitor.intervalSeconds, 300);
      assert.equal(monitor.estimatedCycleSeconds, Math.max(300, monitor.profileCount *
        (requestPolicy.minimumSpacingMilliseconds+requestPolicy.maximumSpacingMilliseconds)/2000));
    },
  );
  await check(
    "Notification opt-in creates an owned isolated Windows shortcut",
    async () => {
      await page
        .getByRole("switch", { name: "Windows bildirimleri", exact: true })
        .click();
      await waitForState(page, reply => (reply.state.settings
            .notifications));
      const details = await desktop.evaluate(
        ({ shell }, qaHome) =>
          shell.readShortcutLink(
            qaHome + "/shortcuts/InstagramTracker Desktop.lnk",
          ),
        home,
      );
      assert.equal(
        details.appUserModelId,
        "dev.kaankaya.instagramtracker.desktop",
      );
      assert.equal(
        details.toastActivatorClsid.toUpperCase(),
        "{D3085DD3-667D-4514-BD5F-1C8C0C8A5927}",
      );
      await page
        .getByRole("switch", { name: "Windows bildirimleri", exact: true })
        .click();
      await waitForState(page, reply => (!reply.state.settings
            .notifications));
    },
  );
  await check("Portable backup preserves long Unicode notes without exporting credentials", async () => {
    const backup = path.join(home, "portable-export.trackerhistory");
    const password = "örnek parola 🌿 123";
    const originalNote = (await page.evaluate(() => window.TrackerDesktop.command("state")))
      .state.people.find(row => row.id === "200").note || "";
    const note = "ş".repeat(1100) + "a".repeat(899) + "🌿";
    const saved = await page.evaluate(note => window.TrackerDesktop.command("save_person", {id:"200",note}), note);
    assert.equal(saved.ok, true);
    await desktop.evaluate(({dialog}, filename) => {
      const original = dialog.showSaveDialog;
      dialog.showSaveDialog = () => {
        dialog.showSaveDialog = original;
        return Promise.resolve({canceled:false,filePath:filename});
      };
    }, backup);
    await page.locator('.sidebar nav a[href="#settings"]').click();
    await page.getByRole("button", {name:"Taşınabilir yedek oluştur",exact:true}).click();
    await page.locator("#portable-password").fill(password);
    await page.locator("#portable-confirm").fill(password);
    await page.locator("#portable-backup-form button").click();
    await page.getByText("Taşınabilir yedek kaydedildi.", {exact:true}).waitFor();
    const value = JSON.parse(execFileSync(python, ['-c',
      'import json,sys; sys.path.insert(0,"interface/desktop/backend"); from portable_backup import open_backup; print(json.dumps(open_backup(sys.argv[1],sys.argv[2])))',
      backup,password], {cwd:root,encoding:'utf8'}));
    assert.equal(value.account.username, "qa.owner");
    assert.ok(value.history.length > 0);
    assert.equal(value.people.find(row => row.profile.username === "qa.deniz").note, note);
    assert.deepEqual(Object.keys(value).sort(), ["account","daily","history","lists","people","version"]);
    const restored = await page.evaluate(note => window.TrackerDesktop.command("save_person", {id:"200",note}), originalNote);
    assert.equal(restored.ok, true);
  });
  await check(
    "Encrypted export and CSV through native file boundary",
    async () => {
      await desktop.evaluate(({ dialog }, qaHome) => {
        dialog.showSaveDialog = async (_window, options) => ({
          canceled: false,
          filePath: qaHome + "/" + options.defaultPath,
        });
      }, home);
      await page
        .getByRole("button", { name: "Windows yedeği oluştur", exact: true })
        .click();
      await page
        .getByText("Dosya işlemi tamamlandı.", { exact: true })
        .waitFor();
      const backup = await readFile(
        path.join(home, "InstagramTracker.trackerbackup"),
      );
      assert.equal(backup.includes(Buffer.from("Saved QA note")), false);
      await page.locator('.sidebar a[href="#lists"]').click();
      await page
        .locator("#toast")
        .evaluate((n) => n.classList.remove("visible"));
      await page
        .getByRole("button", { name: "CSV dışa aktar", exact: true })
        .click();
      await page
        .locator("#toast.visible")
        .getByText("Dosya işlemi tamamlandı.", { exact: true })
        .waitFor();
      // Native save writes asynchronously; wait through the invocation boundary.
      await waitForState(page, reply => (!reply.busy));
      const csv = await readFile(
        path.join(home, "InstagramTracker-liste.csv"),
        "utf8",
      );
      assert.equal(csv.includes("qa.203"), true);
      assert.equal(csv.includes("session"), false);
    },
  );
  await check(
    "Restart restores encrypted notes, favorites and preferences",
    async () => {
      await desktop.close();
      desktop = null;
      await launch();
      await page.locator('.sidebar a[href="#people"]').click();
      await page
        .getByRole("button", { name: "QA qa.deniz kaydını aç", exact: true })
        .click();
      assert.equal(
        await page.locator("#person-note").inputValue(),
        "Saved QA note <script>window.hacked=true</script>",
      );
      assert.equal(
        await page.locator("#person-group").inputValue(),
        "QA saved group",
      );
      assert.equal(await page.evaluate(() => window.hacked), undefined);
      assert.equal(
        await page.evaluate(() => document.body.dataset.density),
        "compact",
      );
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
      const raw = await readFile(path.join(home, "tracker.vault"));
      assert.equal(raw.includes(Buffer.from("Saved QA note")), false);
    },
  );
  await check(
    "Encrypted restore through the native confirmation boundary",
    async () => {
      await desktop.evaluate(({ dialog }, qaHome) => {
        dialog.showOpenDialog = async () => ({
          canceled: false,
          filePaths: [qaHome + "/InstagramTracker.trackerbackup"],
        });
        dialog.showMessageBox = async () => ({ response: 1 });
      }, home);
      await page.locator('.sidebar nav a[href="#settings"]').click();
      await page
        .getByRole("button", { name: "Windows yedeğini geri yükle", exact: true })
        .click();
      await page
        .locator("#toast.visible")
        .getByText("Dosya işlemi tamamlandı.", { exact: true })
        .waitFor();
      const state = await page.evaluate(() =>
        window.TrackerDesktop.command("state"),
      );
      assert.equal(state.state.connected, false);
      assert.equal(
        state.state.people.find((p) => p.id === "200").note,
        "Saved QA note <script>window.hacked=true</script>",
      );
      assert.equal(state.state.gate.status, 429);
    },
  );
  await check("Timeline filters and value details", async () => {
    await page.locator('.sidebar a[href="#timeline"]').click();
    await page
      .getByRole("button", { name: "Profil farkları", exact: true })
      .click();
    await waitHistory(page);
    assert.equal(await page.locator(".timeline-events .event-row").count(), 4);
    await page.locator(".timeline-events .event-row").first().click();
    assert.equal(
      await page.getByRole("dialog").locator(".change-values").isVisible(),
      true,
    );
    await page.getByRole("button", { name: "Kapat", exact: true }).click();
  });
  await check(
    "Account-specific type preferences save without changing other accounts",
    async () => {
      await page.locator('.sidebar nav a[href="#people"]').click();
      await page.getByRole("button", { name: "Tümü", exact: true }).click();
      await page
        .getByRole("button", { name: "QA qa.deniz kaydını aç", exact: true })
        .click();
      await page
        .locator('.profile-more > summary').click();
      await page
        .getByRole("button", { name: "Bildirim tercihleri", exact: true })
        .click();
      const form = page.locator("#notification-rule-form");
      assert.equal(
        await form.getByLabel("Takipçi sayısı", { exact: true }).isChecked(),
        false,
      );
      assert.equal(
        await form.getByLabel("Kullanıcı adı", { exact: true }).isChecked(),
        true,
      );
      await form.getByLabel("Takip edilen sayısı", { exact: true }).uncheck();
      await form
        .getByRole("button", { name: "Tercihleri kaydet", exact: true })
        .click();
      await page
        .getByText("Bildirim tercihleri kaydedildi.", { exact: true })
        .waitFor();
      const state = await page.evaluate(() =>
        window.TrackerDesktop.command("state"),
      );
      assert.equal(
        state.state.notificationRules["200"].fields.followers,
        false,
      );
      assert.equal(
        state.state.notificationRules["200"].fields.following,
        false,
      );
      assert.equal(state.state.notificationRules["100"], undefined);
    },
  );
  await check(
    "One notification opens an escaped old/new log including muted changes and marks it read",
    async () => {
      const badge = page.locator('.sidebar a[href="#notifications"] .notification-count');
      const bellBadge = page.locator('.top-actions [data-action="notifications"] .notification-count');
      const countBefore = (await page.evaluate(() => window.TrackerDesktop.command("state"))).state.historyMeta.notificationCounts.unread.all;
      assert.ok(countBefore > 0);
      const formatted = count => new Intl.NumberFormat("tr-TR").format(count);
      assert.equal(await badge.textContent(), formatted(countBefore));
      assert.equal(await bellBadge.textContent(), formatted(countBefore));
      await page
        .getByRole("button", { name: "Bildirim kayıtları", exact: true })
        .click();
      await page
        .getByRole("heading", {
          name: "Bir kontrolde neler değişti?",
          exact: true,
        })
        .waitFor();
      const row = page.locator(
        '.notification-log-row[data-id="qa-multi-change"]',
      );
      assert.equal(await row.count(), 1);
      assert.equal(
        await row
          .getByText("@qa.deniz · 3 değişiklik", { exact: true })
          .isVisible(),
        true,
      );
      // Exercise the exact main-to-renderer event used by the native toast
      // click callback, without claiming Windows delivered a real toast.
      await desktop.evaluate(
        ({ BrowserWindow }, id) =>
          BrowserWindow.getAllWindows()[0].webContents.send("tracker:event", {
            type: "open_notification",
            id,
          }),
        "qa-multi-change",
      );
      await page
        .getByRole("dialog")
        .getByRole("heading", { name: "@qa.deniz · 3 değişiklik", exact: true })
        .waitFor();
      assert.equal(await page.locator(".change-log-details > li").count(), 3);
      const pairWidths = await page
        .locator(".change-log-details > li")
        .first()
        .locator(".change-values > div")
        .evaluateAll((nodes) =>
          nodes.map((node) => node.getBoundingClientRect().width),
        );
      assert.equal(pairWidths.length, 2);
      assert.equal(Math.abs(pairWidths[0] - pairWidths[1]) < 2, true);
      assert.equal(pairWidths[1] > 150, true);
      assert.equal(
        await page
          .locator(".change-log-details")
          .getByText("Yalnız kayıtta", { exact: true })
          .count(),
        1,
      );
      assert.equal(
        await page
          .locator(".change-log-details")
          .getByText("Bildirim için seçili", { exact: true })
          .count(),
        2,
      );
      assert.equal(
        await page
          .locator(".change-log-details")
          .getByText(
            "Biyografi: After <script>window.logInjected=true</script>",
            { exact: true },
          )
          .isVisible(),
        true,
      );
      assert.equal(await page.evaluate(() => window.logInjected), undefined);
      assert.equal((await historyDetail(page,'notification','qa-multi-change')).readAt!=null,true);
      await page.waitForFunction(({value}) => {
        const counters = document.querySelectorAll('.notification-count');
        return value ? counters.length === 2 && Array.from(counters).every(node => node.textContent === value) : counters.length === 0;
      }, {value:countBefore > 1 ? formatted(countBefore - 1) : ""});
      await page.screenshot({
        path: path.join(output, "notification-detail.png"),
        fullPage: true,
      });
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
      await page.getByRole("button", { name: /^Okunmamış/ }).click();
      assert.equal(
        await page
          .locator('.notification-log-row[data-id="qa-multi-change"]')
          .count(),
        0,
      );
      await page.getByRole("button", { name: "Tümü", exact: true }).click();
      await desktop.close();
      desktop = null;
      await launch();
      const reopened = await page.evaluate(() =>
        window.TrackerDesktop.command("state"),
      );
      assert.equal(
        (await historyDetail(page,'notification','qa-multi-change')).readAt != null,
        true,
      );
      assert.equal(
        reopened.state.notificationRules["200"].fields.following,
        false,
      );
      await page.locator('.sidebar nav a[href="#notifications"]').click();
      await page
        .locator('.notification-log-row[data-id="qa-multi-change"]')
        .click();
      assert.equal(await page.locator(".change-log-details > li").count(), 3);
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
    },
  );
  await check(
    "Access review shows suspicion and concrete evidence without a definitive blocking claim",
    async () => {
      await page.locator('.sidebar nav a[href="#lists"]').click();
      await page.locator("#list-owner").selectOption("100");
      await listReady();
      await page
        .getByRole("button", { name: "Takipçiler", exact: true })
        .click();
      const review = page.locator(".access-review");
      assert.equal(
        await review
          .getByText("Engellenme şüphesi", { exact: true })
          .isVisible(),
        true,
      );
      assert.equal(
        await review
          .getByText("Kullanıcı adı değişmiş; profil açılıyor", {
            exact: true,
          })
          .isVisible(),
        true,
      );
      await review
        .getByRole("button", { name: "Kanıtları gör", exact: true })
        .first()
        .click();
      const evidence = page.getByRole("dialog").locator(".access-evidence");
      assert.equal(await evidence.getByText("404", { exact: true }).isVisible(), false);
      await evidence.getByText("Teknik ayrıntılar", { exact: true }).click();
      assert.equal(
        await evidence
          .getByRole("heading", { name: "Engellenme şüphesi", exact: true })
          .isVisible(),
        true,
      );
      assert.equal(
        await evidence.getByText("404", { exact: true }).isVisible(),
        true,
      );
      assert.equal(
        await evidence.getByText("200", { exact: true }).isVisible(),
        true,
      );
      assert.equal(
        (await evidence.textContent()).includes(
          "kesin engelleme kanıtı değildir",
        ),
        true,
      );
      assert.equal((await evidence.textContent()).includes("engelledi"), false);
      await page.screenshot({
        path: path.join(output, "access-evidence.png"),
        fullPage: true,
      });
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
      await review
        .getByRole("button", { name: "Profil kontrol geçmişi", exact: true })
        .click();
      await waitHistory(page);
      assert.equal(
        await page.locator(".timeline-events .event-row").count(),
        1,
      );
      assert.equal(
        (
          await page.locator(".timeline-events .event-row").textContent()
        ).includes("Engellenme şüphesi"),
        true,
      );
      await page.locator(".timeline-events .event-row").click();
      assert.equal(
        await page.getByRole("dialog").locator(".access-evidence").isVisible(),
        true,
      );
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
    },
  );
  await check(
    "Missing-follower opt-in persists and manual access command respects offline QA boundary",
    async () => {
      await page.locator('.sidebar nav a[href="#settings"]').click();
      await page
        .getByRole("switch", {
          name: "Kaybolan kayıtlı takipçileri incele",
          exact: true,
        })
        .click();
      await waitForState(page, reply => (reply.state.settings
            .investigateMissing));
      const result = await page.evaluate(() =>
        window.TrackerDesktop.command("check_access", { id: "200" }),
      );
      assert.equal(result.ok, false);
      assert.equal(result.error.code, "offline");
      assert.equal(result.state.gate.attempts, 2);
      assert.equal(
        result.state.access.find((c) => c.id === "200").checks.at(-1).status,
        "suspected",
      );
      await page
        .getByRole("switch", {
          name: "Kaybolan kayıtlı takipçileri incele",
          exact: true,
        })
        .click();
      await waitForState(page, reply => (!reply.state.settings
            .investigateMissing));
    },
  );
  await check(
    "Login password is hidden by default and can be toggled without changing the value",
    async () => {
      await page
        .getByRole("button", { name: "Hesap bağlantısı", exact: true })
        .first()
        .click();
      assert.equal(
        await page.locator("#login-password").getAttribute("type"),
        "password",
      );
      assert.equal(
        await page.locator("#login-password").getAttribute("autocomplete"),
        "off",
      );
      const password = page.locator("#login-password");
      const toggle = page.getByRole("button", { name: "Şifreyi göster", exact: true });
      await password.fill("SYNTHETIC-PASSWORD");
      await toggle.click();
      assert.equal(await toggle.getAttribute("aria-pressed"), "true");
      assert.equal(await password.getAttribute("type"), "text");
      assert.equal(await password.inputValue(), "SYNTHETIC-PASSWORD");
      await toggle.click();
      assert.equal(await toggle.getAttribute("aria-pressed"), "false");
      assert.equal(await password.getAttribute("type"), "password");
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
      await page.getByRole("button", { name: "Hesap bağlantısı", exact: true }).first().click();
      assert.equal(await password.getAttribute("type"), "password");
      assert.equal(await password.inputValue(), "");
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
    },
  );
  await check("Sidebar shows the exact running package version", async () => {
    assert.equal(await page.locator(".sidebar-bottom .preview-label > span").last().textContent(), version);
  });
  await check("Request information preserves HTTP and cooldown", async () => {
    await page.locator('.sidebar a[href="#settings"]').click();
    await page.getByRole("button", { name: "İncele", exact: true }).click();
    assert.equal(
      await page
        .getByRole("dialog")
        .getByText("429", { exact: true })
        .isVisible(),
      true,
    );
    const diagnostics = page.getByRole("dialog");
    await diagnostics.getByText("Okuma tanısı", { exact: true }).waitFor();
    await diagnostics.getByText("Takipçiler · Otomatik okuma duraklatıldı", { exact: true }).waitFor();
    await diagnostics.getByText("Instagram bu okuma yolunu reddetti. Uç nokta değişmiş olabilir.", { exact: true }).waitFor();
    await diagnostics.getByText(/^HTTP 404 · /).waitFor();
    await page.getByRole("button", { name: "Kapat", exact: true }).click();
  });
  await check("No horizontal overflow on six desktop routes", async () => {
    await page.setViewportSize({ width: 1100, height: 800 });
    for (const route of [
      "overview",
      "people",
      "lists",
      "timeline",
      "settings",
      "notifications",
    ]) {
      await page.locator(`.sidebar nav a[href="#${route}"]`).click();
      assert.equal(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
        true,
        route,
      );
    }
  });
  await check("English and Turkish cover six routes, preserve user text, and persist after restart", async () => {
    await page.locator('.sidebar nav a[href="#settings"]').click();
    const before = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
    const beforeLogs=await readHistory(page,'notifications',before);
    await page.locator('#app-language').selectOption('en');
    await page.waitForFunction(() => document.documentElement.lang === 'en');
    await page.getByRole('heading', { name: 'Small settings, a smoother experience.', exact: true }).waitFor();
    const after = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
    assert.deepEqual(after.gate, before.gate);
    assert.deepEqual(after.people, before.people);
    assert.deepEqual(after.history, before.history);
    assert.deepEqual(after.notificationRules, before.notificationRules);
    assert.deepEqual(await readHistory(page,'notifications',after), beforeLogs);
    assert.equal(after.notificationTypes.followers, 'Follower count');
    assert.equal(await page.evaluate(() => window.TrackerI18n.formats('en').number.format(1200)), '1,200');
    const note = 'Özet · Kaydet · İstanbul <img src=x onerror=alert(1)>';
    await page.evaluate(note => window.TrackerDesktop.command('save_person', { id: '200', note }), note);
    await page.locator('.sidebar nav a[href="#people"]').click();
    await page.getByRole('button', { name: 'Open QA qa.deniz', exact: true }).click();
    assert.equal(await page.locator('#person-note').inputValue(), note);
    await page.getByRole('dialog').getByRole('button', { name: 'Close', exact: true }).click();
    const error = await page.evaluate(() => window.TrackerDesktop.command('refresh', {}));
    assert.equal(error.error.message, 'Instagram connectivity is disabled in this isolated test window.');
    const detail=await page.evaluate(()=>window.TrackerDesktop.command('profile_detail',{accountId:'100',id:'202'}));
    assert.equal(detail.ok,false);assert.equal(detail.error.code,'offline');assert.equal(detail.state.profileDetail,null);
    const detailRejected=await page.evaluate(async()=>{try {
      await window.TrackerDesktop.command('profile_detail',{accountId:'100',id:'202',url:'https://example.com/'});return false;
    }catch{return true;}});
    assert.equal(detailRejected,true);
    const removed=await page.evaluate(async()=>{try {await window.TrackerDesktop.command('check_threads',{accountId:'100',id:'202',url:'https://www.threads.com/@qa.202'});return false;}catch{return true;}});
    assert.equal(removed,true);

    await page.setViewportSize({ width: 1100, height: 850 });
    for (const route of ['overview', 'people', 'lists', 'timeline', 'settings', 'notifications']) {
      await page.locator(`.sidebar nav a[href="#${route}"]`).click();
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, route);
    }
    await page.locator('.notification-log-row[data-id="qa-multi-change"]').click();
    await page.getByRole('heading', { name: 'What changed in this check?', exact: true }).waitFor();
    assert.match(await page.getByRole('dialog').innerText(), /Follower count/);
    await page.getByRole('dialog').getByRole('button', { name: 'Close', exact: true }).click();
    await page.locator('.sidebar nav a[href="#settings"]').click();
    await page.screenshot({ path: path.join(output, 'settings-en.png'), fullPage: true });
    await desktop.close(); desktop = null; await launch('en');
    assert.equal(await page.evaluate(() => document.documentElement.lang), 'en');
    const restored = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
    assert.equal(restored.people.find(p => p.id === '200').note, note);
    assert.equal(restored.gate.attempts, before.gate.attempts);
    await page.locator('.sidebar nav a[href="#settings"]').click();
    await page.locator('#app-language').selectOption('tr');
    await page.waitForFunction(() => document.documentElement.lang === 'tr');
    await page.getByRole('heading', { name: 'Küçük ayarlar, daha rahat bir deneyim.', exact: true }).waitFor();
  });
  await check("Spanish and Brazilian Portuguese persist, preserve evidence and render all routes", async () => {
    const before = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
    for (const language of ['es', 'pt-BR']) {
      await page.locator('.sidebar nav a[href="#settings"]').click();
      await page.locator('#app-language').selectOption(language);
      await page.waitForFunction(value => document.documentElement.lang === value, language);
      for (const route of ['overview', 'people', 'lists', 'timeline', 'notifications', 'settings']) {
        await page.locator(`.sidebar nav a[href="#${route}"]`).click();
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, `${language}/${route}`);
      }
      await page.screenshot({path:path.join(output, `settings-${language}.png`),fullPage:true});
      await desktop.close(); desktop = null;
      desktop = await electron.launch({executablePath,args:[`--tracker-test-home=${home}`],env});
      page = await desktop.firstWindow();
      page.on('pageerror', e => errors.push(e.message));
      page.on('request', r => {if (!r.url().startsWith('file:') && !r.url().startsWith('data:')) requests.push(r.url());});
      await page.waitForFunction(value => document.documentElement.lang === value && !!document.querySelector('.sidebar'), language);
      const after = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
      assert.equal(after.settings.language, language);
      assert.deepEqual(after.gate, before.gate);
      assert.deepEqual(after.people, before.people);
      assert.deepEqual(after.account, before.account);
      await page.locator('.sidebar nav a[href="#settings"]').click();
    }
    await page.locator('#app-language').selectOption('tr');
    await page.waitForFunction(() => document.documentElement.lang === 'tr');
  });
  await check("All local renderer assets and no script errors", async () => {
    assert.deepEqual(errors, []);
    assert.deepEqual(requests, []);
  });
  await page.setViewportSize({ width: 1440, height: 960 });
  await page.locator('.sidebar nav a[href="#overview"]').click();
  await page.screenshot({
    path: path.join(output, "overview.png"),
    fullPage: true,
  });
  await check(
    "Explicit local deletion removes records while retaining restriction history",
    async () => {
      await page.locator('.sidebar nav a[href="#settings"]').click();
      await page
        .getByRole("button", { name: "Yerel verileri sil", exact: true })
        .click();
      await page
        .getByRole("dialog")
        .getByRole("button", { name: "Yerel verileri sil", exact: true })
        .click();
      await page
        .getByText("Yerel kayıtlar silindi.", { exact: true })
        .waitFor();
      const state = await page.evaluate(() =>
        window.TrackerDesktop.command("state"),
      );
      assert.equal(state.state.account, null);
      assert.equal(state.state.people.length, 0);
      assert.equal(state.state.gate.status, 429);
      assert.equal(state.state.gate.attempts, 2);
      await page.locator('.sidebar nav a[href="#overview"]').click();
      await page.screenshot({
        path: path.join(output, "empty-overview.png"),
        fullPage: true,
      });
    },
  );
  await check("First-run overview opens the full ZIP dashboard without signing in", async () => {
    const archivePath = path.join(home, 'test-instagram-export.zip');
    const start = page.locator('.overview-start');
    await page.getByRole('heading',{name:'Nasıl başlamak istersin?',exact:true}).waitFor();
    assert.equal(await page.locator('.overview-monitor,.stat-card,.operation-panel').count(),0);
    await start.getByRole('button', {name:'Hesabını bağla'}).click();
    await page.locator('#login-form').waitFor();
    await page.keyboard.press('Escape');
    await start.getByRole('button', {name:'Instagram ZIP dosyası nasıl alınır?'}).click();
    const guide = page.locator('.archive-dashboard-guide');
    await guide.waitFor();
    assert.equal(await guide.evaluate(element => element.open), true);
    await page.locator('.sidebar nav a[href="#overview"]').click();
    const before = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
    assert.equal(before.connected, false);
    await desktop.evaluate(({dialog}, filename) => {
      const original = dialog.showOpenDialog;
      let opened = 0;
      dialog.showOpenDialog = () => {
        if (++opened === 1) return Promise.resolve({canceled:true,filePaths:[]});
        dialog.showOpenDialog = original;
        return Promise.resolve({canceled:false,filePaths:[filename]});
      };
    }, archivePath);
    const zip = start.getByRole('button', {name:'Instagram ZIP’ini aç'});
    await zip.click();
    await page.locator('#archive-dashboard-root').waitFor();
    assert.equal(await page.locator('.archive-dashboard-overview').isVisible(), false);
    await page.locator('.sidebar nav a[href="#overview"]').click();
    await zip.click();
    const archive = page.locator('#archive-dashboard-root');
    await archive.getByText('Listeler, ZIP hazırlanırken dışa aktarılan kayıtları gösterir.').waitFor();
    await archive.locator('.archive-dashboard-overview').getByRole('button', {name:/Takipçiler/}).waitFor();
    await archive.getByRole('link', {name:'@common',exact:true}).waitFor();
    await archive.getByRole('button', {name:'İki ZIP karşılaştır'}).waitFor();
    const after = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
    assert.equal(after.connected, false);
    assert.deepEqual(after.gate, before.gate);
    assert.deepEqual(requests, []);
  });
  await check("Dropping a local ZIP uses the same archive reader and distinguishes empty, missing and unreadable data", async () => {
    const dropped = path.join(home, 'instagram-qa.owner-2024-03-02-A1b2C3d4.zip');
    execFileSync(python, ['-c', [
      'import sys, zipfile',
      'with zipfile.ZipFile(sys.argv[1], "w") as archive:',
      '    archive.writestr("connections/followers_and_following/followers_1.json", "[]")',
      '    archive.writestr("your_instagram_activity/messages/no-data.txt", "No data")',
    ].join('\n'), dropped]);
    const archive = page.locator('#archive-dashboard-root');
    const zone = archive.getByText('ZIP dosyasını buraya bırak', {exact:true});
    const coverage = archive.locator('.archive-dashboard-coverage');
    await zone.scrollIntoViewIfNeeded();
    const bounds = await zone.boundingBox();
    const cdp = await page.context().newCDPSession(page);
    const dispatch = async filename => {
      const location = {x:bounds.x + bounds.width / 2,y:bounds.y + bounds.height / 2};
      const data = {items:[],files:[filename],dragOperationsMask:1};
      for (const type of ['dragEnter','dragOver','drop'])
        await cdp.send('Input.dispatchDragEvent',{type,...location,data});
    };
    try {
      await dispatch(dropped);
      await coverage.getByText('ZIP’te kayıt yok',{exact:true}).waitFor();
      assert.deepEqual(await coverage.locator('li').allTextContents(), ['Takipçiler0', 'Takip edilenBu ZIP’te yok', 'MesajlarZIP’te kayıt yok']);
      assert.match(await archive.locator('.archive-dashboard-file-info').innerText(), /Dosya adındaki tarih.*2024/);
      const broken = path.join(home,'broken-list.zip');
      execFileSync(python, ['-c', 'import sys, zipfile; z=zipfile.ZipFile(sys.argv[1],"w"); z.writestr("connections/followers_and_following/followers_1.json","bad"); z.close()', broken]);
      await dispatch(broken);
      await coverage.getByText('Okunamadı',{exact:true}).waitFor();
      assert.deepEqual(await coverage.locator('li').allTextContents(), ['TakipçilerOkunamadı', 'Takip edilenBu ZIP’te yok', 'MesajlarBu ZIP’te yok']);
    } finally { await cdp.detach(); }
  });
  await check("Portable history opens Android-compatible backups without modifying local records", async () => {
    const password = "örnek parola 🌿 123";
    const fixture = process.env.TRACKER_PORTABLE_QA_OUTPUT || path.join(home, 'portable-fixture.trackerhistory');
    if (!process.env.TRACKER_PORTABLE_QA_OUTPUT) {
      await writeFile(fixture, Buffer.from('VFJLQkFLMDGjHOYEO+QlCXVL29tcSYCA0Z61nyYOML2oc8uTmBK8SOGzhBXF2Gl8p/MGzVZRFTcKMo0PYv260emJ1u1TCwJCpO4ZFttS99QXVKj9c3DDZjgnIW2r3fmdBhga1387H8XnKvBeIYqrxR6czzTo78736klr138a3kMR3oeUI0YVjYwS+MP4jRcUAbqGm/LsoJqmvtBhlLcrNaRquSK1YaCDxsW7AiGqadlgK0Q0Y8R4ITTC0xb6be68Z+f5EG+qvfV5AIUxa9J3iMfCOBauDk5VMC0se/8YoAQw9T/CMpbbhQcGyrsNdUVCCO/QsuRPlPtY9Kfynn72H+JaMqnaAZTIEEUQJeGYT1c3iSNICr682U2a9/nVAZ46OmOn+xJl','base64'));
    }
    const before = (await page.evaluate(() => window.TrackerDesktop.command("state"))).state;
      await desktop.evaluate(({dialog}, filename) => {
        const original = dialog.showOpenDialog;
        dialog.showOpenDialog = () => {
          dialog.showOpenDialog = original;
          return Promise.resolve({canceled:false,filePaths:[filename]});
        };
      }, fixture);
      await page.locator('.sidebar nav a[href="#settings"]').click();
      const restore = page.getByRole('group', {name:'Windows yedeği',exact:true});
      const inspect = page.getByRole('group', {name:'Taşınabilir geçmiş',exact:true});
      assert.match(await restore.innerText(), /aynı Windows kullanıcısıyla.*Geri yükleme.*değiştirir/is);
      assert.match(await inspect.innerText(), /Parolayla Windows veya Android’de.*ayrı bir arşivde/s);
      await inspect.getByRole('button', {name:'Taşınabilir yedeği aç',exact:true}).click();
      await page.locator('#portable-password').fill(password);
      await page.locator('#portable-backup-form button').click();
      const portable = page.getByRole('dialog', {name:/Taşınabilir geçmiş.*qa.owner/});
      const expectedNote = process.env.TRACKER_PORTABLE_QA_OUTPUT
        ? "ş".repeat(1100) + "a".repeat(899) + "🌿"
        : "Türkçe 🌿";
      await portable.getByText(expectedNote,{exact:true}).waitFor();
      await page.keyboard.press('Escape');
    const after = (await page.evaluate(() => window.TrackerDesktop.command("state"))).state;
    assert.deepEqual(after.account, before.account);
    assert.deepEqual(after.people, before.people);
    assert.deepEqual(after.gate, before.gate);
  });
  await writeFile(
    path.join(output, "checks.json"),
    JSON.stringify(
      {
        checks,
        errors,
        externalRequests: requests.length,
        isolatedSyntheticHome: home,
        liveInstagram: false,
      },
      null,
      2,
    ),
  );
  console.log(
    `${checks.length} packaged desktop checks passed; no Instagram requests.`,
  );
} catch (error) {
  if (page) {
    await page.screenshot({path:path.join(output,"failure.png"),fullPage:true}).catch(()=>{});
    await writeFile(path.join(output,"failure.json"),JSON.stringify({checks,errors,requests,
      text:await page.locator("body").innerText().catch(()=>"Window unavailable"),
      home,error:String(error)},null,2));
  }
  throw error;
} finally {
  if (desktop) await desktop.close();
}
