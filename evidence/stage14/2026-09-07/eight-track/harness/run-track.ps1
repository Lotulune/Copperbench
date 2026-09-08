param([Parameter(Mandatory=$true)][string]$Track, [switch]$SkipPrepare,
    [ValidateSet('both','native','copperbench')][string]$Variant='both',
    [switch]$BootstrapOnly, [switch]$SkipServerAssets)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$root = [IO.Path]::GetFullPath((Join-Path $repo '../..'))
$out = Join-Path $PSScriptRoot ('projects/' + $Track)
New-Item -ItemType Directory -Force $out | Out-Null
$existing = @(Get-ChildItem $out -File | Where-Object { $_.Name -match '\.(log|json)$' })
if ($existing.Count -gt 0) {
    $archive = Join-Path $out ('attempts/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Force $archive | Out-Null
    $existing | Copy-Item -Destination $archive
}
$env:MCREATOR_HOME = Join-Path $out 'user'
$env:TEMP = Join-Path $PSScriptRoot ('tmp/' + $Track)
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force $env:TEMP | Out-Null
$jdk = Join-Path $root ('jdk/' + $(if ($Track -match '-26\.') {'jbr25_win_64'} else {'jdk21_win_64'}))
$jbr = Join-Path $root 'jdk/jbr25_win_64'
$cache = Join-Path $repo ('build/stage8-workspace-generator-gradle/' + $Track.Replace('.', '_'))
$env:GRADLE_USER_HOME = $cache
function RunProcess([string]$Java, [string]$Cwd, [string[]]$Arguments, [string]$Label, [int]$Seconds) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = Join-Path $Java 'bin/java.exe'; $info.WorkingDirectory = $Cwd
    $info.UseShellExecute = $false; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    $info.Environment['JAVA_HOME'] = $Java
    foreach ($arg in $Arguments) { $info.ArgumentList.Add($arg) }
    $stdoutFile = [IO.FileStream]::new((Join-Path $out ($Label + '.stdout.log')), [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::ReadWrite, 1)
    $stderrFile = [IO.FileStream]::new((Join-Path $out ($Label + '.stderr.log')), [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::ReadWrite, 1)
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $process = [Diagnostics.Process]::Start($info)
    $stdout = $process.StandardOutput.BaseStream.CopyToAsync($stdoutFile)
    $stderr = $process.StandardError.BaseStream.CopyToAsync($stderrFile)
    $completed = $process.WaitForExit($Seconds * 1000)
    if (-not $completed) { $process.Kill($true); $process.WaitForExit() }
    [void]$stdout.GetAwaiter().GetResult(); [void]$stderr.GetAwaiter().GetResult()
    $stdoutFile.Dispose(); $stderrFile.Dispose(); $watch.Stop()
    $data = [ordered]@{ track=$Track; label=$Label; exitCode=$process.ExitCode; timedOut=(-not $completed); elapsedMs=$watch.ElapsedMilliseconds; javaHome=$Java; gradleHome=$cache; arguments=$Arguments; completedAt=[DateTimeOffset]::UtcNow.ToString('o') }
    $data | ConvertTo-Json | Set-Content (Join-Path $out ($Label + '.json')) -Encoding utf8
    Write-Host ([ordered]@{track=$Track;label=$Label;exitCode=$process.ExitCode;timedOut=(-not $completed);elapsedMs=$watch.ElapsedMilliseconds} | ConvertTo-Json -Compress)
    return $process.ExitCode
}
if (-not $SkipPrepare) {
    $cp = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'classpath.txt'))
    $args = @('--add-opens','java.base/java.lang=ALL-UNNAMED','--enable-native-access=ALL-UNNAMED','-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-Duser.language=en','-Xmx1536m','-cp',((Join-Path $PSScriptRoot 'classes')+';'+$cp),'dev.copperbench.headless.TrackAudit',$Track)
    $code = RunProcess $jbr $repo $args 'prepare-process' 300
    if ($code -ne 0) { exit $code }
}
$init = (Join-Path $PSScriptRoot 'server-probe.init.gradle').Replace('\','/')
foreach ($case in @('copperbench','native')) {
    if ($Variant -ne 'both' -and $Variant -ne $case) { continue }
    $project = Join-Path $out $case
    $args = @('-Dfile.encoding=UTF-8','-Duser.language=en','-classpath',(Join-Path $project 'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','--no-daemon','--console=plain','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8 -Duser.language=en')
    if (-not $BootstrapOnly) {
        $code = RunProcess $jdk $project ($args + @('assemble')) ($case+'-assemble') 240
        if ($code -ne 0) { continue }
    }
    $run = Join-Path $project 'build/track-bootstrap'
    New-Item -ItemType Directory -Force $run | Out-Null
    [IO.File]::WriteAllText((Join-Path $run 'eula.txt'), "eula=false`n")
    [IO.File]::WriteAllText((Join-Path $run 'server.properties'), "server-ip=127.0.0.1`nserver-port=0`nonline-mode=true`n")
    $bootstrapArgs = @('runServer','-I',$init)
    $label = $case+'-bootstrap'
    if ($Track.StartsWith('neoforge-')) { $label += '-init-settings' }
    if ($SkipServerAssets) {
        if ($Track.StartsWith('neoforge-') -and $Track -ne 'neoforge-1.20.1') {
            $version = $Track.Substring('neoforge-'.Length)
            $manifestPath = Join-Path $cache ('caches/neoformruntime/artifacts/minecraft_'+$version+'_version_manifest.json')
            $manifest = Get-Content $manifestPath -Raw -Encoding utf8 | ConvertFrom-Json
            $assetRoot = Join-Path $cache 'caches/neoformruntime/assets'
            $assetProperties = Join-Path $project 'build/moddev/minecraft_assets.properties'
            New-Item -ItemType Directory -Force $assetRoot | Out-Null
            $assetRootEscaped = $assetRoot.Replace('\','\\').Replace(':','\:')
            [IO.File]::WriteAllText($assetProperties, "# Headless init-settings probe: genuine index metadata; asset payload intentionally not required.`nasset_index="+$manifest.assetIndex.id+"`nassets_root="+$assetRootEscaped+"`n")
            [ordered]@{manifestSha256=(Get-FileHash $manifestPath -Algorithm SHA256).Hash;assetIndex=$manifest.assetIndex.id;assetPayloadVerified=$false;scope='Metadata from the actual Minecraft manifest only; no claim that downloadAssets completed; headless init-settings probe'} | ConvertTo-Json | Set-Content (Join-Path $out ($case+'-headless-assets-metadata.json')) -Encoding utf8
        }
        $assetTask = if ($Track -eq 'neoforge-1.20.1') { 'neoFormJoined1.20.1-20230612.114412DownloadAssets' } else { 'downloadAssets' }
        $bootstrapArgs += @('-x',$assetTask); $label += '-no-assets'
    }
    $code = RunProcess $jdk $project ($args + $bootstrapArgs) $label 180
}
Write-Output ('TRACK_DONE '+$Track)
[ordered]@{track=$Track;finishedAt=[DateTimeOffset]::UtcNow.ToString('o');note='Inspect individual phase JSON; orchestration completion does not mean all phases passed'} | ConvertTo-Json | Set-Content (Join-Path $out 'track-completed.json') -Encoding utf8
