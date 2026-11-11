(function(root) {
  'use strict';
  function create({load,availability=()=>({}),now=Date.now,setTimer=setTimeout,clearTimer=clearTimeout}) {
    let current=null,timer=null,inflight=false,owner=null,generation=0;
    const cache=new Map();
    function clear() {if(timer!==null)clearTimer(timer);timer=null;}
    function schedule(delay) {clear();timer=setTimer(run,delay);}
    function fresh(profile) {return profile && Number.isFinite(profile.followers) && Number.isFinite(profile.following) &&
      Number.isFinite(profile.observedAt) && now()-profile.observedAt*1000>=0 && now()-profile.observedAt*1000<300000;}
    async function run() {
      timer=null;const view=current;if(!view)return;
      if(inflight) {schedule(250);return;}
      const cached=fresh(view.known) ? view.known : cache.get(view.accountId+':'+view.id);
      if(!view.force && fresh(cached)){view.render({phase:'ready',profile:cached});return;}
      const ready=availability();
      if(ready.stop) {view.render({phase:'error',error:ready.stop});return;}
      if(ready.wait>0) {view.render({phase:'waiting',seconds:Math.ceil(ready.wait/1000)});schedule(Math.min(ready.wait,1000));return;}
      if(ready.busy) {view.render({phase:'busy'});schedule(500);return;}
      inflight=true;const ticket=generation;view.render({phase:'loading'});
      let response;
      try {response=await load({accountId:view.accountId,id:view.id,manual:!!view.force});}catch{response={ok:false};}
      inflight=false;
      const profile=response?.profile;
      const valid=response?.ok && profile?.id===view.id && response.accountId===view.accountId;
      if(valid && ticket===generation && owner===view.accountId) {
        const key=view.accountId+':'+view.id;cache.delete(key);cache.set(key,profile);
        while(cache.size>20)cache.delete(cache.keys().next().value);
      }
      if(current===view) {
        view.render(valid ? {phase:'ready',profile} : {phase:'error',error:response?.error});
      } else if(current)schedule(0);
    }
    return {
      open(context,render) {
        clear();
        if(owner!==context.accountId){generation++;cache.clear();owner=context.accountId;}
        current={...context,render};
        const cached=cache.get(context.accountId+':'+context.id);
        const known=fresh(context.known) ? context.known : fresh(cached) ? cached : null;
        if(known)render({phase:'ready',profile:known});else schedule(0);
      },
      retry() {if(!current)return;current.force=true;cache.delete(current.accountId+':'+current.id);if(!inflight)schedule(0);},
      peek(accountId,id) {
        const profile=cache.get(accountId+':'+id);
        return fresh(profile) ? profile : null;
      },
      close() {current=null;clear();},
      reset() {current=null;owner=null;generation++;cache.clear();clear();},
    };
  }
  root.TrackerProfileDetails=Object.freeze({create});
  if(typeof module==='object')module.exports=root.TrackerProfileDetails;
})(typeof window==='object'?window:globalThis);
