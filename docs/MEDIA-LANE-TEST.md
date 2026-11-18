# Hikâye medyası için ayrı indirme şeridi — Windows 0.2.90 / Android 0.1.103

18 Kasım 2026. Hikâye fotoğraf ve videoları Meta CDN'den çerezsiz ve oturumsuz indirilir. Önceki sürümlerde bu indirmeler Instagram API isteğiyle aynı 6–10 saniyelik sırayı ve aynı hesap/cihaz bütçesini kullanıyordu. Bu sürümde medya kendi şeridinde ilerler.

## Değişen davranış

- **API şeridi değişmedi.** 6–10 saniye aralık, hesap başına 120/3000 ve cihaz başına 240/6000 kayan saat/gün bütçesi, %80 otomatik profil eşiği, 429/Retry-After ve elle durma kuralları aynı.
- **Medya şeridi:** Her CDN indirmesi arasında **1–3 saniye** rastgele aralık. Cihaz genelinde ayrı **400/saat, 4.000/gün** medya sınırı. Medya indirmesi API bütçesinden hak harcamaz, API rezervasyonunu ileri taşımaz ve API deneme sayacını artırmaz.
- **Güvenlik bağı korunur.** API molası (429 sonrası bekleme veya elle durma) sürerken medya indirilmez. CDN'den gelen bir HTTP 429, API ile aynı ortak molayı başlatır. Medya sınırı dolarsa yalnız indirmeler durur; hikâye bilgileri kaydedilir ve eksik dosya sonraki kontrolde indirilebilir.
- Aynı anda tek ağ isteği kuralı değişmedi. Medya ve API istekleri paralel gönderilmez; değişen, medyanın API'nin uzun aralığını beklememesidir.
- Değerlerin sahibi `design/request-policy.json`. Python ve Kotlin sabitleri `node interface/scripts/generate-request-policy.mjs` ile üretilir ve `npm run check` farkları yakalar.

## Çevrimdışı ölçüm

Komut: `python scripts/audit-media-lane.py`. Betik gerçek Windows Engine'ini, hikâye ayrıştırıcısını, DPAPI hikâye deposunu ve istek kapısını sentetik taşıyıcıyla çalıştırır. DNS ve soket bağlantısı kapalıdır. Karşılaştırma için API aralığı sabit 8 saniye, medya aralığı sabit 2 saniye alındı. Bunlar politika aralıklarının orta değerleridir.

| Hikâye öğesi | API bütçesi, önce → sonra | Sanal bekleme, önce → sonra |
| --- | --- | --- |
| 2 | 3 → 1 | 16 sn → 2 sn |
| 10 | 11 → 1 | 80 sn → 18 sn |
| 50 | 51 → 1 | 400 sn → 98 sn |

Her senaryoda Instagram API'sine giden istek sayısı aynıdır (1). CDN indirme sayısı da aynıdır. Kazanç, API bütçesinin profil ve liste kontrollerine kalması ve hikâye kontrolünün daha kısa sürmesidir. Sanal bekleme gerçek ağ süresi değildir. Meta'nın CDN veya API tarafında hangi sıklığı güvenli saydığı bu ölçümle belirlenmez.

## Doğrulama

- Windows: 46 paketli çevrimdışı kontrol, 310 çevrimdışı Python testi (Python 3.12 ve 3.14), 71 masaüstü JavaScript testi ve `npm run check` geçti. Yeni testler medya şeridinin API sırasını beklemediğini, API molasında indirmenin başlamadığını, medya sınırı dolunca hikâye bilgisinin korunup API bütçesinin harcanmadığını, CDN 429'un ortak molayı başlattığını ve bozuk medya kayıtlarının reddedildiğini sınar.
- Android: 196 JVM testi ve release lint (0 hata) geçti. Yeni `MediaLaneTest` cihaz testi ve mevcut hikâye arşivi testleri Android 16 emülatöründe geçti: hikâye kontrolü 1 API hakkı harcar, üç medya dosyası yalnız iki 2 saniyelik medya aralığı bekler. Tam cihaz paketinde 302 testten 301’i geçti; `MemberProfileFlowTest` içindeki 1,8 saniyelik bekleme metnini yakalayan test yük altında kaçırdı, tek başına üç kez geçti. Bu test medya yolunu kullanmaz.
- Gerçek Instagram hesabıyla canlı hikâye kontrolü bu sürümde yapılmadı.
