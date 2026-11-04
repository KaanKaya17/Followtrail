# Güvenlik bildirimleri

Parola, doğrulama kodu, oturum çerezi, Keystore/imza anahtarı veya gerçek hesap yanıtını issue, PR, log veya ekran görüntüsüne eklemeyin. Gerçek kullanıcı verisi içeren test dosyası oluşturmayın.

Bir güvenlik açığı bulursanız GitHub Security sekmesinde **Report a vulnerability** seçeneği açıksa özel bildirim kullanın. Açık değilse, ayrıntıları yayımlamadan yalnızca özel iletişim kanalı istediğinizi belirten kısa bir issue açın. Bildirimi yöneten kişi tekrar üretim için sentetik veri istemelidir.

İlk geliştirici önizlemesi henüz genel kullanıma hazır sayılmıyor. Otomatik test, lint ve Keystore kullanımı bağımsız penetrasyon testinin veya gerçek hesap uyumluluğunun yerine geçmez. [Veri sınırları](docs/PRIVACY.md) ve [doğrulama durumu](docs/VALIDATION.md) açık tutulur.

## Olay müdahalesi

Bildirim alındığında önce etkilenen sürüm, veri türü ve yerel/ağ kapsamı belirlenir. Gerçek kullanıcı dosyası veya erişim bilgisi genel issue'ya taşınmaz; yeniden üretim için sentetik kayıt tercih edilir. Etkilenen dağıtım gerekiyorsa durdurulur, erişim bilgileri yalnız gerçek sızıntı kapsamı doğrulandıktan sonra sahibi tarafından yenilenir. Kaynak geçmişi kanıtı yok etmek amacıyla kendiliğinden yeniden yazılmaz.

Teknik bulgu, etkilenen kişiler ve düzeltme açık biçimde kayda alınır. Veri sorumlusu rolü, ilgili kişiler/Kurul veya başka yetkili mercilere bildirim gereği ve süreleri somut olaya göre hukuki danışmanla değerlendirilir. Düzeltme, sentetik regresyon ve paket kontrolüyle doğrulanır; gerçekleşmeyen canlı test yapılmış gibi bildirilmez.

[Veri envanteri ve saklama/silme kapsamı](docs/DATA-LIFECYCLE.md), ürünün teknik denetim kaydıdır; bağımsız güvenlik denetimi veya hukuki uygunluk belgesi değildir.
