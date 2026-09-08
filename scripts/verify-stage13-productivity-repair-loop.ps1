param(
    [Parameter(Mandatory = $true)]
    [string] $ProductExe,

    [Parameter(Mandatory = $true)]
    [string] $SourceWorkspaceFile,

    [Parameter(Mandatory = $true)]
    [string] $ReplayRoot,

    [Parameter(Mandatory = $true)]
    [string] $EvidencePath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-HeadlessBuild {
    param(
        [Parameter(Mandatory = $true)] [string] $Executable,
        [Parameter(Mandatory = $true)] [string] $WorkspaceFile,
        [Parameter(Mandatory = $true)] [string] $StdoutPath,
        [Parameter(Mandatory = $true)] [string] $StderrPath
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Executable
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $startInfo.StandardErrorEncoding = [System.Text.UTF8Encoding]::new($false)
    $startInfo.ArgumentList.Add('headless')
    $startInfo.ArgumentList.Add('--workspace')
    $startInfo.ArgumentList.Add($WorkspaceFile)
    $startInfo.ArgumentList.Add('build')

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "Failed to start exported product: $Executable"
    }

    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    $utf8 = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($StdoutPath, $stdout, $utf8)
    [System.IO.File]::WriteAllText($StderrPath, $stderr, $utf8)

    if ([string]::IsNullOrWhiteSpace($stdout)) {
        throw "Headless build produced no machine-readable stdout. See $StderrPath"
    }

    try {
        $result = $stdout.Trim() | ConvertFrom-Json -Depth 100
    } catch {
        throw "Headless build stdout is not valid JSON. See $StdoutPath"
    }

    [pscustomobject]@{
        ExitCode = $process.ExitCode
        Result = $result
        StdoutPath = $StdoutPath
        StderrPath = $StderrPath
    }
}

function Get-FullPath {
    param([Parameter(Mandatory = $true)] [string] $Path)
    [System.IO.Path]::GetFullPath($Path)
}

$product = (Resolve-Path -LiteralPath $ProductExe).Path
$productRoot = Split-Path -Parent $product
$applicationJar = Join-Path $productRoot 'lib\copperbench.jar'
if (-not (Test-Path -LiteralPath $applicationJar -PathType Leaf)) {
    throw "Exported product is missing lib/copperbench.jar: $applicationJar"
}
$sourceWorkspace = (Resolve-Path -LiteralPath $SourceWorkspaceFile).Path
$sourceRoot = Split-Path -Parent $sourceWorkspace
$replayRootFull = Get-FullPath $ReplayRoot
$evidenceFull = Get-FullPath $EvidencePath

if (Test-Path -LiteralPath $replayRootFull) {
    Remove-Item -LiteralPath $replayRootFull -Recurse -Force
}
New-Item -ItemType Directory -Path $replayRootFull -Force | Out-Null

$workspaceRoot = Join-Path $replayRootFull 'workspace'
New-Item -ItemType Directory -Path $workspaceRoot -Force | Out-Null
Get-ChildItem -LiteralPath $sourceRoot -Force | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $workspaceRoot -Recurse -Force
}

foreach ($staleDirectory in @('.gradle', 'build', 'run', '.copperbench')) {
    $candidate = Join-Path $workspaceRoot $staleDirectory
    if (Test-Path -LiteralPath $candidate) {
        Remove-Item -LiteralPath $candidate -Recurse -Force
    }
}

$workspaceFile = Join-Path $workspaceRoot (Split-Path -Leaf $sourceWorkspace)
if (-not (Test-Path -LiteralPath $workspaceFile -PathType Leaf)) {
    throw "Copied workspace is missing its .mcreator file: $workspaceFile"
}

$brokenSource = Join-Path $workspaceRoot 'src\main\java\net\example\Stage13RepairProbe.java'
New-Item -ItemType Directory -Path (Split-Path -Parent $brokenSource) -Force | Out-Null
$invalidJava = @'
package net.example;

public final class Stage13RepairProbe {
    public static void run() { missingStage13RepairSymbol(); }
}
'@
[System.IO.File]::WriteAllText($brokenSource, $invalidJava, [System.Text.UTF8Encoding]::new($false))

$failureStdout = Join-Path $replayRootFull 'failure.stdout.json'
$failureStderr = Join-Path $replayRootFull 'failure.stderr.txt'
$failure = Invoke-HeadlessBuild -Executable $product -WorkspaceFile $workspaceFile `
    -StdoutPath $failureStdout -StderrPath $failureStderr

if ($failure.ExitCode -eq 0 -or $failure.Result.status -ne 'failed' -or $failure.Result.task.state -ne 'failed') {
    throw "Expected the injected Java error to fail the exported-product build. See $failureStdout"
}

$compileDiagnostic = @($failure.Result.diagnostics) | Where-Object { $_.code -eq 'JAVA_COMPILE_ERROR' } | Select-Object -First 1
if ($null -eq $compileDiagnostic) {
    throw "Expected JAVA_COMPILE_ERROR in exported-product diagnostics. See $failureStdout"
}

$diagnosticPath = [string] $compileDiagnostic.path
if (-not $diagnosticPath.StartsWith('/')) {
    throw "Compiler diagnostic path is not workspace-relative: $diagnosticPath"
}

$relativeDiagnosticPath = $diagnosticPath.TrimStart('/').Replace('/', [System.IO.Path]::DirectorySeparatorChar)
$locatedFile = Get-FullPath (Join-Path $workspaceRoot $relativeDiagnosticPath)
$workspacePrefix = (Get-FullPath $workspaceRoot).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
if (-not $locatedFile.StartsWith($workspacePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Diagnostic path escaped the replay workspace: $diagnosticPath"
}
if (-not (Test-Path -LiteralPath $locatedFile -PathType Leaf)) {
    throw "Diagnostic path did not locate an existing source file: $locatedFile"
}
if ((Get-FullPath $brokenSource) -ne $locatedFile) {
    throw "Compiler diagnostic located an unexpected source file: $locatedFile"
}

$lineNumber = $null
if ([string] $compileDiagnostic.message.fallback -match '^Line\s+(\d+):') {
    $lineNumber = [int] $Matches[1]
}
if ($null -eq $lineNumber) {
    throw "Compiler diagnostic did not expose a line number: $($compileDiagnostic.message.fallback)"
}

$fixedJava = @'
package net.example;

public final class Stage13RepairProbe {
    public static void run() { }
}
'@
[System.IO.File]::WriteAllText($locatedFile, $fixedJava, [System.Text.UTF8Encoding]::new($false))
$fixedSourceHash = (Get-FileHash -LiteralPath $locatedFile -Algorithm SHA256).Hash.ToLowerInvariant()

$rebuildStdout = Join-Path $replayRootFull 'rebuild.stdout.json'
$rebuildStderr = Join-Path $replayRootFull 'rebuild.stderr.txt'
$rebuild = Invoke-HeadlessBuild -Executable $product -WorkspaceFile $workspaceFile `
    -StdoutPath $rebuildStdout -StderrPath $rebuildStderr

$rebuildLogs = (@($rebuild.Result.logs) | ForEach-Object { [string] $_.text }) -join "`n"
if ($rebuild.ExitCode -ne 0 -or $rebuild.Result.status -ne 'succeeded' -or $rebuild.Result.task.state -ne 'succeeded') {
    throw "Expected repaired workspace to rebuild successfully. See $rebuildStdout"
}
if (-not $rebuildLogs.Contains('BUILD SUCCESSFUL')) {
    throw "Successful rebuild did not contain Gradle's BUILD SUCCESSFUL marker. See $rebuildStdout"
}

$evidenceParent = Split-Path -Parent $evidenceFull
if ($evidenceParent) {
    New-Item -ItemType Directory -Path $evidenceParent -Force | Out-Null
}

$evidence = [ordered]@{
    schemaVersion = 1
    generatedAt = [DateTimeOffset]::UtcNow.ToString('O')
    pass = $true
    sourceRevision = (& git rev-parse HEAD).Trim()
    product = [ordered]@{
        path = $product
        sha256 = (Get-FileHash -LiteralPath $product -Algorithm SHA256).Hash.ToLowerInvariant()
        applicationJar = $applicationJar
        applicationJarSha256 = (Get-FileHash -LiteralPath $applicationJar -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    workspace = [ordered]@{
        source = $sourceWorkspace
        replay = $workspaceFile
    }
    failure = [ordered]@{
        processExit = $failure.ExitCode
        status = [string] $failure.Result.status
        taskState = [string] $failure.Result.task.state
        diagnosticCode = [string] $compileDiagnostic.code
        diagnosticPath = $diagnosticPath
        diagnosticFallback = [string] $compileDiagnostic.message.fallback
        diagnosticLine = $lineNumber
        locatedFile = $locatedFile
        stdout = $failureStdout
        stderr = $failureStderr
    }
    repair = [ordered]@{
        editedFromDiagnosticLocation = $true
        fixedSourceSha256 = $fixedSourceHash
    }
    rebuild = [ordered]@{
        processExit = $rebuild.ExitCode
        status = [string] $rebuild.Result.status
        taskState = [string] $rebuild.Result.task.state
        buildSuccessfulMarker = $true
        diagnostics = @($rebuild.Result.diagnostics).Count
        stdout = $rebuildStdout
        stderr = $rebuildStderr
    }
}

$json = $evidence | ConvertTo-Json -Depth 20
[System.IO.File]::WriteAllText($evidenceFull, $json + "`n", [System.Text.UTF8Encoding]::new($false))
Write-Output $json
