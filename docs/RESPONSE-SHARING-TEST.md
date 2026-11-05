# Yanıt ve görsel işi paylaşımı — Windows 0.2.56 / Android 0.1.86

5 Kasım 2026. [Envanterdeki üç boşluğun](RESPONSE-REUSE-AUDIT.md) uygulama kaydıdır. Günlük/saatlik bütçe, 6–10 saniyelik sıra ve 429/Retry-After davranışı değiştirilmez. Yeni Instagram uç noktası, genel POST yetkisi veya üçüncü taraf veri servisi eklenmez.

## Profil bilgisinin tek sahibi

Windows'un yalnız liste sayıları tutan `ProfileBasis` temsili kaldırılıp `ProfileFacts` ile değiştirildi. Aynı bağlantıya ait en fazla 20 normalize profil kaydı süreç belleğinde tutulur. Ham yanıt, fotoğraf dosyası/adresi, parola, not ve grup kopyalanmaz. Desteklenen kimlik/ad, sayılar, biyografi, gizlilik/doğrulanma, resim kimliği ve profil linkleri gerçek gözlem zamanı ile korunur.

Otomatik liste başlangıcı 60 saniyelik kısa tazelik şartını sürdürür. Panel ve açık kişi kaydetme, Windows panelinin 5 dakikalık tazelik süresini kullanır; kısa liste penceresinin dolması daha uzun panel kaydını silmez. Hesap/bağlantı değişimi ve açık silme işlemleri kapsamı temizler. Saat geri alınması veya süresi dolmuş kayıt eski bilgiyi güncel yapmaz. Açık yenileme `fresh` seçimiyle önbelleği atlar; arayüz keyfî profil gövdesini backend'e aktaramaz.

Başarılı erişim sorgusu `ProfileProbe` ile iki ayrı olgu döndürür: görünürlük kanıtı ve varsa normal profil ayrıştırıcısından geçmiş tam profil. Yalnız HTTP 200 / doğru kimlik / erişilebilir hedef paylaşılır. Tam profil alanları gelmeyen geçerli görünürlük yanıtı teşhisi bozmaz, eski kayıtlı alanları silmez. Kendi profilinin taze sağlık kontrolü kaldırılmaz. Görünürlük günlüğüne profil gövdesi eklenmez.

İzlenen hedefin doğrulanmış yeni profili normal gözlem zincirine gider. İzlenmeyen hedef yalnız geçici bellekte kalır; kullanıcı kaydetmeden kişi/geçmiş oluşturulmaz. Kişi kaydederken backend'in sahip olduğu geçerli ayrıntılar başlangıç gözlemi olarak alınır. Orijinal zaman korunur; yeniden ağ kontrolü veya değişiklik bildirimi üretilmez. Hatalı kayıt seçenekleri herhangi bir kayıt aktarımından önce reddedilir. Android'in mevcut gösterilmiş `Profile` değerini kayda alma davranışı korunur; Android'e yeni erişim teşhisi özelliği eklenmedi.

## Tek indirme, bağımsız tüketiciler

Windows `Thumbnails`, hesap + hedef + URL için devam eden tek üreticiyi paylaşır. Arama ve liste tüketicileri ayrı nesil/iptal sahipleridir. Birinin iptali diğerini durdurmaz; son tüketici ayrılınca indirme iptal edilir. Her pipe isteği tam bir yanıtla sonlandırılır. Tamamlanan önbellek 64 kayıt / 15 dakika, üretici ve bekleyen tüketici sınırları 20 olarak kalır. İptalden geç dönen eski üretici aynı anahtarın yeni işini silemez veya önbelleğini eski görüntüyle dolduramaz.

Android'de aynı `AvatarImages` yükleyicisi içindeki tüketiciler `SharedImageWork` üzerinden paylaşır. Anahtar hesap/hedef/resim sürümü ve URL ayrımını korur. Üretici iş kuyruğu iki çalışan + 20 bekleyen iş; tüketici sınırı bu gerçek kapasiteden türetilir. Her görünüm yalnız kendi biletini iptal eder. Ağ iptali ve fetcher temizliği mevcut ayrı temizleme iş parçacığında kalır. Tek üretici bir kez çözer ve arşiv/önbellek yazar; her geçerli görünüm kendi UI teslimini alır. Kaynaklar silinince/kapandığında bekleyen teslimler geçersiz olur. Tamamlanmış görünüm bileti bağlı kalabildiği için yerleşim olayları aynı resmi tekrar tekrar istemez.

Testler ortak üreticinin `Future` nesnesini artık kontrol etmez. Her tüketicinin yalnız kendi tamamlanmasını bekleyen, üreticiyi iptal edemeyen bir bekleme noktası vardır. Ayrı yükleyici örnekleri arasında süreç çapında tek indirme garantisi verilmez; mevcut disk/bellek önbellekleri ayrıca çalışır.

## Ölçülen örnekler ve kapsam

`scripts/audit-response-reuse.py` güncellendi ve geçici şifreli kasalar/sentetik taşıyıcılarla çalıştırıldı:

- Tek profil API yanıtı özet, alan geçmişi, günlük istatistikler, bildirim kaydı ve biyografi arşivini beslemeyi sürdürdü.
- Erişim kontrolü ve hemen sonraki backend profil ayrıntısı toplam **2 API çağrısında** kaldı: biri gerekli kendi profil sağlığı, biri hedef. Hedefte gelen **77 takipçi / yeni biyografi** artık profil kaydına aktarıldı. Ayrıntı için üçüncü sorgu gerekmedi.
- Panelde **1 API çağrısıyla** okunan **77 takipçi**, kişi kaydedildiğinde **0 ek çağrıyla** korundu. İlk okuma zamanı değişmedi. Profil işleri için tazelik devam ederken ilk otomatik profil işi gereksiz yere hemen tekrarlanmadı; ayrı liste işleri bu iddianın kapsamında değil.
- Aynı geçerli fotoğraf için iki eşzamanlı tüketici **2 yerine 1 sentetik CDN indirmesi** paylaştı. Üçüncü tüketici tamamlanan önbelleği kullandı. Bu, Instagram profil API'sinde %50 tasarruf veya uygulama genelinde %50 hızlanma iddiası değildir.

Windows üretim sınırı regresyonları yeni kayıt aktarımı, erişim sonucu/tam profil ayrımı, orijinal zaman, bilinmeyen alanlar, açık yenileme, süre aşımı, bağlantı değişimi, yanlış kayıt seçenekleri ve döndürülen kopyanın izolasyonunu kapsar. Görsel testleri iptalde tek yanıt, bağımsız arama/liste, tüketici sınırı ve eski üreticinin yeni işe karışmamasını kapsar.

Android üretim koordinatörünün JVM testleri tek üretici, bağımsız iptal, son tüketici iptali, geç tamamlanan eski iş, farklı anahtarlar, tüketici kapasitesi, başarısız üretici, reddedilen teslim ve bir görünümün hatasının diğerini engellememesini sınar. Gerçek Bitmap ile ortak indirme için cihaz testi eklendi. **Kullanıcı cihaz testini sonraya bıraktı; emülatör açılmadı ve instrumentasyon çalıştırılmadı.** Test APK'sının derlenmesi cihazda çalıştığı iddiası değildir.

Bu örnekler çağrı sayısı ve aktarım doğruluğunu ölçer. Gerçek kullanıcıdaki eşzamanlı fotoğraf sıklığı, canlı Meta kotası, uçtan uca ağ hızlanması, soğuk/sıcak süre, CPU/tepe bellek ve ajan token maliyeti için yeni ölçüm yapılmadı. Gerçek hesaba tanı amaçlı ek sorgu gönderilmez; mevcut uygulamanın normal otomasyonu ayrı çalışır.

Doğrulama: 160 seçili Python testi, 183 JVM testi, 6 profil paneli JavaScript testi ve 36 paketli Windows kontrolü geçti. Arayüz JavaScript kaynakları ve ortak istek politikası eşleşmesi doğrulandı. Android release lint: 0 hata / 56 uyarı. Release APK ve debug instrumentasyon APK’sı derlendi; v2/v3 imza ve paket manifesti kontrolü geçti. Android 0.1.86 / kod 87: 2.618.811 bayt; SHA-256 `b13beb090cba81b1b340df092354b5d9757a7684f36b44b443167750e9d526f9`. Windows 0.2.56, önceki sürüm kullanıcı tarafından tamamen kapatıldıktan sonra gerçek kasayla açıldı; bunun dışında hesap bağlantısı veya yeni manuel sunucu testi yapılmadı.
