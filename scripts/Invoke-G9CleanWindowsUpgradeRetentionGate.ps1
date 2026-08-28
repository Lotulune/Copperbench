[CmdletBinding()]
param(
	[string]$VmName = 'Copperbench-G7',
	[string]$PreviousInstallerPath = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path '.tmp\stage9-g95\Copperbench.0.1.0-preview.3.Windows.64bit.exe'),
	[string]$PreviousInstallerSha256 = '4c621c330e933422fca918c3c88ba87bec15eef937e6ed51cccc128a0a61bccf',
	[string]$PreviousRelease = 'v0.1.0-preview.3',
	[string]$CurrentInstallerPath = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'build\export\Copperbench 0.1.0 Windows 64bit.exe'),
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$GuestUser = 'g7admin',
	[string]$PasswordFile = 'D:\Hyper-V\G7\g7admin.password.txt',
	[string]$InstallDir = 'C:\Copperbench-G9',
	[string]$WorkspaceFile = ''
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

if (-not (Test-Path -LiteralPath $PreviousInstallerPath -PathType Leaf)) {
	throw "Previous release installer not found: $PreviousInstallerPath"
}
if (-not (Test-Path -LiteralPath $CurrentInstallerPath -PathType Leaf)) {
	throw "Current installer not found: $CurrentInstallerPath"
}
if (-not (Test-Path -LiteralPath $PasswordFile -PathType Leaf)) {
	throw "Guest password file not found: $PasswordFile"
}

$previousHash = (Get-FileHash -LiteralPath $PreviousInstallerPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($previousHash -ne $PreviousInstallerSha256.ToLowerInvariant()) {
	throw "Previous installer SHA-256 mismatch. expected=$PreviousInstallerSha256 actual=$previousHash"
}
$currentHash = (Get-FileHash -LiteralPath $CurrentInstallerPath -Algorithm SHA256).Hash.ToLowerInvariant()

$plainPassword = (Get-Content -LiteralPath $PasswordFile -Raw).Trim()
$securePassword = ConvertTo-SecureString $plainPassword -AsPlainText -Force
$credential = [pscredential]::new($GuestUser, $securePassword)
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-9\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$resultPath = Join-Path $evidenceDir 'clean-windows11-upgrade-offline-retention.json'

$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'stage9-clean-windows11-upgrade-offline-retention'
	vmName = $VmName
	previousRelease = $PreviousRelease
	previousInstallerPath = $PreviousInstallerPath
	previousInstallerSha256 = $previousHash
	currentInstallerPath = $CurrentInstallerPath
	currentInstallerSha256 = $currentHash
	installDir = $InstallDir
	startedAt = (Get-Date).ToString('o')
	passed = $false
	previousInstallerVerified = $true
	preflightCurrentUninstall = $false
	preflightCurrentUninstallPreservedWorkspace = $false
	preflightCurrentUninstallPreservedUserData = $false
	previousReleaseInstalled = $false
	oldToCurrentUpgrade = $false
	upgradeInstalledDifferentPayload = $false
	upgradePreservedWorkspace = $false
	upgradePreservedUserData = $false
	networkDisconnected = $false
	offlineProcessStarted = $false
	offlineProcessStable = $false
	offlineWorkspaceArgumentObserved = $false
	offlineMainWindowObserved = $false
	silentUninstall = $false
	uninstallPreservedWorkspace = $false
	uninstallPreservedUserData = $false
	restoredCurrentInstall = $false
	finalRcReplayRequired = $true
	gatePromotionReady = $false
}

function New-GuestSession {
	param(
		[Parameter(Mandatory = $true)][string]$TargetVm,
		[Parameter(Mandatory = $true)][pscredential]$Credential,
		[int]$Attempts = 60
	)

	for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
		try {
			return New-PSSession -VMName $TargetVm -Credential $Credential -ErrorAction Stop
		} catch {
			Start-Sleep -Seconds 2
		}
	}

	throw "PowerShell Direct did not become available for $TargetVm."
}

function Reset-GuestSession {
	param([Parameter(Mandatory = $true)][ref]$SessionRef)

	if ($SessionRef.Value) {
		Remove-PSSession $SessionRef.Value -ErrorAction SilentlyContinue
	}
	$SessionRef.Value = New-GuestSession -TargetVm $VmName -Credential $credential
}

function Copy-InstallerIfNeeded {
	param(
		[Parameter(Mandatory = $true)][ref]$SessionRef,
		[Parameter(Mandatory = $true)][string]$HostPath,
		[Parameter(Mandatory = $true)][string]$GuestPath,
		[Parameter(Mandatory = $true)][string]$ExpectedHash
	)

	$matches = Invoke-Command -Session $SessionRef.Value -ArgumentList $GuestPath, $ExpectedHash -ScriptBlock {
		param($Path, $Hash)
		if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
			return $false
		}
		try {
			return ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -eq $Hash.ToLowerInvariant())
		} catch {
			return $false
		}
	}
	if ($matches) {
		return
	}

	Invoke-Command -Session $SessionRef.Value -ScriptBlock {
		New-Item -ItemType Directory -Force -Path 'C:\Temp' | Out-Null
	}
	Copy-Item -LiteralPath $HostPath -Destination $GuestPath -ToSession $SessionRef.Value -Force
	$guestHash = Invoke-Command -Session $SessionRef.Value -ArgumentList $GuestPath -ScriptBlock {
		param($Path)
		(Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
	}
	if ($guestHash -ne $ExpectedHash.ToLowerInvariant()) {
		throw "Guest installer hash mismatch after copy: $GuestPath"
	}
}

function Invoke-GuestSilentProcess {
	param(
		[Parameter(Mandatory = $true)][ref]$SessionRef,
		[Parameter(Mandatory = $true)][string]$File,
		[Parameter(Mandatory = $true)][string[]]$Arguments,
		[Parameter(Mandatory = $true)][string]$Label
	)

	try {
		$exitCode = Invoke-Command -Session $SessionRef.Value -ArgumentList $File, $Arguments -ScriptBlock {
			param($TargetFile, $TargetArguments)
			$process = Start-Process -FilePath $TargetFile -ArgumentList $TargetArguments -Wait -PassThru -WindowStyle Hidden
			if ($null -eq $process) {
				throw "Failed to start $TargetFile"
			}
			$process.ExitCode
		}
		if ($exitCode -ne 0) {
			throw "$Label exit code $exitCode"
		}
		return [pscustomobject]@{ exitCode = [int]$exitCode; sessionDisconnected = $false }
	} catch {
		$message = $_.Exception.Message
		if ($message -notmatch 'Hyper-V socket target process has ended|PSSession.*(broken|closed)|remote session.*ended') {
			throw
		}
		Reset-GuestSession -SessionRef $SessionRef
		return [pscustomobject]@{ exitCode = $null; sessionDisconnected = $true; disconnectMessage = $message }
	}
}

function Get-RetentionState {
	param(
		[Parameter(Mandatory = $true)][ref]$SessionRef,
		[Parameter(Mandatory = $true)][string]$TargetWorkspaceFile,
		[Parameter(Mandatory = $true)][string]$Token
	)

	Invoke-Command -Session $SessionRef.Value -ArgumentList $TargetWorkspaceFile, $Token -ScriptBlock {
		param($WorkspacePath, $ExpectedToken)
		$workspaceDir = Split-Path -Parent $WorkspacePath
		$markerName = 'g95-retention-' + $ExpectedToken + '.txt'
		$workspaceMarker = Join-Path $workspaceDir $markerName
		$userFolder = Join-Path $env:USERPROFILE '.copperbench'
		$userMarker = Join-Path $userFolder $markerName
		[pscustomobject]@{
			workspaceFilePresent = Test-Path -LiteralPath $WorkspacePath -PathType Leaf
			workspaceFileSha256 = if (Test-Path -LiteralPath $WorkspacePath -PathType Leaf) {
				(Get-FileHash -LiteralPath $WorkspacePath -Algorithm SHA256).Hash.ToLowerInvariant()
			} else { $null }
			workspaceMarkerPresent = Test-Path -LiteralPath $workspaceMarker -PathType Leaf
			workspaceMarkerMatches = if (Test-Path -LiteralPath $workspaceMarker -PathType Leaf) {
				(Get-Content -LiteralPath $workspaceMarker -Raw).Trim() -eq $ExpectedToken
			} else { $false }
			userFolderPresent = Test-Path -LiteralPath $userFolder -PathType Container
			userMarkerPresent = Test-Path -LiteralPath $userMarker -PathType Leaf
			userMarkerMatches = if (Test-Path -LiteralPath $userMarker -PathType Leaf) {
				(Get-Content -LiteralPath $userMarker -Raw).Trim() -eq $ExpectedToken
			} else { $false }
		}
	}
}

function Assert-RetentionState {
	param(
		[Parameter(Mandatory = $true)]$State,
		[Parameter(Mandatory = $true)][string]$ExpectedWorkspaceHash,
		[Parameter(Mandatory = $true)][string]$Label
	)

	if (-not $State.workspaceFilePresent -or $State.workspaceFileSha256 -ne $ExpectedWorkspaceHash) {
		throw "$Label changed or removed the retained .mcreator workspace file."
	}
	if (-not $State.workspaceMarkerPresent -or -not $State.workspaceMarkerMatches) {
		throw "$Label removed or changed the planted workspace marker."
	}
	if (-not $State.userFolderPresent -or -not $State.userMarkerPresent -or -not $State.userMarkerMatches) {
		throw "$Label removed or changed the .copperbench user-data marker."
	}
}

function Get-InstalledPayloadState {
	param([Parameter(Mandatory = $true)][ref]$SessionRef)

	Invoke-Command -Session $SessionRef.Value -ArgumentList $InstallDir -ScriptBlock {
		param($TargetInstallDir)
		$launcher = Join-Path $TargetInstallDir 'copperbench.exe'
		$jar = Join-Path $TargetInstallDir 'lib\copperbench.jar'
		$uninstall = Join-Path $TargetInstallDir 'uninstall.exe'
		[pscustomobject]@{
			launcherPresent = Test-Path -LiteralPath $launcher -PathType Leaf
			jarPresent = Test-Path -LiteralPath $jar -PathType Leaf
			jarSha256 = if (Test-Path -LiteralPath $jar -PathType Leaf) {
				(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
			} else { $null }
			uninstallPresent = Test-Path -LiteralPath $uninstall -PathType Leaf
		}
	}
}

$session = $null
$savedNetworkAdapters = @()
$networkWasDisconnectedByGate = $false
$guestPreviousInstaller = 'C:\Temp\Copperbench-preview3-installer.exe'
$guestCurrentInstaller = 'C:\Temp\Copperbench-installer.exe'
$retentionToken = 'g95-' + [guid]::NewGuid().ToString('N')
$resolvedWorkspace = $null

try {
	$vm = Get-VM -Name $VmName -ErrorAction Stop
	if ($vm.State -ne 'Running') {
		Start-VM -Name $VmName | Out-Null
		Start-Sleep -Seconds 5
	}

	$session = New-GuestSession -TargetVm $VmName -Credential $credential
	Copy-InstallerIfNeeded -SessionRef ([ref]$session) -HostPath $PreviousInstallerPath -GuestPath $guestPreviousInstaller -ExpectedHash $previousHash
	Copy-InstallerIfNeeded -SessionRef ([ref]$session) -HostPath $CurrentInstallerPath -GuestPath $guestCurrentInstaller -ExpectedHash $currentHash

	$resolvedWorkspace = Invoke-Command -Session $session -ArgumentList $WorkspaceFile -ScriptBlock {
		param($RequestedWorkspace)
		if ($RequestedWorkspace) {
			if (-not (Test-Path -LiteralPath $RequestedWorkspace -PathType Leaf)) {
				throw "Requested Stage 9 workspace does not exist: $RequestedWorkspace"
			}
			return (Resolve-Path -LiteralPath $RequestedWorkspace).Path
		}

		$root = Join-Path $env:USERPROFILE 'MCreatorWorkspaces'
		$candidates = @(
			Get-ChildItem -LiteralPath $root -Filter '*.mcreator' -Recurse -File -ErrorAction SilentlyContinue |
				Where-Object { $_.Directory.Name -like 'guigate*' -and $_.BaseName -like 'guigate*' } |
				Sort-Object LastWriteTime -Descending
		)
		if ($candidates.Count -eq 0) {
			throw 'No Stage 9 guigate* workspace exists. Run Invoke-G9CleanWindowsGuiGate.ps1 first.'
		}
		$candidates[0].FullName
	}
	$result.workspaceFile = $resolvedWorkspace

	$baseline = Invoke-Command -Session $session -ArgumentList $resolvedWorkspace, $retentionToken, $InstallDir -ScriptBlock {
		param($TargetWorkspaceFile, $Token, $TargetInstallDir)
		$workspaceDir = Split-Path -Parent $TargetWorkspaceFile
		$userFolder = Join-Path $env:USERPROFILE '.copperbench'
		New-Item -ItemType Directory -Force -Path $userFolder | Out-Null
		$markerName = 'g95-retention-' + $Token + '.txt'
		$workspaceMarker = Join-Path $workspaceDir $markerName
		$userMarker = Join-Path $userFolder $markerName
		Set-Content -LiteralPath $workspaceMarker -Value $Token -Encoding UTF8
		Set-Content -LiteralPath $userMarker -Value $Token -Encoding UTF8
		Get-Process copperbench, javaw -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
		Start-Sleep -Seconds 2
		[pscustomobject]@{
			workspaceFileSha256 = (Get-FileHash -LiteralPath $TargetWorkspaceFile -Algorithm SHA256).Hash.ToLowerInvariant()
			workspaceMarker = $workspaceMarker
			userMarker = $userMarker
			currentInstallPresent = Test-Path -LiteralPath (Join-Path $TargetInstallDir 'copperbench.exe') -PathType Leaf
			currentUninstallPresent = Test-Path -LiteralPath (Join-Path $TargetInstallDir 'uninstall.exe') -PathType Leaf
		}
	}
	$result.baseline = $baseline

	if ($baseline.currentInstallPresent) {
		if (-not $baseline.currentUninstallPresent) {
			throw 'Existing current install has no uninstall.exe; refusing destructive cleanup.'
		}
		$preflightUninstall = Join-Path $InstallDir 'uninstall.exe'
		$result.preflightCurrentUninstallResult = Invoke-GuestSilentProcess -SessionRef ([ref]$session) -File $preflightUninstall -Arguments @('/S', "_?=$InstallDir") -Label 'Preflight current uninstall'
		Reset-GuestSession -SessionRef ([ref]$session)
		$preflightPayload = Get-InstalledPayloadState -SessionRef ([ref]$session)
		if ($preflightPayload.launcherPresent) {
			throw 'Preflight current uninstall left copperbench.exe in the install directory.'
		}
		$preflightRetention = Get-RetentionState -SessionRef ([ref]$session) -TargetWorkspaceFile $resolvedWorkspace -Token $retentionToken
		Assert-RetentionState -State $preflightRetention -ExpectedWorkspaceHash $baseline.workspaceFileSha256 -Label 'Preflight current uninstall'
		$result.preflightCurrentUninstall = $true
		$result.preflightCurrentUninstallPreservedWorkspace = $true
		$result.preflightCurrentUninstallPreservedUserData = $true
		$result.preflightRetention = $preflightRetention
	} else {
		$result.preflightCurrentUninstall = $true
		$result.preflightCurrentUninstallPreservedWorkspace = $true
		$result.preflightCurrentUninstallPreservedUserData = $true
	}

	$result.previousInstallResult = Invoke-GuestSilentProcess -SessionRef ([ref]$session) -File $guestPreviousInstaller -Arguments @('/S', "/D=$InstallDir") -Label 'Previous release install'
	Reset-GuestSession -SessionRef ([ref]$session)
	$previousPayload = Get-InstalledPayloadState -SessionRef ([ref]$session)
	if (-not $previousPayload.launcherPresent -or -not $previousPayload.jarPresent -or -not $previousPayload.uninstallPresent) {
		throw 'Previous public release did not produce a complete installed payload.'
	}
	$result.previousPayload = $previousPayload
	$result.previousReleaseInstalled = $true

	$beforeUpgradeRetention = Get-RetentionState -SessionRef ([ref]$session) -TargetWorkspaceFile $resolvedWorkspace -Token $retentionToken
	Assert-RetentionState -State $beforeUpgradeRetention -ExpectedWorkspaceHash $baseline.workspaceFileSha256 -Label 'Previous release install'

	$result.upgradeResult = Invoke-GuestSilentProcess -SessionRef ([ref]$session) -File $guestCurrentInstaller -Arguments @('/S', "/D=$InstallDir") -Label 'Old-to-current upgrade'
	Reset-GuestSession -SessionRef ([ref]$session)
	$currentPayload = Get-InstalledPayloadState -SessionRef ([ref]$session)
	if (-not $currentPayload.launcherPresent -or -not $currentPayload.jarPresent -or -not $currentPayload.uninstallPresent) {
		throw 'Current installer did not produce a complete payload after old-to-current upgrade.'
	}
	$result.currentPayloadAfterUpgrade = $currentPayload
	$result.oldToCurrentUpgrade = $true
	$result.upgradeInstalledDifferentPayload = ($previousPayload.jarSha256 -and $currentPayload.jarSha256 -and
			$previousPayload.jarSha256 -ne $currentPayload.jarSha256)
	if (-not $result.upgradeInstalledDifferentPayload) {
		throw 'Old-to-current upgrade did not replace lib\copperbench.jar with a different payload.'
	}

	$afterUpgradeRetention = Get-RetentionState -SessionRef ([ref]$session) -TargetWorkspaceFile $resolvedWorkspace -Token $retentionToken
	Assert-RetentionState -State $afterUpgradeRetention -ExpectedWorkspaceHash $baseline.workspaceFileSha256 -Label 'Old-to-current upgrade'
	$result.upgradePreservedWorkspace = $true
	$result.upgradePreservedUserData = $true
	$result.afterUpgradeRetention = $afterUpgradeRetention

	$savedNetworkAdapters = @(
		Get-VMNetworkAdapter -VMName $VmName | ForEach-Object {
			[pscustomobject]@{ name = $_.Name; switchName = $_.SwitchName }
		}
	)
	if ($savedNetworkAdapters.Count -eq 0 -or -not ($savedNetworkAdapters | Where-Object { $_.switchName })) {
		throw 'VM has no connected network adapter to disconnect for the offline startup gate.'
	}
	Get-VMNetworkAdapter -VMName $VmName | Disconnect-VMNetworkAdapter
	$networkWasDisconnectedByGate = $true
	$disconnectedAdapters = @(Get-VMNetworkAdapter -VMName $VmName)
	$result.networkDisconnected = -not ($disconnectedAdapters | Where-Object { $_.SwitchName })
	if (-not $result.networkDisconnected) {
		throw 'One or more VM network adapters remained connected.'
	}

	$offline = Invoke-Command -Session $session -ArgumentList $InstallDir, $resolvedWorkspace, $GuestUser -ScriptBlock {
		param($TargetInstallDir, $TargetWorkspaceFile, $TargetUser)
		$exe = Join-Path $TargetInstallDir 'copperbench.exe'
		if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) {
			throw 'copperbench.exe missing before offline launch.'
		}

		Get-Process copperbench, javaw -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
		Start-Sleep -Seconds 2

		$guestRoot = 'C:\Temp\Copperbench-G9-OfflineGate'
		$scriptPath = Join-Path $guestRoot 'Invoke-OfflineWorkspaceProbe.ps1'
		$resultPath = Join-Path $guestRoot 'offline-workspace-probe.json'
		$taskName = 'Copperbench-G9-OfflineWorkspaceProbe'
		New-Item -ItemType Directory -Force -Path $guestRoot | Out-Null
		Remove-Item -LiteralPath $scriptPath, $resultPath -Force -ErrorAction SilentlyContinue

		$probeScript = @'
[CmdletBinding()]
param(
	[Parameter(Mandatory = $true)][string]$TargetInstallDir,
	[Parameter(Mandatory = $true)][string]$TargetWorkspaceFile
)

$ErrorActionPreference = 'Stop'
$GuestRoot = 'C:\Temp\Copperbench-G9-OfflineGate'
$ResultPath = Join-Path $GuestRoot 'offline-workspace-probe.json'
$result = [ordered]@{
	processStarted = $false
	processStable = $false
	workspaceArgumentObserved = $false
	mainWindowObserved = $false
	javaProcessId = $null
	javaCommandLine = $null
	mainWindowHandle = 0
	mainWindowTitle = $null
	interactiveSessionId = (Get-Process -Id $PID).SessionId
	observedWindows = @()
}

try {
	Add-Type -AssemblyName UIAutomationClient
	Add-Type -AssemblyName UIAutomationTypes
	$exe = Join-Path $TargetInstallDir 'copperbench.exe'
	if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) {
		throw 'copperbench.exe missing in interactive probe.'
	}

	Get-Process copperbench, javaw -ErrorAction SilentlyContinue |
		Where-Object { $_.SessionId -eq $result.interactiveSessionId } |
		Stop-Process -Force -ErrorAction SilentlyContinue
	Start-Sleep -Seconds 2
	Start-Process -FilePath $exe -ArgumentList @('-workspace', ('"{0}"' -f $TargetWorkspaceFile)) | Out-Null

	$deadline = (Get-Date).AddSeconds(180)
	$workspaceName = [IO.Path]::GetFileNameWithoutExtension($TargetWorkspaceFile)
	$matched = $null
	$window = $null
	do {
		Start-Sleep -Milliseconds 500
		$candidates = @(Get-CimInstance Win32_Process -Filter "Name='javaw.exe'" -ErrorAction SilentlyContinue |
			Where-Object { $_.CommandLine -and $_.CommandLine -like "*$TargetWorkspaceFile*" })
		if ($candidates.Count -gt 0) {
			$matched = $candidates[0]
			$result.processStarted = $true
			$result.workspaceArgumentObserved = ($matched.CommandLine -like "*$TargetWorkspaceFile*")
			$result.javaProcessId = [int]$matched.ProcessId
			$result.javaCommandLine = $matched.CommandLine

			$desktop = [System.Windows.Automation.AutomationElement]::RootElement
			$pidCondition = [System.Windows.Automation.PropertyCondition]::new(
				[System.Windows.Automation.AutomationElement]::ProcessIdProperty,
				[int]$matched.ProcessId
			)
			$elements = $desktop.FindAll([System.Windows.Automation.TreeScope]::Descendants, $pidCondition)
			$observed = @()
			foreach ($element in $elements) {
				try {
					if ($element.Current.NativeWindowHandle -ne 0 -and $element.Current.ClassName -like 'SunAwt*') {
						$observed += [pscustomobject]@{
							name = $element.Current.Name
							className = $element.Current.ClassName
							controlType = [string]$element.Current.ControlType.ProgrammaticName
							nativeWindowHandle = [int64]$element.Current.NativeWindowHandle
							isOffscreen = [bool]$element.Current.IsOffscreen
						}
					}
					if ($element.Current.ClassName -eq 'SunAwtFrame' -and
							$element.Current.Name -like "$workspaceName - Copperbench*" -and
							$element.Current.NativeWindowHandle -ne 0 -and
							-not $element.Current.IsOffscreen) {
						$window = $element
						break
					}
				} catch {
					# UIA elements can disappear while the product is opening.
				}
			}
			$result.observedWindows = @($observed)
		}
		if ($null -ne $window) {
			break
		}
	} while ((Get-Date) -lt $deadline)

	if ($null -ne $window -and $null -ne $matched) {
		$result.mainWindowObserved = $true
		$result.mainWindowHandle = [int64]$window.Current.NativeWindowHandle
		$result.mainWindowTitle = $window.Current.Name
		Start-Sleep -Seconds 10
		$result.processStable = $null -ne (Get-Process -Id $matched.ProcessId -ErrorAction SilentlyContinue)
	}
} catch {
	$result.error = $_.Exception.Message
} finally {
	$result.completedAt = (Get-Date).ToString('o')
	($result | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $ResultPath -Encoding UTF8
}
'@

		Set-Content -LiteralPath $scriptPath -Value $probeScript -Encoding UTF8
		Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
		$actionArgs = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$scriptPath`" -TargetInstallDir `"$TargetInstallDir`" -TargetWorkspaceFile `"$TargetWorkspaceFile`""
		$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $actionArgs
		$principal = New-ScheduledTaskPrincipal -UserId $TargetUser -LogonType Interactive -RunLevel Highest
		Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Force | Out-Null
		Start-ScheduledTask -TaskName $taskName

		$deadline = (Get-Date).AddMinutes(5)
		$probe = $null
		do {
			Start-Sleep -Milliseconds 500
			if (Test-Path -LiteralPath $resultPath -PathType Leaf) {
				try {
					$probe = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
					break
				} catch {
					# The interactive probe may still be flushing JSON; retry until the deadline.
				}
			}
		} while ((Get-Date) -lt $deadline)

		Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
		Remove-Item -LiteralPath $scriptPath -Force -ErrorAction SilentlyContinue
		if ($null -eq $probe) {
			throw 'Interactive offline workspace probe did not produce a result within five minutes.'
		}
		$probe
	}
	$result.offline = $offline
	$result.offlineProcessStarted = [bool]$offline.processStarted
	$result.offlineProcessStable = [bool]$offline.processStable
	$result.offlineWorkspaceArgumentObserved = [bool]$offline.workspaceArgumentObserved
	$result.offlineMainWindowObserved = [bool]$offline.mainWindowObserved
	if (-not ($result.offlineProcessStarted -and $result.offlineProcessStable -and
			$result.offlineWorkspaceArgumentObserved -and $result.offlineMainWindowObserved)) {
		throw 'Copperbench did not expose a stable workspace window while the VM NIC was disconnected.'
	}

	Invoke-Command -Session $session -ScriptBlock {
		Get-Process copperbench, javaw -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
		Start-Sleep -Seconds 3
	}
	$afterOfflineRetention = Get-RetentionState -SessionRef ([ref]$session) -TargetWorkspaceFile $resolvedWorkspace -Token $retentionToken
	Assert-RetentionState -State $afterOfflineRetention -ExpectedWorkspaceHash $baseline.workspaceFileSha256 -Label 'Offline workspace launch'
	$result.afterOfflineRetention = $afterOfflineRetention
	$preUninstallWorkspaceHash = $baseline.workspaceFileSha256

	foreach ($adapter in $savedNetworkAdapters) {
		if ($adapter.switchName) {
			Get-VMNetworkAdapter -VMName $VmName -Name $adapter.name | Connect-VMNetworkAdapter -SwitchName $adapter.switchName
		}
	}
	$networkWasDisconnectedByGate = $false
	$result.networkRestoredAfterOfflineCheck = $true

	$uninstallPath = Join-Path $InstallDir 'uninstall.exe'
	$result.uninstallResult = Invoke-GuestSilentProcess -SessionRef ([ref]$session) -File $uninstallPath -Arguments @('/S', "_?=$InstallDir") -Label 'Upgraded current uninstall'
	Reset-GuestSession -SessionRef ([ref]$session)
	$afterUninstallPayload = Get-InstalledPayloadState -SessionRef ([ref]$session)
	if ($afterUninstallPayload.launcherPresent) {
		throw 'Silent uninstall left copperbench.exe after the upgraded build.'
	}
	$result.silentUninstall = $true

	$afterUninstallRetention = Get-RetentionState -SessionRef ([ref]$session) -TargetWorkspaceFile $resolvedWorkspace -Token $retentionToken
	Assert-RetentionState -State $afterUninstallRetention -ExpectedWorkspaceHash $preUninstallWorkspaceHash -Label 'Upgraded current uninstall'
	$result.uninstallPreservedWorkspace = $true
	$result.uninstallPreservedUserData = $true
	$result.afterUninstallRetention = $afterUninstallRetention

	$result.restoreInstallResult = Invoke-GuestSilentProcess -SessionRef ([ref]$session) -File $guestCurrentInstaller -Arguments @('/S', "/D=$InstallDir") -Label 'Current install restore'
	Reset-GuestSession -SessionRef ([ref]$session)
	$restoredPayload = Get-InstalledPayloadState -SessionRef ([ref]$session)
	if (-not $restoredPayload.launcherPresent -or -not $restoredPayload.jarPresent) {
		throw 'Current build could not be restored after the uninstall retention check.'
	}
	$result.restoredCurrentInstall = $true
	$result.restoredPayload = $restoredPayload

	$finalRetention = Get-RetentionState -SessionRef ([ref]$session) -TargetWorkspaceFile $resolvedWorkspace -Token $retentionToken
	Assert-RetentionState -State $finalRetention -ExpectedWorkspaceHash $preUninstallWorkspaceHash -Label 'Current install restore'
	$result.finalRetention = $finalRetention

	$result.passed = ($result.previousInstallerVerified -and $result.preflightCurrentUninstall -and
			$result.preflightCurrentUninstallPreservedWorkspace -and $result.preflightCurrentUninstallPreservedUserData -and
			$result.previousReleaseInstalled -and $result.oldToCurrentUpgrade -and $result.upgradeInstalledDifferentPayload -and
			$result.upgradePreservedWorkspace -and $result.upgradePreservedUserData -and $result.networkDisconnected -and
			$result.offlineProcessStarted -and $result.offlineProcessStable -and $result.offlineWorkspaceArgumentObserved -and
			$result.offlineMainWindowObserved -and
			$result.silentUninstall -and $result.uninstallPreservedWorkspace -and $result.uninstallPreservedUserData -and
			$result.restoredCurrentInstall)
} catch {
	$result.error = $_.Exception.Message
	$result.errorType = $_.Exception.GetType().FullName
	$result.errorStack = $_.ScriptStackTrace
} finally {
	if ($networkWasDisconnectedByGate) {
		try {
			foreach ($adapter in $savedNetworkAdapters) {
				if ($adapter.switchName) {
					Get-VMNetworkAdapter -VMName $VmName -Name $adapter.name | Connect-VMNetworkAdapter -SwitchName $adapter.switchName
				}
			}
			$result.networkRestoredInFinally = $true
		} catch {
			$result.networkRestoreError = $_.Exception.Message
		}
	}
	if (-not $result.restoredCurrentInstall) {
		try {
			if ($null -eq $session -or $session.State -ne 'Opened') {
				Reset-GuestSession -SessionRef ([ref]$session)
			}
			$recoveryInstallerPresent = Invoke-Command -Session $session -ArgumentList $guestCurrentInstaller -ScriptBlock {
				param($Path)
				Test-Path -LiteralPath $Path -PathType Leaf
			}
			if ($recoveryInstallerPresent) {
				Invoke-Command -Session $session -ScriptBlock {
					Get-Process copperbench, javaw -ErrorAction SilentlyContinue |
						Stop-Process -Force -ErrorAction SilentlyContinue
					Start-Sleep -Seconds 2
				}
				$result.failureRecoveryInstallResult = Invoke-GuestSilentProcess -SessionRef ([ref]$session) `
					-File $guestCurrentInstaller -Arguments @('/S', "/D=$InstallDir") -Label 'Failure recovery current install'
				Reset-GuestSession -SessionRef ([ref]$session)
				$failureRecoveryPayload = Get-InstalledPayloadState -SessionRef ([ref]$session)
				$result.failureRecoveryPayload = $failureRecoveryPayload
				$result.currentInstallRecoveredAfterFailure = ($failureRecoveryPayload.launcherPresent -and $failureRecoveryPayload.jarPresent)
			}
		} catch {
			$result.currentInstallRecoveryError = $_.Exception.Message
		}
	}
	if ($resolvedWorkspace) {
		try {
			if ($null -eq $session -or $session.State -ne 'Opened') {
				Reset-GuestSession -SessionRef ([ref]$session)
			}
			$result.testMarkerCleanup = Invoke-Command -Session $session -ArgumentList $resolvedWorkspace, $retentionToken -ScriptBlock {
				param($TargetWorkspaceFile, $Token)
				$markerName = 'g95-retention-' + $Token + '.txt'
				$paths = @(
					(Join-Path (Split-Path -Parent $TargetWorkspaceFile) $markerName),
					(Join-Path (Join-Path $env:USERPROFILE '.copperbench') $markerName)
				)
				$removed = @()
				foreach ($path in $paths) {
					if (Test-Path -LiteralPath $path -PathType Leaf) {
						if ((Get-Content -LiteralPath $path -Raw).Trim() -ne $Token) {
							throw "Refusing to remove retention marker whose content no longer matches this run: $path"
						}
						Remove-Item -LiteralPath $path -Force
						$removed += $path
					}
				}
				[pscustomobject]@{
					removed = @($removed)
					workspaceMarkerAbsent = -not (Test-Path -LiteralPath $paths[0])
					userMarkerAbsent = -not (Test-Path -LiteralPath $paths[1])
				}
			}
			$result.testMarkersRemoved = ($result.testMarkerCleanup.workspaceMarkerAbsent -and $result.testMarkerCleanup.userMarkerAbsent)
		} catch {
			$result.testMarkerCleanupError = $_.Exception.Message
		}
	}
	if ($session) {
		Remove-PSSession $session -ErrorAction SilentlyContinue
	}
	$result.completedAt = (Get-Date).ToString('o')
	($result | ConvertTo-Json -Depth 12) | Set-Content -LiteralPath $resultPath -Encoding UTF8
	Write-Output ("passed=" + $result.passed)
	Write-Output ("previousReleaseInstalled=" + $result.previousReleaseInstalled)
	Write-Output ("oldToCurrentUpgrade=" + $result.oldToCurrentUpgrade)
	Write-Output ("upgradeInstalledDifferentPayload=" + $result.upgradeInstalledDifferentPayload)
	Write-Output ("upgradePreservedWorkspace=" + $result.upgradePreservedWorkspace)
	Write-Output ("upgradePreservedUserData=" + $result.upgradePreservedUserData)
	Write-Output ("networkDisconnected=" + $result.networkDisconnected)
	Write-Output ("offlineProcessStable=" + $result.offlineProcessStable)
	Write-Output ("offlineWorkspaceArgumentObserved=" + $result.offlineWorkspaceArgumentObserved)
	Write-Output ("offlineMainWindowObserved=" + $result.offlineMainWindowObserved)
	Write-Output ("silentUninstall=" + $result.silentUninstall)
	Write-Output ("uninstallPreservedWorkspace=" + $result.uninstallPreservedWorkspace)
	Write-Output ("uninstallPreservedUserData=" + $result.uninstallPreservedUserData)
	Write-Output ("restoredCurrentInstall=" + $result.restoredCurrentInstall)
	Write-Output ("gatePromotionReady=" + $result.gatePromotionReady)
	Write-Output ("evidence=" + $resultPath)
	if ($result.error) {
		Write-Output ("error=" + $result.error)
	}
}

if (-not $result.passed) {
	exit 2
}
