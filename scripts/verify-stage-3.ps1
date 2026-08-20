[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$bundledJava = (Resolve-Path (Join-Path $repositoryRoot 'jdk/jbr25_win_64')).Path
$previousJavaHome = $env:JAVA_HOME

try {
	$env:JAVA_HOME = $bundledJava

	# Re-run the stage 2 security, MCP, headless, schema and UI baseline first.
	& (Join-Path $repositoryRoot 'scripts/verify-stage-2.ps1')

	Push-Location $repositoryRoot
	try {
		& .\gradlew.bat 'test' '-Dcopperbench.stage3.fabricBuild=true' '--tests' `
			'dev.copperbench.generator.fabric.Fabric1211GoldenBuildTest' '--no-daemon'
		if ($LASTEXITCODE -ne 0) {
			throw "Stage 3 Fabric build gate failed with exit code $LASTEXITCODE"
		}

		& .\gradlew.bat 'test' '-Dcopperbench.stage3.fabricRunClient=true' '--tests' `
			'dev.copperbench.generator.fabric.Fabric1211GoldenRunClientTest' '--no-daemon'
		if ($LASTEXITCODE -ne 0) {
			throw "Stage 3 Fabric client smoke gate failed with exit code $LASTEXITCODE"
		}
	} finally {
		Pop-Location
	}
} finally {
	$env:JAVA_HOME = $previousJavaHome
}

