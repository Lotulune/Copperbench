[CmdletBinding()]
param(
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$IsoPath = '',
	[string]$ExpectedIsoSha256 = '',
	[string]$VmName = 'Copperbench-Stage15-Linux',
	[string]$VhdRoot = 'D:\Hyper-V\Stage15-Linux',
	[string]$SwitchName = 'Default Switch'
)

$ErrorActionPreference = 'Continue'

$feature = $null
$featureError = $null
try {
	$feature = Get-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V-All
} catch {
	$featureError = $_.Exception.Message
}

$hyperVModuleAvailable = $false
$moduleError = $null
$existingVm = $null
$switch = $null
try {
	Import-Module Hyper-V -ErrorAction Stop
	$hyperVModuleAvailable = [bool](Get-Command New-VM -ErrorAction SilentlyContinue)
	$existingVm = Get-VM -Name $VmName -ErrorAction SilentlyContinue
	$switch = Get-VMSwitch -Name $SwitchName -ErrorAction SilentlyContinue
} catch {
	$moduleError = $_.Exception.Message
}

$isoExists = $false
$isoNameAccepted = $false
$isoSizeBytes = $null
$isoSizeAccepted = $false
$isoActualSha256 = $null
$isoHashMatches = $false
$resolvedIsoPath = $null
if (-not [string]::IsNullOrWhiteSpace($IsoPath) -and (Test-Path -LiteralPath $IsoPath -PathType Leaf)) {
	$isoExists = $true
	$resolvedIsoPath = (Resolve-Path -LiteralPath $IsoPath).Path
	$isoItem = Get-Item -LiteralPath $resolvedIsoPath
	$isoSizeBytes = $isoItem.Length
	$isoSizeAccepted = $isoSizeBytes -ge 4GB
	$leaf = $isoItem.Name
	$isoNameAccepted = $leaf -match '^ubuntu-24\.04(?:\.\d+)?-desktop-amd64\.iso$'
	try {
		$isoActualSha256 = (Get-FileHash -LiteralPath $resolvedIsoPath -Algorithm SHA256).Hash.ToLowerInvariant()
	} catch {
		$isoHashError = $_.Exception.Message
	}
}

$expectedNormalized = $ExpectedIsoSha256.Trim().ToLowerInvariant()
$expectedHashValid = $expectedNormalized -match '^[0-9a-f]{64}$'
if ($expectedHashValid -and $isoActualSha256) {
	$isoHashMatches = $isoActualSha256 -eq $expectedNormalized
}

$vhdPath = Join-Path $VhdRoot ($VmName + '.vhdx')
$vhdRootDrive = Split-Path -Qualifier $VhdRoot
$vhdRootDriveAvailable = -not [string]::IsNullOrWhiteSpace($vhdRootDrive) -and (Test-Path -LiteralPath $vhdRootDrive)
$vhdAlreadyExists = Test-Path -LiteralPath $vhdPath
$featureEnabled = $feature -and [string]$feature.State -eq 'Enabled'
$ready = [bool]($featureEnabled -and $hyperVModuleAvailable -and $switch -and -not $existingVm -and
	-not $vhdAlreadyExists -and $vhdRootDriveAvailable -and $isoExists -and $isoNameAccepted -and
	$isoSizeAccepted -and $expectedHashValid -and $isoHashMatches)

$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'stage15-linux-hyperv-readiness'
	createsVirtualMachine = $false
	formalSupportClaim = $false
	featureState = if ($feature) { [string]$feature.State } else { 'unknown' }
	featureError = $featureError
	hyperVModuleAvailable = $hyperVModuleAvailable
	moduleError = $moduleError
	switchName = $SwitchName
	switchAvailable = [bool]$switch
	vmName = $VmName
	vmAlreadyExists = [bool]$existingVm
	vhdPath = $vhdPath
	vhdRootDriveAvailable = $vhdRootDriveAvailable
	vhdAlreadyExists = $vhdAlreadyExists
	isoPath = $resolvedIsoPath
	isoExists = $isoExists
	isoNameAccepted = $isoNameAccepted
	isoSizeBytes = $isoSizeBytes
	isoSizeAccepted = $isoSizeAccepted
	isoExpectedSha256Provided = $expectedHashValid
	isoActualSha256 = $isoActualSha256
	isoHashMatches = $isoHashMatches
	officialIsoSourceStillRequiresMaintainerVerification = $true
	readyToCreateCleanVm = $ready
	nextHostStep = if ($ready) {
		"Run scripts/New-Stage15LinuxHyperVGuest.ps1 with the same ISO and SHA-256. VM creation is environment setup, not Stage 15 certification."
	} else {
		"Provide an official ubuntu-24.04.x-desktop-amd64.iso (at least 4 GiB) and its SHA-256 from Canonical, ensure Hyper-V, '$SwitchName', and the VHD root drive are available, and keep VM/VHD name '$VmName' unused."
	}
}

$outputDir = Join-Path $RepositoryRoot 'build\stage15-linux-hyperv'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$outputPath = Join-Path $outputDir 'readiness.json'
($result | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $outputPath -Encoding utf8
Write-Output ("readyToCreateCleanVm=" + $result.readyToCreateCleanVm)
Write-Output ("isoHashMatches=" + $result.isoHashMatches)
Write-Output ("evidence=" + $outputPath)
