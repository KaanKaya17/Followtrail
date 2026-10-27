param([string]$Python = $env:TRACKER_BUILD_PYTHON)
$ErrorActionPreference = 'Stop'
$InterfaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $Python) { $Python = (Get-Command python -ErrorAction Stop).Source }
$env:PYTHONPATH = Join-Path $InterfaceRoot '.cache/build-tools'
& $Python -m PyInstaller --noconfirm --clean --onedir --console --name tracker-worker --distpath (Join-Path $InterfaceRoot '.cache/worker-dist') --workpath (Join-Path $InterfaceRoot '.cache/worker-build') --specpath (Join-Path $InterfaceRoot '.cache') --collect-all instagrapi --collect-all curl_cffi --collect-all Cryptodome --exclude-module tkinter --exclude-module pytest (Join-Path $PSScriptRoot 'worker.py')
if ($LASTEXITCODE -ne 0) { throw 'Worker build failed' }
& $Python (Join-Path $PSScriptRoot 'collect_licenses.py')
if ($LASTEXITCODE -ne 0) { throw 'Runtime notice collection failed' }
