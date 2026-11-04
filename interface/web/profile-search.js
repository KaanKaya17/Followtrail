(function(root) {
  'use strict';
  // BEGIN GENERATED SEARCH POLICY
  const CACHE_POLICY=Object.freeze({"ttlMilliseconds":60000,"maxQueries":6,"maxResults":20});
  // END GENERATED SEARCH POLICY
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
  function query(value) {
    const raw=String(value ?? '').trim();
    const q=raw.startsWith('@') || raw.startsWith('https://') ? username(raw) : raw;
    return q && q.length>=2 && q.length<=100 && !/[\x00-\x1f]/.test(q) ? q : null;
  }
  function createCache({now=()=>performance.now()}={}) {
    const entries=new Map();let owner=null,epoch=0;
    const clear=()=>{entries.clear();owner=null;epoch++;};
    return {
      bind(scope) {if(owner!==scope){clear();owner=scope;}return epoch;},
      clear,
      get(scope,q) {
        if(scope!==owner)return null;
        const item=entries.get(q),age=item?now()-item.at:0;
        if(!item || age<0 || age>=item.lifetime){entries.delete(q);return null;}
        return structuredClone(item.response);
      },
      put(scope,q,response,ticket) {
        if(owner!==scope || epoch!==ticket || !response?.ok)return;
        const minimal={ok:true,token:response.token,users:(response.users||[]).slice(0,CACHE_POLICY.maxResults).map(p=>({id:p.id,username:p.username,name:p.name}))};
        const lifetime=Math.min(CACHE_POLICY.ttlMilliseconds,Number.isFinite(response.remainingMs)?Math.max(0,response.remainingMs):CACHE_POLICY.ttlMilliseconds);
        entries.delete(q);entries.set(q,{response:minimal,at:now(),lifetime});
        while(entries.size>CACHE_POLICY.maxQueries)entries.delete(entries.keys().next().value);
      },
    };
  }
  function live({search,local,results,status,availability=()=>({}),cancelImages=()=>{},now=()=>performance.now(),cache=createCache({now}),owner='search',
    setTimer=setTimeout,clearTimer=clearTimeout}) {
    let closed=false,timer=null,inflight=null,value='',attempted=null,held=false,holdError=null;
    const ticket=cache.bind(owner);
    function clear() {if(timer!==null)clearTimer(timer);timer=null;}
    function arm(delay) {clear();timer=setTimer(run,delay);}
    async function run() {
      timer=null;
      const q=query(value);
      if(closed || !q || held || attempted===q)return;
      const cached=cache.get(owner,q);
      if(cached) {attempted=q;results(cached);status('ready');return;}
      if(inflight) {arm(250);return;}
      const state=availability();
      if(state.stop) {status('error',state.stop);return;}
      if(state.wait>0) {status('waiting',Math.ceil(state.wait/1000));arm(Math.min(state.wait,1000));return;}
      if(state.busy) {status('busy');arm(500);return;}
      inflight=q;attempted=q;status('loading');
      let response;
      try {response=await search(q);} catch {response={ok:false};}
      inflight=null;
      if(closed)return;
      if(response?.ok) {
        cache.put(owner,q,response,ticket);
      } else if(['limited','cooldown','budget','login','connection','session','verification'].includes(response?.error?.code)) {held=true;holdError=response.error;}
      if(q===query(value)) {
        attempted=q;
        if(response?.ok) {results(response);status('ready');}
        else status('error',response?.error);
      } else if(held)status('error',holdError);
      else arm(0);
    }
    return {
      input(raw) {
        if(closed)return;
        value=raw;attempted=null;clear();cancelImages();local(raw);
        const q=query(raw),saved=cache.get(owner,q);
        if(saved) {attempted=q;results(saved);status('ready');return;}
        if(held)status('error',holdError);
        else if(q)arm(650);
      },
      submit() {if(closed || inflight===query(value))return;held=false;attempted=null;arm(0);},
      close() {closed=true;clear();cancelImages();},
    };
  }
  const api=Object.freeze({fold,username,matches,query,live,createCache,cachePolicy:CACHE_POLICY});
  root.TrackerProfileSearch=api;
  if(typeof module==='object')module.exports=api;
})(typeof window==='object'?window:globalThis);
