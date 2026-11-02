(function(root){
 'use strict';
 function webUrl(value) {
  if(typeof value!=='string' || value.length>4096 || /[\u0000-\u0020\u007f\\]/.test(value))return null;
  try {const url=new URL(value);return ['https:','http:'].includes(url.protocol) && !url.username && !url.password && url.hostname.includes('.') ? url.href : null;}catch{return null;}
 }
 function describe(value) {
  const url=webUrl(value);if(!url)return null;
  const host=new URL(url).hostname.replace(/^www\./,'');
  const brands=[['youtube.com','YouTube','youtube'],['youtu.be','YouTube','youtube'],['instagram.com','Instagram','instagram'],
   ['threads.com','Threads','threads'],['threads.net','Threads','threads'],['facebook.com','Facebook','facebook'],
   ['x.com','X','x'],['twitter.com','X','x'],['linkedin.com','LinkedIn','linkedin'],['tiktok.com','TikTok','tiktok'],
   ['github.com','GitHub','github'],['linktr.ee','Linktree','linktree'],['spotify.com','Spotify','spotify'],
   ['t.me','Telegram','telegram'],['telegram.me','Telegram','telegram'],['wa.me','WhatsApp','whatsapp'],['whatsapp.com','WhatsApp','whatsapp']];
  const brand=brands.find(([domain])=>host===domain || host.endsWith('.'+domain));
  return {url,host,name:brand?.[1] || host,icon:brand?.[2] || 'globe'};
 }
 function threadsName(value) {
  const url=webUrl(value);if(!url)return null;const parsed=new URL(url);
  if(parsed.protocol!=='https:' || !['','443'].includes(parsed.port) || !['threads.com','www.threads.com','threads.net','www.threads.net'].includes(parsed.hostname))return null;
  return /^\/@([A-Za-z0-9._]{1,30})\/?$/.exec(parsed.pathname)?.[1] || null;
 }
 function links(profile) {
  const websites=[],seen=new Set();
  for(const raw of [...(Array.isArray(profile.links)?profile.links.slice(0,6):[]),{url:profile.url}]) {
   const item=describe(raw?.url);if(!item || seen.has(item.url))continue;
   seen.add(item.url);websites.push(item);
  }
  const linked=websites.find(item=>threadsName(item.url));
  return {websites:websites.filter(item=>item!==linked),instagram:/^[A-Za-z0-9._]{1,30}$/.test(profile.username || '') ? 'https://www.instagram.com/'+profile.username+'/' : null,
   threads:linked?.url || null};
 }
 root.TrackerProfileLinks=Object.freeze({webUrl,describe,threadsName,links});
 if(typeof module==='object')module.exports=root.TrackerProfileLinks;
})(typeof window==='object'?window:globalThis);
