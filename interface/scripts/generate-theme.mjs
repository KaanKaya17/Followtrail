import {readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const theme=JSON.parse(await readFile(path.join(root,'design/theme.json'),'utf8'));
const palettes={light:theme.colors,dark:theme.dark,oled:theme.oled};
const keys=Object.keys(theme.colors);
for(const colors of Object.values(palettes)) {
  if(JSON.stringify(Object.keys(colors))!==JSON.stringify(keys))throw Error('Theme color contracts differ');
  if(Object.values(colors).some(value=>!/^#[a-f0-9]{6}$/.test(value)))throw Error('Invalid theme color');
}
for(const [key,value] of Object.entries(theme.radii))
  if(!['card','button','control'].includes(key)||!Number.isInteger(value)||value<0||value>48)throw Error('Invalid radius');
const kebab=name=>name.replace(/[A-Z]/g,letter=>`-${letter.toLowerCase()}`);
const declarations=colors=>Object.entries(colors).map(([key,value])=>`  --${kebab(key)}: ${value};`).join('\n');
const css=`/* Generated from design/theme.json. Run npm run theme from interface/. */
:root { color-scheme: light;
${declarations(theme.colors)}
${Object.entries(theme.radii).map(([key,value])=>`  --radius-${key}: ${value}px;`).join('\n')}
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme]), :root[data-theme="system"] { color-scheme: dark;
${declarations(theme.dark)}
  }
}
:root[data-theme="dark"] { color-scheme: dark;
${declarations(theme.dark)}
}
:root[data-theme="oled"] { color-scheme: dark;
${declarations(theme.oled)}
}
`;
const kotlin=`package dev.kaankaya.instagramtracker

// Generated from design/theme.json by interface/scripts/generate-theme.mjs.
internal object UiPalette {
    data class Colors(${keys.map(key=>`val ${key}: Int`).join(', ')})
${Object.entries(palettes).map(([name,colors])=>`    private val ${name} = Colors(\n${Object.entries(colors).map(([key,value])=>`        ${key} = 0xFF${value.slice(1).toUpperCase()}.toInt()`).join(',\n')}\n    )`).join('\n')}
    @Volatile private var current = light
    fun select(mode: String) { current = when (mode) { "dark" -> dark; "oled" -> oled; else -> light } }
${keys.map(key=>`    val ${key} get() = current.${key}`).join('\n')}
${Object.entries(theme.radii).map(([key,value])=>`    const val ${key}Radius = ${value}`).join('\n')}
}
`;
// XML contains only native framework style inputs; programmatic views use UiPalette.
const xmlKeys=['background','surface','ink','muted','violet','line'];
const xml=`<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from design/theme.json. -->\n<resources>\n${Object.entries(palettes).map(([mode,colors])=>xmlKeys.map(key=>`    <color name="theme_${mode}_${key}">${colors[key]}</color>`).join('\n')).join('\n')}\n</resources>\n`;
const files=[['interface/web/theme.css',css],['app/src/main/java/dev/kaankaya/instagramtracker/UiPalette.kt',kotlin],['app/src/main/res/values/theme_colors.xml',xml]];
files.push(['interface/desktop/theme.cjs','// Generated from design/theme.json.\nmodule.exports = Object.freeze('+JSON.stringify(Object.fromEntries(Object.entries(palettes).map(([mode,colors])=>[mode,colors.background])))+');\n']);
for(const [name,contents] of files) {
  const file=path.join(root,name);
  if(process.argv.includes('--check')) {
    if((await readFile(file,'utf8')).replace(/\r\n/g,'\n')!==contents)throw Error(`Generated theme is stale: ${file}`);
  } else await writeFile(file,contents);
}
console.log('Android / web / desktop light, dark and OLED themes match their shared source.');
