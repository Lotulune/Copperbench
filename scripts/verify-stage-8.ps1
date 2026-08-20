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
			'--tests' 'dev.copperbench.release.*' `
			'--tests' 'dev.copperbench.core.Stage8G7GateTest' `
			'--tests' 'dev.copperbench.ProductIdentityTest' `
			'--tests' 'dev.copperbench.headless.HeadlessCliTest' `
			'--no-daemon'
		Assert-NativeExitCode 'Stage 8 G7 automated Java tests'

		Push-Location (Join-Path $repositoryRoot 'ui-core')
		try {
			& npm test
			Assert-NativeExitCode 'UI-Core schema tests including release-notes fixture'
		} finally {
			Pop-Location
		}

		$required = @(
			'LICENSE.txt',
			'CHANGES-FROM-UPSTREAM.md',
			'compliance\SOURCE_DISTRIBUTION.md',
			'compliance\THIRD_PARTY_NOTICES.md',
			'platform\windows\installer\install.nsi',
			'ui-core\fixtures\v1.0\release\release-notes.json'
		)
		foreach ($relative in $required) {
			$path = Join-Path $repositoryRoot $relative
			if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
				throw "Missing required Stage 8 file: $relative"
			}
		}

		$nsis = Get-Content -Raw -LiteralPath (Join-Path $repositoryRoot 'platform\windows\installer\install.nsi')
		if ($nsis -notmatch 'StrCpy \$keepUserDataState 1') {
			throw 'NSIS uninstaller does not default to keeping user data'
		}
		if ($nsis -notmatch [regex]::Escape('$PROFILE\.copperbench')) {
			throw 'NSIS uninstaller does not target the Copperbench user folder'
		}
		if ($nsis -match [regex]::Escape('$PROFILE\.mcreator')) {
			throw 'NSIS uninstaller still deletes the upstream .mcreator user folder'
		}
		if ($nsis -notmatch 'IntCmp \$R0 22000') {
			throw 'NSIS installer does not require Windows 11 build 22000'
		}
		if ($nsis -notmatch 'Windows 10 is not supported') {
			throw 'NSIS installer does not refuse Windows 10'
		}

		$win64 = Join-Path $repositoryRoot 'build\export\win64'
		if (Test-Path -LiteralPath $win64 -PathType Container) {
			$requiredExport = @(
				'copperbench.exe',
				'LICENSE.txt',
				'jdk\bin\java.exe',
				'jdk\bin\jcef.dll',
				'lib\copperbench.jar',
				'plugins\generator-fabric-26.1.2.zip',
				'plugins\generator-1.21.1.zip'
			)
			foreach ($relative in $requiredExport) {
				$path = Join-Path $win64 $relative
				if (-not (Test-Path -LiteralPath $path)) {
					throw "Windows export is missing $relative"
				}
			}
			if (Test-Path -LiteralPath (Join-Path $win64 'mcreator.exe')) {
				throw 'Windows export contains the upstream executable name mcreator.exe'
			}
		}

		& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'verify-stage-8-hyperv-ready.ps1') -RepositoryRoot $repositoryRoot
		Assert-NativeExitCode 'Hyper-V clean-Windows G7 readiness probe'
		& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'verify-stage-8-vmware-ready.ps1') -RepositoryRoot $repositoryRoot
		Assert-NativeExitCode 'VMware clean-Windows-11 readiness probe'
		& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'verify-stage-8-signing-ready.ps1') -RepositoryRoot $repositoryRoot
		Assert-NativeExitCode 'Authenticode signing readiness probe'

		function Get-LatestStage8Evidence([string]$fileName) {
			$root = Join-Path $repositoryRoot 'evidence\stage-8'
			if (-not (Test-Path -LiteralPath $root)) {
				return $null
			}
			return Get-ChildItem -LiteralPath $root -Recurse -File -Filter $fileName |
				Sort-Object LastWriteTime -Descending |
				Select-Object -First 1
		}

		$jcefEvidence = Get-LatestStage8Evidence 'jcef-snap-dpi.json'
		if (-not $jcefEvidence) {
			& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'verify-stage-8-jcef-snap-dpi.ps1') -RepositoryRoot $repositoryRoot
			Assert-NativeExitCode 'JCEF Snap/DPI smoke'
			$jcefEvidence = Get-LatestStage8Evidence 'jcef-snap-dpi.json'
		}
		if (-not $jcefEvidence) {
			throw 'Missing evidence/stage-8/*/jcef-snap-dpi.json'
		}
		$jcef = Get-Content -LiteralPath $jcefEvidence.FullName -Raw | ConvertFrom-Json
		if (-not $jcef.passed) {
			throw "JCEF Snap/DPI evidence did not pass: $($jcefEvidence.FullName)"
		}
		if ([int]$jcef.maximizeHit -ne 9) {
			throw "JCEF Snap/DPI maximizeHit is not HTMAXBUTTON=9: $($jcefEvidence.FullName)"
		}
		if ([double]$jcef.dpiAfterChange -ne 1.5) {
			throw "JCEF Snap/DPI dpiAfterChange is not 1.5: $($jcefEvidence.FullName)"
		}
		Write-Output ("jcefSnapDpi=" + $jcefEvidence.FullName)

		$rpEvidence = Get-LatestStage8Evidence 'resource-pack-1211-client.json'
		if (-not $rpEvidence) {
			& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'verify-resource-pack-1211-client.ps1') -RepositoryRoot $repositoryRoot
			Assert-NativeExitCode 'Resource pack 1.21.1 client load'
			$rpEvidence = Get-LatestStage8Evidence 'resource-pack-1211-client.json'
		}
		if (-not $rpEvidence) {
			throw 'Missing evidence/stage-8/*/resource-pack-1211-client.json'
		}
		$rp = Get-Content -LiteralPath $rpEvidence.FullName -Raw | ConvertFrom-Json
		if (-not $rp.packLoaded) {
			throw "Resource pack client-load evidence did not set packLoaded: $($rpEvidence.FullName)"
		}
		Write-Output ("resourcePackClient=" + $rpEvidence.FullName)

		$g7Guest = Get-LatestStage8Evidence 'hyperv-g7-guest-checks.json'
		if (-not $g7Guest) {
			throw 'Missing evidence/stage-8/*/hyperv-g7-guest-checks.json'
		}
		$guest = Get-Content -LiteralPath $g7Guest.FullName -Raw | ConvertFrom-Json
		if (-not $guest.passed) {
			throw "Hyper-V G7 guest checks did not pass: $($g7Guest.FullName)"
		}
		if (-not $guest.silentInstall -or -not $guest.silentUpgrade -or -not $guest.silentUninstall) {
			throw "Hyper-V G7 guest evidence is missing install/upgrade/uninstall: $($g7Guest.FullName)"
		}
		if (-not $guest.uninstallPreservedWorkspace -or -not $guest.uninstallPreservedUserFolder) {
			throw "Hyper-V G7 guest evidence did not preserve workspace/user data: $($g7Guest.FullName)"
		}
		Write-Output ("hypervG7Guest=" + $g7Guest.FullName)

		& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'verify-stage-8-offline-build.ps1') -RepositoryRoot $repositoryRoot
		Assert-NativeExitCode 'Offline cached Fabric/NeoForge 1.21.1 dependency builds'

		$installer = Join-Path $repositoryRoot 'build\export\Copperbench 0.1.0 Windows 64bit.exe'
		if (Test-Path -LiteralPath $installer -PathType Leaf) {
			& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'verify-stage-8-install-rehearsal.ps1')
			Assert-NativeExitCode 'Windows 11 silent install/upgrade/uninstall rehearsal'
		}
	} finally {
		Pop-Location
	}
} finally {
	$env:JAVA_HOME = $previousJavaHome
}
