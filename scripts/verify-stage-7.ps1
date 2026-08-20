[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$bundledJava = Join-Path $repositoryRoot 'jdk\jbr25_win_64'

function Assert-NativeExitCode([string]$step) {
	if ($LASTEXITCODE -ne 0) {
		throw "$step failed with exit code $LASTEXITCODE"
	}
}

$previousJavaHome = $env:JAVA_HOME
try {
	if (Test-Path -LiteralPath (Join-Path $bundledJava 'bin\java.exe')) {
		$env:JAVA_HOME = (Resolve-Path $bundledJava).Path
		$env:Path = "$env:JAVA_HOME\bin;$env:Path"
	}

	Push-Location $repositoryRoot
	try {
		& .\gradlew.bat 'test' `
			'--tests' 'dev.copperbench.tracks.*' `
			'--tests' 'dev.copperbench.migration.*' `
			'--tests' 'dev.copperbench.core.Stage67ApplicationServiceTest' `
			'--tests' 'dev.copperbench.core.Stage7G6GateTest' `
			'--tests' 'dev.copperbench.generator.fabric.Fabric261GeneratorTest' `
			'--tests' 'dev.copperbench.generator.neoforge.NeoForge261GeneratorTest' `
			'--tests' 'dev.copperbench.generator.fabric.Fabric1201GeneratorTest' `
			'--tests' 'dev.copperbench.generator.neoforge.NeoForge1201GeneratorTest' `
			'--tests' 'dev.copperbench.generator.fabric.Fabric262GeneratorTest' `
			'--tests' 'dev.copperbench.generator.neoforge.NeoForge262GeneratorTest' `
			'--tests' 'dev.copperbench.generator.fabric.Fabric1211GeneratorTest' `
			'--tests' 'dev.copperbench.generator.neoforge.NeoForge1211GeneratorTest' `
			'--tests' 'dev.copperbench.generator.neoforge.LoaderRoutingWorkspaceTaskGatewayTest' `
			'--tests' 'dev.copperbench.assets.AssetPublishBatchServiceTest' `
			'--tests' 'dev.copperbench.assets.ResourcePackClientLoadServiceTest' `
			'--tests' 'dev.copperbench.headless.HeadlessCliTest' `
			'--no-daemon'
		Assert-NativeExitCode 'Stage 7 Java domain and application service tests'

		Push-Location (Join-Path $repositoryRoot 'ui-core')
		try {
			& npm test
			Assert-NativeExitCode 'UI-Core schema tests including version-track fixture'
		} finally {
			Pop-Location
		}
	} finally {
		Pop-Location
	}
} finally {
	$env:JAVA_HOME = $previousJavaHome
}
