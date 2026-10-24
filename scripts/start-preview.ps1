param(
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$AvdName = 'TrackerQA',
    [int]$Port = 5580,
    [switch]$Headless
)
$ErrorActionPreference = 'Stop'
$trackerRoot = Split-Path $PSScriptRoot -Parent
$trackerAdb = Join-Path $SdkPath 'platform-tools\adb.exe'
$trackerEmulator = Join-Path $SdkPath 'emulator\emulator.exe'
$trackerApk = Join-Path $trackerRoot 'app\build\outputs\apk\debug\app-debug.apk'
foreach ($trackerFile in @($trackerAdb, $trackerEmulator, $trackerApk)) {
    if (!(Test-Path -LiteralPath $trackerFile)) { throw "Gerekli dosya yok: $trackerFile. Android SDK ve debug APK derlemesini kontrol et." }
}
if ($AvdName -notmatch '^[A-Za-z0-9_-]+$' -or $Port -lt 5554 -or $Port -gt 5682 -or $Port % 2 -ne 0) { throw 'Geçersiz emülatör adı veya port.' }
$trackerLocalAvd = Join-Path $trackerRoot 'artifacts\qa-avd'
if (Test-Path -LiteralPath (Join-Path $trackerLocalAvd "$AvdName.ini")) { $env:ANDROID_AVD_HOME = $trackerLocalAvd }
$trackerSerial = "emulator-$Port"
$trackerState = & $trackerAdb -s $trackerSerial get-state 2>$null
if ($trackerState -ne 'device') {
    if (!((& $trackerEmulator -list-avds) -contains $AvdName)) { throw "AVD bulunamadı: $AvdName. Önceden oluşturulmuş Android emülatörünü -AvdName ile seç." }
    $trackerArguments = @('-avd', $AvdName, '-port', "$Port", '-no-audio', '-no-snapshot', '-no-boot-anim', '-gpu', 'swiftshader_indirect')
    if ($Headless) { $trackerArguments += '-no-window' }
    $trackerWindow = if ($Headless) { 'Hidden' } else { 'Normal' }
    # The visible window is the interactive Android preview explicitly requested by the user.
    Start-Process -FilePath $trackerEmulator -ArgumentList $trackerArguments -WindowStyle $trackerWindow | Out-Null
}
$trackerDeadline = (Get-Date).AddMinutes(3)
do {
    $trackerBoot = & $trackerAdb -s $trackerSerial shell getprop sys.boot_completed 2>$null
    if ($trackerBoot -eq '1') { break }
    if ((Get-Date) -gt $trackerDeadline) { throw 'Emülatör üç dakika içinde açılmadı.' }
    Start-Sleep -Seconds 2
} while ($true)
& $trackerAdb -s $trackerSerial install -r $trackerApk
if ($LASTEXITCODE -ne 0) { throw 'Debug APK kurulamadı.' }
& $trackerAdb -s $trackerSerial shell am start -n 'dev.kaankaya.instagramtracker.debug/dev.kaankaya.instagramtracker.MainActivity' --ez offline_preview true
if ($LASTEXITCODE -ne 0) { throw 'Önizleme açılamadı.' }
Write-Output 'Demo açıldı: örnek veri, Instagram isteği yok. Telefon APK sürümüyle aynı ana Android kodu kullanılıyor.'
