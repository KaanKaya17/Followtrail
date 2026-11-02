(function(root) {
  'use strict';
  function create({request,change}) {
    let context=null,key='',epoch=0,observer=null,sentinel=null,enabled=false;
    let state={items:[],total:null,nextOffset:0,loading:false,error:null,added:0,removed:0};
    function select(value) {
      const next=JSON.stringify(value);
      if(next===key)return;
      key=next;context=value;epoch++;
      state={items:[],total:null,nextOffset:value ? 0 : null,loading:false,error:null,added:0,removed:0};
    }
    async function load() {
      if(!enabled || !context || state.loading || state.nextOffset===null || state.error)return;
      const ticket=epoch,offset=state.nextOffset;
      state={...state,loading:true};change();
      try {
        const response=await request({...context,offset});
        if(ticket!==epoch)return;
        const page=response?.page;
        if(!response?.ok)throw response?.error || {code:'service'};
        if(page?.accountId!==context.accountId || page.revision!==context.revision || page.offset!==offset ||
           !Array.isArray(page.items) || page.items.length>100 || !Number.isInteger(page.total) ||
           page.total<offset+page.items.length || (page.nextOffset===null && page.total!==offset+page.items.length) ||
           (page.nextOffset!==null && (page.nextOffset!==offset+page.items.length || page.nextOffset<=offset)))
          throw {code:'service'};
        state={...state,items:[...state.items,...page.items],total:page.total,nextOffset:page.nextOffset,
          added:page.added,removed:page.removed,loading:false,error:null};
      } catch(error) {
        if(ticket!==epoch)return;
        state={...state,loading:false,error};
      }
      if(ticket!==epoch)return;
      change();
      // Re-arm after the sentinel moves. Short pages can fill a tall viewport.
      observe();
    }
    function observe() {
      observer?.disconnect();observer=null;
      if(!enabled || !sentinel || !context || state.error || state.nextOffset===null)return;
      const ticket=epoch;
      observer=new IntersectionObserver(entries=>{
        if(ticket===epoch && entries.some(e=>e.isIntersecting))load();
      },{rootMargin:'360px'});
      observer.observe(sentinel);
    }
    function mount(node,visible) {
      sentinel=node;enabled=!!visible && !!node;
      observe();
      if(enabled && state.total===null && !state.error)load();
    }
    function reset() {select(null);observer?.disconnect();observer=null;sentinel=null;enabled=false;}
    function retry() {state={...state,error:null};load();}
    return {select,mount,reset,retry,get state(){return state;},get key(){return key;}};
  }
  root.TrackerListPages=Object.freeze({create});
})(typeof window==='object'?window:globalThis);
