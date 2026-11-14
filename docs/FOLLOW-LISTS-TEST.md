# Liste okuma deneyi — 0.1.8

Bu önizleme, seçilmiş profilin takipçi ve takip edilen listelerini **elle, birer sayfa** okumayı dener. 25 Ekim 2026'da kullanıcı fiziksel telefonda kendi sayaçlarının, kendi takip edilen listesinin ve arkadaşının ilk takipçi sayfasının geldiğini bildirdi; çok sayfalı devam henüz canlı sınanmadı. Kanıtın kapsamı [VALIDATION.md](VALIDATION.md) içindedir. Şimdiki bağlantı, Windows prototipinin mobil oturumunun şifreli aktarımıdır. Telefonda açık Instagram oturumu otomatik alınmaz; APK içinde yeni mobil parola/CAA girişi bu sürümde yoktur. Önceki WebView bağlantısı liste okuyucusuna bağlanmadı.

## 1. Kurulum ve mobil bağlantı

1. `artifacts/InstagramTracker-0.1.8.apk` imzalı önizlemesini eski üretim APK'sının üzerine yükle. Eski uygulamayı kaldırma veya verilerini temizleme; oturum ve önceki mola korunmalı. Debug DEMO uygulamasının örnek kişileri gerçek veri değildir.
2. Kendi hesabını açılışta otomatik yenileme ve kişi izlemeleri ilk canlı testte kapalı kalsın. Küçük, erişebildiğin bir test profili kullan. Herhangi bir kısıtlama/429/doğrulama yanıtında testi bitir; mola kaydını silerek deneme.
3. Başarıyla bağlanmış mobil oturum varsa 2. bölüme geç. Yalnız eski web bağlantısı varsa **⋯ → Bağlantıyı yönet → Başka hesap bağla → Bilgisayardan bağlan** kullan. Bağlantı yoksa **Hesap bağla → Bilgisayardan bağlan** seç.
4. **Bağlantıyı hazırla** seç. Telefonu USB ile bağla, USB hata ayıklama erişimini telefonda kendin onayla.
5. Bilgisayarda kayıtlı `InstagramCounter` oturumu bulunmalı. Yalnız aktarım için Windows uygulamasını yeniden açmak gerekmez. Kayıtlı oturum yoksa önce `InstagramCounter/start.ps1` penceresinde kendi hesabını bağlaman gerekir; bu ayrı adım canlı giriş/okuma yapar. Parola ve kodu sohbete yazma.
6. PowerShell'de cihazları listele:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

7. `TELEFON_SERI_NO` yerine telefonun seri numarasını yaz. `emulator-5580` test emülatörüdür, telefon değildir:

```powershell
& '.\scripts\link-desktop.ps1' -Serial 'TELEFON_SERI_NO'
```

8. APK'da **Aktarımı al** seç. Bu adım Instagram'a istek göndermez. Yerel mola izin verince **Bağlan** seç; bu adım tek profil okumasıyla oturumu doğrulamayı dener. Kendi sayıları gelirse liste testine geç. Hata gelirse başka uç/hesap deneme; mesajı ve **⋯ → İstek bilgileri** içindeki HTTP durumunu kaydet.

İlk eşleştirme bilgisayar ister. Başarılı bağlantıdan sonraki liste okumalarını APK doğrudan gönderir; bilgisayarda sunucu çalıştırılmaz. Bağlantı doğrulanmadan liste erişiminin çalıştığı iddia edilmez.

## 2. Bir takipçi sayfasını oku

1. **Kişiler → Kişi ekle** ile erişebildiğin küçük test profilini ekle. Bu adım ayrı bir profil sorgusudur; kayıtlı kişiyi yeniden ekleme.
2. **Analizi aç → Listeleri incele** seç. Kendi hesabın için **⋯ → Kendi listelerim** yolu da var.
3. İlk açılışta **Henüz liste okunmadı** görünmeli. Ekranı açmak istek göndermez. Önceki kayıt varsa onun sayısı/zamanı gösterilir.
4. Ortak süre dolunca **İlk sayfayı oku** düğmesine **bir kez** bas. Başarılıysa adlar ve `Alınan hesap: … · Sayfa: 1` gelir. İstekte 50 kayıt istenir; sunucunun 50 döndüreceği veya bunun güvenli kota olduğu söylenmez.
5. **Devam sayfası var** görünürse ilk sayfayı kontrol et. Devamını ayrıca sınamak istediğinde ortak süre dolunca **Sonraki sayfayı oku** düğmesine bir kez bas. Otomatik sayfa döngüsü yoktur.

Durumların anlamı:

- **Liste sonu gözlendi:** gezinme sona erdi; eksiksizlik veya tek anlık görüntü garantisi değil.
- **Instagram bu listeyi sınırladı:** kayıt kısmi; sonraki sayfa düğmesi sunulmaz. Testi bitir.
- **Devamı ve kapsamı bilinmiyor:** eksik/çelişkili devam bilgisi var; tam liste sayılmaz.
- **Sayfalama ilerlemedi:** boş devam sayfası veya döngü gözlendi; tekrar yapılmaz.
- **Mobil bağlantı gerekiyor:** kayıtlı bağlantı eski web yolunda; 1. bölümdeki mobil eşleştirme gerekir.

## 3. Takip edilenler ve karşılaştırma

Önceki okumada kısıtlama/hata olmadıysa **Takip edilenler** sekmesine geç. Sekme değişimi istek yapmaz. Ortak süre dolunca **İlk sayfayı oku** seç; bu, diğer yönde ayrı bir sayfa isteğidir.

İki yönde kayıt varsa **Alınan listeleri karşılaştır** etkinleşir: iki kayıtta ortak, yalnız alınan takipçi kaydında ve yalnız alınan takip edilen kaydında olan hesaplar gösterilir. Karşılaştırma sabit kimliklere dayanır. Eksik listeden kesin geri takip etmeme veya takipten çıkma sonucu üretilmez.

**Yeni okuma başlat**, yalnız seçili yönü başarılı yeni ilk sayfayla değiştirir. Başarısızlıkta eski kayıt korunur. Diğer yön daha eski olabilir; zamanlarını kontrol et.

## 4. Kalıcılık, istek sayısı ve iptal

- Kapatıp aynı hedefin listesini yeniden aç: kayıt/zaman gelmeli, yeni sayfa otomatik alınmamalı.
- Sekme değiştirmek veya karşılaştırmayı açmak istek başlatmamalı. Ana ekranda **⋯ → İstek bilgileri** mantıksal profil/liste denemelerini birlikte sayar; adım öncesi/sonrası farkına bak. Bu, toplam cihaz trafiği veya Instagram'ın sunucu sayacı değildir.
- Okuma sürerken **İptal** veya Geri seç: eski sayfalar korunmalı; geç yanıt liste kaydı oluşturmamalı. İptal, daha önce gönderilen isteği sunucudan geri alamaz.
- Çevrimdışıyken kayıtlı liste açılmalı; son okuma zamanı yeniymiş gibi değiştirilmemeli.

## Saklama ve mevcut sınırlar

Yalnız **son incelenen hedefin** iki yönlü liste kaydı şifreli saklanır. Başka hedefin başarılı ilk sayfası önceki hedefin liste kaydını değiştirir; sayaç geçmişleri ayrıdır. Geçmiş liste değişim bildirimi eklenmedi.

Liste kaydı mevcut şifreli altyapının 512 KiB bütçesini kullanır. Sınır aşılırsa önceki sayfalar korunur ve okuma tamamlanmış sayılmaz. Kimlik, ad ve yanıtta varsa resim adresi/gizlilik/doğrulanma alanları tutulur. Resim indirme veya üye başına profil sorgusu yoktur. Parola, token ve ham gövde loglanmaz. Bağlantıyı kaldırmak liste kaydını da siler.

## Sonucu iletmek için

Sürüm, bağlantı adımının sonucu, liste yönü, kayıt/sayfa sayısı, kapsam mesajı ve son HTTP durumu yeterlidir. Parola, doğrulama kodu, çerez, Authorization veya oturum dosyası paylaşma; gerçek kullanıcı adlarını gizleyebilirsin.

Otomatik testler sentetik veri kullanır; başarıları gerçek Instagram kabulünü kanıtlamaz. Yürütülen kontroller ve açık kapsam [VALIDATION.md](VALIDATION.md) içindedir.
