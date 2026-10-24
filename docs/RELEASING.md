# Yayın hazırlama

1. `versionCode` değerini artırın, `versionName` ve CHANGELOG'u güncelleyin. Bir kez dağıtılan APK içeriğini aynı sürüm altında değiştirmeyin.
2. Birim testleri, lint, release APK kontrolü ve desteklenen emülatör işleri geçsin. Sonucun hangi commit'e ait olduğunu doğrulayın. GitHub CI testi, gerçek Instagram hesabı testi yerine geçmez.
3. Gerçek cihazda ilk bağlantı, iki gerçek sayı, yeniden açılışta yeni gözlem zamanı, çevrimdışı durum ve bağlantıyı kaldırma akışını doğrulayın. Bu akış bitmediyse sürümü **önizleme** olarak işaretleyin; eksik doğrulamayı açıkça yazın.
4. Yerel anahtarla `scripts/sign-release.ps1` çalıştırın. Anahtar ve DPAPI parola dosyası yalnız yerel signing klasöründe kalır. Aynı anahtar güncellemeler için korunmalıdır. İmzayı ve APK SHA-256 değerini kaydedin.
5. `scripts/verify-release.py` ile **imzalı** APK'yı da kontrol edin. Sürüm etiketi ve kaynak commit'i eşleşsin. APK kaynak dosyalarının yanına commit edilmez; kullanıcıya dağıtılan binary ayrıca tanımlanır.

Otomatik CI hesabın parolasını veya oturumunu almaz ve üretim imza anahtarını kullanmaz. Yalnız raporlar saklanır; imzasız çıktının mağazaya otomatik yayınlanması yoktur. Store/Data Safety beyanı, lisans kararı ve bağımsız güvenlik incelemesi tamamlanmadan genel kullanıma hazır etiketi verilmez.
