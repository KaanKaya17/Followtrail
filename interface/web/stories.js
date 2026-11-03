(function(root) {
  'use strict';
  function day(item) {
    const value=item.publishedAt ?? item.capturedAt;
    return new Date(value*1000).toISOString().slice(0,10);
  }
  function render(archive,{t,esc,date},filter='') {
    const items=archive.items.filter(item=>!filter || day(item)===filter);
    const groups=new Map();
    for(const item of items) {const key=day(item);if(!groups.has(key))groups.set(key,[]);groups.get(key).push(item);}
    const link=(url,label)=>`<button class="button secondary" data-action="open-profile-link" data-url="${esc(url)}" title="${esc(url)}">${esc(label)}</button>`;
    return `<div class="toolbar"><label>${t("Tarih (UTC)")} <input id="story-date" type="date" value="${esc(filter)}"></label><span>${t("{0} hikâye kaydı",archive.items.length)}</span></div>
      <div id="story-player" aria-live="polite"></div>
      ${archive.checkedAt?`<p class="field-hint">${t("Son kontrol")}: ${date(archive.checkedAt)} · ${t("Son kontrolde {0} erişilebilir hikâye döndü.",archive.summary?.returned ?? 0)}</p>`:''}
      ${!items.length?`<div class="empty-state"><p>${t("Bu tarihte kayıt yok. Hikâyeleri kontrol ederek arşivlemeyi deneyebilirsin.")}</p></div>`:''}
      ${[...groups].map(([key,rows])=>`<section class="story-day"><h3>${esc(key)}</h3><div class="story-grid">${rows.map(item=>`<article class="card story-card">
        <strong>${item.type==='image'?t("Fotoğraf"):item.type==='video'?t("Video"):t("Desteklenmeyen içerik")}</strong>
        <p>${date(item.publishedAt ?? item.capturedAt)}${item.publishedAt==null?` · ${t("Kaydedilme zamanı")}`:''}</p>
        ${item.incompleteFields?.length?`<small>${t("Bazı hikâye ayrıntıları okunamadı.")}</small>`:''}
        <button class="button secondary" data-action="story-play" data-id="${esc(archive.id)}" data-story-id="${esc(item.id)}" ${item.status!=='saved'?'disabled':''}>${item.status==='saved'?t("Aç"):t("Yalnız bilgiler kaydedildi")}</button>
        ${item.mentions?.length?`<p>${t("Etiketlenenler")}: ${item.mentions.map(m=>link('https://www.instagram.com/'+m.username+'/', '@'+m.username)).join(' ')}</p>`:''}
        ${item.hashtags?.length?`<p>${item.hashtags.map(value=>'#'+esc(value)).join(' ')}</p>`:''}
        ${item.locations?.length?`<p>${item.locations.map(esc).join(' · ')}</p>`:''}
        ${item.links?.length?`<div class="drawer-actions">${item.links.map(url=>link(url,root.TrackerProfileLinks.describe(url)?.name || t("Bağlantı"))).join('')}</div>`:''}
        ${item.sharedMedia?.length?`<p>${t("Hikâyede paylaşılan gönderi")}</p>${item.sharedMedia.map(media=>media.code?link('https://www.instagram.com/p/'+media.code+'/',t("Instagram'da aç")):`<small>${t("Gönderi kimliği")}: ${esc(media.id)}</small>`).join('')}`:''}
      </article>`).join('')}</div></section>`).join('')}`;
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
    element.src=activeUrl;
    if(element.tagName==='VIDEO') {element.controls=true;element.preload='metadata';}
    else element.alt='';
    container.replaceChildren(element);
    container.scrollIntoView({block:'nearest'});
  }
  root.TrackerStories={render,day,play,release};
  if(typeof module==='object')module.exports=root.TrackerStories;
})(typeof window==='object'?window:globalThis);
