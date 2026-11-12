# Instagram liste isteklerinin alan adını bulma

`scripts/discover-list-hosts.py`, geliştiricinin yerel test kaydında takipçi veya takip edilen listesinin ilk iki sayfasına ait isteği bulur. Alan adı önceden sabitlenmez. Araç gözlenen HTTPS `instagram.com` ve alt alan adlarını inceler. Web tarayıcısından gelen HAR dosyası ilk kullanım yoludur. Android'den canlı kayıt alma ve Windows/Android okuyucularıyla yeni adresi doğrulama ayrı işlerdir.

## Test kaydını alma

1. Bir test hesabıyla Instagram web'de oturum açın. Test profilini açın ve DevTools'ta **Network** kaydını temizleyin.
2. Yalnız bir liste yönünü açın. İlk sayfayı ve aşağı kaydırarak sonraki sayfayı aynı Network kaydında tutun. Ardından **Export HAR (sanitized)** ile tek dosya kaydedin. Diğer yön için aynı adımları ayrı bir kayıtta yapın.
3. Daha önce iki ayrı dosya aldıysanız bunları da kullanabilirsiniz. İlk sayfanın kaydını önce, sonraki sayfanın kaydını ikinci sırada verin.

Chrome'un temizlenmiş HAR seçeneği çerez ve yetkilendirme başlıklarını çıkarır. Form, sorgu ve yanıt gövdelerinde hesap verileri kalabileceğinden HAR dosyalarını yerelde tutun. [Chrome HAR dışa aktarma kılavuzu](https://developer.chrome.com/docs/devtools/network/reference/#save-all-network-requests-to-a-har-file)

## Yerel analiz

Python 3.11+ ile depo kökünden çalıştırın:

```sh
python scripts/discover-list-hosts.py followers takipciler.har
python scripts/discover-list-hosts.py followers ilk.har sonraki.har
python scripts/discover-list-hosts.py following ilk.har sonraki.har --previous eski-ilk.har eski-sonraki.har
```

- `observed`, ilk yanıtın devam belirteciyle eşleşen tek ikinci sayfa bulunduğunu gösterir. Hedef iki istekte de tanınabiliyorsa araç eşitliğini kontrol eder. `same_target: null` hedef eşleşmesinin bilinmediğini gösterir.
- `ambiguous`, birden fazla eşleşme olduğu anlamına gelir. `insufficient` için ikinci sayfa, tanınan yanıt biçimi veya devam belirteci eksik olabilir.
- Rapor, gözlenen alan adını, yöntem ve yol şablonunu, varsa sayısal sorgu kimliğini, eşleşen HAR satır numaralarını ve yanıt sinyalini verir. `direction_evidence: capture` yönün yalnız operatörün seçtiği kayıt etiketine dayandığını gösterir. `end-marker` yalnız sunucunun devam işaretini anlatır. Liste kapsamı veya gerçek profil sayacı bu analizde doğrulanmaz.
- `--previous` kullanıldığında iki kayıt çifti de tek eşleşme verdiyse alan adı, yöntem, yol şablonu ve sorgu kimliği farkları görünür. Sayısal hesap kimlikleri ve tanınmayan yol parçaları maskelenir. Yeni bir yol parçasının gerçek adı gerekirse yerel HAR'daki ilgili satırdan kontrol edilir.

Araç çevrimdışı çalışır ve yalnız JSON raporu üretir. Raporda çerez, erişim anahtarı, üye kimliği, kullanıcı adı, ham gövde veya devam belirteci yer almaz. Gözlenen adresler Tracker'a aktarılmadan önce ilgili platformun bağlantısıyla ayrıca doğrulanır.

## Doğrulama durumu

11 Kasım 2026 tarihli Chrome kaydında takip edilenlerin ilk iki isteği aynı hedef ve devam belirteciyle eşleşti. İki istekte de `GET https://www.instagram.com/api/v1/friendships/{id}/following/` görüldü ve her yanıtta 12 satır ile devam bilgisi vardı. İlk yanıt HAR içinde Base64 kodluydu. Analiz hem Base64 hem düz JSON gövdelerini destekler.

Yedi sentetik HAR testi yeni alt alan adı ve yol, GraphQL sorgu kimliği, yanlış hedef, belirsiz eşleşme, kısıtlı liste, gövde kodlaması ve tek dosyada sayfa sırasını kapsar. Canlı gözlem takip edilenlerin ilk iki web sayfasını kapsar. Takipçiler ve Android kaydı ayrı doğrulanacak.

```sh
python -m unittest discover -s scripts -p test_discover_list_hosts.py -v
```
