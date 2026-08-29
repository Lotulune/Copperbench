[CmdletBinding()]
param(
	[string]$VmName = 'Copperbench-G7',
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$GuestUser = 'g7admin',
	[string]$PasswordFile = 'D:\Hyper-V\G7\g7admin.password.txt',
	[string]$WorkspaceFile = 'C:\Users\g7admin\MCreatorWorkspaces\guigatedelta\guigatedelta.mcreator',
	[string]$InstallDir = 'C:\Copperbench-G9'
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

if (-not (Test-Path -LiteralPath $PasswordFile -PathType Leaf)) {
	throw "Guest password file not found: $PasswordFile"
}

$plainPassword = (Get-Content -LiteralPath $PasswordFile -Raw).Trim()
$credential = [pscredential]::new($GuestUser, (ConvertTo-SecureString $plainPassword -AsPlainText -Force))
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-9\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$evidencePath = Join-Path $evidenceDir 'clean-windows11-workspace-lifecycle.json'

$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'stage9-clean-windows11-workspace-lifecycle'
	vmName = $VmName
	workspaceFile = $WorkspaceFile
	installDir = $InstallDir
	startedAt = (Get-Date).ToString('o')
	passed = $false
	workspaceExists = $false
	generatorId = $null
	generatedSourcePresent = $false
	generatedMetadataPresent = $false
	buildStarted = $false
	buildExitCode = $null
	buildSucceeded = $false
	buildArtifactPresent = $false
	runClientStarted = $false
	runClientWindowObserved = $false
	runClientStable = $false
	runClientLogObserved = $false
	runClientTerminatedAfterReadiness = $false
	usedGuestWorkspaceWrapper = $false
	usedCopperbenchManagedJdk = $false
	usedCopperbenchGradleHome = $false
	gateScope = 'product-created workspace generation/build/run lifecycle on the same clean Windows 11 guest workspace'
}

function New-GuestSession {
	for ($attempt = 1; $attempt -le 60; $attempt++) {
		try {
			return New-PSSession -VMName $VmName -Credential $credential -ErrorAction Stop
		} catch {
			Start-Sleep -Seconds 2
		}
	}
	throw "PowerShell Direct did not become available for $VmName."
}

$session = $null
$guestRoot = 'C:\Temp\Copperbench-G9-LifecycleGate'
$guestProbePath = Join-Path $guestRoot 'Invoke-RunClientProbe.ps1'
$guestRunChildPath = Join-Path $guestRoot 'Invoke-RunClientChild.ps1'
$guestRunResultPath = Join-Path $guestRoot 'run-client-result.json'
$guestRunLogPath = Join-Path $guestRoot 'run-client-gradle.log'
$taskName = 'Copperbench-G9-WorkspaceLifecycle'

try {
	$vm = Get-VM -Name $VmName -ErrorAction Stop
	if ($vm.State -ne 'Running') {
		Start-VM -Name $VmName | Out-Null
		Start-Sleep -Seconds 5
	}

	$session = New-GuestSession
	$preflight = Invoke-Command -Session $session -ArgumentList $WorkspaceFile, $InstallDir -ScriptBlock {
		param($TargetWorkspaceFile, $TargetInstallDir)
		if (-not (Test-Path -LiteralPath $TargetWorkspaceFile -PathType Leaf)) {
			throw "Workspace file missing: $TargetWorkspaceFile"
		}
		$workspaceRoot = Split-Path -Parent $TargetWorkspaceFile
		$workspace = Get-Content -LiteralPath $TargetWorkspaceFile -Raw | ConvertFrom-Json
		$jdk21 = Join-Path $env:USERPROFILE '.copperbench\gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
		$gradleHome = Join-Path $env:USERPROFILE '.copperbench\gradle'
		$source = Join-Path $workspaceRoot 'src\main\java\net\mcreator\guigatedelta\GuigatedeltaMod.java'
		$metadata = Join-Path $workspaceRoot 'src\main\resources\META-INF\neoforge.mods.toml'
		$wrapper = Join-Path $workspaceRoot 'gradlew.bat'
		[pscustomobject]@{
			workspaceRoot = $workspaceRoot
			generatorId = [string]$workspace.workspaceSettings.currentGenerator
			workspaceRevision = [int64]$workspace.'dev.copperbench'.revision
			workspaceSha256 = (Get-FileHash -LiteralPath $TargetWorkspaceFile -Algorithm SHA256).Hash.ToLowerInvariant()
			generatedSourcePresent = Test-Path -LiteralPath $source -PathType Leaf
			generatedSourceSha256 = if (Test-Path -LiteralPath $source -PathType Leaf) {
				(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
			} else { $null }
			generatedMetadataPresent = Test-Path -LiteralPath $metadata -PathType Leaf
			generatedMetadataSha256 = if (Test-Path -LiteralPath $metadata -PathType Leaf) {
				(Get-FileHash -LiteralPath $metadata -Algorithm SHA256).Hash.ToLowerInvariant()
			} else { $null }
			wrapperPresent = Test-Path -LiteralPath $wrapper -PathType Leaf
			jdk21Present = Test-Path -LiteralPath (Join-Path $jdk21 'bin\java.exe') -PathType Leaf
			gradleHomePresent = Test-Path -LiteralPath $gradleHome -PathType Container
			productLauncherPresent = Test-Path -LiteralPath (Join-Path $TargetInstallDir 'copperbench.exe') -PathType Leaf
			jdk21 = $jdk21
			gradleHome = $gradleHome
		}
	}
	$result.preflight = $preflight
	$result.workspaceExists = $true
	$result.generatorId = $preflight.generatorId
	$result.generatedSourcePresent = [bool]$preflight.generatedSourcePresent
	$result.generatedMetadataPresent = [bool]$preflight.generatedMetadataPresent
	$result.usedGuestWorkspaceWrapper = [bool]$preflight.wrapperPresent
	$result.usedCopperbenchManagedJdk = [bool]$preflight.jdk21Present
	$result.usedCopperbenchGradleHome = [bool]$preflight.gradleHomePresent
	if ($preflight.generatorId -ne 'neoforge-1.21.1') {
		throw "Expected clean GUI workspace generator neoforge-1.21.1, got $($preflight.generatorId)"
	}
	if (-not ($result.generatedSourcePresent -and $result.generatedMetadataPresent -and
			$result.usedGuestWorkspaceWrapper -and $result.usedCopperbenchManagedJdk -and
			$result.usedCopperbenchGradleHome -and $preflight.productLauncherPresent)) {
		throw 'Clean guest workspace generation/build prerequisites are incomplete.'
	}

	Invoke-Command -Session $session -ArgumentList $InstallDir -ScriptBlock {
		param($TargetInstallDir)
		Get-Process copperbench, javaw -ErrorAction SilentlyContinue |
			Where-Object {
				try { $_.Path -and $_.Path.StartsWith($TargetInstallDir, [StringComparison]::OrdinalIgnoreCase) }
				catch { $false }
			} |
			Stop-Process -Force -ErrorAction SilentlyContinue
		Start-Sleep -Seconds 3
	}

	$result.buildStarted = $true
	$build = Invoke-Command -Session $session -ArgumentList $preflight.workspaceRoot, $preflight.jdk21, $preflight.gradleHome -ScriptBlock {
		param($WorkspaceRoot, $JavaHome, $GradleHome)
		$env:JAVA_HOME = $JavaHome
		$env:GRADLE_USER_HOME = $GradleHome
		$env:Path = (Join-Path $JavaHome 'bin') + ';' + $env:Path
		$lines = [System.Collections.Generic.List[string]]::new()
		Push-Location $WorkspaceRoot
		try {
			& .\gradlew.bat --no-daemon --stacktrace build 2>&1 | ForEach-Object {
				$line = $_.ToString()
				$lines.Add($line)
			}
			$exitCode = $LASTEXITCODE
		} finally {
			Pop-Location
		}
		$jars = @(Get-ChildItem -LiteralPath (Join-Path $WorkspaceRoot 'build\libs') -Filter '*.jar' -File -ErrorAction SilentlyContinue |
			Sort-Object LastWriteTime -Descending |
			ForEach-Object {
				[pscustomobject]@{
					path = $_.FullName
					length = $_.Length
					sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
				}
			})
		[pscustomobject]@{
			exitCode = [int]$exitCode
			buildSuccessfulMarker = [bool]($lines | Where-Object { $_ -like 'BUILD SUCCESSFUL*' } | Select-Object -First 1)
			logTail = @($lines | Select-Object -Last 120)
			artifacts = $jars
		}
	}
	$result.build = $build
	$result.buildExitCode = [int]$build.exitCode
	$result.buildSucceeded = ($result.buildExitCode -eq 0 -and [bool]$build.buildSuccessfulMarker)
	$result.buildArtifactPresent = @($build.artifacts).Count -gt 0
	if (-not ($result.buildSucceeded -and $result.buildArtifactPresent)) {
		throw 'The clean guest workspace build did not complete successfully or produced no jar.'
	}

	$runChild = @'
[CmdletBinding()]
param(
	[Parameter(Mandatory = $true)][string]$WorkspaceRoot,
	[Parameter(Mandatory = $true)][string]$JavaHome,
	[Parameter(Mandatory = $true)][string]$GradleHome,
	[Parameter(Mandatory = $true)][string]$LogPath
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JavaHome
$env:GRADLE_USER_HOME = $GradleHome
$env:Path = (Join-Path $JavaHome 'bin') + ';' + $env:Path
Push-Location $WorkspaceRoot
try {
	& .\gradlew.bat --no-daemon --stacktrace runClient 2>&1 | Tee-Object -FilePath $LogPath
	exit $LASTEXITCODE
} finally {
	Pop-Location
}
'@

	$runProbe = @'
[CmdletBinding()]
param(
	[Parameter(Mandatory = $true)][string]$WorkspaceRoot,
	[Parameter(Mandatory = $true)][string]$JavaHome,
	[Parameter(Mandatory = $true)][string]$GradleHome,
	[Parameter(Mandatory = $true)][string]$ChildScript,
	[Parameter(Mandatory = $true)][string]$ResultPath,
	[Parameter(Mandatory = $true)][string]$LogPath
)

$ErrorActionPreference = 'Stop'
$result = $null
$probeStage = 'initializing'
$progressPath = "$ResultPath.progress"
function Write-ProbeProgress {
	param([Parameter(Mandatory = $true)][string]$Stage)
	try {
		("{0}|{1}" -f (Get-Date).ToString('o'), $Stage) | Set-Content -LiteralPath $progressPath -Encoding UTF8
	} catch {
		# Progress evidence is diagnostic only and must never break the gate itself.
	}
}
Write-ProbeProgress -Stage $probeStage
trap {
	$trapError = $_
	Write-ProbeProgress -Stage ("trap:" + $probeStage)
	try {
		if ($null -eq $result) {
			$result = [ordered]@{}
		}
		$result['probeStage'] = $probeStage
		$result['probeUnhandledError'] = $true
		$result['error'] = $trapError.Exception.Message
		$result['errorPosition'] = $trapError.InvocationInfo.PositionMessage
		$result['completedAt'] = (Get-Date).ToString('o')
		($result | ConvertTo-Json -Depth 8) | Set-Content -LiteralPath $ResultPath -Encoding UTF8
	} catch {
		# The scheduled-task exit code remains authoritative if even emergency evidence cannot be written.
	}
	exit 1
}
$startedAt = Get-Date
$sessionId = (Get-Process -Id $PID).SessionId
$baselineJava = @(Get-Process java, javaw -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
$result = [ordered]@{
	processStarted = $false
	windowObserved = $false
	stable = $false
	windowTitle = $null
	windowClass = $null
	windowHandle = 0
	javaProcessId = $null
	javaCommandLine = $null
	latestLogPresent = $false
	latestLogMentionsWorkspace = $false
	latestLogMentionsNeoForge = $false
	observedWindows = @()
	terminatedAfterReadiness = $false
	probeStage = 'initialized'
	probeUnhandledError = $false
	error = $null
	errorPosition = $null
	latestLogTail = @()
	completedAt = $null
	durationSeconds = $null
}

try {
	$probeStage = 'starting-run-client'
	$result.probeStage = $probeStage
	$childArgs = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$ChildScript`" -WorkspaceRoot `"$WorkspaceRoot`" -JavaHome `"$JavaHome`" -GradleHome `"$GradleHome`" -LogPath `"$LogPath`""
	$child = Start-Process -FilePath 'powershell.exe' -ArgumentList $childArgs -PassThru -WindowStyle Hidden
	$result.processStarted = $true
	$probeStage = 'waiting-for-minecraft-window'
	$result.probeStage = $probeStage
	Write-ProbeProgress -Stage $probeStage
	$deadline = (Get-Date).AddMinutes(12)
	$matchedProcess = $null
	$matchedWindow = $null
	do {
		Start-Sleep -Seconds 1
		Write-ProbeProgress -Stage 'loop-before-get-process'
		$newJava = @(Get-Process java, javaw -ErrorAction SilentlyContinue |
			Where-Object { $baselineJava -notcontains [int]$_.Id })
		Write-ProbeProgress -Stage ("loop-after-get-process:count=" + $newJava.Count)
		$observed = @()
		foreach ($candidate in $newJava) {
			try {
				Write-ProbeProgress -Stage ("loop-before-refresh:pid=" + $candidate.Id)
				$candidate.Refresh()
				$handle = [int64]$candidate.MainWindowHandle
				$title = [string]$candidate.MainWindowTitle
				Write-ProbeProgress -Stage ("loop-after-window-read:pid=" + $candidate.Id + ":handle=" + $handle)
				if ($handle -eq 0) { continue }
				$row = [pscustomobject]@{
					processId = [int]$candidate.Id
					name = $title
					className = ''
					nativeWindowHandle = $handle
				}
				$observed += $row
				if ($row.name -match 'Minecraft|NeoForge' -or $row.nativeWindowHandle -ne 0) {
					$matchedProcess = [pscustomobject]@{
						ProcessId = [int]$candidate.Id
						CommandLine = $null
					}
					$matchedWindow = $row
					break
				}
			} catch {
				# The client can exit between enumeration and window-property access.
			}
		}
		$result.observedWindows = @($observed | Select-Object -First 40)
		if ($matchedWindow) { break }
		Write-ProbeProgress -Stage 'loop-before-child-process-check'
		$childAlive = $null -ne (Get-Process -Id $child.Id -ErrorAction SilentlyContinue)
		Write-ProbeProgress -Stage ("loop-after-child-process-check:alive=" + $childAlive)
		if (-not $childAlive) { break }
	} while ((Get-Date) -lt $deadline)
	Write-ProbeProgress -Stage 'loop-finished'

	if ($matchedWindow -and $matchedProcess) {
		$probeStage = 'minecraft-window-observed'
		$result.probeStage = $probeStage
		$result.windowObserved = $true
		$result.windowTitle = $matchedWindow.name
		$result.windowClass = $matchedWindow.className
		$result.windowHandle = [int64]$matchedWindow.nativeWindowHandle
		$result.javaProcessId = [int]$matchedProcess.ProcessId
		$result.javaCommandLine = [string]$matchedProcess.CommandLine
		Start-Sleep -Seconds 10
		$result.stable = $null -ne (Get-Process -Id $matchedProcess.ProcessId -ErrorAction SilentlyContinue)
	}

	$latestLog = Join-Path $WorkspaceRoot 'run\logs\latest.log'
	$probeStage = 'reading-latest-log'
	$result.probeStage = $probeStage
	Write-ProbeProgress -Stage $probeStage
	if (Test-Path -LiteralPath $latestLog -PathType Leaf) {
		$result.latestLogPresent = $true
		$tail = @(Get-Content -LiteralPath $latestLog -Tail 240 -Encoding UTF8 -ErrorAction SilentlyContinue |
			ForEach-Object { [string]$_.ToString() })
		$result.latestLogMentionsWorkspace = [bool]($tail | Where-Object { $_ -match 'guigatedelta' } | Select-Object -First 1)
		$result.latestLogMentionsNeoForge = [bool]($tail | Where-Object { $_ -match 'NeoForge|neoforge' } | Select-Object -First 1)
		$result.latestLogTail = @($tail | Select-Object -Last 120 | ForEach-Object { [string]$_.ToString() })
	}
} catch {
	$result.error = $_.Exception.Message
	$result.errorPosition = $_.InvocationInfo.PositionMessage
} finally {
	$probeStage = 'finalizing'
	$result.probeStage = $probeStage
	Write-ProbeProgress -Stage $probeStage
	if ($result.windowObserved) {
		Write-ProbeProgress -Stage 'finalizing-before-java-enumeration'
		$newJava = @(Get-Process java, javaw -ErrorAction SilentlyContinue |
			Where-Object { $baselineJava -notcontains $_.Id })
		Write-ProbeProgress -Stage ("finalizing-after-java-enumeration:count=" + $newJava.Count)
		$newJava | Stop-Process -Force -ErrorAction SilentlyContinue
		Start-Sleep -Seconds 3
		$result.terminatedAfterReadiness = $true
	}
	if ($child) {
		Write-ProbeProgress -Stage 'finalizing-before-child-process-check'
		$childAlive = $null -ne (Get-Process -Id $child.Id -ErrorAction SilentlyContinue)
		Write-ProbeProgress -Stage ("finalizing-after-child-process-check:alive=" + $childAlive)
		if ($childAlive) {
			Stop-Process -Id $child.Id -Force -ErrorAction SilentlyContinue
		}
	}
	Write-ProbeProgress -Stage 'finalizing-before-result-fields'
	$result.completedAt = (Get-Date).ToString('o')
	$result.durationSeconds = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
	$result.probeStage = 'completed'
	Write-ProbeProgress -Stage 'finalizing-before-json'
	$resultJson = $result | ConvertTo-Json -Depth 8
	Write-ProbeProgress -Stage ("finalizing-after-json:length=" + $resultJson.Length)
	$resultJson | Set-Content -LiteralPath $ResultPath -Encoding UTF8
	Write-ProbeProgress -Stage 'finalizing-after-result-write'
	Write-ProbeProgress -Stage 'completed'
}
'@

	Invoke-Command -Session $session -ArgumentList $guestRoot, $guestProbePath, $guestRunChildPath,
			$guestRunResultPath, $guestRunLogPath, $runProbe, $runChild -ScriptBlock {
		param($Root, $ProbePath, $ChildPath, $ResultPath, $LogPath, $ProbeContent, $ChildContent)
		New-Item -ItemType Directory -Force -Path $Root | Out-Null
		Remove-Item -LiteralPath $ProbePath, $ChildPath, $ResultPath, ($ResultPath + '.progress'), $LogPath -Force -ErrorAction SilentlyContinue
		Set-Content -LiteralPath $ProbePath -Value $ProbeContent -Encoding UTF8
		Set-Content -LiteralPath $ChildPath -Value $ChildContent -Encoding UTF8
	}

	$result.runClientStarted = $true
	Invoke-Command -Session $session -ArgumentList $taskName, $guestProbePath, $preflight.workspaceRoot,
			$preflight.jdk21, $preflight.gradleHome, $guestRunChildPath, $guestRunResultPath, $guestRunLogPath,
			$GuestUser -ScriptBlock {
		param($TaskName, $ProbePath, $WorkspaceRoot, $JavaHome, $GradleHome, $ChildPath, $ResultPath, $LogPath,
			$TargetUser)
		Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
		$arguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$ProbePath`" -WorkspaceRoot `"$WorkspaceRoot`" -JavaHome `"$JavaHome`" -GradleHome `"$GradleHome`" -ChildScript `"$ChildPath`" -ResultPath `"$ResultPath`" -LogPath `"$LogPath`""
		$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
		$principal = New-ScheduledTaskPrincipal -UserId $TargetUser -LogonType Interactive -RunLevel Highest
		Register-ScheduledTask -TaskName $TaskName -Action $action -Principal $principal -Force | Out-Null
		Start-ScheduledTask -TaskName $TaskName
	}

	$deadline = (Get-Date).AddMinutes(14)
	$runReady = $false
	do {
		Start-Sleep -Seconds 1
		$runReady = Invoke-Command -Session $session -ArgumentList $guestRunResultPath -ScriptBlock {
			param($Path)
			Test-Path -LiteralPath $Path -PathType Leaf
		}
	} while (-not $runReady -and (Get-Date) -lt $deadline)
	if (-not $runReady) {
		throw 'Clean guest runClient probe did not complete within fourteen minutes.'
	}

	$run = Invoke-Command -Session $session -ArgumentList $guestRunResultPath -ScriptBlock {
		param($Path)
		Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
	}
	$result.runClient = $run
	$result.runClientWindowObserved = [bool]$run.windowObserved
	$result.runClientStable = [bool]$run.stable
	$result.runClientLogObserved = [bool]$run.latestLogPresent
	$result.runClientTerminatedAfterReadiness = [bool]$run.terminatedAfterReadiness
	if (-not ($result.runClientWindowObserved -and $result.runClientStable -and
			$result.runClientLogObserved -and $result.runClientTerminatedAfterReadiness)) {
		throw 'The clean guest runClient task did not reach a stable interactive Minecraft window.'
	}

	$result.passed = $true
} catch {
	$result.error = $_.Exception.Message
	$result.errorType = $_.Exception.GetType().FullName
	$result.errorStack = $_.ScriptStackTrace
} finally {
	if ($session) {
		try {
			Invoke-Command -Session $session -ArgumentList $taskName -ScriptBlock {
				param($TaskName)
				Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
			} -ErrorAction SilentlyContinue
		} catch {
			$result.cleanupError = $_.Exception.Message
		}
		Remove-PSSession $session -ErrorAction SilentlyContinue
	}
	$result.completedAt = (Get-Date).ToString('o')
	($result | ConvertTo-Json -Depth 12) | Set-Content -LiteralPath $evidencePath -Encoding UTF8
	Write-Output ("passed=" + $result.passed)
	Write-Output ("generatorId=" + $result.generatorId)
	Write-Output ("generatedSourcePresent=" + $result.generatedSourcePresent)
	Write-Output ("buildSucceeded=" + $result.buildSucceeded)
	Write-Output ("buildArtifactPresent=" + $result.buildArtifactPresent)
	Write-Output ("runClientWindowObserved=" + $result.runClientWindowObserved)
	Write-Output ("runClientStable=" + $result.runClientStable)
	Write-Output ("runClientLogObserved=" + $result.runClientLogObserved)
	Write-Output ("evidence=" + $evidencePath)
	if ($result.error) { Write-Output ("error=" + $result.error) }
}

if (-not $result.passed) {
	exit 2
}
