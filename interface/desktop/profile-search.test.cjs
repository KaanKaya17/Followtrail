const test=require('node:test'),assert=require('node:assert/strict');
const search=require('../web/profile-search.js');
test('partial saved profile search handles Turkish letters, separated words, @names and shared links',()=>{
 const text='İpek Çağrı Kaya ipek.kaya Yakın Arkadaşlar';
 for(const query of ['ipe','@ipek','IPeK','cagri ka','kaya ipek','yakin','https://instagram.com/ipek.kaya/?igsh=abc'])assert.equal(search.matches(text,query),true,query);
 assert.equal(search.matches(text,'deniz'),false);
 assert.equal(search.matches(text,''),true);
});
test('profile input accepts only an exact Instagram username or profile URL',()=>{
 for(const input of ['@Ipek.Kaya','https://instagram.com/Ipek.Kaya/?igsh=abc'])assert.equal(search.username(input),'ipek.kaya');
 for(const input of ['İpek Kaya','https://instagram.com.evil/a/','https://x@instagram.com/a/','https://instagram.com/p/','https://instagram.com/a/b/'])assert.equal(search.username(input),null);
});
