(function(root) {
  'use strict';
  function create({request,change}) {
    let context=null,key='',epoch=0,wanted=100;
    let state={items:[],total:null,nextOffset:0,loading:false,error:null};
    function select(value) {
      const next=JSON.stringify(value);
      if(next===key)return;
      const sameScope=context && value && JSON.stringify({...context,revision:''})===JSON.stringify({...value,revision:''});
      const retained=sameScope?state.items:[];
      wanted=Math.max(100,retained.length);
      key=next;context=value;epoch++;
      state={items:retained,total:null,nextOffset:0,loading:false,error:null};
    }
    async function load() {
      if(!context || state.loading || state.nextOffset===null)return;
      const ticket=epoch,offset=state.nextOffset;
      state={...state,loading:true,error:null};
      change();
      try {
        const items=[];let nextOffset=offset,page;
        do {
          const response=await request({...context,offset:nextOffset});
          if(ticket!==epoch)return;
          page=response?.page;
          if(!response?.ok)throw response?.error || {code:'service'};
          if(page?.accountId!==context.accountId || page.revision!==context.revision || page.offset!==nextOffset ||
             !Array.isArray(page.items) || page.items.length>100 || !Number.isInteger(page.total) ||
             page.total<nextOffset+page.items.length || (page.nextOffset===null && page.total!==nextOffset+page.items.length) ||
             (page.nextOffset!==null && (page.nextOffset!==nextOffset+page.items.length || page.nextOffset<=nextOffset)))throw {code:'service'};
          items.push(...page.items);nextOffset=page.nextOffset;
        } while(offset===0 && nextOffset!==null && items.length<wanted);
        state={items:offset===0?items:[...state.items,...items],total:page.total,nextOffset,loading:false,error:null};
      } catch(error) {
        if(ticket!==epoch)return;
        state={...state,loading:false,error};
      }
      if(ticket===epoch)change();
    }
    return {select,load,reset:()=>select(null),get state(){return state;}};
  }
  root.TrackerHistoryPages=Object.freeze({create});
})(typeof window==='object'?window:globalThis);
