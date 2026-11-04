import {readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const policy=JSON.parse(await readFile(path.join(root,'design/search-cache.json'),'utf8'));
if(JSON.stringify(Object.keys(policy))!==JSON.stringify(['ttlMilliseconds','maxQueries','maxResults']) ||
  Object.values(policy).some(value=>!Number.isSafeInteger(value)||value<=0))throw Error('Invalid search policy');
const outputs={
  'app/src/main/java/dev/kaankaya/instagramtracker/SearchCachePolicy.kt':`// Generated from design/search-cache.json; run node interface/scripts/generate-search-policy.mjs.\npackage dev.kaankaya.instagramtracker\n\nobject SearchCachePolicy {\n    const val TTL_MS = ${policy.ttlMilliseconds}L\n    const val MAX_QUERIES = ${policy.maxQueries}\n    const val MAX_RESULTS = ${policy.maxResults}\n}\n`,
  'interface/desktop/backend/search_cache_policy.py':`# Generated from design/search-cache.json; run node interface/scripts/generate-search-policy.mjs.\nTTL_SECONDS = ${policy.ttlMilliseconds/1000}\nMAX_QUERIES = ${policy.maxQueries}\nMAX_RESULTS = ${policy.maxResults}\n`
};
const jsPath='interface/web/profile-search.js';
const js=await readFile(path.join(root,jsPath),'utf8');
const start='  // BEGIN GENERATED SEARCH POLICY',end='  // END GENERATED SEARCH POLICY';
if(!js.includes(start)||!js.includes(end))throw Error('Missing search policy markers');
outputs[jsPath]=js.slice(0,js.indexOf(start))+`${start}\n  const CACHE_POLICY=Object.freeze(${JSON.stringify(policy)});\n${end}`+js.slice(js.indexOf(end)+end.length);
for(const [file,value]of Object.entries(outputs)) {
  if(process.argv.includes('--check')) {if(await readFile(path.join(root,file),'utf8')!==value)throw Error(`Search policy drift: ${file}`);}
  else await writeFile(path.join(root,file),value);
}
console.log('Android / Windows search cache policy matches its shared source.');
