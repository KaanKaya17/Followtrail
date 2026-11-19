# Sorun Giderme

[Kılavuz ana sayfası](Home.md) · [Destek bilgileri](Surum-ve-Destek.md#destek-icin-hangi-bilgiler-gerekir)

Önce ekrandaki güncel açıklamayı okuyun. Aynı işlemi tekrar tekrar başlatmak bağlantı veya liste sorununu açıklamayı zorlaştırır.

## Otomatik okuma duraklatıldı

Windows ve Android, **İstek bilgileri → Okuma tanısı** bölümünde profil, takipçi ve takip edilen okumalarının nedenini ayrı gösterir. Yanıtın beklenen biçime uymaması veya aynı yolun art arda reddedilmesi gibi üç uyumluluk hatasında ilgili otomatik okuma durur. Uç noktanın değiştiği doğrulanana kadar bu durum olası neden olarak gösterilir. Tek hedefin bulunamaması, eksik liste ve HTTP 429 ayrı durumlar olarak ele alınır.

Windows’ta bir okuma bu nedenle durduğunda bir kez Windows bildirimi gelir (genel bildirimler açıksa). Ana ekrandaki uyarı eski kayıtların korunduğunu ve aynı hatalı isteğin yeniden gönderilmediğini söyler. Yanıt biçimi değiştiyse **Okuma tanısı** eksik alan adlarını da gösterir (ör. `users.pk`).

**Geliştiriciye bildir**, değer içermeyen kısa bir rapor gösterir: uygulama sürümü, okuma türü, istek yolu şablonu (`{id}` ile), neden, HTTP durumu, eksik alan adları ve hata sayısı. Kullanıcı adı, hesap kimliği, çerez veya yanıt içeriği eklenmez. **Metni kopyala** raporu panoya alır. **GitHub’da bildir** herkese açık yeni bir konu taslağı açar; göndermeden önce metni görüp değiştirebilirsiniz. Uygulama raporu kendiliğinden hiçbir yere göndermez.

Devam isteği 400 veya 404 alırsa sonraki uygun tarama ilk sayfadan başlar. Son tam kayıt karşılaştırma için saklanır. Duraklatılan okuma elle başarıyla tamamlandığında otomatik kontroller o okuma için devam eder. Windows’ta profil okuması durduysa yeni liste başlangıçları da taze profil bilgisi alana kadar bekler. Profil bilgisi hazır olan devam taramaları sırasını kullanabilir.

## Giriş kabul edilmiyor

Aynı kullanıcı adı ve parolayla normal Instagram'da sıfırdan giriş yapabildiğinizi ayırın. Kayıtlı bir oturumun hâlâ açık olması bu doğrulamayı sağlamaz. Normal Instagram da bilgileri reddediyorsa önce hesap bilgilerini oradan doğrulayın.

Normal giriş çalıştığı halde Tracker ilerlemiyorsa sürümü ve **İstek bilgileri** sonucunu paylaşın. Parola veya kod paylaşmayın. HTTP 200, tek başına başarılı oturum veya tamamlanmış profil bağlantısı anlamına gelmez.

## Kod veya CAPTCHA ekranında kalıyor

Doğrulama, açık Instagram sayfasında kullanıcı tarafından tamamlanır. Bir kodu bir kez gönderip sonucu bekleyin. Uygulama desteklenmeyen yöntem açıklıyorsa aynı giriş formunda farklı hesapları art arda denemeyin.

Android'de desteklenen kod adımı ile son profil doğrulaması ayrı aşamalardır. **Instagram oturumu açıldı** ardından **Bağlantıyı tamamla** görülürse [bekleyen bağlantı adımlarını](Hesaplar-ve-Giris.md#bekleyen-baglantiyi-tamamlamak) izleyin.

<a name="instagram-guvenlik-molasi-veya-http-429"></a>

## Instagram güvenlik molası veya HTTP 429

429, bu isteğin sınırlama yanıtı aldığını gösterir; nedeni yalnız bu koddan belirlenemez. Uygulama sonraki denemeyi kayıtlı bir bekleme zamanı ile sınırlar.

1. Yeni giriş, yenileme veya liste taraması başlatmayı durdurun.
2. **İstek bilgileri** içindeki son deneme saatini ve HTTP durumunu kontrol edin.
3. Ekrandaki yerel bekleme zamanını esas alın.
4. Süre dolduğunda ekrandaki uygun işlemi bir kez başlatın.

İlk yerel mola en az bir saattir; tekrarlarında uzayabilir. Sunucunun daha uzun bekleme bilgisi korunur. Gösterilen zaman Instagram'ın isteği o saatte kesin kabul edeceği sözü değildir.

Hesap geçişi, güncelleme, uygulamayı yeniden açma veya veri silme bu beklemeyi kaldırma yöntemi değildir. Eski bir 429 kaydının görünmesi de yeni bir 429 alındığı anlamına gelmez; son deneme saatini kontrol edin.

## Bağlantıyı tamamla düğmesi pasif

Devam ekranında bir yerel mola veya sonraki deneme zamanı varsa bu beklenen davranıştır. **Kapat**, bekleyen oturumu korur. **Bekleyen bağlantıyı sil**, devam etmek için yeniden giriş gerektirebilir.

Düğme etkinleştiğinde işlem yalnız profil kontrolünü dener. Başarılı bir profil gelmeden hesabın tamamen bağlandığı sonucuna varmayın.

<a name="liste-tamamlanmiyor"></a>

## Liste tamamlanmıyor

**Liste henüz tamamlanmadı** veya sayı uyuşmazlığı varsa ilgili kayıt eksiktir. Devam durumu ve bekleme açıklamasını izleyin. Önceki dolu/tam kaydı, yeni kısmi sonuçtan ayırın.

Listenin tekrar başlatılması her durumda yeni ve tam sonuç garantisi vermez. Süreyi, yönü, okunan/beklenen sayıyı ve son hata bilgisini destek bildirimine ekleyin. [Liste okuma mantığı](Takip-Listeleri.md#eksik-listeyi-anlamak).

<a name="bildirim-gelmiyor"></a>

## Bildirim gelmiyor

Önce uygulama içi değişiklik kaydında yeni bir olay olup olmadığını kontrol edin. Profil izleme, otomasyon, tür tercihi ve işletim sistemi iznini ayrı ayrı inceleyin. Windows'ta uygulamanın tamamen kapalı; Android'de işin sistem tarafından geciktirilmiş olması teslimi etkileyebilir.

Yerel kayıt varsa fakat sistem bildirimi yoksa tekrar profil sorgulamak yerine bildirim/odaklanma ayarlarını inceleyin.

## Threads düğmesi veya fotoğraf görünmüyor

Threads için açık profil adresi ya da Instagram'ın etkin rozetinde geçerli ad gerekir. Eski kayıt henüz bu alanı içermeyebilir; sonraki normal profil okuması günceller. Eksik düğme hesabın olmadığına dair kanıt değildir.

Fotoğraf ayrı yüklenir ve geçici olarak alınamayabilir. Baş harf görünmesi profil/listenin mutlaka eksik olduğunu göstermez.

## Yerel veri hizmeti kapandı

Windows'ta uygulamadan **Tamamen çık** ile çıkın; aynı anda başka bir Tracker sürümü çalışmadığını kontrol edip güncel EXE'yi yeniden açın. ZIP'in bütünüyle çıkarıldığını ve paket dosyalarının yanında durduğunu doğrulayın.

Hizmet kapanması mevcut dosyaların otomatik silindiği anlamına gelmez. Sorunu gidermek amacıyla şifreli veri klasörünü elle silmeyin. Devam ediyorsa sürüm ve görülen açıklamayla [destek kaydı](Surum-ve-Destek.md#destek-icin-hangi-bilgiler-gerekir) hazırlayın.
