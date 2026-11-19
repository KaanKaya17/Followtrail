# Kod imzalama politikası

**Durum (19 Kasım 2026):** Windows sürümleri henüz Authenticode ile imzalı değildir. Yayın hattı imzaya hazırdır; [SignPath Foundation](https://signpath.org) başvurusu onaylandığında sonraki sürümler imzalı yayımlanır. O zamana kadar indirdiğin dosyayı [SHA-256 ve kaynak kanıtıyla](#indirdiğin-dosyayı-doğrula) doğrulayabilirsin.

## Sürümler nasıl üretilir

- Her Windows sürümü, `windows-vX.Y.Z` etiketinden GitHub Actions’ta temiz bir Windows çalıştırıcısında üretilir: [`.github/workflows/release-windows.yml`](../.github/workflows/release-windows.yml). Geliştiricinin bilgisayarında derlenen dosya yayımlanmaz.
- Python ve Node bağımlılıkları kilit dosyalarından kurulur (`requirements.lock.txt`, `build-requirements.lock.txt`, `package-lock.json`). Kullanılan GitHub Actions eylemleri commit SHA’sıyla sabitlenmiştir.
- Paket, yayımlanmadan önce aynı çalıştırıcıda test edilir: backend testleri, kaynak kontrolleri, paketli EXE üzerinde 46 işlev kontrolü ve ZIP inceleme kontrolü.
- İmzalanan dosyalar yalnız bu depodan üretilen iki programdır: `Followtrail.exe` ve `resources/app/worker/tracker-worker.exe`. İkisi de ürün adı **Followtrail** ve sürüm numarasını taşır. Electron ve Python çalışma zamanı dosyaları kendi üreticilerinin imzasıyla kalır. [İmza yapılandırması](../packaging/signpath/artifact-configuration.xml).
- İş akışı GitHub [kaynak kanıtı (build provenance attestation)](https://docs.github.com/actions/security-for-github-actions/using-artifact-attestations) yayımlar: ZIP’in hangi commit ve iş akışından üretildiği imzalı olarak kaydedilir.
- Sürüm **taslak** olarak oluşturulur. Bir sorumlu notları ve dosyaları inceleyip elle yayımlar. İmza isteği de her sürümde elle onaylanır.

## Roller

| Rol | Kişiler | Sorumluluk |
| --- | --- | --- |
| Kod sahibi ve gözden geçiren | [Kaan Kaya](https://github.com/KaanKaya17) | Depoya doğrudan değişiklik yapabilir. Dışarıdan gelen her katkıyı (pull request) birleştirmeden önce inceler. İş akışı ve derleme betikleri de kaynak kod gibi incelenir. |
| Onaylayan | [Kaan Kaya](https://github.com/KaanKaya17) | Her sürüm için imza isteğini onaylar. |

Tüm ekip üyeleri GitHub’da ve SignPath’te çok aşamalı kimlik doğrulaması (MFA) kullanır.

## Gizlilik

Bu program, kullanıcı veya programı kuran/çalıştıran kişi özellikle istemedikçe başka ağ sistemlerine bilgi aktarmaz.

- **ZIP modu** hiçbir ağ isteği yapmaz; dosya cihazdan çıkmaz.
- **Hesap bağlamalı mod** yalnız kullanıcı hesabını bağladığında ve kontrol başlattığında istekleri doğrudan Instagram’a gönderir. Uygulamanın kendi sunucusu, telemetrisi veya analitik SDK’sı yoktur.
- Hata raporu yalnız kullanıcı **GitHub’da bildir** veya **Metni kopyala** seçerse paylaşılır.

Ayrıntılar: [Gizlilik rehberi](wiki/Gizlilik-ve-Veriler.md) · [Teknik gizlilik notları](PRIVACY.md).

## İndirdiğin dosyayı doğrula

```powershell
Get-FileHash .\Followtrail-Desktop-X.Y.Z.zip -Algorithm SHA256
```

Çıkan değeri sürüm sayfasındaki `SHA256SUMS.txt` ile karşılaştır. GitHub Actions’ta üretilen sürümlerde kaynak kanıtını da denetleyebilirsin ([GitHub CLI](https://cli.github.com) gerekir):

```powershell
gh attestation verify .\Followtrail-Desktop-X.Y.Z.zip -R KaanKaya17/Followtrail
```

İmzalı sürümlerde EXE’ye sağ tıklayıp **Özellikler → Dijital İmzalar** sekmesinde imzayı görebilirsin.

## Onaydan sonra eklenecek metin

SignPath Foundation onayı geldiğinde bu bölüm, README’deki “Kod imzalama politikası” bağlantısı ve sürüm sayfaları şu cümleyi taşır:

> Free code signing provided by [SignPath.io](https://about.signpath.io), certificate by [SignPath Foundation](https://signpath.org).

Bu cümle onaydan önce yayımlanmaz.

---

## Sorumlu için: SignPath kurulumu

1. **Başvuru:** <https://signpath.org/apply>. Proje: Followtrail, depo `https://github.com/KaanKaya17/Followtrail`, lisans MIT, yayımlanan dosya `Followtrail-Desktop-X.Y.Z.zip`. Bu sayfayı kod imzalama politikası olarak göster.
2. **Dikkat:** SignPath Foundation onayı kendi takdirindedir. Koşullarında kötü amaçlı yazılım ve “güvenlik önlemlerini aşan” araçlar dışlanır. ZIP modu tamamen yereldir. Hesap bağlamalı mod Instagram’ın resmî olmayan arayüzlerini kullanır ve README’de bu açıkça yazar. Başvuruda bunu saklama: uygulamanın salt okuma olduğunu, istek sınırlarını, CAPTCHA’yı veya doğrulamayı aşmadığını ve kullanıcının kendi hesabıyla sınırlı olduğunu belirt.
3. **SignPath projesi:** Onaydan sonra bir proje, bir *signing policy* ve bu depodaki [artifact configuration](../packaging/signpath/artifact-configuration.xml) oluşturulur. GitHub bağlayıcısı (trusted build system) bu depoya bağlanır. *File metadata restrictions* ürün adını `Followtrail` olarak zorlar.
4. **GitHub ayarları** (Settings → Secrets and variables → Actions):
   - Secret: `SIGNPATH_API_TOKEN`
   - Variables: `SIGNPATH_ORGANIZATION_ID`, `SIGNPATH_PROJECT_SLUG`, `SIGNPATH_SIGNING_POLICY_SLUG`, `SIGNPATH_ARTIFACT_CONFIGURATION_SLUG`

   `SIGNPATH_ORGANIZATION_ID` boşken iş akışı imzasız paket üretir; doluyken etiketli her sürümde imza ister, onayı bekler ve iki EXE’nin imzasını `Get-AuthenticodeSignature` ile doğrular. İmza geçersizse sürüm oluşturulmaz.
