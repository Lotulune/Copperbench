[CmdletBinding()]
param(
	[string]$VmName = 'Copperbench-G7',
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$GuestUser = 'g7admin',
	[string]$PasswordFile = 'D:\Hyper-V\G7\g7admin.password.txt',
	[string]$GuestRoot = 'C:\Temp\Copperbench-Stage13-HistoryGate',
	[string]$InstallDir = 'C:\Copperbench-G9',
	[string]$WorkspaceFile = 'C:\Users\g7admin\MCreatorWorkspaces\guigatedelta\guigatedelta.mcreator'
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

if (-not (Test-Path -LiteralPath $PasswordFile -PathType Leaf)) {
	throw "Guest password file not found: $PasswordFile"
}

$plainPassword = (Get-Content -LiteralPath $PasswordFile -Raw).Trim()
$credential = [pscredential]::new($GuestUser, (ConvertTo-SecureString $plainPassword -AsPlainText -Force))
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-13\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

$hostResultPath = Join-Path $evidenceDir 'history-recovery-clean-windows11.json'
$hostCreateScreenshotPath = Join-Path $evidenceDir 'history-recovery-create-dialog.png'
$hostBeforeScreenshotPath = Join-Path $evidenceDir 'history-recovery-before-confirm.png'
$hostAfterScreenshotPath = Join-Path $evidenceDir 'history-recovery-after-restore.png'
$hostErrorPath = Join-Path $evidenceDir 'history-recovery-error.txt'

$guestScriptPath = Join-Path $GuestRoot 'Invoke-HistoryRecovery.ps1'
$guestResultPath = Join-Path $GuestRoot 'history-recovery.json'
$guestCreateScreenshotPath = Join-Path $GuestRoot 'history-recovery-create-dialog.png'
$guestBeforeScreenshotPath = Join-Path $GuestRoot 'history-recovery-before-confirm.png'
$guestAfterScreenshotPath = Join-Path $GuestRoot 'history-recovery-after-restore.png'
$guestErrorPath = Join-Path $GuestRoot 'history-recovery-error.txt'
$taskName = 'Copperbench-Stage13-HistoryRecovery'

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
param(
	[Parameter(Mandatory = $true)][string]$InstallDir,
	[Parameter(Mandatory = $true)][string]$WorkspaceFile,
	[Parameter(Mandatory = $true)][string]$GuestRoot
)

$ErrorActionPreference = 'Stop'
$ResultPath = Join-Path $GuestRoot 'history-recovery.json'
$CreateScreenshotPath = Join-Path $GuestRoot 'history-recovery-create-dialog.png'
$BeforeScreenshotPath = Join-Path $GuestRoot 'history-recovery-before-confirm.png'
$AfterScreenshotPath = Join-Path $GuestRoot 'history-recovery-after-restore.png'
$ErrorPath = Join-Path $GuestRoot 'history-recovery-error.txt'
New-Item -ItemType Directory -Force -Path $GuestRoot | Out-Null
Remove-Item -LiteralPath $ResultPath, $CreateScreenshotPath, $BeforeScreenshotPath, $AfterScreenshotPath, $ErrorPath -Force -ErrorAction SilentlyContinue

$workspaceRoot = Split-Path -Parent $WorkspaceFile
$workspaceName = [IO.Path]::GetFileNameWithoutExtension($WorkspaceFile)
$launcher = Join-Path $InstallDir 'copperbench.exe'
$trackedFile = Join-Path $workspaceRoot 'stage13-history-sentinel.txt'
$legacyGateMutationFile = Join-Path $workspaceRoot 'gradle.properties'
$legacyTrackedMutationFile = Join-Path $workspaceRoot 'gradle\wrapper\gradle-wrapper.properties'
$trackedBaselineContent = "stage13-history-baseline`r`n"
$trackedMutationMarker = "stage13-history-mutated`r`n"
$checkpointLabel = 'Stage13HistoryBaseline'

$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'stage13-history-recovery-clean-windows11'
	startedAt = (Get-Date).ToString('o')
	passed = $false
	workspaceFile = $WorkspaceFile
	trackedFilePath = $trackedFile
	trackedFileBaselineSha256 = $null
	trackedFileMutatedSha256 = $null
	trackedFileSha256AfterSecondLaunch = $null
	trackedFileRestoredSha256 = $null
	checkpointLabel = $checkpointLabel
	firstLaunchObserved = $false
	firstRenderReadyMillis = $null
	checkpointCreated = $false
	checkpointHeadBefore = $null
	checkpointHeadAfter = $null
	checkpointHead = $null
	preflightValidateExitCode = $null
	preflightValidateStatus = $null
	preflightValidateStdout = $null
	preflightValidateStderr = $null
	gpuAccelerationPreference = $null
	jcefMode = $null
	softwareDisplayFallbackObserved = $false
	jcefStartupLog = @()
	firstClientWidth = $null
	firstClientHeight = $null
	historyClickX = 95
	historyClickY = 320
	historyScreenX = $null
	historyScreenY = $null
	historyWindowAtPoint = $null
	historyWindowClassAtPoint = $null
	historyProcessAtPoint = $null
	createClickX = $null
	createClickY = 72
	createInputClickX = $null
	createInputClickY = $null
	createConfirmClickX = $null
	createConfirmClickY = $null
	createDialogScreenshotCaptured = $false
	mutatedBeforeRelaunch = $false
	secondLaunchObserved = $false
	secondRenderReadyMillis = $null
	restoreDialogReached = $false
	restoreConfirmWaitMillis = $null
	restoreConfirmClickX = $null
	restoreConfirmClickY = $null
	restoreConfirmRedSamples = $null
	trackedFileRestored = $false
	validateExitCode = $null
	validateStatus = $null
	validateOperation = $null
	cefLogTail = @()
	beforeScreenshotCaptured = $false
	afterScreenshotCaptured = $false
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

function Get-HistoryHeadId {
	$gitDir = Join-Path $workspaceRoot '.copperbench\local-history.git'
	$headPath = Join-Path $gitDir 'HEAD'
	if (-not (Test-Path -LiteralPath $headPath -PathType Leaf)) { return $null }
	$head = (Get-Content -LiteralPath $headPath -Raw).Trim()
	if ($head.StartsWith('ref: ')) {
		$refPath = Join-Path $gitDir ($head.Substring(5).Replace('/', '\'))
		if (Test-Path -LiteralPath $refPath -PathType Leaf) {
			return (Get-Content -LiteralPath $refPath -Raw).Trim()
		}
	}
	return $head
}

function Get-WorkspaceWindow {
	$process = Get-Process javaw -ErrorAction SilentlyContinue |
		Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like "$workspaceName - Copperbench*" } |
		Select-Object -First 1
	return $process
}

function Wait-WorkspaceWindow {
	param([int]$Seconds = 120)
	$deadline = (Get-Date).AddSeconds($Seconds)
	do {
		Start-Sleep -Milliseconds 500
		$process = Get-WorkspaceWindow
		if ($null -ne $process) { return $process }
	} while ((Get-Date) -lt $deadline)
	throw "Workspace window did not appear for $workspaceName."
}

function Wait-ProductExit {
	param([int]$Seconds = 60)
	$sessionId = (Get-Process -Id $PID).SessionId
	$deadline = (Get-Date).AddSeconds($Seconds)
	do {
		Start-Sleep -Milliseconds 400
		$remaining = @(Get-Process javaw, copperbench -ErrorAction SilentlyContinue |
			Where-Object { $_.SessionId -eq $sessionId })
		if ($remaining.Count -eq 0) { return }
	} while ((Get-Date) -lt $deadline)
	throw 'Copperbench did not exit before the next History gate phase.'
}

function Focus-WorkspaceWindow {
	param([Parameter(Mandatory = $true)]$Process)
	$handle = [IntPtr]$Process.MainWindowHandle
	$foregroundBefore = [Stage13User32]::GetForegroundWindow()
	$foregroundPid = [uint32]0
	$targetPid = [uint32]0
	$foregroundThreadId = if ($foregroundBefore -ne [IntPtr]::Zero) {
		[Stage13User32]::GetWindowThreadProcessId($foregroundBefore, [ref]$foregroundPid)
	} else { [uint32]0 }
	$targetThreadId = [Stage13User32]::GetWindowThreadProcessId($handle, [ref]$targetPid)
	$currentThreadId = [Stage13Kernel32]::GetCurrentThreadId()
	$message = New-Object Stage13User32+MESSAGE
	[void][Stage13User32]::PeekMessage([ref]$message, [IntPtr]::Zero, 0, 0, 0)
	$attachedForeground = $false
	$attachedTarget = $false
	try {
		if ($foregroundThreadId -ne 0 -and $foregroundThreadId -ne $currentThreadId) {
			$attachedForeground = [Stage13User32]::AttachThreadInput($currentThreadId, $foregroundThreadId, $true)
		}
		if ($targetThreadId -ne 0 -and $targetThreadId -ne $currentThreadId) {
			$attachedTarget = [Stage13User32]::AttachThreadInput($currentThreadId, $targetThreadId, $true)
		}
		[void][Stage13User32]::ShowWindowAsync($handle, 3)
		[void][Stage13User32]::BringWindowToTop($handle)
		[void][Stage13User32]::SetForegroundWindow($handle)
		[void][Stage13User32]::SetFocus($handle)
		Start-Sleep -Milliseconds 800
		$foregroundAfter = [Stage13User32]::GetForegroundWindow()
		$focusAfter = [Stage13User32]::GetFocus()
		if ($foregroundAfter -ne $handle -and $focusAfter -ne $handle) {
			throw "Could not focus Copperbench workspace HWND $([int64]$handle)."
		}
	} finally {
		if ($attachedTarget) {
			[void][Stage13User32]::AttachThreadInput($currentThreadId, $targetThreadId, $false)
		}
		if ($attachedForeground) {
			[void][Stage13User32]::AttachThreadInput($currentThreadId, $foregroundThreadId, $false)
		}
	}
	return $handle
}

function Get-ClientSize {
	param([Parameter(Mandatory = $true)][IntPtr]$Handle)
	$rect = New-Object Stage13User32+RECT
	if (-not [Stage13User32]::GetClientRect($Handle, [ref]$rect)) {
		throw 'GetClientRect failed.'
	}
	return [pscustomobject]@{ width = $rect.Right - $rect.Left; height = $rect.Bottom - $rect.Top }
}

function Wait-WorkbenchRendered {
	param(
		[Parameter(Mandatory = $true)][IntPtr]$Handle,
		[int]$Seconds = 30
	)
	$size = Get-ClientSize -Handle $Handle
	$origin = New-Object Stage13User32+POINT
	$origin.X = 0
	$origin.Y = 0
	if (-not [Stage13User32]::ClientToScreen($Handle, [ref]$origin)) {
		throw 'ClientToScreen failed while waiting for the JCEF workbench to render.'
	}
	$started = Get-Date
	$deadline = $started.AddSeconds($Seconds)
	do {
		$bitmap = New-Object Drawing.Bitmap $size.width, $size.height
		$graphics = [Drawing.Graphics]::FromImage($bitmap)
		try {
			$graphics.CopyFromScreen($origin.X, $origin.Y, 0, 0, $bitmap.Size)
			$nonGraySamples = 0
			for ($y = 0; $y -lt $size.height; $y += 16) {
				for ($x = 0; $x -lt $size.width; $x += 16) {
					$pixel = $bitmap.GetPixel($x, $y)
					$max = [Math]::Max($pixel.R, [Math]::Max($pixel.G, $pixel.B))
					$min = [Math]::Min($pixel.R, [Math]::Min($pixel.G, $pixel.B))
					if (($max - $min) -ge 5) { $nonGraySamples++ }
				}
			}
			if ($nonGraySamples -ge 50) {
				return [int][Math]::Round(((Get-Date) - $started).TotalMilliseconds)
			}
		} finally {
			$graphics.Dispose()
			$bitmap.Dispose()
		}
		Start-Sleep -Milliseconds 500
	} while ((Get-Date) -lt $deadline)
	throw "JCEF workbench did not render a non-placeholder UI within $Seconds seconds."
}

function Wait-RestoreConfirmTarget {
	param(
		[Parameter(Mandatory = $true)][IntPtr]$Handle,
		[int]$Seconds = 20
	)
	$size = Get-ClientSize -Handle $Handle
	$origin = New-Object Stage13User32+POINT
	$origin.X = 0
	$origin.Y = 0
	if (-not [Stage13User32]::ClientToScreen($Handle, [ref]$origin)) {
		throw 'ClientToScreen failed while waiting for the restore confirmation action.'
	}
	$started = Get-Date
	$deadline = $started.AddSeconds($Seconds)
	do {
		$bitmap = New-Object Drawing.Bitmap $size.width, $size.height
		$graphics = [Drawing.Graphics]::FromImage($bitmap)
		try {
			$graphics.CopyFromScreen($origin.X, $origin.Y, 0, 0, $bitmap.Size)
			$minX = $size.width
			$minY = $size.height
			$maxX = -1
			$maxY = -1
			$count = 0
			$startX = [int]($size.width * 0.58)
			$startY = [int]($size.height * 0.50)
			$endY = [Math]::Min($size.height - 12, [int]($size.height * 0.86))
			for ($y = $startY; $y -lt $endY; $y += 2) {
				for ($x = $startX; $x -lt ($size.width - 12); $x += 2) {
					$pixel = $bitmap.GetPixel($x, $y)
					if ($pixel.R -ge 150 -and ($pixel.R - $pixel.G) -ge 60 -and ($pixel.R - $pixel.B) -ge 35) {
						$count++
						if ($x -lt $minX) { $minX = $x }
						if ($x -gt $maxX) { $maxX = $x }
						if ($y -lt $minY) { $minY = $y }
						if ($y -gt $maxY) { $maxY = $y }
					}
				}
			}
			if ($count -ge 4) {
				return [pscustomobject]@{
					x = [int][Math]::Round(($minX + $maxX) / 2.0)
					y = [int][Math]::Round(($minY + $maxY) / 2.0)
					waitMillis = [int][Math]::Round(((Get-Date) - $started).TotalMilliseconds)
					redSamples = $count
				}
			}
		} finally {
			$graphics.Dispose()
			$bitmap.Dispose()
		}
		Start-Sleep -Milliseconds 400
	} while ((Get-Date) -lt $deadline)
	throw "Restore confirmation did not become visibly enabled within $Seconds seconds."
}

function Find-ChromeInputTarget {
	param(
		[Parameter(Mandatory = $true)][IntPtr]$ParentHandle,
		[Parameter(Mandatory = $true)][int]$ScreenX,
		[Parameter(Mandatory = $true)][int]$ScreenY
	)
	$stack = New-Object 'System.Collections.Generic.Stack[System.IntPtr]'
	$stack.Push($ParentHandle)
	$matches = @()
	while ($stack.Count -gt 0) {
		$window = $stack.Pop()
		$className = New-Object Text.StringBuilder 256
		[void][Stage13User32]::GetClassName($window, $className, $className.Capacity)
		$rect = New-Object Stage13User32+RECT
		if ($className.ToString().StartsWith('Chrome_') -and [Stage13User32]::GetWindowRect($window, [ref]$rect) -and
				$ScreenX -ge $rect.Left -and $ScreenX -lt $rect.Right -and $ScreenY -ge $rect.Top -and $ScreenY -lt $rect.Bottom) {
			$width = [Math]::Max(1, $rect.Right - $rect.Left)
			$height = [Math]::Max(1, $rect.Bottom - $rect.Top)
			$matches += [pscustomobject]@{
				handle = [int64]$window
				className = $className.ToString()
				area = [int64]$width * [int64]$height
				isRenderer = ($className.ToString() -eq 'Chrome_RenderWidgetHostHWND')
			}
		}
		$child = [Stage13User32]::GetWindow($window, 5)
		while ($child -ne [IntPtr]::Zero) {
			$stack.Push($child)
			$child = [Stage13User32]::GetWindow($child, 2)
		}
	}
	return $matches | Sort-Object @{ Expression = { if ($_.isRenderer) { 0 } else { 1 } } }, area | Select-Object -First 1
}

function Click-Client {
	param(
		[Parameter(Mandatory = $true)][IntPtr]$Handle,
		[Parameter(Mandatory = $true)][int]$X,
		[Parameter(Mandatory = $true)][int]$Y
	)
	if ([Stage13User32]::GetForegroundWindow() -ne $Handle) {
		[void][Stage13User32]::SetForegroundWindow($Handle)
		Start-Sleep -Milliseconds 250
	}
	$point = New-Object Stage13User32+POINT
	$point.X = $X
	$point.Y = $Y
	if (-not [Stage13User32]::ClientToScreen($Handle, [ref]$point)) {
		throw 'ClientToScreen failed.'
	}
	[void][Stage13User32]::SetCursorPos($point.X, $point.Y)
	$targetInfo = Find-ChromeInputTarget -ParentHandle $Handle -ScreenX $point.X -ScreenY $point.Y
	$target = if ($null -ne $targetInfo) { [IntPtr]$targetInfo.handle } else { [Stage13User32]::WindowFromPoint($point) }
	$className = New-Object Text.StringBuilder 256
	[void][Stage13User32]::GetClassName($target, $className, $className.Capacity)
	if ($target -ne [IntPtr]::Zero -and $className.ToString().StartsWith('Chrome_')) {
		$local = New-Object Stage13User32+POINT
		$local.X = $point.X
		$local.Y = $point.Y
		if (-not [Stage13User32]::ScreenToClient($target, [ref]$local)) {
			throw 'ScreenToClient failed for the JCEF Chromium input target.'
		}
		[void][Stage13User32]::SetFocus($target)
		$lParam = [int64](($local.X -band 0xFFFF) -bor (($local.Y -band 0xFFFF) -shl 16))
		[void][Stage13User32]::SendMessage($target, 0x0200, [UIntPtr]::Zero, [IntPtr]$lParam)
		[void][Stage13User32]::SendMessage($target, 0x0201, [UIntPtr]::new([uint64]1), [IntPtr]$lParam)
		[void][Stage13User32]::SendMessage($target, 0x0202, [UIntPtr]::Zero, [IntPtr]$lParam)
	} elseif (-not $className.ToString().StartsWith('Chrome_')) {
		Start-Sleep -Milliseconds 100
		[Stage13User32]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
		[Stage13User32]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
	}
	Start-Sleep -Milliseconds 600
}

function Describe-ClientPoint {
	param(
		[Parameter(Mandatory = $true)][IntPtr]$Handle,
		[Parameter(Mandatory = $true)][int]$X,
		[Parameter(Mandatory = $true)][int]$Y
	)
	$point = New-Object Stage13User32+POINT
	$point.X = $X
	$point.Y = $Y
	if (-not [Stage13User32]::ClientToScreen($Handle, [ref]$point)) {
		throw 'ClientToScreen failed while describing input target.'
	}
	$target = [Stage13User32]::WindowFromPoint($point)
	$className = New-Object Text.StringBuilder 256
	[void][Stage13User32]::GetClassName($target, $className, $className.Capacity)
	$processId = [uint32]0
	[void][Stage13User32]::GetWindowThreadProcessId($target, [ref]$processId)
	return [pscustomobject]@{
		screenX = $point.X
		screenY = $point.Y
		window = [int64]$target
		className = $className.ToString()
		processId = [int]$processId
	}
}

function Send-KeySequence {
	param([Parameter(Mandatory = $true)][string]$Keys)
	[System.Windows.Forms.SendKeys]::SendWait($Keys)
	Start-Sleep -Milliseconds 350
}

function Close-WorkspaceWindow {
	param([Parameter(Mandatory = $true)][IntPtr]$Handle)
	if (-not [Stage13User32]::PostMessage($Handle, 0x0010, [UIntPtr]::Zero, [IntPtr]::Zero)) {
		throw 'Failed to post WM_CLOSE to Copperbench.'
	}
}

function Launch-Workspace {
	Start-Process -FilePath $launcher -ArgumentList @('-workspace', $WorkspaceFile) -WorkingDirectory $InstallDir
	$process = Wait-WorkspaceWindow
	# The AWT frame can publish its title before Windows has finished activating
	# the newly created native/JCEF child hierarchy. Let that one window settle
	# before applying the same verified focus path used by the Stage 9 GUI gate.
	Start-Sleep -Seconds 2
	$process = Get-WorkspaceWindow
	if ($null -eq $process) {
		throw "Workspace window disappeared while settling for $workspaceName."
	}
	$handle = Focus-WorkspaceWindow -Process $process
	return [pscustomobject]@{ process = $process; handle = $handle; size = (Get-ClientSize -Handle $handle) }
}

function Invoke-InstalledValidate {
	$psi = [Diagnostics.ProcessStartInfo]::new()
	$psi.FileName = $launcher
	$psi.WorkingDirectory = $InstallDir
	$psi.UseShellExecute = $false
	$psi.RedirectStandardOutput = $true
	$psi.RedirectStandardError = $true
	$psi.CreateNoWindow = $true
	$psi.Arguments = "headless --workspace `"$WorkspaceFile`" validate"
	$process = [Diagnostics.Process]::new()
	$process.StartInfo = $psi
	[void]$process.Start()
	if (-not $process.WaitForExit(120000)) {
		$process.Kill($true)
		$process.WaitForExit()
		throw 'Installed headless preflight validate timed out.'
	}
	$stdout = $process.StandardOutput.ReadToEnd()
	$stderr = $process.StandardError.ReadToEnd()
	$status = 'invalid_json'
	try {
		$status = ($stdout.Trim() | ConvertFrom-Json).status
	} catch { }
	return [pscustomobject]@{
		exitCode = $process.ExitCode
		status = $status
		stdout = $stdout.Trim()
		stderr = $stderr.Trim()
	}
}

try {
	if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) { throw "Installed launcher missing: $launcher" }
	if (-not (Test-Path -LiteralPath $WorkspaceFile -PathType Leaf)) { throw "Workspace file missing: $WorkspaceFile" }
	Add-Type -AssemblyName System.Windows.Forms
	Add-Type -AssemblyName System.Drawing
	Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Stage13User32 {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
	[StructLayout(LayoutKind.Sequential)] public struct MESSAGE {
		public IntPtr hwnd;
		public uint message;
		public UIntPtr wParam;
		public IntPtr lParam;
		public uint time;
		public POINT pt;
		public uint lPrivate;
	}
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
	[DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
	[DllImport("user32.dll", SetLastError=true)] public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);
	[DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr hWnd);
	[DllImport("user32.dll")] public static extern IntPtr SetFocus(IntPtr hWnd);
	[DllImport("user32.dll")] public static extern IntPtr GetFocus();
	[DllImport("user32.dll")] public static extern bool PeekMessage(out MESSAGE lpMsg, IntPtr hWnd, uint min, uint max, uint remove);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hWnd, ref RECT lpRect);
	[DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, ref RECT lpRect);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hWnd, ref POINT lpPoint);
	[DllImport("user32.dll")] public static extern bool ScreenToClient(IntPtr hWnd, ref POINT lpPoint);
	[DllImport("user32.dll")] public static extern IntPtr WindowFromPoint(POINT Point);
	[DllImport("user32.dll")] public static extern IntPtr GetWindow(IntPtr hWnd, uint uCmd);
	[DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetClassName(IntPtr hWnd, System.Text.StringBuilder lpClassName, int nMaxCount);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int X, int Y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint dwFlags, uint dx, uint dy, uint dwData, UIntPtr dwExtraInfo);
	[DllImport("user32.dll")] public static extern IntPtr SendMessage(IntPtr hWnd, uint Msg, UIntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll", SetLastError=true)] public static extern bool PostMessage(IntPtr hWnd, uint Msg, UIntPtr wParam, IntPtr lParam);
}
public static class Stage13Kernel32 {
	[DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
}
"@

	Get-Process copperbench, javaw -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
	Start-Sleep -Seconds 2
	$preflight = Invoke-InstalledValidate
	$result.preflightValidateExitCode = $preflight.exitCode
	$result.preflightValidateStatus = $preflight.status
	$result.preflightValidateStdout = $preflight.stdout
	$result.preflightValidateStderr = $preflight.stderr
	if ($preflight.exitCode -ne 0 -or $preflight.status -ne 'succeeded') {
		throw "Installed workspace preflight validate failed. exit=$($preflight.exitCode) status=$($preflight.status)"
	}
	$preferencesFile = Join-Path $env:USERPROFILE '.copperbench\userpreferences'
	if (Test-Path -LiteralPath $preferencesFile -PathType Leaf) {
		try {
			$preferences = Get-Content -LiteralPath $preferencesFile -Raw | ConvertFrom-Json
			if ($null -ne $preferences.core -and $null -ne $preferences.core.blockly) {
				$result.gpuAccelerationPreference = [bool]$preferences.core.blockly.useGPUAcceleration
			}
		} catch { }
	}
	# Remove only exact trailing markers left by earlier iterations of this gate
	# when gradle.properties was the acceptance target. Do not normalize or
	# otherwise rewrite that generator-managed file.
	if (Test-Path -LiteralPath $legacyGateMutationFile -PathType Leaf) {
		$legacyText = [IO.File]::ReadAllText($legacyGateMutationFile)
		$cleanLegacyText = [Text.RegularExpressions.Regex]::Replace($legacyText,
			'(\r?\n# stage13-history-mutated)+\z', '')
		if ($cleanLegacyText -ne $legacyText) {
			[IO.File]::WriteAllText($legacyGateMutationFile, $cleanLegacyText)
		}
	}
	if (Test-Path -LiteralPath $legacyTrackedMutationFile -PathType Leaf) {
		$legacyTrackedText = [IO.File]::ReadAllText($legacyTrackedMutationFile)
		$cleanLegacyTrackedText = [Text.RegularExpressions.Regex]::Replace($legacyTrackedText,
			'(\r?\n# stage13-history-mutated)+\z', '')
		if ($cleanLegacyTrackedText -ne $legacyTrackedText) {
			[IO.File]::WriteAllText($legacyTrackedMutationFile, $cleanLegacyTrackedText)
		}
	}
	# Use a dedicated LocalHistory-tracked file that Copperbench itself never rewrites.
	# Product-managed files such as the .mcreator document are normalized/saved during
	# workspace reopen and therefore cannot serve as a byte-stable restore sentinel.
	[IO.File]::WriteAllText($trackedFile, $trackedBaselineContent)
	$result.trackedFileBaselineSha256 = (Get-FileHash -LiteralPath $trackedFile -Algorithm SHA256).Hash.ToLowerInvariant()
	$beforeHead = Get-HistoryHeadId
	$result.checkpointHeadBefore = $beforeHead

	$first = Launch-Workspace
	$result.firstLaunchObserved = $true
	$result.firstClientWidth = $first.size.width
	$result.firstClientHeight = $first.size.height
	# The AWT frame appears before React/JCEF is necessarily paint-ready. Wait
	# for the real client area to contain non-placeholder color before input.
	$result.firstRenderReadyMillis = Wait-WorkbenchRendered -Handle $first.handle
	$applicationLog = Join-Path $env:USERPROFILE '.copperbench\logs\mcreator.log'
	if (Test-Path -LiteralPath $applicationLog -PathType Leaf) {
		$jcefLines = @(Get-Content -LiteralPath $applicationLog -Tail 300 -ErrorAction SilentlyContinue |
			Select-String -Pattern 'Using JCEF software rendering|Initializing JCEF in (OSR|WR)' |
			ForEach-Object { $_.Line })
		$result.jcefStartupLog = @($jcefLines | Select-Object -Last 6)
		$result.softwareDisplayFallbackObserved = [bool]($jcefLines |
			Where-Object { $_ -match 'Using JCEF software rendering because Windows exposes only' })
		if ($jcefLines | Where-Object { $_ -match 'Initializing JCEF in OSR' }) {
			$result.jcefMode = 'OSR'
		} elseif ($jcefLines | Where-Object { $_ -match 'Initializing JCEF in WR' }) {
			$result.jcefMode = 'WR'
		}
	}
	$historyTarget = Describe-ClientPoint -Handle $first.handle -X $result.historyClickX -Y $result.historyClickY
	$result.historyScreenX = $historyTarget.screenX
	$result.historyScreenY = $historyTarget.screenY
	$result.historyWindowAtPoint = $historyTarget.window
	$result.historyWindowClassAtPoint = $historyTarget.className
	$result.historyProcessAtPoint = $historyTarget.processId
	# Maximized product: History is the seventh nav item. The coordinates derive from the
	# stable 38px titlebar, 190px NavRail, 34px item height, and 4px item gap.
	Click-Client -Handle $first.handle -X $result.historyClickX -Y $result.historyClickY
	# History header is 68px tall; create is the right-aligned header action.
	$result.createClickX = $first.size.width - 88
	Click-Client -Handle $first.handle -X $result.createClickX -Y $result.createClickY
	Start-Sleep -Seconds 1
	Capture-Screen -Path $CreateScreenshotPath
	$result.createDialogScreenshotCaptured = Test-Path -LiteralPath $CreateScreenshotPath -PathType Leaf
	# The real installed JCEF screenshot pins the 520px modal card to x=348..866
	# on the fixed 1024x768 G7 acceptance desktop. Its label input spans
	# x=369..844, y=391..420. Use the measured center instead of assuming the
	# overlay is centered across the full native client (the NavRail shifts it).
	$result.createInputClickX = [int](($first.size.width + 190) / 2)
	$result.createInputClickY = 406
	Click-Client -Handle $first.handle -X $result.createInputClickX -Y $result.createInputClickY
	Send-KeySequence $checkpointLabel
	# The same screenshot resolves the disabled primary action to
	# x=773..845, y=457..488. After typing, click its measured center.
	$result.createConfirmClickX = 809
	$result.createConfirmClickY = 473
	Click-Client -Handle $first.handle -X $result.createConfirmClickX -Y $result.createConfirmClickY
	Start-Sleep -Seconds 2
	$afterHead = Get-HistoryHeadId
	$result.checkpointHeadAfter = $afterHead
	if ([string]::IsNullOrWhiteSpace($afterHead) -or $afterHead -eq $beforeHead) {
		throw 'History recovery point was not created by the installed product UI.'
	}
	$result.checkpointCreated = $true
	$result.checkpointHead = $afterHead

	Close-WorkspaceWindow -Handle $first.handle
	Wait-ProductExit
	[IO.File]::AppendAllText($trackedFile, $trackedMutationMarker)
	$result.trackedFileMutatedSha256 = (Get-FileHash -LiteralPath $trackedFile -Algorithm SHA256).Hash.ToLowerInvariant()
	$result.mutatedBeforeRelaunch = ($result.trackedFileMutatedSha256 -ne $result.trackedFileBaselineSha256)
	if (-not $result.mutatedBeforeRelaunch) {
		throw 'Tracked History acceptance file did not change before relaunch.'
	}

	$second = Launch-Workspace
	$result.secondLaunchObserved = $true
	$result.trackedFileSha256AfterSecondLaunch = (Get-FileHash -LiteralPath $trackedFile -Algorithm SHA256).Hash.ToLowerInvariant()
	if ($result.trackedFileSha256AfterSecondLaunch -ne $result.trackedFileMutatedSha256) {
		throw 'Tracked History acceptance file changed during product relaunch; refusing to infer current-point semantics from it.'
	}
	$result.secondRenderReadyMillis = Wait-WorkbenchRendered -Handle $second.handle
	Click-Client -Handle $second.handle -X 95 -Y 320
	Start-Sleep -Seconds 1
	# The installed 1024x768 History screenshot resolves the restore action to
	# x=902..1007, y=123..152. Click the measured center instead of the lower
	# edge so JCEF receives the pointer on the button body.
	Click-Client -Handle $second.handle -X 955 -Y 138
	$result.restoreDialogReached = $true
	$restoreConfirm = Wait-RestoreConfirmTarget -Handle $second.handle
	$result.restoreConfirmWaitMillis = $restoreConfirm.waitMillis
	$result.restoreConfirmClickX = $restoreConfirm.x
	$result.restoreConfirmClickY = $restoreConfirm.y
	$result.restoreConfirmRedSamples = $restoreConfirm.redSamples
	Capture-Screen -Path $BeforeScreenshotPath
	$result.beforeScreenshotCaptured = Test-Path -LiteralPath $BeforeScreenshotPath -PathType Leaf
	Click-Client -Handle $second.handle -X $restoreConfirm.x -Y $restoreConfirm.y

	$restoreDeadline = (Get-Date).AddSeconds(30)
	do {
		Start-Sleep -Milliseconds 500
		$currentTrackedFileSha256 = if (Test-Path -LiteralPath $trackedFile -PathType Leaf) {
			(Get-FileHash -LiteralPath $trackedFile -Algorithm SHA256).Hash.ToLowerInvariant()
		} else { $null }
		if ($currentTrackedFileSha256 -eq $result.trackedFileBaselineSha256) { break }
	} while ((Get-Date) -lt $restoreDeadline)
	$result.trackedFileRestoredSha256 = $currentTrackedFileSha256
	$result.trackedFileRestored = ($currentTrackedFileSha256 -eq $result.trackedFileBaselineSha256)
	Capture-Screen -Path $AfterScreenshotPath
	$result.afterScreenshotCaptured = Test-Path -LiteralPath $AfterScreenshotPath -PathType Leaf
	if (-not $result.trackedFileRestored) {
		throw "Tracked History file was not restored. Current SHA-256: $currentTrackedFileSha256"
	}

	Close-WorkspaceWindow -Handle $second.handle
	Wait-ProductExit

	$psi = [Diagnostics.ProcessStartInfo]::new()
	$psi.FileName = $launcher
	$psi.WorkingDirectory = $InstallDir
	$psi.UseShellExecute = $false
	$psi.RedirectStandardOutput = $true
	$psi.RedirectStandardError = $true
	$psi.CreateNoWindow = $true
	$psi.Arguments = "headless --workspace `"$WorkspaceFile`" validate"
	$validation = [Diagnostics.Process]::new()
	$validation.StartInfo = $psi
	[void]$validation.Start()
	$stdout = $validation.StandardOutput.ReadToEnd()
	$stderr = $validation.StandardError.ReadToEnd()
	if (-not $validation.WaitForExit(180000)) {
		$validation.Kill($true)
		throw 'Installed headless validate timed out.'
	}
	$result.validateExitCode = $validation.ExitCode
	$result.validateStdout = $stdout.Trim()
	$result.validateStderr = $stderr.Trim()
	try {
		$validationJson = $stdout.Trim() | ConvertFrom-Json
		$result.validateStatus = $validationJson.status
		$result.validateOperation = $validationJson.operation
	} catch {
		$result.validateStatus = 'invalid_json'
	}

	$result.passed = ($result.firstLaunchObserved -and $result.checkpointCreated -and
		$result.mutatedBeforeRelaunch -and $result.secondLaunchObserved -and $result.restoreDialogReached -and
		$result.trackedFileRestored -and $result.validateExitCode -eq 0 -and $result.validateStatus -eq 'succeeded' -and
		$result.validateOperation -eq 'validate_workspace')
} catch {
	$result.error = $_.Exception.Message
	$result.errorType = $_.Exception.GetType().FullName
	$cefLog = Join-Path $env:USERPROFILE '.copperbench\cef_log.txt'
	if (Test-Path -LiteralPath $cefLog -PathType Leaf) {
		$result.cefLogTail = @(Get-Content -LiteralPath $cefLog -Tail 120 -ErrorAction SilentlyContinue)
	}
	try { Capture-Screen -Path $AfterScreenshotPath } catch { }
	Set-Content -LiteralPath $ErrorPath -Value ($_ | Out-String) -Encoding UTF8
} finally {
	$result.finishedAt = (Get-Date).ToString('o')
	$result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $ResultPath -Encoding UTF8
}
'@

$session = $null
try {
	$vm = Get-VM -Name $VmName -ErrorAction Stop
	if ($vm.State -ne 'Running') {
		throw "VM must already be running for the History gate: $VmName ($($vm.State))."
	}
	$session = New-GuestSession -TargetVm $VmName -Credential $credential
	Invoke-Command -Session $session -ArgumentList $GuestRoot, $guestScriptPath, $guestResultPath,
		$guestCreateScreenshotPath, $guestBeforeScreenshotPath, $guestAfterScreenshotPath, $guestErrorPath, $guestScript -ScriptBlock {
		param($TargetRoot, $ScriptPath, $ResultPath, $CreateScreenshotPath, $BeforeScreenshotPath, $AfterScreenshotPath, $ErrorPath, $ScriptContent)
		New-Item -ItemType Directory -Force -Path $TargetRoot | Out-Null
		Remove-Item -LiteralPath $ResultPath, $CreateScreenshotPath, $BeforeScreenshotPath, $AfterScreenshotPath, $ErrorPath -Force -ErrorAction SilentlyContinue
		Set-Content -LiteralPath $ScriptPath -Value $ScriptContent -Encoding UTF8
	}

	Invoke-Command -Session $session -ArgumentList $taskName, $guestScriptPath, $GuestUser, $InstallDir, $WorkspaceFile, $GuestRoot -ScriptBlock {
		param($TaskName, $ScriptPath, $TargetUser, $TargetInstallDir, $TargetWorkspaceFile, $TargetRoot)
		Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
		Start-Sleep -Milliseconds 500
		Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
		$arguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$ScriptPath`" -InstallDir `"$TargetInstallDir`" -WorkspaceFile `"$TargetWorkspaceFile`" -GuestRoot `"$TargetRoot`""
		$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
		$principal = New-ScheduledTaskPrincipal -UserId $TargetUser -LogonType Interactive -RunLevel Highest
		Register-ScheduledTask -TaskName $TaskName -Action $action -Principal $principal -Force | Out-Null
		Start-ScheduledTask -TaskName $TaskName
	}

	$deadline = (Get-Date).AddMinutes(8)
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
	} while (-not $guestState.resultPresent -and
		-not ($guestState.errorPresent -and $guestState.taskState -ne 'Running') -and
		(Get-Date) -lt $deadline)

	if (-not $guestState.resultPresent) {
		if ($guestState.errorPresent) {
			Copy-Item -LiteralPath $guestErrorPath -Destination $hostErrorPath -FromSession $session -Force
			throw "History gate wrote an error before producing a result. taskState=$($guestState.taskState) lastTaskResult=$($guestState.lastTaskResult) error=$hostErrorPath"
		}
		throw "Timed out waiting for History gate result. taskState=$($guestState.taskState) lastTaskResult=$($guestState.lastTaskResult)"
	}

	Copy-Item -LiteralPath $guestResultPath -Destination $hostResultPath -FromSession $session -Force
	foreach ($pair in @(
		@($guestCreateScreenshotPath, $hostCreateScreenshotPath),
		@($guestBeforeScreenshotPath, $hostBeforeScreenshotPath),
		@($guestAfterScreenshotPath, $hostAfterScreenshotPath)
	)) {
		if (Invoke-Command -Session $session -ArgumentList $pair[0] -ScriptBlock { param($Path) Test-Path -LiteralPath $Path -PathType Leaf }) {
			Copy-Item -LiteralPath $pair[0] -Destination $pair[1] -FromSession $session -Force
		}
	}
	if ($guestState.errorPresent) {
		Copy-Item -LiteralPath $guestErrorPath -Destination $hostErrorPath -FromSession $session -Force
	} else {
		Remove-Item -LiteralPath $hostErrorPath -Force -ErrorAction SilentlyContinue
	}

	$result = Get-Content -LiteralPath $hostResultPath -Raw | ConvertFrom-Json
	Write-Output ("passed=" + $result.passed)
	Write-Output ("checkpointCreated=" + $result.checkpointCreated)
	Write-Output ("restoreDialogReached=" + $result.restoreDialogReached)
	Write-Output ("trackedFileRestored=" + $result.trackedFileRestored)
	Write-Output ("validateExitCode=" + $result.validateExitCode)
	Write-Output ("validateStatus=" + $result.validateStatus)
	Write-Output ("evidence=" + $hostResultPath)
	if ($result.error) { Write-Output ("error=" + $result.error) }
	if (-not $result.passed) { exit 1 }
} finally {
	if ($null -ne $session) {
		try { Invoke-Command -Session $session -ArgumentList $taskName -ScriptBlock { param($TaskName) Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue } } catch { }
		Remove-PSSession $session -ErrorAction SilentlyContinue
	}
}
