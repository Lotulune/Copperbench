$ErrorActionPreference = 'Stop'

function Assert-True {
	param([bool]$Condition, [string]$Message)
	if (-not $Condition) { throw $Message }
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$guestSmokePath = Join-Path $repositoryRoot 'scripts\Invoke-G9CleanWindowsGuestSmoke.ps1'
$lifecyclePath = Join-Path $repositoryRoot 'scripts\Invoke-G9CleanWindowsWorkspaceLifecycleGate.ps1'
$guestSmoke = Get-Content -LiteralPath $guestSmokePath -Raw
$lifecycle = Get-Content -LiteralPath $lifecyclePath -Raw

Assert-True ($guestSmoke -match 'textExtensions') 'Guest smoke must restrict IPC scanning to known text extensions.'
Assert-True ($guestSmoke -match 'copperbench.*gradle') 'Guest smoke must exclude the managed Gradle/JDK cache from IPC scanning.'

$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('copperbench-stage9-harness-' + [guid]::NewGuid().ToString('N'))
try {
	$root = Join-Path $tempRoot '.copperbench'
	$gradleDir = Join-Path $root 'gradle\cache'
	$logDir = Join-Path $root 'logs'
	New-Item -ItemType Directory -Force -Path $gradleDir, $logDir | Out-Null
	Set-Content -LiteralPath (Join-Path $gradleDir 'binary.jar') -Value 'BindException Address already in use' -Encoding UTF8
	Set-Content -LiteralPath (Join-Path $logDir 'clean.log') -Value 'normal product startup' -Encoding UTF8
	Set-Content -LiteralPath (Join-Path $logDir 'ipc-error.log') -Value 'Address already in use' -Encoding UTF8

	$textExtensions = @('.log', '.txt', '.json', '.xml', '.yaml', '.yml', '.properties', '.conf')
	$files = @(Get-ChildItem -LiteralPath $root -Recurse -File | Where-Object {
		$_.Length -lt 10MB -and
		$textExtensions -contains $_.Extension.ToLowerInvariant() -and
		$_.FullName -notmatch '[\\/]\.copperbench[\\/]gradle[\\/]'
	})
	Assert-True (-not [bool]($files | Where-Object Name -eq 'binary.jar')) 'Binary Gradle cache entries must not be scanned as IPC logs.'
	Assert-True ([bool]($files | Where-Object Name -eq 'ipc-error.log')) 'Real text logs must remain eligible for IPC scanning.'
	$hits = @($files | Select-String -SimpleMatch -Pattern 'Address already in use')
	Assert-True ($hits.Count -eq 1) 'IPC scan must detect the real log without matching binary cache content.'

	$sourceRoot = Join-Path $tempRoot 'workspace\src\main\java\net\mcreator'
	$etaDir = Join-Path $sourceRoot 'guigateeta'
	New-Item -ItemType Directory -Force -Path $etaDir | Out-Null
	$etaSource = Join-Path $etaDir 'GuigateetaMod.java'
	Set-Content -LiteralPath $etaSource -Value 'class GuigateetaMod {}' -Encoding UTF8
	$sourceCandidates = @(Get-ChildItem -LiteralPath $sourceRoot -Filter '*Mod.java' -Recurse -File | Sort-Object FullName)
	Assert-True ($sourceCandidates.Count -eq 1 -and $sourceCandidates[0].FullName -eq $etaSource) 'Lifecycle source discovery must not depend on guigatedelta.'
}
finally {
	Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Assert-True ($lifecycle -notmatch 'net\\mcreator\\guigatedelta\\GuigatedeltaMod\.java') 'Lifecycle must not hardcode the guigatedelta generated source path.'
Assert-True ($lifecycle -match "Filter '\*Mod\.java'") 'Lifecycle must discover generated mod source dynamically.'
Assert-True ($lifecycle -notmatch [regex]::Escape('-or $row.nativeWindowHandle -ne 0')) 'Lifecycle must not accept arbitrary Java windows.'
Assert-True ($lifecycle -match 'Failed to initialize the mod loading system and display') 'Lifecycle must reject NeoForge initialization-error windows.'
Assert-True ($lifecycle -match 'runClientInitializationErrorDetected') 'Lifecycle result must expose initialization-error detection.'

$knownFalsePositivePath = Join-Path $repositoryRoot 'evidence\stage-9\2026-09-01\clean-windows11-workspace-lifecycle-harness-false-positive.json'
$knownFalsePositive = Get-Content -LiteralPath $knownFalsePositivePath -Raw | ConvertFrom-Json
$tail = @($knownFalsePositive.runClient.latestLogTail | ForEach-Object { [string]$_ })
$fatal = [bool]($tail | Where-Object {
	$_ -match 'Failed to initialize the mod loading system and display' -or
	$_ -match 'Failed to find any valid GLFW profile' -or
	$_ -match 'Failed to find a valid GLFW profile'
} | Select-Object -First 1)
Assert-True $fatal 'Known Hyper-V false-positive evidence must be rejected by the hardened predicate.'

Write-Output 'Stage 9 clean Windows harness regression tests passed.'
