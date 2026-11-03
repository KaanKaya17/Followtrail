# Windows profil paneli — 0.2.40

3 Kasım 2026. İlk arayüz aşaması yalnız Windows profil ayrıntı panelini kapsar.

Fotoğraf, ad ve kullanıcı adı üstte; bilinen takipçi/takip edilen/gönderi sayıları ayrı kartlarda gösterilir. Bilinmeyen alanlar gizlenir, gerçek sıfırlar korunur. Biyografi satırları ve mevcut Instagram/Threads/site bağlantıları korunur. İzleme ve favori tercihleri tek satırdadır. Profil kontrolü, takip listeleri ve deneysel hikâye arşivi doğrudan erişilebilir.

Geçmiş, profil zaman tüneli, bildirim tercihleri, görünürlük kontrolü ve kaldırma **Diğer** menüsündedir. Menü küçük pencerede mevcut alana göre yukarı açılır; Esc önce menüyü, sonra paneli kapatır. Kaldırma kırmızı onay gerektirir. Not/grup ve görünürlük kanıtları başlangıçta kapalıdır; **Kanıtları gör** bağlantısı ilgili bölümü doğrudan açar. Kaydedilmemiş liste profilleri ve kendi hesap paneli, kendileri için geçersiz kişi düzenleme araçlarını göstermez.

Yerel favori/izleme değişiklikleri yazılmamış not/grup taslağını korur. Geciken yerel kayıt yanıtı, kapatılmış paneli yeniden açmaz. Profil yanıtları yalnız bilgi alanını günceller; yazı alanını yeniden oluşturmaz.

## Doğrulama

Gerçek hesap deposu yerine ayrı `TrackerQA-*` dizinleri, sentetik profiller ve yerel JPEG kullanıldı. Paketli worker gerçek DPAPI ve IPC sınırından çalıştı. Bu değişiklik için canlı Instagram isteği gönderilmedi.

- `npm run check`: ortak tema, dört dilde 1113 eşleşen metin, yerel bağlantı simgeleri ve kaynak sözdizimi geçti.
- `node --test --test-reporter=dot desktop/*.test.cjs`: 61 Node testi geçti.
- Worker derlemesi ve 0.2.40 Windows paketi üretildi.
- `check-profile-panel-desktop.mjs`: 5 senaryo grubu; temel bilgiler, menü/Esc, taslak korunması/kayıt, geçmiş/kaldırma iptali, dört dil, koyu tema, 960×680 pencere ve hesap geçişi geçti.
- `check-profile-search.mjs`: 11 arayüz kontrolü; fotoğraf aktarımı, geciken profil yanıtı, biyografi satırları, HTML kaçışı, bağlantılar, bilinmeyen/sıfır alanlar ve kapanmış panelde geç yerel kayıt yanıtı geçti.
- `check-functional-desktop.mjs`: 36 genel paketli kontrol geçti.
- `check-accounts-desktop.mjs`: 10 çoklu hesap kontrolü geçti.
- `check-stories-desktop.mjs`: 7 hikâye arşivi kontrolü geçti.
- `check-daily-desktop.mjs`: 5 günlük geçmiş kontrolü geçti.
- `check-wayback.mjs`: 6 Windows/web arşiv senaryosu geçti.
- `check-navigation-desktop.mjs`: 11 klavye/ilk kullanım kontrolü geçti. Açık profilin Ctrl R hatası, mevcut satır içi durum alanından doğrulanır.
- `check-appearance.mjs`: 8 görünüm senaryo grubu geçti; tema, arşiv, dil ve dar pencere kontrolleri dahil.

Paketli profil ekran görüntüleri `artifacts/desktop-0.2.40-qa/profile-panel/` altında yereldir ve Git'e eklenmez. Android bu aşamada yeniden derlenmedi. Instagram erişim davranışı, giriş, istek aralığı ve bütçe mantığı değiştirilmedi; uçtan uca yeni canlı hesap doğrulaması yapılmadı. CPU/RAM veya önceki sürümle performans farkı ölçülmedi.
