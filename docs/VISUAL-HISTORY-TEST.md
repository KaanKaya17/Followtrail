# Profil zaman tüneli doğrulaması — 2026-10-28

Kapsam: Windows/web 0.2.7 ve Android 0.1.31. Testler sentetik profiller ve üretilmiş küçük JPEG'lerle yapıldı; gerçek hesap deposu ve Instagram kullanılmadı.

- Windows: 97 backend testi geçti. Gerçek Windows DPAPI, eski depo geçişi, şifreli yeniden açılış/yedek aktarımı, duraklatma/temizleme, otomatik önceki kopyanın temizlenmesi, eksik biyografi ile boş biyografinin ayrılması, aynı görseli tek kopyada tutma, indirilemeyen yeni fotoğrafın eski fotoğrafla karıştırılmaması ve kapasite davranışı sınandı. 380 sayaç gözlemi sonrası dönen geçmiş 366'ya inerken değişiklik arşivinin korunduğu doğrulandı. Silinen kişilerin arşivleri toplam sınırı doldurursa yeni arşiv açılması kaydı bozmadan reddedilir; eski arşivi temizlemek yer açar.
- JavaScript: 21 test geçti; Unicode/boşluk/URL'lerin eski ve yeni metinde aynen korunması, uzun metinde sınırlı fark hesabı ve HTML içeren biyografinin metin olarak gösterilmesi dahil.
- Paketlenmiş Windows EXE: 30 mevcut işlev kontrolü ve 6 yeni galeri/kalıcılık/dil/temizleme/dar web görünümü kontrolü geçti. Gerçek paketlenmiş worker, üç JPEG, biyografi farkları ve süreç yeniden açılışı kullanıldı. Tarayıcı önizlemesinin 8 iki dilli kontrolü geçti. Yakalanan dış arayüz isteği ve JavaScript hatası: 0.
- Android: 84 birim testi, release lint ve debug/release derlemeleri geçti. Android 15 / TrackerQA emülatöründe yalnız `VisualHistoryScreenTest` çalıştırıldı: Keystore kaydında düz biyografi bulunmadığı, iki fotoğraf ve fark işaretleri, durdur/aç tercihleri ve Activity yeniden oluşturulması doğrulandı. İstek deneme sayısı değişmedi. Bu, bütün Android instrumentasyon paketinin yeniden çalıştırıldığı anlamına gelmez.
- İmzalı APK manifest kontrolü: 0.1.31 / kod 32; debug, yedekleme ve cleartext kapalı; dört mevcut izin; yeni izin veya native kütüphane yok. APK aynı yerel üretim anahtarıyla imzalandı.

Kaynak kontrol komutları:

```powershell
# interface/desktop/backend içinde, mevcut kilitli Python ortamıyla:
python -m unittest discover -p 'test_*.py'
# interface içinde:
npm run check
node --test desktop/*.test.cjs
node scripts/check-wayback.mjs
npm run check:functional
node scripts/check-i18n-web.mjs
# Depo kökünde, JDK 21 / SDK 36:
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.kaankaya.instagramtracker.VisualHistoryScreenTest
```

Rapor ve ekran görüntüleri yerel `artifacts/wayback-ui`, backend/Android günlükleri ve Gradle test raporlarındadır; kullanıcı verisi veya binary kaynak deposuna eklenmez. İlk paketleme denemesinde makinenin kullanılabilir belleği Java imzalayıcısını başlatmaya yetmedi; kullanılmayan Gradle daemon'u durdurulup imzalayıcı 128 MiB heap ile yeniden çalıştırıldı ve imza doğrulaması geçti.

Sınırlar: canlı fotoğraf değişimi, gerçek telefonda yeni APK kurulumu, uzun süreli kullanım ve yeni Instagram giriş kabulü bu değişiklikte sınanmadı. Fotoğrafın geçmişte indirilmemiş sürümleri yeniden oluşturulamaz. Android galeriye normal kullanımda yüklenen fotoğrafları ekler. Tarihler gözlem zamanıdır. Dönen sayaç geçmişi için günlük/aylık kalıcı özet bu değişiklikte eklenmedi. Bu sürümler önizlemedir.
