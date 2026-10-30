# Gerçek hesapla emülatör kullanıcı akışı — 30 Ekim 2026

Kapsam: Small_Phone API 37 emülatöründe imzalı üretim APK'sı, kullanıcı arayüzü üzerinden gerçek giriş denemesi. Test hesabı sahibi giriş bilgilerini sağladı ve hesapların ayrı olduğunu açıkladı. Parola, OTP, çerez değeri ve doğrulama URL/token'ı bu kayda alınmadı. Telefonun yerel molası değiştirilmedi. Emülatörün kendi önceki bekleme süresi dolmuştu; giriş aralığı/bütçe ayarları sıfırlanmadı.

## Gerçekte yapılanlar

1. Mevcut emülatör üretim uygulaması 0.1.51'e güncellendi. Karşılama, Hesaplarım ve giriş yöntemi menüsü kullanıldı. Eski W01 / HTTP 429 kaydı 29 Ekim'e aitti; yeni bu turun sonucu sayılmadı.
2. Önerilen web girişinde Instagram'ın tablet uygulaması karşılama ekranı geldi. Sayfa içinde kaydırıp gerçek Giriş yap bağlantısı seçildi; kullanıcı adı ve parola formuna ulaşıldı.
3. Yaklaşık **12:02**'de ilk tek giriş isteği: HTTP 200, `authenticated=false`, `captchaNeeded=true`, `errorCodes=[1348009]`, `cookiesApplied=false`. Kullanıcı daha sonra ilk parolanın yanlış olduğunu açıkladı. Bu ilk deneme, geçerli bilgilerle yapılmış bağlantı testi değildir.
4. Kullanıcı ayrı test hesabının güncel parolasını verdi. Emülatörde 15 dakikalık giriş aralığı beklendi; bu sırada başka hesaba giriş yapılmadı. Koyu tema ve English seçilip uygulama kapatılıp yeniden açıldı; dil ve seçili Dark tercihi korundu. Sonraki giriş için Türkçe'ye dönüldü.
5. Aralık dolduktan sonra **12:20** civarı güncel parola ile aynı hesapta bir kez daha giriş yapıldı. Yanıt yine HTTP 200, `authenticated=false`, `captchaNeeded=true`, `[1348009]`, session/owner cookie alınmadı ve uygulanmadı. Kullanıcı, güncel bilgilerle normal Instagram uygulamasına giriş yapabildiğini bildirdi. Bu, emülatördeki CAPTCHA gereksiniminin tamamlandığını kanıtlamaz.
6. **Bu turda yeni HTTP 429 gözlenmedi.** Yeni profil veya takip listesi isteğine geçilmedi. Bu nedenle Özet verisi, ilk gerçek tarama, kişi ekleme ve gerçek değişiklik bildirimi bu hesapla uçtan uca doğrulanmış değildir.

## Bulunan ve düzeltilen mesaj sorunu

0.1.51'de CAPTCHA sonucu native arayüzde genel `security_login_scope` uyarısıyla örtülüyordu. Sonradan engellenen gezinme, gerçek giriş yanıtı geldiyse onun mesajını değiştirmemeli. 0.1.52'de bu düzeltildi; gezinme engeli ve izin verilen alıcı/yollar değiştirilmedi.

Güncel parola denemesinden sonra gerçek 0.1.52 arayüzünde `web_login_captcha_unavailable` mesajının korunduğu görüldü: Instagram ek doğrulama istiyor, bu sürüm dönen doğrulama bağlantısını açamıyor ve kullanıcı normal Instagram'daki güvenlik uyarılarına yönlendiriliyor. **Doğrulama düğmesi oluşmadı.** Bağlantının neden desteklenmediği mevcut sonlu tanı alanlarından belirlenemiyor; yeni URL şekli veya parola hatası kök neden diye sunulmaz. Güvenlik bağlantısı/token'ı yakalanmadı ve host/yol izinleri tahminen genişletilmedi.

CAPTCHA tamamlanmış veya giriş başarılı denmedi. Desteklenen bir linkte gizlilik açıklamasından sonra kullanıcı CAPTCHA'yı kendisi tamamlar. Gerçek web 2FA hesabında uçtan uca tamamlanma da bu turda sınanmadı.

## Kullanıcı deneyimi notları

- Küçük ekranda eski mola kartı ve açıklamalar, ana Hesap bağla düğmesini ilk görünümün altına itiyor. Üstteki Hesaplarım → Hesap ekle erişimi çalışıyor; ana ilk adımın görünürlüğü geliştirilebilir.
- Kayıtlı hesap yokken Hesaplarım diyaloğu boş liste alanıyla açılıyor. Hesap ekle eylemi var; kısa yönlendirme daha anlaşılır olur.
- Instagram karşılama sayfası giriş formu yerine tablet uygulamasını öneriyor; giriş bağlantısına kaydırarak ulaşmak gerekiyor. Bu, kullanıcı için hâlâ zahmetli bir adım.
- Türkçe/English ve koyu tema tercihi yeniden açılışta korundu. Üretim ekranları erişilebilirlik ağacıyla incelendi; güvenli pencere bayrağını aşarak ekran görüntüsü alınmadı.

## 0.1.52 doğrulaması

- Derleme başarılı, 105 JVM testi / 0 hata; lint 0 hata / 22 mevcut uyarı.
- WebLoginScreenTest: 14 vaka / 127,471 sn / 0 başarısız. Ayrı canlı public-form opt-in testi bu koşuda atlandı; diğer 13 vaka sentetik yanıtlı gerçek WebView testleridir. Yeni vaka, engellenen gezinmenin CAPTCHA sonucunu ve kullanıcı eylemini kaybettirmediğini denetler.
- İmzalı `InstagramTracker-0.1.52.apk`, versionCode 53, 2.241.176 bayt, SHA-256 `5cea9274e7651fef7765820369b402a8eac90f72767264ea874b9167989fdd6b`. Paket/imza denetimi geçti; aynı dört izin, backup/debuggable/cleartext kapalı, debug taşıyıcısı yok.
- 0.1.52 yalnız emülatöre kuruldu; fiziksel telefon bu turda bağlı değildi. Telefonun son kurulan sürümü 0.1.51.

Sonuç: Gerçek giriş akışı CAPTCHA aşamasında durdu; tam kullanıcı yolculuğu tamamlandı denemez. Açık iş, desteklenmeyen güvenlik adımını güvenli biçimde kullanıcıya tamamlatıp oturum/profil kontrolüne döndürmektir.

## 0.1.53 · ayrı test hesabı, gerçek emülatör denemesi

30 Ekim yaklaşık 13.04, kullanıcı tarafından yeni verilen ayrı test hesabıyla önerilen Instagram web akışında bir giriş gönderildi. Üretim APK 0.1.53 / code 54; emülatörün kayıtlı bekleme süresi dolmuştu, mola veya veri sıfırlanmadı. Kimlik bilgileri ADB shell stdin üzerinden yalnız emülatöre girildi; host komut satırına veya dosyaya yazılmadı.

Giriş yanıtı HTTP 200, `errorCodes=[1348009]`, `authenticated=false`, `captchaNeeded=true`, `twoFactorPayloadPresent=false`, `redirectPresent=true`, `challengeStatus=UNSUPPORTED_PATH`. Oturum ve sahip çerezleri gelmedi, `cookiesApplied=false`. Bu, desteklenen challenge yoluyla eşleşmeyen bir doğrulama bağlantısı demektir; gerçek yol/token kaydedilmediği için hangi yeni akışın gerektiği henüz bilinmiyor. Yanıtta yanlış giriş bilgisi mesaj sınıfı da var; CAPTCHA işaretiyle birlikte bu sonuç parolanın doğruluğunu tek başına kanıtlamaz.

Native arayüzde formun kapandığı, CAPTCHA / güvenlik adımının açılamadığı açıklamasının ve **Instagram’da kontrol et**, **Kapat**, **İstek bilgileri** düğmelerinin gösterildiği doğrulandı. İstek bilgileri ağ isteği göndermeden açıldı. Bu girişte yeni 429 görülmedi; ekrandaki W01 / HTTP 429 satırı 29 Ekim 22.26 tarihli eski kayıttır. İkinci giriş veya profil/liste isteği gönderilmedi. CAPTCHA ve hesap bağlantısı tamamlanmadı.
