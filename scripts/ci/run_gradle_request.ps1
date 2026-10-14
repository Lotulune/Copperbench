[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$RequestFilePath)

$ErrorActionPreference = 'Stop'
$gradleArguments = @(Get-Content -LiteralPath $RequestFilePath -Raw -Encoding UTF8 | ConvertFrom-Json)
if ($gradleArguments.Count -eq 0 -or @($gradleArguments | Where-Object { $_ -isnot [string] }).Count -gt 0) {
    throw 'Expected a nonempty JSON array of Gradle argument strings.'
}
$runner = Join-Path $PSScriptRoot '../run-gradle-external.ps1'
& $runner -GradleArgs $gradleArguments
exit $LASTEXITCODE
