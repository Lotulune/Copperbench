[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $MCreatorSource,

    [Parameter(Mandatory)]
    [string] $FabricGeneratorSource,

    [string] $OutputPath
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$lockPath = Join-Path $projectRoot 'compliance\baseline.lock.json'
$baseline = Get-Content -Raw -LiteralPath $lockPath | ConvertFrom-Json

function Test-LockedSource {
    param(
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [string] $SourcePath,
        [Parameter(Mandatory)] $Specification
    )

    $resolvedSource = (Resolve-Path -LiteralPath $SourcePath).Path
    $head = (& git -C $resolvedSource rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to read Git HEAD for $Name at $resolvedSource"
    }

    $fileChecks = foreach ($property in $Specification.files.PSObject.Properties) {
        $relativePath = $property.Name
        $expectedHash = ([string] $property.Value).ToLowerInvariant()
        $filePath = Join-Path $resolvedSource $relativePath
        $actualHash = if (Test-Path -LiteralPath $filePath) {
            (Get-FileHash -Algorithm SHA256 -LiteralPath $filePath).Hash.ToLowerInvariant()
        } else {
            $null
        }

        [ordered]@{
            path = $relativePath
            expectedSha256 = $expectedHash
            actualSha256 = $actualHash
            passed = $actualHash -eq $expectedHash
        }
    }

    [ordered]@{
        name = $Name
        sourcePath = $resolvedSource
        expectedCommit = $Specification.commit
        actualCommit = $head
        commitPassed = $head -eq $Specification.commit
        files = @($fileChecks)
        passed = ($head -eq $Specification.commit) -and -not ($fileChecks.passed -contains $false)
    }
}

$checks = @(
    Test-LockedSource -Name 'mcreator' -SourcePath $MCreatorSource -Specification $baseline.sources.mcreator
    Test-LockedSource -Name 'fabricGenerator' -SourcePath $FabricGeneratorSource -Specification $baseline.sources.fabricGenerator
)

$report = [ordered]@{
    schemaVersion = 1
    checkedAt = (Get-Date).ToString('o')
    baseline = $lockPath
    passed = -not ($checks.passed -contains $false)
    sources = $checks
}

if ($OutputPath) {
    $outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
    $outputDirectory = Split-Path -Parent $outputFullPath
    if ($outputDirectory) {
        New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $outputFullPath -Encoding UTF8
}

$report | ConvertTo-Json -Depth 8
if (-not $report.passed) {
    exit 1
}
