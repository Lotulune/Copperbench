[CmdletBinding()]
param(
	[string]$VmName = 'Copperbench-G7',
	[string]$InstallerPath = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'build\export\Copperbench 0.1.0 Windows 64bit.exe'),
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$GuestUser = 'g7admin',
	[string]$PasswordFile = 'D:\Hyper-V\G7\g7admin.password.txt',
	[string]$InstallDir = 'C:\Copperbench-Stage15-WindowsRegression',
	[string]$GuestRoot = 'C:\Temp\Copperbench-Stage15-WindowsRegression',
	[string]$PreservedWorkspace = 'C:\Users\g7admin\MCreatorWorkspaces\p0fabricfinald0d\p0fabricfinald0d.mcreator',
	[string]$SourceDeltaSha256 = '',
	[switch]$SkipBaselineGate,
	[string]$BaselineEvidencePath = '',
	[switch]$CollectExistingGuestTask,
	[switch]$InspectExistingGuestTask,
	[switch]$StopFailedExistingGuestTask,
	[switch]$CleanupExistingGuestWindowsOnly
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

if (-not (Test-Path -LiteralPath $InstallerPath -PathType Leaf)) { throw "Installer not found: $InstallerPath" }
if (-not (Test-Path -LiteralPath $PasswordFile -PathType Leaf)) { throw "Guest password file not found: $PasswordFile" }
if ($SourceDeltaSha256 -and $SourceDeltaSha256 -notmatch '^[0-9a-fA-F]{64}$') {
	throw '-SourceDeltaSha256 must be a full SHA-256 when supplied.'
}

$candidateHead = (git -C $RepositoryRoot rev-parse HEAD).Trim()
$candidateSha256 = (Get-FileHash -LiteralPath $InstallerPath -Algorithm SHA256).Hash.ToLowerInvariant()
$sourceDelta = if ($SourceDeltaSha256) { $SourceDeltaSha256.ToLowerInvariant() } else { $null }
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage15\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$hostResultPath = Join-Path $evidenceDir 'windows-product-regression-clean-windows11.json'
$hostErrorPath = Join-Path $evidenceDir 'windows-product-regression-clean-windows11-error.txt'
Remove-Item -LiteralPath $hostErrorPath -Force -ErrorAction SilentlyContinue

$baseline = $null
if ($SkipBaselineGate) {
	if (-not $BaselineEvidencePath) { throw '-BaselineEvidencePath is required with -SkipBaselineGate.' }
	if (-not (Test-Path -LiteralPath $BaselineEvidencePath -PathType Leaf)) {
		throw "Baseline evidence not found: $BaselineEvidencePath"
	}
	$baseline = Get-Content -LiteralPath $BaselineEvidencePath -Raw | ConvertFrom-Json
	if (-not $baseline.passed) { throw 'Supplied baseline evidence did not pass.' }
	if ([string]$baseline.candidateSourceHead -ne $candidateHead) { throw 'Baseline source HEAD does not match current candidate.' }
	if ([string]$baseline.candidateInstallerSha256 -ne $candidateSha256) { throw 'Baseline installer SHA-256 does not match current candidate.' }
	if ($sourceDelta -and [string]$baseline.candidateSourceDeltaSha256 -ne $sourceDelta) {
		throw 'Baseline source-delta SHA-256 does not match the requested Stage 15 delta.'
	}
} else {
	$fixture = Join-Path $RepositoryRoot 'build\stage13-diagnostics-fixture\valid\stage13_diagnostics.mcreator'
	if (-not (Test-Path -LiteralPath $fixture -PathType Leaf)) {
		throw 'Stage 14 baseline fixture is missing. Run Stage13DiagnosticsFixtureExportTest with COPPERBENCH_STAGE13_DIAGNOSTICS_FIXTURE=true first.'
	}
	$baselineScript = Join-Path $PSScriptRoot 'Invoke-Stage14SourceIntegrityGuestGate.ps1'
	$baselineArgs = @(
		'-NoProfile', '-File', $baselineScript,
		'-VmName', $VmName,
		'-InstallerPath', $InstallerPath,
		'-RepositoryRoot', $RepositoryRoot,
		'-GuestUser', $GuestUser,
		'-PasswordFile', $PasswordFile,
		'-InstallDir', $InstallDir,
		'-GuestRoot', ($GuestRoot + '-baseline')
	)
	if ($sourceDelta) { $baselineArgs += @('-SourceDeltaSha256', $sourceDelta) }
	& pwsh @baselineArgs
	if ($LASTEXITCODE -ne 0) { throw "Stage 14 installed-product baseline gate failed with exit code $LASTEXITCODE." }
	$BaselineEvidencePath = Join-Path $RepositoryRoot "evidence\stage14\$stamp\source-integrity-clean-windows11.json"
	$baseline = Get-Content -LiteralPath $BaselineEvidencePath -Raw | ConvertFrom-Json
	if (-not $baseline.passed) { throw 'Stage 14 installed-product baseline evidence did not pass.' }
}

$plainPassword = (Get-Content -LiteralPath $PasswordFile -Raw).Trim()
$credential = [pscredential]::new($GuestUser, (ConvertTo-SecureString $plainPassword -AsPlainText -Force))

function New-GuestSession {
	param([string]$TargetVm, [pscredential]$Credential, [int]$Attempts = 60)
	for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
		try { return New-PSSession -VMName $TargetVm -Credential $Credential -ErrorAction Stop }
		catch { Start-Sleep -Seconds 2 }
	}
	throw "PowerShell Direct did not become available for $TargetVm."
}

if ($InspectExistingGuestTask) {
	$inspectSession = $null
	try {
		$inspectSession = New-GuestSession $VmName $credential
		$inspection = Invoke-Command -Session $inspectSession -ArgumentList $GuestRoot -ScriptBlock {
			param($Root)
			$taskName = 'Copperbench-Stage15-Windows-Product-Regression'
			$task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
			if (-not $task) { return [pscustomobject]@{ taskPresent=$false } }
			$info = Get-ScheduledTaskInfo -TaskName $taskName
			$resultPath = Join-Path $Root 'windows-product-regression-clean-windows11.json'
			$errorPath = Join-Path $Root 'windows-product-regression-clean-windows11-error.txt'
			$diagnosticStdout = Join-Path $Root 'windows-build-diagnostic.stdout.log'
			$diagnosticStderr = Join-Path $Root 'windows-build-diagnostic.stderr.log'
			$relevantProcesses = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
				$_.Name -in @('cmd.exe', 'java.exe', 'javaw.exe', 'copperbench.exe') -and
				([string]$_.ExecutablePath -like 'C:\\Copperbench-Stage15-WindowsRegression*' -or
				 [string]$_.CommandLine -like "*$Root*")
			} | ForEach-Object {
				$process = Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue
				[pscustomobject]@{
					ProcessId=[int]$_.ProcessId
					ParentProcessId=[int]$_.ParentProcessId
					Name=[string]$_.Name
					ExecutablePath=[string]$_.ExecutablePath
					StartTime=if ($process) { try { $process.StartTime } catch { $null } } else { $null }
					MainWindowHandle=if ($process) { try { [int64]$process.MainWindowHandle } catch { 0 } } else { 0 }
				}
			})
			$latestWorkspace = Get-ChildItem -LiteralPath $Root -Directory -Filter 'workspace-*' -ErrorAction SilentlyContinue |
				Sort-Object LastWriteTime -Descending | Select-Object -First 1
			$workspaceProbe = if ($latestWorkspace) {
				[pscustomobject]@{
					path=$latestWorkspace.FullName
					lastWriteTime=$latestWorkspace.LastWriteTime
					descriptorExists=Test-Path -LiteralPath (Join-Path $latestWorkspace.FullName '.copperbench\mcp-connection.json') -PathType Leaf
					buildLibCount=@(Get-ChildItem -LiteralPath (Join-Path $latestWorkspace.FullName 'build\libs') -Filter '*.jar' -File -ErrorAction SilentlyContinue).Count
					runDirExists=Test-Path -LiteralPath (Join-Path $latestWorkspace.FullName 'run') -PathType Container
				}
			} else { $null }
			[pscustomobject]@{
				taskPresent=$true
				state=[string]$task.State
				lastRunTime=$info.LastRunTime
				lastTaskResult=$info.LastTaskResult
				resultExists=Test-Path -LiteralPath $resultPath -PathType Leaf
				errorExists=Test-Path -LiteralPath $errorPath -PathType Leaf
				errorTail=if (Test-Path -LiteralPath $errorPath -PathType Leaf) { @(Get-Content -LiteralPath $errorPath -Tail 20) } else { @() }
				buildDiagnosticStdoutTail=if (Test-Path -LiteralPath $diagnosticStdout -PathType Leaf) { @(Get-Content -LiteralPath $diagnosticStdout -Tail 80) } else { @() }
				buildDiagnosticStderrTail=if (Test-Path -LiteralPath $diagnosticStderr -PathType Leaf) { @(Get-Content -LiteralPath $diagnosticStderr -Tail 80) } else { @() }
				latestWorkspace=$workspaceProbe
				relevantProcesses=$relevantProcesses
			}
		}
		$inspection | ConvertTo-Json -Depth 10
		return
	} finally {
		if ($inspectSession) { Remove-PSSession $inspectSession -ErrorAction SilentlyContinue }
	}
}

if ($StopFailedExistingGuestTask) {
	$stopSession = $null
	try {
		$stopSession = New-GuestSession $VmName $credential
		$stopped = Invoke-Command -Session $stopSession -ArgumentList $GuestRoot -ScriptBlock {
			param($Root)
			$taskName = 'Copperbench-Stage15-Windows-Product-Regression'
			$task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
			if (-not $task) { throw 'No Stage 15 Windows product-regression guest task exists.' }
			$errorPath = Join-Path $Root 'windows-product-regression-clean-windows11-error.txt'
			$resultPath = Join-Path $Root 'windows-product-regression-clean-windows11.json'
			if ([string]$task.State -ne 'Running') { throw 'The Stage 15 Windows product-regression guest task is not running.' }
			if (-not (Test-Path -LiteralPath $errorPath -PathType Leaf)) { throw 'Refusing to stop a running acceptance task without failure evidence.' }
			if (Test-Path -LiteralPath $resultPath -PathType Leaf) { throw 'Refusing to stop a running acceptance task that already has a stable result.' }
			Stop-ScheduledTask -TaskName $taskName
			$deadline = (Get-Date).AddSeconds(15)
			do {
				Start-Sleep -Milliseconds 250
				$task = Get-ScheduledTask -TaskName $taskName -ErrorAction Stop
			} while ([string]$task.State -eq 'Running' -and (Get-Date) -lt $deadline)
			[pscustomobject]@{
				stopped=([string]$task.State -ne 'Running')
				finalState=[string]$task.State
				failureEvidencePreserved=$true
				resultWasAbsent=$true
			}
		}
		$stopped | ConvertTo-Json -Depth 5
		return
	} finally {
		if ($stopSession) { Remove-PSSession $stopSession -ErrorAction SilentlyContinue }
	}
}

if ($CleanupExistingGuestWindowsOnly) {
	$cleanupSession = $null
	try {
		$cleanupSession = New-GuestSession $VmName $credential
		$cleanup = Invoke-Command -Session $cleanupSession -ArgumentList $InstallDir -ScriptBlock {
			param($TargetInstallDir)
			Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Stage15CleanupInput {
  public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumWindowsProc callback, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
  [DllImport("user32.dll", SetLastError=true)] public static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
}
"@
			$roots = @(
				(Join-Path $TargetInstallDir 'jdk\bin').ToLowerInvariant() + '\',
				(Join-Path $TargetInstallDir 'jdk21\bin').ToLowerInvariant() + '\'
			)
			$closed = [System.Collections.Generic.List[object]]::new()
			$callback = [Stage15CleanupInput+EnumWindowsProc]{
				param([IntPtr]$window, [IntPtr]$unused)
				if (-not [Stage15CleanupInput]::IsWindowVisible($window)) { return $true }
				[uint32]$windowProcessId = 0
				$null = [Stage15CleanupInput]::GetWindowThreadProcessId($window, [ref]$windowProcessId)
				if ($windowProcessId -eq 0) { return $true }
				$process = Get-Process -Id $windowProcessId -ErrorAction SilentlyContinue
				if (-not $process) { return $true }
				try { $path = [string]$process.Path } catch { return $true }
				$normalized = $path.ToLowerInvariant()
				if (-not ($roots | Where-Object { $normalized.StartsWith($_) })) { return $true }
				$posted = [Stage15CleanupInput]::PostMessage($window, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero)
				$closed.Add([pscustomobject]@{ processId=$windowProcessId; executable=$path; windowHandle=$window.ToInt64(); wmClosePosted=$posted })
				return $true
			}
			$null = [Stage15CleanupInput]::EnumWindows($callback, [IntPtr]::Zero)
			Start-Sleep -Seconds 3
			[pscustomobject]@{ closeMethod='WM_CLOSE'; processKilled=$false; windows=@($closed); count=$closed.Count }
		}
		Write-Output ('cleanupOnly=true')
		Write-Output ('cleanup=' + ($cleanup | ConvertTo-Json -Depth 8 -Compress))
		return
	} finally {
		if ($cleanupSession) { Remove-PSSession $cleanupSession -ErrorAction SilentlyContinue }
	}
}

$guestScript = @'
param(
	[Parameter(Mandatory = $true)][string]$InstallDir,
	[Parameter(Mandatory = $true)][string]$SourceWorkspace,
	[Parameter(Mandatory = $true)][string]$GuestRoot,
	[Parameter(Mandatory = $true)][string]$CandidateHead,
	[Parameter(Mandatory = $true)][string]$CandidateSha256,
	[string]$SourceDeltaSha256
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class Stage15WindowsInput {
  [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left, Top, Right, Bottom; }
  public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] private static extern bool GetWindowRect(IntPtr hWnd, out Rect rect);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] private static extern IntPtr SendMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
  [DllImport("user32.dll")] private static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] private static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumWindowsProc callback, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool EnumChildWindows(IntPtr parent, EnumWindowsProc callback, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetClassName(IntPtr hWnd, StringBuilder text, int maxCount);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int maxCount);
  [DllImport("user32.dll", SetLastError=true)] public static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
  [DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr OpenProcess(uint desiredAccess, bool inheritHandle, uint processId);
  [DllImport("kernel32.dll", SetLastError=true)] public static extern bool GetExitCodeProcess(IntPtr processHandle, out uint exitCode);
  [DllImport("kernel32.dll", SetLastError=true)] public static extern bool CloseHandle(IntPtr handle);
  public static int[] ReadWindowRect(IntPtr hWnd) {
    Rect rect;
    if (!GetWindowRect(hWnd, out rect)) throw new InvalidOperationException("GetWindowRect failed");
    return new[] { rect.Left, rect.Top, rect.Right, rect.Bottom };
  }
  public static int HitTest(IntPtr hWnd, int screenX, int screenY) {
    long packed = ((long)(screenY & 0xffff) << 16) | (uint)(screenX & 0xffff);
    return unchecked((int)SendMessage(hWnd, 0x0084, IntPtr.Zero, new IntPtr(packed)).ToInt64());
  }
  public static bool ClickScreenPoint(int screenX, int screenY) {
    if (!SetCursorPos(screenX, screenY)) return false;
    mouse_event(0x0002, 0, 0, 0, UIntPtr.Zero);
    mouse_event(0x0004, 0, 0, 0, UIntPtr.Zero);
    return true;
  }
}
"@

$ResultPath = Join-Path $GuestRoot 'windows-product-regression-clean-windows11.json'
$ErrorPath = Join-Path $GuestRoot 'windows-product-regression-clean-windows11-error.txt'
$launcher = Join-Path $InstallDir 'copperbench.exe'
$expectedRuntimeJava = (Join-Path $InstallDir 'jdk\bin\java.exe')
$expectedJdk21Java = (Join-Path $InstallDir 'jdk21\bin\java.exe')
$workspaceRoot = Join-Path $GuestRoot ('workspace-' + [guid]::NewGuid().ToString('N'))
$descriptorPath = Join-Path $workspaceRoot '.copperbench\mcp-connection.json'
$token = $null
$url = $null
$workspaceId = $null
$sessionId = $null
$desktopProcess = $null
$steps = [System.Collections.Generic.List[object]]::new()
$script:callId = 2
$allTaskLogs = [System.Collections.Generic.List[string]]::new()
$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'stage15-windows-product-regression-clean-windows11'
	candidateSourceHead = $CandidateHead
	candidateSourceDirty = -not [string]::IsNullOrWhiteSpace($SourceDeltaSha256)
	candidateSourceDeltaSha256 = if ($SourceDeltaSha256) { $SourceDeltaSha256 } else { $null }
	candidateInstallerSha256 = $CandidateSha256
	sourceWorkspace = $SourceWorkspace
	startedAt = (Get-Date).ToString('o')
	passed = $false
	steps = $steps
}

function Assert-Gate([bool]$Condition, [string]$Message) {
	if (-not $Condition) { throw $Message }
}

function Add-Step([string]$Name, [hashtable]$Facts = @{}) {
	$entry = [ordered]@{ name = $Name; passed = $true }
	foreach ($key in $Facts.Keys) { $entry[$key] = $Facts[$key] }
	$steps.Add([pscustomobject]$entry)
}

function Get-TitlebarClientRegions([IntPtr]$WindowHandle) {
	$rect = [Stage15WindowsInput]::ReadWindowRect($WindowHandle)
	# The production title bar is 38 CSS px high. Scanning 19 native px below the
	# top edge remains inside every 32 CSS px action at the supported DPI range,
	# while staying clear of the resize border. The native WM_NCHITTEST result is
	# driven by the exact DOM regions reported through WindowChromeSnapshot.
	$scanY = [int]$rect[1] + 19
	$regions = [System.Collections.Generic.List[object]]::new()
	$insideClient = $false
	$startX = 0
	for ($x = [int]$rect[0] + 8; $x -lt [int]$rect[2] - 8; $x++) {
		$hit = [Stage15WindowsInput]::HitTest($WindowHandle, $x, $scanY)
		if ($hit -eq 1) {
			if (-not $insideClient) {
				$insideClient = $true
				$startX = $x
			}
			continue
		}
		if ($insideClient) {
			$endX = $x - 1
			if (($endX - $startX + 1) -ge 20) {
				$regions.Add([pscustomobject]@{ left=$startX; right=$endX; top=$scanY; width=($endX - $startX + 1) })
			}
			$insideClient = $false
		}
	}
	if ($insideClient) {
		$endX = [int]$rect[2] - 9
		if (($endX - $startX + 1) -ge 20) {
			$regions.Add([pscustomobject]@{ left=$startX; right=$endX; top=$scanY; width=($endX - $startX + 1) })
		}
	}
	[pscustomobject]@{ windowLeft=[int]$rect[0]; windowRight=[int]$rect[2]; scanY=$scanY; regions=@($regions) }
}

function Resolve-TitlebarNativeAction([IntPtr]$WindowHandle,
		[ValidateSet('generate','build','runClient')][string]$Action, [int]$TimeoutSeconds = 20) {
	# FramelessTitlebar reports the six center actions to the native host in DOM
	# order: generate, build, run-client, run-server, datagen, gametest. JCEF on
	# the clean Windows gate does not expose those Chromium descendants through
	# UI Automation, so the acceptance harness intentionally resolves the real
	# production WindowChromeSnapshot through WM_NCHITTEST instead.
	$regionIndex = switch ($Action) {
		'generate' { 0 }
		'build' { 1 }
		'runClient' { 2 }
	}
	$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
	do {
		$scan = Get-TitlebarClientRegions $WindowHandle
		$regions = @($scan.regions)
		if ($regions.Count -ge 6) {
			$region = $regions[$regionIndex]
			$x = [int][Math]::Round(([double]$region.left + [double]$region.right) / 2.0)
			$y = [int]$scan.scanY
			if ([Stage15WindowsInput]::HitTest($WindowHandle, $x, $y) -eq 1) {
				return [pscustomobject]@{
					action=$Action; x=$x; y=$y; left=[int]$region.left; right=[int]$region.right;
					width=[int]$region.width; regionIndex=$regionIndex; regionCount=$regions.Count;
					resolution='native-titlebar-client-region-order'; resolutionRoot='wm-nchittest-window-chrome-snapshot'
				}
			}
		}
		Start-Sleep -Milliseconds 250
	} while ((Get-Date) -lt $deadline)
	throw "Could not resolve native title-bar action region '$Action' through WM_NCHITTEST."
}

function Invoke-TitlebarHitTestAction([IntPtr]$WindowHandle, [ValidateSet('generate','build','runClient')][string]$Action) {
	$target = Resolve-TitlebarNativeAction $WindowHandle $Action
	$x = [int]$target.x
	$y = [int]$target.y
	$hit = [Stage15WindowsInput]::HitTest($WindowHandle, $x, $y)
	Assert-Gate ($hit -eq 1) "Resolved title-bar action center is not HTCLIENT: $Action hit=$hit"
	[Stage15WindowsInput]::SetForegroundWindow($WindowHandle) | Out-Null
	Start-Sleep -Milliseconds 150
	Assert-Gate ([Stage15WindowsInput]::ClickScreenPoint($x, $y)) "Could not deliver real mouse click to title-bar action: $Action"
	[pscustomobject]@{
		action=$Action; x=$x; y=$y; left=[int]$target.left; right=[int]$target.right; width=[int]$target.width;
		regionIndex=[int]$target.regionIndex; regionCount=[int]$target.regionCount;
		hitTest='HTCLIENT'; resolution=$target.resolution; resolutionRoot=$target.resolutionRoot
	}
}

function Get-ForegroundWindowIdentity {
	$handle = [Stage15WindowsInput]::GetForegroundWindow()
	$classBuilder = [Text.StringBuilder]::new(256)
	$textBuilder = [Text.StringBuilder]::new(512)
	if ($handle -ne [IntPtr]::Zero) {
		$null = [Stage15WindowsInput]::GetClassName($handle, $classBuilder, $classBuilder.Capacity)
		$null = [Stage15WindowsInput]::GetWindowText($handle, $textBuilder, $textBuilder.Capacity)
	}
	[pscustomobject]@{ handle=$handle; className=$classBuilder.ToString(); text=$textBuilder.ToString() }
}

function Find-ChromiumRendererWindow([IntPtr]$WindowHandle, [int]$BrowserProcessId) {
	$script:rendererWindow = [IntPtr]::Zero
	$callback = [Stage15WindowsInput+EnumWindowsProc]{
		param([IntPtr]$candidate, [IntPtr]$unused)
		$className = [Text.StringBuilder]::new(256)
		$null = [Stage15WindowsInput]::GetClassName($candidate, $className, $className.Capacity)
		if ($className.ToString() -ne 'Chrome_RenderWidgetHostHWND') { return $true }
		[uint32]$windowProcessId = 0
		$null = [Stage15WindowsInput]::GetWindowThreadProcessId($candidate, [ref]$windowProcessId)
		if ([int]$windowProcessId -ne $BrowserProcessId) { return $true }
		$script:rendererWindow = $candidate
		return $false
	}
	$null = [Stage15WindowsInput]::EnumChildWindows($WindowHandle, $callback, [IntPtr]::Zero)
	return $script:rendererWindow
}

function Invoke-McpUiButton([IntPtr]$WindowHandle, [string]$AccessibleName, [int]$TimeoutSeconds = 15) {
	[Stage15WindowsInput]::SetForegroundWindow($WindowHandle) | Out-Null
	[System.Windows.Forms.SendKeys]::SendWait('^+m')
	$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
	do {
		Start-Sleep -Milliseconds 250
		try {
			$renderer = Find-ChromiumRendererWindow $WindowHandle $desktopProcess.Id
			if ($renderer -eq [IntPtr]::Zero) { continue }
			$root = [System.Windows.Automation.AutomationElement]::FromHandle($renderer)
			if ($root) {
				try { $root.SetFocus() } catch [System.InvalidOperationException] { }
				$condition = New-Object System.Windows.Automation.PropertyCondition(
					[System.Windows.Automation.AutomationElement]::ControlTypeProperty,
					[System.Windows.Automation.ControlType]::Button)
				$buttons = $root.FindAll([System.Windows.Automation.TreeScope]::Subtree, $condition)
				foreach ($button in $buttons) {
					try {
						if ([string]$button.Current.Name -ne $AccessibleName -or -not $button.Current.IsEnabled) { continue }
						$invoke = $button.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern)
						if ($invoke) {
							$invoke.Invoke()
							return $true
						}
					} catch [System.Windows.Automation.ElementNotAvailableException] { }
				}
			}
		} catch [System.Windows.Automation.ElementNotAvailableException] { }
		catch [System.InvalidOperationException] { }
	} while ((Get-Date) -lt $deadline)
	return $false
}

function Wait-GeneratorSetupDialog([IntPtr]$WindowHandle, [int]$TimeoutSeconds = 480) {
	[Stage15WindowsInput]::SetForegroundWindow($WindowHandle) | Out-Null
	Start-Sleep -Milliseconds 500
	$started = Get-Date
	$deadline = $started.AddSeconds($TimeoutSeconds)
	$observed = $false
	do {
		$foreground = Get-ForegroundWindowIdentity
		$setupBlocking = $foreground.className -eq 'SunAwtDialog' -and $foreground.text -eq 'Workspace setup for selected generator'
		if ($setupBlocking) {
			$observed = $true
			Start-Sleep -Seconds 1
			continue
		}
		[Stage15WindowsInput]::SetForegroundWindow($WindowHandle) | Out-Null
		Start-Sleep -Milliseconds 750
		$foreground = Get-ForegroundWindowIdentity
		$setupBlocking = $foreground.className -eq 'SunAwtDialog' -and $foreground.text -eq 'Workspace setup for selected generator'
		if (-not $setupBlocking) { break }
	} while ((Get-Date) -lt $deadline)
	Assert-Gate (-not $setupBlocking) 'Workspace setup for selected generator did not finish within 8 minutes.'
	[pscustomobject]@{ observed=$observed; waitMs=[int]((Get-Date) - $started).TotalMilliseconds }
}

function Invoke-McpPost([object]$Payload, [string]$CurrentSessionId = $null, [int]$TimeoutSec = 60) {
	$headers = @{
		Accept = 'application/json, text/event-stream'
		Authorization = "Bearer $token"
		'X-Copperbench-Workspace' = $workspaceId
	}
	if ($CurrentSessionId) { $headers['mcp-session-id'] = $CurrentSessionId }
	$json = $Payload | ConvertTo-Json -Depth 60 -Compress
	Invoke-WebRequest -UseBasicParsing -Uri $url -Method Post -Headers $headers -ContentType 'application/json' -Body $json -TimeoutSec $TimeoutSec
}

function Convert-McpToolResult($Response) {
	$line = @($Response.Content -split "`r?`n" | Where-Object { $_.StartsWith('data: ') }) | Select-Object -First 1
	if (-not $line) { throw 'MCP response did not contain SSE data.' }
	$envelope = $line.Substring(6) | ConvertFrom-Json
	if ($null -eq $envelope.result -or $null -eq $envelope.result.content -or $envelope.result.content.Count -lt 1) {
		throw 'MCP tool response envelope is incomplete.'
	}
	$envelope.result.content[0].text | ConvertFrom-Json
}

function Call-Tool([string]$Name, [object]$Arguments) {
	$id = $script:callId
	$script:callId++
	$response = Invoke-McpPost ([ordered]@{
		jsonrpc='2.0'; id=$id; method='tools/call'; params=[ordered]@{ name=$Name; arguments=$Arguments }
	}) $sessionId
	Assert-Gate ($response.StatusCode -eq 200) "$Name returned HTTP $($response.StatusCode)."
	Convert-McpToolResult $response
}

function Read-Task([string]$TaskId, [int64]$AfterSequence = 0) {
	Call-Tool 'get_task' @{ taskId=$TaskId; afterLogSequence=$AfterSequence }
}

function Collect-Task([string]$TaskId, [int]$TimeoutSeconds) {
	$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
	$after = [int64]0
	do {
		$snapshot = Read-Task $TaskId $after
		foreach ($entry in @($snapshot.data.logs)) {
			if ($entry.text) { $allTaskLogs.Add([string]$entry.text) }
			if ($entry.sequence -is [ValueType]) { $after = [Math]::Max($after, [int64]$entry.sequence) }
		}
		$state = [string]$snapshot.data.task.state
		if ($state -in @('succeeded','failed','cancelled')) {
			return [pscustomobject]@{ state=$state; snapshot=$snapshot; after=$after }
		}
		Start-Sleep -Milliseconds 500
	} while ((Get-Date) -lt $deadline)
	throw "Task $TaskId did not reach a terminal state within $TimeoutSeconds seconds."
}

function Get-BundledRuntimeProcessIds {
	$expectedRoot = (Split-Path -Parent $expectedRuntimeJava).ToLowerInvariant() + '\'
	@(Get-Process java,javaw -ErrorAction SilentlyContinue | Where-Object {
		try {
			$path = [string]$_.Path
			-not [string]::IsNullOrWhiteSpace($path) -and $path.ToLowerInvariant().StartsWith($expectedRoot)
		} catch { $false }
	} | ForEach-Object { [int]$_.Id })
}

function Get-BundledRuntimeVisibleWindow([int[]]$ExcludedProcessIds = @()) {
	$expectedRoot = (Split-Path -Parent $expectedRuntimeJava).ToLowerInvariant() + '\'
	$found = [System.Collections.Generic.List[object]]::new()
	$callback = [Stage15WindowsInput+EnumWindowsProc]{
		param([IntPtr]$window, [IntPtr]$unused)
		if (-not [Stage15WindowsInput]::IsWindowVisible($window)) { return $true }
		[uint32]$windowProcessId = 0
		$null = [Stage15WindowsInput]::GetWindowThreadProcessId($window, [ref]$windowProcessId)
		if ($windowProcessId -eq 0) { return $true }
		if ([int]$windowProcessId -in $ExcludedProcessIds) { return $true }
		$process = Get-Process -Id $windowProcessId -ErrorAction SilentlyContinue
		if (-not $process) { return $true }
		try { $path = [string]$process.Path } catch { return $true }
		if ([string]::IsNullOrWhiteSpace($path) -or -not $path.ToLowerInvariant().StartsWith($expectedRoot)) { return $true }
		$title = [Text.StringBuilder]::new(512)
		$className = [Text.StringBuilder]::new(256)
		$null = [Stage15WindowsInput]::GetWindowText($window, $title, $title.Capacity)
		$null = [Stage15WindowsInput]::GetClassName($window, $className, $className.Capacity)
		$found.Add([pscustomobject]@{
			process=$process; handle=$window; title=$title.ToString(); className=$className.ToString(); executable=$path
		})
		return $false
	}
	$null = [Stage15WindowsInput]::EnumWindows($callback, [IntPtr]::Zero)
	$found | Select-Object -First 1
}

function Invoke-FrameButtonByName([IntPtr]$WindowHandle, [string]$Name, [int]$TimeoutSeconds = 30) {
	[Stage15WindowsInput]::SetForegroundWindow($WindowHandle) | Out-Null
	$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
	do {
		Start-Sleep -Milliseconds 250
		try {
			$root = [System.Windows.Automation.AutomationElement]::FromHandle($WindowHandle)
			if ($root) {
				$condition = New-Object System.Windows.Automation.PropertyCondition(
					[System.Windows.Automation.AutomationElement]::ControlTypeProperty,
					[System.Windows.Automation.ControlType]::Button)
				$buttons = $root.FindAll([System.Windows.Automation.TreeScope]::Subtree, $condition)
				foreach ($button in $buttons) {
					try {
						if ([string]$button.Current.Name -ne $Name -or -not $button.Current.IsEnabled -or $button.Current.IsOffscreen) { continue }
						$invoke = $button.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern)
						if ($invoke) { $invoke.Invoke(); return $true }
					} catch [System.Windows.Automation.ElementNotAvailableException] { }
				}
			}
		} catch [System.Windows.Automation.ElementNotAvailableException] { }
		catch [System.InvalidOperationException] { }
	} while ((Get-Date) -lt $deadline)
	return $false
}

function Get-ProcessIdsByName([string]$Name) {
	@(Get-CimInstance Win32_Process -Filter "Name = '$Name'" -ErrorAction SilentlyContinue | ForEach-Object { [int]$_.ProcessId })
}

function Open-ProcessExitHandle([int]$ProcessId) {
	$handle = [Stage15WindowsInput]::OpenProcess(0x00101000, $false, [uint32]$ProcessId)
	Assert-Gate ($handle -ne [IntPtr]::Zero) "Could not open native process handle for PID $ProcessId."
	$handle
}

function Read-ProcessExitCode([IntPtr]$ProcessHandle) {
	[uint32]$exitCode = 0
	Assert-Gate ([Stage15WindowsInput]::GetExitCodeProcess($ProcessHandle, [ref]$exitCode)) 'GetExitCodeProcess failed for an observed Gradle command.'
	[int]$exitCode
}

function Get-InstalledWorkspaceWindows {
	$expectedJavaw = (Join-Path $InstallDir 'jdk\bin\javaw.exe').ToLowerInvariant()
	$found = [System.Collections.Generic.List[object]]::new()
	$callback = [Stage15WindowsInput+EnumWindowsProc]{
		param([IntPtr]$window, [IntPtr]$unused)
		if (-not [Stage15WindowsInput]::IsWindowVisible($window)) { return $true }
		[uint32]$windowProcessId = 0
		$null = [Stage15WindowsInput]::GetWindowThreadProcessId($window, [ref]$windowProcessId)
		if ($windowProcessId -eq 0) { return $true }
		$process = Get-Process -Id $windowProcessId -ErrorAction SilentlyContinue
		if (-not $process) { return $true }
		try { $path = [string]$process.Path } catch { return $true }
		if ([string]::IsNullOrWhiteSpace($path) -or $path.ToLowerInvariant() -ne $expectedJavaw) { return $true }
		$title = [Text.StringBuilder]::new(512)
		$className = [Text.StringBuilder]::new(256)
		$null = [Stage15WindowsInput]::GetWindowText($window, $title, $title.Capacity)
		$null = [Stage15WindowsInput]::GetClassName($window, $className, $className.Capacity)
		$found.Add([pscustomobject]@{
			process=$process; handle=[int64]$window; title=$title.ToString(); className=$className.ToString(); executable=$path
		})
		return $true
	}
	$null = [Stage15WindowsInput]::EnumWindows($callback, [IntPtr]::Zero)
	@($found)
}

function Test-ProcessDescendsFrom([object]$ProcessInfo, [int]$AncestorProcessId) {
	$currentParent = [int]$ProcessInfo.ParentProcessId
	for ($depth = 0; $depth -lt 12 -and $currentParent -gt 0; $depth++) {
		if ($currentParent -eq $AncestorProcessId) { return $true }
		$parent = Get-CimInstance Win32_Process -Filter "ProcessId = $currentParent" -ErrorAction SilentlyContinue
		if (-not $parent) { return $false }
		$currentParent = [int]$parent.ParentProcessId
	}
	return $false
}

function Wait-NewGradleCommand([string]$TaskArgument, [int[]]$ExistingIds, [int]$ExpectedParentProcessId,
		[int]$TimeoutSeconds = 60) {
	$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
	$escapedTask = [regex]::Escape($TaskArgument)
	do {
		$candidates = @(Get-CimInstance Win32_Process -Filter "Name = 'cmd.exe'" -ErrorAction SilentlyContinue | Where-Object {
			([int]$_.ProcessId -notin $ExistingIds) -and
			(Test-ProcessDescendsFrom $_ $ExpectedParentProcessId) -and
			([string]$_.CommandLine -match '(?i)gradlew\.bat') -and
			([string]$_.CommandLine -match ("(?i)(?:^|\s)" + $escapedTask + "(?:\s|$)"))
		})
		if ($candidates.Count -gt 0) { return $candidates | Sort-Object ProcessId | Select-Object -First 1 }
		Start-Sleep -Milliseconds 200
	} while ((Get-Date) -lt $deadline)
	throw "No new Gradle cmd.exe for $TaskArgument appeared in the active Copperbench javaw process tree after the real title-bar action."
}

function Wait-BundledRuntimeJava([int[]]$ExistingIds, [int]$TimeoutSeconds = 60) {
	$expected = $expectedRuntimeJava.ToLowerInvariant()
	$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
	do {
		$candidates = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue | Where-Object {
			(-not [string]::IsNullOrWhiteSpace([string]$_.ExecutablePath)) -and
			([string]$_.ExecutablePath).ToLowerInvariant() -eq $expected
		})
		if ($candidates.Count -gt 0) {
			$candidate = $candidates | Sort-Object @{ Expression = { if ([int]$_.ProcessId -in $ExistingIds) { 1 } else { 0 } } }, ProcessId | Select-Object -First 1
			return [pscustomobject]@{
				ProcessId = [int]$candidate.ProcessId
				ExecutablePath = [string]$candidate.ExecutablePath
				CommandLine = [string]$candidate.CommandLine
				Reused = ([int]$candidate.ProcessId -in $ExistingIds)
			}
		}
		Start-Sleep -Milliseconds 200
	} while ((Get-Date) -lt $deadline)
	throw 'No Java process from the installed bundled Java 25 runtime was observable after the build action.'
}

try {
	Assert-Gate (Test-Path -LiteralPath $launcher -PathType Leaf) "Installed launcher missing: $launcher"
	Assert-Gate (Test-Path -LiteralPath $expectedRuntimeJava -PathType Leaf) "Installed Java 25 runtime missing: $expectedRuntimeJava"
	Assert-Gate (Test-Path -LiteralPath $expectedJdk21Java -PathType Leaf) "Installed Java 21 missing: $expectedJdk21Java"
	Assert-Gate (Test-Path -LiteralPath $SourceWorkspace -PathType Leaf) "Preserved Fabric 26.1.2 workspace missing: $SourceWorkspace"

	New-Item -ItemType Directory -Force -Path $workspaceRoot | Out-Null
	$sourceRoot = Split-Path -Parent $SourceWorkspace
	$transientTopLevel = @('.gradle', 'build', 'run', '.copperbench')
	Get-ChildItem -LiteralPath $sourceRoot -Force | Where-Object { $_.Name -notin $transientTopLevel } | ForEach-Object {
		Copy-Item -LiteralPath $_.FullName -Destination $workspaceRoot -Recurse -Force
	}
	$WorkspaceFile = Join-Path $workspaceRoot (Split-Path -Leaf $SourceWorkspace)
	Assert-Gate (Test-Path -LiteralPath $WorkspaceFile -PathType Leaf) 'Disposable Fabric 26.1.2 workspace copy was not created.'
	$result.disposableWorkspace = $WorkspaceFile
	Remove-Item -LiteralPath $descriptorPath -Force -ErrorAction SilentlyContinue
	Assert-Gate (-not (Test-Path -LiteralPath $descriptorPath -PathType Leaf)) 'Stale Desktop MCP descriptor remained in the disposable workspace before product launch.'
	foreach ($transient in $transientTopLevel) {
		Assert-Gate (-not (Test-Path -LiteralPath (Join-Path $workspaceRoot $transient))) "Transient source workspace directory was copied into the acceptance workspace: $transient"
	}

	$desktopJavaBefore = Get-ProcessIdsByName 'javaw.exe'
	$workspaceWindowsBefore = @(Get-InstalledWorkspaceWindows | ForEach-Object { [int64]$_.handle })
	$launcherProcess = Start-Process -FilePath $launcher -ArgumentList @('-workspace', $WorkspaceFile) -WorkingDirectory $InstallDir -PassThru
	$deadline = (Get-Date).AddSeconds(60)
	$workspaceWindow = $null
	do {
		Start-Sleep -Milliseconds 500
		$workspaceWindow = Get-InstalledWorkspaceWindows | Where-Object {
			([int64]$_.handle -notin $workspaceWindowsBefore) -and
			([string]$_.title -like '* - Copperbench*')
		} | Select-Object -First 1
	} while ($null -eq $workspaceWindow -and (Get-Date) -lt $deadline)
	Assert-Gate ($null -ne $workspaceWindow) 'A fresh installed Copperbench workspace window did not appear after single-instance launch forwarding.'
	$desktopProcess = $workspaceWindow.process
	$hwnd = [IntPtr]$workspaceWindow.handle
	[Stage15WindowsInput]::ShowWindowAsync($hwnd, 3) | Out-Null
	$setup = Wait-GeneratorSetupDialog $hwnd 480
	Add-Step 'installed_fabric_2612_workspace_open' @{
		generatorSetupObserved=[bool]$setup.observed; waitMs=[int]$setup.waitMs;
		launcherProcessId=[int]$launcherProcess.Id; desktopProcessId=[int]$desktopProcess.Id;
		workspaceWindowHandle=[int64]$workspaceWindow.handle; workspaceWindowTitle=[string]$workspaceWindow.title;
		freshWorkspaceWindow=$true; desktopProcessReused=([int]$desktopProcess.Id -in $desktopJavaBefore)
	}

	$descriptorDeadline = (Get-Date).AddSeconds(30)
	while (-not (Test-Path -LiteralPath $descriptorPath -PathType Leaf) -and (Get-Date) -lt $descriptorDeadline) { Start-Sleep -Milliseconds 300 }
	Assert-Gate (Test-Path -LiteralPath $descriptorPath -PathType Leaf) 'Desktop MCP descriptor did not appear.'
	$descriptor = Get-Content -LiteralPath $descriptorPath -Raw | ConvertFrom-Json
	Assert-Gate ([string]$descriptor.status -eq 'listening') 'Desktop MCP was not listening.'
	Assert-Gate (-not ($descriptor.PSObject.Properties.Name -contains 'token')) 'Desktop MCP descriptor persisted a bearer token.'
	Add-Step 'desktop_mcp_descriptor_boundary' @{ listening=$true; tokenPersisted=$false }

	$buildCmdBefore = Get-ProcessIdsByName 'cmd.exe'
	$buildJavaBefore = Get-ProcessIdsByName 'java.exe'
	$buildClick = Invoke-TitlebarHitTestAction $hwnd 'build'
	Add-Step 'titlebar_build_click' @{ launchSurface='native-window-chrome-click'; click=$buildClick }
	$buildCmd = Wait-NewGradleCommand 'build' $buildCmdBefore $desktopProcess.Id 60
	$buildCommandLine = [string]$buildCmd.CommandLine
	$buildJava = Wait-BundledRuntimeJava $buildJavaBefore 60
	$buildProcess = [Diagnostics.Process]::GetProcessById([int]$buildCmd.ProcessId)
	$buildNativeHandle = Open-ProcessExitHandle ([int]$buildCmd.ProcessId)
	try {
		Assert-Gate ($buildProcess.WaitForExit(1200000)) 'Installed Fabric 26.1.2 build did not finish within 20 minutes.'
		$buildExitCode = Read-ProcessExitCode $buildNativeHandle
	} finally {
		$null = [Stage15WindowsInput]::CloseHandle($buildNativeHandle)
	}
	$builtJars = @(Get-ChildItem -LiteralPath (Join-Path $workspaceRoot 'build\libs') -Filter '*.jar' -File -ErrorAction SilentlyContinue)
	if ($builtJars.Count -eq 0 -or [int]$buildExitCode -ne 0) {
		$previousJavaHome = $env:JAVA_HOME
		$diagnosticLines = @()
		$diagnosticExitCode = $null
		$diagnosticStdout = Join-Path $GuestRoot 'windows-build-diagnostic.stdout.log'
		$diagnosticStderr = Join-Path $GuestRoot 'windows-build-diagnostic.stderr.log'
		try {
			$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $expectedRuntimeJava)
			Remove-Item -LiteralPath $diagnosticStdout, $diagnosticStderr -Force -ErrorAction SilentlyContinue
			$diagnosticProcess = Start-Process -FilePath 'cmd.exe' -ArgumentList @('/d','/s','/c','gradlew.bat --no-daemon build --stacktrace') `
				-WorkingDirectory $workspaceRoot -RedirectStandardOutput $diagnosticStdout -RedirectStandardError $diagnosticStderr -PassThru -Wait
			$diagnosticExitCode = $diagnosticProcess.ExitCode
			$diagnosticLines = @(
				@(Get-Content -LiteralPath $diagnosticStdout -ErrorAction SilentlyContinue)
				@(Get-Content -LiteralPath $diagnosticStderr -ErrorAction SilentlyContinue)
			)
		} finally {
			$env:JAVA_HOME = $previousJavaHome
		}
		$result.buildFailureDiagnostic = [ordered]@{
			uiGradleProcessId=[int]$buildCmd.ProcessId
			uiGradleParentProcessId=[int]$buildCmd.ParentProcessId
			uiCommandLine=$buildCommandLine
			uiExitCode=$buildExitCode
			artifactCountBeforeReplay=$builtJars.Count
			directReplay=$true
			javaHome=(Split-Path -Parent (Split-Path -Parent $expectedRuntimeJava))
			exitCode=$diagnosticExitCode
			outputTail=@($diagnosticLines | Select-Object -Last 160)
		}
		throw "Installed Fabric 26.1.2 UI build failed acceptance (exitCode=$buildExitCode, artifactCount=$($builtJars.Count)); direct bundled-Java25 diagnostic replay was captured."
	}
	Assert-Gate ([int]$buildExitCode -eq 0) "Installed Fabric 26.1.2 build exited $buildExitCode."
	Assert-Gate (-not $buildCommandLine.ToLowerInvariant().Contains('jdk\jbr25_win_64')) 'Build command referenced obsolete source-layout JDK path.'
	Add-Step 'installed_fabric_2612_build' @{
		launchSurface='titlebar-build-native-window-chrome-click'; click=$buildClick;
		gradleProcessId=[int]$buildCmd.ProcessId; gradleParentProcessId=[int]$buildCmd.ParentProcessId; gradleCommandLine=$buildCommandLine;
		bundledRuntimeJavaRelease=25; bundledRuntimeProcessId=[int]$buildJava.ProcessId; bundledRuntimePath=[string]$buildJava.ExecutablePath;
		bundledRuntimeProcessReused=[bool]$buildJava.Reused; java21SidecarPresent=$true;
		exitCode=$buildExitCode; exitCodeObserved=$true; artifactCount=$builtJars.Count
	}

	$runCmdBefore = Get-ProcessIdsByName 'cmd.exe'
	$runRuntimeBefore = Get-BundledRuntimeProcessIds
	$runClick = Invoke-TitlebarHitTestAction $hwnd 'runClient'
	Add-Step 'titlebar_run_client_click' @{ launchSurface='native-window-chrome-click'; click=$runClick }
	$runCmd = Wait-NewGradleCommand 'runClient' $runCmdBefore $desktopProcess.Id 60
	$runCommandLine = [string]$runCmd.CommandLine
	Assert-Gate ($runCommandLine -match '(?i)--no-daemon') 'Installed runClient command did not preserve the historical --no-daemon lifecycle contract.'
	Assert-Gate (-not $runCommandLine.ToLowerInvariant().Contains('jdk\jbr25_win_64')) 'runClient command referenced obsolete source-layout JDK path.'
	$runProcess = [Diagnostics.Process]::GetProcessById([int]$runCmd.ProcessId)
	$runNativeHandle = Open-ProcessExitHandle ([int]$runCmd.ProcessId)
	$runDeadline = (Get-Date).AddMinutes(10)
	$minecraft = $null
	do {
		Assert-Gate (-not $runProcess.HasExited) 'The title-bar runClient Gradle task exited before Minecraft became visible.'
		$minecraft = Get-BundledRuntimeVisibleWindow $runRuntimeBefore
		if ($minecraft) { break }
		Start-Sleep -Milliseconds 500
	} while ((Get-Date) -lt $runDeadline)
	Assert-Gate ($null -ne $minecraft) 'No visible bundled Java 25 top-level window appeared for the installed Fabric 26.1.2 run_client task.'
	$minecraftProcess = $minecraft.process
	$minecraftProcess.Refresh()
	$minecraftPath = [string]$minecraft.executable
	$expectedRuntimeRoot = (Split-Path -Parent $expectedRuntimeJava).ToLowerInvariant()
	Assert-Gate ($minecraftPath.ToLowerInvariant().StartsWith($expectedRuntimeRoot + '\')) 'Minecraft did not run from the installed bundled Java 25 runtime.'

	for ($attempt = 0; $attempt -lt 40; $attempt++) {
		Assert-Gate (-not $runProcess.HasExited) 'The title-bar runClient Gradle task did not remain running for the 20-second interactive stability window.'
		Assert-Gate ($null -ne (Get-Process -Id $minecraftProcess.Id -ErrorAction SilentlyContinue)) 'Minecraft process exited before the normal-close gate.'
		Start-Sleep -Milliseconds 500
	}

	$windowHandle = [IntPtr]$minecraft.handle
	Assert-Gate ($windowHandle -ne [IntPtr]::Zero) 'Minecraft lost its visible window before the normal-close gate.'
	Assert-Gate ([Stage15WindowsInput]::PostMessage($windowHandle, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero)) 'WM_CLOSE could not be posted to the Minecraft window.'
	try {
		Assert-Gate ($runProcess.WaitForExit(300000)) 'The title-bar runClient Gradle task did not end after normal Minecraft window close.'
		$runExitCode = Read-ProcessExitCode $runNativeHandle
	} finally {
		$null = [Stage15WindowsInput]::CloseHandle($runNativeHandle)
	}
	Assert-Gate ([int]$runExitCode -eq 0) "runClient Gradle task exited $runExitCode after normal Minecraft close."
	Add-Step 'interactive_run_client_lifecycle' @{
		launchSurface='titlebar-run-client-native-window-chrome-click'
		click=$runClick
		gradleProcessId=[int]$runCmd.ProcessId
		gradleParentProcessId=[int]$runCmd.ParentProcessId
		gradleCommandLine=$runCommandLine
		minecraftProcessId=$minecraftProcess.Id
		minecraftWindowTitle=$minecraft.title
		minecraftWindowClass=$minecraft.className
		minecraftJavaPath=$minecraftPath
		bundledRuntimeJavaRelease=25
		bundledRuntime=$true
		java21SidecarPresent=$true
		stableRunningSeconds=20
		closeMethod='WM_CLOSE'
		taskCancelled=$false
		processKilled=$false
		gradleExitCode=$runExitCode
		gradleExitCodeObserved=$true
	}

	Assert-Gate ($desktopProcess -and -not $desktopProcess.HasExited) 'Copperbench exited before normal-close cleanup.'
	Assert-Gate ([Stage15WindowsInput]::PostMessage([IntPtr]$desktopProcess.MainWindowHandle, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero)) 'WM_CLOSE could not be posted to Copperbench.'
	$closeDeadline = (Get-Date).AddSeconds(60)
	while ((Test-Path -LiteralPath $descriptorPath -PathType Leaf) -and (Get-Date) -lt $closeDeadline) { Start-Sleep -Milliseconds 500 }
	Assert-Gate (-not (Test-Path -LiteralPath $descriptorPath -PathType Leaf)) 'Desktop MCP descriptor remained after normal Copperbench close.'
	Add-Step 'normal_product_close_cleanup' @{ descriptorRemoved=$true; processKilled=$false }

	$result.passed = $true
	$result.completedAt = (Get-Date).ToString('o')
} catch {
	$result.error = $_.Exception.Message
	$result.completedAt = (Get-Date).ToString('o')
	Set-Content -LiteralPath $ErrorPath -Value ($_ | Out-String) -Encoding UTF8
} finally {
	$token = $null
	$result | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $ResultPath -Encoding UTF8
}
if (-not $result.passed) { exit 2 }
'@

$session = $null
try {
	$session = New-GuestSession $VmName $credential
	$guestWorkspaceExists = Invoke-Command -Session $session -ArgumentList $PreservedWorkspace -ScriptBlock {
		param($Path) Test-Path -LiteralPath $Path -PathType Leaf
	}
	if (-not $guestWorkspaceExists) { throw "Preserved Fabric 26.1.2 workspace not found in guest: $PreservedWorkspace" }

	if ($CollectExistingGuestTask) {
		$existingTask = Invoke-Command -Session $session -ScriptBlock {
			Get-ScheduledTask -TaskName 'Copperbench-Stage15-Windows-Product-Regression' -ErrorAction SilentlyContinue
		}
		if (-not $existingTask) { throw 'No existing Stage 15 Windows product-regression guest task was found to collect.' }
	} else {
		Invoke-Command -Session $session -ArgumentList $GuestRoot, $guestScript, $InstallDir, $PreservedWorkspace,
			$candidateHead, $candidateSha256, $sourceDelta, $GuestUser -ScriptBlock {
			param($Root, $Body, $TargetInstallDir, $SourceWorkspace, $Head, $Sha, $Delta, $TargetUser)
			$taskName = 'Copperbench-Stage15-Windows-Product-Regression'
			$existingTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
			if ($existingTask -and [string]$existingTask.State -eq 'Running') {
				throw 'A previous Stage 15 Windows product-regression guest task is still running; refusing to overlap acceptance runs.'
			}
			Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
			New-Item -ItemType Directory -Force -Path $Root | Out-Null
			$scriptPath = Join-Path $Root 'Invoke-Stage15WindowsRunClientGate.ps1'
			Remove-Item -LiteralPath (Join-Path $Root 'windows-product-regression-clean-windows11.json') -Force -ErrorAction SilentlyContinue
			Remove-Item -LiteralPath (Join-Path $Root 'windows-product-regression-clean-windows11-error.txt') -Force -ErrorAction SilentlyContinue
			Remove-Item -LiteralPath (Join-Path $Root 'windows-build-diagnostic.stdout.log') -Force -ErrorAction SilentlyContinue
			Remove-Item -LiteralPath (Join-Path $Root 'windows-build-diagnostic.stderr.log') -Force -ErrorAction SilentlyContinue
			# Scheduled tasks run Windows PowerShell 5.1. Write the guest script with a BOM-backed
			# Unicode encoding so non-ASCII UI Automation names survive regardless of the guest
			# ANSI code page or whether the host session is PowerShell 7.
			Set-Content -LiteralPath $scriptPath -Value $Body -Encoding Unicode
			$arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`" -InstallDir `"$TargetInstallDir`" -SourceWorkspace `"$SourceWorkspace`" -GuestRoot `"$Root`" -CandidateHead `"$Head`" -CandidateSha256 `"$Sha`""
			if ($Delta) { $arguments += " -SourceDeltaSha256 `"$Delta`"" }
			$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
			$principal = New-ScheduledTaskPrincipal -UserId $TargetUser -LogonType Interactive -RunLevel Highest
			Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Force | Out-Null
			Start-ScheduledTask -TaskName $taskName
		}
	}

	$guestResultPath = Join-Path $GuestRoot 'windows-product-regression-clean-windows11.json'
	$deadline = (Get-Date).AddMinutes(30)
	do {
		Start-Sleep -Seconds 2
		if ($CollectExistingGuestTask) {
			$taskState = Invoke-Command -Session $session -ScriptBlock {
				$t = Get-ScheduledTask -TaskName 'Copperbench-Stage15-Windows-Product-Regression' -ErrorAction SilentlyContinue
				if ($t) { [string]$t.State } else { 'Missing' }
			}
			if ($taskState -eq 'Missing') { throw 'Existing Stage 15 Windows product-regression guest task disappeared before evidence was written.' }
		}
		$ready = Invoke-Command -Session $session -ArgumentList $guestResultPath -ScriptBlock {
			param($Path) Test-Path -LiteralPath $Path -PathType Leaf
		}
		if ($CollectExistingGuestTask -and $ready -and $taskState -eq 'Running') { $ready = $false }
	} while (-not $ready -and (Get-Date) -lt $deadline)
	if (-not $ready) { throw 'Stage 15 Windows product-regression guest gate timed out.' }
	Copy-Item -LiteralPath $guestResultPath -Destination $hostResultPath -FromSession $session -Force
	$guestErrorPath = Join-Path $GuestRoot 'windows-product-regression-clean-windows11-error.txt'
	$errorExists = Invoke-Command -Session $session -ArgumentList $guestErrorPath -ScriptBlock {
		param($Path) Test-Path -LiteralPath $Path -PathType Leaf
	}
	if ($errorExists) { Copy-Item -LiteralPath $guestErrorPath -Destination $hostErrorPath -FromSession $session -Force }
} finally {
	if ($session) { Remove-PSSession $session -ErrorAction SilentlyContinue }
}

$result = Get-Content -LiteralPath $hostResultPath -Raw | ConvertFrom-Json
$result | Add-Member -NotePropertyName baselineGate -NotePropertyValue ([pscustomobject]@{
	passed = [bool]$baseline.passed
	evidencePath = (Resolve-Path $BaselineEvidencePath).Path
	candidateSourceHead = [string]$baseline.candidateSourceHead
	candidateInstallerSha256 = [string]$baseline.candidateInstallerSha256
}) -Force
$fullPassed = [bool]$result.passed -and [bool]$baseline.passed
$result | Add-Member -NotePropertyName fullStage15WindowsRegressionPassed -NotePropertyValue $fullPassed -Force
$result | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $hostResultPath -Encoding UTF8
Write-Output ("passed=" + $fullPassed)
Write-Output ("candidateSourceHead=" + [string]$result.candidateSourceHead)
Write-Output ("candidateInstallerSha256=" + [string]$result.candidateInstallerSha256)
Write-Output ("baselineEvidence=" + (Resolve-Path $BaselineEvidencePath).Path)
Write-Output ("evidence=" + $hostResultPath)
if (-not $fullPassed) {
	if ($result.error) { Write-Output ("error=" + [string]$result.error) }
	exit 2
}
