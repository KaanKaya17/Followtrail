const test=require('node:test'),assert=require('node:assert/strict');
const links=require('../web/profile-links.js');
test('profile websites use exact brands, preserve destinations and reject active content or credentials',()=>{
 assert.deepEqual(links.describe('https://www.youtube.com/@demo?view=1'),{url:'https://www.youtube.com/@demo?view=1',host:'youtube.com',name:'YouTube',icon:'youtube'});
 assert.equal(links.describe('https://youtube.com.evil.example/a').icon,'globe');
 for(const url of ['javascript:alert(1)','file:///C:/secret','https://u:p@example.com/','https://example.com/\nq','https://example.com\\a','https://example.com:999999/'])assert.equal(links.webUrl(url),null);
 assert.equal(links.describe('https://portfolio.example/projects?q=1').name,'portfolio.example');
});
test('explicit Threads link takes precedence over the Instagram username, website links deduplicate',()=>{
 const view=links.links({username:'ig.name',url:'https://youtube.com/@demo',links:[{url:'https://youtube.com/@demo'},{url:'https://threads.net/@other.name'}]});
 assert.equal(view.instagram,'https://www.instagram.com/ig.name/');assert.equal(view.threads,'https://threads.net/@other.name');
 assert.equal(view.websites.length,1);
 assert.equal(links.links({username:'ig.name'}).threads,null);
 assert.equal(links.links({username:'ig.name',url:'https://threads.com/@linked?source=profile'}).threads,'https://threads.com/@linked?source=profile');
 assert.equal(links.threadsName('https://threads.com.evil.example/@name'),null);
 assert.equal(links.threadsName('https://threads.com/@name/post/abc'),null);
});
