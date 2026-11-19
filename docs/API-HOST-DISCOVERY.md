# Instagram istek adreslerini HAR kaydından bulma

`scripts/discover-list-hosts.py` yerel HAR kaydında profil, takipçi, takip edilen ve parola şifreleme anahtarı isteklerini bulur. Alan adı ve yol, HTTPS `instagram.com` ve alt alan adlarında gözlenen trafik üzerinden belirlenir. Tarayıcı HAR kaydı tarayıcının kullandığı adresleri gösterir. Windows worker veya Android’in kullandığı adres için o istemciye ait bir kayıt gerekir.

## Uygulamadan gelen rapor

Windows, bir okuma üç kez üst üste uyumsuz yanıt alıp durduğunda kullanıcıya **Geliştiriciye bildir** raporu sunar. Raporda istek yolu şablonu, neden, HTTP durumu ve eksik alan adları vardır; hesap verisi yoktur. Bu rapor hangi okuma için HAR kaydı alınacağını gösterir. Rapordaki `Request` satırı, aracın bulduğu adresle karşılaştırılacak uygulama adresidir. [Kullanıcı tarafı](wiki/Sorun-Giderme.md#otomatik-okuma-duraklatıldı)

## Test kaydını alma

1. Bir test hesabıyla Instagram web'de oturum açın. Test profilini açın ve DevTools'ta **Network** kaydını temizleyin.
2. Yalnız bir liste yönünü açın. İlk sayfayı ve aşağı kaydırarak sonraki sayfayı aynı Network kaydında tutun. Ardından **Export HAR (sanitized)** ile tek dosya kaydedin. Diğer yön için aynı adımları ayrı bir kayıtta yapın.
3. Daha önce iki ayrı dosya aldıysanız bunları da kullanabilirsiniz. İlk sayfanın kaydını önce, sonraki sayfanın kaydını ikinci sırada verin.

Parola anahtarı incelemesi için istemcinin yalnız anahtar isteğini içeren, hesap ve parola kullanılmadan alınmış bir test kaydı yeterlidir. İstekle birlikte HTTP durumu ve anahtar yanıt başlıkları kayıtta yer almalıdır.

Profil isteği için test profilini açarken yapılan ilk isteği ve yanıt gövdesini kaydedin. Araç, istekteki sayısal hedef veya kullanıcı adını, takipçi ve takip edilen sayaçlarını içeren profil yanıtıyla eşleştirir.

Chrome'un temizlenmiş HAR seçeneği çerez ve yetkilendirme başlıklarını çıkarır. Form, sorgu ve yanıt gövdelerinde hesap verileri kalabileceğinden HAR dosyalarını yerelde tutun. [Chrome HAR dışa aktarma kılavuzu](https://developer.chrome.com/docs/devtools/network/reference/#save-all-network-requests-to-a-har-file)

## Yerel analiz

Komutları depo kökünden Python 3.11 veya üzeriyle çalıştırın.

```sh
python scripts/discover-list-hosts.py auto kayit.har
python scripts/discover-list-hosts.py profile profil.har
python scripts/discover-list-hosts.py followers takipciler.har
python scripts/discover-list-hosts.py followers ilk.har sonraki.har
python scripts/discover-list-hosts.py password-key guncel.har --previous eski.har
python scripts/discover-list-hosts.py following ilk.har sonraki.har --previous eski-ilk.har eski-sonraki.har
```

- `auto`, profil, iki liste yönü ve parola anahtarı isteklerini ayrı ayrı değerlendirir. Liste yönünü yoldan veya GraphQL yanıtından belirleyebildiği istekleri eşleştirir. Yön bilgisi kayıttan anlaşılmıyorsa açıkça `followers` veya `following` kullanın.
- `profile`, hedefi ve sayaçları doğrulanabilen HTTP 200 yanıtlarını eşleştirir. Aynı profil yolu üzerinde farklı hesaplara yapılan istekler tek adayda toplanır.
- Liste sonucunda `observed`, ilk yanıtın devam belirteciyle eşleşen tek sonraki sayfanın bulunduğunu gösterir. Hedef iki istekte de tanınabiliyorsa eşitliği kontrol edilir. `same_target: null` hedefin kayıttan doğrulanamadığını, `end-marker` sunucunun son sayfa işareti döndürdüğünü gösterir. Bu işaret liste kapsamını veya profil sayacını doğrulamaz.
- `password-key`, uygun HTTP durumu ve beklenen iki anahtar başlığıyla gözlenen alan adı, yöntem ve yol şablonlarını listeler. Aynı adrese ait tekrarlar tek adayda toplanır. Bilinen anahtar yollarındaki HTTP 404 veya eksik başlıklar `failures` alanında görünür.
- `ambiguous`, birden fazla eşleşme veya adres gözlendiğini gösterir. `insufficient` için ikinci sayfa, tanınan yanıt biçimi, devam belirteci veya anahtar başlığı eksik olabilir. Rapor, ilgili kayda dönmek için `entry` alanında 1’den başlayan HAR girdi numarasını verir.
- `--previous` bir birleşik HAR veya sayfa sırasına göre iki HAR kabul eder. Eski ve yeni kayıtlar tek aday verdiğinde alan adı, yöntem, yol şablonu ve varsa sayısal GraphQL sorgu kimliği farklarını gösterir. Tanınmayan yol parçaları maskelenir. Gerçek yol gerekiyorsa ilgili yerel HAR girdisine bakın.

Araç çevrimdışı çalışır ve JSON raporu üretir. Çerez, anahtar başlığı değeri, hesap kimliği, kullanıcı adı, ham gövde ve devam belirteci rapordan çıkarılır. Üretim adresi değişikliği için ilgili Tracker istemcisinin bağlantı testi gerekir.

## Doğrulama durumu

11 Kasım 2026 tarihli Chrome kaydında takip edilenlerin ilk iki isteği aynı hedef ve devam belirteciyle eşleşti. İki istekte de `GET https://www.instagram.com/api/v1/friendships/{id}/following/` görüldü ve her yanıtta 12 satır ile devam bilgisi vardı. İlk yanıt HAR içinde Base64 kodluydu. Analiz hem Base64 hem düz JSON gövdelerini destekler.

12 Kasım 2026 tarihli tek Chrome kaydında takipçilerin ilk iki isteği aynı hedef ve devam belirteciyle eşleşti. Kayıttaki 4. ve 10. istekler `GET https://www.instagram.com/api/v1/friendships/{id}/followers/` yolunu kullandı. Her yanıt 12 satır ve devam bilgisi içerdi. Bu gözlem takipçi listesinin ilk iki sayfasını kapsar.

15 Kasım 2026’da Windows worker’ın kullandığı parola anahtarı isteği, hesap ve parola kullanılmadan tek sefer çalıştırıldı. Araç geçici HAR kaydında `POST https://i.instagram.com/api/v1/launcher/sync/` isteğini HTTP 200 ile tek aday olarak buldu.

15 sentetik HAR testi iki liste yönünün otomatik ayrımını, profil hedefi eşleşmesini, anahtar yolu değişikliğini, HTTP 404 ve 429 tanısını, GraphQL ve boş son sayfaları, kodlamayı, belirsiz eşleşmeleri ve hassas değerlerin raporda yer almamasını kapsar. Chrome’da iki liste yönü doğrulandı. Profil modunun gerçek HAR kaydıyla ve Android’in canlı ağ kaydıyla doğrulanması bekleniyor.

```sh
python -m unittest discover -s scripts -p test_discover_list_hosts.py -v
```
