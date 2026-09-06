[CmdletBinding()]
param(
	[string]$VmName = 'Copperbench-G7',
	[string]$InstallerPath = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'build\export\Copperbench 0.1.0 Windows 64bit.exe'),
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$GuestUser = 'g7admin',
	[string]$PasswordFile = 'D:\Hyper-V\G7\g7admin.password.txt',
	[string]$InstallDir = 'C:\Copperbench-Stage13-Diagnostics',
	[string]$GuestRoot = 'C:\Temp\Copperbench-Stage13-Diagnostics'
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

$fixtureRoot = Join-Path $RepositoryRoot 'build\stage13-diagnostics-fixture\broken'
$manifestPath = Join-Path $RepositoryRoot 'build\stage13-diagnostics-fixture\manifest.json'
if (-not (Test-Path -LiteralPath $InstallerPath -PathType Leaf)) { throw "Installer not found: $InstallerPath" }
if (-not (Test-Path -LiteralPath $PasswordFile -PathType Leaf)) { throw "Guest password file not found: $PasswordFile" }
if (-not (Test-Path -LiteralPath $fixtureRoot -PathType Container)) { throw "Diagnostics fixture missing: $fixtureRoot" }
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Diagnostics fixture manifest missing: $manifestPath" }

$plainPassword = (Get-Content -LiteralPath $PasswordFile -Raw).Trim()
$credential = [pscredential]::new($GuestUser, (ConvertTo-SecureString $plainPassword -AsPlainText -Force))
$candidateCommit = (git -C $RepositoryRoot rev-parse HEAD).Trim()
$candidateSha256 = (Get-FileHash -LiteralPath $InstallerPath -Algorithm SHA256).Hash.ToLowerInvariant()
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-13\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$hostResultPath = Join-Path $evidenceDir 'diagnostics-clean-windows11.json'
$hostErrorPath = Join-Path $evidenceDir 'diagnostics-clean-windows11-error.txt'
$fixtureArchive = Join-Path $RepositoryRoot 'build\stage13-diagnostics-fixture.zip'
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
	[Parameter(Mandatory = $true)][string]$ManifestPath,
	[Parameter(Mandatory = $true)][string]$GuestRoot,
	[Parameter(Mandatory = $true)][string]$CandidateCommit,
	[Parameter(Mandatory = $true)][string]$CandidateSha256
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Stage13DiagnosticsInput {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int X, int Y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
}
"@

$ResultPath = Join-Path $GuestRoot 'diagnostics-clean-windows11.json'
$ErrorPath = Join-Path $GuestRoot 'diagnostics-clean-windows11-error.txt'
$launcher = Join-Path $InstallDir 'copperbench.exe'
$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
$descriptorPath = Join-Path (Split-Path -Parent $WorkspaceFile) '.copperbench\mcp-connection.json'
$token = $null
$url = $null
$workspaceId = $null
$sessionId = $null
$desktopProcess = $null
$steps = [System.Collections.Generic.List[object]]::new()
$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'stage13-diagnostics-clean-windows11'
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

function Click-Point([int]$X, [int]$Y) {
	[Stage13DiagnosticsInput]::SetCursorPos($X, $Y) | Out-Null
	Start-Sleep -Milliseconds 80
	[Stage13DiagnosticsInput]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
	[Stage13DiagnosticsInput]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
}

function Invoke-Headless([string]$Command, [int]$TimeoutSeconds = 420) {
	$psi = [Diagnostics.ProcessStartInfo]::new()
	$psi.FileName = $launcher
	$psi.WorkingDirectory = $InstallDir
	$psi.UseShellExecute = $false
	$psi.RedirectStandardOutput = $true
	$psi.RedirectStandardError = $true
	$psi.CreateNoWindow = $true
	$psi.Arguments = "headless --workspace `"$WorkspaceFile`" $Command"
	$process = [Diagnostics.Process]::new()
	$process.StartInfo = $psi
	[void]$process.Start()
	$stdoutTask = $process.StandardOutput.ReadToEndAsync()
	$stderrTask = $process.StandardError.ReadToEndAsync()
	if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
		$process.Kill($true)
		$process.WaitForExit()
		throw "Installed headless command timed out: $Command"
	}
	$stdout = $stdoutTask.GetAwaiter().GetResult().Trim()
	$stderr = $stderrTask.GetAwaiter().GetResult().Trim()
	$json = $null
	try { $json = $stdout | ConvertFrom-Json } catch { }
	return [pscustomobject]@{ exitCode = $process.ExitCode; stdout = $stdout; stderr = $stderr; json = $json }
}

function Invoke-McpPost([object]$Payload, [string]$CurrentSessionId = $null, [int]$TimeoutSec = 30) {
	$headers = @{
		Accept = 'application/json, text/event-stream'
		Authorization = "Bearer $token"
		'X-Copperbench-Workspace' = $workspaceId
	}
	if ($CurrentSessionId) { $headers['mcp-session-id'] = $CurrentSessionId }
	$json = $Payload | ConvertTo-Json -Depth 50 -Compress
	Invoke-WebRequest -UseBasicParsing -Uri $url -Method Post -Headers $headers -ContentType 'application/json' -Body $json -TimeoutSec $TimeoutSec
}

function Convert-McpToolResult($Response) {
	$line = @($Response.Content -split "`r?`n" | Where-Object { $_.StartsWith('data: ') }) | Select-Object -First 1
	if (-not $line) { throw 'MCP response did not contain SSE data.' }
	$envelope = $line.Substring(6) | ConvertFrom-Json
	if ($null -eq $envelope.result -or $null -eq $envelope.result.content -or $envelope.result.content.Count -lt 1) {
		throw 'MCP tool response envelope is incomplete.'
	}
	return $envelope.result.content[0].text | ConvertFrom-Json
}

function Call-Tool([int]$Id, [string]$Name, [object]$Arguments) {
	$response = Invoke-McpPost ([ordered]@{
		jsonrpc = '2.0'; id = $Id; method = 'tools/call'; params = [ordered]@{ name = $Name; arguments = $Arguments }
	}) $sessionId
	Assert-Gate ($response.StatusCode -eq 200) "$Name returned HTTP $($response.StatusCode)."
	return Convert-McpToolResult $response
}

try {
	Assert-Gate (Test-Path -LiteralPath $launcher -PathType Leaf) "Installed launcher missing: $launcher"
	Assert-Gate (Test-Path -LiteralPath $WorkspaceFile -PathType Leaf) "Fixture workspace missing: $WorkspaceFile"

	$broken = Invoke-Headless 'validate' 180
	Assert-Gate ($null -ne $broken.json) 'Broken installed validate did not return machine JSON.'
	Assert-Gate ($broken.exitCode -ne 0) 'Broken installed validate unexpectedly returned exit 0.'
	$diagnostic = @($broken.json.diagnostics | Where-Object { [string]$_.code -eq [string]$manifest.diagnosticCode }) | Select-Object -First 1
	Assert-Gate ($null -ne $diagnostic) "Expected diagnostic $($manifest.diagnosticCode) was not returned."
	Assert-Gate ([string]$diagnostic.elementId -eq [string]$manifest.elementId) 'Generator diagnostic lost its Mod Element identity.'
	Assert-Gate ([string]$diagnostic.path -eq [string]$manifest.diagnosticPath) 'Generator diagnostic lost its producer field path.'
	$locate = @($diagnostic.actions | Where-Object { [string]$_.id -eq 'locate_generator_field' }) | Select-Object -First 1
	$repair = @($diagnostic.actions | Where-Object { [string]$_.kind -eq 'preview_repair' }) | Select-Object -First 1
	Assert-Gate ($null -ne $locate -and [string]$locate.target -eq [string]$manifest.actionTarget) 'Installed diagnostic did not expose the exact field-location action.'
	Assert-Gate ($null -ne $repair -and $null -ne $repair.payload) 'Installed diagnostic did not expose a safe repair plan payload.'
	$repairPayload = $repair.payload
	Assert-Gate ([bool]$repairPayload.requireRecoveryPoint) 'Safe repair payload did not require a recovery point.'
	$repairChange = @($repairPayload.operations)[0].payload.changes[0]
	Assert-Gate ([string]$repairChange.path -eq [string]$manifest.actionTarget) 'Safe repair change targeted the wrong field.'
	Assert-Gate ([int]$repairChange.value -eq [int]$manifest.repairValue) 'Safe repair did not preserve the producer replacement value.'
	Add-Step 'installed_failure_location_repair_payload' @{
		diagnosticCode = [string]$diagnostic.code
		elementId = [string]$diagnostic.elementId
		path = [string]$diagnostic.path
		actionTarget = [string]$locate.target
		repairValue = [int]$repairChange.value
		recoveryRequired = $true
	}

	$desktopProcess = Start-Process -FilePath $launcher -ArgumentList @('-workspace', $WorkspaceFile) -WorkingDirectory $InstallDir -PassThru
	$deadline = (Get-Date).AddSeconds(60)
	do {
		Start-Sleep -Milliseconds 500
		$uiProcess = Get-Process javaw -ErrorAction SilentlyContinue | Where-Object {
			$_.Path -eq (Join-Path $InstallDir 'jdk\bin\javaw.exe') -and $_.MainWindowHandle -ne 0
		} | Select-Object -First 1
	} while ($null -eq $uiProcess -and (Get-Date) -lt $deadline)
	Assert-Gate ($null -ne $uiProcess) 'Installed desktop workspace window did not appear.'
	$desktopProcess = $uiProcess
	$descriptorDeadline = (Get-Date).AddSeconds(30)
	while (-not (Test-Path -LiteralPath $descriptorPath -PathType Leaf) -and (Get-Date) -lt $descriptorDeadline) {
		Start-Sleep -Milliseconds 300
	}
	Assert-Gate (Test-Path -LiteralPath $descriptorPath -PathType Leaf) 'Desktop MCP descriptor did not appear.'
	$descriptor = Get-Content -LiteralPath $descriptorPath -Raw | ConvertFrom-Json
	Assert-Gate ([string]$descriptor.status -eq 'listening') 'Desktop MCP was not listening.'
	Assert-Gate (-not ($descriptor.PSObject.Properties.Name -contains 'token')) 'Desktop MCP descriptor persisted a bearer token.'

	$hwnd = [IntPtr]$uiProcess.MainWindowHandle
	[Stage13DiagnosticsInput]::ShowWindowAsync($hwnd, 3) | Out-Null
	Start-Sleep -Milliseconds 300
	[Stage13DiagnosticsInput]::SetForegroundWindow($hwnd) | Out-Null
	Start-Sleep -Milliseconds 500
	[System.Windows.Forms.Clipboard]::SetText('STAGE13_DIAGNOSTICS_URL_SENTINEL')
	Click-Point 95 383
	Start-Sleep -Milliseconds 400
	1..3 | ForEach-Object { [System.Windows.Forms.SendKeys]::SendWait('{TAB}'); Start-Sleep -Milliseconds 90 }
	[System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
	Start-Sleep -Milliseconds 500
	$copiedUrl = [System.Windows.Forms.Clipboard]::GetText()
	Assert-Gate ($copiedUrl -eq [string]$descriptor.url) 'Real UI Copy URL did not match the Desktop MCP descriptor.'
	[System.Windows.Forms.SendKeys]::SendWait('{TAB}')
	[System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
	Start-Sleep -Milliseconds 900
	[System.Windows.Forms.Clipboard]::SetText('STAGE13_DIAGNOSTICS_CONFIG_SENTINEL')
	Click-Point 95 383
	Start-Sleep -Milliseconds 350
	1..4 | ForEach-Object { [System.Windows.Forms.SendKeys]::SendWait('{TAB}'); Start-Sleep -Milliseconds 90 }
	[System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
	Start-Sleep -Milliseconds 500
	$config = [System.Windows.Forms.Clipboard]::GetText()
	$urlMatch = [regex]::Match($config, '(?m)^URL:\s*(http://127\.0\.0\.1:\d+/mcp)\s*$')
	$tokenMatch = [regex]::Match($config, '(?m)^Authorization:\s*Bearer\s+(\S+)\s*$')
	$workspaceMatch = [regex]::Match($config, '(?m)^workspaceId:\s*([0-9a-fA-F-]+)\s*$')
	Assert-Gate ($urlMatch.Success -and $tokenMatch.Success -and $workspaceMatch.Success) 'Visible UI did not yield a complete one-time MCP configuration.'
	$url = $urlMatch.Groups[1].Value
	$token = $tokenMatch.Groups[1].Value
	$workspaceId = $workspaceMatch.Groups[1].Value
	Assert-Gate ($url -eq [string]$descriptor.url -and $workspaceId -eq [string]$descriptor.workspaceId) 'Visible MCP configuration did not match the active workspace descriptor.'
	$config = $null
	[System.Windows.Forms.Clipboard]::Clear()
	Assert-Gate ([System.Windows.Forms.Clipboard]::GetText().Length -eq 0) 'Clipboard could not be cleared before MCP repair.'
	Add-Step 'desktop_mcp_credential_from_real_ui' @{ descriptorMatched = $true; tokenPersisted = $false; clipboardCleared = $true }

	$initialize = [ordered]@{
		jsonrpc = '2.0'; id = 1; method = 'initialize'; params = [ordered]@{
			protocolVersion = '2025-11-25'; capabilities = @{}; clientInfo = [ordered]@{ name = 'stage13-diagnostics-installed-gate'; version = '1.0' }
		}
	}
	$initialized = Invoke-McpPost $initialize
	Assert-Gate ($initialized.StatusCode -eq 200) 'Desktop MCP initialize failed.'
	$sessionId = [string]$initialized.Headers['mcp-session-id']
	Assert-Gate (-not [string]::IsNullOrWhiteSpace($sessionId)) 'Desktop MCP initialize returned no session id.'
	$null = Invoke-McpPost ([ordered]@{ jsonrpc = '2.0'; method = 'notifications/initialized' }) $sessionId
	$callId = 2
	$current = Call-Tool $callId 'get_workspace' @{}
	$callId++
	$expectedRevision = [int64]$repairPayload.expectedRevision
	Assert-Gate ([int64]$current.revision -eq $expectedRevision) 'Workspace revision changed between installed validation and safe repair.'

	$planArgs = [ordered]@{
		expectedRevision = $expectedRevision
		requireRecoveryPoint = [bool]$repairPayload.requireRecoveryPoint
		operations = @($repairPayload.operations)
		idempotencyKey = 'stage13-installed-generator-repair'
	}
	$planned = Call-Tool $callId 'plan_workspace_changes' $planArgs
	$callId++
	Assert-Gate ([string]$planned.status -eq 'succeeded') 'Safe repair WorkspacePlan was not created.'
	$plan = $planned.data
	Assert-Gate ([bool]$plan.requireRecoveryPoint -and [bool]$plan.safety.ready) 'Safe repair plan was not recovery-ready.'
	$semanticDiffText = $plan.semanticDiff | ConvertTo-Json -Depth 30 -Compress
	Assert-Gate ($semanticDiffText -match '/values/fields/maxStackSize') 'Safe repair semantic diff lost the invalid field.'
	$preview = Call-Tool $callId 'preview_workspace_plan' ([ordered]@{ plan = $plan })
	$callId++
	Assert-Gate ([bool]$preview.data.wouldApply) 'Safe repair WorkspacePlan preview would not apply.'
	$applied = Call-Tool $callId 'apply_workspace_plan' ([ordered]@{ plan = $plan; expectedRevision = $expectedRevision })
	$callId++
	Assert-Gate ([string]$applied.status -eq 'committed') 'Safe repair WorkspacePlan did not commit.'
	Assert-Gate (-not [string]::IsNullOrWhiteSpace([string]$applied.recoveryPointId)) 'Safe repair did not create a recovery point.'
	$newRevision = [int64]$applied.newRevision
	Assert-Gate ($newRevision -eq ($expectedRevision + 1)) 'Safe repair did not advance exactly one workspace revision.'
	$after = Call-Tool $callId 'get_workspace' @{}
	Assert-Gate ([int64]$after.revision -eq $newRevision) 'Desktop MCP did not read back the repaired revision.'
	$auditPath = Join-Path (Split-Path -Parent $WorkspaceFile) '.copperbench\automation-audit.jsonl'
	Assert-Gate (Test-Path -LiteralPath $auditPath -PathType Leaf) 'Automation audit log was not created by the real MCP repair.'
	Assert-Gate (-not (Get-Content -LiteralPath $auditPath -Raw).Contains($token)) 'Automation audit unexpectedly persisted the MCP token.'
	Add-Step 'desktop_mcp_safe_repair' @{
		fromRevision = $expectedRevision
		toRevision = $newRevision
		recoveryPointCreated = $true
		semanticField = '/values/fields/maxStackSize'
		auditTokenAbsent = $true
	}

	$closed = $uiProcess.CloseMainWindow()
	Assert-Gate $closed 'Installed desktop did not accept normal close.'
	$closeDeadline = (Get-Date).AddSeconds(30)
	do { Start-Sleep -Milliseconds 400; $alive = Get-Process -Id $uiProcess.Id -ErrorAction SilentlyContinue }
	while ($null -ne $alive -and (Get-Date) -lt $closeDeadline)
	Assert-Gate ($null -eq $alive) 'Installed desktop process did not exit after safe repair.'
	Assert-Gate (-not (Test-Path -LiteralPath $descriptorPath -PathType Leaf)) 'Desktop MCP descriptor remained after normal close.'
	$token = $null
	$url = $null
	$workspaceId = $null
	$sessionId = $null

	$validated = Invoke-Headless 'validate' 180
	Assert-Gate ($validated.exitCode -eq 0 -and $null -ne $validated.json -and [string]$validated.json.status -eq 'succeeded') 'Installed validate did not succeed after safe repair.'
	$built = Invoke-Headless 'build' 600
	Assert-Gate ($built.exitCode -eq 0 -and $null -ne $built.json -and [string]$built.json.status -eq 'succeeded') 'Installed build did not succeed after safe repair.'
	Add-Step 'installed_revalidate_and_rebuild' @{ validate = 'succeeded'; build = 'succeeded' }

	$migration = Invoke-Headless 'preview-migrate --target neoforge-1.21.1' 180
	Assert-Gate ($migration.exitCode -eq 0 -and $null -ne $migration.json -and [string]$migration.json.status -eq 'succeeded') 'Installed loader migration preview failed.'
	$migrationItem = @($migration.json.data.items | Where-Object {
		[string]$_.reasonCode -eq 'LOADER_EXCLUSIVE_FIELDS_PRESERVED' -and [string]$_.path -eq "/elements/$($manifest.elementId)"
	}) | Select-Object -First 1
	Assert-Gate ($null -ne $migrationItem) 'Installed migration preview lost the loader-exclusive element review location.'
	Add-Step 'installed_migration_review_location' @{
		reasonCode = [string]$migrationItem.reasonCode
		path = [string]$migrationItem.path
		disposition = [string]$migrationItem.disposition
	}

	$result.passed = $true
	$result.finalRevision = $newRevision
	$result.diagnosticCode = [string]$manifest.diagnosticCode
	$result.repairValue = [int]$manifest.repairValue
} catch {
	$message = [string]$_.Exception.Message
	if ($token) { $message = $message.Replace($token, '[REDACTED]') }
	$result.error = $message
	$result.errorType = $_.Exception.GetType().FullName
	Set-Content -LiteralPath $ErrorPath -Value ($result.errorType + ': ' + $message) -Encoding UTF8
} finally {
	if ($desktopProcess) {
		try {
			$alive = Get-Process -Id $desktopProcess.Id -ErrorAction SilentlyContinue
			if ($alive) { Stop-Process -Id $desktopProcess.Id -Force -ErrorAction SilentlyContinue }
		} catch { }
	}
	$token = $null
	$url = $null
	$workspaceId = $null
	$sessionId = $null
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
		New-Item -ItemType Directory -Force -Path 'C:\Temp' | Out-Null
		New-Item -ItemType Directory -Force -Path $Root | Out-Null
		Get-Process copperbench, javaw -ErrorAction SilentlyContinue | Where-Object {
			$_.Path -and $_.Path.StartsWith($TargetInstallDir, [StringComparison]::OrdinalIgnoreCase)
		} | Stop-Process -Force -ErrorAction SilentlyContinue
		Remove-Item -LiteralPath (Join-Path $Root 'workspace'), (Join-Path $Root 'fixture.zip'),
			(Join-Path $Root 'manifest.json'), (Join-Path $Root 'diagnostics-clean-windows11.json'),
			(Join-Path $Root 'diagnostics-clean-windows11-error.txt') -Recurse -Force -ErrorAction SilentlyContinue
	}
	Copy-Item -LiteralPath $InstallerPath -Destination 'C:\Temp\Copperbench-Stage13-Diagnostics-installer.exe' -ToSession $session -Force
	Copy-Item -LiteralPath $fixtureArchive -Destination (Join-Path $GuestRoot 'fixture.zip') -ToSession $session -Force
	Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $GuestRoot 'manifest.json') -ToSession $session -Force

	$install = Invoke-Command -Session $session -ArgumentList $InstallDir -ScriptBlock {
		param($TargetInstallDir)
		$process = Start-Process -FilePath 'C:\Temp\Copperbench-Stage13-Diagnostics-installer.exe' -ArgumentList @('/S', "/D=$TargetInstallDir") -Wait -PassThru
		[pscustomobject]@{
			exitCode = $process.ExitCode
			exePresent = Test-Path -LiteralPath (Join-Path $TargetInstallDir 'copperbench.exe') -PathType Leaf
			javaPresent = Test-Path -LiteralPath (Join-Path $TargetInstallDir 'jdk\bin\java.exe') -PathType Leaf
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
		$scriptPath = Join-Path $Root 'Invoke-DiagnosticsGate.ps1'
		Set-Content -LiteralPath $scriptPath -Value $Body -Encoding UTF8
		$workspaceFile = Join-Path $workspaceRoot 'stage13_diagnostics.mcreator'
		$manifest = Join-Path $Root 'manifest.json'
		$taskName = 'Copperbench-Stage13-Diagnostics-Gate'
		Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
		$arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`" -InstallDir `"$TargetInstallDir`" -WorkspaceFile `"$workspaceFile`" -ManifestPath `"$manifest`" -GuestRoot `"$Root`" -CandidateCommit `"$Commit`" -CandidateSha256 `"$Sha`""
		$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
		$principal = New-ScheduledTaskPrincipal -UserId $TargetUser -LogonType Interactive -RunLevel Highest
		Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Force | Out-Null
		Start-ScheduledTask -TaskName $taskName
	}

	$guestResultPath = Join-Path $GuestRoot 'diagnostics-clean-windows11.json'
	$deadline = (Get-Date).AddMinutes(15)
	do {
		Start-Sleep -Seconds 2
		$ready = Invoke-Command -Session $session -ArgumentList $guestResultPath -ScriptBlock { param($Path) Test-Path -LiteralPath $Path -PathType Leaf }
	} while (-not $ready -and (Get-Date) -lt $deadline)
	if (-not $ready) { throw 'Stage 13 Diagnostics guest gate timed out.' }
	Copy-Item -LiteralPath $guestResultPath -Destination $hostResultPath -FromSession $session -Force
	$guestErrorPath = Join-Path $GuestRoot 'diagnostics-clean-windows11-error.txt'
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
if (-not $result.passed) {
	if ($result.error) { Write-Output ("error=" + [string]$result.error) }
	exit 2
}
