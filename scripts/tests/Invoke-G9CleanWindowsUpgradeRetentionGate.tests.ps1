$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$gateScript = Join-Path $repositoryRoot 'scripts\Invoke-G9CleanWindowsUpgradeRetentionGate.ps1'

function Invoke-FailFastCase {
	param(
		[Parameter(Mandatory = $true)][string[]]$Arguments,
		[Parameter(Mandatory = $true)][string]$ExpectedMessage
	)

	$output = & pwsh -NoProfile -ExecutionPolicy Bypass -File $gateScript @Arguments 2>&1 | Out-String
	$exitCode = $LASTEXITCODE
	if ($exitCode -eq 0) {
		throw "Expected fail-fast case to fail: $($Arguments -join ' ')"
	}
	if ($output -notmatch [regex]::Escape($ExpectedMessage)) {
		throw "Expected fail-fast message '$ExpectedMessage' but got: $output"
	}
}

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($gateScript, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count -gt 0) {
	throw "G9.5 gate PowerShell parse failed: $($errors[0].Message)"
}

Invoke-FailFastCase `
	-Arguments @('-FinalRcReplay', '-ExpectedSourceCommit', 'bad', '-ExpectedCurrentInstallerSha256', 'bad') `
	-ExpectedMessage 'Final RC replay requires -ExpectedCurrentInstallerSha256 with a full 64-character SHA-256.'

Invoke-FailFastCase `
	-Arguments @(
		'-ExpectedSourceCommit', ('0' * 40),
		'-ExpectedCurrentInstallerSha256', ('0' * 64)
	) `
	-ExpectedMessage '-ExpectedCurrentInstallerSha256 and -ExpectedSourceCommit are only valid together with -FinalRcReplay.'

$content = Get-Content -LiteralPath $gateScript -Raw
$promotionAssignments = [regex]::Matches($content, '\$result\.gatePromotionReady\s*=\s*\$true').Count
if ($promotionAssignments -ne 1) {
	throw "Expected exactly one gatePromotionReady=true assignment; found $promotionAssignments."
}
if ($content -notmatch '\$FinalRcReplay\s+-and\s+\$result\.finalRcSourceWorktreeClean\s+-and\s+\$result\.passed\s+-and\s+\$result\.testMarkersRemoved') {
	throw 'Final RC promotion must require FinalRcReplay, a clean build-affecting worktree, passed=true, and testMarkersRemoved=true.'
}
if ($content -notmatch 'status --porcelain --untracked-files=all') {
	throw 'Final RC replay must inspect tracked and untracked worktree changes before touching the guest.'
}
if ($content -notmatch "allowedDirtyPrefixes = @\('docs/history-session/', 'evidence/'\)") {
	throw 'Final RC replay may only exempt history-session and evidence directories from the clean-source check.'
}

Write-Output 'G9.5 final-RC replay contract tests passed.'
