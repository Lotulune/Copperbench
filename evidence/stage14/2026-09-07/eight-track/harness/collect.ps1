$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$destination = Join-Path $repo 'evidence/stage14/2026-09-07/eight-track'
New-Item -ItemType Directory -Force $destination | Out-Null
$tracks = @('fabric-1.20.1','fabric-1.21.1','fabric-26.1.2','fabric-26.2','neoforge-1.20.1','neoforge-1.21.1','neoforge-26.1.2','neoforge-26.2')
function ReadJson([string]$Path) {
    if (Test-Path $Path) { return Get-Content $Path -Raw -Encoding utf8 | ConvertFrom-Json }
    return $null
}
function Phase([string]$Dir,[string]$Name,[switch]$Runtime) {
    $raw = ReadJson (Join-Path $Dir ($Name+'.json'))
    if ($null -eq $raw -and -not (Test-Path (Join-Path $Dir ($Name+'.stdout.log')))) { return [ordered]@{state='not_run'} }
    $text = ''
    foreach ($suffix in @('.stdout.log','.stderr.log')) {
        $file = Join-Path $Dir ($Name+$suffix)
        if (Test-Path $file) { $text += [IO.File]::ReadAllText($file) + "`n" }
    }
    $state = if ($null -eq $raw) {'interrupted_no_exit_receipt'} elseif ($raw.timedOut) {'blocked_timeout'} elseif ($raw.exitCode -eq 0) {'passed'} else {'failed'}
    $prepared = ReadJson (Join-Path $Dir 'prepare.json')
    if ($prepared -and $prepared.startedAt -and $raw.completedAt -and ([DateTimeOffset]::Parse($raw.completedAt) -lt [DateTimeOffset]::Parse($prepared.startedAt))) { $state = 'stale_previous_attempt' }
    $phase = [ordered]@{state=$state; exitCode=$raw.exitCode; timedOut=$raw.timedOut; elapsedMs=$raw.elapsedMs; completedAt=$raw.completedAt; arguments=$raw.arguments}
    if ($Runtime) {
        $phase['initializerExecuted'] = $text.Contains('SURVEY_PULSE_INITIALIZED track=')
        $phase['eulaBoundaryReached'] = ($text -match '(?i)(agree to the EULA|agree to the eula|eula.*agree|agree.*eula)')
        $phase['worldReady'] = ($text -match 'Done \([0-9.,]+s\)!')
        $phase['behaviorVerified'] = $false
        $phase['initSettingsProbe'] = $Name.Contains('-init-settings')
        if ($state -eq 'failed' -and $phase.initSettingsProbe -and $text.Contains("Couldn't find Minecraft server thread")) {
            $phase.state = 'blocked_probe_incompatibility'
            $phase['classification'] = 'The launcher expects a server thread after --initSettings intentionally exits before creating one; regular world startup is not classified from this probe.'
        }
        if ($state -eq 'passed' -and -not $phase.initializerExecuted) { $phase.state = 'boundary_only_or_incomplete' }
        elseif ($state -eq 'passed' -and -not ($phase.eulaBoundaryReached -or $phase.initSettingsProbe)) { $phase.state = 'unexpected_past_eula_boundary' }
    }
    $phase['failureHints'] = @($text -split "`r?`n" | Where-Object { $_ -match '(?i)(error:|incompatible mods|requires version|failed injection|could not find|required by|what went wrong|Caused by:|missing|Could not resolve|unsupported class file|no suitable method)' } | Select-Object -First 10)
    return $phase
}
$rows = @()
foreach ($track in $tracks) {
    $dir = Join-Path $PSScriptRoot ('projects/'+$track)
    $p = ReadJson (Join-Path $dir 'prepare.json')
    $row = [ordered]@{track=$track; preparation=$p; variants=[ordered]@{}}
    foreach ($variant in @('copperbench','native')) {
        $data = [ordered]@{
            assemble = (Phase $dir ($variant+'-assemble'))
            defaultBootstrap = (Phase $dir ($variant+'-bootstrap') -Runtime)
            noClientAssetsBootstrap = (Phase $dir ($variant+'-bootstrap-no-assets') -Runtime)
            initSettingsBootstrap = (Phase $dir ($variant+'-bootstrap-init-settings') -Runtime)
            initSettingsNoClientAssetsBootstrap = (Phase $dir ($variant+'-bootstrap-init-settings-no-assets') -Runtime)
            packagedJarDeployment = 'not_run'
            gameplay = 'not_run'
        }
        $libs = Join-Path $dir ($variant+'/build/libs')
        $data['artifacts'] = @(if (Test-Path $libs) {
            Get-ChildItem $libs -File -Filter '*.jar' | ForEach-Object {
                [ordered]@{name=$_.Name;bytes=$_.Length;sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash}
            }
        })
        $eula = Join-Path $dir ($variant+'/build/track-bootstrap/eula.txt')
        $data['eulaAccepted'] = if (Test-Path $eula) { ([IO.File]::ReadAllText($eula) -match '(?m)^eula=true\s*$') } else { $false }
        $data['isolatedWorldPresent'] = Test-Path (Join-Path $dir ($variant+'/build/track-bootstrap/world/level.dat'))
        $row.variants[$variant] = $data
    }
    $rows += $row
    if (Test-Path $dir) {
        $target = Join-Path $destination $track
        New-Item -ItemType Directory -Force $target | Out-Null
        Get-ChildItem $dir -File | Where-Object { $_.Extension -in '.json','.log' } | Copy-Item -Destination $target -Force
        if (Test-Path (Join-Path $dir 'attempts')) { Copy-Item (Join-Path $dir 'attempts') $target -Recurse -Force }
        # Only source/definition digests; no Minecraft jars, caches, user credentials or generated worlds are copied.
        $manifest = @()
        foreach ($variant in @('copperbench','native')) {
            $project = Join-Path $dir $variant
            foreach ($sub in @('src','elements')) {
                $folder = Join-Path $project $sub
                if (Test-Path $folder) {
                    $manifest += @(Get-ChildItem $folder -Recurse -File | ForEach-Object {
                        [ordered]@{variant=$variant;path=[IO.Path]::GetRelativePath($project,$_.FullName).Replace('\','/');bytes=$_.Length;sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash}
                    })
                }
            }
        }
        $manifest | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $target 'source-manifest.json') -Encoding utf8
    }
}
$summary = [ordered]@{
    schemaVersion=1; capturedAt=[DateTimeOffset]::UtcNow.ToString('o')
    source=(ReadJson (Join-Path $destination 'source-snapshot.json'))
    sharedPureLogic=(ReadJson (Join-Path $PSScriptRoot 'pure-contract.json'))
    documentedFieldContract=(ReadJson (Join-Path $PSScriptRoot 'field-contract.json'))
    fieldProbeNamingNote='canonicalStackSize in early preparation receipts refers to the upstream storage spelling, not a frozen public Core field. documentedFieldContract is the independent public fields.maxStackSize replay.'
    contract='Eight existing Java generator tracks; same Survey Pulse intent, independent native source with matching rendered build config and stock Wrapper'
    sourceIntegrityScope='Production Core/disk gateway, direct create/update collision and metadata-only byte preservation; not full MCP/UI/recovery/detach closure'
    limits=@('No cold-start productivity or token measurement.','Native variants reuse exact toolchain configuration and known fixture logic.','Core source edits use an in-memory task stub; real Gradle assemble/bootstrap are separate measured child processes.','Development source-set EULA-boundary/initSettings probes are not deployed-JAR or gameplay validation.','All written EULA flags remained false; NeoForge 26.1.2 native development mode nevertheless initialized an isolated world. The session and uniquely identified runtime processes were stopped; this exception is preserved. Later NeoForge probes use --initSettings.','A standard stackSize update may commit without materializing its value; compiled does not mean semantically equivalent.','No-client-assets probes explicitly exclude downloadAssets; they do not certify the default runServer preparation path.','Preliminary failures and harness corrections are retained under per-track attempts.')
    tracks=$rows
}
$summary | ConvertTo-Json -Depth 35 | Set-Content (Join-Path $destination 'matrix.json') -Encoding utf8
foreach($file in @('pure-contract.json','pure-contract.log','field-contract.json','HARNESS-README.md')) { if (Test-Path (Join-Path $PSScriptRoot $file)) { Copy-Item (Join-Path $PSScriptRoot $file) $destination -Force } }
$harness = Join-Path $destination 'harness'
New-Item -ItemType Directory -Force $harness | Out-Null
Get-ChildItem $PSScriptRoot -File | Where-Object { $_.Extension -in '.ps1','.java','.gradle','.md' } | Copy-Item -Destination $harness -Force
$rows | ForEach-Object {
    [ordered]@{track=$_.track;prepared=$_.preparation.prepared;sourcePreserved=$_.preparation.metadataOnlyPreservesHelper;collisionRejected=$_.preparation.ownershipConflictRejected;stackSize=$_.preparation.persistedStackSize;generatedBuild=$_.variants.copperbench.assemble.state;generatedBoot=$_.variants.copperbench.defaultBootstrap.state;generatedServerOnly=$_.variants.copperbench.noClientAssetsBootstrap.state;nativeBuild=$_.variants.native.assemble.state;nativeBoot=$_.variants.native.defaultBootstrap.state;nativeServerOnly=$_.variants.native.noClientAssetsBootstrap.state} | ConvertTo-Json -Compress
}
