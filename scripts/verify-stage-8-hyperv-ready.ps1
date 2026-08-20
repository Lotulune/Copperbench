[CmdletBinding()]
param(
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Continue'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-8\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

$feature = $null
try {
	$feature = Get-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V-All
} catch {
	$featureError = $_.Exception.Message
}

$getVm = $false
$vms = @()
try {
	Import-Module Hyper-V -ErrorAction Stop
	$getVm = $true
	$vms = @(Get-VM | Select-Object -ExpandProperty Name)
} catch {
	$vmError = $_.Exception.Message
}

$isoCandidates = @()
$isoRoots = @(
	'D:\ISO', 'D:\ISOs', 'C:\ISO', 'C:\ISOs',
	"$env:USERPROFILE\Downloads", "$env:USERPROFILE\Desktop",
	'D:\AICoding'
)
foreach ($root in $isoRoots) {
	if (Test-Path -LiteralPath $root) {
		$isoCandidates += @(Get-ChildItem -LiteralPath $root -Filter '*.iso' -File -ErrorAction SilentlyContinue |
			Where-Object {
				$_.Name -notmatch 'HEU|KMS|Activator' -and
				($_.Name -match 'win(dows)?[-_ ]?11' -or $_.Length -gt 3GB)
			} |
			Select-Object -ExpandProperty FullName)
	}
}

$cbsRebootPending = Test-Path 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Component Based Servicing\RebootPending'
$rebootRequired = $false
if (-not $feature -or [string]$feature.State -ne 'Enabled') {
	$rebootRequired = $true
} elseif (-not $getVm) {
	$rebootRequired = $true
}

$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'hyperv-clean-windows-g7-readiness'
	planned = $true
	createsVirtualMachine = $false
	featureName = 'Microsoft-Hyper-V-All'
	featureState = if ($feature) { [string]$feature.State } else { 'unknown' }
	featureError = $featureError
	getVmAvailable = $getVm
	existingVmNames = $vms
	windowsIsoCandidates = $isoCandidates
	readyToCreateCleanVm = ($getVm -and $isoCandidates.Count -gt 0)
	rebootRequiredForHyperV = $rebootRequired
	cbsRebootPending = $cbsRebootPending
	nextHostStep = 'Enable Microsoft-Hyper-V-All, reboot, supply a Windows 11 x64 ISO, then run New-G7HyperVGuest.ps1.'
}
if ($result.featureState -eq 'Enabled' -and -not $getVm) {
	$result.nextHostStep = 'Reboot the host so the Hyper-V module loads, then rerun this probe and supply a Windows 11 x64 ISO.'
} elseif ($getVm -and $isoCandidates.Count -eq 0) {
	$result.nextHostStep = 'Hyper-V is ready. Place an official Windows 11 x64 ISO, then run scripts/New-G7HyperVGuest.ps1 -IsoPath <windows.iso>.'
}
if ($result.readyToCreateCleanVm) {
	$result.nextHostStep = 'Run scripts/New-G7HyperVGuest.ps1 -IsoPath <windows.iso> then Invoke-G7HyperVGuestChecks.ps1.'
}

$evidencePath = Join-Path $evidenceDir 'hyperv-ready.json'
($result | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $evidencePath -Encoding utf8
Write-Output ("featureState=" + $result.featureState)
Write-Output ("readyToCreateCleanVm=" + $result.readyToCreateCleanVm)
Write-Output ("evidence=" + $evidencePath)
