[CmdletBinding()]
param(
	[string]$VmName = 'Copperbench-G7',
	[string]$InstallerPath = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'build\export\Copperbench 0.1.0 Windows 64bit.exe'),
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$GuestUser = 'g7admin',
	[string]$PasswordFile = 'D:\Hyper-V\G7\g7admin.password.txt',
	[string]$InstallDir = 'C:\Copperbench-Stage13-AssetDrop',
	[string]$GuestRoot = 'C:\Users\g7admin\Documents\Copperbench-Stage13-AssetDrop',
	[switch]$ReuseInstalledCandidate
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

$fixtureRoot = Join-Path $RepositoryRoot 'build\stage13-diagnostics-fixture\valid'
if (-not (Test-Path -LiteralPath $InstallerPath -PathType Leaf)) { throw "Installer not found: $InstallerPath" }
if (-not (Test-Path -LiteralPath $PasswordFile -PathType Leaf)) { throw "Guest password file not found: $PasswordFile" }
if (-not (Test-Path -LiteralPath $fixtureRoot -PathType Container)) { throw "Valid Stage 13 fixture missing: $fixtureRoot" }

$plainPassword = (Get-Content -LiteralPath $PasswordFile -Raw).Trim()
$credential = [pscredential]::new($GuestUser, (ConvertTo-SecureString $plainPassword -AsPlainText -Force))
$candidateCommit = (git -C $RepositoryRoot rev-parse HEAD).Trim()
$candidateSha256 = (Get-FileHash -LiteralPath $InstallerPath -Algorithm SHA256).Hash.ToLowerInvariant()
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-13\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$hostResultPath = Join-Path $evidenceDir 'asset-drop-clean-windows11.json'
$hostScreenshotPath = Join-Path $evidenceDir 'asset-drop-clean-windows11.png'
$hostErrorPath = Join-Path $evidenceDir 'asset-drop-clean-windows11-error.txt'
Remove-Item -LiteralPath $hostResultPath, $hostScreenshotPath, $hostErrorPath -Force -ErrorAction SilentlyContinue
$fixtureArchive = Join-Path $RepositoryRoot 'build\stage13-asset-drop-fixture.zip'
Remove-Item -LiteralPath $fixtureArchive -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $fixtureRoot '*') -DestinationPath $fixtureArchive -CompressionLevel Fastest

function New-GuestSession {
	param([string]$TargetVm, [pscredential]$Credential, [int]$Attempts = 60)
	for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
		try { return New-PSSession -VMName $TargetVm -Credential $Credential -ErrorAction Stop }
		catch { Start-Sleep -Seconds 2 }
	}
	throw "PowerShell Direct did not become available for $TargetVm."
}

$guestScript = @'
param(
	[Parameter(Mandatory = $true)][string]$InstallDir,
	[Parameter(Mandatory = $true)][string]$WorkspaceFile,
	[Parameter(Mandatory = $true)][string]$GuestRoot,
	[Parameter(Mandatory = $true)][string]$CandidateCommit,
	[Parameter(Mandatory = $true)][string]$CandidateSha256
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class Stage13AssetDropInput {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetClassName(IntPtr hWnd, StringBuilder text, int maxCount);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int maxCount);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int X, int Y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
  [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hWnd, IntPtr insertAfter, int X, int Y, int cx, int cy, uint flags);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hWnd, uint message, UIntPtr wParam, IntPtr lParam);
  public static void MoveMouseAbsolute(int x, int y) {
    int width = Math.Max(1, GetSystemMetrics(0) - 1);
    int height = Math.Max(1, GetSystemMetrics(1) - 1);
    uint dx = (uint)Math.Round(x * 65535.0 / width);
    uint dy = (uint)Math.Round(y * 65535.0 / height);
    mouse_event(0x0001 | 0x8000, dx, dy, 0, UIntPtr.Zero);
  }
}
"@

$ResultPath = Join-Path $GuestRoot 'asset-drop-clean-windows11.json'
$ErrorPath = Join-Path $GuestRoot 'asset-drop-clean-windows11-error.txt'
$ScreenshotPath = Join-Path $GuestRoot 'asset-drop-clean-windows11.png'
$launcher = Join-Path $InstallDir 'copperbench.exe'
$sourceDir = Join-Path $GuestRoot 'asset-drop-source'
$sourceFile = Join-Path $sourceDir 'dropped_panel.png'
$controlDir = Join-Path $GuestRoot 'asset-drop-control'
$expectedTarget = 'assets/stage13_diagnostics/textures/imported/dropped_panel.png'
$desktopProcess = $null
$explorerHandle = [IntPtr]::Zero
$controlExplorerHandle = [IntPtr]::Zero
$steps = [System.Collections.Generic.List[object]]::new()
$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'stage13-asset-drop-clean-windows11'
	candidateCommit = $CandidateCommit
	candidateInstallerSha256 = $CandidateSha256
	workspaceFile = $WorkspaceFile
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

function Get-ForegroundWindowIdentity {
	$handle = [Stage13AssetDropInput]::GetForegroundWindow()
	$classBuilder = [Text.StringBuilder]::new(256)
	$textBuilder = [Text.StringBuilder]::new(512)
	if ($handle -ne [IntPtr]::Zero) {
		$null = [Stage13AssetDropInput]::GetClassName($handle, $classBuilder, $classBuilder.Capacity)
		$null = [Stage13AssetDropInput]::GetWindowText($handle, $textBuilder, $textBuilder.Capacity)
	}
	[pscustomobject]@{ handle = $handle; className = $classBuilder.ToString(); text = $textBuilder.ToString() }
}

function Wait-GeneratorSetupDialog([IntPtr]$WindowHandle, [int]$TimeoutSeconds = 480) {
	[Stage13AssetDropInput]::SetForegroundWindow($WindowHandle) | Out-Null
	Start-Sleep -Milliseconds 500
	$started = Get-Date
	$deadline = $started.AddSeconds($TimeoutSeconds)
	$observed = $false
	$setupBlocking = $false
	do {
		$foreground = Get-ForegroundWindowIdentity
		$setupBlocking = $foreground.className -eq 'SunAwtDialog' -and $foreground.text -eq 'Workspace setup for selected generator'
		if ($setupBlocking) {
			$observed = $true
			Start-Sleep -Seconds 1
			continue
		}
		[Stage13AssetDropInput]::SetForegroundWindow($WindowHandle) | Out-Null
		Start-Sleep -Milliseconds 750
		$foreground = Get-ForegroundWindowIdentity
		$setupBlocking = $foreground.className -eq 'SunAwtDialog' -and $foreground.text -eq 'Workspace setup for selected generator'
		if (-not $setupBlocking) { break }
	} while ((Get-Date) -lt $deadline)
	Assert-Gate (-not $setupBlocking) 'Workspace setup for selected generator did not finish within 8 minutes.'
	[pscustomobject]@{ observed = $observed; waitMs = [int]((Get-Date) - $started).TotalMilliseconds }
}

function Click-ScreenPoint([int]$X, [int]$Y) {
	[Stage13AssetDropInput]::MoveMouseAbsolute($X, $Y)
	Start-Sleep -Milliseconds 100
	[Stage13AssetDropInput]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
	[Stage13AssetDropInput]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
	Start-Sleep -Milliseconds 200
}

function Drag-ScreenPoint([int]$FromX, [int]$FromY, [int]$ToX, [int]$ToY) {
	[Stage13AssetDropInput]::MoveMouseAbsolute($FromX, $FromY)
	Start-Sleep -Milliseconds 200
	[Stage13AssetDropInput]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
	Start-Sleep -Milliseconds 180
	# Move a few pixels while the button is down to cross Explorer's drag threshold before traversing
	# windows, then continue with explicit absolute mouse MOVE events so the Shell enters OLE DnD.
	$kickX = if ($ToX -ge $FromX) { $FromX + 12 } else { $FromX - 12 }
	$kickY = if ($ToY -ge $FromY) { $FromY + 6 } else { $FromY - 6 }
	[Stage13AssetDropInput]::MoveMouseAbsolute($kickX, $kickY)
	Start-Sleep -Milliseconds 250
	$stepsCount = 48
	for ($index = 1; $index -le $stepsCount; $index++) {
		$x = [int][Math]::Round($kickX + (($ToX - $kickX) * $index / $stepsCount))
		$y = [int][Math]::Round($kickY + (($ToY - $kickY) * $index / $stepsCount))
		[Stage13AssetDropInput]::MoveMouseAbsolute($x, $y)
		Start-Sleep -Milliseconds 30
	}
	Start-Sleep -Milliseconds 350
	[Stage13AssetDropInput]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
	Start-Sleep -Milliseconds 800
}

function Get-ExplorerWindow([string]$FolderName) {
	$desktop = [System.Windows.Automation.AutomationElement]::RootElement
	$deadline = (Get-Date).AddSeconds(20)
	do {
		$windows = $desktop.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)
		foreach ($window in $windows) {
			try {
				if ($window.Current.ClassName -eq 'CabinetWClass' -and -not $window.Current.IsOffscreen -and
						$window.Current.Name -like "*$FolderName*") { return $window }
			} catch { }
		}
		Start-Sleep -Milliseconds 300
	} while ((Get-Date) -lt $deadline)
	return $null
}

function Select-ExplorerFileItem([System.Windows.Automation.AutomationElement]$Item, [int]$X, [int]$Y) {
	$selectedThroughUia = $false
	try {
		$pattern = $null
		if ($Item.TryGetCurrentPattern([System.Windows.Automation.SelectionItemPattern]::Pattern, [ref]$pattern)) {
			([System.Windows.Automation.SelectionItemPattern]$pattern).Select()
			$selectedThroughUia = $true
		}
	} catch { }
	# A first real click is intentional: Windows may activate an Explorer window on mouse-down without
	# treating that same press as the start of a shell drag. The subsequent press is the drag gesture.
	Click-ScreenPoint $X $Y
	Start-Sleep -Milliseconds 350
	return $selectedThroughUia
}

function Get-ExplorerFileItem([System.Windows.Automation.AutomationElement]$Window, [string]$FileName) {
	$stem = [IO.Path]::GetFileNameWithoutExtension($FileName)
	$deadline = (Get-Date).AddSeconds(15)
	do {
		$items = $Window.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)
		foreach ($item in $items) {
			try {
				$name = [string]$item.Current.Name
				$bounds = $item.Current.BoundingRectangle
				if (($name -eq $FileName -or $name -eq $stem) -and -not $item.Current.IsOffscreen -and
						$bounds.Width -gt 8 -and $bounds.Height -gt 8) { return $item }
			} catch { }
		}
		Start-Sleep -Milliseconds 300
	} while ((Get-Date) -lt $deadline)
	return $null
}

function Capture-Screen([string]$Path) {
	$bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
	$bitmap = [Drawing.Bitmap]::new($bounds.Width, $bounds.Height)
	$graphics = [Drawing.Graphics]::FromImage($bitmap)
	try {
		$graphics.CopyFromScreen($bounds.X, $bounds.Y, 0, 0, $bounds.Size)
		$bitmap.Save($Path, [Drawing.Imaging.ImageFormat]::Png)
	} finally {
		$graphics.Dispose()
		$bitmap.Dispose()
	}
}

function Test-ExplorerDragControl {
	$sourceHandle = [IntPtr]::Zero
	$targetHandle = [IntPtr]::Zero
	try {
		$null = Start-Process explorer.exe -ArgumentList $sourceDir -PassThru
		$sourceWindow = Get-ExplorerWindow (Split-Path -Leaf $sourceDir)
		Assert-Gate ($null -ne $sourceWindow) 'Explorer control source window did not appear.'
		$sourceHandle = [IntPtr]$sourceWindow.Current.NativeWindowHandle
		Assert-Gate ($sourceHandle -ne [IntPtr]::Zero) 'Explorer control source exposed no native handle.'
		$null = [Stage13AssetDropInput]::SetWindowPos($sourceHandle, [IntPtr]::Zero, 0, 120, 420, 500, 0x0040)

		$null = Start-Process explorer.exe -ArgumentList $controlDir -PassThru
		$targetWindow = Get-ExplorerWindow (Split-Path -Leaf $controlDir)
		Assert-Gate ($null -ne $targetWindow) 'Explorer control-target window did not appear.'
		$targetHandle = [IntPtr]$targetWindow.Current.NativeWindowHandle
		Assert-Gate ($targetHandle -ne [IntPtr]::Zero) 'Explorer control-target window exposed no native handle.'
		$null = [Stage13AssetDropInput]::SetWindowPos($targetHandle, [IntPtr]::Zero, 604, 120, 420, 500, 0x0040)

		[Stage13AssetDropInput]::SetForegroundWindow($sourceHandle) | Out-Null
		Start-Sleep -Milliseconds 700
		$fileItem = Get-ExplorerFileItem $sourceWindow 'dropped_panel.png'
		Assert-Gate ($null -ne $fileItem) 'Explorer control source did not expose dropped_panel.png.'
		$bounds = $fileItem.Current.BoundingRectangle
		$sourceX = [int][Math]::Round($bounds.Left + ($bounds.Width / 2))
		$sourceY = [int][Math]::Round($bounds.Top + ($bounds.Height / 2))
		$selectedThroughUia = Select-ExplorerFileItem $fileItem $sourceX $sourceY
		$foregroundBeforeDrag = Get-ForegroundWindowIdentity
		Assert-Gate ($foregroundBeforeDrag.className -eq 'CabinetWClass') "Explorer control source was not foreground before drag: $($foregroundBeforeDrag.className)"
		Drag-ScreenPoint $sourceX $sourceY 800 420

		$controlFile = Join-Path $controlDir 'dropped_panel.png'
		$deadline = (Get-Date).AddSeconds(5)
		while (-not (Test-Path -LiteralPath $controlFile -PathType Leaf) -and (Get-Date) -lt $deadline) {
			Start-Sleep -Milliseconds 200
		}
		Assert-Gate (Test-Path -LiteralPath $controlFile -PathType Leaf) 'Explorer-to-Explorer control drag did not transfer dropped_panel.png.'
		if (-not (Test-Path -LiteralPath $sourceFile -PathType Leaf)) {
			Move-Item -LiteralPath $controlFile -Destination $sourceFile -Force
		} else {
			Remove-Item -LiteralPath $controlFile -Force
		}
		Add-Step 'explorer_drag_control' @{
			sourceName = 'dropped_panel.png'
			shellTransferObserved = $true
			selectedThroughUia = $selectedThroughUia
			foregroundClassBeforeDrag = [string]$foregroundBeforeDrag.className
			sourceUiPoint = "$sourceX,$sourceY"
			dropUiPoint = '800,420'
		}
	} finally {
		if ($targetHandle -ne [IntPtr]::Zero) {
			try { $null = [Stage13AssetDropInput]::PostMessage($targetHandle, 0x0010, [UIntPtr]::Zero, [IntPtr]::Zero) } catch { }
		}
		if ($sourceHandle -ne [IntPtr]::Zero) {
			try { $null = [Stage13AssetDropInput]::PostMessage($sourceHandle, 0x0010, [UIntPtr]::Zero, [IntPtr]::Zero) } catch { }
		}
		Start-Sleep -Milliseconds 700
	}
}

try {
	Assert-Gate (Test-Path -LiteralPath $launcher -PathType Leaf) "Installed launcher missing: $launcher"
	Assert-Gate (Test-Path -LiteralPath $WorkspaceFile -PathType Leaf) "Fixture workspace missing: $WorkspaceFile"
	New-Item -ItemType Directory -Force -Path $sourceDir | Out-Null
	New-Item -ItemType Directory -Force -Path $controlDir | Out-Null
	# A tiny valid PNG. The evidence records only its file name; browser code must never receive the external path.
	$png = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z4x8AAAAASUVORK5CYII=')
	[IO.File]::WriteAllBytes($sourceFile, $png)
	Add-Step 'external_source_prepared' @{ fileName = 'dropped_panel.png'; bytes = $png.Length }
	Test-ExplorerDragControl

	$nativeLauncher = Start-Process -FilePath $launcher -ArgumentList @('-workspace', $WorkspaceFile) -WorkingDirectory $InstallDir -PassThru
	$deadline = (Get-Date).AddSeconds(60)
	do {
		Start-Sleep -Milliseconds 500
		$uiProcess = Get-Process javaw -ErrorAction SilentlyContinue | Where-Object {
			$_.Path -eq (Join-Path $InstallDir 'jdk\bin\javaw.exe') -and $_.MainWindowHandle -ne 0
		} | Select-Object -First 1
	} while ($null -eq $uiProcess -and (Get-Date) -lt $deadline)
	Assert-Gate ($null -ne $uiProcess) 'Installed desktop workspace window did not appear.'
	$desktopProcess = $uiProcess
	$hwnd = [IntPtr]$uiProcess.MainWindowHandle
	[Stage13AssetDropInput]::ShowWindowAsync($hwnd, 3) | Out-Null
	Start-Sleep -Milliseconds 300
	$setup = Wait-GeneratorSetupDialog $hwnd 480
	Add-Step 'installed_generator_setup' @{ observed = [bool]$setup.observed; completed = $true; waitMs = [int]$setup.waitMs }

	# Playwright-calibrated 1024x768 center of [data-testid=nav-assets] is (94.5, 291.5).
	[Stage13AssetDropInput]::SetForegroundWindow($hwnd) | Out-Null
	Start-Sleep -Milliseconds 400
	Click-ScreenPoint 95 292
	Start-Sleep -Milliseconds 1200
	Add-Step 'asset_center_opened_through_real_ui' @{ navPoint = '95,292'; viewport = '1024x768' }

	$explorer = Start-Process explorer.exe -ArgumentList $sourceDir -PassThru
	$folderName = Split-Path -Leaf $sourceDir
	$explorerWindow = Get-ExplorerWindow $folderName
	Assert-Gate ($null -ne $explorerWindow) 'Explorer source window did not appear.'
	$explorerHandle = [IntPtr]$explorerWindow.Current.NativeWindowHandle
	Assert-Gate ($explorerHandle -ne [IntPtr]::Zero) 'Explorer source window exposed no native handle.'
	# Keep the right side of the full-screen Copperbench window visible as a genuine OS drop target.
	$null = [Stage13AssetDropInput]::SetWindowPos($explorerHandle, [IntPtr]::Zero, 0, 120, 420, 500, 0x0040)
	[Stage13AssetDropInput]::SetForegroundWindow($explorerHandle) | Out-Null
	Start-Sleep -Milliseconds 700
	$fileItem = Get-ExplorerFileItem $explorerWindow 'dropped_panel.png'
	Assert-Gate ($null -ne $fileItem) 'Explorer did not expose the dropped_panel.png item through UI Automation.'
	$sourceBounds = $fileItem.Current.BoundingRectangle
	$sourceX = [int][Math]::Round($sourceBounds.Left + ($sourceBounds.Width / 2))
	$sourceY = [int][Math]::Round($sourceBounds.Top + ($sourceBounds.Height / 2))
	Assert-Gate ($sourceX -ge 0 -and $sourceX -lt 420 -and $sourceY -ge 120 -and $sourceY -lt 650) 'Explorer source item was outside the visible source window.'
	$selectedThroughUia = Select-ExplorerFileItem $fileItem $sourceX $sourceY
	$foregroundBeforeProductDrag = Get-ForegroundWindowIdentity
	Assert-Gate ($foregroundBeforeProductDrag.className -eq 'CabinetWClass') "Explorer source was not foreground before Copperbench drag: $($foregroundBeforeProductDrag.className)"

	# Drag the actual Explorer item into an exposed JCEF content point, not a synthetic browser event.
	Drag-ScreenPoint $sourceX $sourceY 760 500
	Start-Sleep -Milliseconds 1800
	Add-Step 'real_explorer_to_copperbench_drop' @{
		sourceName = 'dropped_panel.png'
		sourceUiPoint = "$sourceX,$sourceY"
		dropUiPoint = '760,500'
		explorerClass = [string]$explorerWindow.Current.ClassName
		selectedThroughUia = $selectedThroughUia
		foregroundClassBeforeDrag = [string]$foregroundBeforeProductDrag.className
	}

	$null = [Stage13AssetDropInput]::PostMessage($explorerHandle, 0x0010, [UIntPtr]::Zero, [IntPtr]::Zero)
	Start-Sleep -Milliseconds 600
	[Stage13AssetDropInput]::SetForegroundWindow($hwnd) | Out-Null
	Start-Sleep -Milliseconds 500
	# Playwright-calibrated center of the first reviewed target input after one dropped PNG is (~644,336).
	[System.Windows.Forms.Clipboard]::SetText('STAGE13_ASSET_DROP_REVIEW_SENTINEL')
	Click-ScreenPoint 644 336
	[System.Windows.Forms.SendKeys]::SendWait('^a')
	Start-Sleep -Milliseconds 100
	[System.Windows.Forms.SendKeys]::SendWait('^c')
	Start-Sleep -Milliseconds 500
	$reviewedTarget = [System.Windows.Forms.Clipboard]::GetText()
	Capture-Screen $ScreenshotPath
	Assert-Gate ($reviewedTarget -eq $expectedTarget) "Installed Asset Center did not expose the expected reviewed drop target: $reviewedTarget"
	Add-Step 'asset_batch_review_opened' @{
		sourceName = 'dropped_panel.png'
		reviewedTarget = $reviewedTarget
		reviewInputPoint = '644,336'
		externalPathDisclosedToEvidence = $false
	}

	$result.passed = $true
	$result.reviewedTarget = $reviewedTarget
} catch {
	$result.error = [string]$_.Exception.Message
	$result.errorType = $_.Exception.GetType().FullName
	try { Capture-Screen $ScreenshotPath } catch { }
	Set-Content -LiteralPath $ErrorPath -Value ($result.errorType + ': ' + $result.error) -Encoding UTF8
} finally {
	if ($controlExplorerHandle -ne [IntPtr]::Zero) {
		try { $null = [Stage13AssetDropInput]::PostMessage($controlExplorerHandle, 0x0010, [UIntPtr]::Zero, [IntPtr]::Zero) } catch { }
	}
	if ($explorerHandle -ne [IntPtr]::Zero) {
		try { $null = [Stage13AssetDropInput]::PostMessage($explorerHandle, 0x0010, [UIntPtr]::Zero, [IntPtr]::Zero) } catch { }
	}
	if ($desktopProcess) {
		try {
			$alive = Get-Process -Id $desktopProcess.Id -ErrorAction SilentlyContinue
			if ($alive) {
				$alive.CloseMainWindow() | Out-Null
				Start-Sleep -Milliseconds 700
				$alive = Get-Process -Id $desktopProcess.Id -ErrorAction SilentlyContinue
				if ($alive) { Stop-Process -Id $desktopProcess.Id -Force -ErrorAction SilentlyContinue }
			}
		} catch { }
	}
	try { [System.Windows.Forms.Clipboard]::Clear() } catch { }
	$result.clipboardEmptyAtEnd = try { [System.Windows.Forms.Clipboard]::GetText().Length -eq 0 } catch { $false }
	$result.completedAt = (Get-Date).ToString('o')
	$result.steps = @($steps)
	$result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $ResultPath -Encoding UTF8
}
'@

$session = $null
try {
	$vm = Get-VM -Name $VmName -ErrorAction Stop
	if ($vm.State -ne 'Running') { Start-VM -Name $VmName | Out-Null; Start-Sleep -Seconds 5 }
	$session = New-GuestSession -TargetVm $VmName -Credential $credential
	Invoke-Command -Session $session -ArgumentList $GuestRoot, $InstallDir -ScriptBlock {
		param($Root, $TargetInstallDir)
		Get-Process copperbench, javaw -ErrorAction SilentlyContinue | Where-Object {
			$_.Path -and $_.Path.StartsWith($TargetInstallDir, [StringComparison]::OrdinalIgnoreCase)
		} | Stop-Process -Force -ErrorAction SilentlyContinue
		Remove-Item -LiteralPath $Root -Recurse -Force -ErrorAction SilentlyContinue
		New-Item -ItemType Directory -Force -Path $Root | Out-Null
	}
	Copy-Item -LiteralPath $fixtureArchive -Destination (Join-Path $GuestRoot 'fixture.zip') -ToSession $session -Force
	if (-not $ReuseInstalledCandidate) {
		Copy-Item -LiteralPath $InstallerPath -Destination (Join-Path $GuestRoot 'installer.exe') -ToSession $session -Force
		$install = Invoke-Command -Session $session -ArgumentList $InstallDir, $GuestRoot -ScriptBlock {
			param($TargetInstallDir, $Root)
			$process = Start-Process -FilePath (Join-Path $Root 'installer.exe') -ArgumentList @('/S', "/D=$TargetInstallDir") -Wait -PassThru
			[pscustomobject]@{
				exitCode = $process.ExitCode
				exePresent = Test-Path -LiteralPath (Join-Path $TargetInstallDir 'copperbench.exe') -PathType Leaf
				javaPresent = Test-Path -LiteralPath (Join-Path $TargetInstallDir 'jdk\bin\java.exe') -PathType Leaf
			}
		}
	} else {
		$install = Invoke-Command -Session $session -ArgumentList $InstallDir -ScriptBlock {
			param($TargetInstallDir)
			[pscustomobject]@{
				exitCode = 0
				exePresent = Test-Path -LiteralPath (Join-Path $TargetInstallDir 'copperbench.exe') -PathType Leaf
				javaPresent = Test-Path -LiteralPath (Join-Path $TargetInstallDir 'jdk\bin\java.exe') -PathType Leaf
			}
		}
	}
	if ($install.exitCode -ne 0 -or -not $install.exePresent -or -not $install.javaPresent) {
		throw "Silent install failed: $($install | ConvertTo-Json -Compress)"
	}

	Invoke-Command -Session $session -ArgumentList $GuestRoot, $guestScript, $InstallDir, $candidateCommit, $candidateSha256, $GuestUser -ScriptBlock {
		param($Root, $Body, $TargetInstallDir, $Commit, $Sha, $TargetUser)
		$workspaceRoot = Join-Path $Root 'workspace'
		New-Item -ItemType Directory -Force -Path $workspaceRoot | Out-Null
		Expand-Archive -LiteralPath (Join-Path $Root 'fixture.zip') -DestinationPath $workspaceRoot -Force
		$scriptPath = Join-Path $Root 'Invoke-AssetDropGate.ps1'
		Set-Content -LiteralPath $scriptPath -Value $Body -Encoding UTF8
		$workspaceFile = Join-Path $workspaceRoot 'stage13_diagnostics.mcreator'
		$taskName = 'Copperbench-Stage13-AssetDrop-Gate'
		Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
		$arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`" -InstallDir `"$TargetInstallDir`" -WorkspaceFile `"$workspaceFile`" -GuestRoot `"$Root`" -CandidateCommit `"$Commit`" -CandidateSha256 `"$Sha`""
		$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
		# Real Explorer drag/drop must run at the same ordinary interactive integrity level as Explorer.
		$principal = New-ScheduledTaskPrincipal -UserId $TargetUser -LogonType Interactive -RunLevel Limited
		Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Force | Out-Null
		Start-ScheduledTask -TaskName $taskName
	}

	$guestResultPath = Join-Path $GuestRoot 'asset-drop-clean-windows11.json'
	$deadline = (Get-Date).AddMinutes(15)
	do {
		Start-Sleep -Seconds 2
		$ready = Invoke-Command -Session $session -ArgumentList $guestResultPath -ScriptBlock { param($Path) Test-Path -LiteralPath $Path -PathType Leaf }
	} while (-not $ready -and (Get-Date) -lt $deadline)
	if (-not $ready) { throw 'Stage 13 Asset Drop guest gate timed out.' }
	Copy-Item -LiteralPath $guestResultPath -Destination $hostResultPath -FromSession $session -Force
	$guestScreenshotPath = Join-Path $GuestRoot 'asset-drop-clean-windows11.png'
	$screenshotExists = Invoke-Command -Session $session -ArgumentList $guestScreenshotPath -ScriptBlock { param($Path) Test-Path -LiteralPath $Path -PathType Leaf }
	if ($screenshotExists) { Copy-Item -LiteralPath $guestScreenshotPath -Destination $hostScreenshotPath -FromSession $session -Force }
	$guestErrorPath = Join-Path $GuestRoot 'asset-drop-clean-windows11-error.txt'
	$errorExists = Invoke-Command -Session $session -ArgumentList $guestErrorPath -ScriptBlock { param($Path) Test-Path -LiteralPath $Path -PathType Leaf }
	if ($errorExists) { Copy-Item -LiteralPath $guestErrorPath -Destination $hostErrorPath -FromSession $session -Force }
} finally {
	if ($session) { Remove-PSSession $session -ErrorAction SilentlyContinue }
}

$result = Get-Content -LiteralPath $hostResultPath -Raw | ConvertFrom-Json
Write-Output ("passed=" + [bool]$result.passed)
Write-Output ("candidateCommit=" + [string]$result.candidateCommit)
Write-Output ("candidateInstallerSha256=" + [string]$result.candidateInstallerSha256)
Write-Output ("steps=" + @($result.steps).Count)
Write-Output ("evidence=" + $hostResultPath)
if (Test-Path -LiteralPath $hostScreenshotPath -PathType Leaf) { Write-Output ("screenshot=" + $hostScreenshotPath) }
if (-not $result.passed) {
	if ($result.error) { Write-Output ("error=" + [string]$result.error) }
	exit 2
}
