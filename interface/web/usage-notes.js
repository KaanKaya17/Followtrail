/* Shared, static help. Rendering this content never reads an account or starts a check. */
window.TrackerUsageNotes = Object.freeze({
  render(t, retention = "") {
    return `<h2 id="dialog-title">${t("Kullanım notları")}</h2><div class="usage-notes">
      <section><h3>${t("Kontroller neleri gösterir?")}</h3><p>${t("Sonuçlar, kaydedilen kontroller arasındaki farkları gösterir. Değişikliğin kesin zamanı veya tek başına takipten çıkış kanıtı değildir.")}</p><p>${t("Noktalar arasındaki hareketler bilinmiyor.")}</p></section>
      <section><h3>${t("Takip Listeleri")}</h3><p>${t("İlk tam liste başlangıç kaydıdır. Sonraki tamamlanmış listeler karşılaştırılır. Yarım listeler kesin sonuç üretmez; arama yalnız cihazdaki kayıtlarda çalışır.")}</p><p>${t("Liste sayfa sayfa okunur. İstekler arasında molalar verilir; kaydedilen sayfa ve hesap sayısını ilerleme göstergesinden izleyebilirsin. Bu sırada diğer ekranları kullanabilirsin; yeni ağ işlemleri sıra bekler.")}</p><p>${t("Bu sonuç kaydedilen takipçi ve takip edilen listelerine göre gösterilir. İki listenin kontrol zamanı farklı olabilir.")}</p></section>
      <section><h3>${t("Otomatik profil kontrolü")}</h3><p>${t("Kontrol aralığı bir hedeftir. Instagram molası, internet ve arka plan koşulları geciktirebilir.")}</p></section>
      <section><h3>${t("Günlük geçmiş")}</h3><p>${t("Günlük sayaç özetleri kendiliğinden silinmez. Son kontrollerden ayrı, cihazda şifreli saklanır; günlük geçmiş ekranından temizlenebilir.")}</p><p>${t("Günler UTC saatine göre gruplanır. Kontrol yapılmayan günler boş kalır.")}</p></section>
      ${retention ? `<section><h3>${t("Zaman çizelgesi")}</h3><p>${retention}</p></section>` : ""}
    </div>`;
  },
});
