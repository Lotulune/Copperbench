[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param(
	[string]$VmName = 'Copperbench-Stage15-Linux',
	[string]$VhdRoot = 'D:\Hyper-V\Stage15-Linux',
	[string]$SeedVhdPath = '',
	[switch]$Start
)

$ErrorActionPreference = 'Stop'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
	throw 'New-Stage15LinuxAutoinstallSeed.ps1 must run elevated.'
}

Import-Module Hyper-V -ErrorAction Stop
$vm = Get-VM -Name $VmName -ErrorAction Stop
if ($vm.State -ne 'Off') {
	throw "VM must be Off before attaching the autoinstall seed: $VmName ($($vm.State))"
}

if ([string]::IsNullOrWhiteSpace($SeedVhdPath)) {
	$SeedVhdPath = Join-Path $VhdRoot 'stage15-autoinstall-cidata.vhdx'
}
$seedPath = [IO.Path]::GetFullPath($SeedVhdPath)
if (Test-Path -LiteralPath $seedPath) {
	throw "Refusing to replace existing autoinstall seed VHDX: $seedPath"
}

$userData = @'
#cloud-config
autoinstall:
  version: 1
  interactive-sections:
    - identity
  user-data: {}
  locale: en_US.UTF-8
  keyboard:
    layout: us
  refresh-installer:
    update: false
  source:
    id: ubuntu-desktop
    search_drivers: false
  drivers:
    install: false
  codecs:
    install: false
  oem:
    install: false
  ssh:
    install-server: false
    authorized-keys: []
    allow-pw: false
  storage:
    layout:
      name: direct
  late-commands:
    - |
      curtin in-target --target=/target -- sh -c 'for tool in java gradle git; do if command -v "$tool" >/dev/null 2>&1; then echo "Unexpected system tool in clean Stage 15 image: $tool" >&2; exit 42; fi; done'
'@
$metaData = @'
instance-id: copperbench-stage15-linux-install
local-hostname: copperbench-stage15-linux
'@

if (-not $PSCmdlet.ShouldProcess($seedPath, "Create CIDATA autoinstall seed for $VmName")) {
	return
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $seedPath) | Out-Null
New-VHD -Path $seedPath -Dynamic -SizeBytes 64MB | Out-Null
$mounted = $false
try {
	$disk = Mount-VHD -Path $seedPath -PassThru | Get-Disk
	$mounted = $true
	$disk = Initialize-Disk -Number $disk.Number -PartitionStyle MBR -PassThru
	$partition = New-Partition -DiskNumber $disk.Number -UseMaximumSize -AssignDriveLetter
	Format-Volume -Partition $partition -FileSystem FAT -NewFileSystemLabel 'CIDATA' -Confirm:$false | Out-Null
	$root = "$($partition.DriveLetter):\"
	Set-Content -LiteralPath (Join-Path $root 'user-data') -Value $userData -Encoding utf8NoBOM -NoNewline
	Set-Content -LiteralPath (Join-Path $root 'meta-data') -Value $metaData -Encoding utf8NoBOM -NoNewline
} finally {
	if ($mounted) {
		Dismount-VHD -Path $seedPath -ErrorAction SilentlyContinue
	}
}

Add-VMHardDiskDrive -VMName $VmName -Path $seedPath | Out-Null
$userDataSha = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData(
	[Text.Encoding]::UTF8.GetBytes($userData))).ToLowerInvariant()
$record = [ordered]@{
	schemaVersion = '1.0'
	kind = 'stage15-linux-autoinstall-seed-created'
	formalSupportClaim = $false
	vmName = $VmName
	seedVhdPath = $seedPath
	fileSystemLabel = 'CIDATA'
	userDataSha256 = $userDataSha
	identityInteractive = $true
	diskWriteConfirmationBypassed = $false
	sshInstalled = $false
	extraDriversInstalled = $false
	codecsInstalled = $false
	oemPackagesInstalled = $false
	sourceId = 'ubuntu-desktop'
	forbiddenSystemToolsRemoved = $false
	nextStep = 'Start the VM, complete the interactive identity section and explicitly confirm installation. If the late clean-tooling assertion fails, record the image cause; do not remove packages to manufacture a pass.'
}
$recordPath = Join-Path (Split-Path -Parent $seedPath) 'stage15-autoinstall-seed-created.json'
($record | ConvertTo-Json -Depth 5) | Set-Content -LiteralPath $recordPath -Encoding utf8

if ($Start) {
	Start-VM -Name $VmName
}

Write-Output ("seed=" + $seedPath)
Write-Output ("userDataSha256=" + $userDataSha)
Write-Output ("record=" + $recordPath)
Write-Output 'Identity and disk-write approval remain interactive. Autoinstall seed creation is not Stage 15 certification.'
