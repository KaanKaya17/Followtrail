# Takip geçmişi: geri gelenler ve ayrılanlar — Windows 0.2.91

Windows 0.2.91, her takip listesi için kalıcı bir üyelik defteri tutar. Kişinin listede ilk ve son görüldüğü tarama, listeden çıktığı aralık ve geri döndüğü tarama kaydedilir. Yeni Instagram isteği yoktur; defter yalnız zaten yapılan tam liste taramalarından beslenir.

## Ne gösterilir?

- **Takip Listeleri → İlişki filtresi → Geri gelenler · N:** Bir tam listede görülüp sonraki tam listede olmayan, daha sonra yeniden görülen hesaplar. Satırda geri dönüş zamanı, önceki çıkış aralığı ve ilk görülme bilgisi bulunur.
- **İlişki filtresi → Ayrılanlar · N:** Son tam listede olmayan hesaplar. Son görülme ve listeden çıkış aralığı gösterilir. Kişi, son altı tarama dönüşünden sonra da defterde kalır.
- **Normal liste:** Son taramada geri dönen hesap **Yeni görüldü** yerine **Geri döndü** rozetini alır.
- **Kişi paneli → Takip geçmişi:** Bağlı hesabın kendi takipçi ve takip edilen listelerinde bu kişinin ilk/son görülmesi, çıkış aralıkları, geri dönüşleri ve toplam geri dönüş sayısı.
- **Bildirimler:** Liste değişikliği kaydı ve Windows bildirimi geri dönen hesap sayısını içerir.

## Zamanların anlamı

Değişiklik iki tam tarama arasında bir anda olur. Defter iki sınırı birlikte saklar: “12 Kas 11:01 – 13 Kas 11:01 arası listeden çıktı”, kişinin 12 Kasım taramasında görüldüğünü ve 13 Kasım taramasında görülmediğini söyler. Kesin olay saati üretilmez. Kısmi, iptal edilen veya sayısı uyuşmayan taramalar defteri değiştirmez.

İlk tam listede zaten bulunan kişi için “ilk tam listede vardı” yazılır; uygulama daha önceki takip tarihini bilmez. Defter olmadan açılan eski bir kasa, saklanan son altı tam taramadan bir kez geriye doğru doldurulur. Daha eski hareketler geri üretilemez.

## Sınırlar ve gizlilik

- Liste başına en fazla 40.000 kayıt; kapasite aşılırsa en eski ayrılanlar silinir, listede olanlar korunur. Kişi başına son 24 dönem tutulur.
- **Ayrıntılı geçmiş** saklama süresi deftere de uygulanır: süresi dolan çıkış/dönüş aralıkları ve süresinden önce ayrılmış kişiler silinir; güncel üyelik korunur.
- Kaydedilen profili silmek o kişinin kendi listelerinin defterlerini de siler. Kişinin başka listelerde görünmesi o listenin üyelik olgusudur ve korunur.
- Defter, hesabın DPAPI ile şifrelenmiş kasasındadır. Taşınabilir geçmiş dosyası ve CSV bu sürümde defteri içermez; geri yüklenen kayıtta defter, saklanan taramalardan yeniden oluşturulur.
- Android 0.1.103'te bu görünüm yoktur.

## Doğrulama

- `python -m unittest discover -p "test_*.py"` (Python 3.14.3): **325 test geçti**; 15'i yeni `test_membership.py`. Çıkış/dönüş sınırları, kısmi ve tekrar eden taramaların yok sayılması, girdilerin kopyalanarak değiştirilmesi, eski kasanın geri doldurulması, altı tarama döngüsünden sonra ayrılanların korunması, saklama süresi, profil silme, kapasite ve bozuk defter/olay doğrulaması sınandı. Ağ yanıtları sentetiktir.
- `node --test desktop/*.test.cjs`: **72 test geçti**; yeni test dört dilde geri dönen sayısını Windows bildiriminde doğrular.
- `npm run check:functional`: paketli 0.2.91 EXE'de **46 kontrol geçti**.
- `node scripts/check-membership-desktop.mjs`: paketli EXE'de **7 kontrol geçti**. Defteri olmayan sentetik kasa açılışta dört taramadan dolduruldu; Geri gelenler/Ayrılanlar sayıları, satır zamanları, kişi paneli, dört dilde etiket ve satırların yeniden çizilmesi, satır taşmaması, istek sayacının değişmemesi ve sıfır dış ağ isteği doğrulandı. Ekran görüntüleri `artifacts/desktop-0.2.91-qa/membership` altında.
- Dil değiştirildiğinde zaten yüklenmiş liste satırlarının eski dilde kalması da bu sürümde düzeltildi.
- Gerçek hesapla canlı liste taraması bu sürümde yapılmadı.
- `check-list-recovery-desktop.mjs`, `check-profile-panel-desktop.mjs` ve `check-navigation-desktop.mjs` bu sürümde de 0.2.90 paketinde de aynı adımda başarısız oluyor; beklentileri güncel tohum verisiyle uyuşmuyor ve bu değişiklikle ilgili değil.
