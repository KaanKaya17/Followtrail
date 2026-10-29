# Anlaşılır gezinme — Windows/web 0.2.18 / Android 0.1.38

29 Ekim 2026. Kaydedilen profil alanının bütün takipçiler sanılmasını önlemek ve liste okumalarının neden beklediğini açıklamak için gezinme metinleri düzenlendi. Yeni Instagram veri kaynağı, hesap bağlantısı veya kontrol aralığı eklenmedi.

## Kullanım

- **Kaydedilen Profiller**, kullanıcının özellikle eklediği profilleri, favorilerini ve notlarını içerir. **Takip Listeleri**, seçili hesabın takipçi/takip edilen kayıtlarını içerir. Ekran başlıkları, menüler, arama paleti, ilgili erişilebilirlik metinleri ve Türkçe/İngilizce karşılıkları birlikte değişti. Android alt menüsü uzun başlık için iki satıra izin verir; web mobil menüsü kelime sınırından sarılır.
- Boş profil ekranı takipçilerin otomatik buraya eklenmediğini söyler. Profil ekle mevcut kayıt formunu açar. Takipçi listemi aç, kendi hesabının takipçi kayıtlarını istek göndermeden gösterir. Windows kendi hesap/yön/arama/karşılaştırma seçimlerini sıfırlar; bağlı hesap yoksa bu kısayol kapalıdır. Profil kaydetmek Instagram’da takip etme işlemi değildir.
- Windows ilk liste okuması Listeyi kontrol et, mevcut kayıt varsa Listeyi yeniden kontrol et olarak adlandırılır. Android devam sayfasında Listeyi okumaya devam et gösterir; bitmiş kayıt yeniden kontrol edilebilir. Mevcut manuel baştan başlama/devam sözleşmesi korunur.
- Liste alanındaki açıklama sayfalama ve istekler arasındaki molayı belirtir. Windows ilerleme alanı diğer ekranların kullanılabileceğini, yeni ağ işlerinin sıra beklediğini söyler. Var olan sayfa/hesap sayacı ve gerçek son tarihe dayanan geri sayım korunur. Toplam sayfa, kesin süre veya Instagram sınırlamalarına karşı güvenlik garantisi üretilmez. Android manuel liste ekranı sürekli arka plan görevi olarak sunulmaz.
- İlk bağlantıdaki otomatik hazırlık korunur. Bu metin düzenlemesi mevcut kapalı otomasyonu açmaz. Web örnek verili tasarım önizlemesi olarak kalır.

## Doğrulama

- `npm run check`: ortak temalar, JavaScript kaynakları ve 912 Türkçe/İngilizce mesaj eşleşti. `node --test desktop/*.test.cjs`: 28 test geçti.
- Paketlenmiş Windows EXE: 11 gezinme/onboarding, 30 işlev, 11 ilerleme ve 8 başlangıç hazırlığı kontrolü geçti. Gerçek IPC/DPAPI, izole sentetik hesaplar kullanıldı; Instagram isteği yoktu. Yeni kontrol boş Kaydedilen Profiller ekranından kendi takipçilerine geçişte hesap/yönü ve değişmeyen istek sayacını doğrular. Devam eden taramada sayaç/geri sayım ve gezinme açıklaması birlikte denetlenir.
- `check-i18n-web.mjs`: 10 tarayıcı kontrolü geçti. İki dilde ekranlar, boş profil alanı, çalışan takipçi kısayolu ve 320/390 px taşma denetimi; dış istek sıfır. Boş ekran yalnız test tarayıcısında örnek profil başlangıcı değiştirilerek oluşturulur; üretim önizlemesinin hesapları örnek olarak kalır. Mobil görünüm görsel olarak da incelendi.
- `scripts/check-locales.py`: 604 Android kaynağı, yer tutucular ve çoğullar eşleşti. `:app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:assembleDebug :app:assembleDebugAndroidTest`: 89 JVM testi, release lint ve derleme geçti. APK mevcut üretim anahtarıyla imzalandı; `verify-release.py` sürüm 0.1.38/kod 39, izin, imza ve paket denetiminden geçti. Debug, yedekleme ve cleartext kapalıdır.
- API 37 Small_Phone (4 GB RAM) emülatöründe PeopleScreenTest, FollowListFlowTest, ClarityFlowTest, InitialPreparationFlowTest, LanguageFlowTest ve AppearanceFlowTest: 40 test geçti (192,984 saniye). Yeni boş profil kısayolu, mevcut kişi düzenleme/silme/favori işlemleri, manuel liste devamı ve 429 koruması, otomatik başlangıç, iki dil ve üç tema kapsanır. Gerçek Activity/Keystore ve sentetik servis kullanılır. Yeni kısayol testi kendi hesap kimliği, FOLLOWERS yönü ve değişmeyen istek sayacını doğrular.

## Çalışma sırasında giderilen sorunlar ve sınırlar

- Windows sürüm metnini düzenleyen yerel betik varsayılan Windows kodlamasıyla Türkçe hata mesajlarını bozdu. İngilizce hata gösterimi regresyonu bunu yakaladı. Dosyanın önceki UTF-8 baytları Git'ten alınıp yalnız sürüm alanı değiştirildi; worker yeniden derlenip dosya hash'leri doğrulanarak yayımlanmamış pakete aktarıldı. Sonraki işlev/ilerleme/gezinme kontrolleri geçti. Hatalı paket yayımlanmadı.
- İlk Android 40 test turunda 14 diyalog denetimi başarısız oldu. İzole yeniden üretim ekran görüntüsünde emülatörün System UI yanıt vermiyor penceresinin uygulama diyaloğunu örttüğü görüldü. Bu pencere kapatılınca aynı dil testi geçti; ardından odaklı tur yeniden çalıştırıldı. Uygulama üretim koduna bu emülatör sorunu için çözüm eklenmedi.
- Canlı Instagram girişi, önceki Android 429 sorunu, fiziksel telefon, performans/bellek farkı ve bütün Android sürümleri bu değişiklikte doğrulanmadı. Testler API 37 emülatörü ve sentetik servislerle sınırlıdır. Kullanıcı kayıtları veya oturumları test için kullanılmadı. Windows dağıtımı imzasız x64 önizlemesidir; genel kullanıma hazır beyanı değildir.

Windows'ta eski uygulamayı tepsi → Tamamen çık ile kapatıp ZIP'i bütünüyle çıkararak yeni EXE açılır. Android mevcut APK üzerine güncellenebilir. Sırf bu güncelleme için hesaptan çıkış veya yeniden parola girişi gerekmez. Önceki yayımlanmış paketler korunur.
