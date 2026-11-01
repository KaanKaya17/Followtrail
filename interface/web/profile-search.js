(function(root) {
  'use strict';
  const fold=value=>String(value ?? '').normalize('NFKD').replace(/\p{M}/gu,'').toLowerCase().replace(/ı/g,'i').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
  function username(input) {
    let value=String(input ?? '').trim();
    if(value.startsWith('https://')) {
      try {
        const url=new URL(value);
        if(!['instagram.com','www.instagram.com'].includes(url.hostname) || url.username || url.password || url.port || url.pathname.includes('%'))return null;
        value=url.pathname.replace(/^\/+|\/+$/g,'');
      } catch {return null;}
    } else value=value.replace(/^@/,'');
    return /^[A-Za-z0-9._]{1,30}$/.test(value) && !['accounts','explore','reels','reel','stories','direct','p'].includes(value.toLowerCase()) ? value.toLowerCase() : null;
  }
  function matches(text,query) {
    const q=fold(username(query) || query), source=fold(text);
    return !q || q.split(/\s+/).every(token=>source.includes(token));
  }
  const api=Object.freeze({fold,username,matches});
  root.TrackerProfileSearch=api;
  if(typeof module==='object')module.exports=api;
})(typeof window==='object'?window:globalThis);
