// Render the production interface with synthetic data only. Never connects to a worker/account.
import {chromium} from 'playwright-core';
import {execFileSync} from 'node:child_process';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath,pathToFileURL} from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
import {memoryHistoryFixture} from './local-list-fixture.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const python=process.env.TRACKER_BUILD_PYTHON || 'python';
const executable=process.env.TRACKER_DOCS_BROWSER || path.join(process.env['ProgramFiles(x86)'] || 'C:/Program Files (x86)','Microsoft/Edge/Application/msedge.exe');
const history=await memoryHistoryFixture(python,JSON.parse(execFileSync(python,[path.join(root,'scripts/wiki-demo-state.py')],{encoding:'utf8'})));
const state=history.state;
assert.equal(state.connected,true);
assert.equal(state.gate.status,200);
const output=process.env.TRACKER_DOCS_OUTPUT || path.join(root,'docs/wiki/assets');
await mkdir(output,{recursive:true});
const browser=await chromium.launch({executablePath:executable,headless:true});
const errors=[],external=[];
try {
    const page=await browser.newPage({viewport:{width:1440,height:1000},locale:'tr-TR',timezoneId:'Europe/Istanbul'});
    await page.exposeFunction('qaHistory',(args,operation)=>history.request(args,operation));
    await page.clock.install({time:new Date('2026-11-02T15:00:00Z')});
    await page.route(/^https?:/,route=>{external.push(route.request().url());return route.abort();});
    page.on('pageerror',error=>errors.push(error.message));
    await page.addInitScript(({state})=>{
        window.TrackerDesktop={subscribe:()=>{},openLink:async()=>({ok:true}),command:async(operation,args={})=>{
            if(['history_page','history_detail','read_notification'].includes(operation))return window.qaHistory(args,operation);
            if(operation==='daily_stats') {
                const points=(state.history[args.id] || []).filter(p=>typeof p[args.metric]==='number').map(p=>({
                    bucket:Math.floor(p.observedAt/86400),day:Math.floor(p.observedAt/86400)*86400000,at:p.observedAt*1000,
                    first:p[args.metric],value:p[args.metric],minimum:p[args.metric],maximum:p[args.metric],observedDays:1,samples:1}));
                return {ok:true,daily:{...args,points,bucketDays:1,observedDays:points.length,samples:points.length,timezone:'UTC'}};
            }
            if(operation==='profile_detail') state.profileDetail={accountId:'100',profile:state.people.find(p=>p.id===args.id)};
            else if(!['state','cancel_list_images','list_avatar','search_avatar'].includes(operation))
                throw new Error('Documentation renderer does not implement '+operation);
            return {ok:true,state,avatar:null};
        }};
    },{state});
    const entry=pathToFileURL(path.join(root,'interface/web/desktop.html')).href;
    await page.goto(entry+'#overview');
    await page.locator('.stat-card').first().waitFor();
    await page.locator('#daily-chart-root .chart-point').first().waitFor();
    assert.equal((await page.locator('body').innerText()).includes('\uFFFD'),false);
    assert.equal(await page.getByText('Hesap bağlı değil',{exact:true}).count(),0);
    assert.equal(await page.locator('.pause-banner').count(),0);
    await page.screenshot({path:path.join(output,'ozet.png')});
    await page.goto(entry+'#people');
    await page.locator('[data-action="person"][data-id="200"]').first().click();
    await page.locator('#person-profile-info .profile-metrics').waitFor();
    await page.locator('[data-url="https://www.threads.com/@demo.deniz"]').waitFor();
    assert.equal((await page.locator('body').innerText()).includes('\uFFFD'),false);
    await page.screenshot({path:path.join(output,'profil-baglantilari.png')});
    await page.keyboard.press('Escape');
    await page.goto(entry+'#notifications');
    await page.locator('[data-action="notification-log"]').first().click();
    await page.locator('.change-log-details').waitFor();
    assert.equal(await page.getByText('Bildirim için seçili',{exact:true}).count(),3);
    assert.equal((await page.locator('body').innerText()).includes('\uFFFD'),false);
    await page.screenshot({path:path.join(output,'bildirimler.png')});
    assert.deepEqual(errors,[]);
    assert.deepEqual(external,[]);
    console.log('3 documentation screenshots captured; synthetic state, zero external requests, zero script errors.');
} finally {await browser.close();await history.close();}
