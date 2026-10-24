# Bağlantı prototipi — 0.1.5

24 Ekim 2026. Amaç: resmî uygulamada görünen profil sayıları bizim HTTP okuyucumuzda 429 alırken, tarayıcı oturumu üzerinden tek okumayı ölçülebilir biçimde sınamak. Instagram'ın bütün protokolünün çözüldüğü veya 429'un nedeninin bulunduğu iddia edilmez.

## REA bulguları

Kullanıcının `C:\Downloads\instagram.apk` dosyası önceki analizle aynı SHA-256'ya sahip: `897b023309e3e2d80a99babe57be0c02f247b58380b2453d4f0682997f9259cd`. Önceki paket kaydı 452.1.0.53.64 bildiriyor. REA 6.7.0 / JADX headless 0.7.1 statik incelemesi APK'yı çalıştırmadı; hesaba istek göndermedi. Ham Evidence yerel `artifacts/` altında korunur. APK, üçüncü taraf decompile kodu ve kimlik bilgileri Git'e eklenmez.

- **Statik gözlem:** TigonServiceLayer.makeTigonRequest koşullu IGTigonAuthHandler kurup istek oluşturucusuna bağlıyor. Korunmuş Evidence: `ev_b96d888c022a5eb10d32f4c1a447a014747d4b1138ddee374711f0c3d4ea2aeb`.
- **Statik gözlem:** IGTigonAuthHandler.getAllHeaders, URI ve oturum bayraklarına bağlı Authorization, X-MID, IG-U ve IG-INTENDED-USER-ID alanlarını hazırlıyor. Hepsinin her istekte zorunlu olduğu sonucu çıkarılmadı. Evidence: `ev_6bae6d62ec77be228aafb42e84554700e281ef19afc9e277a927da005de8f408`, `artifacts/rea-429-auth-getAllHeaders.json`.
- **Statik gözlem:** IGTigonAuthHandler.update yanıt başlıklarını oturum bileşeninin HgM metoduna iletiyor. Evidence: `ev_9378bffde9f6af80e2f7172b749c2fe2303e18b02fd302bdf3c3af4975c048ed`, `artifacts/rea-connection/1-inspect_android_method.json`.
- **Statik gözlem:** X.C02vv.HgM, URI koşulunda IG-Set-Authorization, IG-Set-X-MID ve IG-SET-IG-U alanlarını ilgili sahiplerine iletiyor; ayrı boolean koşulunda X-IG-Set-WWW-Claim işleniyor. Koşulların bütün çağrı yerleri çözülmedi. Evidence: `ev_50096757318b5e1fca066770bb1735892bf74f4cc2de92b10acc773a1164a9db`, `artifacts/rea-connection/3-inspect_android_method.json`.
- **Kendi kaynağımızdan gözlem:** mevcut Android okuyucusu WebView çerezlerinin seçilmiş kısmıyla ayrı HttpsURLConnection açıyor; bu mobil oturum döngüsünü uygulamıyor ve kayıtlı oturumu yanıt Set-Cookie/IG-Set alanlarıyla yenilemiyor. Windows prototipi farklı instagrapi istemci yoludur; onun başarısı Android adaptörünü doğrulamaz.
- **Hipotez:** istemci/oturum bağlamı veya seçilen erişim yolu 429'a katkıda bulunabilir. Kullanıcının resmî uygulamayı açabilmesi, bu uçta veya bu oturumda sınır bulunmadığını kanıtlamaz. Sunucu kararının nedeni ve süresi bilinmiyor. Split/native kod, profil çağrısının bütün üreticileri ve canlı ağ karşılaştırması tamamlanmış kapsam değildir.

## Deney ve sınırları

İlk deney, kendi uygulamamızdaki WebView oturumuyla aynı-origin web profil okumasıdır. Resmî Instagram Android oturumunu devralmaz, resmî geliştirici API'si değildir ve web frontend'in bütün durumunu uyguladığı iddia edilmez. Mobil başlık isimlerinden sahte oturum üretilmez. 0.1.5'te tek kontrollü canlı okuma HTTP 429 döndürdü; sayılar alınamadı ve tekrar yapılmadı. Ayrıntılar doğrulama kaydındadır.

### Windows başarısı ile Android denemesi arasındaki fark

Yerel Windows v0.3 kaynağı (`InstagramCounter/app.py`, `read_counts`) instagrapi 3.2.0 `Client.private_request` ile `https://i.instagram.com/api/v1/users/{own_id}/info/` yolunu kullanır; `is_prefetch`, `entry_point`, `from_module`, `is_app_start` parametrelerini gönderir. Kütüphanenin mobil istemci/Authorization durumu ve `get_settings`/`set_settings` ile saklanan oturum bağlamı kullanılır. Windows doğrulama kaydı ilk girişte ve yeni süreçte oturumdan okumada başarı bildirir; ağ paket kaydı yoktur.

Android'in mevcut ana okuyucusu `www.instagram.com` üzerinde seçilmiş WebView çerezlerini ayrı HttpsURLConnection'a taşır; kendi hesap özeti için `users/{id}/info/`, kişi için `users/web_profile_info/` kullanır. 0.1.5 deneyi ise aynı WebView bağlamında yalnız web_profile_info çağrısını sınar. Bunlar Windows'ta başarılı olan taşımanın birebir Android aktarımı değildir. İstemci, oturum durumu, host ve bazı sorgular değiştiği için eldeki sonuç bir işletim sistemi karşılaştırması sayılmaz. HTTP 429'un yalnız Android'den kaynaklandığı veya mobil yolu aktarmanın kesin çözüm olacağı sonucu çıkarılamaz. Sonraki karşılaştırma çalışan Windows taşımasının sözleşmesini temel almalıdır; yeni canlı deneme yapılmadan önce yerel mola korunur.

Ana ekrandaki **Bağlantı prototipini aç**, dışarıya açık olmayan ayrı Activity'yi açar. Önceki sürüm etiketi korunur; güncelleme aynı kalıcı RequestGate'i kullanır ve eski 429'u silmez.

1. Açılışta sayfa/profil otomatik yüklenmez. Arka plan/otomatik okumalar kalıcı prototip bekletmesiyle durur. Süreç kapanırsa prototipi yeniden açıp bitirmek bu bekletmeyi kaldırır.
2. **Instagram oturumunu aç** açık kullanıcı eylemidir; önceki yerel mola sürüyorsa kapalıdır. Giriş/doğrulama tarayıcıda tamamlanır. Cookie/parola native istemciye alınmaz; form alanları okunmaz; JavaScript-native köprüsü kurulmaz.
3. Kullanıcı profil adını girip **Tek okumayı başlat** düğmesine basar. Exact HTTPS origin ve giriş/doğrulama dışı sayfa gerekir. Sabit göreli web_profile_info adresine tek fetch, same-origin credentials, yönlendirmede hata ve tekrar yok. Aynı ekran/yeniden oluşma ikinci okuma başlatmaz; yeni açık denemeler de cihazda en az 15 dakika aralıklıdır.
4. Gövde birleştirme 1 MiB, native'e dönen projeksiyon 8192 karakterle sınırlı. Yalnız doğrulanmış profil kimliği/adı ve sayılar döner. Eksik değer sıfır yapılmaz; yanlış profil ve güvenli sayı aralığı dışındaki JSON değerleri reddedilir. Cookie/header/ham yanıt aktarılmaz.
5. 429 aynı gate'e yazılır; daha uzun Retry-After korunur. Alt kaynak hatası açık okumanın yanıtı sayılmaz; JS kendi fetch sonucunu bildirir. Prototip başarısı eski HTTP adaptörünü doğrulamadığı için onun otomasyonunu veya önceki sınırlama kaydını başarılı saymaz.
6. İptal, arka plana geçiş ve sayfa değişimi fetch'i iptal eder; geç sonuç reddedilir. Sonuçtan veya iptalden sonra WebView kapatılır; site arka planda açık bırakılmaz. Normal **Denemeyi bitir**, kullanılan tarayıcı verisini temizleyip bekletmeyi kaldırır. Sayfa hiç açılmadıysa önceden var olan tarayıcı çerezlerine dokunulmaz. Ani süreç kapanmasında temizliğin tamamlanması garanti edilmez; bu kalıcı şifreli tarayıcı oturumu çözümü değildir.

Tanılama yalnız zaman, event enum'u, sayısal HTTP durumları ve sayaçları saklar; URL/query, profil adı, parola, cookie/header/yanıt kabul eden bir kayıt API'si yoktur. Tarayıcı kaynak gözlemleri toplam sistem/Instagram trafiğinin eksiksiz ölçümü değildir. Açık okumalar ayrı ve gönderimden önce kalıcı sayılır.

Debug fixture ayrı sentetik HTTPS origin ve kapalı taşıma kullanır; release'te yoktur. Gerçek WebView ve aynı JS asset çalışır; Instagram/TLS sunucusu başarısı sayılmaz. Prototip yalnız öndeki bağlantı deneyidir; arka plan bildirimlerine bağlanmadı. Canlı başarı, yeniden açılış ve üretici uyumluluğu ayrıca doğrulanmalıdır.

Gerekli tarayıcı API'leri sayfada denetlendikten sonra okuma düğmesi açılır. Eski System WebView'de profil isteği veya deneme bütçesi harcanmadan güncelleme mesajı gösterilir. Eski emülatör imajında bu durumda üç fetch testi açık JUnit assumption ile atlanabilir; diğer gate/temizlik kontrolleri çalışır. Atlanan testler canlı/başarılı okuma sayılmaz. Yeni Android cihazda da eski WebView olabilir; yalnız Android API seviyesi özelliği garanti etmez.

Kaynaklar: [WebView evaluateJavascript](https://developer.android.com/reference/android/webkit/WebView), [CookieManager](https://developer.android.com/reference/android/webkit/CookieManager), [WebView güvenliği](https://developer.android.com/develop/ui/views/layout/webapps/webview).
