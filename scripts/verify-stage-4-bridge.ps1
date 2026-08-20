[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$bundledJava = (Resolve-Path (Join-Path $repositoryRoot 'jdk/jbr25_win_64')).Path
$previousJavaHome = $env:JAVA_HOME

function Assert-NativeExitCode([string]$step) {
	if ($LASTEXITCODE -ne 0) {
		throw "$step failed with exit code $LASTEXITCODE"
	}
}

try {
	$env:JAVA_HOME = $bundledJava

	Push-Location (Join-Path $repositoryRoot 'ui-core')
	try {
		& npm.cmd test
		Assert-NativeExitCode 'UI-Core contract verification'
	} finally {
		Pop-Location
	}

	Push-Location (Join-Path $repositoryRoot 'ui-shell')
	try {
		& npm.cmd run build
		Assert-NativeExitCode 'Product shell production build'
		& npx.cmd playwright test
		Assert-NativeExitCode 'Product shell Playwright regression'
	} finally {
		Pop-Location
	}

	Push-Location $repositoryRoot
	try {
		& .\gradlew.bat 'test' '-Dcopperbench.stage4.jcefSmoke=true' '-Dcopperbench.stage4.windowSmoke=true' `
			'--tests' 'dev.copperbench.core.WorkspacePersistenceFoundationTest' `
			'--tests' 'net.mcreator.workspace.WorkspacePersistenceCompatibilityTest' `
			'--tests' 'dev.copperbench.bridge.JcefCoreBridgeTransportTest' `
			'--tests' 'dev.copperbench.bridge.JcefBridgeEndpointTest' `
			'--tests' 'dev.copperbench.bridge.JcefWindowBridgeTransportTest' `
			'--tests' 'dev.copperbench.bridge.JcefLegacyPluginBridgeTransportTest' `
			'--tests' 'dev.copperbench.window.WindowChromeHitTestTest' `
			'--tests' 'dev.copperbench.window.WindowsWindowChromeControllerSmokeTest' `
			'--tests' 'net.mcreator.plugin.PluginLoaderSwingFixtureTest' `
			'--tests' 'dev.copperbench.shell.CopperbenchProductShellResourceTest' `
			'--tests' 'dev.copperbench.shell.RecoverableBrowserHostTest' `
			'--tests' 'dev.copperbench.shell.LegacyPluginWindowTest' `
			'--tests' 'dev.copperbench.shell.CopperbenchProductShellJcefSmokeTest' `
			'--no-daemon'
		Assert-NativeExitCode 'Stage 4 JCEF bridge integration verification'
	} finally {
		Pop-Location
	}
} finally {
	$env:JAVA_HOME = $previousJavaHome
}
