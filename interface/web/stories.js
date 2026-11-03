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
  function calendar(archive,view={},now=new Date()) {
    const counts=new Map();
    for(const item of archive.items) {const key=day(item);counts.set(key,(counts.get(key)||0)+1);}
    const latest=[...counts.keys()].sort().at(-1) || null;
    const today=now.toISOString().slice(0,10);
    const month=monthDate(view.month)?view.month:(latest || today).slice(0,7);
    const start=monthDate(month),offset=(start.getUTCDay()+6)%7;
    const days=new Date(Date.UTC(start.getUTCFullYear(),start.getUTCMonth()+1,0)).getUTCDate();
    const lastInMonth=[...counts.keys()].filter(key=>key.startsWith(month+'-')).sort().at(-1);
    const selected=(view.selected?.startsWith(month+'-') && /^\d{4}-\d{2}-\d{2}$/.test(view.selected) &&
      Number(view.selected.slice(-2))>=1 && Number(view.selected.slice(-2))<=days)?view.selected:lastInMonth || (today.startsWith(month+'-')?today:month+'-01');
    return {month,start,offset,days,selected,counts,today,latest};
  }
  function render(archive,{t,esc,date,locale='tr-TR'},view={}) {
    const model=calendar(archive,view);
    const items=archive.items.filter(item=>day(item)===model.selected);
    const monthLabel=new Intl.DateTimeFormat(locale,{month:'long',year:'numeric',timeZone:'UTC'}).format(model.start);
    const dates=new Intl.DateTimeFormat(locale,{dateStyle:'long',timeZone:'UTC'});
    const weekdays=new Intl.DateTimeFormat(locale,{weekday:'short',timeZone:'UTC'});
    const dateLabel=value=>dates.format(new Date(value+'T00:00:00Z'));
    const weekday=index=>weekdays.format(new Date(Date.UTC(2024,0,1+index)));
    const previous=shiftMonth(model.month,-1),next=shiftMonth(model.month,1);
    const link=(url,label)=>`<button class="button secondary" data-action="open-profile-link" data-url="${esc(url)}" title="${esc(url)}">${esc(label)}</button>`;
    return `<section class="story-calendar" data-month="${model.month}" data-selected="${model.selected}" aria-label="${esc(t("Hikâye takvimi"))}">
      <header class="story-calendar-heading"><button class="icon-button" data-action="story-month" data-month="${previous || ''}" ${!previous?'disabled':''} aria-label="${esc(t("Önceki ay"))}">‹</button><h3 aria-live="polite">${esc(monthLabel)}</h3><button class="icon-button" data-action="story-month" data-month="${next || ''}" ${!next?'disabled':''} aria-label="${esc(t("Sonraki ay"))}">›</button></header>
      <div class="story-calendar-tools"><span>${t("{0} hikâye kaydı",archive.items.length)}</span>${model.latest?`<button class="text-button" data-action="story-day" data-day="${model.latest}">${t("Son kayıt")}</button>`:''}<small>UTC</small></div>
      <div class="story-calendar-grid">${Array.from({length:7},(_,index)=>`<span class="story-weekday">${esc(weekday(index))}</span>`).join('')}
      ${'<span class="story-calendar-blank" aria-hidden="true"></span>'.repeat(model.offset)}
      ${Array.from({length:model.days},(_,index)=>{
        const key=model.month+'-'+String(index+1).padStart(2,'0'),count=model.counts.get(key)||0;
        return `<button class="story-calendar-day ${count?'has-stories':''} ${key===model.selected?'is-selected':''} ${key===model.today?'is-today':''}" data-action="story-day" data-day="${key}" aria-pressed="${key===model.selected}" aria-label="${esc(dateLabel(key))} · ${esc(t("{0} hikâye",count))}" aria-controls="story-day-detail"><span>${index+1}</span>${count?`<small>${t("{0} hikâye",count)}</small>`:''}</button>`;
      }).join('')}</div></section>
      <section id="story-day-detail" class="story-day" aria-labelledby="story-day-title"><div class="section-heading"><h3 id="story-day-title">${esc(dateLabel(model.selected))}</h3><span>${t("{0} hikâye",items.length)}</span></div>
      <div id="story-player" aria-live="polite"></div>
      ${!items.length?`<div class="empty-state story-empty"><p>${t("Bu günde kayıtlı hikâye yok.")}</p></div>`:''}
      <div class="story-grid">${items.map(item=>`<article class="card story-card">
        <strong>${item.type==='image'?t("Fotoğraf"):item.type==='video'?t("Video"):t("Desteklenmeyen içerik")}</strong>
        <p>${date(item.publishedAt ?? item.capturedAt)}${item.publishedAt==null?` · ${t("Kaydedilme zamanı")}`:''}</p>
        ${item.incompleteFields?.length?`<small>${t("Bazı hikâye ayrıntıları okunamadı.")}</small>`:''}
        <button class="button secondary" data-action="story-play" data-id="${esc(archive.id)}" data-story-id="${esc(item.id)}" ${item.status!=='saved'?'disabled':''}>${item.status==='saved'?t("Aç"):t("Yalnız bilgiler kaydedildi")}</button>
        ${item.mentions?.length?`<p>${t("Etiketlenenler")}: ${item.mentions.map(m=>link('https://www.instagram.com/'+m.username+'/', '@'+m.username)).join(' ')}</p>`:''}
        ${item.hashtags?.length?`<p>${item.hashtags.map(value=>'#'+esc(value)).join(' ')}</p>`:''}
        ${item.locations?.length?`<p>${item.locations.map(esc).join(' · ')}</p>`:''}
        ${item.links?.length?`<div class="drawer-actions">${item.links.map(url=>link(url,root.TrackerProfileLinks.describe(url)?.name || t("Bağlantı"))).join('')}</div>`:''}
        ${item.sharedMedia?.length?`<p>${t("Hikâyede paylaşılan gönderi")}</p>${item.sharedMedia.map(media=>media.code?link('https://www.instagram.com/p/'+media.code+'/',t("Instagram'da aç")):`<small>${t("Gönderi kimliği")}: ${esc(media.id)}</small>`).join('')}`:''}
      </article>`).join('')}</div></section>
      ${archive.checkedAt?`<p class="field-hint story-last-check">${t("Son kontrol")}: ${date(archive.checkedAt)} · ${t("Son kontrolde {0} erişilebilir hikâye döndü.",archive.summary?.returned ?? 0)}</p>`:''}
      ${archive.checkedAt && archive.summary?.returned===0?`<p class="notice warning">${t("Instagram bu kontrolde hikâye verisi döndürmedi. Bu, hikâye paylaşılmadığını göstermez. Bağlı hesabın hikâyeyi Instagram’da açabildiğini kontrol et.")}</p>`:''}`;
  }
  let activeUrl=null;
  function release() {
    const player=typeof document==='object' && document.getElementById('story-player');
    player?.querySelector('video')?.pause();
    if(player)player.replaceChildren();
    if(activeUrl)URL.revokeObjectURL(activeUrl);
    activeUrl=null;
  }
  function play(asset,container) {
    release();
    const raw=atob(asset.base64),bytes=new Uint8Array(raw.length);
    for(let i=0;i<raw.length;i++)bytes[i]=raw.charCodeAt(i);
    activeUrl=URL.createObjectURL(new Blob([bytes],{type:asset.mime}));
    const element=document.createElement(asset.mime.startsWith('video/')?'video':'img');
    element.addEventListener(element.tagName==='VIDEO'?'loadedmetadata':'load',()=>{
      if(activeUrl===element.src && container.isConnected)container.scrollIntoView({block:'nearest'});
    },{once:true});
    element.src=activeUrl;
    if(element.tagName==='VIDEO') {element.controls=true;element.preload='metadata';}
    else element.alt='';
    container.replaceChildren(element);
    container.scrollIntoView({block:'nearest'});
  }
  root.TrackerStories={render,day,calendar,shiftMonth,play,release};
  if(typeof module==='object')module.exports=root.TrackerStories;
})(typeof window==='object'?window:globalThis);
