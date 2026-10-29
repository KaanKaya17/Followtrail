# Android 0.1.45–0.1.46 — giriş sonrası doğrulama

29 Ekim 2026. Önceki 0.1.44 canlı girişinde `authenticated=true` doğrulandı; profil okuması tamamlanmamıştı. Bu değişiklik iki ayrı yerel hatayı ele alır.

## Üreten sınırlar ve düzeltme

- 0.1.45: Sayfa tamamlandığında 15 saniyelik okuma aralığı için yeniden değerlendirme planlanmıyordu. Düğme hazırlığı `updateLoginCompletion` tarafından hesaplanır; sayfa başlangıcında önceki uyandırma iptal edilir, bitişinde tek uyandırma kurulur. Uyandırma ağ isteği göndermez ve giriş aktarımındaki hata açıklamasını silmez. Girişin 15 dakikalık yeniden deneme aralığı ayrı kalır.
- Canlı 0.1.45: Tek giriş HTTP 200 / `authenticated=true`, oturum ve hesap çerezleri uygulanmış; A düğmesi kendiliğinden etkinleşmişti. A yine başarısız oldu; karşılaştırma kaydı oluşmadı ve son sayaçlı HTTP girişteki 200 kaldı. Kaynakta A'nın ağ öncesi sayfa denetiminin tüm `/accounts/` yollarını reddettiği görüldü. Giriş sonrası `/accounts/onetap/` zaten uygulamanın yerel karşılama sayfası olarak sunuluyordu.
- 0.1.46: `ProbePolicy` bu istisnayı yeniden yorumlamak yerine mevcut `LoginBrowserPolicy.landing` kararını kullanır. Kesin HTTPS kökeni, port ve kimlik bilgili URL denetimi korunur; giriş, challenge, checkpoint ve onetap alt yolları hazır sayılmaz. Okuma yolu/yöntemi veya giriş belgesi izinleri genişletilmez.
- Debug fixture, üretimdeki yerel karşılama yanıtını kullanır. Gerçek MainActivity/WebView A→B testi artık `/accounts/onetap/` yönlendirmesinden başlar; yalnız native okuma başarıyla bitince şifreli hesap kaydı oluştuğunu sınar. Diğer senaryolar 429, iptal, kimlik ve başarısız bağlantıda eski hesabın korunmasını kapsar.

## Paket ve yürütülen kontroller

0.1.46/kod 47. 99 JVM testi geçti; release lint 0 hata / 22 uyarı. Tam derleme 3 dk 34 sn; fixture son durumunu doğrulayan ek derleme 16 sn ve tüm görevler günceldi. Önceki 0.1.45'te 39 emülatör vakası / 38 başarılı + 1 opt-in atlandı (148,677 sn).

0.1.46 imzalı APK 2.200.216 bayt; SHA256 `c6ba24fabd566e62074ee5e890a39b3d0ba059a9dfad1f9418eca3f72b14f563`. Mevcut üretim imzası kullanıldı. Paket izinleri aynı dört izin; debug, yedekleme ve açık HTTP kapalı; native kütüphane ve debug fixture yok. Manifest denetimi canlı Meta doğrulamasının yerine geçmez.

İlk 0.1.46 test turunda 39 vakanın yönlendirmeli A→B vakası başarısızdı: test yeni sayfa bitmeden eski etkin düğmeye basıyor, gezinme iptali oluşuyordu. Tek vaka tanısında `BROWSER/CANCELLED`, HTTP yok görüldü. Test artık LP01 belge tamamlama sayısının artmasını ve yeni URL/düğme hazırlığını birlikte bekler; başarısız terminal durumu da açıkça raporlanır. Üretim kodu bu test düzeltmesi için değiştirilmedi.

0.1.46 son API 37 emülatör turu: 39 vaka / 38 başarılı, 1 isteğe bağlı canlı form vakası atlandı; 150,870 sn. Yönlendirmeli A→B, düğme uyandırma, 429/iptal ve hesap saklama testleri geçti. Sentetik testler gerçek Meta başarısının yerine sayılmaz.

## Canlı sonuç ve açık sınırlama

Üretim 0.1.46 emülatöründe mevcut 15 dakikalık giriş aralığı korundu. Süre dolunca yalnız bir giriş gönderildi: HTTP 200, `data=OBJECT`, `caa_login_web` var, `authenticated=true`, captcha/iki aşamalı yük yok, oturum ve hesap çerezleri alınıp uygulandı; hata kodu yok. A düğmesi zorlanmadan etkinleşti.

A'ya bir kez basıldı. Bu kez ağ öncesi sayfa denetimi geçti ve yalnız bir izinli profil GET'i gönderildi. LP01 `ALLOW:GET:PROFILE=1`, `ALLOWED_RESOURCE:PART:429=1`; W01 `LIMITED / HTTP 429` kaydı oluştu. Kaynak URL/kimlik/çerez/gövde kaydedilmedi. B'ye basılmadı, yeni giriş denenmedi; hesap şifreli depoya bağlanmadı. Yerel mola 29 Ekim 23:26; sunucu Retry-After süresi belirtmedi. Yerel sürenin dolması sunucu erişimi garantisi değildir.

Bu sonuç giriş hatasının giderildiğini, profil okumasındaki 429'un ise devam ettiğini gösterir. 429 WebView içindeki GET'e geldiği için yalnız OkHttp taşıyıcısına bağlanamaz. Hesap, ağ/IP veya uç nokta koşullarından hangisinin etkili olduğu doğrulanmadı; otomatik uç değiştirme/tekrar eklenmedi. Tam çalışan hesap bağlantısı veya canlı liste taraması iddia edilmez. Sonraki canlı kontrol, mola korunarak yapılacak tek profil okumasını ve ancak başarılı olursa B'yi kapsamalıdır; aynı kısıtı aşmak için cihaz/hesap değiştirilmemelidir.

Fiziksel telefona mevcut imzayla `install -r` yapıldı: sürüm 0.1.46/kod 47 doğrulandı. Uygulama verisi silinmedi; yeni fiziksel giriş/okuma başlatılmadı. Sahipli emülatör test sonrası kapatıldı. 0.1.45 adayı ayrı yayınlanmadı; 0.1.46 bu açık sınırlamayla yalnız test önizlemesidir.
