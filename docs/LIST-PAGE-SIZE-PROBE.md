# Liste sayfa boyutu: 4 Kasım 2026 canlı karşılaştırması

Windows 0.2.49 kaynak ağacındaki aynı mobil okuyucu ve kayıtlı test hesabı kullanıldı. Üretimdeki `count=50` değiştirilmedi. Kurulu instagrapi 3.2.0 kitaplığının `MAX_USER_COUNT=200` sabiti, Instagram sunucusunun garantili üst sınırı olarak yorumlanmadı.

## Gözlenen sonuç

- Küçük, kayıtlı bir profilin **takip edilenler** sayısı testin başında ve sonunda 144'tü. 50/100/200 için ayrı başlangıçtan ikişer sayfa okundu. **Altı sayfanın her biri 25 kişi** döndürdü; her ikili dizide toplam 50 benzersiz kişi, sıfır sayfalar arası tekrar ve geçerli devam adresi vardı. Tam liste okunmadı.
- Kullanıcının test için verdiği büyük profilin **takipçi** sayısı ilk okumada 9.644, sonraki okumada 9.645'ti. 50/100/200 için ilk sayfa karşılaştırıldı. **Üç isteğin her biri 50 kişi**, `should_limit_list_of_followers=true` ve devam adresi olmadan döndü. Bu yanıtlar tam liste değildir; 9.600 kişinin tamamı indirilemedi. İlk 50 denemesi ile sonraki 100/200 denemesi farklı kısa çalıştırmalarda yapıldı; profil sayısı bu sırada bir arttı.
- Test akışında toplam **16 HTTP isteği** gönderildi: sayfa okumaları ve başarılı oturum/hedef profil kontrolleri. Dokuz liste sayfasının hepsi HTTP 200 döndü; testte 429 veya ek doğrulama gözlenmedi. HTTP 200, listenin eksiksiz döndüğünün kanıtı değildir.

**Çıkarım:** Bu oturum/iki profil örneğinde yüksek `count`, daha fazla kişi getirmedi. Üretim ayarını 200'e yükseltmek için bir hız kazanımı kanıtı yoktur. Bu, bütün Instagram hesapları için 25/50 kişilik evrensel üst sınır bulunduğu anlamına gelmez. Önceki teorik süre hesapları, sunucunun her sayfada talep edilen adedi döndürmesi varsayımına bağlıdır; canlı sonuçlar bu varsayımın her zaman sağlanmadığını gösterir.

Büyük profildeki eksiklik bu testte yerel kota/sayfa sınırından kaynaklanmadı: sunucu ilk sayfada liste kapsamını açıkça kısıtladı. Yerel istek kotasını yükseltmek veya aynı cevabı tekrar tekrar istemek, olmayan devam adresini üretmez. Alternatif uç nokta, GraphQL/web geri dönüşü, proxy veya yeni giriş denenmedi.

## Sınırlar ve veri koruma

Tanı aracı gerçek uygulama kapalıyken çalıştırıldı; aynı kasaya iki süreç yazmadı. Hesabın var olan şifreli oturumu, istek aralığı, bütçesi ve HTTP/429 kapısı kullanıldı. Yeni giriş, takip/beğeni/mesaj işlemi yapılmadı. Sayfa karşılaştırması yalnız seçilen `count` ve onun devam adresini değiştirdi.

Kapsamı kısıtlı yanıtta o boyutun sayfalaması durur; karşılaştırma yalnız sonraki açıkça seçilen boyutun ilk sayfasına geçebilir. HTTP sınırlaması, doğrulama, oturum veya taşıma hatasında sonraki boyuta geçilmez ve otomatik tekrar yapılmaz. Sonuçlar eski tam liste kaydına/karşılaştırma başlangıcına yazılmaz. Güncellenen oturum ve istek sınırları gerçek şifreli depoda korunur.

Yerel JSON raporları yalnız sayılar, boyutlar, süreler ve kapsam olgularını içerir; üye adları/kimlikleri, çerezler, ham yanıtlar ve devam adresleri yazılmaz. Rapor üst bilgisindeki test hesabı/profil adları nedeniyle dosyalar `artifacts/` altında yereldir ve Git'e gönderilmez. Bu notta hedefler adlandırılmamıştır. `decodedJsonBytes`, yeniden kodlanan JSON boyutudur; sıkıştırılmış ağ aktarım boyutu değildir. Ölçülen süreler mevcut 6–10 saniyelik istek aralığı beklemesini de içerir; yalın sunucu gecikmesi ölçümü değildir.

## Araç ve doğrulama

`scripts/probe-list-page-size.py`, yalnız 50/100/200 boyutlarını ve boyut başına en fazla dört sayfayı kabul eder. Kayıtlı hesap gerekir; yeni giriş yapmaz. Masaüstü uygulaması veya worker açıksa başlamaz. Açıkça verilen profil adı doğrulanır. Test hesabı/profil seçimi kullanıcı tarafından yapılmalıdır.

```powershell
python scripts/probe-list-page-size.py --account TEST_ACCOUNT --target ALLOWED_PROFILE --direction following --pages 2 --output artifacts/page-size-probe.json
python scripts/test_probe_list_page_size.py -v
```

Dört çevrimdışı regresyon geçti: boyutlara göre yeni başlangıç ve doğru cursor, kısıtlı cursor'ın izlenmemesi, başarısızlık sonrası önceki ölçümlerin korunması/tekrar yapılmaması ve geçersiz sınırların ağ çağrısından önce reddi. Android canlı karşılaştırması yapılmadı; aynı sonuçların her platform/hesap için geçerli olduğu varsayılmaz.
