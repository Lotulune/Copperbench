[CmdletBinding()]
param(
	[string]$IsoPath = 'C:\Users\Administrator\Downloads\Win11_25H2_Chinese_Simplified_x64_v2.iso',
	[string]$VmName = 'Copperbench-G7',
	[string]$VhdRoot = 'D:\Hyper-V\G7',
	[string]$GuestUser = 'g7admin',
	[int]$ImageIndex = 4,
	[int]$WaitMinutes = 90
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

function New-IsoFromFolder {
	param(
		[Parameter(Mandatory = $true)][string]$SourceFolder,
		[Parameter(Mandatory = $true)][string]$IsoPath,
		[string]$VolumeName = 'UNATTEND'
	)
	Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;

public static class G7IsoWriter {
	public static void WriteIStreamToFile(object stream, string path) {
		IStream comStream = (IStream)stream;
		using (FileStream fs = new FileStream(path, FileMode.Create, FileAccess.Write)) {
			byte[] buffer = new byte[8192];
			IntPtr pcbRead = Marshal.AllocHGlobal(sizeof(int));
			try {
				while (true) {
					comStream.Read(buffer, buffer.Length, pcbRead);
					int read = Marshal.ReadInt32(pcbRead);
					if (read <= 0) break;
					fs.Write(buffer, 0, read);
				}
			} finally {
				Marshal.FreeHGlobal(pcbRead);
			}
		}
	}
}
'@
	if (Test-Path -LiteralPath $IsoPath) {
		Remove-Item -LiteralPath $IsoPath -Force
	}
	$fsi = New-Object -ComObject IMAPI2FS.MsftFileSystemImage
	$fsi.FileSystemsToCreate = 3
	$fsi.VolumeName = $VolumeName
	$fsi.Root.AddTree((Resolve-Path $SourceFolder).Path, $false)
	$result = $fsi.CreateResultImage()
	[G7IsoWriter]::WriteIStreamToFile($result.ImageStream, $IsoPath)
}

New-Item -ItemType Directory -Force -Path $VhdRoot | Out-Null
$stage = Join-Path $VhdRoot 'unattend-iso'
if (Test-Path -LiteralPath $stage) {
	Remove-Item -LiteralPath $stage -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $stage | Out-Null

$passwordFile = Join-Path $VhdRoot 'g7admin.password.txt'
if (Test-Path -LiteralPath $passwordFile) {
	$plainPassword = (Get-Content -LiteralPath $passwordFile -Raw).Trim()
} else {
	$plainPassword = 'G7-' + [guid]::NewGuid().ToString('N').Substring(0, 10) + '-Aa1'
	Set-Content -LiteralPath $passwordFile -Value $plainPassword -Encoding ascii
}

$xml = @"
<?xml version="1.0" encoding="utf-8"?>
<unattend xmlns="urn:schemas-microsoft-com:unattend" xmlns:wcm="http://schemas.microsoft.com/WMIConfig/2002/State">
	<settings pass="windowsPE">
		<component name="Microsoft-Windows-International-Core-WinPE" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS">
			<SetupUILanguage><UILanguage>zh-CN</UILanguage></SetupUILanguage>
			<InputLocale>0804:00000804</InputLocale>
			<SystemLocale>zh-CN</SystemLocale>
			<UILanguage>zh-CN</UILanguage>
			<UserLocale>zh-CN</UserLocale>
		</component>
		<component name="Microsoft-Windows-Setup" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS">
			<DiskConfiguration>
				<Disk wcm:action="add">
					<DiskID>0</DiskID>
					<WillWipeDisk>true</WillWipeDisk>
					<CreatePartitions>
						<CreatePartition wcm:action="add"><Order>1</Order><Type>EFI</Type><Size>100</Size></CreatePartition>
						<CreatePartition wcm:action="add"><Order>2</Order><Type>MSR</Type><Size>16</Size></CreatePartition>
						<CreatePartition wcm:action="add"><Order>3</Order><Type>Primary</Type><Extend>true</Extend></CreatePartition>
					</CreatePartitions>
					<ModifyPartitions>
						<ModifyPartition wcm:action="add"><Order>1</Order><PartitionID>1</PartitionID><Label>System</Label><Format>FAT32</Format></ModifyPartition>
						<ModifyPartition wcm:action="add"><Order>2</Order><PartitionID>2</PartitionID></ModifyPartition>
						<ModifyPartition wcm:action="add"><Order>3</Order><PartitionID>3</PartitionID><Label>Windows</Label><Letter>C</Letter><Format>NTFS</Format></ModifyPartition>
					</ModifyPartitions>
				</Disk>
			</DiskConfiguration>
			<ImageInstall>
				<OSImage>
					<InstallFrom>
						<MetaData wcm:action="add"><Key>/IMAGE/INDEX</Key><Value>$ImageIndex</Value></MetaData>
					</InstallFrom>
					<InstallTo><DiskID>0</DiskID><PartitionID>3</PartitionID></InstallTo>
				</OSImage>
			</ImageInstall>
			<UserData>
				<AcceptEula>true</AcceptEula>
				<ProductKey><Key>VK7JG-NPHTM-C97JM-9MPGT-3V66T</Key></ProductKey>
			</UserData>
		</component>
	</settings>
	<settings pass="specialize">
		<component name="Microsoft-Windows-Shell-Setup" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS">
			<ComputerName>COPPERBENCH-G7</ComputerName>
			<TimeZone>China Standard Time</TimeZone>
		</component>
		<component name="Microsoft-Windows-Deployment" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS">
			<RunSynchronous>
				<RunSynchronousCommand wcm:action="add">
					<Order>1</Order>
					<Path>reg add HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\OOBE /v BypassNRO /t REG_DWORD /d 1 /f</Path>
				</RunSynchronousCommand>
			</RunSynchronous>
		</component>
	</settings>
	<settings pass="oobeSystem">
		<component name="Microsoft-Windows-International-Core" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS">
			<InputLocale>0804:00000804</InputLocale>
			<SystemLocale>zh-CN</SystemLocale>
			<UILanguage>zh-CN</UILanguage>
			<UserLocale>zh-CN</UserLocale>
		</component>
		<component name="Microsoft-Windows-Shell-Setup" processorArchitecture="amd64" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS">
			<OOBE>
				<HideEULAPage>true</HideEULAPage>
				<HideOEMRegistrationScreen>true</HideOEMRegistrationScreen>
				<HideOnlineAccountScreens>true</HideOnlineAccountScreens>
				<HideWirelessSetupInOOBE>true</HideWirelessSetupInOOBE>
				<ProtectYourPC>3</ProtectYourPC>
			</OOBE>
			<UserAccounts>
				<LocalAccounts>
					<LocalAccount wcm:action="add">
						<Name>$GuestUser</Name>
						<Group>Administrators</Group>
						<DisplayName>G7 Admin</DisplayName>
						<Password><Value>$plainPassword</Value><PlainText>true</PlainText></Password>
					</LocalAccount>
				</LocalAccounts>
			</UserAccounts>
			<AutoLogon>
				<Enabled>true</Enabled>
				<Username>$GuestUser</Username>
				<Password><Value>$plainPassword</Value><PlainText>true</PlainText></Password>
				<LogonCount>3</LogonCount>
			</AutoLogon>
		</component>
	</settings>
</unattend>
"@
Set-Content -LiteralPath (Join-Path $stage 'autounattend.xml') -Value $xml -Encoding UTF8

$unattendIso = Join-Path $VhdRoot 'autounattend.iso'
New-IsoFromFolder -SourceFolder $stage -IsoPath $unattendIso -VolumeName 'UNATTEND'
Write-Output ("unattendIso=" + $unattendIso)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not (Get-VM -Name $VmName -ErrorAction SilentlyContinue)) {
	& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'New-G7HyperVGuest.ps1') `
		-IsoPath $IsoPath -VmName $VmName -VhdRoot $VhdRoot -GuestOs windows11
}

Set-VMMemory -VMName $VmName -DynamicMemoryEnabled $true -MinimumBytes 1GB -StartupBytes 2GB -MaximumBytes 6GB
Get-VMIntegrationService -VMName $VmName | Where-Object { -not $_.Enabled } | ForEach-Object {
	Enable-VMIntegrationService $_
}
$nic = Get-VMNetworkAdapter -VMName $VmName | Select-Object -First 1
if ($nic -and -not $nic.SwitchName) {
	$defaultSwitch = Get-VMSwitch -Name 'Default Switch' -ErrorAction SilentlyContinue
	if ($defaultSwitch) {
		Connect-VMNetworkAdapter -VMName $VmName -Name $nic.Name -SwitchName 'Default Switch'
	}
}
$security = Get-VMSecurity -VMName $VmName
if (-not $security.TpmEnabled) {
	Set-VMFirmware -VMName $VmName -EnableSecureBoot On -SecureBootTemplate 'MicrosoftWindows'
	Set-VMKeyProtector -VMName $VmName -NewLocalKeyProtector
	Enable-VMTPM -VMName $VmName
}
$dvds = @(Get-VMDvdDrive -VMName $VmName)
$hasWindowsIso = $dvds | Where-Object { $_.Path -and ($_.Path -like '*Win11*' -or $_.Path -eq $IsoPath) }
$hasUnattendIso = $dvds | Where-Object { $_.Path -and ($_.Path -like '*autounattend.iso') }
if (-not $hasWindowsIso) {
	Add-VMDvdDrive -VMName $VmName -Path $IsoPath
}
if (-not $hasUnattendIso) {
	Add-VMDvdDrive -VMName $VmName -Path $unattendIso
}
$winDvd = Get-VMDvdDrive -VMName $VmName | Where-Object { $_.Path -and ($_.Path -like '*Win11*' -or $_.Path -eq $IsoPath) } | Select-Object -First 1
if ($winDvd) {
	Set-VMFirmware -VMName $VmName -FirstBootDevice $winDvd
}
if ((Get-VM -Name $VmName).State -ne 'Running') {
	Start-VM -Name $VmName
}
Write-Output ("started=" + $VmName)
Write-Output ("guestUser=" + $GuestUser)
Write-Output ("passwordFile=" + $passwordFile)

$secure = ConvertTo-SecureString $plainPassword -AsPlainText -Force
$cred = New-Object System.Management.Automation.PSCredential ($GuestUser, $secure)
$deadline = (Get-Date).AddMinutes($WaitMinutes)
$ready = $false
while ((Get-Date) -lt $deadline) {
	$vm = Get-VM -Name $VmName
	$heartbeat = Get-VMIntegrationService -VMName $VmName | Where-Object { $_.Name -eq 'Heartbeat' }
	Write-Output ("state=" + $vm.State + " heartbeat=" + $heartbeat.PrimaryStatusDescription + " uptime=" + $vm.Uptime)
	try {
		$probe = Invoke-Command -VMName $VmName -Credential $cred -ScriptBlock { $env:COMPUTERNAME } -ErrorAction Stop
		Write-Output ("powershellDirect=" + $probe)
		$ready = $true
		break
	} catch {
		Start-Sleep -Seconds 30
	}
}
if (-not $ready) {
	throw "Windows guest did not become reachable over PowerShell Direct within $WaitMinutes minutes."
}

Get-VMDvdDrive -VMName $VmName | ForEach-Object {
	Set-VMDvdDrive -VMName $VmName -ControllerNumber $_.ControllerNumber -ControllerLocation $_.ControllerLocation -Path $null
}
$hdd = Get-VMHardDiskDrive -VMName $VmName
Set-VMFirmware -VMName $VmName -FirstBootDevice $hdd
Write-Output 'guest-ready'
