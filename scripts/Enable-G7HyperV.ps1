[CmdletBinding()]
param(
	[switch]$Restart
)

$ErrorActionPreference = 'Stop'
$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
	throw 'Enable-G7HyperV.ps1 must run elevated.'
}

$before = Get-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V-All
Write-Output ("before=" + $before.State)
if ($before.State -ne 'Enabled') {
	Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V-All -All -NoRestart | Out-Null
}
$after = Get-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V-All
Write-Output ("after=" + $after.State)
$moduleOk = $false
try {
	Import-Module Hyper-V -ErrorAction Stop
	$moduleOk = [bool](Get-Command Get-VM -ErrorAction SilentlyContinue)
} catch {
	$moduleOk = $false
}
Write-Output ("hyperVModuleAvailable=" + $moduleOk)
if ($after.State -ne 'Enabled' -or -not $moduleOk) {
	Write-Output 'Hyper-V is not usable until the host reboots.'
	if ($Restart) {
		Restart-Computer -Force
	}
} else {
	Write-Output 'Hyper-V is Enabled and the Hyper-V module is available.'
}
