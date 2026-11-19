# Followtrail geliştirme fikirleri — 19 Kasım 2026

Windows 0.2.95 / Android 0.1.103 kaynağı, belgeler, CI ve GitHub deposu incelenerek hazırlandı. Kod değişikliği yapılmadı. Her maddede **neden**, **nasıl** (mevcut koddan neyin kullanılacağı) ve kabaca **emek** (K = birkaç saat, O = 1–3 gün, B = 1 hafta+) var.

Ana gözlem: Proje teknik olarak çok olgun (777 dosya, 243 commit, ~60 backend testi, 4 dil, şifreli kasa, istek bütçesi). Ama **dağıtım ve görünürlük** zayıf (1 yıldız, 0 fork, imzasız EXE, otomatik güncelleme yok) ve **en güvenli mod olan ZIP modu** hesap bağlamalı modun yanında hâlâ "tek seferlik görüntüleyici". En yüksek getiri bu iki alanda.

---

## 1. Öncelik listesi (önerilen sıra)

| # | Fikir | Neden önemli | Emek |
|---|---|---|---|
| 1 | Çoklu ZIP takip geçmişi | Hesap bağlamadan, risksiz "kim geldi/gitti/geri döndü" geçmişi | O |
| 2 | Arşiv klasörünü izleme (prototipi uygulamaya taşı) | ZIP modunu otomatik hale getirir | O |
| 3 | Otomatik güncelleme bildirimi (isteğe bağlı) | Kullanıcılar eski sürümde kalıyor; Instagram değişikliklerinde kritik | K |
| 4 | Kod imzalama + winget | SmartScreen uyarısı en büyük kurulum engeli | O |
| 5 | Tarayıcıda çalışan ZIP görüntüleyici (GitHub Pages) | Kurulumsuz deneme = büyüme kanalı | B |
| 6 | Etkileşim analizi: "takip ettiğin ama hiç etkileşmediğin" | ZIP'te zaten var olan veriden yeni, çok istenen içgörü | O |
| 7 | Güvenlik ve giriş etkinliği paneli | ZIP'te var, hiç okunmuyor; gizlilik odaklı kitleye birebir | O |
| 8 | Yıl özeti (paylaşılabilir, kullanıcı adı içermeyen görsel) | Organik tanıtım | O |
| 9 | Release'i CI'da üretme + build attestation | "Yerel ve güvenilir" iddiasını kanıtlar | O |
| 10 | Dependabot/Renovate + feature request şablonu | Bakım ve topluluk | K |

---

## 2. ZIP modu: en güvenli büyüme alanı

ZIP modu Instagram'a istek göndermediği için kullanım şartı riski yok; README de zaten onu öneriyor. Ama şu an yalnız **bir ZIP'i inceleme** ve **iki ZIP'i karşılaştırma** yapıyor (`export_browser.py` → `prepare_comparison/compare`).

### 2.1 Çoklu ZIP takip geçmişi (★ en değerli)
- **Ne:** Kullanıcı farklı tarihlerdeki 3+ ZIP'i (veya bir klasörü) verir; uygulama her arşivi bir "tam tarama" sayar ve takipçi/takip edilen için kalıcı geçmiş çıkarır: ilk görülme, ayrılma aralığı, geri dönme.
- **Nasıl:** `membership.py` zaten "ilk/son görülme, ayrılanlar, geri gelenler" defterini tutuyor ve hesap bağlamalı moddaki **Geri gelenler / Ayrılanlar** filtresini besliyor. ZIP listelerini sentetik "tam tarama" olarak bu deftere vermek, aynı arayüzü ZIP kullanıcılarına açar. Tarama zamanı = ZIP adındaki tarih (`EXPORT_NAME`) veya `export_metadata`.
- **Dikkat:** Aynı tarihli iki ZIP'in sırası bilinmiyor (prototip belgesi bunu zaten not ediyor) → aynı günü tek tarama say. Kullanıcı adı değişikliği için mevcut "takip tarihi eşleşmesi" sezgisi kullanılabilir.
- **Kazanç:** "Hesabımı bağlamadan kim takipten çıktı, geçmişiyle görmek istiyorum" — ürünün en çok istenecek özelliği, sıfır hesap riskiyle.

### 2.2 Arşiv klasörü izleme
- `scripts/watch-instagram-exports.py` prototipi hazır, 13 Kasım'da ertelenmiş. 2.1 ile birlikte anlamlı hale geliyor: kullanıcı Instagram'da **Cihaza aktar** ile indirdiği ZIP'leri `İndirilenler`e bırakır, uygulama yeni ZIP'i fark edip geçmişe ekler ve "3 kişi takipten çıktı" bildirimi gönderir.
- Mevcut `notifications.cjs` ve tray altyapısı yeniden kullanılır. Varsayılan kapalı, klasör seçimi açık olmalı.
- Meta'nın **zamanlanmış dışa aktarım** (günlük/haftalık) seçeneği doğrulanırsa bu, "hesap bağlamadan yarı otomatik takip" demek.

### 2.3 Etkileşim analizi (kendi verinden)
ZIP'te beğeniler, yorumlar, kayıtlar, hikâye etkileşimleri ve mesajlar zaten okunuyor ama yalnız sayı/takvim olarak. Bunları takip listesiyle çaprazlamak yeni içgörü verir:
- **Takip ettiğin ama son 12 ayda hiç beğenmediğin/yorum yapmadığın hesaplar** ("sessiz takipler").
- **En çok etkileştiğin hesaplar** (beğeni + yorum + hikâye tepkisi), ve bunlardan seni takip etmeyenler.
- **Geri takip etmeyenler + takip süresi**: "3 yıldır takip ediyorsun, seni takip etmiyor".
- Hepsi yerel, ek istek yok. Sonuçlar CSV'ye (mevcut `comparison_csv` deseni) aktarılabilir.

### 2.4 Hesap güvenliği paneli
ZIP'te `security_and_login_information/` altında giriş etkinliği, oturum açılan cihazlar, parola/e-posta değişiklikleri, hesap gizlilik değişiklikleri var. Şu an yalnız arşiv tarayıcısında ham kayıt olarak görünüyor; ayrıştırılmıyor (`login_activity`, `password_change` kodda geçmiyor).
- **Ne:** "Hesabına son 90 günde 4 farklı cihaz/şehirden girildi" gibi bir zaman çizelgesi; tanımadığın cihazı işaretleme.
- Gizlilik odaklı kitleye birebir uyuyor ve hesap bağlamalı moda hiç ihtiyaç duymuyor.

### 2.5 Reklam ve ilgi alanları özeti
`ads_information/` şu an yalnız "Reklam bilgileri" etiketi alıyor. "Senin etkinliğini kullanan reklamverenler", "ilgi alanların", "gördüğün reklamlar" sayılabilir ve listelenebilir. Gizlilik farkındalığı için güçlü bir ekran; emek K–O.

### 2.6 Etkinlik ısı haritası
Beğeni/yorum/mesaj zaman damgalarından **haftanın günü × saat** ısı haritası ("en aktif olduğun saatler"). `daily-chart.js` çizim altyapısı var. Mesajlarda yalnız kendi gönderdiklerinin zamanı kullanılmalı (karşı tarafın verisi toplu bile olsa ayrı düşünülmeli).

### 2.7 Yıl özeti
"2026'da Followtrail": yeni takipçi sayısı, en çok etkileştiğin ay, toplam beğeni, en aktif saat. **Kullanıcı adı ve profil fotoğrafı içermeyen** bir PNG olarak dışa aktarılır. Kullanıcıların paylaşması, projenin tek organik tanıtım kanalı olabilir. Görselin altına küçük "followtrail" filigranı.

### 2.8 Rapor dışa aktarma
Mevcut CSV'ye ek olarak tek dosyalık, çevrimdışı açılan **HTML raporu** (veya PDF). "ZIP'imin özetini sakla/yazdır" ihtiyacı. Kullanıcı adları isteğe bağlı maskelensin.

---

## 3. Dağıtım ve güven

### 3.1 Güncelleme bildirimi (K)
`main.cjs`'de güncelleme kontrolü yok. Instagram okuma yollarını değiştirdiğinde (0.2.94'teki uyarı tam bu yüzden eklendi) kullanıcının yeni sürümü bilmesi kritik.
- İsteğe bağlı, varsayılan **açık ama ilk açılışta sorulan** bir ayar: günde bir kez `api.github.com/repos/KaanKaya17/Followtrail/releases/latest` okunur, kullanıcı verisi gönderilmez. Yeni sürüm varsa Özet'te kart + sürüm notu bağlantısı.
- Gizlilik belgesine "uygulamanın yaptığı tek proje bağlantısı" olarak açıkça yazılmalı. Tam otomatik kurulum (electron-updater) imzasız EXE ile anlamsız; önce bildirim yeterli.

### 3.2 Kod imzalama (O)
EXE imzasız → SmartScreen uyarısı, yeni kullanıcıların çoğunu burada kaybedersin.
- **SignPath Foundation** açık kaynak projelere ücretsiz Authenticode imzası veriyor (CI'dan üretilen build şartıyla — bkz. 3.4).
- Alternatif: Azure Artifact Signing (eski adı Trusted Signing), düşük aylık ücret.
- `scripts/sign-release.ps1` şu an yalnız Android APK imzalıyor ve içinde eski "InstagramTracker" adları var (yol ve `CN=` alanı); ad değişikliğiyle tutarlı hale getirilebilir (anahtar yolunu değiştirmeden).

### 3.3 winget / Scoop (K)
Taşınabilir ZIP için Scoop manifesti, imza sonrası winget manifesti. `winget install Followtrail` hem güven hem keşfedilebilirlik sağlar.

### 3.4 Release'i CI'da üretme + attestation (O)
Şu an paketleme yerel makinede yapılıyor (`windows-release-workflow`). GitHub Actions'ta `windows-2025` üzerinde paketleyip `actions/attest-build-provenance` ile kaynak→EXE kanıtı eklemek:
- "Bu EXE bu commit'ten üretildi" doğrulanabilir hale gelir — "hesap verini kimseye göndermiyoruz" iddiası için en güçlü kanıt.
- SignPath ücretsiz imzanın önkoşulu.
- SHA-256 zaten yayımlanıyor; attestation onu tamamlar.

### 3.5 Windows açılışında başlat (K)
Hesap bağlamalı modda düzenli kontrol için uygulama açık olmalı ama `openAtLogin` ayarı yok. Tray'de **"Windows ile başlat"** seçeneği (varsayılan kapalı). 2.2'deki klasör izleme için de gerekli.

---

## 4. Görünürlük ve topluluk

- **Tarayıcı ZIP görüntüleyici (B):** GitHub Pages üzerinde, dosyayı hiçbir yere yüklemeden tarayıcıda açan salt-ZIP sürüm. `interface/web` arayüzü zaten var; Python ayrıştırıcıları ya JS'e taşınır ya Pyodide ile çalıştırılır. "Kurmadan dene, beğenirsen Windows sürümünü indir." Bu, imzasız EXE engelini de aşar. Ağ isteği yapmadığı CSP ile kanıtlanabilir.
- **Android'i ZIP öncelikli yayımla:** Android girişi 429 nedeniyle aylardır askıda. Android'de ZIP modu (0.1.92+) zaten var. Hesap bağlamayı gizli/deneysel bırakıp "ZIP görüntüleyici" olarak bir kararlı sürüm çıkarmak, sonra F-Droid'e başvurmak (açık kaynak + telemetri yok profili F-Droid'e çok uygun).
- **Depo vitrini:** README'nin başına 10 saniyelik GIF (ZIP aç → karşılaştır → sonuç). Şu an yalnız durağan görüntüler var.
- **Issue şablonları:** Yalnız `bug.yml` var. `feature.yml` ve "Instagram dışa aktarım biçimi değişti" şablonu (kullanıcı adı içermeyen dosya ağacını yapıştırma) ekle — biçim değişikliklerini kullanıcılardan erken öğrenirsin.
- **GitHub Discussions** aç; "good first issue" etiketli küçük işler (çeviri, yeni ZIP kategorisi) bırak.
- **Çeviri:** 4 dil var. Weblate'in açık kaynak ücretsiz planı ile topluluk çevirisi (Almanca, Fransızca, Arapça, Endonezce, Hintçe Instagram'ın büyük pazarları).
- **Paylaşım yerleri:** r/privacy, r/DataHoarder, r/selfhosted, Hacker News "Show HN", Product Hunt. Vurgu: "yerel, açık kaynak, ZIP ile, hesap gerekmez".

---

## 5. Teknik borç ve sağlamlık

- **Bağımlılık güncellemesi:** `.github/` altında Dependabot/Renovate yok. Electron 45, Playwright, instagrapi, Gradle bağımlılıkları için haftalık PR. Electron güvenlik yamaları masaüstü uygulamada önemli.
- **ZIP ayrıştırıcı fuzz testi:** `instagram_archive.py` ve `export_browser.py` güvenilmeyen dosya okuyor. Sınırlar (`MAX_ZIP`, `MAX_ENTRY` …) iyi; `hypothesis` ile bozuk JSON/HTML, iç içe dizinler, aşırı uzun adlar, Unicode tuhaflıkları için özellik tabanlı test eklemek ucuz ve değerli.
- **Gerçek dışa aktarım biçimi değişikliği alarmı:** 0.2.93'teki `label_values` olayı gibi sessiz 0'lar tekrar olacak. ZIP'te tanınmayan ama "takip listesine benzeyen" dosya bulunduğunda **"Bu ZIP'te tanımadığımız bir liste var"** uyarısı + dosya yolunu (içeriği değil) raporlama.
- **Büyük dosyalar:** `interface/web/desktop.js` 2344 satır, `engine.py` 1312 satır. Son commit'lerdeki bölme (backup service, list scan adımları) iyi gidiyor; aynı yaklaşım `desktop.js` için (görünüm başına modül).
- **Backend metinleri:** `instagram_archive.py:23` gibi yerlerde hata metni Türkçe sabit. Arayüzde çevriliyor olabilir ama backend'in metin yerine **hata kodu** döndürmesi (`archive_invalid`) i18n'i tek yere toplar.
- **Tip kontrolü:** Python için `pyright`/`mypy` (en azından yeni modüllerde), JS için `// @ts-check` + `tsc --noEmit`. Büyük, testli ama tipsiz bir kod tabanında regresyonları ucuza yakalar.
- **Belge düzeni:** `docs/` kökünde ~75 dosya; çoğu tarihli test raporu. `docs/tests/` ve `docs/research/` altına taşımak, kullanıcı belgelerini (wiki) ve tasarım kararlarını bulmayı kolaylaştırır. Bağlantı kırılmaması için yönlendirme notu ya da toplu bağlantı güncellemesi.
- **Kayıt formatı sürümleme:** Kasalar ve yedekler için açık bir `schema_version` + göç testleri (eski sürümden açılan kasa) — ad değişikliğinde "kayıtlar olduğu gibi açılır" vaadini kalıcı güvenceye alır. (Mevcut `refactor-golden.json` buna iyi bir başlangıç.)

---

## 6. Hesap bağlamalı mod için (dikkatli, düşük öncelik)

Bu mod hesap kısıtlaması riski taşıdığı için yeni okuma eklemek yerine **mevcut okumaları daha verimli** kullanmak önerilir:
- **Değişiklik özeti e-postası yerine haftalık yerel özet bildirimi:** "Bu hafta +12 / −5, 2 kişi geri döndü." Ek istek yok, mevcut defterden.
- **Akıllı tarama zamanı:** Kullanıcının uygulamayı açtığı saatlere göre taramaları kaydırmak; gece taramayı azaltmak (istek bütçesine dokunmadan daha "insan" bir desen).
- **ZIP ↔ hesap birleştirme:** Hesap bağlı kullanıcı bir ZIP açtığında, ZIP'teki takip tarihleri mevcut defterdeki "ilk görülme" aralığını geçmişe doğru doldurabilir (yalnız kullanıcı onaylarsa, kimlik eşleşmesi doğrulanarak). Uygulamanın "ilk liste başlangıç kaydıdır" sınırını kısmen kaldırır.

---

## 7. Yapılmaması önerilenler

Projenin güven sermayesi "salt okuma, yerel, telemetri yok" sınırında. Şunlar o sınırı bozar ve hesapları riske atar:
- Profil ziyaretçisi gösterme (mümkün değil; vaat etmek güveni yıkar).
- Toplu takipten çıkma / takip etme otomasyonu.
- Başkalarının tam takip listelerini düzenli kazıma.
- Bulut senkronizasyonu (gerekirse yalnız kullanıcının kendi şifreli yedek dosyasını kendi Drive'ına koyması).
- Paylaşım profili / kişilik çıkarımı (backlog'da ertelenmiş "paylaşım profili ve video analizi"): başkaları hakkında çıkarım yaptığı için en hassas fikir; yapılacaksa yalnız kendi içeriğine uygulanmalı.

---

## 8. Önerilen ilk iki hafta

1. **Hafta 1:** Güncelleme bildirimi (3.1) → Dependabot + feature şablonu (5, 4) → Çoklu ZIP geçmişi tasarımı ve `membership.py` entegrasyonu (2.1).
2. **Hafta 2:** Çoklu ZIP geçmişinin arayüzü ve testleri → arşiv klasörü izleme (2.2) → CI'da release + attestation (3.4) → SignPath başvurusu (3.2).

Bu sırayla ilk sürüm (0.3.0) "Hesap bağlamadan takip geçmişi" başlığıyla duyurulabilir — projenin en net, en güvenli satış cümlesi.
