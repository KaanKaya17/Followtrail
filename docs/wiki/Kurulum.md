# Kurulum ve Güncelleme

[Kılavuz ana sayfası](Home.md) · [İlk kullanım](Ilk-Kullanim.md)

## Paketi seçin

**Windows:** Windows x64 için taşınabilir masaüstü paketi kullanılır. Son kullanıcı bilgisayarına ayrıca Python kurulması gerekmez.

**Android:** Android 7.0 / API 24 ve üzeri için imzalı APK kullanılır. Telefon modeli ve Instagram'ın istediği doğrulama adımları bağlantı sonucunu etkileyebilir. [Android bağlantısının güncel durumu](Surum-ve-Destek.md#android).

**Web:** Kurulum gerektirmeyen örnek veri önizlemesidir. Gerçek hesaba bağlanıp sürekli kontrol yapan bir hizmet olarak sunulmaz.

Güncel sürüm numaraları ve dosya adları [sürüm sayfasındadır](Surum-ve-Destek.md). Kaynak etiketi tek başına indirilebilir APK/EXE anlamına gelmez; proje sahibinin paylaştığı dağıtım paketini kullanın.

## Windows kurulumu

1. ZIP paketini bilgisayarınızdaki bir klasöre **tamamen çıkarın**.
2. Çıkarılan klasördeki `InstagramTracker.exe` dosyasını açın.
3. EXE'nin yanındaki `resources` ve diğer paket dosyalarını aynı klasörde tutun.
4. Açılıştan sonra [hesap bağlama adımına](Hesaplar-ve-Giris.md#windows) geçin.

EXE'yi ZIP içinden çalıştırmak veya tek başına başka klasöre taşımak yerel veri hizmetinin başlamasını engelleyebilir. Mevcut Windows önizlemesi kod imzalı bir yükleyici değildir; kaynak ve sürüm bilgisi doğrulanmamış bir dosyayı çalıştırmayın.

## Android kurulumu

1. Proje sahibinin paylaştığı imzalı APK'yı telefona aktarın.
2. Dosyayı açın. Android isterse yalnız kullandığınız dosya yöneticisi veya tarayıcı için bu kuruluma izin verin.
3. Uygulamayı açıp [Android bağlantı adımlarını](Hesaplar-ve-Giris.md#android) izleyin.
4. Bildirim kullanmak istiyorsanız uygulamanın ve Android'in bildirim iznini etkinleştirin.

Menü adları telefon üreticisine göre değişebilir. USB hata ayıklama, emülatör veya bilgisayar bağlantısı normal APK kurulumu için gerekli değildir.

## Kayıtları koruyarak güncelleme

### Windows

1. Tepsideki Tracker simgesine sağ tıklayıp **Tamamen çık** seçeneğini kullanın.
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
