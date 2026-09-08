param([Parameter(Mandatory=$true)][string]$Track)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$trackRoot = Join-Path $PSScriptRoot ('projects/' + $Track)
$reports = @(Get-ChildItem $trackRoot -Recurse -File -Filter 'prepare.json' | ForEach-Object {
    (Get-Content $_.FullName -Raw | ConvertFrom-Json).startedAt
} | Where-Object { $_ })
if ($reports.Count -eq 0) { exit 0 }
$start = @($reports | ForEach-Object { [DateTimeOffset]::Parse($_) } | Sort-Object UtcDateTime)[0]
$cache = Join-Path $repo ('build/stage8-workspace-generator-gradle/' + $Track.Replace('.', '_') + '/daemon')
$stopped = @()
if (Test-Path $cache) {
    foreach($file in Get-ChildItem $cache -Recurse -File -Filter 'daemon-*.out.log') {
        if ($file.CreationTimeUtc -lt $start.UtcDateTime.AddSeconds(-3)) { continue }
        $processId = [int]([regex]::Match($file.Name, '^daemon-(\d+)\.out\.log$').Groups[1].Value)
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if (-not $process -or $process.StartTime.ToUniversalTime() -lt $start.UtcDateTime.AddSeconds(-3)) { continue }
        $command = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $processId)
        if ($command.CommandLine -notmatch 'org\.gradle\.launcher\.daemon\.bootstrap\.GradleDaemon') { continue }
        $log = [IO.File]::ReadAllText($file.FullName)
        if (-not ($log.Contains($trackRoot) -or $log.Contains($trackRoot.Replace('\','/')))) { continue }
        Stop-Process -Id $processId -Force
        $stopped += $processId
    }
}
[ordered]@{track=$Track; stoppedOwnedSetupDaemonPids=$stopped; guard='new since this track began; PID/start time/type and unique fixture path in daemon log verified; invoke only after track worker exited'} | ConvertTo-Json | Set-Content (Join-Path $trackRoot 'cleanup.json') -Encoding utf8
Write-Output ('OWNED_SETUP_DAEMONS_CLEANED '+$Track+' count='+$stopped.Count)
