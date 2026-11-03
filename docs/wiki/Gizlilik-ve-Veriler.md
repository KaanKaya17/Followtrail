# Gizlilik ve Veriler

[Kılavuz ana sayfası](Home.md) · [Güvenlik bildirimi](../../SECURITY.md)

## Cihazda saklama

Windows kayıtları Windows kullanıcısına bağlı **DPAPI**, Android kayıtları **Android Keystore ve AES-GCM** ile korunur. Bağlı hesapların oturumları, kaydedilen profiller ve gözlemler yerel şifreli depolardadır. Çoklu hesap kayıtları birbirinden ayrılır.

Bu şifreleme uygulamanın kendi bulut yedeği veya cihazlar arası otomatik eşitleme hizmeti olduğu anlamına gelmez. Android'in uygulama yedekleme ve cihaz aktarımı bu kayıtlar için kapalıdır. Uygulama verilerinin temizlenmesi veya anahtarın kaybı, kayıtları okunamaz hale getirebilir.

## Hangi bağlantılar kurulur?

- Canlı giriş ve veri okumaları Instagram/Meta altyapısına gider.
- Profil fotoğrafları gerektiğinde izinli Meta CDN adreslerinden yüklenir.
- Güvenlik sayfası, kullanıcının onayına bağlı olarak Meta/Google CAPTCHA kaynaklarına ihtiyaç duyabilir.
- Bir site veya sosyal bağlantıya tıklarsanız normal tarayıcı o adrese gider; tarayıcının kendi gizlilik ve oturum davranışı geçerlidir.

Uygulamada kendi veri sunucusu, reklam, analitik veya çökme toplama SDK'sı bulunmaz. “Yerel saklama”, Instagram'dan veri alınırken hiç ağ bağlantısı kurulmadığı anlamına gelmez.

Marka ikonları paket içindedir; site logosu/favicon veya başlık almak için üçüncü taraf hizmete sorgu gönderilmez.

## Yalnız okuma davranışı

Uygulama takip etme, takipten çıkma, beğeni ve DM gönderme özelliği sunmaz. Okuyucudaki izin verilmeyen yöntem/yol birleşimleri gönderimden önce durdurulur.

Giriş, cihaz bağlamı ve desteklenen güvenlik doğrulamaları ayrı dar izinlere sahiptir; bu nedenle bütün ağ isteklerinin yalnız GET olduğu söylenmez. Ayrıntılı sözleşmeler [teknik gizlilik notlarında](../PRIVACY.md) bulunur.

## Parola, kod ve oturum

Parola ve doğrulama kodu ek bir günlük/kasa kaydı olarak tutulmaz. Girişten doğan oturum, veri okumaya devam edebilmek için şifreli saklanabilir. Oturum çerezi de parolayla aynı özenle korunması gereken bir erişim bilgisidir.

**İstek bilgileri**, sınırlı sonuç ve hata olgularını gösterir; ham parola, kod, çerez veya yanıt gövdesi tanı kaydına yazılmaz. Buna rağmen destek için ekran görüntüsü paylaşmadan önce kullanıcı adı, not ve diğer kişisel alanları kontrol edin.

## Günlük sayaç arşivi

Günlük özetler, yakın gözlemlerden ayrı şifreli profil/yıl dosyalarında tutulur. Süreye bağlı otomatik silme yoktur. Günlük geçmiş ekranından hedefe özel temizleme yapılabilir; hesabın yerel verilerini silmek kendi arşivini de siler. [Günlük istatistikler](Gunluk-Istatistikler.md).

## Fotoğraflar

Arama/liste küçük resimleri süre ve adet sınırları olan bellek önbelleklerini kullanır. Profil kayıtlarında kaynak görsel adresi bulunabilir. İsteğe bağlı avatar zaman tüneli, bu geçici küçük resimlerden farklı olarak görselleri yerel şifreli arşivde tutar.

## Yedek ve dışa aktarma

Windows'taki **Şifreli yedek al**, gözlem verilerini dışa aktarır; Instagram oturumunu çıkarır. Bu dosya aynı Windows kullanıcı bağlamında açılmak üzere DPAPI ile korunur. Başka bilgisayarda veya Windows yeniden kurulduktan sonra açılabilen taşınabilir, parola tabanlı yedek olarak değerlendirmeyin.

Günlük özetler de bu yedeğe dahildir. Geri yüklemede çakışan günlerin daha yeni olanı korunur; mevcut temizleme sınırları aşılmaz. Yaklaşık 64 MiB tek yedek sınırı aşılırsa dosya üretilmez; önceki dosya korunur.

Geri yükleme oturumu veya otomatik kontrolü kendiliğinden etkinleştirmez. Kayıtlı beklemeler kaldırılmaz. Mevcut hesapla yedeğin hesap kapsamı uyuşmuyorsa işlem reddedilebilir.

**CSV şifreli değildir.** İçeriği başka uygulamalar okuyabilir; dosyayı nerede tuttuğunuz ve kiminle paylaştığınız sizin kontrolünüzdedir. Yedek/CSV açıklaması Windows özelliğidir; Android'de aynı dışa aktarma kapsamı varsayılmamalıdır.

<a name="silme-ve-baglanti-kaldirma"></a>

## Silme ve bağlantı kaldırma

Bağlantı kaldırma, uygulamadaki yerel oturum kopyasını yönetir. Instagram sunucusundaki oturumu kapattığını varsaymayın. Gerekirse resmî Instagram güvenlik/giriş hareketleri ekranını kendiniz inceleyin.

Seçili hesabı kaldırmak ile bütün yerel kayıtları temizlemek farklı kapsamlardır; onay metnini okuyun. Yerel veriyi silmek mevcut güvenlik molasını sıfırlamak için kullanılmaz.

Güvenlik açığı bildirimi için [güvenlik politikasını](../../SECURITY.md) izleyin; parola, kod, çerez ve anahtarları issue'ya eklemeyin.
