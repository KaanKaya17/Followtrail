# Paket yöneticileri

Windows sürümü GitHub Releases’taki taşınabilir ZIP’tir. Bu klasör ve depodaki `bucket/` aynı ZIP’i winget ve Scoop üzerinden kurmayı sağlar. Her iki manifest de [Package manager manifests](../.github/workflows/packaging.yml) iş akışında temiz bir Windows çalıştırıcısında kurulur, dosyaları denetlenir ve kaldırılır.

Uygulama verisi (`%LOCALAPPDATA%\InstagramTracker-Desktop`) paket klasörünün dışındadır; güncelleme veya kaldırma kayıtları silmez. Güncellemeden önce uygulamadan sistem tepsisindeki **Tamamen çık** ile çık.

## Scoop

```powershell
scoop bucket add followtrail https://github.com/KaanKaya17/Followtrail
scoop install followtrail
```

`bucket/followtrail.json` en son Windows sürümünü GitHub API’den bulur (`checkver`) ve yeni sürümün SHA-256 değerini `SHA256SUMS.txt` dosyasından alır (`autoupdate`). Yeni sürümden sonra manifest şu komutla güncellenir:

```powershell
& "$env:USERPROFILE\scoop\apps\scoop\current\bin\checkver.ps1" -App followtrail -Dir bucket -Update
```

## winget

`winget/manifests/k/KaanKaya/Followtrail/<sürüm>/` klasörü [winget-pkgs](https://github.com/microsoft/winget-pkgs) deposunun düzenini izler. Taşınabilir ZIP olarak kurulur; Electron dosyaları EXE’nin yanında kalmak zorunda olduğu için `ArchiveBinariesDependOnPath: true` kullanılır.

**Henüz winget-pkgs’e gönderilmedi.** SignPath başvurusu kabul edilmediği için imzalı sürüm beklenmeyecek ([kod imzalama politikası](../docs/CODE-SIGNING.md)); gönderim CI’da üretilmiş ilk imzasız sürümle yapılır. İmzasız paket kabul edilir, ancak Microsoft’un otomatik taramasında takılma olasılığı daha yüksektir.

Gönderim (sorumlunun GitHub hesabıyla, onayıyla):

```powershell
winget install Microsoft.WingetCreate
wingetcreate update KaanKaya.Followtrail --version X.Y.Z --urls https://github.com/KaanKaya17/Followtrail/releases/download/windows-vX.Y.Z/Followtrail-Desktop-X.Y.Z.zip
```

İlk gönderimde paket henüz winget-pkgs’te olmadığı için bu klasördeki manifest kullanılır: `wingetcreate submit packaging\winget\manifests\k\KaanKaya\Followtrail\X.Y.Z`. Yeni sürümde klasörü kopyalayıp sürüm numaralarını, `RelativeFilePath`, `InstallerUrl`, `InstallerSha256`, `ReleaseDate` ve `ReleaseNotesUrl` değerlerini güncelle; iş akışı PR’da yeni manifesti kurarak dener.
