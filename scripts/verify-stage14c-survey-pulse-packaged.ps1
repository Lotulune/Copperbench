param(
    [int]$TimeoutSeconds = 600
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new()

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$source = Join-Path $repo 'examples/agent-native/survey-pulse'
$hostRoot = Join-Path $repo 'build/stage14c-packaged-host'
$evidenceDir = Join-Path $repo 'evidence/stage14/2026-09-08'
$evidenceJson = Join-Path $evidenceDir 'packaged-behavior-fabric-1.21.1.json'
$logFile = Join-Path $evidenceDir 'packaged-behavior-fabric-1.21.1.log'
$gradleHome = Join-Path $repo 'build/stage8-workspace-generator-gradle/fabric-1_21_1'

function Resolve-Java21Home {
    foreach ($candidate in @(
        (Join-Path $repo 'jdk/jdk21_win_64'),
        ([IO.Path]::GetFullPath((Join-Path $repo '../../jdk/jdk21_win_64'))),
        (Join-Path $env:USERPROFILE '.copperbench/gradle/jdks/eclipse_adoptium-21-amd64-windows.2')
    )) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin/java.exe') -PathType Leaf) {
            return [IO.Path]::GetFullPath($candidate)
        }
    }
    throw 'Stage 14C packaged behavior probe requires a usable Java 21 home.'
}

function Invoke-BoundedProcess([string]$FileName, [string]$WorkingDirectory, [string[]]$Arguments,
        [hashtable]$Environment, [int]$Seconds) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $FileName
    $info.WorkingDirectory = $WorkingDirectory
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($argument in $Arguments) { [void]$info.ArgumentList.Add($argument) }
    foreach ($entry in $Environment.GetEnumerator()) { $info.Environment[$entry.Key] = [string]$entry.Value }
    $process = [Diagnostics.Process]::Start($info)
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $completed = $process.WaitForExit($Seconds * 1000)
    if (-not $completed) {
        $process.Kill($true)
        $process.WaitForExit()
    }
    $out = $stdout.GetAwaiter().GetResult()
    $err = $stderr.GetAwaiter().GetResult()
    return [pscustomobject]@{
        exitCode = if ($completed) { $process.ExitCode } else { -1 }
        timedOut = -not $completed
        stdout = $out
        stderr = $err
        combined = $out + "`n" + $err
    }
}

$javaHome = Resolve-Java21Home
$environment = @{
    JAVA_HOME = $javaHome
    GRADLE_USER_HOME = $gradleHome
    SURVEY_PULSE_STAGE14C_BEHAVIOR_PROBE = '1'
}

New-Item -ItemType Directory -Force $evidenceDir | Out-Null
if (Test-Path -LiteralPath $hostRoot) { Remove-Item -LiteralPath $hostRoot -Recurse -Force }
New-Item -ItemType Directory -Force $hostRoot | Out-Null

$build = Invoke-BoundedProcess (Join-Path $source 'gradlew.bat') $source @('--no-daemon', '--console=plain', 'clean', 'remapJar') $environment $TimeoutSeconds
if ($build.timedOut -or $build.exitCode -ne 0) {
    [IO.File]::WriteAllText($logFile, $build.combined, [Text.UTF8Encoding]::new($false))
    throw "Survey Pulse remapJar failed with exit $($build.exitCode)."
}

$jar = Get-ChildItem -LiteralPath (Join-Path $source 'build/libs') -File -Filter '*.jar' |
    Where-Object { $_.Name -notmatch '(sources|dev-shadow)' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $jar) { throw 'Survey Pulse remapped JAR was not produced.' }

Copy-Item -LiteralPath (Join-Path $source 'gradlew.bat') -Destination $hostRoot
Copy-Item -LiteralPath (Join-Path $source 'gradlew') -Destination $hostRoot
New-Item -ItemType Directory -Force (Join-Path $hostRoot 'gradle/wrapper') | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'gradle/wrapper/gradle-wrapper.jar') -Destination (Join-Path $hostRoot 'gradle/wrapper/gradle-wrapper.jar')
Copy-Item -LiteralPath (Join-Path $source 'gradle/wrapper/gradle-wrapper.properties') -Destination (Join-Path $hostRoot 'gradle/wrapper/gradle-wrapper.properties')
[IO.File]::WriteAllText((Join-Path $hostRoot 'settings.gradle'), @'
pluginManagement {
    repositories {
        maven { url = 'https://maven.fabricmc.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = 'stage14c-packaged-host'
'@, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $hostRoot 'gradle.properties'), @'
org.gradle.jvmargs=-Xmx1G -Dfile.encoding=UTF-8 -Duser.language=en
org.gradle.parallel=false
org.gradle.configuration-cache=false
'@, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $hostRoot 'build.gradle'), @'
plugins {
    id 'net.fabricmc.fabric-loom-remap' version '1.17.19'
}

dependencies {
    minecraft 'com.mojang:minecraft:1.21.1'
    mappings loom.officialMojangMappings()
    modImplementation 'net.fabricmc:fabric-loader:0.19.3'
    modImplementation 'net.fabricmc.fabric-api:fabric-api:0.116.15+1.21.1'
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}
'@, [Text.UTF8Encoding]::new($false))

$runDir = Join-Path $hostRoot 'run'
$modsDir = Join-Path $runDir 'mods'
New-Item -ItemType Directory -Force $modsDir | Out-Null
$deployedJar = Join-Path $modsDir 'survey_pulse-1.0.jar'
Copy-Item -LiteralPath $jar.FullName -Destination $deployedJar
[IO.File]::WriteAllText((Join-Path $runDir 'eula.txt'), "eula=true`n", [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $runDir 'server.properties'), @'
server-ip=127.0.0.1
server-port=0
online-mode=false
level-name=stage14c-isolated-world
view-distance=4
simulation-distance=4
'@, [Text.UTF8Encoding]::new($false))

$runtime = Invoke-BoundedProcess (Join-Path $hostRoot 'gradlew.bat') $hostRoot @('--no-daemon', '--console=plain', 'runServer') $environment $TimeoutSeconds
$combined = $runtime.combined
[IO.File]::WriteAllText($logFile, $combined, [Text.UTF8Encoding]::new($false))

$initializer = $combined.Contains('SURVEY_PULSE_INITIALIZED')
$serverReady = $combined -match 'Done \([0-9.,]+s\)!'
$behavior = $combined.Contains('SURVEY_PULSE_BEHAVIOR_VERIFIED')
$fatal = $combined -match '(?i)(crash report|failed to start the minecraft server|exception in server tick loop)'
$hostContainsSurveySources = Test-Path -LiteralPath (Join-Path $hostRoot 'src/main/java/dev/example/surveypulse')
$jarSha256 = (Get-FileHash -LiteralPath $deployedJar -Algorithm SHA256).Hash.ToLowerInvariant()
$result = [ordered]@{
    schemaVersion = '1.0'
    kind = 'stage14c-packaged-real-world-behavior'
    generatorId = 'fabric-1.21.1'
    minecraftVersion = '1.21.1'
    loaderVersion = '0.19.3'
    javaHome = $javaHome
    compile = [ordered]@{ state = if ($build.exitCode -eq 0) {'passed'} else {'failed'}; exitCode = $build.exitCode }
    packagedJar = [ordered]@{ state = if (Test-Path -LiteralPath $deployedJar) {'passed'} else {'failed'}; path = 'run/mods/survey_pulse-1.0.jar'; sha256 = $jarSha256 }
    hostContainsSurveySources = [bool]$hostContainsSurveySources
    eula = [ordered]@{ state = 'accepted_for_isolated_fixture_only'; path = 'build/stage14c-packaged-host/run/eula.txt' }
    initializerExecuted = $initializer
    serverReady = $serverReady
    behaviorVerified = $behavior
    runtimeExitCode = $runtime.exitCode
    timedOut = $runtime.timedOut
    fatalSeen = $fatal
    behaviorScope = @(
        'real Minecraft ServerLevel',
        'isolated generated world',
        'controlled near/far ore placement',
        'radius 5 versus radius 8 scan result',
        'world blocks restored before shutdown'
    )
    explicitlyNotClaimed = @('real player right-click input', 'sneak key transition', 'cooldown timing', 'multiplayer isolation')
    logFile = 'evidence/stage14/2026-09-08/packaged-behavior-fabric-1.21.1.log'
    passed = ($build.exitCode -eq 0 -and -not $hostContainsSurveySources -and $initializer -and $serverReady -and $behavior -and -not $fatal -and $runtime.exitCode -eq 0)
    completedAt = [DateTimeOffset]::UtcNow.ToString('o')
}
[IO.File]::WriteAllText($evidenceJson, ($result | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
$result | ConvertTo-Json -Compress -Depth 8
if (-not $result.passed) { exit 1 }
