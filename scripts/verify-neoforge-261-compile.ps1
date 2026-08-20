[CmdletBinding()]
param(
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$bundledJava = Join-Path $RepositoryRoot 'jdk\jbr25_win_64'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-8\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

$summary = [ordered]@{
	schemaVersion = '1.0'
	kind = 'preview-track-compile-probe-runner'
	generatorId = 'neoforge-26.1.2'
	probeRan = $false
	compileSucceeded = $false
	goldenClaimed = $false
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
			'--tests' 'dev.copperbench.generator.neoforge.NeoForge261CompileProbeTest' `
			'-Dcopperbench.neoforge261Compile=true' `
			'--no-daemon'
		if ($LASTEXITCODE -ne 0) {
			throw "NeoForge 26.1 compile probe test failed with exit code $LASTEXITCODE"
		}
	}
	finally {
		Pop-Location
	}

	$probePath = Join-Path $evidenceDir 'neoforge-261-compile.json'
	if (-not (Test-Path -LiteralPath $probePath)) {
		throw "Probe test passed but evidence file is missing: $probePath"
	}
	$probe = Get-Content -Raw -LiteralPath $probePath | ConvertFrom-Json
	$summary.probeRan = [bool]$probe.generateSucceeded
	$summary.compileSucceeded = [bool]$probe.compileSucceeded
	$summary.catalogStatus = [string]$probe.catalogStatus
	$summary.catalogReasonCode = [string]$probe.catalogReasonCode
	$summary.probeEvidence = $probePath
	if (-not $probe.generateSucceeded) {
		throw 'NeoForge 26.1 generate did not succeed; refusing to treat the probe as recorded'
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
	$summaryPath = Join-Path $evidenceDir 'neoforge-261-compile-runner.json'
	($summary | ConvertTo-Json -Depth 5) | Set-Content -LiteralPath $summaryPath -Encoding utf8
	Write-Output ("evidence=" + $summaryPath)
}

if ($summary.compileSucceeded) {
	Write-Output 'NeoForge 26.1 compile probe: generate + cache-warm/build produced a jar.'
} else {
	Write-Output 'NeoForge 26.1 compile probe: generate succeeded, compile did not.'
}
