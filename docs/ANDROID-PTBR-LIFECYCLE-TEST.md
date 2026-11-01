# Android 0.1.64 · PT-BR ekran yenileme düzeltmesi

1 Kasım 2026. Kaynak incelemesinde `LocalizedActivity.onResume()` içindeki karşılaştırma, `Locale.language` değerini (`pt`) seçilen tam etiketle (`pt-BR`) karşılaştırıyordu. Bu koşul PT-BR için her zaman farklıydı ve her öne gelişte `recreate()` çağrısı üretebiliyordu. Önceki birim testleri ve kaynak denetimi bu ekran yaşam döngüsünü çalıştırmadığı için hatayı yakalamamıştı; eski kaynak testi yalnız çevrilmiş metni okuyordu.

Karşılaştırma artık ekranın oluşturulması sırasında saklanan `createdLanguage` ile güncel tam dil tercihi arasında yapılır. Dil ve tema için aynı yaşam döngüsü ilkesi kullanılır: tercih değişirse ekran yenilenir, aynı tercih korunuyorsa yenilenmez. Bölge bilgisi kesilmez. Sayı/tarih biçimlendirmesi ve dil paketleri değiştirilmedi.

## Çalıştırılan kontroller

- `scripts/check-locales.py`: Türkçe, İngilizce, İspanyolca ve PT-BR için 648 eşleşen kaynak; biçim parametreleri ve çoğullar geçerli.
- `:app:testDebugUnitTest`: 118 test, 0 başarısızlık, 0 hata.
- `:app:lintRelease`: 0 hata, önceden bulunan 24 uyarı.
- Release, debug ve debug AndroidTest APK'leri derlendi. Yeni `brazilianPortugueseKeepsTheSameActivityAcrossResumeAndPreservesRecords` testi PT-BR ana ekranında üç durdurma/devam döngüsünü, Activity kimliğini, tam bölge etiketini ve yerel kayıt/mola korunmasını sınamak üzere eklendi.
- İmzalı `InstagramTracker-0.1.64.apk`: versionCode 65, 2.384.611 bayt. v2/v3 imzaları ve paket denetimi geçti; debug, yedekleme, cleartext ve debug preview taşıyıcısı kapalı.
- SHA-256: `1287be79dba38c70e8bfc51f76d9c05fab356afbd486e8e46814d74b4cd8347a`.

## Cihaz kapsamı

Yeni ekran testi henüz bir Android cihazında çalıştırılmadı. Bağlanan Android 13 telefon test paketi kurulumunu `INSTALL_FAILED_USER_RESTRICTED` ile reddetti; ardından kullanıcı bu telefona kurulum istemedi. Üretim uygulamasına kurulum veya veri silme yapılmadı. Derleme sonrası yaklaşık 3,2 GiB boş fiziksel RAM ölçüldü; kullanıcı emülatör testini yeterli bellek koşuluna bağladığı için emülatör açılmadı.

Dolayısıyla bu sürümün sonucu kaynak düzeltmesi, başarılı derleme, birim testleri ve imzalı paket doğrulamasıdır. Gerçek cihazda PT-BR ekranının kararlılığı çalıştırılmış test olarak sunulmaz. Daha sonra ayrı bir test cihazı veya yeterli bellekte emülatörde `LanguageFlowTest` seçilerek doğrulanabilir; gerçek Instagram hesabı gerekmez.
