[CmdletBinding()]
param(
	[string]$IsoPath = 'C:\Users\Administrator\Downloads\Win11_25H2_Chinese_Simplified_x64_v2.iso',
	[string]$VmName = 'Copperbench-G7',
	[string]$VhdPath = 'D:\Hyper-V\G7\Copperbench-G7.vhdx',
	[string]$UnattendPath = 'D:\Hyper-V\G7\unattend-iso\autounattend.xml',
	[int]$ImageIndex = 4
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

$vm = Get-VM -Name $VmName
if ($vm.State -ne 'Off') {
	Stop-VM -Name $VmName -TurnOff -Force
	Start-Sleep -Seconds 2
}

Get-VMDvdDrive -VMName $VmName | ForEach-Object {
	Set-VMDvdDrive -VMName $VmName -ControllerNumber $_.ControllerNumber -ControllerLocation $_.ControllerLocation -Path $null
}

if (Get-DiskImage -ImagePath $IsoPath -ErrorAction SilentlyContinue | Where-Object Attached) {
	Dismount-DiskImage -ImagePath $IsoPath
}
$mount = Mount-DiskImage -ImagePath $IsoPath -PassThru
$isoLetter = ($mount | Get-Volume).DriveLetter
$wim = Join-Path ($isoLetter + ':\') 'sources\install.wim'
if (-not (Test-Path -LiteralPath $wim)) {
	$wim = Join-Path ($isoLetter + ':\') 'sources\install.esd'
}
if (-not (Test-Path -LiteralPath $wim)) {
	throw "install.wim/esd not found in $IsoPath"
}
Write-Output ("wim=" + $wim)

$vhd = Mount-VHD -Path $VhdPath -Passthru
try {
	$disk = $vhd | Get-Disk
	if ($disk.PartitionStyle -eq 'RAW') {
		Initialize-Disk -Number $disk.Number -PartitionStyle GPT
	} else {
		$disk | Get-Partition | ForEach-Object {
			if ($_.DriveLetter) {
				Remove-PartitionAccessPath -DiskNumber $disk.Number -PartitionNumber $_.PartitionNumber -AccessPath ($_.DriveLetter + ':') -ErrorAction SilentlyContinue
			}
		}
		Get-Partition -DiskNumber $disk.Number | Remove-Partition -Confirm:$false
		Clear-Disk -Number $disk.Number -RemoveData -Confirm:$false
		Initialize-Disk -Number $disk.Number -PartitionStyle GPT -ErrorAction SilentlyContinue
	}

	$efi = New-Partition -DiskNumber $disk.Number -Size 260MB -GptType '{c12a7328-f81f-11d2-ba4b-00a0c93ec93b}'
	New-Partition -DiskNumber $disk.Number -Size 16MB -GptType '{e3c9e316-0b5c-4db8-817d-f92df0038493}' | Out-Null
	$win = New-Partition -DiskNumber $disk.Number -UseMaximumSize -GptType '{ebd0a0a2-b9e5-4433-87c0-68b6b72699c7}'
	Format-Volume -Partition $efi -FileSystem FAT32 -NewFileSystemLabel System -Confirm:$false | Out-Null
	Format-Volume -Partition $win -FileSystem NTFS -NewFileSystemLabel Windows -Confirm:$false | Out-Null

	$used = Get-Volume | Where-Object { $_.DriveLetter } | ForEach-Object { $_.DriveLetter }
	$efiLetter = [char[]](83..90 + 71..82) | Where-Object { $_ -notin $used } | Select-Object -First 1
	$used2 = @($used + $efiLetter)
	$winLetter = [char[]](83..90 + 71..82) | Where-Object { $_ -notin $used2 } | Select-Object -First 1
	Set-Partition -DiskNumber $disk.Number -PartitionNumber $efi.PartitionNumber -NewDriveLetter $efiLetter
	Set-Partition -DiskNumber $disk.Number -PartitionNumber $win.PartitionNumber -NewDriveLetter $winLetter
	$winRoot = $winLetter + ':\'
	$efiRoot = $efiLetter + ':\'
	Write-Output ("windows=" + $winRoot + " efi=" + $efiRoot)

	$dism = Join-Path $env:SystemRoot 'System32\dism.exe'
	& $dism /Apply-Image /ImageFile:$wim /Index:$ImageIndex /ApplyDir:$winRoot
	if ($LASTEXITCODE -ne 0) {
		throw "DISM apply failed with $LASTEXITCODE"
	}

	$bcdboot = Join-Path $env:SystemRoot 'System32\bcdboot.exe'
	& $bcdboot ($winRoot + 'Windows') /s $efiRoot.TrimEnd('\') /f UEFI
	if ($LASTEXITCODE -ne 0) {
		throw "bcdboot failed with $LASTEXITCODE"
	}

	$panther = Join-Path $winRoot 'Windows\Panther'
	New-Item -ItemType Directory -Force -Path $panther | Out-Null
	Copy-Item -LiteralPath $UnattendPath -Destination (Join-Path $panther 'unattend.xml') -Force
	Copy-Item -LiteralPath $UnattendPath -Destination (Join-Path $winRoot 'autounattend.xml') -Force

	$offlineSoft = Join-Path $winRoot 'Windows\System32\config\SOFTWARE'
	reg.exe load HKLM\G7SYS $offlineSoft | Out-Null
	try {
		reg.exe add 'HKLM\G7SYS\Microsoft\Windows\CurrentVersion\OOBE' /v BypassNRO /t REG_DWORD /d 1 /f | Out-Null
	} finally {
		reg.exe unload HKLM\G7SYS | Out-Null
	}
	Write-Output 'image-applied'
} finally {
	Dismount-VHD -Path $VhdPath -ErrorAction SilentlyContinue
	Dismount-DiskImage -ImagePath $IsoPath -ErrorAction SilentlyContinue
}

$hdd = Get-VMHardDiskDrive -VMName $VmName
Set-VMFirmware -VMName $VmName -FirstBootDevice $hdd
Write-Output 'firmware-hdd'
