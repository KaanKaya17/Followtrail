# Windows 0.2.9 — klavye ve boş durum doğrulaması

28 Ekim 2026. Bu değişiklik masaüstü arayüzü, native Reload kısayolunun menü davranışı ve yerel rehber kartlarıyla sınırlıdır. Backend işlem, zamanlama ve ağ kuralları değiştirilmedi; görüntülenen sürüm 0.2.9 oldu. Android'deki mevcut kişi boş durumu zaten rehber kartı içeriyor; Android paketi ve örnek web işlevleri değiştirilmedi.

Çalıştırılan kontroller:

- `npm run check`: ortak tema, Türkçe/İngilizce katalog ve tüm JavaScript sözdizimi geçti.
- `node --test desktop/*.test.cjs`: 22 mevcut Node testi geçti.
- `node scripts/check-navigation-desktop.mjs`: gerçek paketlenmiş EXE üzerinde 10 senaryo grubu geçti. Beş sekme, `/` ve Ctrl+K, Türkçe/ASCII sorgu eşleştirme, HTML içeren adın metin olarak gösterilmesi, ok/Enter/Esc akışı, form taslağının korunması ve formda rakam/slash yazılması sınandı. Native `webContents.sendInputEvent` ile Ctrl+R'nin sayfayı yeniden yüklemeden açık kişi için tek `refresh` komutu gönderdiği doğrulandı; Listeler'deki hesap, kendi hesap ve tekrar tuşu da sınandı. Ana süreçte uygulamanın açık `Backend.command` sınırı yalnız sentetik yenileme kimliklerini izlemek için test sırasında gözlendi; üretime test IPC'si eklenmedi.
- Aynı EXE kontrolünde sentetik ilerleme olayıyla meşgul durum ve kayıtlı mola sırasında Ctrl+R'nin ikinci komut üretmediği; ilk kullanım ve okunmamış/arama boş durumlarındaki düğmelerin doğru yerel ekran/formu açtığı doğrulandı. Bildirim/otomatik kontrol kendiliğinden açılmadı. İngilizce palet ve yatay taşma kontrolü geçti.
- `npm run check:functional`: 30 mevcut paketlenmiş masaüstü kontrolü geçti. Yenileme denemelerinde test kipinin gerçek Instagram erişimini reddetmesi korundu; gerçek kullanıcı deposuna veya Başlat menüsüne yazılmadı. Testlerde dış arayüz isteği ve JavaScript hatası: 0.

Raporlar ve sentetik ekran görüntüleri yerel `artifacts/navigation-ui` altındadır. Paketleme sırasında C: dolduğu için ara kaynak paket yedekleri yerelde başka sürücüye taşındı; yayımlanmış sürümler değiştirilmedi.

İlk testte palet kaldırma zamanı form odağını etkiliyordu; paletin kendi kapatma/temizleme işlemi eşzamanlı tamamlanır hale getirildi. Hızlı programatik sekme geçişinden kalan hash olayı yeni paneli kapatabiliyordu; uygulama yönlendirmeyi hemen uygular ve zaten gösterilen sayfanın gecikmiş hash olayını yeniden yorumlamaz. Testte aynı eski hata mesajını yeni işlem tamamlanması sanan bekleme de yeni hata modalını gözleyecek şekilde düzeltildi.

Sınırlar: gerçek hesapla yenileme, yeni giriş/2FA, canlı Instagram erişimi ve uzun süreli çalışma bu UI değişikliğinde tekrar sınanmadı. Ctrl+R mevcut backend yenileme yolunu kullanır; başarılı canlı yanıt garantisi eklemez. Bu sürüm önizlemedir.
