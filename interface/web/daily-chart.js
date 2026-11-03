(function(root) {
  'use strict';
  function create({request,change}) {
    let key='',epoch=0,context=null,state={loading:false,result:null,error:null};
    async function load(args,revision,force=false) {
      const next=JSON.stringify([args,revision]);
      if(next===key && !force)return;
      key=next;context=args;const ticket=++epoch;
      state={loading:true,result:null,error:null};change();
      try {
        const response=await request(args),value=response?.daily;
        if(ticket!==epoch)return;
        if(!response?.ok || value?.accountId!==args.accountId || value.id!==args.id ||
           value.metric!==args.metric || value.range!==args.range || !Array.isArray(value.points) || value.points.length>720)
          throw response?.error || {code:'service'};
        state={loading:false,result:value,error:null};
      } catch(error) {if(ticket!==epoch)return;state={loading:false,result:null,error};}
      if(ticket===epoch)change();
    }
    function reset(){epoch++;key='';context=null;state={loading:false,result:null,error:null};}
    return {load,reset,get state(){return state;},get context(){return context;}};
  }
  function render(state,{t,num,esc,locale}) {
    if(state.loading)return `<p role="status">${t("Günlük geçmiş yükleniyor…")}</p>`;
    if(state.error)return `<p role="status">${t("Günlük geçmiş okunamadı. Kayıtlar korunuyor.")}</p><button class="button secondary" data-action="daily-retry">${t("Tekrar dene")}</button>`;
    const result=state.result,points=result?.points || [];
    if(!points.length)return `<p>${t("İlk başarılı kontrolün ardından günlük geçmiş burada birikir.")}</p>`;
    const low=Math.max(0,Math.min(...points.map(p=>p.value))-5),high=Math.max(...points.map(p=>p.value))+5;
    const start=points[0].at,end=points.at(-1).at;
    const positions=points.map(p=>[points.length===1 ? 360 : 50+620*(p.at-start)/Math.max(1,end-start),210-170*(p.value-low)/(high-low)]);
    const line=positions.map(([x,y],i)=>`${i && points[i].bucket===points[i-1].bucket+1?'L':'M'}${x.toFixed(2)} ${y.toFixed(2)}`).join(' ');
    const date=at=>new Date(at).toLocaleDateString(locale,{timeZone:'UTC'});
    return `<div class="chart-summary"><strong>${num(points.at(-1).value)}</strong><span class="muted">${t("{0} gün · {1} kontrol",num(result.observedDays),num(result.samples))}</span></div><div class="chart-wrap"><svg class="history-chart" viewBox="0 0 710 252" role="img" aria-label="${t("Günlük sayaç geçmişi")}">${[0,1,2,3].map(i=>`<line x1="50" y1="${40+i*170/3}" x2="680" y2="${40+i*170/3}" class="grid-line"/><text x="34" y="${44+i*170/3}" text-anchor="end" class="axis-label">${num(Math.round(high-i*(high-low)/3))}</text>`).join('')}<path d="${line}" class="chart-line"/>${positions.map(([x,y],i)=>`<circle cx="${x}" cy="${y}" r="4" class="chart-point" tabindex="0"><title>${esc(date(points[i].day))} · ${num(points[i].value)} · ${t("En düşük {0} · En yüksek {1}",num(points[i].minimum),num(points[i].maximum))}</title></circle>`).join('')}<text x="50" y="242" class="axis-label">${esc(date(points[0].day))}</text><text x="670" y="242" text-anchor="end" class="axis-label">${esc(date(points.at(-1).day))}</text></svg></div>${result.bucketDays>1?`<p class="field-hint">${t("Uzun dönem için {0} günlük gruplar gösteriliyor.",num(result.bucketDays))}</p>`:''}`;
  }
  root.TrackerDailyChart=Object.freeze({create,render});
})(typeof window==='object'?window:globalThis);
