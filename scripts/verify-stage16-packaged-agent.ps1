param(
    [Parameter(Mandatory = $true)] [string] $ProductExe,
    [Parameter(Mandatory = $true)] [string] $SourceWorkspaceFile,
    [Parameter(Mandatory = $true)] [string] $TrialRoot,
    [Parameter(Mandatory = $true)] [string] $EvidenceDirectory,
    [ValidateRange(30, 2400)] [int] $TimeoutSeconds = 1500
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$utf8 = [Text.UTF8Encoding]::new($false)
$product = (Resolve-Path -LiteralPath $ProductExe).Path
$sourceFile = (Resolve-Path -LiteralPath $SourceWorkspaceFile).Path
$sourceRoot = Split-Path -Parent $sourceFile
$trial = [IO.Path]::GetFullPath($TrialRoot)
$evidence = [IO.Path]::GetFullPath($EvidenceDirectory)
if (Test-Path -LiteralPath $trial) { throw 'TrialRoot must be a new directory; existing workspaces are never overwritten.' }
if ($trial.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'TrialRoot must be outside the source workspace.'
}
New-Item -ItemType Directory -Path $trial, $evidence -Force | Out-Null
$steps = [Collections.Generic.List[object]]::new()
$summary = [ordered]@{
    schemaVersion = '1.0'
    kind = 'stage16-packaged-agent-repair-loop'
    startedAt = [DateTimeOffset]::UtcNow.ToString('o')
    productExeSha256 = (Get-FileHash -LiteralPath $product -Algorithm SHA256).Hash.ToLowerInvariant()
    applicationJarSha256 = (Get-FileHash -LiteralPath (Join-Path (Split-Path -Parent $product) 'lib/copperbench.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
    execution = 'packaged executable only; no repository classpath or injected authorization'
    cacheState = 'existing machine caches; not a clean-machine certification'
    humanInterventionsDuringReplay = 0
    bootstrapCreation = 'not exercised; replay starts from a supplied workspace'
    freshAgent = $false
    passed = $false
    steps = $steps
}

function Invoke-Product([string] $Name, [string[]] $Arguments) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $product
    $info.WorkingDirectory = $trial
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = $utf8
    $info.StandardErrorEncoding = $utf8
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    foreach ($variable in @('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'JAVA_OPTS', 'GRADLE_OPTS', 'COPPERBENCH_STAGE5_GRADLE_EXECUTABLE')) {
        [void] $info.Environment.Remove($variable)
    }
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $process = [Diagnostics.Process]::Start($info)
    $stderr = $process.StandardError.ReadToEndAsync()
    $stdoutPath = Join-Path $evidence ($Name + '.jsonl')
    $writer = [IO.StreamWriter]::new($stdoutPath, $false, $utf8)
    $writer.AutoFlush = $true
    $messages = [Collections.Generic.List[object]]::new()
    $firstResponseMs = $null
    try {
        $pending = $process.StandardOutput.ReadLineAsync()
        while ($true) {
            if ($timer.Elapsed.TotalSeconds -gt $TimeoutSeconds) { throw "Product operation timed out: $Name" }
            if (-not $pending.Wait(200)) { continue }
            $line = $pending.GetAwaiter().GetResult()
            if ($null -eq $line) { break }
            $writer.WriteLine($line)
            if ($null -eq $firstResponseMs) { $firstResponseMs = $timer.ElapsedMilliseconds }
            if ([string]::IsNullOrWhiteSpace($line)) { throw "Empty stdout line in $Name" }
            $messages.Add(($line | ConvertFrom-Json -Depth 100))
            $pending = $process.StandardOutput.ReadLineAsync()
        }
        $remainingMs = [Math]::Max(1, [int](($TimeoutSeconds - $timer.Elapsed.TotalSeconds) * 1000))
        if (-not $process.WaitForExit($remainingMs)) { throw "Product did not exit: $Name" }
    } finally {
        if (-not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
        $writer.Dispose()
        [IO.File]::WriteAllText((Join-Path $evidence ($Name + '.stderr.log')), $stderr.GetAwaiter().GetResult(), $utf8)
    }
    if ($messages.Count -eq 0) { throw "No machine-readable output in $Name" }
    $result = $messages[$messages.Count - 1]
    if ($result.exitCode -ne $process.ExitCode) { throw "Process and JSON exit codes disagree in $Name" }
    $step = [ordered]@{
        name = $Name; exitCode = $process.ExitCode; status = $result.status
        elapsedMs = $timer.ElapsedMilliseconds; firstResponseMs = $firstResponseMs
        jsonMessages = $messages.Count; stdout = ($Name + '.jsonl')
    }
    $steps.Add($step)
    Write-Output "${Name}: exit=$($process.ExitCode), status=$($result.status), elapsed=$([Math]::Round($timer.Elapsed.TotalSeconds, 1))s" | Out-Host
    return $result
}

function Assert-Success($Result, [string] $Name) {
    if ($Result.exitCode -ne 0 -or $Result.status -ne 'succeeded') { throw "$Name failed; inspect its JSONL evidence." }
}

try {
    # Copy only authored input, pruning caches and user worlds before descent.
    $excludeRoot = @('.git', '.gradle', '.copperbench', '.idea', '.vscode', 'build', 'out', 'run', 'runs', 'logs', 'node_modules', '.env')
    $pendingDirectories = [Collections.Generic.Queue[string]]::new()
    $pendingDirectories.Enqueue($sourceRoot)
    $sourceHashes = [ordered]@{}
    while ($pendingDirectories.Count -gt 0) {
        $directory = $pendingDirectories.Dequeue()
        foreach ($entry in Get-ChildItem -LiteralPath $directory -Force) {
            $relative = [IO.Path]::GetRelativePath($sourceRoot, $entry.FullName)
            $parts = $relative.Replace('\', '/').Split('/')
            if ($parts.Length -gt 1 -and $parts[0] -eq '.mcreator' -and
                @('localHistory', 'workspaceBackups', 'userSettings') -contains $parts[1]) { continue }
            if (($directory -eq $sourceRoot -and ($excludeRoot -contains $entry.Name -or $entry.Name.StartsWith('.env.'))) -or
                @('.git', '.gradle', 'node_modules') -contains $entry.Name) { continue }
            if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Fixture contains a reparse point: $relative" }
            $destination = Join-Path $trial $relative
            if ($entry.PSIsContainer) {
                New-Item -ItemType Directory -Path $destination -Force | Out-Null
                $pendingDirectories.Enqueue($entry.FullName)
            } else {
                Copy-Item -LiteralPath $entry.FullName -Destination $destination
                $sourceHashes[$relative] = (Get-FileHash -LiteralPath $entry.FullName -Algorithm SHA256).Hash
            }
        }
    }
    $workspace = Join-Path $trial (Split-Path -Leaf $sourceFile)
    $baseArguments = @('headless', '--workspace', $workspace)
    Assert-Success (Invoke-Product '01-discovery' @('bootstrap', 'list-generators')) 'Generator discovery'
    $unapprovedTarget = Join-Path $trial 'unapproved-child'
    $denial = Invoke-Product '01b-no-prompt' @('bootstrap', 'create-workspace', '--generator-id', 'fabric-1.21.1',
        '--mod-name', 'Stage16 Approval Probe', '--mod-id', 'stage16_approval_probe', '--workspace-folder', $unapprovedTarget, '--no-prompt', 'true')
    if ($denial.exitCode -eq 0 -or $denial.code -ne 'USER_APPROVAL_REQUIRED' -or (Test-Path -LiteralPath $unapprovedTarget)) {
        throw 'Non-interactive creation did not return an approval request without creating the workspace.'
    }
    $summary.bootstrapCreation = 'missing-authorization request verified without a dialog; authorized creation not exercised'
    $environment = Invoke-Product '02-environment' ($baseArguments + 'environment')
    Assert-Success $environment 'Workspace environment'
    $summary.generatorId = $environment.data.generator.id
    $summary.bundledJava = $environment.data.execution.java

    $probe = Join-Path $trial 'src/main/java/copperbench/trial/CompileFailureProbe.java'
    if (Test-Path -LiteralPath $probe) { throw 'Fixture already contains the reserved repair probe.' }
    New-Item -ItemType Directory -Path (Split-Path -Parent $probe) -Force | Out-Null
    [IO.File]::WriteAllText($probe, "package copperbench.trial; public final class CompileFailureProbe { int value = STAGE16_EXPECTED_COMPILE_FAILURE; }`n", $utf8)
    $failedBuild = Invoke-Product '03-compile-failure' ($baseArguments + @('build', '--stream', 'true'))
    $compileErrors = @($failedBuild.diagnostics | Where-Object { $_.code -eq 'JAVA_COMPILE_ERROR' -and $_.path -like '*CompileFailureProbe.java' })
    if ($failedBuild.exitCode -eq 0 -or $compileErrors.Count -eq 0) { throw 'Compiler failure did not produce an actionable source diagnostic.' }
    [IO.File]::WriteAllText($probe, "package copperbench.trial; public final class CompileFailureProbe { int value = 16; }`n", $utf8)
    Assert-Success (Invoke-Product '04-repaired-build' ($baseArguments + @('build', '--stream', 'true'))) 'Repaired build'

    $configurationPath = Join-Path $trial 'copperbench-tests.json'
    $configurationText = [IO.File]::ReadAllText($configurationPath)
    $configuration = $configurationText | ConvertFrom-Json -Depth 50
    if ($configuration.mode -ne 'packaged_jar') { throw 'This gate requires packaged_jar acceptance.' }
    $requiredTests = $configuration.minimumTests
    $configuration.minimumTests = 10000
    [IO.File]::WriteAllText($configurationPath, ($configuration | ConvertTo-Json -Depth 50), $utf8)
    try {
        $insufficient = Invoke-Product '05-insufficient-tests' ($baseArguments + @('run-gametest', '--stream', 'true'))
        if ($insufficient.exitCode -eq 0 -or $insufficient.task.verification.reasonCode -ne 'GAMETEST_NO_TESTS' -or
            $insufficient.task.verification.acceptanceExecuted -lt 1) {
            throw 'Insufficient acceptance coverage was not rejected after real test execution.'
        }
    } finally {
        [IO.File]::WriteAllText($configurationPath, $configurationText, $utf8)
    }
    $accepted = Invoke-Product '06-accepted' ($baseArguments + @('run-gametest', '--stream', 'true'))
    Assert-Success $accepted 'Gameplay acceptance'
    $verification = $accepted.task.verification
    if ($verification.status -ne 'passed' -or -not $verification.sourceCurrentAtCompletion -or
        $verification.acceptanceExecuted -lt $requiredTests) { throw 'Acceptance does not prove current source and required coverage.' }
    foreach ($relative in $sourceHashes.Keys) {
        if ((Get-FileHash -LiteralPath (Join-Path $sourceRoot $relative) -Algorithm SHA256).Hash -ne $sourceHashes[$relative]) {
            throw "Source workspace changed during replay: $relative"
        }
    }
    foreach ($field in @('artifactPath', 'reportPath')) {
        $expectedHash = if ($field -eq 'artifactPath') { $verification.artifactSha256 } else { $verification.reportSha256 }
        if ((Get-FileHash -LiteralPath $verification.$field -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedHash) {
            throw "Evidence hash mismatch: $field"
        }
    }
    Copy-Item -LiteralPath $verification.reportPath -Destination (Join-Path $evidence 'gametest.xml')
    Copy-Item -LiteralPath $verification.sourceSnapshot.manifestPath -Destination (Join-Path $evidence 'source-manifest.json')
    $summary.verification = $verification
    $summary.sourceWorkspaceUnchanged = $true
    $summary.passed = $true
} catch {
    $summary.failure = $_.Exception.Message
    Write-Warning $_.Exception.Message
} finally {
    $summary.completedAt = [DateTimeOffset]::UtcNow.ToString('o')
    [IO.File]::WriteAllText((Join-Path $evidence 'summary.json'), ($summary | ConvertTo-Json -Depth 100), $utf8)
}
if (-not $summary.passed) { exit 1 }
