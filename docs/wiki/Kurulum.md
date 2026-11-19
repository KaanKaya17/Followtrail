# Kurulum ve Güncelleme

[Kılavuz ana sayfası](Home.md) · [İlk kullanım](Ilk-Kullanim.md)

## Paketi seçin

**Windows:** Windows x64 için taşınabilir masaüstü paketi kullanılır. Son kullanıcı bilgisayarına ayrıca Python kurulması gerekmez.

**Android:** Android 7.0 / API 24 ve üzeri için imzalı APK kullanılır. Telefon modeli ve Instagram'ın istediği doğrulama adımları bağlantı sonucunu etkileyebilir. [Android bağlantısının güncel durumu](Surum-ve-Destek.md#android).

**Web:** Kurulum gerektirmeyen örnek veri önizlemesidir. Gerçek hesaba bağlanıp sürekli kontrol yapan bir hizmet olarak sunulmaz.

Güncel ZIP ve APK bağlantıları [sürüm sayfasındadır](Surum-ve-Destek.md#kurulum-dosyasını-nereden-alabilirim). GitHub’da özel depoya erişimi olan hesabınızla oturum açın.

## Windows kurulumu

1. ZIP paketini bilgisayarınızdaki bir klasöre **tamamen çıkarın**.
2. Çıkarılan klasördeki `Followtrail.exe` dosyasını açın.
3. EXE'nin yanındaki `resources` ve diğer paket dosyalarını aynı klasörde tutun.
4. ZIP’inizi incelemek için [Instagram Veri Arşivi rehberini](Instagram-Veri-Arsivi.md), canlı takip için [hesap bağlama adımlarını](Hesaplar-ve-Giris.md#windows) izleyin.

EXE'yi ZIP içinden çalıştırmak veya tek başına başka klasöre taşımak yerel veri hizmetinin başlamasını engelleyebilir. Mevcut Windows önizlemesi kod imzalı bir yükleyici değildir; kaynak ve sürüm bilgisi doğrulanmamış bir dosyayı çalıştırmayın.

## Android kurulumu

1. [Sürüm sayfasındaki](Surum-ve-Destek.md#kurulum-dosyasını-nereden-alabilirim) imzalı APK'yı telefona indirin veya aktarın.
2. Dosyayı açın. Android isterse yalnız kullandığınız dosya yöneticisi veya tarayıcı için bu kuruluma izin verin.
3. Uygulamayı açın. ZIP için sürümünüzün desteklediği [takip listesi içe aktarma yolunu](Takip-Listeleri.md#instagram-zip-arşivi), canlı takip için [Android bağlantı adımlarını](Hesaplar-ve-Giris.md#android) kullanın.
4. Bildirim kullanmak istiyorsanız uygulamanın ve Android'in bildirim iznini etkinleştirin.

Menü adları telefon üreticisine göre değişebilir. USB hata ayıklama, emülatör veya bilgisayar bağlantısı normal APK kurulumu için gerekli değildir.

## Kayıtları koruyarak güncelleme

### Windows

1. Tepsideki Followtrail simgesine sağ tıklayıp **Tamamen çık** seçeneğini kullanın.
2. Yeni ZIP'i ayrı bir sürüm klasörüne çıkarın.
3. Yeni klasördeki EXE'yi açın. Mevcut şifreli kayıtlar aynı Windows kullanıcı bağlamından yüklenir.

Farklı sürümleri aynı veri deposuyla eşzamanlı çalıştırmayın. Yeni sürüm daha geniş kayıt biçimi kullanıyorsa eski EXE'ye dönmek desteklenmeyebilir. Uygulama klasörünü silmek ile [uygulamanın yerel verilerini silmek](Gizlilik-ve-Veriler.md#silme-ve-baglanti-kaldirma) farklı işlemlerdir.

### Android

Aynı uygulamanın, aynı imza anahtarıyla hazırlanmış daha yeni APK'sını mevcut sürümün üzerine kurun. **Önceki uygulamayı kaldırmayın ve uygulama verilerini temizlemeyin.** Bunlar cihazdaki şifreli kayıtları kaybettirebilir.

Android imza uyuşmazlığı veya sürüm düşürme hatası verirse işlemi durdurup paket kaynağını kontrol edin. Güncelleme mevcut Instagram molasını kaldırmaz.

## İlk açılış kontrolü

- Arayüz ve menüler açılıyor mu?
- Güncellemeyse kayıtlı hesabınız ve kişileriniz görünüyor mu?
- Sürüm, beklediğiniz paketle aynı mı?
- Bir bekleme veya bağlantı açıklaması varsa [sorun giderme adımı](Sorun-Giderme.md) anlaşılır mı?

Sonraki adım: [İlk kullanım](Ilk-Kullanim.md).
