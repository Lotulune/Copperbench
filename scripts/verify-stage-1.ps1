[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$bundledJava = (Resolve-Path (Join-Path $repositoryRoot 'jdk/jbr25_win_64')).Path
$previousJavaHome = $env:JAVA_HOME

try {
	$env:JAVA_HOME = $bundledJava
	Push-Location $repositoryRoot
	try {
		& .\gradlew.bat --no-daemon test `
			--tests 'dev.copperbench.*' `
			--tests 'net.mcreator.workspace.WorkspacePersistenceCompatibilityTest'
		if ($LASTEXITCODE -ne 0) {
			throw "Stage 1 Java gate failed with exit code $LASTEXITCODE"
		}
	} finally {
		Pop-Location
	}

	Push-Location (Join-Path $repositoryRoot 'ui-core')
	try {
		& npm test
		if ($LASTEXITCODE -ne 0) {
			throw "UI-Core contract gate failed with exit code $LASTEXITCODE"
		}
	} finally {
		Pop-Location
	}
} finally {
	$env:JAVA_HOME = $previousJavaHome
}
