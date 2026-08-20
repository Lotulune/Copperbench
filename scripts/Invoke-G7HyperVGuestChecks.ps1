[CmdletBinding()]
param(
	[string]$VmName = 'Copperbench-G7',
	[string]$InstallerPath = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'build\export\Copperbench 0.1.0 Windows 64bit.exe'),
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$GuestUser,
	[securestring]$GuestPassword,
	[switch]$DisconnectNetwork
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-8\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$startedAt = Get-Date

$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'hyperv-g7-guest-checks'
	vmName = $VmName
	installerPath = $InstallerPath
	passed = $false
	createsClaim = $false
	silentInstall = $false
	silentUpgrade = $false
	workspacePreservedAfterUpgrade = $false
	processStartedWhileDisconnected = $false
	silentUninstall = $false
	uninstallPreservedWorkspace = $false
	uninstallPreservedUserFolder = $false
}

try {
	$vm = Get-VM -Name $VmName -ErrorAction Stop
	$result.vmState = [string]$vm.State
	if ($vm.State -ne 'Running') {
		Start-VM -Name $VmName
		Start-Sleep -Seconds 8
		$vm = Get-VM -Name $VmName
		$result.vmState = [string]$vm.State
	}
	if (-not (Test-Path -LiteralPath $InstallerPath -PathType Leaf)) {
		throw "Installer not found: $InstallerPath"
	}

	Enable-VMIntegrationService -VMName $VmName -Name 'Guest Service Interface' -ErrorAction SilentlyContinue
	if ($DisconnectNetwork) {
		Get-VMNetworkAdapter -VMName $VmName | Disconnect-VMNetworkAdapter
		$result.networkDisconnected = $true
	}

	$guestInstaller = 'C:\Temp\Copperbench-installer.exe'
	Copy-VMFile -Name $VmName -SourcePath $InstallerPath -DestinationPath $guestInstaller -FileSource Host -CreateFullPath -Force
	$result.installerCopied = $true

	if (-not ($GuestUser -and $GuestPassword)) {
		$result.nextGuestStep = 'Install a clean Windows guest with no developer tools, then rerun with -GuestUser/-GuestPassword.'
		throw 'Guest credentials were not supplied; installer was copied but install/upgrade/uninstall/offline were not executed.'
	}

	$cred = New-Object System.Management.Automation.PSCredential ($GuestUser, $GuestPassword)
	$guestScript = {
		$ErrorActionPreference = 'Stop'
		$installer = 'C:\Temp\Copperbench-installer.exe'
		$installDir = 'C:\Copperbench-G7'
		$workspace = Join-Path $env:USERPROFILE 'Documents\copperbench-g7-test'
		$userFolder = Join-Path $env:USERPROFILE '.copperbench'
		New-Item -ItemType Directory -Force -Path $installDir, $workspace, $userFolder, 'C:\Temp' | Out-Null
		$marker = Join-Path $workspace 'workspace-marker.txt'
		$userMarker = Join-Path $userFolder 'g7-keep.txt'
		$token = 'copperbench-g7-workspace'
		Set-Content -LiteralPath $marker -Value $token -Encoding utf8
		Set-Content -LiteralPath $userMarker -Value $token -Encoding utf8

		function Invoke-Silent([string]$file, [string[]]$arguments) {
			$proc = Start-Process -FilePath $file -ArgumentList $arguments -Wait -PassThru -WindowStyle Hidden
			if ($null -eq $proc) { throw "Failed to start $file" }
			return $proc.ExitCode
		}

		$installExit = Invoke-Silent $installer @('/S', "/D=$installDir")
		if ($installExit -ne 0) { throw "Silent install exit $installExit" }
		if (-not (Test-Path -LiteralPath (Join-Path $installDir 'copperbench.exe'))) {
			throw 'copperbench.exe missing after silent install'
		}

		$upgradeExit = Invoke-Silent $installer @('/S', "/D=$installDir")
		if ($upgradeExit -ne 0) { throw "Silent upgrade exit $upgradeExit" }
		if ((Get-Content -LiteralPath $marker -Raw).Trim() -ne $token) {
			throw 'Upgrade mutated the planted workspace'
		}

		$processStarted = $false
		$app = Start-Process -FilePath (Join-Path $installDir 'copperbench.exe') -PassThru
		Start-Sleep -Seconds 10
		if ($app -and -not $app.HasExited) {
			$processStarted = $true
		}
		Get-Process -Name 'copperbench', 'javaw' -ErrorAction SilentlyContinue |
			Where-Object {
				-not $_.Path -or $_.Path -like ($installDir + '*') -or $_.Path -like '*Copperbench*'
			} |
			Stop-Process -Force -ErrorAction SilentlyContinue
		Start-Sleep -Seconds 5

		$uninstall = Join-Path $installDir 'uninstall.exe'
		if (-not (Test-Path -LiteralPath $uninstall)) {
			throw 'uninstall.exe missing before silent uninstall'
		}
		$uninstallExit = Invoke-Silent $uninstall @('/S', "_?=$installDir")
		if ($uninstallExit -ne 0) { throw "Silent uninstall exit $uninstallExit" }
		Start-Sleep -Seconds 5
		if (Test-Path -LiteralPath (Join-Path $installDir 'copperbench.exe')) {
			throw 'Uninstall left copperbench.exe'
		}
		if ((Get-Content -LiteralPath $marker -Raw).Trim() -ne $token) {
			throw 'Uninstall mutated the planted workspace'
		}
		if (-not (Test-Path -LiteralPath $userMarker)) {
			throw 'Uninstall deleted the user-folder keep marker'
		}

		[pscustomobject]@{
			installExit = $installExit
			upgradeExit = $upgradeExit
			uninstallExit = $uninstallExit
			workspacePreserved = $true
			userFolderPreserved = $true
			processStarted = $processStarted
		}
	}

	$out = Invoke-Command -VMName $VmName -Credential $cred -ScriptBlock $guestScript
	$result.silentInstall = $true
	$result.silentUpgrade = $true
	$result.workspacePreservedAfterUpgrade = [bool]$out.workspacePreserved
	$result.processStartedWhileDisconnected = [bool]$out.processStarted
	$result.silentUninstall = $true
	$result.uninstallPreservedWorkspace = [bool]$out.workspacePreserved
	$result.uninstallPreservedUserFolder = [bool]$out.userFolderPreserved
	$result.guest = $out
	$result.passed = $true
	$result.createsClaim = $true
}
catch {
	$result.error = $_.Exception.Message
	if (-not $result.nextGuestStep) {
		$result.nextGuestStep = 'Fix the guest error, then rerun Invoke-G7HyperVGuestChecks.ps1. Do not claim G7 passed.'
	}
}
finally {
	$result.completedAt = (Get-Date).ToString('o')
	$result.durationSeconds = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
	$evidencePath = Join-Path $evidenceDir 'hyperv-g7-guest-checks.json'
	($result | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $evidencePath -Encoding utf8
	Write-Output ("passed=" + $result.passed)
	Write-Output ("evidence=" + $evidencePath)
	if ($result.error) {
		Write-Output ("error=" + $result.error)
	}
}

if (-not $result.passed) {
	exit 2
}
