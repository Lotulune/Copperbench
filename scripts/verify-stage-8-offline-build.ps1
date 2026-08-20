[CmdletBinding()]
param(
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$bundledJava = Join-Path $RepositoryRoot 'jdk\jbr25_win_64'
$jdk21 = Join-Path $RepositoryRoot 'jdk\jdk21_win_64'
$gradleHome = Join-Path $env:USERPROFILE '.gradle'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-8\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'offline-cached-dependency-build'
	loaders = @('fabric-1.21.1', 'neoforge-1.21.1')
	gradleUserHome = $gradleHome
	jdk21 = $jdk21
	mode = 'gradle --offline'
	osNetworkDisconnected = $false
	passed = $false
}
$startedAt = Get-Date
$previousJavaHome = $env:JAVA_HOME
try {
	if (-not (Test-Path -LiteralPath (Join-Path $jdk21 'bin\java.exe'))) {
		throw "Bundled JDK 21 is missing: $jdk21"
	}
	$wrapperDist = Join-Path $gradleHome 'wrapper\dists\gradle-9.6.0-bin'
	$fabricApi = Join-Path $gradleHome 'caches\modules-2\files-2.1\net.fabricmc.fabric-api\fabric-api\0.116.15+1.21.1'
	if (-not (Test-Path -LiteralPath $wrapperDist) -or -not (Test-Path -LiteralPath $fabricApi)) {
		throw 'Required Gradle wrapper or Fabric 1.21.1 API cache is missing; refusing to pretend an offline build succeeded'
	}
	if (Test-Path -LiteralPath (Join-Path $bundledJava 'bin\java.exe')) {
		$env:JAVA_HOME = (Resolve-Path $bundledJava).Path
		$env:Path = "$env:JAVA_HOME\bin;$env:Path"
	}
	Push-Location $RepositoryRoot
	try {
		& .\gradlew.bat 'test' `
			'--tests' 'dev.copperbench.release.OfflineCachedBuildTest' `
			'-Dcopperbench.stage8.offlineBuild=true' `
			'--no-daemon'
		if ($LASTEXITCODE -ne 0) {
			throw "Offline cached build test failed with exit code $LASTEXITCODE"
		}
		$resultXml = Join-Path $RepositoryRoot 'build\test-results\test\TEST-dev.copperbench.release.OfflineCachedBuildTest.xml'
		if (-not (Test-Path -LiteralPath $resultXml)) {
			throw 'Offline cached build test did not produce JUnit results'
		}
		[xml]$junit = Get-Content -LiteralPath $resultXml
		$suite = $junit.testsuite
		if ([int]$suite.skipped -gt 0 -or [int]$suite.tests -lt 2 -or [int]$suite.failures -gt 0) {
			throw "Offline cached build tests did not run to completion (tests=$($suite.tests) skipped=$($suite.skipped) failures=$($suite.failures))"
		}
	}
	finally {
		Pop-Location
	}
	$result.passed = $true
}
catch {
	$result.error = $_.Exception.Message
	throw
}
finally {
	$env:JAVA_HOME = $previousJavaHome
	$result.completedAt = (Get-Date).ToString('o')
	$result.durationSeconds = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
	$evidencePath = Join-Path $evidenceDir 'offline-cached-build.json'
	($result | ConvertTo-Json -Depth 5) | Set-Content -LiteralPath $evidencePath -Encoding utf8
	Write-Output ("evidence=" + $evidencePath)
}

Write-Output 'Offline cached Fabric/NeoForge 1.21.1 builds passed'
