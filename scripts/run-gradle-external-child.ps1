[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RequestFile
)

$ErrorActionPreference = 'Continue'
$request = Get-Content -LiteralPath $RequestFile -Raw | ConvertFrom-Json
$exitCode = 1

try {
    Set-Location -LiteralPath $request.repositoryRoot
    $env:JAVA_HOME = (Resolve-Path $request.javaHome).Path
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"

    & (Join-Path $request.repositoryRoot 'gradlew.bat') @($request.gradleArgs) *> $request.logPath
    $exitCode = $LASTEXITCODE
} catch {
    $_ | Out-String | Set-Content -LiteralPath $request.logPath -Encoding UTF8
    $exitCode = 1
}

$result = [ordered]@{
    exitCode = $exitCode
    logPath = $request.logPath
    completedAt = (Get-Date).ToString('o')
}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $request.resultPath -Encoding UTF8
