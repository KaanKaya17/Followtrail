# Cihazda çalışan web bağlantısı — 0.1.27

Önerilen bağlantı artık **Instagram ile bağlan** yoludur. Instagram'ın kendi HTTPS sayfasında giriş/doğrulama tamamlandıktan sonra **Hesabı doğrula** yalnız bir profil isteği başlatır. Uygulama bu web formunun parola/kod alanlarını okumaz veya JavaScript köprüsüyle dışarı çıkarmaz. Alternatif mobil parola girişi ve bilgisayardan şifreli aktarım ayrıca korunur.

## Ağ ve veri sınırı

- Yeni profil ve liste okuyucusu yalnız sabit `https://www.instagram.com` adresine GET gönderir. URL, yöntem veya başlık tarifi bir üçüncü taraf sunucudan alınmaz. FollowMeter servisi, kendi sunucumuz, reklam, analitik veya çökme toplama SDK'sı yoktur.
- WebView üst gezinmesi Instagram HTTPS alanlarıyla; sayfa kaynakları Instagram, cdninstagram.com ve fbcdn.net ile sınırlıdır. Facebook ile ayrı giriş bu sürümün akışı değildir. Profil fotoğrafları, önceki gibi yalnız izin verilen Meta CDN adreslerinden ayrı ve çerezsiz istemciyle yüklenebilir.
- Oturum referansı kullanıcı kimliği, UA ve rastgele web binding içerir; çerez kopyası taşımaz. Çerezler/claim, Keystore AES-GCM ile noBackupFilesDir içindeki iki web bankasında tutulur. Uygulamanın hesap ve liste kayıtları da şifrelidir. Yedekleme/debug/düz HTTP kapalıdır.
- Kimlik bilgileri bir sunucu yanıtındaki URL'ye veya yönlendirme adresine gönderilmez. Taşıma URL'sinin sabit HTTPS host/port ve boş userinfo koşulları, Call.Factory çalışmadan önce doğrulanır. HTTP yönlendirmesi, otomatik tekrar ve paralel alternatif kapalıdır; ikinci ağ iletimi ve HTTP/1 kimlik doğrulamalı iletimi engellenir.
- Yanıt çerezleri yalnız aynı kök origin için geçerliyse ve izin verilen adlardaysa uygulanır. Hesap kimliği değişirse veya sessionid silinirse oturum yenilemesi kabul edilmez. Claim/CSRF yenilenmesi şifreli bankaya, sabit bağlantı kimliği korunarak yazılır. Ham yanıt, parola/kod veya çerez tanıya yazılmaz.
- WebView normal bitirme/iptal/kapanışta temizlenir. Ani süreç ölümü tarayıcı temizliğini garanti etmez; sonraki girişte tekrar temizlenir. Bağlantıyı kaldır bütün web bankalarını da temizler. Instagram tarafındaki oturumları uzaktan iptal ettiği iddia edilmez.

## Oturum ve okuyucu sahipliği

`Session.webBinding` yeni şifreli web oturumunu, `mobileBinding` mevcut mobil oturumu seçer. İkisi aynı anda kullanılamaz. Liste/yeniden oluşturma kapsamı `connectionBinding` ile taşınır; web kapsamı `web:` önekiyle mobil kapsamdan ayrıdır. Bir istek başarısız olunca diğer protokole otomatik geçilmez.

`SessionBanks`, iki protokolün şifreli staging ve yenileme sahibidir. Yeni bağlantı önce aktif hesabı ezmeyen bankaya yazılır. Profil kimliği doğrulanmadan ana hesaba bağlanmaz. İptal, yanlış hesap veya başarısız bağlantı önceki hesabı ve bankayı korur; başarısız temizlikte kalan şifreli pending kaydı sonraki işlemde aynı sahip tarafından ele alınır.

`PrivateReadTransport` istek kapısı, tek etkin taşıma, 1 MiB yanıt sınırı, yanıt/kısıtlama politikası, iptal ve yenileme kaydının ortak sahibidir. Mobil protokolün mevcut başlık ve cihaz imzaları değişmez. Eski çerezli hesapların profil uyumluluk yolu korunur; yeni önerilen bağlantılar web bankası kullanır. Eski web bağlantısıyla liste okumak için yeni yoldan yeniden bağlantı gerekir.

Hesap kayıt biçimi v5'tir; v1–v4 hesapları okunur. Liste kaydı biçimi değişmez. Eski APK v5 hesabını okuyamaz; APK yedeği otomatik veri geri dönüşü değildir. Uygulamayı silmek veya verisini temizlemek gerekmez.

## Sabit web istek sözleşmesi

27 Ekim 2026'da Instagram'ın açık ana sayfası kullanıcı oturumu olmadan bir kez okundu: HTTP 200; yayımlanan web `app_id`/`APP_ID`/`appId` değeri `1217981644879628`. HTML, yanıt çerezleri veya kimlik doğrulama değerleri tutulmadı. Yeni web başlık sözleşmesi bu sabiti kullanır. Bu gözlem, oturumlu API kabulünün kanıtı değildir.

- Profil: `/api/v1/users/{id}/info/`; kullanıcı adı araması: `/api/v1/users/web_profile_info/?username=...`.
- Liste: `/api/v1/friendships/{id}/followers/` veya `/following/`, `count=50`, `search_surface=follow_list_page`; devam için URL-encoded `max_id`.
- Referer kök Instagram adresidir; CSRF ve WWW-Claim bu cihazdaki web oturumundan gelir. Instagram mobil Authorization header'ı üretilmez veya taklit edilmez.

Bu uçlar resmî Graph API garantisi değildir. 50 yerel sayfa tercihidir, güvenli Instagram kotası değildir. Instagram formatı veya kabulü değişebilir. Eksik sayaç/liste sıfırla doldurulmaz; eksik sayfalama tam liste sayılmaz.

## Kullanıcı doğrulaması

1. İmzalı 0.1.27'yi mevcut uygulamanın üzerine kur; veri temizleme veya bağlantıyı kaldırma yapma.
2. Kayıtlı Instagram molası bitmeden yeni giriş/okuma deneme. Güncelleme 429 kaydını veya 15 dakikalık bağlantı aralığını silmez.
3. Bağlantıyı yönet → Başka hesap bağla → Instagram ile bağlan. Instagram formunda giriş ve varsa doğrulamayı tamamla; sonra Hesabı doğrula.
4. Kimliği doğru hesabın gerçek sayıları gelmeli. Ardından bir liste sayfasını açık eylemle oku; uygulamayı yeniden açınca tekrar parola istenmeden kayıtlı hesap gelmeli.
5. İlk 429/doğrulama hatasında dur. Başka uç/hesap/IP denemesi veya kısıtlama temizliği yapma. Parola/kod/çerez paylaşmadan yalnız görünen hata ve İstek bilgileri durumunu bildir.

Yerel testler gerçek Keystore, WebView çerez aktarımı, staging, iptal, yönlendirme, kapasite ve kısıtlama sınırlarını sentetik yanıtlarla sınar. Bunlar canlı Instagram başarısının yerine geçmez. Gerçek testin ve APK'nın kapsamı `VALIDATION.md` içinde ayrıca kaydedilir.

0.1.28'den itibaren başarısız web doğrulaması tarayıcıyı kapatır ve hata/varsa o denemenin HTTP durumu ile sonuç penceresi gösterir. Mola yalnız kısıtlama/erken deneme sonucuna eklenir; eski mola başka hata nedenini gizlemez. İstek bilgileri düğmesi ağ isteği başlatmaz.
