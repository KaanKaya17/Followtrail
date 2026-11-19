/* Reader diagnosis shown in the desktop window: the pause notice, the request
   details section and the value-free report for the maintainers. */
window.TrackerReadHealth = Object.freeze({
  create({t, esc, date, num, icon, button, dialog, toast, api, data, page}) {
    function readerLabel(reader) {
      if (reader === 'profile') return t("Profil okuması");
      return reader === 'followers' ? t("Takipçiler") : t("Takip edilen");
    }
    function readerReason(reason) {
      const messages = {
        format: t("Instagram beklenen biçimde veri döndürmedi. Yanıt biçimi değişmiş olabilir."),
        identity: t("Yanıttaki hesap kimliği istenen hesapla eşleşmedi."),
        route: t("Instagram bu okuma yolunu reddetti. Uç nokta değişmiş olabilir."),
        redirect: t("Instagram bu okuma için beklenmedik bir yönlendirme döndürdü."),
        target: t("Instagram bu hedefi döndürmedi. Profilin erişimini kontrol et."),
        cursor: t("Instagram devam bilgisini reddetti. Sonraki tarama ilk sayfadan başlar."),
        server: t("Instagram sunucusu hata döndürdü."),
        connection: t("Instagram bağlantısından yanıt alınamadı."),
        session: t("Instagram oturumu reddetti. Hesabı yeniden bağla."),
        verification: t("Instagram ek kontrol istiyor. Resmî Instagram uygulamasında doğrula."),
        limited: t("Instagram istekleri sınırladı. Kontroller geçici olarak duraklatıldı."),
        budget: t("Yerel istek bütçesi veya mola nedeniyle okuma bekliyor."),
        private: t("Bu hesaba veya listeye erişim iznin yok."),
        partial: t("Instagram listenin tamamını göstermedi. Okunan hesaplar saklandı; bu listeden değişiklik sonucu çıkarılmadı."),
        pagination: t("Instagram aynı devam bilgisini döndürdü veya sayfalama ilerlemedi."),
        capacity: t("Tarama uygulamanın kapasite sınırına ulaştı."),
        policy: t("İzin verilmeyen ağ hedefi durduruldu."),
      };
      return messages[reason] || t("Instagram yanıtının nedeni belirlenemedi.");
    }
    // Reasons that point to a changed Instagram request rather than this account.
    const READ_COMPATIBILITY = ['format', 'identity', 'route', 'redirect'];
    function notice() {
      if (!data().account || !['overview','lists'].includes(page())) return "";
      const paused = Object.entries(data().readHealth || {}).filter(([, item]) => item.paused);
      if (!paused.length) return "";
      const changed = paused.find(([, item]) => READ_COMPATIBILITY.includes(item.reason));
      return `<div class="notice warning read-pause" role="status"><strong>${t("Otomatik okuma duraklatıldı")}</strong>
        ${paused.map(([reader,item]) => `<p><strong>${esc(readerLabel(reader))}</strong> · ${esc(readerReason(item.reason))}</p>`).join("")}
        ${changed ? `<p>${t("Eski kayıtların korunuyor. Aynı hatalı istek tekrar gönderilmez; uygulama güncellenene kadar bu okuma bekler.")}</p>` : ''}
        <div class="read-pause-actions"><button class="text-button" data-action="requests">${t("İstek bilgileri")}</button>${changed ? `<button class="text-button" data-action="read-report" data-id="${esc(changed[0])}">${t("Geliştiriciye bildir")}</button>` : ''}</div></div>`;
    }
    function diagnostics() {
      const items = Object.entries(data().readHealth || {});
      if (!items.length) return "";
      return `<div><h3>${t("Okuma tanısı")}</h3>${items.map(([reader,item]) => `<div class="dialog-note">
        <strong>${esc(readerLabel(reader))}${item.paused ? ' · ' + t("Otomatik okuma duraklatıldı") : ''}</strong>
        <p>${esc(readerReason(item.reason))}</p>
        <small>${item.http != null ? 'HTTP ' + num(item.http) + ' · ' : ''}${esc(date(item.at))}</small>
        ${Array.isArray(item.missing) && item.missing.length ? `<p><small>${t("Yanıtta eksik alanlar: {0}", esc(item.missing.join(', ')))}</small></p>` : ''}
        ${item.paused ? `<p>${t("Başarılı bir elle kontrol otomatik okumayı yeniden başlatır.")}</p>` : ''}
        ${READ_COMPATIBILITY.includes(item.reason) ? `<button class="text-button" data-action="read-report" data-id="${esc(reader)}">${t("Geliştiriciye bildir")}</button>` : ''}
        </div>`).join("")}</div>`;
    }
    async function reportDialog(reader) {
      const report = await api.readReport(reader, 'preview').catch(() => null);
      if (!report?.ok) { toast(t("Bu okuma için gönderilecek bir rapor yok.")); return; }
      dialog(`<span class="dialog-symbol lavender">${icon("info")}</span><h2 id="dialog-title">${t("Geliştiriciye bildir")}</h2>
        <p>${t("Bu rapor, Instagram’ın hangi isteği değiştirmiş olabileceğini gösterir. Kullanıcı adı, hesap kimliği, çerez veya yanıt içeriği eklenmez.")}</p>
        <pre class="read-report" tabindex="0">${esc(report.text)}</pre>
        <p class="dialog-note">${t("GitHub’da bildir, herkese açık yeni bir konu taslağı açar. Göndermeden önce metni GitHub’da görüp değiştirebilirsin. GitHub hesabı gerekir.")}</p>
        <div class="dialog-buttons">${button(t("Metni kopyala"), "read-report-copy", "note", "secondary", `data-id="${esc(reader)}"`)}${button(t("GitHub’da bildir"), "read-report-open", "arrow", "primary", `data-id="${esc(reader)}"`)}</div>`);
    }
    return {notice, diagnostics, reportDialog};
  },
});
