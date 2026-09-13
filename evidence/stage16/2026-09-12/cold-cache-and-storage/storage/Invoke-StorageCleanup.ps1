$ErrorActionPreference = 'Stop'
$boundary = 'D:\AICoding\Minecraft_ModCreator'
$runRoot = Join-Path $boundary '.tmp\stage15-linux\build\stage16-agent-reliability\cold-cache-c0178f6b-20260912'
$plan = Get-Content -LiteralPath (Join-Path $runRoot 'cleanup-plan.json') -Encoding UTF8 -Raw | ConvertFrom-Json
if ($plan.workspaceBoundary -ne $boundary) { throw 'Workspace boundary mismatch' }
$protected = @((Join-Path $boundary '.git'),(Join-Path $boundary 'evidence'),(Join-Path $boundary '.tmp\stage15-linux\evidence'),(Join-Path $boundary '.tmp\stage15-linux\build\stage16-agent-reliability\installed-retest-c0178f6b-20260912'),$runRoot)
$active = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe','copperbench.exe') })
if ($active.Count -gt 0) { throw 'Java or Copperbench is active; cleanup aborted' }
foreach ($entry in $plan.targets) {
    $resolved = (Resolve-Path -LiteralPath $entry.path).ProviderPath
    if ($resolved -ne $entry.path -or -not $resolved.StartsWith($boundary + '\',[StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe cleanup path: $resolved" }
    foreach ($keep in $protected) {
        if ($resolved -eq $keep -or $resolved.StartsWith($keep + '\',[StringComparison]::OrdinalIgnoreCase) -or $keep.StartsWith($resolved + '\',[StringComparison]::OrdinalIgnoreCase)) { throw "Protected cleanup overlap: $resolved" }
    }
    $item = Get-Item -LiteralPath $resolved -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse point target: $resolved" }
    if ($item.PSIsContainer) {
        $reparse = @(Get-ChildItem -LiteralPath $resolved -Recurse -Force | Where-Object { ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 })
        if ($reparse.Count -gt 0) { throw "Reparse point inside cleanup target: $resolved" }
    }
    $repo = if ($resolved.StartsWith((Join-Path $boundary '.tmp\stage12a-depth') + '\')) { Join-Path $boundary '.tmp\stage12a-depth' } elseif ($resolved.StartsWith((Join-Path $boundary '.tmp\stage15-linux') + '\')) { Join-Path $boundary '.tmp\stage15-linux' } else { $boundary }
    $relative = [IO.Path]::GetRelativePath($repo,$resolved).Replace('\','/')
    $tracked = @(& git -C $repo ls-files -- $relative)
    if ($LASTEXITCODE -ne 0 -or $tracked.Count -gt 0) { throw "Tracked source present in cleanup target: $resolved" }
}
$before = (Get-PSDrive D).Free
$started = [DateTime]::UtcNow.ToString('o')
$completed = [Collections.Generic.List[object]]::new()
try {
    foreach ($entry in $plan.targets) {
        if ($entry.kind -eq 'directory') { Remove-Item -LiteralPath $entry.path -Recurse -Force } else { Remove-Item -LiteralPath $entry.path -Force }
        if (Test-Path -LiteralPath $entry.path) { throw "Cleanup target survived: $($entry.path)" }
        $completed.Add([pscustomobject]@{path=$entry.path;logicalBytes=$entry.bytes;reason=$entry.reason;removedUtc=[DateTime]::UtcNow.ToString('o')})
    }
} finally {
    $after = (Get-PSDrive D).Free
    $record = [ordered]@{schemaVersion='1.0';startedUtc=$started;completedUtc=[DateTime]::UtcNow.ToString('o');plannedTargetCount=$plan.targets.Count;removedTargetCount=$completed.Count;diskFreeBefore=$before;diskFreeAfter=$after;observedDiskSpaceReclaimed=$after-$before;removed=$completed;virtualDisksDeleted=$false;checkpointsDeleted=$false;sourceFilesDeleted=$false;candidateInstallersRetained=$true;rollbackBackupsRetained=$true}
    $record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $runRoot 'cleanup-result.json') -Encoding UTF8
    [pscustomobject]@{removed=$completed.Count;planned=$plan.targets.Count;reclaimedGiB=[math]::Round(($after-$before)/1GB,2);freeGiB=[math]::Round($after/1GB,2)} | ConvertTo-Json
}
