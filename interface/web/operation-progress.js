(function (root) {
  'use strict';
  const escape = value => String(value ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function create({element,t,date,number,button,icon}) {
    let state = {busy:false,progress:null,gate:{}}, timer = null;
    function stop() { if(timer!==null) { clearInterval(timer);timer=null; } }
    function remaining() {
      return Number.isFinite(state.progress?.until) ? Math.max(0,Math.ceil(state.progress.until-Date.now()/1000)) : null;
    }
    function tick() {
      const node=element.querySelector('[data-wait-countdown]');
      if(!node || state.visible===false || document.hidden || state.progress?.phase!=='waiting') { stop();return; }
      const seconds=remaining();
      node.textContent=seconds===null ? t("Sonraki isteğin zamanı doğrulanamadı.") : seconds>0 ? t("Kısa mola · {0} saniye sonra devam edecek",number(seconds)) : t("Kontrole devam etmek için hazırlanıyor…");
      const bar=element.querySelector('progress');
      const started=state.progress.waitStartedAt, until=state.progress.until;
      if(bar && Number.isFinite(started) && Number.isFinite(until) && until>started) {
        bar.max=until-started;
        bar.value=Math.max(0,Math.min(bar.max,Date.now()/1000-started));
      }
      if(seconds===null || seconds===0) stop();
    }
    function render() {
      stop();
      if(state.visible===false || document.hidden) return;
      const p=state.progress || {}, gate=state.gate || {}, scan=p.scan;
      const paused=!state.busy && Number.isFinite(gate.blockedUntil) && gate.blockedUntil>0 && (gate.blockedUntil>Date.now()/1000 || gate.manualOnly && gate.status===429);
      if(!state.busy && !paused) { element.innerHTML='';return; }
      if(paused || p.phase==='limited') {
        element.innerHTML=`<section class="operation-panel paused"><span class="progress-symbol">${icon('info')}</span><div><strong>${escape(gate.status === 429 || p.phase === "limited" ? t("Instagram istekleri sınırladı") : t("Kontroller duraklatıldı"))}</strong><p>${escape(t("En erken {0} yeniden deneyebilirsin. Yeni deneme kendiliğinden başlamayacak.",date(paused?gate.blockedUntil:p.until)))}</p><small>${escape(t("Instagram daha uzun beklemeni isteyebilir. Mola bitince kontrolü sen başlatabilirsin; kayıtlarını incelemeye devam edebilirsin."))}</small></div>${button(t("İstek bilgileri"),'requests','info')}</section>`;
        return;
      }
      const messages={preparing:t("İşlem hazırlanıyor…"),login:t("Instagram hesabına bağlanılıyor…"),profile:t("Profil okunuyor…"),avatar:t("Profil fotoğrafı yükleniyor…"),access_health:t("Hesap bağlantın kontrol ediliyor…"),access_target:t("Profilin açılıp açılmadığı kontrol ediliyor…"),access_anonymous:t("Profilin giriş yapmadan açılıp açılmadığı kontrol ediliyor…"),request:t("Instagram yanıtı bekleniyor…"),waiting:t("İstekler arasında kısa mola…"),list:t("Liste sayfaları okunuyor…"),code:t("Doğrulama kodu bekleniyor…"),cancelling:t("İptal isteniyor…")};
      let title=messages[p.phase] || t("İşlem sürüyor…"), context='';
      if(p.ownerUsername) context=`<p>${escape(t("Bağlı hesap: @{0}",p.ownerUsername))}</p>`;
      if(scan) {
        const direction=scan.direction==='followers'?t("Takipçiler"):t("Takip edilen");
        context+=`<p class="scan-owner">@${escape(scan.username)} · ${escape(direction)}</p>`;
        if(scan.step==='profile') title=t("Liste için güncel profil sayısı okunuyor…");
        else {
          title=p.phase==='request'?t("Sayfa {0} için Instagram yanıtı bekleniyor…",number(scan.page)):p.phase==='list_received'?t("Sayfa {0} alındı",number(scan.page)):t("Sayfa {0} sırada",number(scan.page));
          context+=`<p class="scan-counts">${escape(t("{0} sayfa alındı · {1} hesap kaydedildi",number(scan.completedPages),number(scan.count)))}</p><small>${escape(scan.expected==null?t("Son profil sayısı henüz bilinmiyor."):t("Son profil sayısı: {0}. Tarama sırasında değişebilir.",number(scan.expected)))}</small><small>${escape(t("Toplam sayfa sayısı henüz bilinmiyor."))}</small>`;
        }
      }
      if(p.phase==='cancelling') title=messages.cancelling;
      const wait=p.phase==='waiting'?`<p class="wait-countdown" data-wait-countdown aria-live="off"></p><small>${escape(t("Uygulama istekler arasında bekliyor. Instagram ayrıca daha uzun bir mola isteyebilir."))}</small>`:p.phase==='cancelling'?`<small>${escape(t("İptal isteği gönderildi. Devam eden ağ yanıtı zaman aşımı süresine kadar bekleyebilir."))}</small>`:`<small>${escape(t("İşlem bitene kadar diğer ağ işlemleri bekler."))}</small>`;
      const navigation=scan ? `<small>${escape(t("Bu sırada diğer ekranları kullanabilirsin; yeni ağ işlemleri sıra bekler."))}</small>` : '';
      element.innerHTML=`<section class="operation-panel"><span class="working-dot"></span><div class="operation-copy"><strong>${escape(title)}</strong>${context}${wait}${navigation}<progress aria-label="${escape(p.phase==='waiting'?t("İstek aralığı ilerlemesi"):t("İşlem ilerlemesi"))}"></progress></div>${p.phase==='code'?button(t("Kodu gir"),'enter-code','lock'):''}${button(t("İptal et"),'cancel','close','secondary',p.phase==='cancelling'?'disabled':'')}</section>`;
      if(p.phase==='waiting') { tick(); if(remaining()>0)timer=setInterval(tick,1000); }
    }
    document.addEventListener('visibilitychange',()=>document.hidden?stop():render());
    window.addEventListener('pagehide',stop);
    return Object.freeze({update(value){state=value;render();}});
  }
  root.TrackerOperationProgress=Object.freeze({create});
})(window);
