[CmdletBinding()]
param(
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

# Honest Fabric 1.20.1 runClient probe. Failure is recorded, not a golden claim.
$ErrorActionPreference = 'Stop'
$jdk21 = Join-Path $RepositoryRoot 'jdk\jdk21_win_64'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-8\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

$summary = [ordered]@{
	schemaVersion = '1.0'
	kind = 'preview-track-runclient-probe-runner'
	generatorId = 'fabric-1.20.1'
	probeRan = $false
	runClientSucceeded = $false
	goldenClaimed = $false
	catalogStatus = 'PREVIEW'
	catalogReasonCode = 'TRACK_GENERATE_READY'
}
$startedAt = Get-Date
$previousJavaHome = $env:JAVA_HOME
try {
	if (-not (Test-Path -LiteralPath (Join-Path $jdk21 'bin\java.exe'))) {
		throw "Bundled JDK 21 is missing: $jdk21"
	}
	$env:JAVA_HOME = (Resolve-Path $jdk21).Path
	$env:Path = "$env:JAVA_HOME\bin;$env:Path"
	Push-Location $RepositoryRoot
	try {
		& .\gradlew.bat 'test' `
			'--tests' 'dev.copperbench.generator.fabric.Fabric1201RunClientProbeTest' `
			'-Dcopperbench.fabric1201RunClient=true' `
			'--no-daemon'
		if ($LASTEXITCODE -ne 0) {
			throw "Fabric 1.20.1 runClient probe test failed with exit code $LASTEXITCODE"
		}
		$resultXml = Join-Path $RepositoryRoot 'build\test-results\test\TEST-dev.copperbench.generator.fabric.Fabric1201RunClientProbeTest.xml'
		if (-not (Test-Path -LiteralPath $resultXml)) {
			throw 'Fabric 1.20.1 runClient probe did not produce JUnit results'
		}
		[xml]$junit = Get-Content -LiteralPath $resultXml
		$suite = $junit.testsuite
		if ([int]$suite.skipped -gt 0 -or [int]$suite.tests -lt 1 -or [int]$suite.failures -gt 0) {
			throw "Fabric 1.20.1 runClient probe did not run to completion (tests=$($suite.tests) skipped=$($suite.skipped) failures=$($suite.failures))"
		}
	}
	finally {
		Pop-Location
	}

	$probePath = Join-Path $evidenceDir 'fabric-1201-runclient.json'
	if (-not (Test-Path -LiteralPath $probePath)) {
		throw "Probe test passed but evidence file is missing: $probePath"
	}
	$probe = Get-Content -Raw -LiteralPath $probePath | ConvertFrom-Json
	$summary.probeRan = [bool]$probe.generateSucceeded
	$summary.runClientSucceeded = [bool]$probe.runClientSucceeded
	$summary.goldenClaimed = $false
	$summary.catalogStatus = [string]$probe.catalogStatus
	$summary.catalogReasonCode = [string]$probe.catalogReasonCode
	$summary.probeEvidence = $probePath
	if (-not $probe.generateSucceeded) {
		throw 'Fabric 1.20.1 generate did not succeed; refusing to treat the probe as recorded'
	}
}
catch {
	$summary.error = $_.Exception.Message
	throw
}
finally {
	$env:JAVA_HOME = $previousJavaHome
	$summary.completedAt = (Get-Date).ToString('o')
	$summary.durationSeconds = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
	$summaryPath = Join-Path $evidenceDir 'fabric-1201-runclient-runner.json'
	($summary | ConvertTo-Json -Depth 5) | Set-Content -LiteralPath $summaryPath -Encoding utf8
	Write-Output ("evidence=" + $summaryPath)
}

if ($summary.runClientSucceeded) {
	Write-Output 'Fabric 1.20.1 runClient probe: readiness marker seen. Track remains PREVIEW / TRACK_GENERATE_READY (not golden).'
} else {
	Write-Output 'Fabric 1.20.1 runClient probe: generate succeeded, client did not reach readiness. Track remains PREVIEW.'
}
