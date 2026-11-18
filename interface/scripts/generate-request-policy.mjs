import {readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const p=JSON.parse(await readFile(path.join(root,'design/request-policy.json'),'utf8'));
const keys=['minimumSpacingMilliseconds','maximumSpacingMilliseconds','accountHourly','accountDaily','deviceHourly','deviceDaily','automaticProfileBudgetPercent','profileBasisFreshnessSeconds','defaultIntervalMinutes','intervalMinutes','mediaMinimumSpacingMilliseconds','mediaMaximumSpacingMilliseconds','mediaDeviceHourly','mediaDeviceDaily'];
const counts=keys.filter(k=>k!=='intervalMinutes');
if(JSON.stringify(Object.keys(p))!==JSON.stringify(keys) || counts.some(k=>!Number.isSafeInteger(p[k])||p[k]<=0) ||
 !Array.isArray(p.intervalMinutes)||!p.intervalMinutes.includes(p.defaultIntervalMinutes)||
 p.intervalMinutes.some((v,i)=>!Number.isSafeInteger(v)||v<1||v>1440||i>0&&v<=p.intervalMinutes[i-1])||
 p.automaticProfileBudgetPercent>100||p.profileBasisFreshnessSeconds>300||
 p.minimumSpacingMilliseconds>p.maximumSpacingMilliseconds||p.minimumSpacingMilliseconds%1000||p.maximumSpacingMilliseconds%1000||
 p.accountHourly>p.deviceHourly||p.accountDaily>p.deviceDaily||p.deviceHourly>p.deviceDaily||p.accountHourly>p.accountDaily||
 p.mediaMinimumSpacingMilliseconds>p.mediaMaximumSpacingMilliseconds||p.mediaMinimumSpacingMilliseconds%1000||p.mediaMaximumSpacingMilliseconds%1000||
 p.mediaMaximumSpacingMilliseconds>p.minimumSpacingMilliseconds||p.mediaDeviceHourly>p.mediaDeviceDaily)throw Error('Invalid shared request policy');
const outputs={
 'app/src/main/java/dev/kaankaya/instagramtracker/RequestDefaults.kt':`// Generated from design/request-policy.json; run node interface/scripts/generate-request-policy.mjs.\npackage dev.kaankaya.instagramtracker\n\nobject RequestDefaults {\n    const val MIN_SPACING_MS = ${p.minimumSpacingMilliseconds}L\n    const val MAX_SPACING_MS = ${p.maximumSpacingMilliseconds}L\n    const val ACCOUNT_HOURLY = ${p.accountHourly}\n    const val ACCOUNT_DAILY = ${p.accountDaily}\n    const val DEVICE_HOURLY = ${p.deviceHourly}\n    const val DEVICE_DAILY = ${p.deviceDaily}\n    const val AUTO_PROFILE_PERCENT = ${p.automaticProfileBudgetPercent}\n    const val PROFILE_BASIS_MS = ${p.profileBasisFreshnessSeconds*1000}L\n    const val DEFAULT_INTERVAL_MINUTES = ${p.defaultIntervalMinutes}\n    val INTERVAL_MINUTES = listOf(${p.intervalMinutes.join(', ')})\n    const val MEDIA_MIN_SPACING_MS = ${p.mediaMinimumSpacingMilliseconds}L\n    const val MEDIA_MAX_SPACING_MS = ${p.mediaMaximumSpacingMilliseconds}L\n    const val MEDIA_DEVICE_HOURLY = ${p.mediaDeviceHourly}\n    const val MEDIA_DEVICE_DAILY = ${p.mediaDeviceDaily}\n}\n`,
 'interface/desktop/backend/request_defaults.py':`# Generated from design/request-policy.json; run node interface/scripts/generate-request-policy.mjs.\nREQUEST_SPACING = (${p.minimumSpacingMilliseconds/1000}, ${p.maximumSpacingMilliseconds/1000})\nACCOUNT_BUDGET = (${p.accountHourly}, ${p.accountDaily})\nDEVICE_BUDGET = (${p.deviceHourly}, ${p.deviceDaily})\nAUTO_PROFILE_PERCENT = ${p.automaticProfileBudgetPercent}\nPROFILE_BASIS_SECONDS = ${p.profileBasisFreshnessSeconds}\nDEFAULT_INTERVAL_MINUTES = ${p.defaultIntervalMinutes}\nCONTROL_INTERVALS = (${p.intervalMinutes.join(', ')})\nMEDIA_SPACING = (${p.mediaMinimumSpacingMilliseconds/1000}, ${p.mediaMaximumSpacingMilliseconds/1000})\nMEDIA_BUDGET = (${p.mediaDeviceHourly}, ${p.mediaDeviceDaily})\n`
};
for(const [file,content]of Object.entries(outputs)) {
 const target=path.join(root,file);
 if(process.argv.includes('--check')) {if(await readFile(target,'utf8')!==content)throw Error(`Request policy drift: ${file}`);}
 else await writeFile(target,content);
}
console.log('Android / Windows request policy matches its shared source.');
