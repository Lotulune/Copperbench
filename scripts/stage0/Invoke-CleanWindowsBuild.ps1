[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $SourcePath,

    [Parameter(Mandatory)]
    [string] $JdkHome,

    [Parameter(Mandatory)]
    [string] $GradleUserHome,

    [string[]] $Tasks = @('clean', 'test'),

    [string] $OutputPath,

    [uri] $ProxyUri
)

$ErrorActionPreference = 'Stop'

$resolvedSource = (Resolve-Path -LiteralPath $SourcePath).Path
$resolvedJdk = (Resolve-Path -LiteralPath $JdkHome).Path
$gradleWrapper = Join-Path $resolvedSource 'gradlew.bat'
$javaExecutable = Join-Path $resolvedJdk 'bin\java.exe'
$javacExecutable = Join-Path $resolvedJdk 'bin\javac.exe'

foreach ($requiredPath in @($gradleWrapper, $javaExecutable, $javacExecutable)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required build file not found: $requiredPath"
    }
}

$gradleHomeFullPath = [System.IO.Path]::GetFullPath($GradleUserHome)
New-Item -ItemType Directory -Force -Path $gradleHomeFullPath | Out-Null

$reportPath = if ($OutputPath) { [System.IO.Path]::GetFullPath($OutputPath) } else { $null }
$logPath = if ($reportPath) { [System.IO.Path]::ChangeExtension($reportPath, '.log') } else {
    Join-Path $gradleHomeFullPath 'stage-0-build.log'
}
if ($reportPath) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $reportPath) | Out-Null
}

$previousJavaHome = $env:JAVA_HOME
$previousGradleHome = $env:GRADLE_USER_HOME
$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$startedAt = Get-Date
$exitCode = 1

try {
    $env:JAVA_HOME = $resolvedJdk
    $env:GRADLE_USER_HOME = $gradleHomeFullPath
    if ($ProxyUri) {
        $proxyOptions = "-Dhttp.proxyHost=$($ProxyUri.Host) -Dhttp.proxyPort=$($ProxyUri.Port) -Dhttps.proxyHost=$($ProxyUri.Host) -Dhttps.proxyPort=$($ProxyUri.Port)"
        $env:JAVA_TOOL_OPTIONS = @($previousJavaToolOptions, $proxyOptions).Where({ $_ }) -join ' '
    }

    Push-Location $resolvedSource
    try {
        & $gradleWrapper --no-daemon --no-build-cache @Tasks 2>&1 | Tee-Object -FilePath $logPath
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleHome
    $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
}

$completedAt = Get-Date
$report = [ordered]@{
    schemaVersion = 1
    startedAt = $startedAt.ToString('o')
    completedAt = $completedAt.ToString('o')
    durationSeconds = [Math]::Round(($completedAt - $startedAt).TotalSeconds, 3)
    sourcePath = $resolvedSource
    jdkHome = $resolvedJdk
    gradleUserHome = $gradleHomeFullPath
    tasks = $Tasks
    exitCode = $exitCode
    passed = $exitCode -eq 0
    logPath = $logPath
}

if ($reportPath) {
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reportPath -Encoding UTF8
}
$report | ConvertTo-Json -Depth 5
exit $exitCode
