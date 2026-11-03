(function(root) {
  'use strict';
  function day(item) {
    const value=item.publishedAt ?? item.capturedAt;
    return new Date(value*1000).toISOString().slice(0,10);
  }
  function monthDate(value) {
    if(!/^\d{4}-(0[1-9]|1[0-2])$/.test(value || '') || Number(value.slice(0,4))<1970)return null;
    return new Date(value+'-01T00:00:00Z');
  }
  function shiftMonth(value,offset) {
    const current=monthDate(value);if(!current)return null;
    current.setUTCMonth(current.getUTCMonth()+offset);
    const year=current.getUTCFullYear();return year>=1970 && year<=9999?current.toISOString().slice(0,7):null;
  }
  function validDay(value) {
    return typeof value==='string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && monthDate(value.slice(0,7))!==null &&
      Number.isFinite(Date.parse(value+'T00:00:00Z')) && new Date(value+'T00:00:00Z').toISOString().slice(0,10)===value;
  }
  function calendar(archive,view={},now=new Date()) {
    const counts=new Map();
    for(const item of archive.items) {const key=day(item);counts.set(key,(counts.get(key)||0)+1);}
    const latest=[...counts.keys()].sort().at(-1) || null;
    const today=now.toISOString().slice(0,10);
    const month=monthDate(view.month)?view.month:(latest || today).slice(0,7);
    const start=monthDate(month),offset=(start.getUTCDay()+6)%7;
    const days=new Date(Date.UTC(start.getUTCFullYear(),start.getUTCMonth()+1,0)).getUTCDate();
    const lastInMonth=[...counts.keys()].filter(key=>key.startsWith(month+'-')).sort().at(-1);
    const selected=validDay(view.selected)?view.selected:lastInMonth || (today.startsWith(month+'-')?today:month+'-01');
    return {month,start,offset,days,selected,counts,today,latest};
  }
  function dayItems(archive,key) {
    return archive.items.filter(item=>day(item)===key).sort((a,b)=>(a.publishedAt ?? a.capturedAt)-(b.publishedAt ?? b.capturedAt) || a.id.localeCompare(b.id));
  }
  function create({load,availability=()=>({}),change,setTimer=setTimeout,clearTimer=clearTimeout}) {
    let current=null,timer=null,inflight=false;
    function clearTimerIfNeeded() {if(timer!==null)clearTimer(timer);timer=null;}
    function publish(asset) {if(current)change(current,asset);}
    function schedule() {clearTimerIfNeeded();timer=setTimer(run,400);}
    async function run() {
      timer=null;const view=current;
      if(!view || view.mode!=='detail' || !view.storyId || ['ready','error','unavailable'].includes(view.phase))return;
      if(inflight || availability().busy) {if(view.phase!=='waiting'){view.phase='waiting';publish();}schedule();return;}
      inflight=true;view.phase='loading';publish();
      let response;
      try {response=await load({accountId:view.archive.accountId,id:view.archive.id,storyId:view.storyId});}catch{response={ok:false};}
      inflight=false;
      if(current!==view) {if(current)run();return;}
      if(response?.error?.code==='busy') {view.phase='waiting';publish();schedule();return;}
      const asset=response?.storyMedia;
      const valid=response?.ok && asset?.accountId===view.archive.accountId && asset?.id===view.archive.id && asset?.storyId===view.storyId;
      view.phase=valid?'ready':'error';publish(valid?asset:null);
    }
    function select(selected,storyId) {
      if(!current)return;
      clearTimerIfNeeded();
      const rows=dayItems(current.archive,selected);
      const item=rows.find(row=>row.id===storyId) || rows.find(row=>row.status==='saved') || rows[0];
      current={archive:current.archive,month:selected.slice(0,7),selected,mode:'detail',storyId:item?.id || null,
        phase:item?.status==='saved'?'idle':'unavailable'};
      publish();run();
    }
    return {
      get view(){return current;},
      open(archive) {clearTimerIfNeeded();current={archive,mode:'calendar',phase:'idle',storyId:null};publish();},
      month(month) {if(current && monthDate(month)){current.month=month;publish();}},
      selectDay(selected) {if(current && validDay(selected))select(selected);},
      selectStory(id) {if(current?.selected && dayItems(current.archive,current.selected).some(row=>row.id===id))select(current.selected,id);},
      calendar() {if(current){clearTimerIfNeeded();current={...current,mode:'calendar',storyId:null,phase:'idle'};publish();}},
      retry() {if(current?.phase==='error'){current.phase='idle';run();}},
      wake() {if(current?.phase==='waiting' && !availability().busy && !inflight){clearTimerIfNeeded();run();}},
      close() {clearTimerIfNeeded();current=null;},
    };
  }
  function render(archive,{t,esc,date,locale='tr-TR'},view={}) {
    const model=calendar(archive,view);
    const items=dayItems(archive,model.selected);
    const item=items.find(row=>row.id===view.storyId) || items.find(row=>row.status==='saved') || items[0];
    const monthLabel=new Intl.DateTimeFormat(locale,{month:'long',year:'numeric',timeZone:'UTC'}).format(model.start);
    const dates=new Intl.DateTimeFormat(locale,{dateStyle:'long',timeZone:'UTC'});
    const weekdays=new Intl.DateTimeFormat(locale,{weekday:'short',timeZone:'UTC'});
    const dateLabel=value=>dates.format(new Date(value+'T00:00:00Z'));
    const weekday=index=>weekdays.format(new Date(Date.UTC(2024,0,1+index)));
    const previous=shiftMonth(model.month,-1),next=shiftMonth(model.month,1);
    const link=(url,label)=>`<button class="button secondary" data-action="open-profile-link" data-url="${esc(url)}" title="${esc(url)}">${esc(label)}</button>`;
    const details=view.mode==='detail';
    return `<div class="story-workspace ${details?'is-detail':'is-calendar'}"><aside class="story-sidebar"><section class="story-calendar" data-month="${model.month}" data-selected="${model.selected}" aria-label="${esc(t("Hikâye takvimi"))}">
      <header class="story-calendar-heading"><button class="icon-button" data-action="story-month" data-month="${previous || ''}" ${!previous?'disabled':''} aria-label="${esc(t("Önceki ay"))}">‹</button><h3 aria-live="polite">${esc(monthLabel)}</h3><button class="icon-button" data-action="story-month" data-month="${next || ''}" ${!next?'disabled':''} aria-label="${esc(t("Sonraki ay"))}">›</button></header>
      <div class="story-calendar-tools"><span>${t("{0} hikâye kaydı",archive.items.length)}</span>${model.latest?`<button class="text-button" data-action="story-day" data-day="${model.latest}">${t("Son kayıt")}</button>`:''}<small>UTC</small></div>
      <div class="story-calendar-grid">${Array.from({length:7},(_,index)=>`<span class="story-weekday">${esc(weekday(index))}</span>`).join('')}
      ${'<span class="story-calendar-blank" aria-hidden="true"></span>'.repeat(model.offset)}
      ${Array.from({length:model.days},(_,index)=>{
        const key=model.month+'-'+String(index+1).padStart(2,'0'),count=model.counts.get(key)||0;
        return `<button class="story-calendar-day ${count?'has-stories':''} ${key===model.selected?'is-selected':''} ${key===model.today?'is-today':''}" data-action="story-day" data-day="${key}" aria-pressed="${key===model.selected}" aria-label="${esc(dateLabel(key))} · ${esc(t("{0} hikâye",count))}" aria-controls="story-day-detail"><span>${index+1}</span>${count?`<small data-count="${count}">${t("{0} hikâye",count)}</small>`:''}</button>`;
      }).join('')}</div></section>
      ${archive.checkedAt?`<p class="field-hint story-last-check">${t("Son kontrol")}: ${date(archive.checkedAt)} · ${t("Son kontrolde {0} erişilebilir hikâye döndü.",archive.summary?.returned ?? 0)}</p>`:''}
      </aside>${details?`<section id="story-day-detail" class="story-day" aria-labelledby="story-day-title"><header class="section-heading"><div><h3 id="story-day-title">${esc(dateLabel(model.selected))}</h3><span>${t("{0} hikâye",items.length)}</span></div><button class="text-button" data-action="story-calendar">${t("Takvime dön")}</button></header>
      ${!items.length?`<div class="empty-state story-empty"><p>${t("Bu günde kayıtlı hikâye yok.")}</p></div>`:`
      <nav class="story-choices" aria-label="${esc(t("Günün hikâyeleri"))}">${items.map((row,index)=>`<button class="story-choice ${row.id===item.id?'selected':''}" data-action="story-select" data-story-id="${esc(row.id)}" aria-pressed="${row.id===item.id}">${index+1} · ${row.type==='video'?t("Video"):t("Fotoğraf")}</button>`).join('')}</nav>
      <div class="story-detail-layout"><div id="story-player" aria-live="polite"><div class="story-player-message">${view.phase==='error'?`${t("Hikâye açılamadı.")}<button class="button secondary" data-action="story-media-retry">${t("Yeniden dene")}</button>`:view.phase==='unavailable'?t("Yalnız bilgiler kaydedildi"):view.phase==='waiting'?t("Diğer işlem bitince hikâye açılacak."):t("Hikâye açılıyor…")}</div></div>
      <article class="story-facts"><h3>${t("Hikâye bilgileri")}</h3><p class="field-hint">${date(item.publishedAt ?? item.capturedAt)}${item.publishedAt==null?` · ${t("Kaydedilme zamanı")}`:''}</p>
      <section><h4>${t("Etiketlenenler")}</h4>${item.mentions?.length?item.mentions.map(m=>link('https://www.instagram.com/'+m.username+'/', '@'+m.username)).join(' '):`<p class="field-hint">${item.mentions===null?t("Etiket bilgisi alınamadı."):t("Kayıtlı etiket yok.")}</p>`}</section>
      <section><h4>${t("Paylaşım kaynağı")}</h4><p class="field-hint">${t("Yeniden paylaşımın kaynağı bu kayıtta doğrulanamadı.")}</p>
      ${item.sharedMedia?.length?`<p>${t("Hikâyede paylaşılan gönderi")}</p>${item.sharedMedia.map(media=>media.code?link('https://www.instagram.com/p/'+media.code+'/',t("Instagram'da aç")):`<small>${t("Gönderi kimliği")}: ${esc(media.id)}</small>`).join('')}`:''}</section>
      ${item.links?.length?`<section><h4>${t("Bağlantılar")}</h4><div class="drawer-actions">${item.links.map(url=>link(url,root.TrackerProfileLinks.describe(url)?.name || t("Bağlantı"))).join('')}</div></section>`:''}
      ${item.hashtags?.length?`<p>${item.hashtags.map(value=>'#'+esc(value)).join(' ')}</p>`:''}
      ${item.locations?.length?`<p>${item.locations.map(esc).join(' · ')}</p>`:''}
      ${item.incompleteFields?.length?`<small>${t("Bazı hikâye ayrıntıları okunamadı.")}</small>`:''}
      </article></div>`}</section>`:`<p class="story-calendar-prompt">${t("Hikâyeleri görmek için bir gün seç.")}</p>`}</div>
      ${archive.checkedAt && archive.summary?.returned===0?`<p class="notice warning">${t("Instagram bu kontrolde hikâye verisi döndürmedi. Bu, hikâye paylaşılmadığını göstermez. Bağlı hesabın hikâyeyi Instagram’da açabildiğini kontrol et.")}</p>`:''}`;
  }
  let activeMedia=null;
  function release() {
    if(!activeMedia)return;
    activeMedia.container.querySelector('video')?.pause();
    activeMedia.container.replaceChildren();
    URL.revokeObjectURL(activeMedia.url);
    activeMedia=null;
  }
  function retainedMedia(archive,storyId) {
    return activeMedia?.accountId===archive.accountId && activeMedia?.id===archive.id && activeMedia?.storyId===storyId && activeMedia.container.isConnected?activeMedia.container:null;
  }
  function play(asset,container) {
    release();
    const raw=atob(asset.base64),bytes=new Uint8Array(raw.length);
    for(let i=0;i<raw.length;i++)bytes[i]=raw.charCodeAt(i);
    const url=URL.createObjectURL(new Blob([bytes],{type:asset.mime}));
    const owner={url,container,accountId:asset.accountId,id:asset.id,storyId:asset.storyId};activeMedia=owner;
    const element=document.createElement(asset.mime.startsWith('video/')?'video':'img');
    element.addEventListener(element.tagName==='VIDEO'?'loadedmetadata':'load',()=>{
      if(activeMedia===owner && container.isConnected)container.scrollIntoView({block:'nearest'});
    },{once:true});
    element.src=url;
    if(element.tagName==='VIDEO') {element.controls=true;element.preload='metadata';}
    else element.alt='';
    container.replaceChildren(element);
    container.scrollIntoView({block:'nearest'});
  }
  root.TrackerStories={create,render,day,calendar,dayItems,shiftMonth,play,release,retainedMedia};
  if(typeof module==='object')module.exports=root.TrackerStories;
})(typeof window==='object'?window:globalThis);
