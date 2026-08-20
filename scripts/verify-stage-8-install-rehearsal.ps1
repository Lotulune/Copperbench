[CmdletBinding()]
param(
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[switch]$AllowExistingInstall
)

$ErrorActionPreference = 'Stop'
$uninstallKeys = @(
	'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\Copperbench',
	'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\Copperbench'
)

function Get-UninstallProperty {
	foreach ($key in $uninstallKeys) {
		if (Test-Path -LiteralPath $key) {
			return Get-ItemProperty -LiteralPath $key
		}
	}
	return $null
}

function Assert-NoRunningCopperbench {
	$running = Get-Process -Name 'copperbench', 'javaw' -ErrorAction SilentlyContinue |
		Where-Object { $_.Path -and $_.Path -like '*Copperbench*' }
	if ($running) {
		throw 'Copperbench appears to be running; refusing the install rehearsal'
	}
}

function Invoke-Native([string]$file, [string[]]$arguments, [string]$workDir, [int]$timeoutSeconds) {
	$proc = Start-Process -FilePath $file -ArgumentList $arguments -WorkingDirectory $workDir `
		-PassThru -WindowStyle Hidden
	if ($null -eq $proc) {
		throw "Failed to start $file"
	}
	if (-not $proc.WaitForExit($timeoutSeconds * 1000)) {
		Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
		throw "$file timed out after $timeoutSeconds seconds"
	}
	return $proc.ExitCode
}

Assert-NoRunningCopperbench

$exportDir = Join-Path $RepositoryRoot 'build\export'
$installer = Join-Path $exportDir 'Copperbench 0.1.0 Windows 64bit.exe'
if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) {
	throw "Installer not found: $installer"
}

$rehearsalRoot = Join-Path $RepositoryRoot 'build\g7-rehearsal'

$existing = Get-UninstallProperty
if ($existing) {
	$existingUninstall = [string]$existing.UninstallString.Trim('"')
	$normalizedExisting = $existingUninstall.Replace('/', '\')
	$normalizedRehearsal = $rehearsalRoot.Replace('/', '\')
	if ($normalizedExisting.StartsWith($normalizedRehearsal, [StringComparison]::OrdinalIgnoreCase)) {
		Write-Output 'Removing leftover rehearsal installation'
		$existingDir = Split-Path $existingUninstall
		Invoke-Native $existingUninstall @('/S', "_?=$existingDir") $existingDir 600 | Out-Null
		Start-Sleep -Seconds 2
	}
	elseif (-not $AllowExistingInstall) {
		throw "Copperbench is already registered at $($existing.UninstallString). Refusing to run UninstallPrevious against a real install."
	}
}
$installDir = Join-Path $rehearsalRoot 'install'
$workspaceDir = Join-Path $rehearsalRoot 'workspace'
$userFolder = Join-Path $env:USERPROFILE '.copperbench'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-8\$stamp"
New-Item -ItemType Directory -Force -Path $workspaceDir, $evidenceDir | Out-Null
if (Test-Path -LiteralPath $installDir) {
	Remove-Item -LiteralPath $installDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $installDir | Out-Null

$workspaceFile = Join-Path $workspaceDir 'workspace.mcreator'
$workspaceToken = [guid]::NewGuid().ToString('N')
Set-Content -LiteralPath $workspaceFile -Value "{`"g7`":`"$workspaceToken`"}" -Encoding utf8
$workspaceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $workspaceFile).Hash

New-Item -ItemType Directory -Force -Path $userFolder | Out-Null
$userNamesBefore = @(Get-ChildItem -LiteralPath $userFolder -Force | ForEach-Object Name | Sort-Object)
$marker = Join-Path $userFolder 'g7-rehearsal-keep.txt'
$markerToken = [guid]::NewGuid().ToString('N')
Set-Content -LiteralPath $marker -Value $markerToken -Encoding utf8

$startedAt = Get-Date
$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'windows11-silent-install-upgrade-uninstall'
	machine = $env:COMPUTERNAME
	os = [Environment]::OSVersion.VersionString
	admin = $true
	installer = $installer
	installDir = $installDir
	workspaceFile = $workspaceFile
	workspaceHashBefore = $workspaceHash
	passed = $false
}
$failed = $null
try {
	$installExit = Invoke-Native $installer @('/S', "/D=$installDir") $exportDir 600
	if ($installExit -ne 0) { throw "Silent install exit $installExit" }
	foreach ($relative in @('copperbench.exe', 'LICENSE.txt', 'jdk\bin\java.exe', 'jdk\bin\jcef.dll', 'lib\copperbench.jar')) {
		$path = Join-Path $installDir $relative
		if (-not (Test-Path -LiteralPath $path)) { throw "Installed payload missing $relative" }
	}
	if (-not (Get-UninstallProperty)) { throw 'Uninstall registry key was not written' }

	$upgradeExit = Invoke-Native $installer @('/S', "/D=$installDir") $exportDir 600
	if ($upgradeExit -ne 0) { throw "Silent upgrade exit $upgradeExit" }
	if (-not (Test-Path -LiteralPath (Join-Path $installDir 'copperbench.exe'))) {
		throw 'Upgrade removed copperbench.exe'
	}

	$afterUpgradeHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $workspaceFile).Hash
	if ($afterUpgradeHash -ne $workspaceHash) { throw 'Upgrade mutated the planted workspace' }
	if ((Get-Content -LiteralPath $marker -Raw).Trim() -ne $markerToken) {
		throw 'Upgrade mutated the user-folder keep marker'
	}

	$uninstall = (Get-UninstallProperty).UninstallString.Trim('"')
	$uninstallExit = Invoke-Native $uninstall @('/S', "_?=$installDir") (Split-Path $uninstall) 600
	if ($uninstallExit -ne 0) { throw "Silent uninstall exit $uninstallExit" }

	Start-Sleep -Seconds 2
	if (Test-Path -LiteralPath (Join-Path $installDir 'copperbench.exe')) {
		throw 'Uninstall left copperbench.exe behind'
	}
	if (Get-UninstallProperty) { throw 'Uninstall registry key still present' }

	$afterHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $workspaceFile).Hash
	if ($afterHash -ne $workspaceHash) { throw 'Uninstall mutated the planted workspace' }
	if (-not (Test-Path -LiteralPath $marker)) { throw 'Uninstall deleted the user-folder keep marker' }
	if ((Get-Content -LiteralPath $marker -Raw).Trim() -ne $markerToken) {
		throw 'Uninstall mutated the user-folder keep marker'
	}

	$userNamesAfter = @(Get-ChildItem -LiteralPath $userFolder -Force | ForEach-Object Name | Sort-Object)
	$missingUser = @($userNamesBefore | Where-Object { $_ -notin $userNamesAfter })
	if ($missingUser.Count -gt 0) {
		throw ("Uninstall removed pre-existing user-folder entries: " + ($missingUser -join ', '))
	}

	$result.workspaceHashAfter = $afterHash
	$result.upgradePreservedWorkspace = $true
	$result.uninstallPreservedWorkspace = $true
	$result.uninstallPreservedUserFolder = $true
	$result.passed = $true
}
catch {
	$failed = $_.Exception.Message
	$result.error = $failed
	try {
		$leftover = Get-UninstallProperty
		if ($leftover -and $leftover.UninstallString) {
			$leftoverUninstall = [string]$leftover.UninstallString.Trim('"')
			if (Test-Path -LiteralPath $leftoverUninstall) {
				Invoke-Native $leftoverUninstall @('/S', "_?=$installDir") (Split-Path $leftoverUninstall) 600 | Out-Null
			}
		}
	}
	catch {
		$result.cleanupError = $_.Exception.Message
	}
	throw $failed
}
finally {
	if (Test-Path -LiteralPath $marker) {
		Remove-Item -LiteralPath $marker -Force
	}
	$result.completedAt = (Get-Date).ToString('o')
	$result.durationSeconds = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
	$evidencePath = Join-Path $evidenceDir 'windows11-install-rehearsal.json'
	($result | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $evidencePath -Encoding utf8
	$result.evidencePath = $evidencePath
}

if (-not $result.passed) {
	throw "Windows 11 install rehearsal failed: $failed"
}

Write-Output "Windows 11 silent install/upgrade/uninstall rehearsal passed"
Write-Output ("evidence=" + $result.evidencePath)
