[CmdletBinding()]
param(
	[string]$VmName = 'Copperbench-G7',
	[string]$InstallerPath = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'build\export\Copperbench 0.1.0 Windows 64bit.exe'),
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$GuestUser = 'g7admin',
	[string]$PasswordFile = 'D:\Hyper-V\G7\g7admin.password.txt',
	[string]$InstallDir = 'C:\Copperbench-Stage14-SourceIntegrity',
	[string]$GuestRoot = 'C:\Temp\Copperbench-Stage14-SourceIntegrity',
	[string]$SourceDeltaSha256 = ''
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

$fixtureRoot = Join-Path $RepositoryRoot 'build\stage13-diagnostics-fixture\valid'
$workspaceName = 'stage13_diagnostics.mcreator'
if (-not (Test-Path -LiteralPath $InstallerPath -PathType Leaf)) { throw "Installer not found: $InstallerPath" }
if (-not (Test-Path -LiteralPath $PasswordFile -PathType Leaf)) { throw "Guest password file not found: $PasswordFile" }
if (-not (Test-Path -LiteralPath (Join-Path $fixtureRoot $workspaceName) -PathType Leaf)) {
	throw "Valid product-readable fixture missing: $fixtureRoot"
}
if ($SourceDeltaSha256 -and $SourceDeltaSha256 -notmatch '^[0-9a-fA-F]{64}$') {
	throw '-SourceDeltaSha256 must be a full SHA-256 when supplied.'
}

$plainPassword = (Get-Content -LiteralPath $PasswordFile -Raw).Trim()
$credential = [pscredential]::new($GuestUser, (ConvertTo-SecureString $plainPassword -AsPlainText -Force))
$candidateHead = (git -C $RepositoryRoot rev-parse HEAD).Trim()
$candidateSha256 = (Get-FileHash -LiteralPath $InstallerPath -Algorithm SHA256).Hash.ToLowerInvariant()
$sourceDelta = if ($SourceDeltaSha256) { $SourceDeltaSha256.ToLowerInvariant() } else { $null }
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage14\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$hostResultPath = Join-Path $evidenceDir 'source-integrity-clean-windows11.json'
$hostErrorPath = Join-Path $evidenceDir 'source-integrity-clean-windows11-error.txt'
Remove-Item -LiteralPath $hostErrorPath -Force -ErrorAction SilentlyContinue
$fixtureArchive = Join-Path $RepositoryRoot 'build\stage14-source-integrity-fixture.zip'
Remove-Item -LiteralPath $fixtureArchive -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $fixtureRoot '*') -DestinationPath $fixtureArchive -CompressionLevel Fastest
Write-Output ('stage=archive-ready zipBytes='+(Get-Item -LiteralPath $fixtureArchive).Length)

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
	[Parameter(Mandatory = $true)][string]$CandidateHead,
	[Parameter(Mandatory = $true)][string]$CandidateSha256,
	[string]$SourceDeltaSha256
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class Stage14SourceInput {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetClassName(IntPtr hWnd, StringBuilder text, int maxCount);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int maxCount);
}
"@

$ResultPath = Join-Path $GuestRoot 'source-integrity-clean-windows11.json'
$ErrorPath = Join-Path $GuestRoot 'source-integrity-clean-windows11-error.txt'
$launcher = Join-Path $InstallDir 'copperbench.exe'
$workspaceRoot = Split-Path -Parent $WorkspaceFile
$descriptorPath = Join-Path $workspaceRoot '.copperbench\mcp-connection.json'
$token = $null
$url = $null
$workspaceId = $null
$sessionId = $null
$desktopProcess = $null
$steps = [System.Collections.Generic.List[object]]::new()
$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'stage14-source-integrity-clean-windows11'
	candidateSourceHead = $CandidateHead
	candidateSourceDirty = -not [string]::IsNullOrWhiteSpace($SourceDeltaSha256)
	candidateSourceDeltaSha256 = if ($SourceDeltaSha256) { $SourceDeltaSha256 } else { $null }
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
	$handle = [Stage14SourceInput]::GetForegroundWindow()
	$classBuilder = [Text.StringBuilder]::new(256)
	$textBuilder = [Text.StringBuilder]::new(512)
	if ($handle -ne [IntPtr]::Zero) {
		$null = [Stage14SourceInput]::GetClassName($handle, $classBuilder, $classBuilder.Capacity)
		$null = [Stage14SourceInput]::GetWindowText($handle, $textBuilder, $textBuilder.Capacity)
	}
	[pscustomobject]@{ handle=$handle; className=$classBuilder.ToString(); text=$textBuilder.ToString() }
}

function Wait-GeneratorSetupDialog([IntPtr]$WindowHandle, [int]$TimeoutSeconds = 480) {
	[Stage14SourceInput]::SetForegroundWindow($WindowHandle) | Out-Null
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
		[Stage14SourceInput]::SetForegroundWindow($WindowHandle) | Out-Null
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

function Call-Tool([int]$Id, [string]$Name, [object]$Arguments) {
	$response = Invoke-McpPost ([ordered]@{
		jsonrpc='2.0'; id=$Id; method='tools/call'; params=[ordered]@{ name=$Name; arguments=$Arguments }
	}) $sessionId
	Assert-Gate ($response.StatusCode -eq 200) "$Name returned HTTP $($response.StatusCode)."
	Convert-McpToolResult $response
}

function Diagnostic-Text($ToolResult) {
	if ($null -eq $ToolResult.diagnostics) { return '' }
	return ($ToolResult.diagnostics | ConvertTo-Json -Depth 20 -Compress)
}

function Find-JavaWithMarker([string]$Marker) {
	$matches = @(Get-ChildItem -LiteralPath $workspaceRoot -Recurse -File -Filter '*.java' | Where-Object {
		try { (Get-Content -LiteralPath $_.FullName -Raw).Contains($Marker) } catch { $false }
	})
	Assert-Gate ($matches.Count -eq 1) "Expected one Java file containing $Marker, found $($matches.Count)."
	return $matches[0].FullName
}

try {
	Assert-Gate (Test-Path -LiteralPath $launcher -PathType Leaf) "Installed launcher missing: $launcher"
	Assert-Gate (Test-Path -LiteralPath $WorkspaceFile -PathType Leaf) "Fixture workspace missing: $WorkspaceFile"

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
	[Stage14SourceInput]::ShowWindowAsync($hwnd, 3) | Out-Null
	Start-Sleep -Milliseconds 300
	$setup = Wait-GeneratorSetupDialog $hwnd 480
	Add-Step 'installed_generator_setup' @{ observed=[bool]$setup.observed; completed=$true; waitMs=[int]$setup.waitMs }

	[Stage14SourceInput]::SetForegroundWindow($hwnd) | Out-Null
	Start-Sleep -Milliseconds 500
	$copiedUrl = ''
	for ($uiAttempt = 1; $uiAttempt -le 12 -and $copiedUrl -ne [string]$descriptor.url; $uiAttempt++) {
		[System.Windows.Forms.Clipboard]::SetText('STAGE14_SOURCE_URL_SENTINEL')
		[System.Windows.Forms.SendKeys]::SendWait('^+m')
		Start-Sleep -Milliseconds 500
		1..3 | ForEach-Object { [System.Windows.Forms.SendKeys]::SendWait('{TAB}'); Start-Sleep -Milliseconds 90 }
		[System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
		Start-Sleep -Milliseconds 500
		$copiedUrl = [System.Windows.Forms.Clipboard]::GetText()
		if ($copiedUrl -ne [string]$descriptor.url) { Start-Sleep -Milliseconds 500 }
	}
	Assert-Gate ($copiedUrl -eq [string]$descriptor.url) 'Real UI Copy URL did not match the Desktop MCP descriptor.'
	[System.Windows.Forms.SendKeys]::SendWait('{TAB}')
	[System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
	Start-Sleep -Milliseconds 900
	[System.Windows.Forms.Clipboard]::SetText('STAGE14_SOURCE_CONFIG_SENTINEL')
	[System.Windows.Forms.SendKeys]::SendWait('^+m')
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
	Add-Step 'desktop_mcp_credential_from_real_ui' @{ descriptorMatched=$true; tokenPersisted=$false; clipboardCleared=$true }

	$initialize = [ordered]@{
		jsonrpc='2.0'; id=1; method='initialize'; params=[ordered]@{
			protocolVersion='2025-11-25'; capabilities=@{}; clientInfo=[ordered]@{ name='stage14-source-integrity-installed-gate'; version='1.0' }
		}
	}
	$initialized = Invoke-McpPost $initialize
	Assert-Gate ($initialized.StatusCode -eq 200) 'Desktop MCP initialize failed.'
	$sessionId = [string]$initialized.Headers['mcp-session-id']
	Assert-Gate (-not [string]::IsNullOrWhiteSpace($sessionId)) 'Desktop MCP initialize returned no session id.'
	$null = Invoke-McpPost ([ordered]@{ jsonrpc='2.0'; method='notifications/initialized' }) $sessionId
	$callId = 2
	$current = Call-Tool $callId 'get_workspace' @{}
	$callId++
	$revision = [int64]$current.revision

	# CB-AUDIT-01: a real pre-existing source file may not be silently claimed/overwritten by an Agent create.
	$unmanaged = Join-Path $workspaceRoot 'src\main\java\net\mcreator\stage13_diagnostics\unmanaged_owner_probe.java'
	New-Item -ItemType Directory -Force -Path (Split-Path -Parent $unmanaged) | Out-Null
	$unmanagedBefore = "package net.mcreator.stage13_diagnostics;`npublic final class unmanaged_owner_probe { public static final int IDE_OWNS = 5; }`n"
	[IO.File]::WriteAllText($unmanaged, $unmanagedBefore)
	$ownershipCreate = [ordered]@{
		elementType='code'; name='unmanaged_owner_probe'; expectedRevision=$revision
		initialValues=[ordered]@{ code="package net.mcreator.stage13_diagnostics; public final class unmanaged_owner_probe { public static final int AGENT_WOULD_OVERWRITE = 9; }" }
	}
	$ownershipResult = Call-Tool $callId 'create_mod_element' $ownershipCreate
	$callId++
	Assert-Gate ([string]$ownershipResult.status -eq 'rejected') 'Desktop MCP unexpectedly claimed a pre-existing unmanaged Java source.'
	Assert-Gate ((Get-Content -LiteralPath $unmanaged -Raw) -eq $unmanagedBefore) 'Rejected ownership create changed the pre-existing source bytes.'
	$current = Call-Tool $callId 'get_workspace' @{}
	$callId++
	Assert-Gate ([int64]$current.revision -eq $revision) 'Rejected ownership create changed the workspace revision.'
	Add-Step 'desktop_mcp_unmanaged_source_ownership' @{
		status='rejected'; revisionPreserved=$true; sourceBytesPreserved=$true; diagnostics=(Diagnostic-Text $ownershipResult)
	}

	# CB-AUDIT-02: metadata-only edits preserve external IDE source; a later explicit stale source write is rejected.
	$initialCode = "package net.mcreator.stage13_diagnostics; public final class SourceIntegrityProbe { public static final int STAGE14_INITIAL = 1; }"
	$created = Call-Tool $callId 'create_mod_element' ([ordered]@{
		elementType='code'; name='source_integrity_probe'; expectedRevision=$revision; initialValues=[ordered]@{ code=$initialCode }
	})
	$callId++
	Assert-Gate ([string]$created.status -eq 'committed') 'Installed Desktop MCP could not create the source-integrity code probe.'
	$probeId = [string]$created.data.element.id
	$revision = [int64]$created.newRevision
	$probeSource = Find-JavaWithMarker 'STAGE14_INITIAL'
	$externalOne = (Get-Content -LiteralPath $probeSource -Raw).Replace('STAGE14_INITIAL = 1', 'IDE_EDIT_ONE = 17')
	[IO.File]::WriteAllText($probeSource, $externalOne)
	$metadataOnly = Call-Tool $callId 'update_mod_element' ([ordered]@{
		elementId=$probeId; expectedRevision=$revision; changes=@([ordered]@{ path='/displayName'; value='Stage 14 Metadata Only' })
	})
	$callId++
	Assert-Gate ([string]$metadataOnly.status -eq 'committed') 'Metadata-only update failed after an external IDE edit.'
	$revision = [int64]$metadataOnly.newRevision
	Assert-Gate ((Get-Content -LiteralPath $probeSource -Raw) -eq $externalOne) 'Metadata-only update replayed stale source over the IDE edit.'
	$externalTwo = $externalOne.Replace('IDE_EDIT_ONE = 17', 'IDE_EDIT_TWO = 23')
	[IO.File]::WriteAllText($probeSource, $externalTwo)
	$agentCode = "package net.mcreator.stage13_diagnostics; public final class SourceIntegrityProbe { public static final int AGENT_EDIT = 29; }"
	$staleDirect = Call-Tool $callId 'update_mod_element' ([ordered]@{
		elementId=$probeId; expectedRevision=$revision; changes=@([ordered]@{ path='/code'; value=$agentCode })
	})
	$callId++
	Assert-Gate ([string]$staleDirect.status -eq 'rejected') 'A stale explicit source write unexpectedly committed.'
	Assert-Gate ((Diagnostic-Text $staleDirect).Contains('SOURCE_CONTENT_CONFLICT')) 'Stale source rejection did not expose SOURCE_CONTENT_CONFLICT.'
	Assert-Gate ((Get-Content -LiteralPath $probeSource -Raw) -eq $externalTwo) 'Stale source rejection changed the newer IDE bytes.'
	$current = Call-Tool $callId 'get_workspace' @{}
	$callId++
	Assert-Gate ([int64]$current.revision -eq $revision) 'Stale source rejection changed the workspace revision.'
	Add-Step 'desktop_mcp_metadata_preservation_and_stale_source' @{
		metadataStatus='committed'; staleStatus='rejected'; diagnostic='SOURCE_CONTENT_CONFLICT'; sourceBytesPreserved=$true; revisionPreserved=$true
	}

	# CB-AUDIT-02 Workspace Plan variant: a valid issued plan becomes source-stale after an IDE edit.
	$planInitial = "package net.mcreator.stage13_diagnostics; public final class PlanIntegrityProbe { public static final int PLAN_INITIAL = 31; }"
	$planCreated = Call-Tool $callId 'create_mod_element' ([ordered]@{
		elementType='code'; name='plan_integrity_probe'; expectedRevision=$revision; initialValues=[ordered]@{ code=$planInitial }
	})
	$callId++
	Assert-Gate ([string]$planCreated.status -eq 'committed') 'Installed Desktop MCP could not create the Workspace Plan probe.'
	$planElementId = [string]$planCreated.data.element.id
	$revision = [int64]$planCreated.newRevision
	$planSource = Find-JavaWithMarker 'PLAN_INITIAL'
	$plannedCode = "package net.mcreator.stage13_diagnostics; public final class PlanIntegrityProbe { public static final int PLAN_AGENT_EDIT = 37; }"
	$plannedResult = Call-Tool $callId 'plan_workspace_changes' ([ordered]@{
		expectedRevision=$revision; idempotencyKey='stage14-installed-source-stale-plan'; requireRecoveryPoint=$false
		operations=@([ordered]@{
			operation='update_mod_element'; payload=[ordered]@{
				elementId=$planElementId; changes=@([ordered]@{ path='/code'; value=$plannedCode })
			}
		})
	})
	$callId++
	Assert-Gate ([string]$plannedResult.status -eq 'succeeded') 'Workspace Plan could not be issued before the IDE edit.'
	$plan = $plannedResult.data
	$externalPlan = (Get-Content -LiteralPath $planSource -Raw).Replace('PLAN_INITIAL = 31', 'PLAN_IDE_EDIT = 41')
	[IO.File]::WriteAllText($planSource, $externalPlan)
	$preview = Call-Tool $callId 'preview_workspace_plan' ([ordered]@{ plan=$plan })
	$callId++
	Assert-Gate ([string]$preview.status -eq 'failed') 'Source-stale Workspace Plan preview unexpectedly remained applicable.'
	Assert-Gate ((Diagnostic-Text $preview).Contains('WORKSPACE_PLAN_SOURCE_CONFLICT')) 'Source-stale preview did not expose WORKSPACE_PLAN_SOURCE_CONFLICT.'
	$applied = Call-Tool $callId 'apply_workspace_plan' ([ordered]@{ plan=$plan; expectedRevision=$revision })
	$callId++
	Assert-Gate ([string]$applied.status -eq 'rejected') 'Source-stale Workspace Plan unexpectedly committed.'
	Assert-Gate ((Diagnostic-Text $applied).Contains('WORKSPACE_PLAN_SOURCE_CONFLICT')) 'Source-stale apply did not expose WORKSPACE_PLAN_SOURCE_CONFLICT.'
	Assert-Gate ((Get-Content -LiteralPath $planSource -Raw) -eq $externalPlan) 'Source-stale Workspace Plan changed the IDE bytes.'
	$current = Call-Tool $callId 'get_workspace' @{}
	$callId++
	Assert-Gate ([int64]$current.revision -eq $revision) 'Source-stale Workspace Plan changed the workspace revision.'
	Add-Step 'desktop_mcp_workspace_plan_source_staleness' @{
		planIssued=$true; previewStatus='failed'; applyStatus='rejected'; diagnostic='WORKSPACE_PLAN_SOURCE_CONFLICT'; sourceBytesPreserved=$true
	}

	$auditPath = Join-Path $workspaceRoot '.copperbench\automation-audit.jsonl'
	Assert-Gate (Test-Path -LiteralPath $auditPath -PathType Leaf) 'Automation audit log was not created by the real MCP session.'
	Assert-Gate (-not (Get-Content -LiteralPath $auditPath -Raw).Contains($token)) 'Automation audit unexpectedly persisted the MCP token.'
	Add-Step 'desktop_mcp_audit_secret_boundary' @{ auditPresent=$true; tokenAbsent=$true }

	$closed = $uiProcess.CloseMainWindow()
	Assert-Gate $closed 'Installed desktop did not accept normal close.'
	$closeDeadline = (Get-Date).AddSeconds(30)
	do { Start-Sleep -Milliseconds 400; $alive = Get-Process -Id $uiProcess.Id -ErrorAction SilentlyContinue }
	while ($null -ne $alive -and (Get-Date) -lt $closeDeadline)
	Assert-Gate ($null -eq $alive) 'Installed desktop process did not exit after the source-integrity replay.'
	Assert-Gate (-not (Test-Path -LiteralPath $descriptorPath -PathType Leaf)) 'Desktop MCP descriptor remained after normal close.'

	$result.passed = $true
	$result.finalRevision = $revision
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
	$result | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $ResultPath -Encoding UTF8
}
'@

$session = $null
try {
	$vm = Get-VM -Name $VmName -ErrorAction Stop
	if ($vm.State -ne 'Running') { Start-VM -Name $VmName | Out-Null; Start-Sleep -Seconds 5 }
	$session = New-GuestSession -TargetVm $VmName -Credential $credential
	Write-Output 'stage=session-ready'
	$preClean = Invoke-Command -Session $session -ArgumentList $GuestRoot, $InstallDir -ScriptBlock {
		param($Root, $TargetInstallDir)
		New-Item -ItemType Directory -Force -Path 'C:\Temp' | Out-Null
		New-Item -ItemType Directory -Force -Path $Root | Out-Null

		# This gate is a clean-install candidate replay. The long-lived validation VM is also
		# used by other release gates, while Copperbench has one machine-wide uninstall key.
		# Remove a prior registered candidate explicitly so the current install is not
		# accidentally exercising an upgrade path or inheriting a stale registration.
		$uninstallKey = 'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\Copperbench'
		$registered = Get-ItemProperty -LiteralPath $uninstallKey -ErrorAction SilentlyContinue
		$registeredUninstall = if ($registered) { [string]$registered.UninstallString } else { '' }
		$uninstallAttempted = $false
		$uninstallExitCode = $null
		$staleRegistrationRemoved = $false

		Get-Process copperbench, javaw -ErrorAction SilentlyContinue | Where-Object {
			try { $_.Path -and $_.Path.IndexOf('Copperbench', [StringComparison]::OrdinalIgnoreCase) -ge 0 } catch { $false }
		} | Stop-Process -Force -ErrorAction SilentlyContinue
		Start-Sleep -Milliseconds 500

		if ($registeredUninstall) {
			$uninstallPath = [Environment]::ExpandEnvironmentVariables($registeredUninstall).Trim().Trim('"')
			if (Test-Path -LiteralPath $uninstallPath -PathType Leaf) {
				$uninstallAttempted = $true
				$previousInstallDir = Split-Path -Parent $uninstallPath
				$uninstall = Start-Process -FilePath $uninstallPath -ArgumentList @('/S') -PassThru
				$uninstallStartedAt = Get-Date
				$uninstallDeadline = $uninstallStartedAt.AddMinutes(5)
				$uninstallRelatedProcessCount = 0
				do {
					Start-Sleep -Milliseconds 500
					$registrationPresent = Test-Path -LiteralPath $uninstallKey
					$uninstallerPresent = Test-Path -LiteralPath $uninstallPath -PathType Leaf
					$uninstallRelatedProcessCount = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
						($_.ExecutablePath -and $_.ExecutablePath.StartsWith($previousInstallDir, [StringComparison]::OrdinalIgnoreCase)) -or
						($_.CommandLine -and $_.CommandLine.IndexOf($previousInstallDir, [StringComparison]::OrdinalIgnoreCase) -ge 0)
					}).Count
					$uninstallComplete = -not $registrationPresent -and -not $uninstallerPresent -and ($uninstallRelatedProcessCount -eq 0)
				} while (-not $uninstallComplete -and (Get-Date) -lt $uninstallDeadline)
				try {
					$uninstall.Refresh()
					if ($uninstall.HasExited) { $uninstallExitCode = $uninstall.ExitCode }
				} catch { }
				if (-not $uninstallComplete) {
					throw "Previous Copperbench uninstall did not fully finish before timeout: registrationPresent=$registrationPresent uninstallerPresent=$uninstallerPresent relatedProcesses=$uninstallRelatedProcessCount"
				}
				if ($null -ne $uninstallExitCode -and $uninstallExitCode -ne 0) { throw "Previous Copperbench uninstall exit code $uninstallExitCode" }
				Remove-Item -LiteralPath $previousInstallDir -Recurse -Force -ErrorAction SilentlyContinue
			} else {
				Remove-Item -LiteralPath $uninstallKey -Recurse -Force -ErrorAction SilentlyContinue
				$staleRegistrationRemoved = $true
			}
		}

		Get-Process copperbench, javaw -ErrorAction SilentlyContinue | Where-Object {
			try { $_.Path -and $_.Path.StartsWith($TargetInstallDir, [StringComparison]::OrdinalIgnoreCase) } catch { $false }
		} | Stop-Process -Force -ErrorAction SilentlyContinue
		Remove-Item -LiteralPath $TargetInstallDir -Recurse -Force -ErrorAction SilentlyContinue
		Remove-Item -LiteralPath (Join-Path $Root 'workspace'), (Join-Path $Root 'fixture.zip'),
			(Join-Path $Root 'source-integrity-clean-windows11.json'),
			(Join-Path $Root 'source-integrity-clean-windows11-error.txt') -Recurse -Force -ErrorAction SilentlyContinue

		[pscustomobject]@{
			registeredInstallDetected = [bool]$registeredUninstall
			uninstallAttempted = $uninstallAttempted
			uninstallExitCode = $uninstallExitCode
			staleRegistrationRemoved = $staleRegistrationRemoved
			uninstallRegistrationPresentAfterCleanup = Test-Path -LiteralPath $uninstallKey
		}
	}

	Write-Output ('stage=preclean-ready details='+($preClean|ConvertTo-Json -Compress -Depth 4))
	$guestInstaller = Invoke-Command -Session $session -ArgumentList $candidateSha256 -ScriptBlock {
		param($ExpectedSha)
		$candidates = @(
			'C:\Temp\Copperbench-Stage14-Candidate.exe',
			'C:\Temp\Stage14ArgProbeInstaller.exe'
		)
		foreach ($path in $candidates) {
			if (Test-Path -LiteralPath $path -PathType Leaf) {
				$hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
				if ($hash -eq $ExpectedSha) { return $path }
			}
		}
		return ''
	}
	if (-not $guestInstaller) {
		$guestInstaller = 'C:\Temp\Copperbench-Stage14-Candidate.exe'
		Copy-Item -LiteralPath $InstallerPath -Destination $guestInstaller -ToSession $session -Force
	}
	Write-Output ('stage=candidate-ready path='+$guestInstaller)
	Copy-Item -LiteralPath $fixtureArchive -Destination (Join-Path $GuestRoot 'fixture.zip') -ToSession $session -Force
	$guestHash = Invoke-Command -Session $session -ArgumentList $guestInstaller -ScriptBlock {
		param($Path) (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
	}
	if ($guestHash -ne $candidateSha256) { throw "Guest installer SHA-256 mismatch: $guestHash" }

	$install = Invoke-Command -Session $session -ArgumentList $InstallDir, $guestInstaller -ScriptBlock {
		param($TargetInstallDir, $Installer)
		$process = Start-Process -FilePath $Installer -ArgumentList @('/S', "/D=$TargetInstallDir") -Wait -PassThru
		$exitCode = $process.ExitCode
		$deadline = (Get-Date).AddMinutes(10)
		$started = Get-Date
		$complete = $false
		$installerProcessCount = 0
		$registryMatches = $false
		do {
			$exePresent = Test-Path -LiteralPath (Join-Path $TargetInstallDir 'copperbench.exe') -PathType Leaf
			$javaPresent = Test-Path -LiteralPath (Join-Path $TargetInstallDir 'jdk\bin\java.exe') -PathType Leaf
			$java21Present = Test-Path -LiteralPath (Join-Path $TargetInstallDir 'jdk21\bin\java.exe') -PathType Leaf
			$uninstallerPath = Join-Path $TargetInstallDir 'uninstall.exe'
			$uninstallerPresent = Test-Path -LiteralPath $uninstallerPath -PathType Leaf
			$uninstallKey = 'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\Copperbench'
			$registration = Get-ItemProperty -LiteralPath $uninstallKey -ErrorAction SilentlyContinue
			$registryMatches = $registration -and ([string]$registration.UninstallString -eq $uninstallerPath)
			$installerProcessCount = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
				$_.ExecutablePath -and $_.ExecutablePath.Equals($Installer, [StringComparison]::OrdinalIgnoreCase)
			}).Count
			$complete = $exePresent -and $javaPresent -and $java21Present -and $uninstallerPresent -and $registryMatches -and ($installerProcessCount -eq 0)
			if (-not $complete) { Start-Sleep -Milliseconds 500 }
		} while (-not $complete -and (Get-Date) -lt $deadline)
		[pscustomobject]@{
			exitCode = $exitCode
			exePresent = $exePresent
			javaPresent = $javaPresent
			java21Present = $java21Present
			uninstallerPresent = $uninstallerPresent
			registryMatches = [bool]$registryMatches
			installerProcessCount = $installerProcessCount
			waitTimedOut = -not $complete
			postStartWaitMs = [int]((Get-Date) - $started).TotalMilliseconds
		}
	}
	if ($install.exitCode -ne 0 -or $install.waitTimedOut -or -not $install.exePresent -or -not $install.javaPresent -or -not $install.java21Present -or -not $install.uninstallerPresent -or -not $install.registryMatches -or $install.installerProcessCount -ne 0) {
		throw "Silent install failed: $($install | ConvertTo-Json -Compress)"
	}

	Write-Output ('stage=install-ready details='+($install|ConvertTo-Json -Compress -Depth 4))
	Invoke-Command -Session $session -ArgumentList $GuestRoot, $guestScript, $InstallDir, $candidateHead,
		$candidateSha256, $sourceDelta, $GuestUser, $workspaceName -ScriptBlock {
		param($Root, $Body, $TargetInstallDir, $Head, $Sha, $Delta, $TargetUser, $WorkspaceName)
		$workspaceRoot = Join-Path $Root 'workspace'
		New-Item -ItemType Directory -Force -Path $workspaceRoot | Out-Null
		Expand-Archive -LiteralPath (Join-Path $Root 'fixture.zip') -DestinationPath $workspaceRoot -Force
		$scriptPath = Join-Path $Root 'Invoke-SourceIntegrityGate.ps1'
		Set-Content -LiteralPath $scriptPath -Value $Body -Encoding UTF8
		$workspaceFile = Join-Path $workspaceRoot $WorkspaceName
		$taskName = 'Copperbench-Stage14-SourceIntegrity-Gate'
		Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
		$arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`" -InstallDir `"$TargetInstallDir`" -WorkspaceFile `"$workspaceFile`" -GuestRoot `"$Root`" -CandidateHead `"$Head`" -CandidateSha256 `"$Sha`""
		if ($Delta) { $arguments += " -SourceDeltaSha256 `"$Delta`"" }
		$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
		$principal = New-ScheduledTaskPrincipal -UserId $TargetUser -LogonType Interactive -RunLevel Highest
		Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Force | Out-Null
		Start-ScheduledTask -TaskName $taskName
	}

	Write-Output 'stage=guest-task-started'
	$guestResultPath = Join-Path $GuestRoot 'source-integrity-clean-windows11.json'
	$deadline = (Get-Date).AddMinutes(15)
	do {
		Start-Sleep -Seconds 2
		$ready = Invoke-Command -Session $session -ArgumentList $guestResultPath -ScriptBlock {
			param($Path) Test-Path -LiteralPath $Path -PathType Leaf
		}
	} while (-not $ready -and (Get-Date) -lt $deadline)
	if (-not $ready) { throw 'Stage 14 source-integrity guest gate timed out.' }
	Copy-Item -LiteralPath $guestResultPath -Destination $hostResultPath -FromSession $session -Force
	$guestErrorPath = Join-Path $GuestRoot 'source-integrity-clean-windows11-error.txt'
	$errorExists = Invoke-Command -Session $session -ArgumentList $guestErrorPath -ScriptBlock {
		param($Path) Test-Path -LiteralPath $Path -PathType Leaf
	}
	if ($errorExists) { Copy-Item -LiteralPath $guestErrorPath -Destination $hostErrorPath -FromSession $session -Force }
} finally {
	if ($session) { Remove-PSSession $session -ErrorAction SilentlyContinue }
}

$result = Get-Content -LiteralPath $hostResultPath -Raw | ConvertFrom-Json
$installVerification = [ordered]@{
	guestInstallerSha256 = $guestHash
	preClean = [ordered]@{
		registeredInstallDetected = [bool]$preClean.registeredInstallDetected
		uninstallAttempted = [bool]$preClean.uninstallAttempted
		uninstallExitCode = $preClean.uninstallExitCode
		staleRegistrationRemoved = [bool]$preClean.staleRegistrationRemoved
		uninstallRegistrationPresentAfterCleanup = [bool]$preClean.uninstallRegistrationPresentAfterCleanup
	}
	exitCode = $install.exitCode
	exePresent = [bool]$install.exePresent
	javaPresent = [bool]$install.javaPresent
	java21Present = [bool]$install.java21Present
}
$result | Add-Member -NotePropertyName installVerification -NotePropertyValue ([pscustomobject]$installVerification) -Force
$result | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $hostResultPath -Encoding UTF8
Write-Output ("passed=" + [bool]$result.passed)
Write-Output ("candidateSourceHead=" + [string]$result.candidateSourceHead)
Write-Output ("candidateSourceDeltaSha256=" + [string]$result.candidateSourceDeltaSha256)
Write-Output ("candidateInstallerSha256=" + [string]$result.candidateInstallerSha256)
Write-Output ("steps=" + @($result.steps).Count)
Write-Output ("evidence=" + $hostResultPath)
if (-not $result.passed) {
	if ($result.error) { Write-Output ("error=" + [string]$result.error) }
	exit 2
}
