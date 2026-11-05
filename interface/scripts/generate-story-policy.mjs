import {readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const p=JSON.parse(await readFile(path.join(root,'design/story-policy.json'),'utf8'));
const capabilities=JSON.parse(await readFile(path.join(root,'design/story-capabilities.json'),'utf8'));
if(!Array.isArray(capabilities)||capabilities.length>32||capabilities.some(v=>!v||typeof v!=='object'||Object.keys(v).sort().join(',')!=='name,value'||
 [v.name,v.value].some(s=>typeof s!=='string'||s.length>1024||!/^[A-Za-z0-9_., -]+$/.test(s))))throw Error('Invalid story capabilities');
const serialized=JSON.stringify(JSON.stringify(capabilities));
const keys=['maxItems','maxMediaBytes','maxArchiveBytes','maxIndexBytes','maxPixels','maxSide'];
if(JSON.stringify(Object.keys(p))!==JSON.stringify(keys)||Object.values(p).some(v=>!Number.isSafeInteger(v)||v<=0)||p.maxMediaBytes>p.maxArchiveBytes||p.maxMediaBytes>2147483647-28||p.maxIndexBytes>2097152)throw Error('Invalid story policy');
const names=['MAX_ITEMS','MAX_MEDIA','MAX_BYTES','MAX_INDEX','MAX_PIXELS','MAX_SIDE'];
const outputs={
 'app/src/main/java/dev/kaankaya/instagramtracker/StoryLimits.kt':`// Generated from design/story-policy.json and story-capabilities.json; run node interface/scripts/generate-story-policy.mjs.\npackage dev.kaankaya.instagramtracker\n\nobject StoryLimits {\n${keys.map((k,i)=>`    const val ${names[i]} = ${p[k]}`).join('\n')}\n    const val CAPABILITIES_JSON = ${serialized}\n}\n`,
 'interface/desktop/backend/story_limits.py':`# Generated from design/story-policy.json and story-capabilities.json; run node interface/scripts/generate-story-policy.mjs.\n${keys.map((k,i)=>`${names[i]} = ${p[k]}`).join('\n')}\nCAPABILITIES_JSON = ${serialized}\n`
};
for(const [file,value]of Object.entries(outputs)) {
 const target=path.join(root,file);
 if(process.argv.includes('--check')) {if(await readFile(target,'utf8')!==value)throw Error(`Story policy drift: ${file}`);}
 else await writeFile(target,value);
}
console.log('Android / Windows story archive bounds match their shared source.');
