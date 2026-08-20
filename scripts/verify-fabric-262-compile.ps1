[CmdletBinding()]
param(
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

# Honest Fabric 26.2 compile probe. A failed Gradle compile is recorded, not
# treated as a gate pass, and never promotes the track to supported/golden.
$ErrorActionPreference = 'Stop'
$bundledJava = Join-Path $RepositoryRoot 'jdk\jbr25_win_64'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-8\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

$summary = [ordered]@{
	schemaVersion = '1.0'
	kind = 'preview-track-compile-probe-runner'
	generatorId = 'fabric-26.2'
	probeRan = $false
	compileSucceeded = $false
	goldenClaimed = $false
	catalogStatus = 'PREVIEW'
	catalogReasonCode = 'TRACK_GENERATE_READY'
}
$startedAt = Get-Date
$previousJavaHome = $env:JAVA_HOME
try {
	if (-not (Test-Path -LiteralPath (Join-Path $bundledJava 'bin\java.exe'))) {
		throw "Bundled JBR 25 is missing: $bundledJava"
	}
	$env:JAVA_HOME = (Resolve-Path $bundledJava).Path
	$env:Path = "$env:JAVA_HOME\bin;$env:Path"
	Push-Location $RepositoryRoot
	try {
		& .\gradlew.bat 'test' `
			'--tests' 'dev.copperbench.generator.fabric.Fabric262CompileProbeTest' `
			'-Dcopperbench.fabric262Compile=true' `
			'--no-daemon'
		if ($LASTEXITCODE -ne 0) {
			throw "Fabric 26.2 compile probe test failed with exit code $LASTEXITCODE (generate or probe harness, not a silent compile miss)"
		}
		$resultXml = Join-Path $RepositoryRoot 'build\test-results\test\TEST-dev.copperbench.generator.fabric.Fabric262CompileProbeTest.xml'
		if (-not (Test-Path -LiteralPath $resultXml)) {
			throw 'Fabric 26.2 compile probe did not produce JUnit results'
		}
		[xml]$junit = Get-Content -LiteralPath $resultXml
		$suite = $junit.testsuite
		if ([int]$suite.skipped -gt 0 -or [int]$suite.tests -lt 1 -or [int]$suite.failures -gt 0) {
			throw "Fabric 26.2 compile probe did not run to completion (tests=$($suite.tests) skipped=$($suite.skipped) failures=$($suite.failures))"
		}
	}
	finally {
		Pop-Location
	}

	$probePath = Join-Path $evidenceDir 'fabric-262-compile.json'
	if (-not (Test-Path -LiteralPath $probePath)) {
		throw "Probe test passed but evidence file is missing: $probePath"
	}
	$probe = Get-Content -Raw -LiteralPath $probePath | ConvertFrom-Json
	$summary.probeRan = [bool]$probe.generateSucceeded
	$summary.compileSucceeded = [bool]$probe.compileSucceeded
	$summary.goldenClaimed = $false
	$summary.catalogStatus = [string]$probe.catalogStatus
	$summary.catalogReasonCode = [string]$probe.catalogReasonCode
	$summary.probeEvidence = $probePath
	if (-not $probe.generateSucceeded) {
		throw 'Fabric 26.2 generate did not succeed; refusing to treat the probe as recorded'
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
	$summaryPath = Join-Path $evidenceDir 'fabric-262-compile-runner.json'
	($summary | ConvertTo-Json -Depth 5) | Set-Content -LiteralPath $summaryPath -Encoding utf8
	Write-Output ("evidence=" + $summaryPath)
}

if ($summary.compileSucceeded) {
	Write-Output 'Fabric 26.2 compile probe: generate + cache-warm/build produced a jar. Track remains PREVIEW / TRACK_GENERATE_READY (not golden).'
} else {
	Write-Output 'Fabric 26.2 compile probe: generate succeeded, compile did not. Track remains PREVIEW / TRACK_GENERATE_READY.'
}
