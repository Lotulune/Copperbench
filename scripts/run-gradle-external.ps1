[CmdletBinding()]
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs,

    [int]$TimeoutMinutes = 120
)

$ErrorActionPreference = 'Stop'

if (-not $IsWindows) {
    throw 'run-gradle-external.ps1 is only needed on Windows.'
}
if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    throw 'Pass at least one Gradle argument, for example: test --tests dev.copperbench.shell.Stage9NativeJcefAccessibilityTest'
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runDirectory = Join-Path $repositoryRoot '.tmp\gradle-external'
$runId = [Guid]::NewGuid().ToString('N')
$requestPath = Join-Path $runDirectory "$runId.request.json"
$resultPath = Join-Path $runDirectory "$runId.result.json"
$logPath = Join-Path $runDirectory "$runId.log"
$childScript = (Resolve-Path (Join-Path $PSScriptRoot 'run-gradle-external-child.ps1')).Path
$javaHome = (Resolve-Path (Join-Path $repositoryRoot 'jdk\jbr25_win_64')).Path

New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
$request = [ordered]@{
    repositoryRoot = $repositoryRoot
    javaHome = $javaHome
    gradleArgs = @($GradleArgs)
    resultPath = $resultPath
    logPath = $logPath
}
$request | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $requestPath -Encoding UTF8

$pwsh = (Get-Command pwsh -ErrorAction Stop).Source
$commandLine = '"{0}" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "{1}" -RequestFile "{2}"' -f $pwsh, $childScript, $requestPath
$created = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $commandLine }
if ($created.ReturnValue -ne 0) {
    throw "Could not create the external Gradle process (WMI return code $($created.ReturnValue))."
}

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
while (-not (Test-Path -LiteralPath $resultPath) -and (Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 250
}
if (-not (Test-Path -LiteralPath $resultPath)) {
    throw "External Gradle process timed out after $TimeoutMinutes minutes. Log: $logPath"
}

$result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
if (Test-Path -LiteralPath $logPath) {
    Get-Content -LiteralPath $logPath
}
Write-Output "externalGradleLog=$logPath"

[System.IO.File]::Delete($requestPath)
[System.IO.File]::Delete($resultPath)
exit ([int]$result.exitCode)
