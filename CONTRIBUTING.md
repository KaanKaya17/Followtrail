# Geliştirme

JDK 21, SDK platform 36 ve build-tools 36.0.0 kullanın. Gradle wrapper sürümü ve SHA-256 değeri repoda sabitlenmiştir. Yerel SDK yolu, imza anahtarı, oturum, APK ve kullanıcı verileri git'e eklenmez. Android bağımlılık sürümleri `app/gradle.lockfile` ile kilitlidir; bağımlılık değişince ilgili kontrol görevlerini `--write-locks` ile çalıştırıp oluşan farkı inceleyin.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
python3 scripts/verify-release.py app/build/outputs/apk/release/app-release-unsigned.apk
./gradlew :app:connectedDebugAndroidTest
```

Windows'ta `gradlew.bat` kullanın. Python 3.11+ yalnız paket doğrulama script'i için gerekir; Android uygulaması Python içermez.

Instrumentasyon testleri **ayrı `dev.kaankaya.instagramtracker.debug` paketinde**, sentetik veriyle çalışır. Emulator veya yalnız test için ayrılmış cihaz kullanın: testler debug uygulamasının test/oturum verisini oluşturup siler. Gerçek hesapla giriş yapmayın. Ağ testleri HttpsURLConnection sınırında sahte taşıyıcı kullanır; Instagram'a istek göndermez. Bu, gerçek ağ/TLS ve Instagram sunucusu testi değildir.

Davranış değiştiren katkılar ilgili üretim sınırında doğrulanmalıdır. Eksik sayaç sıfıra dönüştürülmez; yanlış hesap kimliği kabul edilmez; zaman aşımı ve iptal başarısızlıkları arayüzü beklemede bırakmaz. Kullanıcı doğrulamasını, sunucu sınırlamasını ve TLS hatasını aşan akışlar eklenmez.

Commit mesajları `feat:`, `fix:`, `test:`, `ci:` gibi Conventional Commit biçiminde olmalıdır. Değişiklik kaydı ve doğrulama kaydı gerçek yapılan kontrolleri yansıtmalıdır. İlgili CI geçmeden veya açık kalan kapsam yazılmadan yayın hazır iddiası kurulmaz. Kodun lisansı seçilmedi; bu çalışma üçüncü taraflara yeni bir lisans hakkı atamaz.
