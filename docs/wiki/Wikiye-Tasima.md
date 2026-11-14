# Kılavuzu GitHub Wiki'ye Taşıma

[Kılavuz ana sayfası](Home.md) · [Teknik dokümantasyon](../README.md)

Bu sayfa proje yöneticileri içindir. Kullanıcı kılavuzu şu anda ana deponun `docs/wiki/` dizininde tutulur; GitHub Wiki'ye henüz yayımlanmış değildir.

## Ne zaman kullanılabilir?

GitHub Free'de Wiki herkese açık depolarda kullanılabilir. Özel depoda Wiki için uygun ücretli plan gerekir. Bu kılavuzu hazırlamak veya dışa aktarmak depo görünürlüğünü değiştirmez. [GitHub Wiki belgeleri](https://docs.github.com/en/communities/documenting-your-project-with-wikis/about-wikis).

## Kaynak ve çıktı

- Düzenlenecek asıl içerik: `docs/wiki/`.
- Başlangıç sayfası: `Home.md`.
- Wiki gezinmesi: `_Sidebar.md` ve `_Footer.md`.
- Ekran görselleri: `assets/`; hepsi sentetik hesap verisidir.
- Yerel denetim ve dışa aktarma: `scripts/prepare-wiki.py`.

Windows görsellerini yenilemek için depo kökünden `node interface/scripts/capture-docs.mjs` komutunu çalıştırın. İlk açılış ve ZIP akışı için `node interface/scripts/capture-archive-docs.mjs <EXE-yolu> docs/wiki/assets` komutuna güncel Windows paketindeki EXE’yi verin. Her iki komut da örnek veri kullanır. Gerekirse `TRACKER_BUILD_PYTHON` ile proje bağımlılıklarını içeren Python’u seçin.

Araştırma günlükleri, yerel paketler, test raporlarının özel ayrıntıları ve gerçek hesap kayıtları dışa aktarma kapsamına girmez. Teknik dokümantasyona verilen bağlantılar, kaynak depodaki dosyalara yönlenir; okuyucunun o depoya erişimi gerekir.

Sayfa adları dosya adlarından gelir. Türkçe başlıklara verilen önemli bölüm bağlantıları, GitHub'ın desteklediği açık HTML çapalarıyla sabitlenmiştir. [GitHub bağlantı ve çapa sözdizimi](https://docs.github.com/en/get-started/writing-on-github/getting-started-with-writing-and-formatting-on-github/basic-writing-and-formatting-syntax).

## 1. İçeriği denetleyin

Depo kökünde Python 3.11+ ile:

```sh
python scripts/prepare-wiki.py --check
```

Denetim, kılavuzun yerel bağlantılarını, bölüm hedeflerini ve görsellerini kontrol eder. GitHub'a istek göndermez. Sürüm durumunu ve sentetik ekran örneklerinin güncelliğini yayın öncesinde ayrıca gözden geçirin.

## 2. Yayımlanabilir dosyaları hazırlayın

```sh
python scripts/prepare-wiki.py --output interface/.cache/wiki-export
```

Araç Markdown sayfalarını ve görselleri ayrı çıktı klasörüne kopyalar. Sayfa bağlantılarını Wiki adreslerine; teknik kaynak/görsel bağlantılarını kaynak deponun belirtilen Git referansına dönüştürür. `_Sidebar.md` ve `_Footer.md` korunur.

Varsayılan kaynak referansı çalışılan Git dalıdır. Farklı bir referans için `--ref` kullanılabilir. `--repository KaanKaya17/InstagramTracker` hedefi açıkça seçer. Farklı bir depoya aktarırken görsellerin bulunduğu kaynak deposunu da buna göre düzenleyin.

Görsel adresleri, `?raw=true` ile kaynak depodaki PNG dosyalarına yönlenir; dosyalar ayrıca çıktıdaki `assets/` klasöründe bulunur. Özellikle özel depoda, Wiki açıldığında görsellerin okuyucu yetkileriyle yüklendiğini kontrol edin. [GitHub'ın Wiki görsel açıklaması](https://docs.github.com/en/communities/documenting-your-project-with-wikis/editing-wiki-content).

Çıktı zaten varsa araç yalnız kendi manifestiyle sahipliğini doğruladığı dosyaları günceller; değişen önceki dosyaları yedekler. Başka bir klasörün dosyalarını sessizce değiştirmez. Bu komut Wiki'yi açmaz veya yayımlamaz.

## 3. Wiki kullanılabilir olduğunda

1. GitHub'da Wiki özelliğini etkinleştirin ve ilk sayfayı oluşturun.
2. Wiki deposunu GitHub'ın verdiği `.wiki.git` adresinden klonlayın.
3. Hazırlanan Markdown dosyaları ile `assets/` klasörünü klona kopyalayın. Dışa aktarma manifesti/yedeklerini Wiki'ye eklemeyin.
4. Farkları, sayfa bağlantılarını ve görselleri inceleyin.
5. Değişiklikleri Wiki'nin varsayılan dalına commit/push ederek yayımlayın.

GitHub, Wiki'yi klonlamak için önce ilk sayfanın oluşturulmasını tarif eder. Wiki içeriğini yalnız varsayılan dala gönderilen değişiklikler günceller. [GitHub'ın yerel Wiki düzenleme adımları](https://docs.github.com/en/communities/documenting-your-project-with-wikis/adding-or-editing-wiki-pages).

## Bakım kuralı

Yeni sürümde önce kaynak kılavuzu güncelleyin; ardından denetim ve dışa aktarmayı tekrar çalıştırın. Wiki'de doğrudan yapılan bir düzenleme kaynak kılavuza da alınmalıdır. Önizleme veya test başarısını canlı hesap uyumluluğu gibi sunmayın.

Ekran örneklerini yeniden üretmek için Windows masaüstü geliştirme ortamındaki kilitli Python bağımlılıkları, arayüz geliştirme bağımlılıkları ve yerel Chromium tabanlı tarayıcı gerekir. `TRACKER_BUILD_PYTHON` ile Python'u, gerekirse `TRACKER_DOCS_BROWSER` ile tarayıcı EXE'sini seçip `interface/` içinde `node scripts/capture-docs.mjs` çalıştırın. Araç gerçek hesap deposu veya worker açmaz; üretim arayüzünü bellekteki sentetik verilerle görüntüler ve dış HTTP isteklerini engeller.
