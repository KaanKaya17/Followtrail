const test = require('node:test');
const assert = require('node:assert/strict');
const {diff,render} = require('../web/wayback.js');
test('Diff preserves old and new exact Unicode, whitespace and URL content',()=>{
  for(const [a,b] of [['İstanbul ☕\nhttps://a.test/x','Ankara ☕\nhttps://b.test/x'],['','empty'],['repeat repeat','repeat'],['<img src=x>','<script>x</script>']]){
    const parts=diff(a,b);assert.equal(parts.filter(p=>p.kind!=='add').map(p=>p.text).join(''),a);assert.equal(parts.filter(p=>p.kind!=='remove').map(p=>p.text).join(''),b);
  }
});
test('Long biographies use bounded fallback without losing content',()=>{
  const a='word '.repeat(4000),b='other '.repeat(4000);const p=diff(a,b);
  assert.equal(p.filter(x=>x.kind!=='add').map(x=>x.text).join(''),a);assert.equal(p.filter(x=>x.kind!=='remove').map(x=>x.text).join(''),b);
});
test('Timeline escapes hostile biography text and keeps deletion and insertion labels',()=>{
  const esc=s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
  const html=render({entries:[{at:1,bio:'old'},{at:2,bio:'<img src=x onerror=evil>'}],images:{}},{t:(s)=>s,date:String,esc});
  assert.ok(html.includes('<del>'));assert.ok(html.includes('<ins>'));assert.ok(!html.includes('<img src=x'));assert.ok(html.includes('aria-hidden'));
});
