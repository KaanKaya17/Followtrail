import {readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const icons=JSON.parse(await readFile(path.join(root,'design/profile-link-icons.json'),'utf8'));
const targets=new Map();
for(const [name,d] of Object.entries(icons)) {
 if(!/^[a-z]+$/.test(name)||!/^[MmLlHhVvQqCcAaZz0-9 .,-]+$/.test(d))throw Error('Invalid local icon');
 targets.set(`app/src/main/res/drawable/profile_brand_${name}.xml`,`<!-- Generated from design/profile-link-icons.json. -->\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:pathData="${d}" android:fillColor="@android:color/transparent" android:strokeColor="#242237" android:strokeWidth="1.7" android:strokeLineCap="round" android:strokeLineJoin="round" /></vector>\n`);
}
targets.set('interface/web/link-icons.js',`// Generated from design/profile-link-icons.json.\nwindow.TrackerLinkIcon=name=>'<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="'+(${JSON.stringify(icons)}[name] || ${JSON.stringify(icons.globe)})+'"/></svg>';\n`);
targets.set('app/src/main/java/dev/kaankaya/instagramtracker/ProfileBrandIcons.kt',`// Generated from design/profile-link-icons.json.\npackage dev.kaankaya.instagramtracker\n\nobject ProfileBrandIcons {\n fun drawable(name: String): Int = when(name) {\n${Object.keys(icons).map(name=>`  "${name}" -> R.drawable.profile_brand_${name}`).join('\n')}\n  else -> R.drawable.profile_brand_globe\n }\n}\n`);
for(const [target,source] of targets) {
 const file=path.join(root,target);
 if(process.argv.includes('--check')){if(await readFile(file,'utf8')!==source)throw Error('Stale link asset: '+target);}
 else await writeFile(file,source);
}
console.log('Local link icons match on Android and Windows.');
