$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence = Join-Path $repo 'evidence/stage14/2026-09-07/eight-track'
$matrix = Get-Content (Join-Path $evidence 'matrix.json') -Raw -Encoding utf8 | ConvertFrom-Json
function BuildText($phase) {
    switch ($phase.state) {
        'passed' { return 'passed' }
        'failed' { return 'failed' }
        'blocked_timeout' { return 'blocked: timeout' }
        'stale_previous_attempt' { return 'stale / not current' }
        default { return $phase.state }
    }
}
function BootText($variant) {
    foreach ($name in @('initSettingsNoClientAssetsBootstrap','initSettingsBootstrap','noClientAssetsBootstrap','defaultBootstrap')) {
        $phase = $variant.$name
        if ($phase.state -eq 'not_run' -or $phase.state -eq 'stale_previous_attempt') { continue }
        $mode = if ($name -match 'initSettings') {'initSettings'} else {'default'}
        if ($name -match '(?i)NoClientAssets') { $mode += '; client assets excluded' }
        if ($phase.state -eq 'passed' -and $phase.initializerExecuted) { return 'initialized ('+$mode+')' }
        if ($phase.state -eq 'boundary_only_or_incomplete') { return 'boundary only; initializer not observed ('+$mode+')' }
        return $phase.state+' ('+$mode+')'
    }
    return 'not_run'
}
$lines = @('| Track | Primary/helper preservation + collision cleanup | Documented maxStackSize materialized | Generated compile | Native compile | Generated bootstrap | Native bootstrap |', '| --- | --- | --- | --- | --- | --- | --- |')
$compact = @()
foreach($row in $matrix.tracks) {
    $p = $row.preparation
    $integrity = $p.metadataOnlyPreservesPrimary -and $p.metadataOnlyPreservesHelper -and $p.ownershipConflictRejected -and $p.rejectedCreateLeavesNoOrphan -and $p.collisionPreservesExistingPrimary
    $source = if($integrity) {'passed (five direct-Core assertions)'} else {'not fully passed'}
    $field = @($matrix.documentedFieldContract | Where-Object { $_.track -eq $row.track })[0]
    $property = if($field) { 'create 1 -> '+$field.actualAfterCreate+'; update 7 -> '+$field.actualAfterUpdate } else { 'not verified' }
    $genBuild = BuildText $row.variants.copperbench.assemble
    $nativeBuild = BuildText $row.variants.native.assemble
    $genBoot = BootText $row.variants.copperbench
    $nativeBoot = BootText $row.variants.native
    $lines += '| '+$row.track+' | '+$source+' | '+$property+' | '+$genBuild+' | '+$nativeBuild+' | '+$genBoot+' | '+$nativeBoot+' |'
    $compact += [ordered]@{track=$row.track;fiveSourceAssertionsPassed=[bool]$integrity;publicField=$field;upstreamStorageProbe=$p.persistedStackSize;generatedBuild=$genBuild;nativeBuild=$nativeBuild;generatedBootstrap=$genBoot;nativeBootstrap=$nativeBoot;generatedWorldPresent=$row.variants.copperbench.isolatedWorldPresent;nativeWorldPresent=$row.variants.native.isolatedWorldPresent}
}
$table = $lines -join "`n"
$reportPath = Join-Path $repo 'docs/testing/stage14-eight-track-survey-pulse-2026-09-07.md'
$report = [IO.File]::ReadAllText($reportPath)
$report = [regex]::Replace($report, '(?s)<!-- MATRIX-START -->.*?<!-- MATRIX-END -->', "<!-- MATRIX-START -->`n"+$table+"`n<!-- MATRIX-END -->")
[IO.File]::WriteAllText($reportPath, $report, [Text.UTF8Encoding]::new($false))
$compact | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $evidence 'matrix-summary.json') -Encoding utf8
$compact | ForEach-Object { $_ | ConvertTo-Json -Compress }
