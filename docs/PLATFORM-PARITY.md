# Android / Windows eşitlemesi

## Android 0.1.88 ve Windows 0.2.61

Ortak hesaplar için iki kaynak listeden kayıtlı fotoğraf adresi kullanılabilir. Windows ve Android eldeki takipçi ve takip edilen sayılarını gösterir. Görseller mevcut CDN yükleyicisinden gelebilir. Karşılaştırma ekranı satırlar için otomatik profil sorgusu başlatmaz.

## Android 0.1.87 ve Windows 0.2.60

- Otomatik liste hazırlığı açıkken yeni kaydedilen Android profillerinin iki liste yönü sıraya alınır. İlk tam kayıt oluşana kadar hazırlık sürer. İlk sayı uyuşmazlığında yaklaşık 30 dakika sonra bir erken tekrar planlanır. Sonraki uyuşmazlık günlük plana girer.
- Gizli bir profil, bağlı hesabın son tam takip edilen listesinde yer almıyorsa ve liste sayısı profil sayacıyla uyuşuyorsa erişim koşulu gösterilir.
- **Takip Listeleri → Ortak hesapları karşılaştır** iki profilin yerel kayıtlarını hesap kimliğiyle eşleştirir. Kaynağın tarihi, kapsamı ve eksik liste durumu görünür.
- Profil ve iki liste yönü için ayrı tanı vardır. Aynı uyumluluk hatası üç kez art arda görülürse ilgili otomatik okuma duraklar. Son tam kayıt korunur ve başarılı elle okuma ilgili duruşu kaldırır. Devam bilgisi 400 veya 404 ile reddedilirse sonraki uygun tarama ilk sayfadan başlar.

0.1.87 için 187 Android birim testi, ortak istek/tema politikası denetimleri ve dört dilde 820 metin kaynağının denetimi geçti. Lint 0 hata ve 56 uyarı verdi. İmzalı paket ve manifest doğrulandı. Cihaz test paketi derlendi. Yeni 0.1.87 ekranlarının cihaz doğrulaması açık.

İmzalı APK SHA-256 değeri `3a7f976276eb52d0ca0e7e4098386519622fc4c37cda7d63b785e5264d76e2fd`.

11 Kasım 2026 fiziksel telefon testi 0.1.86 ile yapıldı. Mevcut oturumun profil yenilemesi HTTP 200 aldı. Takipçiler (1 hesap) ve takip edilenler (4 hesap) tam kayıt olarak yenilendi. Kullanıcı yeni görülen hesabın bildirimini aldığını söyledi. Ayrı bekleyen web oturumunu bağlamak için yapılan profil okuması HTTP 429 aldı. Telefondan yeni hesap bağlamanın uçtan uca doğrulaması açık.

## 5 Kasım 2026 eşitlemesi

Bu eşitleme kaydı Windows **0.2.56** ve Android **0.1.86** (sürüm kodu **87**) kaynaklarını kapsar. Windows 0.2.57 ilk profil listelerinin hazırlığını ayrıca günceller. [Güncel sürüm durumu](wiki/Surum-ve-Destek.md). Bu çalışma canlı Instagram girişinin çözüldüğü veya bütün platformların birebir aynı olduğu iddiasını taşımaz. İki platformda otomatik profil işleri hareketli bütçenin %80'inde ertelenir; tam bütçe listeler ve elle yapılan işlemlerle ortaktır. Ayarlarda kesintisiz kontrol talebi gösterilir. Windows'a otomatik liste başlangıcında kısa profil paylaşımı eklendi; Android mevcut ListBasis paylaşımını sürdürür. [Verimlilik kapsamı ve doğrulama](API-EFFICIENCY-TEST.md).

Windows erişim/panel/kaydetme arasında doğrulanmış profil bilgisini paylaşır. Android mevcut kayıt aktarımını sürdürür. İki platformun görsel yükleyicilerinde eşzamanlı tüketiciler ortak iş kullanır; [kapsam ve cihaz testi sınırı](RESPONSE-SHARING-TEST.md).

## Ortak kontrol politikası

- API ve hikâye medya indirmeleri arasındaki yeni rezervasyonlar **6–10 saniye** rastgele aralık kullanır. Önceden ayrılan daha uzun bekleme korunur. Bu değer, kontrol sıklığı veya işlemin bitiş süresi değildir.
- Hesap başına kayan saat / 24 saat bütçesi **120 / 3000**; aynı cihazdaki hesapların ortak bütçesi **240 / 6000** istektir. Masaüstünün önceki daha yüksek bütçesinden kalan kayıtlar sayılmaya devam eder; kota düşmesi geçmişi veya 429 molasını sıfırlamaz. Farklı cihazlar birbirinin sayacını paylaşmaz.
- Yeni profil kontrol varsayılanı **5 dakika**; seçenekler **1, 5, 15, 30, 60, 180, 360, 720, 1440 dakika**. Eski tercihler korunur. Android kayıtlı kişinin aralığı kendi ayarında; kendi hesabının aralığı **Seçenekler → Kontrol ayarları** içindedir. Arka planda kendi profilini kontrol etme ayrıca açılır; eski açılışta yenileme tercihi arka plan izni olarak yorumlanmaz.
- Birden fazla hesap ayrı şifreli kayıtlarını ve hesap bütçelerini kullanır. Aynı cihazın istek sırası ve sunucu sınırlaması ortaktır. Hesap kimliği henüz bilinmeyen giriş adımları, yanlışlıkla seçili eski hesaba yazılmadan ortak bütçede sayılır.
- Android'in periyodik sistem işi en az **15 dakika** hedefini korur; sistem bunu geciktirebilir. 1 veya 5 dakika seçmek, kapalı Android uygulamasına bu sıklıkta uyanma garantisi vermez. Görünür hazırlık/hızlı izleme kendi yaşam döngüsünü korur.
- Giriş denemesi aralığı, HTTP 429, Retry-After, doğrulama ve manuel durma kuralları değişmedi. Sosyal yazma işlemlerine yeni izin verilmedi. Bunlar Meta tarafından onaylanmış güvenli kotalar değildir.

Politikanın sahibi `design/request-policy.json`; Kotlin/Python sabitleri bunun üretilen çıktılarıdır. `npm --prefix interface run check` farkları yakalar.

## Android hikâye arşivi

Kendi hesabının arşivi Özet ekranındaki seçeneklerde; kayıtlı kişinin arşivi profilinin **Diğer → Hikâye arşivi** menüsündedir. Takvimde önceki/sonraki aya geçilir, hikâye olan gün seçilir. Telefonda takvim yerini günün görüntüleyicisine bırakır; geniş ekranda yan yana görünür. Saat/gün sınırı Windows ile aynı **UTC** standardındadır.

Arşivi açma, takvim ve oynatma yereldir. Yalnız **Hikâyeleri kontrol et** ve onay, hesabın erişebildiği hikâyeleri okur. GET isteği; görüldü, beğeni veya yanıt göndermez. Gizlenmiş hikâyelere erişim sağlamaz. Mention, bağlantı, hashtag, konum ve paylaşılan gönderi alanları yalnız yanıtta gerçekten varsa sunulur. Mention özgün hikâye sahibinin kanıtı değildir; müzik/çıkartmaların birebir oynatılması garanti edilmez.

İndeks ve medya Android Keystore/AES-GCM ile hesap bazında şifrelenir. Süresi dolan CDN adresleri kalıcı indekse/loga yazılmaz; CDN indirmelerine oturum çerezi taşınmaz. Oynatma bellek içindedir, şifresiz geçici medya dosyası oluşturulmaz. Üst sınırlar iki platformda da **200 öğe, dosya başına 16 MiB, hesap başına 256 MiB, indeks 2 MiB**. Bunların sahibi `design/story-policy.json`; istek kabiliyetleri mevcut masaüstü okuyucusunun kilitli instagrapi 3.2.0 yapılandırmasından `design/story-capabilities.json` içine alındı. Bu metadata, cihazda her efektin desteklendiği iddiası değildir.

Profil kaldırma, hesap silme ve ayrıntılı geçmiş saklama süresi medya dosyalarını kapsar. Kaydetme sırasında profil/hesap kaldırılırsa geç gelen indirme silinen kaydı yeniden oluşturamaz. İptal, önceden tamamlanan öğeleri korur. Tekrar kontrol, tamamlanmış medyayı yeniden indirmez; eksik medya yeniden denenirse ilk yakalanma zamanı değişmez.

## Checkup düzeltmeleri

- Windows'ta sayı uyuşmazlığıyla biten aynı liste her yarım saatte bir tekrar taranmaz. Günlük kontrol veya sonraki profil okumasında gerçekten değişen yeni sayı beklenir; eski kısa zamanlama kayıtlarına da uygulanır. Eksik liste tam sayılmaz, başlangıç kaydı korunur, eksik üyeler takipten çıkmış gibi bildirilmez.
- Hikâye tekrarlarında ilk kayıt zamanı korunur; aynı ID ile gelen yabancı sahiplikli tekrar öğesi atlanarak gizlenemez. Aşırı büyük medya adayları seçilmez.
- Windows Ayarlar'daki sürüm artık Python içindeki eski sabitten değil, çalışan Electron paketinden gelir.
- Android istek kapısının eski üçüncü/trailing lambda saat sözleşmesi korunur; rastgele gecikme ayrı bağımlılıktır. Saat sağlayan test/okuyucu yanlışlıkla gecikme sağlayıcısına dönüşmez.

## Kalan farklar ve doğrulama sınırı

Android'in önceki Özet, profil, bildirim, zaman çizelgesi, dil, tema, liste arama/fotoğraf, günlük ve görsel geçmiş çalışmaları korunur. Native Android ile Electron'un ekran yerleşimi, işletim sistemi bildirimleri ve klavye davranışları farklıdır. Android'in 100 kayıtlı kişi, 90 ayrıntılı profil gözlemi ve 2 MiB ana kasa sınırı; Windows'un farklı kaynak sınırları bu değişiklikte körlemesine yükseltilmez. Kalıcı günlük özetler ayrıntılı gözlemlerden bağımsızdır. Bu nedenle eksiksiz birebir eşitlik denmez.

İlk 0.2.52 / 0.1.81 eşitlemesinin aşağıdaki çevrimdışı doğrulamasında Instagram'a canlı istek gönderilmedi, gerçek kullanıcı kasası açılmadı, uygulama/telefon kurulumu yapılmadı ve emülatör başlatılmadı. Bu cümle sonraki bütün sürümlerin çalışma koşullarını tanımlamaz. Kotlin/Python/JavaScript birim testleri, kaynak/çeviri tutarlılığı ve paket kontrolleri uygulanır; çalıştırılmış sonuçlar değişiklik kaydında kaydedilir. Android Keystore/disk/iptal instrumentasyon testleri pakete eklenip derlenir; kullanıcı cihaz testini ertelediği için çalıştırılmaz. Android görsel kalite ve video oynatımı cihazda henüz doğrulanmadı. Telefon içinden giriş ve profil/liste 429 sorunu ayrı, açık canlı doğrulama işidir; mevcut bekleyen oturum ve mola korunur.

## Yanıt paylaşımı paket doğrulaması — 0.2.56 / 0.1.86

160 seçili Python, 183 JVM, 6 profil paneli JavaScript testi ve 36 paketli Windows kontrolü geçti. Android lint 0 hata / 56 uyarı; release ve test APK'sı derlendi, imza/manifest denetlendi. Windows 0.2.56 gerçek kasayla açıldı; normal otomasyonu ayrı çalışır. Yeni Android cihaz/emülatör ve canlı giriş testi yapılmadı. [APK özeti, kapsam ve sınırlamalar](RESPONSE-SHARING-TEST.md).

## Tarihsel eşitleme kontrolleri — 0.2.52 / 0.1.81

Aşağıdaki sayılar ve APK özeti ilk eşitleme paketine aittir; güncel sürümün tüm testleri yeniden çalıştırılmış gibi okunmamalıdır.

- Windows Python: **271 test geçti**; sentetik ağ yanıtları ve ayrı geçici şifreli kasalar kullanıldı.
- JavaScript: **69 test geçti**. Ortak politika üretimi, tema/arama/bağlantı kaynakları ve dört dilde 1166 masaüstü mesajı tutarlı.
- Paketli Windows 0.2.52: **36 işlev kontrolü + 7 hikâye arşivi senaryosu geçti**. Kontrol seçenekleri/bütçeler ortak politika ile, Ayarlar sürümü gerçek paketle karşılaştırıldı. Testlerin ağ kaydında Instagram isteği yoktur.
- Android dört dilde **783 kaynak**; yer tutucular ve çoğullar doğrulandı.

Bozuk hesap kaydının hikâye temizliğini engellememesi için silme, hesap içeriğini çözmeden uygulamanın sahip olduğu depolama alanı üzerinden yapılır. Bu regresyonun Keystore/disk testi instrumentasyon paketindedir; cihazda çalıştırılması ertelenmiştir.

- Android son kaynakla **167 JVM testi geçti**; `lintRelease` **0 hata / 54 uyarı** ile tamamlandı. Release APK ve debug instrumentasyon APK’sı derlendi. Var olan stil/yerelleştirme uyarıları temiz kapı sonucu gibi gizlenmedi; cihaz testi yerine geçmez.
- İmzalı APK mimari/manifest kontrolü geçti: `dev.kaankaya.instagramtracker`, sürüm 0.1.81 / 82, 2.602.427 bayt; yedekleme ve açık metin trafik kapalı, debug örnek taşıyıcısı release içinde yok. SHA-256: `d963a1198660e6615042dcf5400115fcc7f7e8a95e17e77e1bfa90405d711a24`. Son alan-temelli silme uygulaması imzalı DEX içinde doğrulandı.
