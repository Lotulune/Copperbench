[CmdletBinding()]
param(
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$InstallDir = 'D:\Copperbench-ReleasePreview',
	[int]$LaunchSeconds = 25
)

$ErrorActionPreference = 'Stop'

function Test-Administrator {
	$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
	$principal = [Security.Principal.WindowsPrincipal]$identity
	return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Invoke-Native([string]$file, [string[]]$arguments, [string]$workDir, [int]$timeoutSeconds) {
	$proc = Start-Process -FilePath $file -ArgumentList $arguments -WorkingDirectory $workDir `
		-PassThru -Wait -WindowStyle Hidden
	if ($null -eq $proc) {
		throw "Failed to start $file"
	}
	if (-not $proc.HasExited) {
		Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
		throw "$file timed out after $timeoutSeconds seconds"
	}
	return $proc.ExitCode
}

function Get-AsciiHaystack([string]$path) {
	$bytes = [System.IO.File]::ReadAllBytes($path)
	$chars = [System.Collections.Generic.List[char]]::new()
	foreach ($b in $bytes) {
		if ($b -ge 32 -and $b -le 126) {
			[void]$chars.Add([char]$b)
		} else {
			[void]$chars.Add(' ')
		}
	}
	return -join $chars
}

function Stop-CopperbenchProcesses {
	Get-Process -Name 'copperbench', 'javaw' -ErrorAction SilentlyContinue |
		Where-Object { $_.Path -and ($_.Path -like '*Copperbench*') } |
		ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
}

if (-not (Test-Administrator)) {
	throw 'Silent NSIS install needs an elevated PowerShell. Re-run as Administrator.'
}

$exportDir = Join-Path $RepositoryRoot 'build\export'
$win64 = Join-Path $exportDir 'win64'
$installer = Join-Path $exportDir 'Copperbench 0.1.0 Windows 64bit.exe'
$zip = Join-Path $exportDir 'Copperbench 0.1.0 Windows 64bit.zip'
$exe = Join-Path $win64 'copperbench.exe'
$previewDir = Join-Path $exportDir 'unsigned-preview'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-8\$stamp"
New-Item -ItemType Directory -Force -Path $previewDir, $evidenceDir | Out-Null

foreach ($path in @($installer, $zip, $exe)) {
	if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
		throw "Missing export artifact: $path"
	}
}

$required = @(
	'copperbench.exe',
	'LICENSE.txt',
	'jdk\bin\java.exe',
	'jdk\bin\jcef.dll',
	'lib\copperbench.jar',
	'plugins'
)
$missing = @($required | Where-Object { -not (Test-Path -LiteralPath (Join-Path $win64 $_)) })
if ($missing.Count -gt 0) {
	throw ("win64 layout missing: " + ($missing -join ', '))
}

$haystack = Get-AsciiHaystack $exe
$productShellEmbedded = $haystack.Contains('-Dcopperbench.productShell=true')
if (-not $productShellEmbedded) {
	throw 'copperbench.exe does not embed -Dcopperbench.productShell=true; rebuild exportWin64'
}

$installerSignature = [string](Get-AuthenticodeSignature -FilePath $installer).Status
$exeSignature = [string](Get-AuthenticodeSignature -FilePath $exe).Status
if ($installerSignature -ne 'NotSigned' -or $exeSignature -ne 'NotSigned') {
	throw "Expected NotSigned; installer=$installerSignature exe=$exeSignature"
}

$hashes = @(
	@{ id = 'installer'; path = $installer },
	@{ id = 'zip'; path = $zip },
	@{ id = 'portableExe'; path = $exe }
) | ForEach-Object {
	$item = Get-Item -LiteralPath $_.path
	[ordered]@{
		id = $_.id
		path = $_.path
		bytes = $item.Length
		sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.path).Hash.ToLowerInvariant()
		lastWriteTime = $item.LastWriteTime.ToString('o')
	}
}

$sums = Join-Path $previewDir 'SHA256SUMS.txt'
$lines = @($hashes | ForEach-Object { "$($_.sha256)  $(Split-Path $_.path -Leaf)" })
$lines | Set-Content -LiteralPath $sums -Encoding ascii

Stop-CopperbenchProcesses
Start-Sleep -Seconds 1

if (Test-Path -LiteralPath $InstallDir) {
	$existingUninstall = Join-Path $InstallDir 'Uninstall.exe'
	if (Test-Path -LiteralPath $existingUninstall) {
		Invoke-Native $existingUninstall @('/S', "_?=$InstallDir") $InstallDir 600 | Out-Null
		Start-Sleep -Seconds 2
	}
	if (Test-Path -LiteralPath $InstallDir) {
		Remove-Item -LiteralPath $InstallDir -Recurse -Force
	}
}
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null

$installExit = Invoke-Native $installer @('/S', "/D=$InstallDir") $exportDir 600
if ($installExit -ne 0) {
	throw "Silent install exit $installExit"
}
$installedExe = Join-Path $InstallDir 'copperbench.exe'
if (-not (Test-Path -LiteralPath $installedExe)) {
	throw "copperbench.exe missing after install at $InstallDir"
}

$launch = Start-Process -FilePath $installedExe -WorkingDirectory $InstallDir -PassThru
Start-Sleep -Seconds $LaunchSeconds
$running = @(Get-Process -Name 'copperbench', 'javaw' -ErrorAction SilentlyContinue |
	Where-Object { $_.Path -and ($_.Path -like '*Copperbench*') })
$windowTitles = @(
	$running |
		Where-Object { $_.MainWindowTitle } |
		Select-Object -ExpandProperty MainWindowTitle -Unique
)

$screenshot = Join-Path $previewDir 'launch-smoke.png'
try {
	Add-Type -AssemblyName System.Windows.Forms
	Add-Type -AssemblyName System.Drawing
	$bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
	$bitmap = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height
	$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
	$graphics.CopyFromScreen($bounds.Location, [System.Drawing.Point]::Empty, $bounds.Size)
	$bitmap.Save($screenshot, [System.Drawing.Imaging.ImageFormat]::Png)
	$graphics.Dispose()
	$bitmap.Dispose()
}
catch {
	$screenshot = $null
}

$processAlive = $running.Count -gt 0
Stop-CopperbenchProcesses

$notes = @"
Copperbench 0.1.0 unsigned release preview
==========================================

GitHub Release is NOT published. Review these local artifacts first.

Artifacts
- Installer: $installer
- ZIP:       $zip
- Portable:  $win64
- Checksums: $sums

Local install left for you
- $InstallDir
- Launch: $installedExe
- I stopped copperbench/javaw after a ${LaunchSeconds}s smoke so you can start it yourself.

What this script already checked
- Authenticode installer=$installerSignature exe=$exeSignature
- win64 required files present
- copperbench.exe embeds -Dcopperbench.productShell=true
- Silent NSIS install to $InstallDir
- Process alive after ${LaunchSeconds}s: $processAlive
- Window titles: $($windowTitles -join ' | ')

What you should try
1. Double-click the installer EXE (expect SmartScreen / unknown publisher).
2. Launch $installedExe and click through 工作区 / 帮助 About.
3. Confirm About still says 未生产签名.
4. Create or open a workspace, then close the app.
5. If that looks right, say so and a GitHub Release can be published from these same files.

Do not publish if the window did not stay up, About is wrong, or SmartScreen/install is unusable.
"@
$notesPath = Join-Path $previewDir 'NOTES.txt'
$notes | Set-Content -LiteralPath $notesPath -Encoding UTF8

$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'unsigned-github-release-preview'
	published = $false
	passed = $processAlive -and $productShellEmbedded -and ($installerSignature -eq 'NotSigned')
	installDir = $InstallDir
	installerSignature = $installerSignature
	exeSignature = $exeSignature
	productShellEmbedded = $productShellEmbedded
	silentInstallExit = $installExit
	processAliveAfterLaunchSeconds = $processAlive
	launchSeconds = $LaunchSeconds
	windowTitles = $windowTitles
	screenshot = $screenshot
	hashes = $hashes
	notes = $notesPath
	gitHead = (git -C $RepositoryRoot rev-parse HEAD).Trim()
	completedAt = (Get-Date).ToString('o')
}
$evidencePath = Join-Path $evidenceDir 'unsigned-release-preview.json'
($result | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $evidencePath -Encoding utf8

Write-Output ("passed=" + $result.passed)
Write-Output ("installDir=" + $InstallDir)
Write-Output ("processAlive=" + $processAlive)
Write-Output ("notes=" + $notesPath)
Write-Output ("evidence=" + $evidencePath)
if (-not $result.passed) {
	throw 'Unsigned release preview did not keep copperbench.exe/javaw alive. Do not publish.'
}
