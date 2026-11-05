# Yerel veri yaşam döngüsü

5 Kasım 2026 içerik denetimi: Windows 0.2.56 / Android 0.1.86. Saklama/silme yapısı 0.2.52 / 0.1.81 eşitlemesinden korunur; güncel geçici paylaşım ayrıca aşağıda belirtilir. Bu belge ürünün teknik davranışını açıklar; hukuki uygunluk görüşü değildir.

## Kullanıcı kontrolleri

- Windows: **Ayarlar → Yerel veriler → Veri saklama**. Android: **⋯ → Veri saklama**.
- Ayrıntılı geçmiş ve günlük istatistikler için birbirinden bağımsız **Ben silene kadar / 30 / 90 / 365 gün** seçenekleri bulunur. Tercihler bağlı hesabın şifreli deposuna aittir. Varsayılan mevcut kayıtları korur; süre seçilip temizlik onaylanmadan eski kayıtlar topluca silinmez.
- Güncel profil, izleme tercihleri ve son liste kaydı karşılaştırma yapabilmek için korunur. Eski ayrıntılı gözlemler, yerel olay/bildirim geçmişi, eski liste karşılaştırmaları, biyografi/fotoğraf arşivi ve Windows hikâye dosyaları seçilen ayrıntılı geçmiş süresine göre temizlenir. Günlük özetler UTC gün sınırlarıyla ayrı temizlenir. Güncel/yarım tarama silinmez.
- Uygulama kullanılırken veya mevcut arka plan işi çalışırken, ilgili hesap için en fazla günde bir temizlik yapılır. Kapalı uygulamanın dosyaları zamanın dolmasıyla kendiliğinden silinmez. Bu özellik yeni bir ağ isteği veya ayrı sürekli çalışan servis eklemez.
- Temizlik tamamlanamazsa uyarı görünür. Aynı ayarlar ekranından **Kaydet ve temizle** ile yeniden denenebilir. Bir dosyanın silinememesi başarılı silme olarak bildirilmez; işlem kısmen ilerlemiş olabilir.

## Profil ve arşivlerini silmek

Kaydedilen profili kaldırmak artık yalnız izlemeyi durdurmaz: o profile ait geçmiş, not/favori/izleme tercihi, günlük özetler, görsel arşiv, uygulama içi bildirim/olay kayıtları ve o kişinin kendi liste arşivleri de temizlenir. Her iki platformda o kişinin hikâye arşivi de silinir. Android sistem bildirimleri iptal edilir; işletim sisteminin kendi bildirim geçmişi veya ekran görüntüleri bu uygulamanın kontrolünde değildir.

**Kapsam sınırı:** Bir kişinin başka hesapların takipçi/takip edilen listelerinde görünmesi, o listenin tarihli üyelik olgusudur; profili silmek bu listeleri değiştirmez. Başka bağlı hesabın deposu etkilenmez. Kullanıcının dışa aktardığı yedek/CSV dosyaları, paylaşılan kopyalar ve işletim sistemi yedekleri ayrıca yönetilmelidir. Instagram hesabında takip/beğeni/mesaj işlemi yapılmaz.

Silinen günlük özetlerin eldeki eski yakın geçmişten geri doldurulmaması için hesap/hedef kimliği ve son silme sınırı şifreli olarak korunur. Bunlar sayaç veya biyografi içermez. Hesabın bütün yerel verileri kaldırıldığında bu işaretler de kaldırılır. Kayıtlı güvenlik molaları ve istek bütçeleri profil silmeyle sıfırlanmaz. Windows'un otomatik kurtarma kopyası da temizlenmiş içerikle değiştirilir.

Silme, uygulamanın erişebildiği dosya ve anahtarları yönetir; SSD üzerinde adli yöntemlerle kurtarılamaz fiziksel üzerine yazma veya başka cihazlardaki kopyaları yok etme garantisi değildir.

## Geçici profil ve görsel paylaşımı

Windows'ta en fazla 20 normalize profil kaydı süreç belleğindedir: otomatik liste başlangıcında 60 saniye, panel/kaydetmede 5 dakika tazelik koşulu kullanılır. Ham yanıt, parola/kod, fotoğraf URL'si/dosyası ve not/grup bu önbelleğe alınmaz. Hesap/bağlantı değişimi ve silme kapsamı temizler. İki platformun görsel işlerinde tüketiciler aynı indirmeyi paylaşabilir; son tüketici ayrılınca iş iptal edilir. Bu paylaşım mevcut şifreli arşiv/saklama süresini uzatmaz veya yeni kalıcı ham veri deposu oluşturmaz. [Kesin kapsam ve testler](RESPONSE-SHARING-TEST.md).

## Veri envanteri

- **Bağlı hesap:** sayısal kimlik, kullanıcı adı, oturum ve bağlantı bağlamı. Amaç yetkilendirilmiş okuma ve hesap ayrımıdır. Windows DPAPI / Android Keystore AES-GCM. Bağlantıyı kaldırmakla bütün arşivi silmek farklı işlemlerdir; onay metni geçerlidir. Ham parola ve doğrulama kodu ek tanı veya arşiv kaydı olarak tutulmaz.
- **Profil ve listeler:** bilinen isim, sayaç, biyografi, herkese açık profil bağlantıları, gizlilik bilgisi, fotoğraf kaynağı, liste üyeleri, kontrol zamanları, kullanıcı notları ve tercihler. Ayrıntılı geçmiş süre seçimine ve ayrıca mevcut adet/boyut sınırlarına tabidir. Güncel karşılaştırma kayıtları korunur.
- **Günlük istatistik:** hesap/hedef kimliği, UTC gün, ilk/son/minimum/maksimum sayaçlar ve gözlem sayısı. Ayrı şifreli profil/yıl dosyaları; günlük istatistik süresi ve hedefe özel temizleme uygulanır.
- **Görsel zaman tüneli:** yalnız açılan profillerin değişiklik kayıtları ve eldeki küçük resimleri. Şifreli, boyut/adet sınırlı; ayrıntılı geçmiş süresi uygulanır. Biyografideki metinden hassas kişilik, inanç veya sağlık sınıflandırması yapılmaz.
- **Hikâye arşivi (Windows / Android):** erişilebilen hikâyelerin yapılandırılmış bilgisi ve indirilebilen medyası şifreli tutulur. Ayrıntılı geçmiş süresi, yakalanma zamanına uygulanır. Her hikâyenin yakalanacağı veya bütün etiket/yeniden paylaşım bilgisinin sağlanacağı garanti edilmez. Android karşılığı 0.1.81 ile eklendi; cihaz testi ertelendi ([kapsam](PLATFORM-PARITY.md)).
- **Geçici fotoğraflar:** Windows sınırlı bellek önbelleği; Android sınırlı özel disk/bellek önbelleği. Android disk küçük resimleri artık ayrı Keystore anahtarıyla AES-GCM kullanır; dosya kimliği doğrulanır. Eski düz PNG önbelleği uygulamanın görsel yükleyicisi açıldığında arka planda temizlenir. Bu temizlik başarısızsa Veri saklama ekranından tekrar denenebilir. Eski fotoğraflar gerektiğinde yeniden indirilir; görsel arşiv bundan ayrıdır.
- **Tanı ve istek sınırları:** sınırlı HTTP sonuçları, işlem türü/zamanı ve bekleme kayıtları. Ham ağ gövdesi, parola, kod veya oturum anahtarı destek için istenmez. Mevcut sınırlı kayıt politikası ve hesap/cihaz güvenlik kuralları sürer; arşiv süresi bunları silmez.
- **Kullanıcı dışa aktarımları:** Windows DPAPI yedeği oturum içermez; CSV şifresizdir. Bunlar uygulama dışındaki kullanıcı seçimiyle üretilmiş kopyalardır ve süre temizliği dışında kalır.

## Ağ ve paylaşım

Kendi veri sunucusu, reklam, analitik veya çökme toplama SDK'sı eklenmedi. Canlı giriş/okumalar doğrudan Instagram/Meta'ya, küçük resimler izinli Meta CDN'lerine gider. Kullanıcının açtığı güvenlik sayfası Meta/Google CAPTCHA kaynaklarını, tıklanan dış bağlantılar normal tarayıcıyı kullanabilir. Yerel şifreleme bu bağlantıları ortadan kaldırmaz. Eklenen saklama/silme kontrolleri ağ izni veya üçüncü taraf servis eklemez.

## Yayın öncesi açık işler

Yerel çalışma veya kullanıcının kendi hesabını bağlaması tek başına bütün üçüncü kişi verilerini arşivlemek için hukuki dayanak oluşturmaz. Özellikle başkalarının listeleri, görsel/biyografi geçmişi ve hikâyeleri için amaç, gereklilik, süre, aydınlatma/hak başvuruları ve veri sorumlusu/işleyen rolü uzman tarafından değerlendirilmelidir. Kamuya açık bir profil de kişisel veri içerebilir. Meta'nın otomatik erişim kuralları ayrıca değerlendirilmelidir; HTTP 200 veya rakip uygulamanın çalışması izin kanıtı değildir.

Genel dağıtım öncesinde hukuki değerlendirme, başvuru/iletişim kanalı ve lisans kararı ürün sahibiyle tamamlanmalıdır. Bu sürüm **KVKK/GDPR uyum sertifikası** iddiası taşımaz. Uygulama içi PIN/biyometrik kilit henüz eklenmedi; cihaz/Windows oturumu güvenliği önemini korur. Olay müdahale yaklaşımı [güvenlik politikasındadır](../SECURITY.md).

## Doğrulama sınırı

Üretim depolarına dokunmadan sentetik verilerle Windows DPAPI silme/süre/hesap ayrımı regresyonları ve Android saf politika testleri kullanılır. Android Keystore, eski küçük resim temizliği ve tam profil silme için cihaz testleri kaynakta bulunur; **bu sürümde cihazda çalıştırılmaları sonraya bırakılmıştır**. Android giriş sorunu ve canlı Instagram uyumluluğu bu değişiklikle çözülmüş sayılmaz.

4 Kasım 2026 doğrulaması:

- Windows: `python -m unittest discover -q`, **258 test geçti**. Bunların dokuzu varsayılan/migrasyon, arşiv silme, hata/yeniden deneme, saklama sınırı, geri doldurma, iki gerçek yerel hesap kasasının ayrımı ve şifreli hikâye medyasının silinmesini kapsar. Ağ yanıtları sentetiktir.
- Paketli Windows 0.2.48: **36 genel + 5 gizlilik kontrolü geçti**. Dört dilde modal düzeni, iki tercihin gerçek IPC üzerinden kaydı, profil silme kapsamı ve şifreli deponun yeniden okunması sınandı. Gerçek Instagram isteği gönderilmedi; gerçek kullanıcı kasası açılmadı.
- Android: **146 JVM testi geçti**; `lintRelease` (0 hata, 50 uyarı), `assembleRelease` ve `assembleDebugAndroidTest` başarılı. Dört yeni Keystore/AtomicFile cihaz testi derlendi, çalıştırılmadı. Önceki sürümün eksik emülatör ekran kapsamı da kapanmış sayılmaz.
- **727 native kaynak** ve **1166 masaüstü ileti** dört dilde eşleşti; biçim değişkenleri ve çoğullar kontrol edildi. Ortak tema/simge ve JavaScript kaynak kontrolleri geçti. Wiki kontrolü 16 sayfa / 200 yerel bağlantıyı doğruladı.
- İmzalı APK: `InstagramTracker-0.1.77.apk`, sürüm kodu 78, 2.516.411 bayt; SHA-256 `6d3fead4bf32427d5e924c08e7bed205aa6596fc4bb67713e31511676edd02d7`. v2/v3 imza, manifest/izin ve yedekleme/cleartext kontrolleri geçti. Yeni izin veya reklam/analitik bağımlılığı eklenmedi.

Kaynak araştırmasında kullanılan [KVKK veri sorumlusu açıklaması](https://www.kvkk.gov.tr/Icerik/2032/Veri-Sorumlusu-Kimdir), [kişisel verilerin işlenmesi açıklaması](https://www.kvkk.gov.tr/Icerik/2050/Kisisel-Veriler) ve [Meta'nın otomatik veri toplama yaklaşımı](https://about.fb.com/news/2021/04/how-we-combat-scraping/) genel çerçeveyi açıklar; bu uygulamanın somut hukuki durumu hakkında onay oluşturmaz.
