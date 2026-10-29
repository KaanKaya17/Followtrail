((settings)=>{
  'use strict';
  if(location.origin!=='https://www.instagram.com'||window[settings.slot]||
     !/^\/api\/v1\/users\/[1-9][0-9]{0,29}\/info\/$/.test(settings.path)||
     settings.path!=='/api/v1/users/'+settings.owner+'/info/'||!/^__tracker_connection_[a-f0-9]{32}$/.test(settings.slot))return false;
  const state={stage:'waiting'};window[settings.slot]=state;
  if(!window.fetch||!window.AbortController||!window.TextDecoder){Object.assign(state,{stage:'done',reason:'invalid'});return true;}
  const controller=new AbortController();state.cancel=()=>controller.abort();
  let status=null,timedOut=false;
  const deadline=setTimeout(()=>{timedOut=true;controller.abort();},20000);
  const done=value=>{clearTimeout(deadline);delete state.cancel;Object.assign(state,{stage:'done',...(status===null?{}:{status}),...value});};
  (async()=>{
    try {
      const response=await fetch(settings.path,{method:'GET',credentials:'same-origin',mode:'same-origin',redirect:'error',cache:'no-store',headers:settings.headers,signal:controller.signal});
      status=response.status;
      const raw=response.headers.get('Retry-After'),retryAfter=raw&&raw.length<=160?raw:null;
      if(status===429){controller.abort();done({reason:'limited',retryAfter});return;}
      if([401,403,404].includes(status)){controller.abort();done({reason:status===401?'login_required':status===403?'verification':'not_found'});return;}
      if(!response.body||!response.body.getReader){controller.abort();done({reason:'invalid'});return;}
      const reader=response.body.getReader(),decoder=new TextDecoder('utf-8',{fatal:true});let bytes=0,text='';
      while(true){const part=await reader.read();if(part.done)break;bytes+=part.value.byteLength;
        if(bytes>1048576){await reader.cancel();done({reason:'oversized'});return;}
        text+=decoder.decode(part.value,{stream:true});}
      text+=decoder.decode();let body;try{body=JSON.parse(text);}catch(_){done({reason:'invalid'});return;}
      const message=body.message;
      if(message==='feedback_required'){done({reason:'feedback',retryAfter});return;}
      if(['please_wait_a_few_minutes','Please wait a few minutes before you try again.'].includes(message)){done({reason:'wait',retryAfter});return;}
      if(message==='login_required'){done({reason:'login_required'});return;}
      if(['challenge_required','checkpoint_required','consent_required'].includes(message)){done({reason:'verification'});return;}
      const user=body.user;
      if(status!==200||body.status!=='ok'||!user){done({reason:'invalid'});return;}
      const id=typeof user.pk==='string'?user.pk:Number.isSafeInteger(user.pk)?String(user.pk):'';
      if(!/^[1-9][0-9]{0,29}$/.test(id)||typeof user.username!=='string'||!/^[A-Za-z0-9._]{1,30}$/.test(user.username)||
         !Number.isSafeInteger(user.follower_count)||user.follower_count<0||!Number.isSafeInteger(user.following_count)||user.following_count<0){done({reason:'invalid'});return;}
      done({reason:'ok',payload:{status:'ok',user:{pk:id,username:user.username,follower_count:user.follower_count,following_count:user.following_count}}});
    }catch(_){done({reason:timedOut?'timeout':controller.signal.aborted?'cancelled':'connection'});}
  })();return true;
})
