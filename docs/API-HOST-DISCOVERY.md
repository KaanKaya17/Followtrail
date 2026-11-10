# Instagram liste isteklerinin alan adını bulma

`scripts/discover-list-hosts.py`, geliştiricinin yerel test kaydında takipçi veya takip edilen listesinin ilk iki sayfasına ait isteği bulur. Alan adı önceden sabitlenmez. Araç gözlenen HTTPS `instagram.com` ve alt alan adlarını inceler. Web tarayıcısından gelen HAR dosyası ilk kullanım yoludur. Android'den canlı kayıt alma ve Windows/Android okuyucularıyla yeni adresi doğrulama ayrı işlerdir.

## Test kaydını alma

1. Bir test hesabıyla Instagram web'de oturum açın. Test profilini açın ve DevTools'ta **Network** kaydını temizleyin.
2. Yalnız bir liste yönünü açın. İlk sayfa yüklendiğinde **Export HAR (sanitized)** ile `ilk.har` kaydedin.
3. Liste açıkken Network kaydını temizleyin. Bir sonraki sayfa yüklenene kadar aşağı kaydırın ve ikinci kaydı `sonraki.har` olarak dışa aktarın. Diğer yön için aynı adımları ayrı dosyalarla yapın.

Chrome'un temizlenmiş HAR seçeneği çerez ve yetkilendirme başlıklarını çıkarır. Form, sorgu ve yanıt gövdelerinde hesap verileri kalabileceğinden HAR dosyalarını yerelde tutun. [Chrome HAR dışa aktarma kılavuzu](https://developer.chrome.com/docs/devtools/network/reference/#save-all-network-requests-to-a-har-file)

## Yerel analiz

Python 3.11+ ile depo kökünden çalıştırın:

```sh
python scripts/discover-list-hosts.py followers ilk.har sonraki.har
python scripts/discover-list-hosts.py following ilk.har sonraki.har --previous eski-ilk.har eski-sonraki.har
```

- `observed`, ilk yanıtın devam belirteciyle eşleşen tek ikinci sayfa bulunduğunu gösterir. Hedef iki istekte de tanınabiliyorsa araç eşitliğini kontrol eder. `same_target: null` hedef eşleşmesinin bilinmediğini gösterir.
- `ambiguous`, birden fazla eşleşme olduğu anlamına gelir. `insufficient` için ikinci sayfa, tanınan yanıt biçimi veya devam belirteci eksik olabilir.
- Rapor, gözlenen alan adını, yöntem ve yol şablonunu, varsa sayısal sorgu kimliğini, eşleşen HAR satır numaralarını ve yanıt sinyalini verir. `direction_evidence: capture` yönün yalnız operatörün seçtiği kayıt etiketine dayandığını gösterir. `end-marker` yalnız sunucunun devam işaretini anlatır. Liste kapsamı veya gerçek profil sayacı bu analizde doğrulanmaz.
- `--previous` kullanıldığında iki kayıt çifti de tek eşleşme verdiyse alan adı, yöntem, yol şablonu ve sorgu kimliği farkları görünür. Sayısal hesap kimlikleri ve tanınmayan yol parçaları maskelenir. Yeni bir yol parçasının gerçek adı gerekirse yerel HAR'daki ilgili satırdan kontrol edilir.

Araç çevrimdışı çalışır ve yalnız JSON raporu üretir. Raporda çerez, erişim anahtarı, üye kimliği, kullanıcı adı, ham gövde veya devam belirteci yer almaz. Gözlenen adresler Tracker'a aktarılmadan önce ilgili platformun bağlantısıyla ayrıca doğrulanır.

## Doğrulama durumu

Sentetik HAR testleri yeni alt alan adı ve yol, GraphQL sorgu kimliği, yanlış hedef, belirsiz eşleşme ve kısıtlı liste davranışını kapsar. İlk prototip güncel bir canlı Instagram kaydıyla henüz doğrulanmadı.

```sh
python -m unittest discover -s scripts -p test_discover_list_hosts.py -v
```
