$ErrorActionPreference = 'Stop'
Get-ChildItem (Join-Path $PSScriptRoot 'projects') -Directory | ForEach-Object {
    $dir = $_.FullName
    $row = [ordered]@{track=$_.Name}
    $prepare = Join-Path $dir 'prepare.json'
    if (Test-Path $prepare) {
        $p = Get-Content $prepare -Raw | ConvertFrom-Json
        $row['sourcePreserved'] = $p.metadataOnlyPreservesHelper
        $row['ownershipRejected'] = $p.ownershipConflictRejected
        $row['prepared'] = $p.prepared
        $row['failure'] = $p.failure
    }
    foreach($label in @('prepare-process','copperbench-assemble','copperbench-bootstrap','native-assemble','native-bootstrap','copperbench-bootstrap-init-settings','native-bootstrap-init-settings','copperbench-bootstrap-init-settings-no-assets','native-bootstrap-init-settings-no-assets')) {
        $file = Join-Path $dir ($label+'.json')
        if (Test-Path $file) { $result = Get-Content $file -Raw | ConvertFrom-Json; $row[$label] = $result.exitCode }
    }
    $row | ConvertTo-Json -Compress
}
