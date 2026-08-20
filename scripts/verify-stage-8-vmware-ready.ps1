[CmdletBinding()]
param(
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-8\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

$candidates = @(
	'C:\Program Files (x86)\VMware\VMware Workstation\vmrun.exe',
	'C:\Program Files\VMware\VMware Workstation\vmrun.exe',
	'C:\Program Files (x86)\VMware\VMware Player\vmrun.exe',
	'C:\Program Files\VMware\VMware Player\vmrun.exe',
	'C:\Program Files (x86)\VMware\VMware Workstation\vmware.exe',
	'C:\Program Files\VMware\VMware Workstation\vmware.exe'
)
$found = @($candidates | Where-Object { Test-Path -LiteralPath $_ })
$vmrun = Get-Command vmrun -ErrorAction SilentlyContinue

$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'vmware-clean-windows11-readiness'
	planned = $true
	createsVirtualMachine = $false
	hypervisor = 'VMware'
	vmrunOnPath = [bool]$vmrun
	foundInstallPaths = $found
	readyToCreateCleanVm = ($found.Count -gt 0 -or [bool]$vmrun)
	nextHostStep = 'Finish installing VMware Workstation/Player, then create a stock Windows 11 VM with no developer tools and only the Copperbench installer.'
}
if ($result.readyToCreateCleanVm) {
	$result.nextHostStep = 'Create a stock Windows 11 VM in VMware, install only the Copperbench package, and rerun the install rehearsal inside the guest.'
}

$evidencePath = Join-Path $evidenceDir 'vmware-ready.json'
($result | ConvertTo-Json -Depth 5) | Set-Content -LiteralPath $evidencePath -Encoding utf8
Write-Output ("readyToCreateCleanVm=" + $result.readyToCreateCleanVm)
Write-Output ("evidence=" + $evidencePath)
