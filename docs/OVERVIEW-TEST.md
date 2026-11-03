# Windows Özet ekranı — 0.2.41

3 Kasım 2026. Profil panelinden sonraki arayüz aşaması Windows Özet ekranıdır. Yeni veri toplama veya Instagram uç noktası eklenmedi.

## Görünen davranış

- Hesap fotoğrafı, adı ve kullanıcı adı üstte; kimlik satırı profil ayrıntısını açar.
- Takipçi/takip edilen kartları bağlı hesaba aittir. Fark, mevcut profilden önceki tarihli son kayıtla karşılaştırılır. Bilinmeyen sayılar sıfıra dönüşmez; önceki kayıt yoksa fark üretilmez. Kartlar ilgili takip listesini açar. Üçüncü kart saklanan profil değişikliği sayısını gösterir; tüm zamanların toplamı olarak sunulmaz.
- Son kontrol ve sonraki kontrol, tek yatay kontrol kartındadır. Sonraki zaman mevcut hesap zamanlayıcısından okunur. Otomatik kontrol kapalıysa, bağlantı gerekiyorsa veya kısıtlama nedeniyle duraklatılmışsa yeni bir otomatik kontrol saati vaat edilmez.
- Aktif işlem, iptal ve Instagram molası aynı kartın içinde gösterilir. Diğer ekranlarda mevcut sabit ilerleme paneli korunur. Aynı panel elemanı taşınır; ikinci bir sayaç veya ilerleme zamanlayıcısı oluşturulmaz. Genel sınırlama banner'ı Özet'te tekrar edilmez; asıl hata ve HTTP İstek bilgileri'nde korunur.
- Son hareketlerde sayısal profil değişiklikleri önceki → yeni değerle, liste farkları yeni görülen/son kayıtta olmayan hesap sayısıyla gösterilir. Tıklama mevcut ayrıntıyı açar; liste farkı kesin takipten çıkış olarak adlandırılmaz. Kimliği olmayan hata kayıtları da çizilebilir.
- Günlük grafikte seçili serinin ilk ve son gösterilen değerinin farkı belirtilir. Tek noktada fark üretilmez; eksik günler çizgiyi kesmeye devam eder. Günlük/son kontroller grafikleri ortak, tam sayılı bir eksen ölçeği kullanır; küçük sayılarda yinelenen yuvarlanmış etiketler oluşmaz. Eksenin sıfırdan başlaması zorunlu değildir; değerler eksende açıkça yazılır.

## Doğrulama

Paketlenmiş EXE ayrı `TrackerQA-*` dizinleri, gerçek DPAPI/IPC ve sentetik profillerle sınandı; üretim hesabının kasası kullanılmadı. Görsel QA açık/koyu/OLED, TR/EN/ES/PT-BR ve 960×680 yerel pencereyi kapsar. Ekranlar yerel `artifacts/desktop-0.2.41-qa/overview/` altındadır.

- `npm run check`: tema, dört dilde 1127 metin, yerel bağlantı simgeleri ve kaynak sözdizimi geçti.
- `node --test --test-reporter=dot desktop/*.test.cjs`: 62 test geçti. Yeni eksen kontrolü sıfır, küçük/çok büyük sayılar, veri aralığının kapsanması ve benzersiz tam sayı etiketlerini kapsar.
- `check-overview-desktop.mjs`: 5 senaryo grubu geçti; sayaç/fark, planlı/kapalı durum, ilk kayıt, kimliksiz hata, HTML kaçışı, ayrıntı/listelere erişim, kart içi mola/ilerleme, sayfalar arası panel sahipliği, dil/tema/dar pencere ve hesap değişimi dahil.
- `check-plain-language-desktop.mjs`: 5 kontrol geçti; kullanıcı metninde teknik hata kodu yok, özgün hata ve HTTP tanıda korunuyor.
- `check-daily-desktop.mjs`: 5 kontrol geçti; 799 günlük arşiv, eksik günler, dönem/seri seçimi, temizleme ve hesap ayrımı dahil.
- `check-functional-desktop.mjs`: 36 genel paketli kontrol geçti; gizli/küçültülmüş pencerenin güncellemeleri durdurması dahil.
- `check-progress-desktop.mjs`: 11 kontrol geçti; geri sayım, iptal, mola, gizli pencere ve zamanlayıcıların durması dahil.
- `check-accounts-desktop.mjs`: 10 çoklu hesap kontrolü geçti.

Bu çalışma canlı Instagram isteği göndermedi; Android girişindeki profil HTTP 429 sorununun çözümü değildir. Android APK yeniden derlenmedi. CPU/RAM veya önceki sürümle performans farkı ölçülmedi.
