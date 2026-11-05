# Yayın hazırlama

1. `versionCode` değerini artırın, `versionName` ve CHANGELOG'u güncelleyin. Bir kez dağıtılan APK içeriğini aynı sürüm altında değiştirmeyin.
2. Birim testleri, lint, release APK kontrolü ve desteklenen emülatör işleri geçsin. Sonucun hangi commit'e ait olduğunu doğrulayın. GitHub CI testi, gerçek Instagram hesabı testi yerine geçmez.
3. Gerçek cihazda ilk bağlantı, iki gerçek sayı, yeniden açılışta yeni gözlem zamanı, çevrimdışı durum ve bağlantıyı kaldırma akışını doğrulayın. Bildirim için izin reddi/geri alma, uygulama ekranı kapalıyken değişim, bildirime dokunma, yeniden başlatma ve üretici pil kısıtlarını ayrıca değerlendirin. 429 sırasında canlı testi tekrarlamayın. Bu akış bitmediyse sürümü **önizleme** olarak işaretleyin; eksik doğrulamayı açıkça yazın.
4. Yerel anahtarla `scripts/sign-release.ps1` çalıştırın. Anahtar ve DPAPI parola dosyası yalnız yerel signing klasöründe kalır. Aynı anahtar güncellemeler için korunmalıdır. İmzayı ve APK SHA-256 değerini kaydedin.
5. `scripts/verify-release.py` ile **imzalı** APK'yı da kontrol edin. Sürüm etiketi ve kaynak commit'i eşleşsin. APK kaynak dosyalarının yanına commit edilmez; kullanıcıya dağıtılan binary ayrıca tanımlanır.

6. README, `docs/wiki/Surum-ve-Destek.md`, masaüstü rehberi ve güncel politika/platform notlarını kaynak sürümleriyle karşılaştırın. `python scripts/prepare-wiki.py --check` çalıştırın. Kaynak dalı/etiketi, GitHub Releases dosyası ve ayrı Wiki yayınının farklı durumlar olduğunu açıkça belirtin; eski doğrulama raporlarının sürüm/ölçümlerini değiştirmeyin. Wiki kullanılabiliyorsa kaynak kılavuzdan tekrar dışa aktarın.

Otomatik CI hesabın parolasını veya oturumunu almaz ve üretim imza anahtarını kullanmaz. Yalnız raporlar saklanır; imzasız çıktının mağazaya otomatik yayınlanması yoktur. Store/Data Safety beyanı, lisans kararı ve bağımsız güvenlik incelemesi tamamlanmadan genel kullanıma hazır etiketi verilmez.
