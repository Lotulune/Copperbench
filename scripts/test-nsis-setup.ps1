[CmdletBinding()]
param(
    [ValidateSet('all', 'compiler-missing', 'plugin-missing', 'complete', 'malformed')]
    [string]$Scenario = 'all',
    [switch]$ExternalGradle
)

$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'NSIS setup regression requires Windows.' }
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runRoot = Join-Path $repository ('build/nsis-setup-regression/' + [Guid]::NewGuid().ToString('N'))
$scenarios = if ($Scenario -eq 'all') { @('compiler-missing', 'plugin-missing', 'complete', 'malformed') } else { @($Scenario) }
$results = @()
foreach ($case in $scenarios) {
    $fixture = Join-Path $runRoot $case
    New-Item -ItemType Directory -Path $fixture | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'tests/nsis-setup-fixture.gradle') -Destination (Join-Path $fixture 'build.gradle')
    Copy-Item -LiteralPath (Join-Path $repository 'platform/setup.gradle') -Destination (Join-Path $fixture 'setup-under-test.gradle')
    "rootProject.name = 'nsis-setup-regression'" | Set-Content -LiteralPath (Join-Path $fixture 'settings.gradle') -Encoding UTF8
    $log = Join-Path $fixture 'gradle.log'
    $arguments = @('--offline', '--no-daemon', '--max-workers=1', '--project-dir', $fixture,
        ('-Pscenario=' + $case), 'verifyNsisSetup')
    if ($ExternalGradle) {
        & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'run-gradle-external.ps1') @arguments *> $log
    } else {
        & (Join-Path $repository 'gradlew.bat') @arguments *> $log
    }
    $exitCode = $LASTEXITCODE
    $logText = Get-Content -LiteralPath $log -Raw -Encoding UTF8
    $passed = if ($case -eq 'malformed') {
        $exitCode -ne 0 -and $logText.Contains('NSIS compiler is missing after extraction')
    } else {
        $exitCode -eq 0 -and $logText.Contains("NSIS fixture passed: $case;")
    }
    $results += [ordered]@{ scenario = $case; passed = $passed; exitCode = $exitCode; log = $log }
    $results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $runRoot 'results.json') -Encoding UTF8
    if (-not $passed) {
        Get-Content -LiteralPath $log -Tail 60
        throw "NSIS setup regression failed: $case. Evidence: $runRoot"
    }
    Write-Output "NSIS setup passed: $case"
}
Write-Output "NSIS setup evidence: $runRoot"
