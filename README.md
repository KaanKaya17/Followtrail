# InstagramTracker

Yerel Android uygulaması. İlk sürüm yalnız kendi hesabının takipçi ve takip edilen sayılarını gösterir.

Geliştirme sürümü 0.1.0. Canlı Android hesap testi tamamlanana kadar girişin veya sayı okumanın çalıştığı iddia edilmez.

## Temel kararlar
- Native Kotlin/Android arayüzü; Python, sunucu, reklam, analitik veya çökme toplama servisi yok.
- Android 6.0+ (API 23), işlemciye özel native kütüphane yok. Minimum sürüm desteği test kapsamıyla aynı şey değildir.
- İlk bağlantı uygulama içindeki Instagram web giriş sayfasında. Parola uygulama kodunca okunmaz. Sonraki açılışlarda oturumla API okuma hedeflenir.
- Oturum AES-GCM / Android Keystore ile cihazda şifrelenir. Yedekleme ve cihaz aktarımı kapalıdır.
- Ağ istekleri Instagram'a gider; kullanıcı verisinin hiç ağdan çıkmadığı iddia edilmez.

Bu ürün Meta/Instagram tarafından sunulmaz veya desteklenmez. Kullanılan kişisel hesap uçları resmî geliştirici API sözleşmesi değildir ve değişebilir.
