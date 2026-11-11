(function(root) {
  'use strict';
  const valid=value=>typeof value==='string' && value.length<=100000 && /^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/.test(value);
  function create({request,cancel,onImage=()=>{}}) {
    let observer=null,epoch=0,scope=null,owner=null,active=0,queue=[];
    const cache=new Map(),pending=new Map();let cacheEpoch=0;
    const keyOf=context=>[context.accountId,context.ownerId,context.direction,context.scanId,context.id].join(':');
    function peek(context) {
      const saved=cache.get(keyOf(context));
      return saved && Date.now()-saved.at<(saved.avatar ? 900000 : 60000) ? saved.avatar : null;
    }
    function get(context) {
      if(owner!==context.accountId){clear(true);owner=context.accountId;}
      const key=keyOf(context),saved=cache.get(key),ticket=cacheEpoch,listTicket=epoch;
      if(saved && Date.now()-saved.at<(saved.avatar ? 900000 : 60000))return Promise.resolve({ok:true,avatar:saved.avatar});
      if(pending.has(key))return pending.get(key);
      const work=request(context).catch(()=>null).then(response=>{
        if(ticket===cacheEpoch && owner===context.accountId && response?.ok && (valid(response.avatar) || listTicket===epoch)) {
          cache.delete(key);cache.set(key,{avatar:valid(response.avatar)?response.avatar:null,at:Date.now()});
          while(cache.size>32)cache.delete(cache.keys().next().value);
          if(valid(response.avatar))onImage(context,response.avatar);
        }
        return response;
      }).finally(()=>{if(pending.get(key)===work)pending.delete(key);});
      pending.set(key,work);return work;
    }
    function clear(reset=false) {
      epoch++;observer?.disconnect();observer=null;queue=[];
      if(scope!==null)Promise.resolve(cancel()).catch(()=>{});
      scope=null;
      if(reset){cacheEpoch++;cache.clear();pending.clear();owner=null;}
    }
    function mount(container,context) {
      if(!container){clear();return;}
      if(owner!==context.accountId){clear(true);owner=context.accountId;}
      const next=[context.accountId,context.ownerId,context.peerOwnerId,context.direction].join(':');
      if(scope!==null && scope!==next)clear();
      scope=next;epoch++;observer?.disconnect();queue=[];
      const ticket=epoch,scheduled=new WeakSet();
      function paint(node,value) {
        if(!valid(value) || ticket!==epoch || !node.isConnected)return;
        const image=document.createElement('img');image.alt='';image.src=value;
        node.querySelector('.avatar')?.replaceChildren(image);
      }
      async function load(node) {
        const response=await get({accountId:context.accountId,ownerId:node.dataset.photoOwner || context.ownerId,direction:context.direction,scanId:node.dataset.photoScan,id:node.dataset.id});
        paint(node,response?.avatar);
      }
      function pump() {
        if(ticket!==epoch)return;
        while(active<2 && queue.length) {
          const node=queue.shift();
          if(ticket!==epoch || !node.isConnected)continue;
          const bounds=node.getBoundingClientRect();
          if(bounds.bottom < -160 || bounds.top > innerHeight+160) {
            scheduled.delete(node);observer.observe(node);continue;
          }
          active++;
          load(node).finally(()=>{active--;if(ticket===epoch)pump();else refresh();});
        }
      }
      // Old in-flight replies may finish after a local filter/refresh. Their
      // cache can serve the new rows, but only the current pump owns the DOM.
      refresh=pump;
      observer=new IntersectionObserver(entries=>{
        if(ticket!==epoch)return;
        for(const entry of entries)if(entry.isIntersecting && !scheduled.has(entry.target)) {
          scheduled.add(entry.target);queue.push(entry.target);observer.unobserve(entry.target);
        }
        pump();
      },{rootMargin:'160px'});
      for(const node of container.querySelectorAll('.list-person[data-photo-scan]:not([data-photo-scan=""])'))
        if(!node.querySelector('.avatar img'))observer.observe(node);
    }
    let refresh=()=>{};
    return {mount,clear,peek,get};
  }
  root.TrackerListPhotos=Object.freeze({create});
})(typeof window==='object'?window:globalThis);
