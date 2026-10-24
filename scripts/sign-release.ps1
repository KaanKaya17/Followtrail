param([string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference = 'Stop'
$trackerRoot = Split-Path $PSScriptRoot -Parent
$trackerSigningDir = Join-Path $env:LOCALAPPDATA 'InstagramTracker\signing'
$trackerKeystore = Join-Path $trackerSigningDir 'release.jks'
$trackerProtectedSecret = Join-Path $trackerSigningDir 'password.dpapi'
$trackerKeytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
if (!(Test-Path -LiteralPath $trackerKeytool)) { throw 'Set JAVA_HOME to JDK 17 or newer.' }
$trackerApkSigner = Join-Path $SdkPath 'build-tools\36.0.0\apksigner.bat'
if (!(Test-Path -LiteralPath $trackerApkSigner)) { throw 'Android build-tools 36.0.0 are required.' }
$trackerUnsigned = Join-Path $trackerRoot 'app\build\outputs\apk\release\app-release-unsigned.apk'
if (!(Test-Path -LiteralPath $trackerUnsigned)) { throw 'Build :app:assembleRelease first.' }
if ((Test-Path -LiteralPath $trackerKeystore) -and !(Test-Path -LiteralPath $trackerProtectedSecret)) {
    throw 'Signing material is incomplete. Restore the existing key and protected secret; do not replace them.'
}
New-Item -ItemType Directory -Path $trackerSigningDir -Force | Out-Null
if (!(Test-Path -LiteralPath $trackerProtectedSecret)) {
    $trackerRandom = New-Object byte[] 48
    $trackerRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $trackerRng.GetBytes($trackerRandom) } finally { $trackerRng.Dispose() }
    $trackerSecure = ConvertTo-SecureString ([Convert]::ToBase64String($trackerRandom)) -AsPlainText -Force
    $trackerSecure | ConvertFrom-SecureString | Set-Content -LiteralPath $trackerProtectedSecret -Encoding utf8 -NoNewline
}
$trackerSecure = ConvertTo-SecureString -String (Get-Content -LiteralPath $trackerProtectedSecret -Raw).Trim()
$env:TRACKER_SIGNING_PASSWORD = (New-Object System.Management.Automation.PSCredential('signing', $trackerSecure)).GetNetworkCredential().Password
try {
    if (!(Test-Path -LiteralPath $trackerKeystore)) {
        & $trackerKeytool -genkeypair -keystore $trackerKeystore -alias tracker -keyalg RSA -keysize 3072 -validity 10000 -storepass:env TRACKER_SIGNING_PASSWORD -keypass:env TRACKER_SIGNING_PASSWORD -dname 'CN=InstagramTracker local release' -storetype PKCS12
        if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed.' }
    }
    $trackerMetadata = Get-Content -LiteralPath (Join-Path $trackerRoot 'app\build\outputs\apk\release\output-metadata.json') -Raw | ConvertFrom-Json
    $trackerVersion = $trackerMetadata.elements[0].versionName
    if ($trackerVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Unexpected version name.' }
    $trackerArtifacts = Join-Path $trackerRoot 'artifacts'
    New-Item -ItemType Directory -Path $trackerArtifacts -Force | Out-Null
    $trackerOutput = Join-Path $trackerArtifacts "InstagramTracker-$trackerVersion.apk"
    & $trackerApkSigner sign --ks $trackerKeystore --ks-key-alias tracker --ks-pass env:TRACKER_SIGNING_PASSWORD --key-pass env:TRACKER_SIGNING_PASSWORD --out $trackerOutput $trackerUnsigned
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & $trackerApkSigner verify --verbose $trackerOutput
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    Get-FileHash -LiteralPath $trackerOutput -Algorithm SHA256 | Select-Object Path,Hash
} finally {
    Remove-Item Env:\TRACKER_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    $trackerSecure = $null
}
