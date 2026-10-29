# Sade kullanıcı dili — Windows/web 0.2.19 / Android 0.1.39

29 Ekim 2026. Kullanıcının teknik sözlük bilmeden sonuçları okuyabilmesi için ana ekran, liste, profil durumu ve ilerleme metinleri Türkçe/İngilizce birlikte düzenlendi. Ağ okuyucusu, kontrol zamanlayıcısı, hesap oturumu ve istek bütçesinde davranış değişikliği yoktur.

## Görünen davranış

- Gözlem zamanı ve son gözlem yerine Son kontrol; Kayıt kapsamı yerine Neler kontrol edildi?; kısmi liste yerine Listenin tamamı henüz okunmadı kullanılır. Sayaçlar, grafikler ve geçmiş etiketleri de kontrol dili kullanır.
- Normal istek arası Kısa mola olarak anlatılır. Mevcut son tarihten kalan süre gösterilir; yeni zaman uydurulmaz. Instagram sınırlaması ayrı olarak Instagram istekleri sınırladı der. Mola erişim garantisi veya ban koruması olarak sunulmaz; mevcut otomatik tekrar durdurması korunur.
- Windows hata sunumu, bilinen hata kodundan günlük dilde açıklama üretir. `limited`, `cooldown`, liste eksikliği/sayı uyuşmazlığı ve hesap bağlantısının doğrulanamaması ayrı nedenlerdir. Bilinmeyen hata nedeni başka bir hataya çevrilmez. Hata arşivi ve protokol çıktıları değiştirilmez.
- HTTP kodları, Retry-After, istek sayacı ve özgün hata metni İstek bilgileri altında kalır. Hata penceresi ve profil kontrolü ayrıntıları teknik bilgiyi katlanır Teknik ayrıntılar alanında sunar. Ana ekrandaki eski hata etkinliği Kontrol tamamlanamadı başlığı gösterir; aynı geçmiş kaydında özgün tanı açılabilir.
- Yeni görülen takipçiler ve Son kontrolde görünmeyenler kesin takipten çıkış listesi olarak sunulmaz. Takip edilenler yönündeki karşılaştırma Yeni takip edilen hesaplar der. Geri takip filtresinin adı liste yönüne göre Seni geri takip etmeyenler veya Senin geri takip etmediklerin olur; kaydedilmiş iki listeye dayandığı ve zamanlarının farklı olabileceği açıklanır. Liste karşılaştırmasının mevcut tamamlama koşulları değişmedi.
- Android son sayfaya ulaşılmasını, sayısı doğrulanmış tamamlanmış bir liste veya kesin sosyal olay olarak yeniden yorumlamaz. Teknik giriş deneyi ve İstek bilgileri tanıları korunur. Web hâlâ örnek verili önizlemedir; yeni gerçek hesap bağlantısı yoktur.

## Doğrulama

- `npm run check`: 999 Türkçe/İngilizce mesaj, ortak tema ve JavaScript kaynak denetimi geçti. Node testleri 28/28 geçti. Android `check-locales.py`: 604 kaynak, yer tutucular ve çoğullar eşleşti.
- Paketlenmiş Windows EXE'de 5 yeni sade dil kontrolü, 30 işlev, 11 ilerleme, 11 gezinme ve 8 başlangıç hazırlığı kontrolü geçti. Son çeviri kataloğuyla worker yeniden derlendi; son paket üzerinde 5 yeni kontrol ile 30 işlev/11 ilerleme yeniden geçti. Paketlenmiş worker ve kaynak katalog byte/hash eşleşmesi denetlendi.
- Yeni sade dil testleri gerçek EXE/IPC/DPAPI ile izole sentetik hesap kullanır. Sunucu sınırlaması UI durumuna yansıtılır; gerçek 429 istenmez. Ana ekran ve uyarıda HTTP/429 görünmemesi, özgün mesajın İstek bilgileri ve hata etkinliğinde açılması, İngilizceye geçerken tanı metninin korunması, sıfır dış istek ve script hatası denetlenir. Profil HTTP kanıtının yalnız Teknik ayrıntılar açılınca görünmesi mevcut işlev testine eklendi.
- Web tarayıcı denetimi 10/10 geçti: iki dil, beş ekran, boş profil yönlendirmesi ve 320/390 px taşma kontrolü. Dış istek sıfırdı. Yeni Windows uyarı ekranı görsel olarak incelendi.
- `:app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:assembleDebug :app:assembleDebugAndroidTest`: 89 JVM testi, release lint ve derleme geçti. Android 0.1.39/kod 40 mevcut üretim anahtarıyla imzalandı; `verify-release.py` paket, izin ve imza denetimi geçti. Debug, yedekleme ve cleartext kapalıdır.
- API 37 Small_Phone (4 GB RAM) emülatöründe ClarityFlowTest, FollowListFlowTest, InitialPreparationFlowTest, LanguageFlowTest, ProfileStatusFlowTest, AutomaticFlowTest ve ScreenFlowTest çalıştırıldı. İlk turda 50 test geçti, aşağıdaki iki eski metin beklentisi başarısız oldu. Test APK yeniden derlenip yalnız bu iki test yeniden çalıştırıldı ve 2/2 geçti (7,931 saniye). Toplam 52 farklı senaryo doğrulandı; son kaynakla 52 testin tek seferde yeniden çalıştırıldığı iddia edilmez. Gerçek Keystore/Activity/JobScheduler ve sentetik servisler kullanıldı. Ana ekranın HTTP göstermemesi, kayıtların korunması, liste devamı, bildirimler, iki dil, 429 durdurması ve otomatik hazırlık kapsanır.

## Test düzeltmeleri ve sınırlar

Windows işlev testi daha önce 404/200 kanıtını doğrudan görünür bekliyordu; şimdi önce gizli olduğunu, Teknik ayrıntılar açılınca aynı kanıtın göründüğünü doğrular. Android ilk 52 test turunda iki eski metin beklentisi başarısız oldu: İngilizce sınırlama mesajının eski başlangıcı ve özet satırı yerine ayrıntı başlığının beklenmesi. Beklentiler yeni metne ve doğru kaynak sahibine göre düzeltildi. Üretim davranışı bu testler için değiştirilmedi.

Gerçek Instagram girişi, önceki Android giriş/429 sorununun çözümü, fiziksel telefon, bütün Android sürümleri ve performans/bellek farkı doğrulanmadı. Hedef HTTP yanıtları ve hesaplar sentetiktir; kullanıcı oturumu/verisi test amacıyla kullanılmadı. Windows imzasız x64 önizlemesidir. APK mevcut uygulamanın üzerine güncellenir; Windows'ta eski uygulama tepsiden tamamen kapatılıp ZIP bütünüyle çıkarılır. Sırf metin güncellemesi için yeniden giriş gerekmez.
