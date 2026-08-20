[CmdletBinding()]
param(
	[Parameter(Mandatory = $true)]
	[string]$IsoPath,
	[string]$VmName = 'Copperbench-G7',
	[string]$VhdRoot = 'D:\Hyper-V\G7',
	[int]$MemoryGB = 6,
	[int]$StartupMemoryGB = 2,
	[int]$CpuCount = 4,
	[ValidateSet('windows11')]
	[string]$GuestOs = 'windows11'
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $IsoPath -PathType Leaf)) {
	throw "Windows ISO not found: $IsoPath"
}
if ((Split-Path -Leaf $IsoPath) -match 'HEU|KMS|Activator') {
	throw 'Refusing a KMS/activator ISO. Use an official Windows 11 x64 ISO.'
}

Import-Module Hyper-V -ErrorAction Stop
if (-not (Get-Command New-VM -ErrorAction SilentlyContinue)) {
	throw 'Hyper-V PowerShell cmdlets are unavailable. Reboot the host after enabling Microsoft-Hyper-V-All.'
}

New-Item -ItemType Directory -Force -Path $VhdRoot | Out-Null
$vhdPath = Join-Path $VhdRoot ($VmName + '.vhdx')
if (Get-VM -Name $VmName -ErrorAction SilentlyContinue) {
	throw "VM already exists: $VmName"
}

New-VM -Name $VmName -Generation 2 -MemoryStartupBytes ($StartupMemoryGB * 1GB) -NewVHDPath $vhdPath -NewVHDSizeBytes 80GB |
	Out-Null
Set-VMProcessor -VMName $VmName -Count $CpuCount
Set-VMMemory -VMName $VmName -DynamicMemoryEnabled $true -MinimumBytes 1GB -MaximumBytes ($MemoryGB * 1GB) -StartupBytes ($StartupMemoryGB * 1GB)
Set-VM -Name $VmName -AutomaticCheckpointsEnabled $false
Get-VMIntegrationService -VMName $VmName | Where-Object { -not $_.Enabled } | ForEach-Object {
	Enable-VMIntegrationService $_
}

$defaultSwitch = Get-VMSwitch -Name 'Default Switch' -ErrorAction SilentlyContinue
if ($defaultSwitch) {
	Connect-VMNetworkAdapter -VMName $VmName -Name 'Network Adapter' -SwitchName 'Default Switch'
}

$dvd = Get-VMDvdDrive -VMName $VmName -ErrorAction SilentlyContinue
if ($dvd) {
	Set-VMDvdDrive -VMName $VmName -Path $IsoPath
} else {
	Add-VMDvdDrive -VMName $VmName -Path $IsoPath
	$dvd = Get-VMDvdDrive -VMName $VmName
}

if ($GuestOs -eq 'windows11') {
	Set-VMFirmware -VMName $VmName -EnableSecureBoot On -SecureBootTemplate 'MicrosoftWindows'
	$security = Get-VMSecurity -VMName $VmName
	if (-not $security.TpmEnabled) {
		Set-VMKeyProtector -VMName $VmName -NewLocalKeyProtector
		Enable-VMTPM -VMName $VmName
	}
} else {
	Set-VMFirmware -VMName $VmName -EnableSecureBoot Off
}
Set-VMFirmware -VMName $VmName -FirstBootDevice $dvd

Write-Output ("created=" + $VmName)
Write-Output ("guestOs=" + $GuestOs)
Write-Output ("vhd=" + $vhdPath)
Write-Output ("iso=" + $IsoPath)
Write-Output 'Install Windows in the guest with no developer tools. Then run Invoke-G7HyperVGuestChecks.ps1 -GuestUser <admin> -GuestPassword <secure>.'
