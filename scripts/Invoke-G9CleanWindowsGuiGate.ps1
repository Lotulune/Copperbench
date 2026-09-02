[CmdletBinding()]
param(
	[string]$VmName = 'Copperbench-G7',
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$GuestUser = 'g7admin',
	[string]$PasswordFile = 'D:\Hyper-V\G7\g7admin.password.txt',
	[string]$GuestRoot = 'C:\Temp\Copperbench-G9-GuiGate'
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

if (-not (Test-Path -LiteralPath $PasswordFile -PathType Leaf)) {
	throw "Guest password file not found: $PasswordFile"
}

$plainPassword = (Get-Content -LiteralPath $PasswordFile -Raw).Trim()
$securePassword = ConvertTo-SecureString $plainPassword -AsPlainText -Force
$credential = [pscredential]::new($GuestUser, $securePassword)
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-9\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

$hostResultPath = Join-Path $evidenceDir 'clean-windows11-gui-new-workspace.json'
$hostScreenshotPath = Join-Path $evidenceDir 'clean-windows11-gui-new-workspace.png'
$hostErrorPath = Join-Path $evidenceDir 'clean-windows11-gui-new-workspace-error.txt'

$guestScriptPath = Join-Path $GuestRoot 'Invoke-GuiNewWorkspace.ps1'
$guestResultPath = Join-Path $GuestRoot 'gui-new-workspace.json'
$guestScreenshotPath = Join-Path $GuestRoot 'gui-new-workspace.png'
$guestErrorPath = Join-Path $GuestRoot 'gui-new-workspace-error.txt'
$taskName = 'Copperbench-G9-GuiNewWorkspace'

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

$guestScript = @'
$ErrorActionPreference = 'Stop'

$GuestRoot = 'C:\Temp\Copperbench-G9-GuiGate'
$ResultPath = Join-Path $GuestRoot 'gui-new-workspace.json'
$ScreenshotPath = Join-Path $GuestRoot 'gui-new-workspace.png'
$ErrorPath = Join-Path $GuestRoot 'gui-new-workspace-error.txt'

New-Item -ItemType Directory -Force -Path $GuestRoot | Out-Null
Remove-Item -LiteralPath $ResultPath, $ScreenshotPath, $ErrorPath -Force -ErrorAction SilentlyContinue

$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'stage9-clean-windows11-gui-new-workspace'
	startedAt = (Get-Date).ToString('o')
	passed = $false
	interactiveSessionId = $null
	workspaceName = $null
	modid = $null
	workspaceRoot = $null
	expectedWorkspaceFile = $null
	selectorObserved = $false
	dialogObserved = $false
	dialogClosed = $false
	derivedFieldsSettled = $false
	workspaceCreated = $false
	workspaceMainObserved = $false
	generatorSetupObserved = $false
	generatorSetupClosed = $false
	cliWorkspaceObserved = $false
	cliArgumentObserved = $false
	cliSelectorObserved = $false
	priorGeneratorSetupWaited = $false
	testWorkspaceRecoveryPerformed = $false
	screenshotCaptured = $false
}

function Convert-ElementRow {
	param([Parameter(Mandatory = $true)]$Element)

	$bounds = $Element.Current.BoundingRectangle
	[pscustomobject]@{
		processId = $Element.Current.ProcessId
		name = $Element.Current.Name
		className = $Element.Current.ClassName
		controlType = [string]$Element.Current.ControlType.ProgrammaticName
		isEnabled = $Element.Current.IsEnabled
		isOffscreen = $Element.Current.IsOffscreen
		hasKeyboardFocus = $Element.Current.HasKeyboardFocus
		nativeWindowHandle = [int64]$Element.Current.NativeWindowHandle
		boundingRectangle = [pscustomobject]@{
			left = [double]$bounds.Left
			top = [double]$bounds.Top
			width = [double]$bounds.Width
			height = [double]$bounds.Height
		}
	}
}

function Send-TargetKey {
	param(
		[Parameter(Mandatory = $true)][IntPtr]$Handle,
		[Parameter(Mandatory = $true)][uint32]$VirtualKey,
		[Nullable[char]]$Character = $null
	)

	$scanCode = [G9User32]::MapVirtualKey($VirtualKey, 0)
	$keyDownLParam = [int64](1 -bor ([int64]$scanCode -shl 16))
	$keyUpLParam = $keyDownLParam -bor 0xC0000000L
	if (-not [G9User32]::PostMessage($Handle, 0x0100, [UIntPtr]$VirtualKey, [IntPtr]$keyDownLParam)) {
		throw "Failed to post WM_KEYDOWN to HWND $([int64]$Handle) for VK $VirtualKey."
	}
	if ($null -ne $Character) {
		$charCode = [uint32][char]$Character.Value
		if (-not [G9User32]::PostMessage($Handle, 0x0102, [UIntPtr]$charCode, [IntPtr]::Zero)) {
			throw "Failed to post WM_CHAR to HWND $([int64]$Handle) for character $($Character.Value)."
		}
	}
	if (-not [G9User32]::PostMessage($Handle, 0x0101, [UIntPtr]$VirtualKey, [IntPtr]$keyUpLParam)) {
		throw "Failed to post WM_KEYUP to HWND $([int64]$Handle) for VK $VirtualKey."
	}
	Start-Sleep -Milliseconds 35
}

function Send-TargetEnter {
	param([Parameter(Mandatory = $true)][IntPtr]$Handle)
	Send-TargetKey -Handle $Handle -VirtualKey 0x0D
}

function Send-TargetEscape {
	param([Parameter(Mandatory = $true)][IntPtr]$Handle)
	Send-TargetKey -Handle $Handle -VirtualKey 0x1B
}

function Send-TargetClose {
	param([Parameter(Mandatory = $true)][IntPtr]$Handle)
	if (-not [G9User32]::PostMessage($Handle, 0x0010, [UIntPtr]::Zero, [IntPtr]::Zero)) {
		throw "Failed to post WM_CLOSE to HWND $([int64]$Handle)."
	}
}

function Send-TargetText {
	param(
		[Parameter(Mandatory = $true)][IntPtr]$Handle,
		[Parameter(Mandatory = $true)][string]$Text
	)

	foreach ($character in $Text.ToCharArray()) {
		$vkAndShift = [G9User32]::VkKeyScan($character)
		if ($vkAndShift -eq -1) {
			throw "No virtual-key mapping exists for character '$character'."
		}
		$virtualKey = [uint32]($vkAndShift -band 0xFF)
		Send-TargetKey -Handle $Handle -VirtualKey $virtualKey -Character $character
	}
}

function Get-TargetWindows {
	$sessionId = (Get-Process -Id $PID).SessionId
	$targetPids = @(
		Get-Process javaw, copperbench -ErrorAction SilentlyContinue |
			Where-Object { $_.SessionId -eq $sessionId } |
			ForEach-Object { $_.Id }
	)
	if ($targetPids.Count -eq 0) {
		return @()
	}

	$desktop = [System.Windows.Automation.AutomationElement]::RootElement
	$matches = @()
	foreach ($targetPid in $targetPids) {
		$condition = [System.Windows.Automation.PropertyCondition]::new(
			[System.Windows.Automation.AutomationElement]::ProcessIdProperty,
			[int]$targetPid
		)
		$processMatches = $desktop.FindAll([System.Windows.Automation.TreeScope]::Descendants, $condition)
		foreach ($element in $processMatches) {
			try {
				$matches += $element
			} catch {
				# A target element can disappear while UIA is enumerating it.
			}
		}
	}
	return @($matches)
}

function Get-WorkspaceSelectorWindow {
	param([Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Windows)

	return @(
		$Windows | Where-Object {
			try {
				$bounds = $_.Current.BoundingRectangle
				$_.Current.ClassName -eq 'SunAwtFrame' -and
				$_.Current.Name -like 'Copperbench*' -and
				-not $_.Current.IsOffscreen -and
				$bounds.Width -ge 740 -and $bounds.Width -le 850 -and
				$bounds.Height -ge 420 -and $bounds.Height -le 520
			} catch {
				$false
			}
		}
	) | Select-Object -First 1
}

function Get-GeneratorSetupWindow {
	param([Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Windows)

	return @(
		$Windows | Where-Object {
			try {
				$_.Current.ClassName -eq 'SunAwtDialog' -and
				$_.Current.Name -eq 'Workspace setup for selected generator' -and
				$_.Current.NativeWindowHandle -ne 0 -and
				-not $_.Current.IsOffscreen
			} catch {
				$false
			}
		}
	) | Select-Object -First 1
}

function Get-CreatedWorkspaceMainWindow {
	param(
		[Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Windows,
		[Parameter(Mandatory = $true)][string]$WorkspaceName
	)

	return @(
		$Windows | Where-Object {
			try {
				$_.Current.ClassName -eq 'SunAwtFrame' -and
				$_.Current.Name -like "$WorkspaceName - Copperbench*" -and
				$_.Current.NativeWindowHandle -ne 0 -and
				-not $_.Current.IsOffscreen
			} catch {
				$false
			}
		}
	) | Select-Object -First 1
}

function Get-NewWorkspaceDialogWindow {
	param([Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Windows)

	return @(
		$Windows | Where-Object {
			try {
				$bounds = $_.Current.BoundingRectangle
				# NewWorkspaceDialog calls super(w, null, true), so its native AWT dialog title is blank.
				# Keep broad DPI/layout tolerances while still excluding small JOptionPane dialogs.
				$_.Current.ClassName -eq 'SunAwtDialog' -and
				[string]::IsNullOrEmpty($_.Current.Name) -and
				$_.Current.IsEnabled -and
				-not $_.Current.IsOffscreen -and
				$bounds.Width -ge 580 -and
				$bounds.Height -ge 500
			} catch {
				$false
			}
		}
	) | Select-Object -First 1
}

function Get-WindowRows {
	$rows = @()
	foreach ($element in @(Get-TargetWindows)) {
		try {
			$rows += (Convert-ElementRow -Element $element)
		} catch {
			# Preserve the rest of the snapshot if one transient HWND disappears.
		}
	}
	return @($rows)
}

function Set-ForegroundWindowSafe {
	param([Parameter(Mandatory = $true)]$Element)

	$handle = [IntPtr]([int64]$Element.Current.NativeWindowHandle)
	if ($handle -eq [IntPtr]::Zero) {
		throw 'UIA target has no native window handle.'
	}

	$foregroundBefore = [G9User32]::GetForegroundWindow()
	$foregroundProcessId = [uint32]0
	$targetProcessId = [uint32]0
	$foregroundThreadId = if ($foregroundBefore -ne [IntPtr]::Zero) {
		[G9User32]::GetWindowThreadProcessId($foregroundBefore, [ref]$foregroundProcessId)
	} else {
		[uint32]0
	}
	$targetThreadId = [G9User32]::GetWindowThreadProcessId($handle, [ref]$targetProcessId)
	$currentThreadId = [G9Kernel32]::GetCurrentThreadId()
	$message = New-Object G9User32+G9Message
	[void][G9User32]::PeekMessage([ref]$message, [IntPtr]::Zero, 0, 0, 0)

	$attachedForeground = $false
	$attachedTarget = $false
	$attachForegroundError = 0
	$attachTargetError = 0
	try {
		if ($foregroundThreadId -ne 0 -and $foregroundThreadId -ne $currentThreadId) {
			$attachedForeground = [G9User32]::AttachThreadInput($currentThreadId, $foregroundThreadId, $true)
			if (-not $attachedForeground) {
				$attachForegroundError = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
			}
		}
		if ($targetThreadId -ne 0 -and $targetThreadId -ne $currentThreadId) {
			$attachedTarget = [G9User32]::AttachThreadInput($currentThreadId, $targetThreadId, $true)
			if (-not $attachedTarget) {
				$attachTargetError = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
			}
		}

		[void][G9User32]::ShowWindowAsync($handle, 9)
		[void][G9User32]::BringWindowToTop($handle)
		$setForegroundResult = [G9User32]::SetForegroundWindow($handle)
		$setFocusResult = [G9User32]::SetFocus($handle)
		try {
			$Element.SetFocus()
		} catch {
			# Native activation/focus remains authoritative for top-level Swing windows.
		}
		Start-Sleep -Milliseconds 750
		$foregroundAfter = [G9User32]::GetForegroundWindow()
		$focusAfter = [G9User32]::GetFocus()

		return [pscustomobject]@{
			targetHandle = [int64]$handle
			targetProcessId = [uint32]$targetProcessId
			targetThreadId = [uint32]$targetThreadId
			currentThreadId = [uint32]$currentThreadId
			foregroundBefore = [int64]$foregroundBefore
			foregroundBeforeProcessId = [uint32]$foregroundProcessId
			foregroundBeforeThreadId = [uint32]$foregroundThreadId
			attachedForeground = $attachedForeground
			attachForegroundError = $attachForegroundError
			attachedTarget = $attachedTarget
			attachTargetError = $attachTargetError
			setForegroundResult = $setForegroundResult
			setFocusResult = [int64]$setFocusResult
			focusAfter = [int64]$focusAfter
			foregroundAfter = [int64]$foregroundAfter
			verified = ([int64]$foregroundAfter -eq [int64]$handle)
			targetFocusVerified = ([int64]$focusAfter -eq [int64]$handle)
		}
	} finally {
		if ($attachedTarget) {
			[void][G9User32]::AttachThreadInput($currentThreadId, $targetThreadId, $false)
		}
		if ($attachedForeground) {
			[void][G9User32]::AttachThreadInput($currentThreadId, $foregroundThreadId, $false)
		}
	}
}

function Capture-Screen {
	param([Parameter(Mandatory = $true)][string]$Path)

	$bounds = [System.Windows.Forms.SystemInformation]::VirtualScreen
	$bitmap = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height
	$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
	try {
		$graphics.CopyFromScreen($bounds.Left, $bounds.Top, 0, 0, $bounds.Size)
		$bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
	} finally {
		$graphics.Dispose()
		$bitmap.Dispose()
	}
}

try {
	Add-Type -AssemblyName UIAutomationClient
	Add-Type -AssemblyName UIAutomationTypes
	Add-Type -AssemblyName System.Windows.Forms
	Add-Type -AssemblyName System.Drawing
	Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class G9User32 {
	[StructLayout(LayoutKind.Sequential)]
	public struct G9Point {
		public int X;
		public int Y;
	}
	[StructLayout(LayoutKind.Sequential)]
	public struct G9Message {
		public IntPtr hwnd;
		public uint message;
		public UIntPtr wParam;
		public IntPtr lParam;
		public uint time;
		public G9Point pt;
		public uint lPrivate;
	}
    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);
    [DllImport("user32.dll")]
    public static extern bool BringWindowToTop(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern IntPtr SetFocus(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern IntPtr GetFocus();
    [DllImport("user32.dll")]
    public static extern bool PeekMessage(out G9Message lpMsg, IntPtr hWnd, uint wMsgFilterMin, uint wMsgFilterMax, uint wRemoveMsg);
    [DllImport("user32.dll")]
    public static extern uint MapVirtualKey(uint uCode, uint uMapType);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern short VkKeyScan(char ch);
    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool PostMessage(IntPtr hWnd, uint Msg, UIntPtr wParam, IntPtr lParam);
}
public static class G9Kernel32 {
    [DllImport("kernel32.dll")]
    public static extern uint GetCurrentThreadId();
}
"@

	$result.interactiveSessionId = (Get-Process -Id $PID).SessionId
	$workspaceRoot = Join-Path $env:USERPROFILE 'MCreatorWorkspaces'
	if ($workspaceRoot.StartsWith('\\')) {
		$workspaceRoot = 'C:\MCreatorWorkspaces'
	}
	$result.workspaceRoot = $workspaceRoot

	$workspaceCandidates = @(
		'Guigatealpha',
		'Guigatebeta',
		'Guigategamma',
		'Guigatedelta',
		'Guigatedepsilon',
		'Guigatezeta',
		'Guigateeta',
		'Guigatetheta'
	)
	$selection = $null
	foreach ($candidate in $workspaceCandidates) {
		$candidateModid = $candidate.ToLowerInvariant()
		$candidateFolder = Join-Path $workspaceRoot $candidateModid
		$candidateFile = Join-Path $candidateFolder ($candidateModid + '.mcreator')
		if (-not (Test-Path -LiteralPath $candidateFolder)) {
			$selection = [pscustomobject]@{
				name = $candidate
				modid = $candidateModid
				file = $candidateFile
			}
			break
		}
	}
	if ($null -eq $selection) {
		throw 'No unused alphabetic GUI-gate workspace candidate remains.'
	}

	$result.workspaceName = $selection.name
	$result.modid = $selection.modid
	$result.expectedWorkspaceFile = $selection.file

	$startWindows = @(Get-TargetWindows)
	$result.startWindows = @(Get-WindowRows)
	$selector = Get-WorkspaceSelectorWindow -Windows $startWindows
	$staleNewWorkspaceDialog = Get-NewWorkspaceDialogWindow -Windows $startWindows

	# If the immediately preceding attempt created its workspace successfully but evidence capture
	# finished while generator setup was still running, do not interrupt Gradle. Wait for that exact
	# modal progress dialog to close naturally. A setup-failure JOptionPane will remain as another
	# enabled AWT dialog and therefore prevents the recovery-close branch below from firing.
	if ($null -eq $selector) {
		$priorSetupDialog = Get-GeneratorSetupWindow -Windows $startWindows
		$priorSetupMain = @(
			$startWindows | Where-Object {
				try {
					$_.Current.ClassName -eq 'SunAwtFrame' -and
					$_.Current.Name -like 'guigate* - Copperbench*' -and
					$_.Current.NativeWindowHandle -ne 0 -and -not $_.Current.IsOffscreen
				} catch {
					$false
				}
			}
		)
		if ($null -ne $priorSetupDialog -and $priorSetupMain.Count -eq 1) {
			$result.priorGeneratorSetupWaited = $true
			$result.priorGeneratorSetupHandle = [int64]$priorSetupDialog.Current.NativeWindowHandle
			$priorSetupDeadline = (Get-Date).AddMinutes(8)
			do {
				Start-Sleep -Seconds 1
				$startWindows = @(Get-TargetWindows)
				$priorSetupDialog = Get-GeneratorSetupWindow -Windows $startWindows
				if ($null -eq $priorSetupDialog) {
					break
				}
			} while ((Get-Date) -lt $priorSetupDeadline)
			if ($null -ne $priorSetupDialog) {
				throw 'Previous GUI-gate generator setup did not finish within the bounded wait.'
			}
			$selector = Get-WorkspaceSelectorWindow -Windows $startWindows
			$staleNewWorkspaceDialog = Get-NewWorkspaceDialogWindow -Windows $startWindows
			$result.afterPriorGeneratorSetupWindows = @(Get-WindowRows)
		}
	}

	# A previous GUI-gate run can legitimately finish generator setup after its evidence timeout,
	# leaving only that disposable guigate* workspace window open. Recover only this narrowly defined
	# state: exactly one enabled guigate* MCreator frame, no enabled AWT dialogs, and a known launcher
	# path from the same interactive session. WM_CLOSE exercises MCreator.windowClosing ->
	# closeThisMCreator(false); no workspace files are deleted. Relaunch the same executable and wait
	# for the selector before any new-workspace input is sent.
	if ($null -eq $selector) {
		$recoverableTestMains = @(
			$startWindows | Where-Object {
				try {
					$_.Current.ClassName -eq 'SunAwtFrame' -and
					$_.Current.Name -like 'guigate* - Copperbench*' -and
					$_.Current.IsEnabled -and -not $_.Current.IsOffscreen -and
					$_.Current.NativeWindowHandle -ne 0
				} catch {
					$false
				}
			}
		)
		$enabledDialogs = @(
			$startWindows | Where-Object {
				try {
					$_.Current.ClassName -eq 'SunAwtDialog' -and $_.Current.IsEnabled -and
					-not $_.Current.IsOffscreen -and $_.Current.NativeWindowHandle -ne 0
				} catch {
					$false
				}
			}
		)
		if ($recoverableTestMains.Count -eq 1 -and $enabledDialogs.Count -eq 0) {
			$sessionId = (Get-Process -Id $PID).SessionId
			$launcherProcess = Get-Process copperbench -ErrorAction SilentlyContinue |
				Where-Object { $_.SessionId -eq $sessionId -and $_.Path } | Select-Object -First 1
			if ($null -eq $launcherProcess) {
				throw 'Recoverable GUI-gate workspace is open, but its Copperbench launcher path could not be resolved.'
			}
			$launcherPath = $launcherProcess.Path
			$result.testWorkspaceRecoveryHandle = [int64]$recoverableTestMains[0].Current.NativeWindowHandle
			$result.testWorkspaceRecoveryLauncher = $launcherPath
			Send-TargetClose -Handle ([IntPtr]$result.testWorkspaceRecoveryHandle)

			$exitDeadline = (Get-Date).AddSeconds(45)
			do {
				Start-Sleep -Milliseconds 500
				$remainingTargets = @(Get-Process javaw, copperbench -ErrorAction SilentlyContinue |
					Where-Object { $_.SessionId -eq $sessionId })
				if ($remainingTargets.Count -eq 0) {
					break
				}
			} while ((Get-Date) -lt $exitDeadline)
			if ($remainingTargets.Count -ne 0) {
				throw 'Completed GUI-gate workspace did not close cleanly before selector recovery relaunch.'
			}

			Start-Process -FilePath $launcherPath -WorkingDirectory (Split-Path -Parent $launcherPath)
			$selectorDeadline = (Get-Date).AddSeconds(60)
			do {
				Start-Sleep -Milliseconds 500
				$startWindows = @(Get-TargetWindows)
				$selector = Get-WorkspaceSelectorWindow -Windows $startWindows
				if ($null -ne $selector) {
					$result.testWorkspaceRecoveryPerformed = $true
					$result.afterTestWorkspaceRecoveryWindows = @(Get-WindowRows)
					break
				}
			} while ((Get-Date) -lt $selectorDeadline)
		}
	}

	# Previous diagnostic attempts can leave the modal NewWorkspaceDialog open. Normalize that
	# state first using the dialog's source-defined Escape binding (MCreatorDialog -> WINDOW_CLOSING
	# -> dispose). The Escape is posted only to the positively identified blank-title large AWT dialog.
	if ($null -ne $staleNewWorkspaceDialog) {
		$result.staleDialogRecovered = $false
		$result.staleDialogHandle = [int64]$staleNewWorkspaceDialog.Current.NativeWindowHandle
		$result.staleDialogActivation = Set-ForegroundWindowSafe -Element $staleNewWorkspaceDialog
		if (-not $result.staleDialogActivation.verified -and -not $result.staleDialogActivation.targetFocusVerified) {
			throw "Stale New Workspace dialog could not obtain verified native focus for source-defined Escape recovery. target=$($result.staleDialogActivation.targetHandle) foreground=$($result.staleDialogActivation.foregroundAfter) focus=$($result.staleDialogActivation.focusAfter)"
		}
		Send-TargetEscape -Handle ([IntPtr]$result.staleDialogHandle)

		$recoveryDeadline = (Get-Date).AddSeconds(15)
		do {
			Start-Sleep -Milliseconds 250
			$recoveryWindows = @(Get-TargetWindows)
			$staleStillPresent = Get-NewWorkspaceDialogWindow -Windows $recoveryWindows
			$selector = Get-WorkspaceSelectorWindow -Windows $recoveryWindows
			$selectorReady = $null -ne $selector -and $selector.Current.IsEnabled
			if ($null -eq $staleStillPresent -and $selectorReady) {
				$result.staleDialogRecovered = $true
				break
			}
		} while ((Get-Date) -lt $recoveryDeadline)

		$result.afterRecoveryWindows = @(Get-WindowRows)
		if (-not $result.staleDialogRecovered) {
			throw 'Source-defined Escape recovery did not restore the enabled WorkspaceSelector state.'
		}
	}

	$normalizedWindows = @(Get-TargetWindows)
	$selector = Get-WorkspaceSelectorWindow -Windows $normalizedWindows
	if ($null -eq $selector) {
		throw 'Copperbench workspace selector window was not observed.'
	}
	if (-not $selector.Current.IsEnabled) {
		throw 'Copperbench workspace selector is present but disabled by an unrecognized modal window.'
	}
	$unexpectedEnabled = @(
		$normalizedWindows | Where-Object {
			try {
				$handle = [int64]$_.Current.NativeWindowHandle
				$handle -ne [int64]$selector.Current.NativeWindowHandle -and
				$handle -ne 0 -and
				$_.Current.ControlType.ProgrammaticName -eq 'ControlType.Window' -and
				$_.Current.IsEnabled -and -not $_.Current.IsOffscreen
			} catch {
				$false
			}
		}
	)
	if ($unexpectedEnabled.Count -gt 0) {
		$result.unexpectedEnabledWindows = @(Get-WindowRows)
		throw 'Unexpected enabled Copperbench/Java top-level window remains after GUI-state normalization.'
	}
	$result.normalizedWindows = @(Get-WindowRows)
	$result.selectorObserved = $true
	$result.selectorHandle = [int64]$selector.Current.NativeWindowHandle

	$result.selectorActivation = Set-ForegroundWindowSafe -Element $selector
	if (-not $result.selectorActivation.verified -and -not $result.selectorActivation.targetFocusVerified) {
		throw "Copperbench selector could not obtain verified native focus. target=$($result.selectorActivation.targetHandle) foreground=$($result.selectorActivation.foregroundAfter) focus=$($result.selectorActivation.focusAfter)"
	}

	# Swing controls are lightweight: the top-level AWT HWND receives the native key messages and
	# dispatches them to the Swing focus owner. Target the exact enumerated HWND so SearchHost or any
	# unrelated foreground application can never receive this input.
	Send-TargetEnter -Handle ([IntPtr]$result.selectorHandle)

	$dialog = $null
	$dialogDeadline = (Get-Date).AddSeconds(15)
	while ((Get-Date) -lt $dialogDeadline -and $null -eq $dialog) {
		Start-Sleep -Milliseconds 250
		$currentWindows = @(Get-TargetWindows)
		$dialog = Get-NewWorkspaceDialogWindow -Windows $currentWindows
	}
	$result.afterFirstEnterWindows = @(Get-WindowRows)
	if ($null -eq $dialog) {
		throw 'New Workspace dialog did not appear after activating the focused New Workspace button.'
	}
	$result.dialogObserved = $true
	$result.dialogHandle = [int64]$dialog.Current.NativeWindowHandle
	$result.dialogActivationBeforeTyping = Set-ForegroundWindowSafe -Element $dialog
	if (-not $result.dialogActivationBeforeTyping.verified -and -not $result.dialogActivationBeforeTyping.targetFocusVerified) {
		throw "New Workspace dialog could not obtain verified native focus. target=$($result.dialogActivationBeforeTyping.targetHandle) foreground=$($result.dialogActivationBeforeTyping.foregroundAfter) focus=$($result.dialogActivationBeforeTyping.focusAfter)"
	}

	# NewWorkspaceDialog calls current.focusMainField(); the panel implementation targets modName.
	# Use lowercase ASCII so each character has a one-key virtual-key mapping; the UI's keyReleased
	# listener still runs and therefore exercises the real mod-id/package auto-fill behavior.
	Send-TargetText -Handle ([IntPtr]$result.dialogHandle) -Text $selection.modid

	# Directly posted WM_CHAR messages reach Swing through the AWT queue, while the modName -> modID
	# derivation is source-defined on KeyAdapter.keyReleased. The final character can therefore land
	# after the last synthetic keyReleased has already observed the field. Post a non-mutating RIGHT
	# key after all text so one real Swing keyReleased observes the complete modName and synchronizes
	# modID, package name, and the workspace-folder DocumentListener before Create is submitted.
	Send-TargetKey -Handle ([IntPtr]$result.dialogHandle) -VirtualKey 0x27
	Start-Sleep -Milliseconds 750
	$result.derivedFieldsSettled = $true

	# NewWorkspaceDialog installs Create as its root-pane default button.
	$result.dialogActivationBeforeCreate = Set-ForegroundWindowSafe -Element $dialog
	if (-not $result.dialogActivationBeforeCreate.verified -and -not $result.dialogActivationBeforeCreate.targetFocusVerified) {
		throw "New Workspace dialog lost verified native focus before Create. target=$($result.dialogActivationBeforeCreate.targetHandle) foreground=$($result.dialogActivationBeforeCreate.foregroundAfter) focus=$($result.dialogActivationBeforeCreate.focusAfter)"
	}
	Send-TargetEnter -Handle ([IntPtr]$result.dialogHandle)

	$fileDeadline = (Get-Date).AddSeconds(60)
	while ((Get-Date) -lt $fileDeadline -and -not (Test-Path -LiteralPath $selection.file -PathType Leaf)) {
		Start-Sleep -Milliseconds 500
	}
	$result.workspaceCreated = Test-Path -LiteralPath $selection.file -PathType Leaf

	$closeDeadline = (Get-Date).AddSeconds(20)
	do {
		$currentRows = @(Get-WindowRows)
		$dialogStillPresent = @($currentRows | Where-Object {
				$_.nativeWindowHandle -eq $result.dialogHandle
			}).Count -gt 0
		if (-not $dialogStillPresent) {
			$result.dialogClosed = $true
			break
		}
		Start-Sleep -Milliseconds 500
	} while ((Get-Date) -lt $closeDeadline)

	# Workspace.createWorkspace writes the .mcreator file before NewWorkspaceDialog disposes. Opening
	# the newly created workspace then starts WorkspaceGeneratorSetupDialog.runSetup(), which is a
	# progress dialog rather than a confirmation form. Do not send input to it: wait for Gradle setup
	# to finish naturally and fail closed if it remains present or is replaced by another modal.
	$setupObserveDeadline = (Get-Date).AddSeconds(30)
	$setupDialog = $null
	$createdMain = $null
	$mainStableSince = $null
	$mainStableSeconds = 5
	do {
		$currentWindows = @(Get-TargetWindows)
		$setupDialog = Get-GeneratorSetupWindow -Windows $currentWindows
		$createdMain = Get-CreatedWorkspaceMainWindow -Windows $currentWindows -WorkspaceName $selection.modid
		if ($null -ne $createdMain) {
			$result.workspaceMainObserved = $true
		}
		if ($null -ne $setupDialog) {
			break
		}
		if ($null -ne $createdMain -and $createdMain.Current.IsEnabled) {
			if ($null -eq $mainStableSince) {
				$mainStableSince = Get-Date
			}
			if (((Get-Date) - $mainStableSince).TotalSeconds -ge $mainStableSeconds) {
				break
			}
		} else {
			$mainStableSince = $null
		}
		Start-Sleep -Milliseconds 500
	} while ((Get-Date) -lt $setupObserveDeadline)

	$result.workspaceMainObserved = $result.workspaceMainObserved -or $null -ne $createdMain
	if ($null -ne $setupDialog) {
		$result.generatorSetupObserved = $true
		$result.generatorSetupHandle = [int64]$setupDialog.Current.NativeWindowHandle
		$setupDeadline = (Get-Date).AddMinutes(8)
		do {
			Start-Sleep -Seconds 1
			$currentWindows = @(Get-TargetWindows)
			$setupDialog = Get-GeneratorSetupWindow -Windows $currentWindows
			$createdMain = Get-CreatedWorkspaceMainWindow -Windows $currentWindows -WorkspaceName $selection.modid
			if ($null -ne $createdMain) {
				$result.workspaceMainObserved = $true
			}
			if ($null -eq $setupDialog) {
				$result.generatorSetupClosed = $true
				break
			}
		} while ((Get-Date) -lt $setupDeadline)
	} elseif ($null -ne $createdMain -and $createdMain.Current.IsEnabled) {
		# A warm Gradle/cache path can complete before the first setup-dialog sample.
		$result.generatorSetupClosed = $true
	}

	# Validate the packaged/native launcher's workspace argument on a clean process boundary. The Java
	# application treats the final argument as a .mcreator file, so the public launcher form
	# `copperbench.exe -workspace <file>` must skip the visible selector and open this exact workspace.
	# Close only the just-created GUI-gate workspace through MCreator's normal WM_CLOSE path, wait for
	# both native launcher and javaw to exit, then relaunch the same executable with the workspace arg.
	if ($result.workspaceCreated -and $result.workspaceMainObserved -and $result.generatorSetupClosed) {
		$currentWindows = @(Get-TargetWindows)
		$createdMain = Get-CreatedWorkspaceMainWindow -Windows $currentWindows -WorkspaceName $selection.modid
		$enabledDialogs = @(
			$currentWindows | Where-Object {
				try {
					$_.Current.ClassName -eq 'SunAwtDialog' -and $_.Current.IsEnabled -and
					-not $_.Current.IsOffscreen -and $_.Current.NativeWindowHandle -ne 0
				} catch {
					$false
				}
			}
		)
		if ($null -eq $createdMain -or -not $createdMain.Current.IsEnabled) {
			throw 'Created GUI-gate workspace did not become enabled after generator setup.'
		}
		if ($enabledDialogs.Count -gt 0) {
			throw 'An enabled AWT dialog remains after generator setup; refusing CLI cold-start transition.'
		}

		$sessionId = (Get-Process -Id $PID).SessionId
		$launcherProcess = Get-Process copperbench -ErrorAction SilentlyContinue |
			Where-Object { $_.SessionId -eq $sessionId -and $_.Path } | Select-Object -First 1
		if ($null -eq $launcherProcess) {
			throw 'Copperbench launcher path could not be resolved before CLI cold-start validation.'
		}
		$launcherPath = $launcherProcess.Path
		$result.cliLauncherPath = $launcherPath
		$result.cliClosedGuiHandle = [int64]$createdMain.Current.NativeWindowHandle
		Send-TargetClose -Handle ([IntPtr]$result.cliClosedGuiHandle)

		$cliExitDeadline = (Get-Date).AddSeconds(45)
		do {
			Start-Sleep -Milliseconds 500
			$remainingTargets = @(Get-Process javaw, copperbench -ErrorAction SilentlyContinue |
				Where-Object { $_.SessionId -eq $sessionId })
			if ($remainingTargets.Count -eq 0) {
				break
			}
		} while ((Get-Date) -lt $cliExitDeadline)
		if ($remainingTargets.Count -ne 0) {
			throw 'GUI-created workspace did not close cleanly before CLI cold-start validation.'
		}

		Start-Process -FilePath $launcherPath -ArgumentList @('-workspace', $selection.file) -WorkingDirectory (Split-Path -Parent $launcherPath)
		$cliDeadline = (Get-Date).AddSeconds(120)
		$cliMain = $null
		do {
			Start-Sleep -Milliseconds 500
			$cliWindows = @(Get-TargetWindows)
			$cliSelector = Get-WorkspaceSelectorWindow -Windows $cliWindows
			if ($null -ne $cliSelector) {
				$result.cliSelectorObserved = $true
			}
			$cliMain = Get-CreatedWorkspaceMainWindow -Windows $cliWindows -WorkspaceName $selection.modid
			if ($null -ne $cliMain -and $cliMain.Current.IsEnabled) {
				$result.cliWorkspaceObserved = $true
				$result.cliWorkspaceHandle = [int64]$cliMain.Current.NativeWindowHandle
				$result.cliWorkspaceProcessId = [int]$cliMain.Current.ProcessId
				break
			}
		} while ((Get-Date) -lt $cliDeadline)

		if ($result.cliWorkspaceObserved) {
			$cliProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($result.cliWorkspaceProcessId)" -ErrorAction SilentlyContinue
			if ($null -ne $cliProcess) {
				$result.cliJavaCommandLine = $cliProcess.CommandLine
				$result.cliArgumentObserved = $cliProcess.CommandLine -like "*$($selection.file)*"
			}
		}
	}

	$result.finalWindows = @(Get-WindowRows)
	$result.foregroundFinal = [int64][G9User32]::GetForegroundWindow()
	$result.passed = ($result.selectorObserved -and $result.dialogObserved -and $result.derivedFieldsSettled -and
			$result.workspaceCreated -and $result.dialogClosed -and $result.workspaceMainObserved -and
			$result.generatorSetupClosed -and $result.cliWorkspaceObserved -and $result.cliArgumentObserved -and
			-not $result.cliSelectorObserved)
} catch {
	$result.error = $_.Exception.Message
	$result.errorType = $_.Exception.GetType().FullName
	$result.errorStack = $_.ScriptStackTrace
	($_ | Out-String) | Set-Content -LiteralPath $ErrorPath -Encoding UTF8
} finally {
	try {
		Capture-Screen -Path $ScreenshotPath
		$result.screenshotCaptured = Test-Path -LiteralPath $ScreenshotPath -PathType Leaf
	} catch {
		$result.screenshotError = $_.Exception.Message
	}
	if (-not $result.finalWindows) {
		try {
			$result.finalWindows = @(Get-WindowRows)
		} catch {
			$result.finalWindowsError = $_.Exception.Message
		}
	}
	$result.completedAt = (Get-Date).ToString('o')
	($result | ConvertTo-Json -Depth 10) | Set-Content -LiteralPath $ResultPath -Encoding UTF8
}

if (-not $result.passed) {
	exit 2
}
'@

$session = $null
try {
	$vm = Get-VM -Name $VmName -ErrorAction Stop
	if ($vm.State -ne 'Running') {
		throw "VM must already be running for the non-installing GUI gate: $VmName ($($vm.State))."
	}

	$session = New-GuestSession -TargetVm $VmName -Credential $credential
	Invoke-Command -Session $session -ArgumentList $GuestRoot, $guestScriptPath, $guestResultPath, $guestScreenshotPath,
		$guestErrorPath, $guestScript -ScriptBlock {
		param($TargetRoot, $ScriptPath, $ResultPath, $ScreenshotPath, $ErrorPath, $ScriptContent)
		New-Item -ItemType Directory -Force -Path $TargetRoot | Out-Null
		Remove-Item -LiteralPath $ResultPath, $ScreenshotPath, $ErrorPath -Force -ErrorAction SilentlyContinue
		Set-Content -LiteralPath $ScriptPath -Value $ScriptContent -Encoding UTF8
	}

	Invoke-Command -Session $session -ArgumentList $taskName, $guestScriptPath, $GuestUser -ScriptBlock {
		param($TaskName, $ScriptPath, $TargetUser)
		Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
		$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$ScriptPath`""
		# The installed Copperbench process can inherit an elevated integrity level from the clean-install
		# launch path. Run the input driver at the same user's highest level so UIPI does not block
		# foreground-thread attachment; exact HWND verification below still gates every SendKeys call.
		$principal = New-ScheduledTaskPrincipal -UserId $TargetUser -LogonType Interactive -RunLevel Highest
		Register-ScheduledTask -TaskName $TaskName -Action $action -Principal $principal -Force | Out-Null
		Start-ScheduledTask -TaskName $TaskName
	}

	$deadline = (Get-Date).AddMinutes(10)
	$guestState = $null
	do {
		Start-Sleep -Milliseconds 500
		$guestState = Invoke-Command -Session $session -ArgumentList $taskName, $guestResultPath, $guestErrorPath -ScriptBlock {
			param($TaskName, $ResultPath, $ErrorPath)
			$task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
			$info = Get-ScheduledTaskInfo -TaskName $TaskName -ErrorAction SilentlyContinue
			[pscustomobject]@{
				resultPresent = Test-Path -LiteralPath $ResultPath -PathType Leaf
				errorPresent = Test-Path -LiteralPath $ErrorPath -PathType Leaf
				taskState = if ($task) { [string]$task.State } else { $null }
				lastTaskResult = if ($info) { $info.LastTaskResult } else { $null }
			}
		}
	} while (-not $guestState.resultPresent -and (Get-Date) -lt $deadline)

	if (-not $guestState.resultPresent) {
		throw "Timed out waiting for GUI-gate result. taskState=$($guestState.taskState) lastTaskResult=$($guestState.lastTaskResult)"
	}

	Copy-Item -LiteralPath $guestResultPath -Destination $hostResultPath -FromSession $session -Force
	if (Invoke-Command -Session $session -ArgumentList $guestScreenshotPath -ScriptBlock {
			param($Path)
			Test-Path -LiteralPath $Path -PathType Leaf
		}) {
		Copy-Item -LiteralPath $guestScreenshotPath -Destination $hostScreenshotPath -FromSession $session -Force
	}
	if ($guestState.errorPresent) {
		Copy-Item -LiteralPath $guestErrorPath -Destination $hostErrorPath -FromSession $session -Force
	} else {
		Remove-Item -LiteralPath $hostErrorPath -Force -ErrorAction SilentlyContinue
	}

	$result = Get-Content -LiteralPath $hostResultPath -Raw | ConvertFrom-Json
	Write-Output ("passed=" + $result.passed)
	Write-Output ("selectorObserved=" + $result.selectorObserved)
	Write-Output ("dialogObserved=" + $result.dialogObserved)
	Write-Output ("derivedFieldsSettled=" + $result.derivedFieldsSettled)
	Write-Output ("workspaceCreated=" + $result.workspaceCreated)
	Write-Output ("dialogClosed=" + $result.dialogClosed)
	Write-Output ("workspaceMainObserved=" + $result.workspaceMainObserved)
	Write-Output ("generatorSetupObserved=" + $result.generatorSetupObserved)
	Write-Output ("generatorSetupClosed=" + $result.generatorSetupClosed)
	Write-Output ("cliWorkspaceObserved=" + $result.cliWorkspaceObserved)
	Write-Output ("cliArgumentObserved=" + $result.cliArgumentObserved)
	Write-Output ("cliSelectorObserved=" + $result.cliSelectorObserved)
	Write-Output ("workspaceFile=" + $result.expectedWorkspaceFile)
	Write-Output ("evidence=" + $hostResultPath)
	Write-Output ("screenshot=" + $hostScreenshotPath)
	if ($result.error) {
		Write-Output ("error=" + $result.error)
	}

	if (-not $result.passed) {
		exit 2
	}
} finally {
	if ($session) {
		Remove-PSSession $session -ErrorAction SilentlyContinue
	}
}
