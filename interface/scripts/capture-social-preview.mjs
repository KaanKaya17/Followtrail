// GitHub social preview (1280x640) from the app icon and the synthetic English archive screenshot. Local files only.
import {chromium} from 'playwright-core';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const executable = process.env.TRACKER_DOCS_BROWSER || path.join(process.env['ProgramFiles(x86)'] || 'C:/Program Files (x86)', 'Microsoft/Edge/Application/msedge.exe');
const data = async file => 'data:image/png;base64,' + (await readFile(path.join(root, file))).toString('base64');
const icon = await data('interface/.cache/app-icon.png');
const shot = await data('docs/wiki/assets/en/zip-arsiv.png');
const html = `<!doctype html><meta charset="utf-8"><style>
  *{box-sizing:border-box;margin:0}
  body{width:1280px;height:640px;overflow:hidden;font-family:"Segoe UI",system-ui,sans-serif;background:#f7f5f0;color:#1f1d2b;display:grid;grid-template-columns:520px 1fr}
  .copy{padding:72px 0 64px 72px;display:flex;flex-direction:column;gap:22px}
  .brand{display:flex;align-items:center;gap:18px}
  .brand img{width:76px;height:76px;border-radius:20px}
  .brand strong{font-size:54px;letter-spacing:-1.5px}
  h1{font-size:34px;line-height:1.2;font-weight:650;letter-spacing:-.5px}
  p{font-size:21px;line-height:1.5;color:#5e5a6e}
  .tags{margin-top:auto;display:flex;gap:10px;flex-wrap:wrap}
  .tags span{padding:8px 14px;border-radius:999px;background:#ece8fb;color:#5b3fd6;font-size:16px;font-weight:600}
  .shot{position:relative;overflow:hidden}
  .shot img{position:absolute;left:40px;top:64px;width:1100px;border-radius:18px;box-shadow:0 24px 60px rgba(40,30,90,.18);border:1px solid #e4e0ee}
</style><div class="copy"><div class="brand"><img src="${icon}" alt=""><strong>Followtrail</strong></div>
<h1>Your Instagram follow history, on your own computer.</h1>
<p>Compare data exports, see who came and who left, and keep encrypted local records.</p>
<div class="tags"><span>Open source</span><span>Local first</span><span>Windows</span></div></div>
<div class="shot"><img src="${shot}" alt=""></div>`;
const browser = await chromium.launch({executablePath: executable, headless: true});
try {
  const page = await browser.newPage({viewport: {width: 1280, height: 640}});
  await page.route(/^https?:/, route => route.abort());
  await page.setContent(html);
  await page.screenshot({path: path.join(root, 'docs/assets/social-preview.png')});
} finally {
  await browser.close();
}
console.log('Social preview written to docs/assets/social-preview.png');
