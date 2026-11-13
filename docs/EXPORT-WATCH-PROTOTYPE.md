# Instagram arşiv klasörü prototipi

Prototip, bir test hesabı için belirlenen yerel klasörü izler. Tam arşivlerde takipçi ve takip edilen listelerini önceki kayıtlarla karşılaştırır. Zamanlanmış teslimatlarda yalnız gelen kayıtları sayar. İşlemler cihazda yürür ve sonuçlar terminalde görünür.

## Başlatma

Test hesabına ayrılmış yerel bir klasör ve Python 3.10 veya üzeri kullanın. Aşağıdaki örnekte klasör yolunu ve kullanıcı adını değiştirin.

```powershell
python scripts/watch-instagram-exports.py 'C:\TrackerExport' --account test_hesabi --mode snapshot
```

Varsayılan klasör kontrolü beş saniyede bir yapılır. Yeni dosyanın boyutu ve değiştirilme zamanı iki kontrol boyunca aynı kaldığında okuyucu ZIP bütünlüğünü denetler. Mevcut dosyaları bir kez incelemek için `--once`, tam arşivler arasındaki değişen kullanıcı adlarını yerel çıktıda görmek için `--show-users` ekleyin. Varsayılan çıktı yalnız sayıları gösterir. İşlemi `Ctrl+C` ile durdurun.

## Karşılaştırma

- `--mode snapshot` ile yalnız tam arşivleri karşılaştırın. Zamanlanmış harici teslimatları `--mode incremental` ile açın. Bu mod yalnız teslim edilen satır sayısını gösterir ve takip değişikliği çıkarmak için bir başlangıç listesi oluşturmaz.
- Prototip, cihaz indirmesinde gözlenen `instagram-kullaniciadi-YYYY-MM-DD-ek.zip` adlandırmasını tanır. Test klasörünü tek hesaba ayırın ve her seferinde aynı kategori ve tarih aralığıyla dışa aktarın.
- Tam arşiv modunda ilk geçerli liste başlangıç kaydı olur. Daha sonraki farklı tarihli arşiv, kullanıcı adlarını karşılaştırır. Bir yön eksikse o yönün son geçerli kaydı korunur. Toplam değişmese de eklenen ve kaybolan adlar bulunabilir.
- Aynı tarihli farklı dosyaların sırası bilinmediği için prototip bu dosyalardan yeni değişiklik üretmez. Bozuk veya tamamlanmamış ZIP, yeni bir sürümü geldiğinde tekrar okunabilir. Önceki liste bilgisi süreç belleğinde tutulur. Yeniden başlatınca klasördeki dosyalar tekrar değerlendirilir.
- Arşiv adındaki hesap ve tarih operatörün seçtiği kaynağı tanımlar. Kullanıcı adı karşılaştırması hesap kimliğini veya kesin olay saatini doğrulamaz.

## Meta teslimatı

Meta, uygun hesaplarda harici depolamaya düzenli dışa aktarımı belgeliyor. Test hesabının ekranında bir defalık, günlük, haftalık, aylık ve yıllık seçenekler görüldü. Ekran, zamanlanmış arşivlerin önceki aktarımda bulunmayan bilgileri içerdiğini belirtiyor. Google Drive’a teslim edilen dosyanın kapsamı ve varsa açık takip ekleme veya kaldırma olayları henüz doğrulanmadı. Yerel prototip için kullanılan arşiv adı cihaz indirmesinden gözlendi. Harici hedef farklı bir ad veya dosya yapısı kullanabilir. [Meta'nın 2026 veri taşınabilirliği raporu](https://ppc.land/content/files/2026/03/Non-confidential-Public-Summary-of-Meta-s-DMA-Compliance-Report--2026.pdf#page=53)

```powershell
python -m unittest discover -s scripts -p test_watch_instagram_exports.py -v
```
