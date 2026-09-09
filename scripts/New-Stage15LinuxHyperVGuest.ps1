[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
	[Parameter(Mandatory = $true)]
	[string]$IsoPath,
	[Parameter(Mandatory = $true)]
	[ValidatePattern('^[0-9A-Fa-f]{64}$')]
	[string]$ExpectedIsoSha256,
	[string]$VmName = 'Copperbench-Stage15-Linux',
	[string]$VhdRoot = 'D:\Hyper-V\Stage15-Linux',
	[string]$SwitchName = 'Default Switch',
	[int]$StartupMemoryGB = 8,
	[int]$MaximumMemoryGB = 12,
	[int]$CpuCount = 4,
	[int]$VhdSizeGB = 100,
	[switch]$Start
)

$ErrorActionPreference = 'Stop'

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
	throw 'New-Stage15LinuxHyperVGuest.ps1 must run elevated.'
}
if (-not (Test-Path -LiteralPath $IsoPath -PathType Leaf)) {
	throw "Ubuntu ISO not found: $IsoPath"
}

$resolvedIso = (Resolve-Path -LiteralPath $IsoPath).Path
$isoItem = Get-Item -LiteralPath $resolvedIso
$isoLeaf = $isoItem.Name
if ($isoLeaf -notmatch '^ubuntu-24\.04(?:\.\d+)?-desktop-amd64\.iso$') {
	throw "Expected an official Ubuntu 24.04 LTS Desktop amd64 ISO filename, got: $isoLeaf"
}
if ($isoItem.Length -lt 4GB) {
	throw "Ubuntu Desktop ISO is unexpectedly small ($($isoItem.Length) bytes); refusing to create a certification VM."
}
$actualIsoSha256 = (Get-FileHash -LiteralPath $resolvedIso -Algorithm SHA256).Hash.ToLowerInvariant()
$expectedNormalized = $ExpectedIsoSha256.ToLowerInvariant()
if ($actualIsoSha256 -ne $expectedNormalized) {
	throw "Ubuntu ISO SHA-256 mismatch. Expected $expectedNormalized but got $actualIsoSha256"
}

Import-Module Hyper-V -ErrorAction Stop
$feature = Get-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V-All
if ([string]$feature.State -ne 'Enabled') {
	throw 'Microsoft-Hyper-V-All is not enabled.'
}
if (-not (Get-VMSwitch -Name $SwitchName -ErrorAction SilentlyContinue)) {
	throw "Hyper-V switch not found: $SwitchName"
}
if (Get-VM -Name $VmName -ErrorAction SilentlyContinue) {
	throw "Refusing to replace existing VM: $VmName"
}

$vhdRootDrive = Split-Path -Qualifier $VhdRoot
if ([string]::IsNullOrWhiteSpace($vhdRootDrive) -or -not (Test-Path -LiteralPath $vhdRootDrive)) {
	throw "VHD root drive is unavailable: $vhdRootDrive"
}

New-Item -ItemType Directory -Force -Path $VhdRoot | Out-Null
$vhdPath = Join-Path $VhdRoot ($VmName + '.vhdx')
if (Test-Path -LiteralPath $vhdPath) {
	throw "Refusing to replace existing VHDX: $vhdPath"
}
if ($StartupMemoryGB -lt 4 -or $MaximumMemoryGB -lt $StartupMemoryGB) {
	throw 'Stage 15 GNOME/Minecraft guest requires startup memory >= 4 GB and maximum memory >= startup memory.'
}
if ($CpuCount -lt 2 -or $VhdSizeGB -lt 64) {
	throw 'Stage 15 guest requires at least 2 CPUs and a 64 GB VHDX.'
}

if (-not $PSCmdlet.ShouldProcess($VmName, 'Create clean Ubuntu 24.04 LTS GNOME Hyper-V guest')) {
	return
}

New-VM -Name $VmName -Generation 2 -MemoryStartupBytes ($StartupMemoryGB * 1GB) `
	-NewVHDPath $vhdPath -NewVHDSizeBytes ($VhdSizeGB * 1GB) -SwitchName $SwitchName | Out-Null
Set-VMProcessor -VMName $VmName -Count $CpuCount
Set-VMMemory -VMName $VmName -DynamicMemoryEnabled $true -MinimumBytes 4GB `
	-MaximumBytes ($MaximumMemoryGB * 1GB) -StartupBytes ($StartupMemoryGB * 1GB)
Set-VM -Name $VmName -AutomaticCheckpointsEnabled $false

$dvd = Get-VMDvdDrive -VMName $VmName -ErrorAction SilentlyContinue | Select-Object -First 1
if ($dvd) {
	Set-VMDvdDrive -VMName $VmName -Path $resolvedIso
} else {
	Add-VMDvdDrive -VMName $VmName -Path $resolvedIso
	$dvd = Get-VMDvdDrive -VMName $VmName | Select-Object -First 1
}

# Ubuntu's signed shim is accepted by the Microsoft third-party UEFI CA. Do not disable Secure Boot for certification.
Set-VMFirmware -VMName $VmName -EnableSecureBoot On -SecureBootTemplate 'MicrosoftUEFICertificateAuthority'
Set-VMFirmware -VMName $VmName -FirstBootDevice $dvd

$record = [ordered]@{
	schemaVersion = '1.0'
	kind = 'stage15-linux-hyperv-vm-created'
	formalSupportClaim = $false
	vmName = $VmName
	generation = 2
	vhdPath = $vhdPath
	vhdSizeGB = $VhdSizeGB
	startupMemoryGB = $StartupMemoryGB
	maximumMemoryGB = $MaximumMemoryGB
	cpuCount = $CpuCount
	switchName = $SwitchName
	secureBoot = $true
	secureBootTemplate = 'MicrosoftUEFICertificateAuthority'
	isoPath = $resolvedIso
	isoSizeBytes = $isoItem.Length
	isoSha256 = $actualIsoSha256
	automaticCheckpointsEnabled = $false
	defaultIntegrationServicesPreserved = $true
	guestInstallationCompleted = $false
	cleanGnomeCertificationCompleted = $false
	nextStep = 'Install Ubuntu 24.04 LTS Desktop amd64 in VMConnect. Do not install Java, Gradle, Git, Blockbench, or Copperbench until the clean-guest checklist says to do so.'
}
$recordPath = Join-Path $VhdRoot 'stage15-linux-vm-created.json'
($record | ConvertTo-Json -Depth 5) | Set-Content -LiteralPath $recordPath -Encoding utf8

if ($Start) {
	Start-VM -Name $VmName
}

Write-Output ("created=" + $VmName)
Write-Output ("vhd=" + $vhdPath)
Write-Output ("isoSha256=" + $actualIsoSha256)
Write-Output ("record=" + $recordPath)
Write-Output 'VM creation is environment setup only. Complete docs/testing/stage15-linux-hyperv-checklist.md before claiming any Stage 15 clean-GNOME evidence.'
