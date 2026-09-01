[CmdletBinding()]
param(
	[string]$VmName = 'Copperbench-G7',
	[string]$InstallerPath = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'build\export\Copperbench 0.1.0 Windows 64bit.exe'),
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$GuestUser = 'g7admin',
	[string]$PasswordFile = 'D:\Hyper-V\G7\g7admin.password.txt',
	[string]$InstallDir = 'C:\Copperbench-G9'
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

if (-not (Test-Path -LiteralPath $InstallerPath -PathType Leaf)) {
	throw "Installer not found: $InstallerPath"
}
if (-not (Test-Path -LiteralPath $PasswordFile -PathType Leaf)) {
	throw "Guest password file not found: $PasswordFile"
}

$plainPassword = (Get-Content -LiteralPath $PasswordFile -Raw).Trim()
$securePassword = ConvertTo-SecureString $plainPassword -AsPlainText -Force
$credential = [pscredential]::new($GuestUser, $securePassword)
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-9\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$screenshotHost = Join-Path $evidenceDir 'clean-windows11-product-shell.png'
$resultPath = Join-Path $evidenceDir 'clean-windows11-product-shell.json'

$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'stage9-clean-windows11-product-shell'
	vmName = $VmName
	installerPath = $InstallerPath
	installDir = $InstallDir
	startedAt = (Get-Date).ToString('o')
	passed = $false
	cleanGuest = $false
	installPassed = $false
	productProcessStarted = $false
	ipcFailureDetected = $false
	screenshotCaptured = $false
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

$session = $null
try {
	$vm = Get-VM -Name $VmName -ErrorAction Stop
	if ($vm.State -ne 'Running') {
		Start-VM -Name $VmName | Out-Null
		Start-Sleep -Seconds 5
	}

	$session = New-GuestSession -TargetVm $VmName -Credential $credential
	$baseline = Invoke-Command -Session $session -ScriptBlock {
		$os = Get-CimInstance Win32_OperatingSystem
		$computer = Get-CimInstance Win32_ComputerSystem
		$toolNames = @('git', 'java', 'javac', 'gradle', 'code', 'idea64', 'devenv', 'studio64')
		$tools = foreach ($toolName in $toolNames) {
			[pscustomobject]@{
				name = $toolName
				present = [bool](Get-Command $toolName -ErrorAction SilentlyContinue)
			}
		}
		[pscustomobject]@{
			caption = $os.Caption
			version = $os.Version
			build = $os.BuildNumber
			installDate = [string]$os.InstallDate
			osArchitecture = $os.OSArchitecture
			computerName = $env:COMPUTERNAME
			userName = [Environment]::UserName
			totalPhysicalMemory = $computer.TotalPhysicalMemory
			pathBeforeInstall = $env:Path
			tools = @($tools)
		}
	}
	$result.baseline = $baseline
	$result.cleanGuest = -not ($baseline.tools | Where-Object { $_.present })

	Invoke-Command -Session $session -ScriptBlock {
		New-Item -ItemType Directory -Force -Path 'C:\Temp' | Out-Null
	}
	$installerLength = (Get-Item -LiteralPath $InstallerPath).Length
	$guestInstallerMatches = Invoke-Command -Session $session -ArgumentList $installerLength -ScriptBlock {
		param($ExpectedLength)
		$path = 'C:\Temp\Copperbench-installer.exe'
		(Test-Path -LiteralPath $path -PathType Leaf) -and ((Get-Item -LiteralPath $path).Length -eq $ExpectedLength)
	}
	if (-not $guestInstallerMatches) {
		Copy-Item -LiteralPath $InstallerPath -Destination 'C:\Temp\Copperbench-installer.exe' -ToSession $session -Force
	}

	try {
		$install = Invoke-Command -Session $session -ArgumentList $InstallDir -ScriptBlock {
			param($TargetInstallDir)
			$installer = 'C:\Temp\Copperbench-installer.exe'
			$process = Start-Process -FilePath $installer -ArgumentList @('/S', "/D=$TargetInstallDir") -Wait -PassThru
			$exe = Join-Path $TargetInstallDir 'copperbench.exe'
			$java = Join-Path $TargetInstallDir 'jdk\bin\java.exe'
			[pscustomobject]@{
				exitCode = $process.ExitCode
				disconnectedDuringInstall = $false
				exePresent = Test-Path -LiteralPath $exe -PathType Leaf
				bundledJavaPresent = Test-Path -LiteralPath $java -PathType Leaf
				pathAfterInstall = $env:Path
			}
		}
	} catch {
		if ($_.Exception.Message -notmatch 'Hyper-V socket target process has ended') {
			throw
		}
		Remove-PSSession $session -ErrorAction SilentlyContinue
		$session = New-GuestSession -TargetVm $VmName -Credential $credential
		$install = Invoke-Command -Session $session -ArgumentList $InstallDir -ScriptBlock {
			param($TargetInstallDir)
			$exe = Join-Path $TargetInstallDir 'copperbench.exe'
			$java = Join-Path $TargetInstallDir 'jdk\bin\java.exe'
			[pscustomobject]@{
				exitCode = $null
				disconnectedDuringInstall = $true
				exePresent = Test-Path -LiteralPath $exe -PathType Leaf
				bundledJavaPresent = Test-Path -LiteralPath $java -PathType Leaf
				pathAfterInstall = $env:Path
			}
		}
	}
	$result.install = $install
	$result.installPassed = (($install.exitCode -eq 0 -or $install.disconnectedDuringInstall) -and
			$install.exePresent -and $install.bundledJavaPresent)

	$desktop = Invoke-Command -Session $session -ArgumentList $InstallDir, $GuestUser -ScriptBlock {
		param($TargetInstallDir, $TargetUser)
		$exe = Join-Path $TargetInstallDir 'copperbench.exe'
		Get-Process copperbench, javaw -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
		Start-Sleep -Seconds 2

		$launchTask = 'Copperbench-G9-ProductLaunch'
		Unregister-ScheduledTask -TaskName $launchTask -Confirm:$false -ErrorAction SilentlyContinue
		$launchAction = New-ScheduledTaskAction -Execute $exe
		$launchPrincipal = New-ScheduledTaskPrincipal -UserId $TargetUser -LogonType Interactive -RunLevel Highest
		Register-ScheduledTask -TaskName $launchTask -Action $launchAction -Principal $launchPrincipal -Force | Out-Null
		Start-ScheduledTask -TaskName $launchTask
		Start-Sleep -Seconds 20

		$processes = @(Get-Process copperbench, javaw -ErrorAction SilentlyContinue | Select-Object Id, ProcessName, SessionId, StartTime)
		$interactive = @($processes | Where-Object { $_.SessionId -gt 0 })

		$capturePath = 'C:\Temp\copperbench-g9-product-shell.png'
		$captureScript = 'C:\Temp\capture-copperbench-g9.ps1'
		$capture = @'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
$bitmap = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
try {
    $graphics.CopyFromScreen($bounds.Location, [System.Drawing.Point]::Empty, $bounds.Size)
    $bitmap.Save('C:\Temp\copperbench-g9-product-shell.png', [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $graphics.Dispose()
    $bitmap.Dispose()
}
'@
		Set-Content -LiteralPath $captureScript -Value $capture -Encoding UTF8
		$captureTask = 'Copperbench-G9-Capture'
		Unregister-ScheduledTask -TaskName $captureTask -Confirm:$false -ErrorAction SilentlyContinue
		$captureAction = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$captureScript`""
		$capturePrincipal = New-ScheduledTaskPrincipal -UserId $TargetUser -LogonType Interactive -RunLevel Highest
		Register-ScheduledTask -TaskName $captureTask -Action $captureAction -Principal $capturePrincipal -Force | Out-Null
		Start-ScheduledTask -TaskName $captureTask
		$deadline = (Get-Date).AddSeconds(30)
		while (-not (Test-Path -LiteralPath $capturePath -PathType Leaf) -and (Get-Date) -lt $deadline) {
			Start-Sleep -Milliseconds 500
		}

		$root = Join-Path $env:USERPROFILE '.copperbench'
		$patterns = @(
			'Unable to establish loopback connection',
			'Failed to send args to first instance',
			'Failed to read args from secondary instance',
			'Unique4j',
			'BindException',
			'Address already in use'
		)
		$matches = @()
		if (Test-Path -LiteralPath $root) {
			$textExtensions = @('.log', '.txt', '.json', '.xml', '.yaml', '.yml', '.properties', '.conf')
			$files = Get-ChildItem -LiteralPath $root -Recurse -File -ErrorAction SilentlyContinue |
				Where-Object {
					$_.Length -lt 10MB -and
					$textExtensions -contains $_.Extension.ToLowerInvariant() -and
					$_.FullName -notmatch '[\/]\.copperbench[\/]gradle[\/]'
				}
			foreach ($file in $files) {
				foreach ($pattern in $patterns) {
					$hits = Select-String -LiteralPath $file.FullName -SimpleMatch -Pattern $pattern -ErrorAction SilentlyContinue
					foreach ($hit in $hits) {
						$matches += [pscustomobject]@{ file = $file.FullName; pattern = $pattern; line = $hit.Line }
					}
				}
			}
		}

		[pscustomobject]@{
			processes = $processes
			interactiveProcessCount = $interactive.Count
			screenshotPath = $capturePath
			screenshotPresent = Test-Path -LiteralPath $capturePath -PathType Leaf
			ipcMatches = @($matches)
		}
	}
	$result.desktop = $desktop
	$result.productProcessStarted = ($desktop.interactiveProcessCount -gt 0)
	$result.ipcFailureDetected = (@($desktop.ipcMatches).Count -gt 0)
	$result.screenshotCaptured = [bool]$desktop.screenshotPresent

	if ($desktop.screenshotPresent) {
		Copy-Item -LiteralPath $desktop.screenshotPath -Destination $screenshotHost -FromSession $session -Force
	}

	$result.passed = ($result.cleanGuest -and $result.installPassed -and $result.productProcessStarted -and
			$result.screenshotCaptured -and -not $result.ipcFailureDetected)
}
catch {
	$result.error = $_.Exception.Message
}
finally {
	if ($session) {
		Remove-PSSession $session -ErrorAction SilentlyContinue
	}
	$result.completedAt = (Get-Date).ToString('o')
	($result | ConvertTo-Json -Depth 10) | Set-Content -LiteralPath $resultPath -Encoding UTF8
	Write-Output ("passed=" + $result.passed)
	Write-Output ("cleanGuest=" + $result.cleanGuest)
	Write-Output ("installPassed=" + $result.installPassed)
	Write-Output ("productProcessStarted=" + $result.productProcessStarted)
	Write-Output ("ipcFailureDetected=" + $result.ipcFailureDetected)
	Write-Output ("screenshotCaptured=" + $result.screenshotCaptured)
	Write-Output ("evidence=" + $resultPath)
	if ($result.error) {
		Write-Output ("error=" + $result.error)
	}
}

if (-not $result.passed) {
	exit 2
}
