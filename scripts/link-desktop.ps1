param([string]$CounterRoot = "$env:USERPROFILE\Documents\InstagramCounter", [string]$Serial, [switch]$DebugApp)
$ErrorActionPreference = 'Stop'
$trackerPython = Join-Path $CounterRoot '.venv\Scripts\python.exe'
$trackerAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (!(Test-Path -LiteralPath $trackerPython) -or !(Test-Path -LiteralPath $trackerAdb)) {
    throw 'The tested desktop Python environment and Android platform-tools are required.'
}
$trackerArgs = @((Join-Path $PSScriptRoot 'link-desktop.py'), '--counter-root', $CounterRoot, '--adb', $trackerAdb)
if ($Serial) { $trackerArgs += @('--serial', $Serial) }
if ($DebugApp) { $trackerArgs += '--debug' }
& $trackerPython @trackerArgs
if ($LASTEXITCODE -ne 0) { throw 'Encrypted USB pairing did not complete.' }
