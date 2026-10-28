param(
  [Parameter(Mandatory=$true)][int]$RootProcessId,
  [Parameter(Mandatory=$true)][string]$OutputPath,
  [int]$Seconds = 20
)
$ErrorActionPreference = 'Stop'
if ($Seconds -lt 1 -or $Seconds -gt 60) { throw 'Bounded sample required' }
# Snapshot descendants once after warmup; retain process objects and creation
# times so a reused PID is never charged to the measured application.
$rows = @(Get-CimInstance Win32_Process | Select-Object ProcessId,ParentProcessId)
$ids = [System.Collections.Generic.HashSet[int]]::new()
[void]$ids.Add($RootProcessId)
do {
  $added = $false
  foreach ($row in $rows) {
    if ($ids.Contains([int]$row.ParentProcessId) -and $ids.Add([int]$row.ProcessId)) { $added = $true }
  }
} while ($added)
$tracked = @($ids | ForEach-Object {
  $p = Get-Process -Id $_ -ErrorAction SilentlyContinue
  if ($p) { @{ Process=$p; Started=$p.StartTime; CPU=$p.TotalProcessorTime.TotalSeconds } }
})
if (-not ($tracked | Where-Object { $_.Process.Id -eq $RootProcessId })) { throw 'Root exited' }
$timer = [System.Diagnostics.Stopwatch]::StartNew()
$peakWorking = 0L; $peakPrivate = 0L; $endCPU = 0.0; $samples = 0
$startWorking = 0L; $startPrivate = 0L
function Sample {
  $working = 0L; $private = 0L; $cpu = 0.0
  foreach ($item in $tracked) {
    $p = $item.Process
    $p.Refresh()
    if (-not $p.HasExited -and $p.StartTime -eq $item.Started) {
      $working += $p.WorkingSet64; $private += $p.PrivateMemorySize64
      $cpu += $p.TotalProcessorTime.TotalSeconds - $item.CPU
    } else { throw 'Measured process exited; incomplete sample' }
  }
  return @{Working=$working; Private=$private; CPU=$cpu}
}
$first = Sample
$startWorking = $first.Working; $startPrivate = $first.Private
Set-Content -LiteralPath ($OutputPath + '.ready') -Value 'ready' -Encoding utf8
do {
  $sample = Sample; $samples++
  $peakWorking = [math]::Max($peakWorking, $sample.Working)
  $peakPrivate = [math]::Max($peakPrivate, $sample.Private)
  $endCPU = $sample.CPU
  Start-Sleep -Milliseconds 500
} while ($timer.Elapsed.TotalSeconds -lt $Seconds)
$elapsed = $timer.Elapsed.TotalSeconds
$result = [ordered]@{
  elapsedSeconds=$elapsed; processCount=$tracked.Count; samples=$samples
  cpuSeconds=$endCPU; cpuPercentOneCore=100*$endCPU/$elapsed
  cpuPercentMachine=100*$endCPU/$elapsed/[Environment]::ProcessorCount
  startWorkingMB=$startWorking/1MB; endWorkingMB=$sample.Working/1MB; peakWorkingMB=$peakWorking/1MB
  startPrivateMB=$startPrivate/1MB; endPrivateMB=$sample.Private/1MB; peakPrivateMB=$peakPrivate/1MB
  sampling='500ms; process-family working sets include shared pages; private bytes are committed memory; observer excluded'
}
$result | ConvertTo-Json | Set-Content -LiteralPath $OutputPath -Encoding utf8
