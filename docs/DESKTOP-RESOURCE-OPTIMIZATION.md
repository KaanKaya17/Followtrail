# Windows kaynak kullanımı — 0.2.47

4 Kasım 2026. Kontrol aralıkları, istek sırası, molalar ve şifreli saklama kuralları değiştirilmeden üç iyileştirme yapıldı.

## Davranış

- Normal profil okuması sayaçları yine canlı yanıtla alır. Sayısal hesap kimliği ve bilinen fotoğraf kimliği önceki kayıtla aynıysa mevcut yerel JPEG kullanılır. Fotoğraf kimliği değişmişse/bilinmiyorsa veya görsel eksikse mevcut çerezsiz CDN okuyucusu çalışır. Dönen URL imzasının değişmesi tek başına aynı fotoğrafı yeniden indirtmez. Kullanıcılar/hesaplar arasında görsel önbelleği paylaşılmaz.
- Ana arayüzde bütün DOM'un değiştirilmesi kaldırıldı. Kimlikli satırlar, gezinme, sayaçlar ve form elemanları yerinde güncellenir; metin seçimi/odak korunur. Liste satırları, günlük grafik ve işlem panelinin içerikleri kendi denetleyicilerine aittir. Güncelleme gerçek DOM'u karşılaştırır; yerel arama gibi başka bir denetleyicinin yaptığı değişiklikleri eski HTML önbelleğiyle geçersiz saymaz.
- Olay ve bildirim geçmişinin tamamı her işlem sonucunda aktarılmaz. Düzenli durum yanıtı son beş olayın önizlemesi, toplamlar, okunmamış/tür sayıları ve geçmiş sürümünü taşır. Açılan geçmiş ekranı yerel IPC üzerinden 100 kayıtlık sayfalar alır. Liste farkları önizlemede her yönde en fazla iki kullanıcı adı taşır; eksiksiz eski/yeni değerler ve değişen kişiler ayrıntı açıldığında alınır. Yeni Windows bildirimi adaylarında da kısa kişi önizlemesi ve gerçek toplam korunur.
- Geçmiş sahibi yayınlanan görünümü dondurur; sayfa istekleri hesap ve sürümle doğrulanır. Hesap/filtre değişince önceki istekler geçersizleşir. Aynı hesap/filtrede yeni gözlem geldiğinde daha önce açılan sayfa aralığı yeniden alınır; eski satırlar hazır yenisi gelene kadar korunur. Pencere gizliyken gelen yerel sayfa yanıtı DOM'u güncellemez.

Kayıtlar silinmedi veya saklama süresi kısaltılmadı. Kasa biçimi değişmedi. Son 366 profil gözlemi grafikleri desteklemek için durum yanıtında bulunmaya devam eder; bu çalışma olay/bildirim geçmişinin aktarımını sayfaladı. Gerçek fotoğraf indirme/Instagram API istek sıklığı, canlı ağ üzerinden ölçülmedi. Yeni ağ hizmeti veya bağımlılık eklenmedi.

## Doğrulama

- 249 Python testi ve 65 Node testi geçti. Yeni kontroller bilinen/değişen/bilinmeyen fotoğraf kimliği, eksik görsel, taze sayaçlar, farklı kullanıcı kimliği; sayfalama sınırı, bütün geçmişe erişim, eksiksiz ayrıntı, hesap/sürüm reddi, dondurulmuş okunma işareti, boş ilk kurulum, geç yanıt ve açılmış sayfa aralığını kapsar.
- `check-resource-desktop.mjs`: büyük örnek kasayla 3 paketli senaryo grubu geçti. 2.000 olay ve 500 bildirim grubunun tamamına sayfalama ile erişildi; sıradan durumda yalnız beş olay taşındı. Gerçek sayaç elemanı/gezinme/grafik kökü ve odaklı arama alanı kimlikleri korundu. 200 kayda genişletilen akış yenilenince aynı kapsam ve ilk satır korundu. Başka hesap/sürüm istekleri reddedildi.
- `check-functional-desktop.mjs`: 36 mevcut Windows kontrolü geçti. `check-timeline-desktop.mjs` 5, `check-notifications-desktop.mjs` 5, `check-navigation-desktop.mjs` 11, `check-overview-desktop.mjs` 5 ve `check-profile-search.mjs` 12 senaryo grubu geçti. Geçmiş/bildirim testleri yeni sayfa/ayrıntı IPC'sini kullanır; bütün geçmişin değişmediğini de bu yoldan karşılaştırır.
- `npm run check` geçti; dört dilde 1150 metin eşleşir. Dokümantasyon görüntüleyicisi aynı üretim geçmiş sahibini bellekteki örneklerle kullanır; üç ekran görüntüsü sıfır harici istek ve sıfır JavaScript hatasıyla ayrı artifact klasörüne üretildi.

Testler ayrı `TrackerQA-*` kasaları, gerçek paketli EXE/worker/IPC ve Windows DPAPI kullandı. Gerçek kullanıcı kasası veya Instagram oturumu teste açılmadı. Android APK değiştirilmedi.

## Ölçüm yöntemi

`node scripts/profile-desktop.mjs 0.2.46 --large` ve aynı komutun 0.2.47 sürümü karşılaştırıldı. Her çalıştırma yeni bir test süreci açıp 3 saniye bekler. Örnek veri: kendi hesap dahil 24 profil, 20 profilde 60 geçmiş gözlemi, iki 10.000 kişilik liste, 2.000 olay ve 500 bildirim grubu. Sıralı aşamalar: açık pencere boşta, açık pencerede 40 şifreli not güncellemesi, gizli pencere boşta ve gizliyken 40 not güncellemesi.

Her aşama yaklaşık 20 saniye, süreç ailesi 500 ms aralıklarla örneklendi. CPU süresi çekirdek-saniyedir. Özel bellek ayrılmış özel baytların toplamıdır; fiziksel RAM'in birebir ölçümü değildir. Working-set toplamı paylaşılan sayfaları birden fazla sayabilir. Gözlemci ölçülen aile dışındadır. Dosya/işletim sistemi önbellekleri soğutulmadı; soğuk açılış ölçülmedi.

## Son karşılaştırma

- Düzenli durum gövdesi 817.555 → 252.914 bayt: %69,1 daha az yerel aktarım. Bu, Instagram internet trafiği ölçümü değildir.
- Açık pencerede 40 güncelleme 8.838 → 7.339 ms: %17,0 daha kısa. Örneklenen tepe özel bellek 427,9 → 342,6 MiB: %19,9 azalma. DOM child-list değişim kaydı 200 → 80; değişmeyen seçili elemanların kimlikleri ayrıca fonksiyonel testte doğrulandı.
- Gizliyken 40 güncelleme 7.699 → 6.850 ms: %11,0 daha kısa. Tepe özel bellek 397,7 → 285,3 MiB: %28,3 azalma. Gizli pencere DOM değişimi iki sürümde de sıfır.
- CPU açık güncelleme aşamasında 7,484 → 7,734 çekirdek-saniye (%3,3 artış), gizli güncellemede 5,125 → 4,266 çekirdek-saniye (%16,8 azalma) oldu. Ön çalıştırma ile tekrar arasında da dalgalanma görüldü. Bu nedenle genel veya sürekli CPU/pil tasarrufu yüzdesi iddia edilmiyor; doğrulanan kazanımlar yerel aktarım, tamamlanan görev süresi ve bu örneklerdeki bellek tepesidir.

Bu son tekrar 0.2.47 paketinde yapıldı; her aşamada yedi süreç izlendi. Ham çıktının `MB` adlı bellek alanları `/1MB` ile hesaplandığından yukarıda MiB olarak yazıldı. Sonuçlar tek makine ve kısa örneklerle sınırlıdır.

Ham sonuçlar `artifacts/desktop-0.2.46-qa/performance-large/` ve `artifacts/desktop-0.2.47-qa/performance-large/` altındadır ve Git'e eklenmez. Önceki sürümün durum boyutu ayrıca aynı örnek kasayla `--snapshot-only` çalıştırılarak ölçüldü. Bunlar kısa yerel örneklerdir; saatler süren bellek sızıntısı, farklı cihazlar, gerçek Instagram gecikmesi veya pil/elektrik tüketimi ölçümü değildir.
