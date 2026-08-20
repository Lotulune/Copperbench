[CmdletBinding()]
param(
	[switch]$RunClient
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$bundledJava = Join-Path $repositoryRoot 'jdk/jdk21_win_64'
$previousJavaHome = $env:JAVA_HOME
$previousGradleExecutable = $env:COPPERBENCH_STAGE5_GRADLE_EXECUTABLE
$previousGradleUserHome = $env:COPPERBENCH_GRADLE_USER_HOME

function Assert-NativeExitCode([string]$step) {
	if ($LASTEXITCODE -ne 0) {
		throw "$step failed with exit code $LASTEXITCODE"
	}
}

try {
	if (-not (Test-Path -LiteralPath (Join-Path $bundledJava 'bin\java.exe'))) {
		throw "Bundled JDK 21 is required at $bundledJava"
	}
	$env:JAVA_HOME = (Resolve-Path $bundledJava).Path
	$env:Path = "$env:JAVA_HOME\bin;$env:Path"
	if ([string]::IsNullOrWhiteSpace($env:COPPERBENCH_GRADLE_USER_HOME)) {
		$env:COPPERBENCH_GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
	}

	Push-Location $repositoryRoot
	try {
		& .\gradlew.bat 'test' '--tests' 'dev.copperbench.generator.neoforge.*' '--tests' 'dev.copperbench.generator.LoaderRoutingWorkspaceTaskGatewayTest' '--no-daemon'
		Assert-NativeExitCode 'NeoForge generator and routing verification'

		& .\gradlew.bat 'test' '-Dcopperbench.stage5.neoforgeBuild=true' '--tests' 'dev.copperbench.generator.neoforge.NeoForge1211GoldenBuildTest' '--no-daemon'
		Assert-NativeExitCode 'NeoForge golden build verification'

		if ($RunClient) {
			& .\gradlew.bat 'test' '-Dcopperbench.stage5.neoforgeRunClient=true' '--tests' 'dev.copperbench.generator.neoforge.NeoForge1211GoldenRunClientTest' '--no-daemon'
			Assert-NativeExitCode 'NeoForge golden runClient verification'
		}
	} finally {
		Pop-Location
	}
} finally {
	$env:JAVA_HOME = $previousJavaHome
	$env:COPPERBENCH_STAGE5_GRADLE_EXECUTABLE = $previousGradleExecutable
	$env:COPPERBENCH_GRADLE_USER_HOME = $previousGradleUserHome
}
