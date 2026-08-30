[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$verifier = (Resolve-Path (Join-Path $PSScriptRoot '..\verify-release-source.ps1')).Path
$testRoot = Join-Path ([IO.Path]::GetTempPath()) "copperbench-release-source-$([guid]::NewGuid())"
$repository = Join-Path $testRoot 'repository'
$keyPath = Join-Path $testRoot 'release-key'

function Invoke-TestGit {
	param([string[]] $Arguments)
	& git -C $repository @Arguments | Out-Null
	if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') failed" }
}

function Invoke-Verifier {
	param([string] $Tag, [string] $MainCommit)
	$output = @(& pwsh -NoProfile -File $verifier -Tag $Tag -ExpectedMainCommit $MainCommit -RepositoryRoot $repository 2>&1)
	return @{ ExitCode = $LASTEXITCODE; Output = $output -join "`n" }
}

[IO.Directory]::CreateDirectory($repository) | Out-Null
try {
	& ssh-keygen -q -t ed25519 -N '' -f $keyPath
	if ($LASTEXITCODE -ne 0) { throw 'Unable to generate temporary SSH signing key' }
	Invoke-TestGit -Arguments @('init')
	Invoke-TestGit -Arguments @('config', 'user.name', 'Release Test')
	Invoke-TestGit -Arguments @('config', 'user.email', 'release-test@example.invalid')
	[IO.Directory]::CreateDirectory((Join-Path $repository 'src/main/resources')) | Out-Null
	[IO.Directory]::CreateDirectory((Join-Path $repository '.github')) | Out-Null
	Set-Content -LiteralPath (Join-Path $repository 'src/main/resources/mcreator.conf') -Value 'product.version=0.1.0' -Encoding utf8
	$initialStatus = @{
		product = @{ betaEligible = $false }
		delivery = @{ betaRelease = @{ tag = 'v0.1.0-beta.1'; status = 'blocked' } }
		gates = @(@{ id = 'five-external-testers'; betaBlocking = $true; status = 'blocked' })
	} | ConvertTo-Json -Depth 8
	Set-Content -LiteralPath (Join-Path $repository 'product-status.json') -Value $initialStatus -Encoding utf8
	$publicKey = (Get-Content -LiteralPath "$keyPath.pub" -Raw).Trim()
	Set-Content -LiteralPath (Join-Path $repository '.github/release-signers') -Value "release-test@example.invalid $publicKey" -Encoding utf8
	Invoke-TestGit -Arguments @('add', '.')
	Invoke-TestGit -Arguments @('commit', '-m', 'tested preview candidate source')
	$head = (& git -C $repository rev-parse HEAD).Trim()
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $head)
	& git -C $repository -c gpg.format=ssh -c "user.signingkey=$keyPath" tag -s -a v0.1.0-preview.3 -m 'signed release'
	if ($LASTEXITCODE -ne 0) { throw 'Unable to create signed test tag' }
	Invoke-TestGit -Arguments @('tag', '-a', 'v0.1.0-preview.4', '-m', 'unsigned release')

	$valid = Invoke-Verifier -Tag 'v0.1.0-preview.3' -MainCommit $head
	if ($valid.ExitCode -ne 0 -or $valid.Output -notmatch "releaseSource=$head") {
		throw "Valid signed release source was rejected:`n$($valid.Output)"
	}

	$g95EvidenceRelative = 'evidence/stage-9/2026-08-30/clean-windows11-upgrade-offline-retention.json'
	$g95EvidencePath = Join-Path $repository $g95EvidenceRelative
	[IO.Directory]::CreateDirectory((Split-Path -Parent $g95EvidencePath)) | Out-Null
	$g95Evidence = [ordered]@{
		schemaVersion = '1.0'
		kind = 'stage9-clean-windows11-upgrade-offline-retention'
		finalRcReplayRequested = $true
		finalRcSourceCommit = $head
		finalRcSourceWorktreeClean = $true
		currentInstallerSha256 = ('1' * 64)
		passed = $true
		testMarkersRemoved = $true
		finalRcReplayRequired = $false
		gatePromotionReady = $true
	} | ConvertTo-Json -Depth 8
	Set-Content -LiteralPath $g95EvidencePath -Value $g95Evidence -Encoding utf8

	$externalEvidenceDirectory = Join-Path $repository 'evidence/stage-9/external-testers'
	[IO.Directory]::CreateDirectory($externalEvidenceDirectory) | Out-Null
	$testerTasks = [ordered]@{
		downloaded = 'passed'
		hashVerified = 'passed'
		installed = 'passed'
		workspaceCreatedOrImported = 'passed'
		elementCreated = 'passed'
		buildCompleted = 'passed'
		failureInduced = 'passed'
		diagnosticInspected = 'passed'
		diagnosticBundleExported = 'passed'
		recoveryPointCreated = 'passed'
		recoveryRestored = 'passed'
		uninstalled = 'passed'
		workspaceRetainedAfterUninstall = 'passed'
	}
	foreach ($index in 1..5) {
		$testerRecord = [ordered]@{
			schemaVersion = '1.0'
			testerId = ('tester-{0:x8}' -f $index)
			nonCoreDeveloper = $true
			testedAt = '2026-08-30T10:00:00Z'
			source = [ordered]@{
				version = '0.1.0'
				commit = $head
				installerSha256 = ('1' * 64)
				packageType = 'exe'
			}
			environment = [ordered]@{
				windowsVersion = 'Windows 11'
				windowsBuild = '26100'
				architecture = 'x64'
				preinstalledDeveloperTools = @()
			}
			tasks = $testerTasks
			issues = @()
			privacyConfirmed = $true
			result = 'passed'
		}
		$testerRecord | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $externalEvidenceDirectory "tester-$index.json") -Encoding utf8
	}
	$eligibleStatus = @{
		product = @{ betaEligible = $true }
		delivery = @{
			betaRelease = @{
				tag = 'v0.1.0-beta.1'
				status = 'ready'
				candidateRelease = @{
					tag = 'v0.1.0-preview.3'
					sourceCommit = $head
					assets = @{
						exe = @{ name = 'Copperbench.0.1.0.Windows.64bit.exe'; sha256 = ('1' * 64) }
						zip = @{ name = 'Copperbench.0.1.0.Windows.64bit.zip'; sha256 = ('2' * 64) }
						msix = @{ name = 'Copperbench.0.1.0.Windows.64bit.msix'; sha256 = ('3' * 64) }
						sbom = @{ name = 'copperbench.spdx.json'; sha256 = ('4' * 64) }
					}
				}
			}
		}
		gates = @(
			@{ id = 'real-jcef-accessibility'; betaBlocking = $true; status = 'passed'; evidence = @() },
			@{ id = 'clean-windows-11-stage9'; betaBlocking = $true; status = 'passed'; evidence = @($g95EvidenceRelative) },
			@{ id = 'five-external-testers'; betaBlocking = $true; status = 'passed'; evidence = @('evidence/stage-9/external-testers') }
		)
	} | ConvertTo-Json -Depth 12
	Set-Content -LiteralPath (Join-Path $repository 'product-status.json') -Value $eligibleStatus -Encoding utf8
	Invoke-TestGit -Arguments @('add', 'product-status.json', 'evidence/stage-9')
	Invoke-TestGit -Arguments @('commit', '-m', 'promote tested preview to beta')
	$betaHead = (& git -C $repository rev-parse HEAD).Trim()
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $betaHead)
	& git -C $repository -c gpg.format=ssh -c "user.signingkey=$keyPath" tag -s -a v0.1.0-beta.1 -m 'signed beta release'
	if ($LASTEXITCODE -ne 0) { throw 'Unable to create signed beta test tag' }
	$validBeta = Invoke-Verifier -Tag 'v0.1.0-beta.1' -MainCommit $betaHead
	if ($validBeta.ExitCode -ne 0 -or $validBeta.Output -notmatch "releaseSource=$betaHead") {
		throw "Valid signed beta release source was rejected:`n$($validBeta.Output)"
	}

	$badPathStatus = $eligibleStatus | ConvertFrom-Json -Depth 12
	$badPathStatus.delivery.betaRelease.tag = 'v0.1.0-beta.6'
	$badPathStatus.delivery.betaRelease.candidateRelease.assets.exe.name = '../escape.exe'
	$badPathStatus | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $repository 'product-status.json') -Encoding utf8
	Invoke-TestGit -Arguments @('add', 'product-status.json')
	Invoke-TestGit -Arguments @('commit', '-m', 'invalid beta candidate asset path')
	$badPathHead = (& git -C $repository rev-parse HEAD).Trim()
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $badPathHead)
	& git -C $repository -c gpg.format=ssh -c "user.signingkey=$keyPath" tag -s -a v0.1.0-beta.6 -m 'invalid beta candidate asset path'
	if ($LASTEXITCODE -ne 0) { throw 'Unable to create invalid candidate-path Beta tag' }
	$badPathBeta = Invoke-Verifier -Tag 'v0.1.0-beta.6' -MainCommit $badPathHead
	if ($badPathBeta.ExitCode -eq 0 -or $badPathBeta.Output -notmatch 'plain Windows-safe file name') {
		throw "Beta release with candidate path traversal was not rejected:`n$($badPathBeta.Output)"
	}
	Invoke-TestGit -Arguments @('checkout', '--detach', $betaHead)
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $betaHead)

	$badTesterRecordPath = Join-Path $externalEvidenceDirectory 'tester-3.json'
	$badTesterRecord = Get-Content -LiteralPath $badTesterRecordPath -Raw | ConvertFrom-Json -Depth 12
	$badTesterRecord.source.packageType = 'zip'
	$badTesterRecord | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $badTesterRecordPath -Encoding utf8
	$badTesterStatus = $eligibleStatus | ConvertFrom-Json -Depth 12
	$badTesterStatus.delivery.betaRelease.tag = 'v0.1.0-beta.8'
	$badTesterStatus | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $repository 'product-status.json') -Encoding utf8
	Invoke-TestGit -Arguments @('add', 'product-status.json', 'evidence/stage-9/external-testers/tester-3.json')
	Invoke-TestGit -Arguments @('commit', '-m', 'invalid beta tester binary')
	$badTesterHead = (& git -C $repository rev-parse HEAD).Trim()
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $badTesterHead)
	& git -C $repository -c gpg.format=ssh -c "user.signingkey=$keyPath" tag -s -a v0.1.0-beta.8 -m 'invalid beta tester binary'
	if ($LASTEXITCODE -ne 0) { throw 'Unable to create invalid tester Beta tag' }
	$badTesterBeta = Invoke-Verifier -Tag 'v0.1.0-beta.8' -MainCommit $badTesterHead
	if ($badTesterBeta.ExitCode -eq 0 -or $badTesterBeta.Output -notmatch 'external tester evidence is not complete') {
		throw "Beta release with mismatched tester binary was not rejected:`n$($badTesterBeta.Output)"
	}
	Invoke-TestGit -Arguments @('checkout', '--detach', $betaHead)
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $betaHead)

	$badG95 = Get-Content -LiteralPath $g95EvidencePath -Raw | ConvertFrom-Json -Depth 12
	$badG95.currentInstallerSha256 = ('9' * 64)
	$badG95 | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $g95EvidencePath -Encoding utf8
	$badG95Status = $eligibleStatus | ConvertFrom-Json -Depth 12
	$badG95Status.delivery.betaRelease.tag = 'v0.1.0-beta.7'
	$badG95Status | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $repository 'product-status.json') -Encoding utf8
	Invoke-TestGit -Arguments @('add', 'product-status.json', $g95EvidenceRelative)
	Invoke-TestGit -Arguments @('commit', '-m', 'invalid beta g95 binary')
	$badG95Head = (& git -C $repository rev-parse HEAD).Trim()
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $badG95Head)
	& git -C $repository -c gpg.format=ssh -c "user.signingkey=$keyPath" tag -s -a v0.1.0-beta.7 -m 'invalid beta g95 binary'
	if ($LASTEXITCODE -ne 0) { throw 'Unable to create invalid G9.5 Beta tag' }
	$badG95Beta = Invoke-Verifier -Tag 'v0.1.0-beta.7' -MainCommit $badG95Head
	if ($badG95Beta.ExitCode -eq 0 -or $badG95Beta.Output -notmatch 'no gatePromotionReady G9.5 final-RC evidence') {
		throw "Beta release with mismatched G9.5 binary was not rejected:`n$($badG95Beta.Output)"
	}
	Invoke-TestGit -Arguments @('checkout', '--detach', $betaHead)
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $betaHead)

	Set-Content -LiteralPath (Join-Path $repository 'src/main/resources/mcreator.conf') -Value "product.version=0.1.0`nchanged.after.candidate=true" -Encoding utf8
	$sourceChangedStatus = $eligibleStatus | ConvertFrom-Json -Depth 12
	$sourceChangedStatus.delivery.betaRelease.tag = 'v0.1.0-beta.9'
	$sourceChangedStatus | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $repository 'product-status.json') -Encoding utf8
	Invoke-TestGit -Arguments @('add', 'src/main/resources/mcreator.conf', 'product-status.json')
	Invoke-TestGit -Arguments @('commit', '-m', 'invalid source change after candidate')
	$sourceChangedHead = (& git -C $repository rev-parse HEAD).Trim()
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $sourceChangedHead)
	& git -C $repository -c gpg.format=ssh -c "user.signingkey=$keyPath" tag -s -a v0.1.0-beta.9 -m 'invalid changed beta source'
	if ($LASTEXITCODE -ne 0) { throw 'Unable to create changed-source beta test tag' }
	$changedBeta = Invoke-Verifier -Tag 'v0.1.0-beta.9' -MainCommit $sourceChangedHead
	if ($changedBeta.ExitCode -eq 0 -or $changedBeta.Output -notmatch 'build-affecting changes after the tested candidate') {
		throw "Beta release with build-affecting candidate delta was not rejected:`n$($changedBeta.Output)"
	}
	Invoke-TestGit -Arguments @('checkout', '--detach', $betaHead)
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $betaHead)

	$blockedStatus = @{
		product = @{ betaEligible = $false }
		delivery = @{ betaRelease = @{ tag = 'v0.1.0-beta.2' } }
		gates = @(@{ id = 'five-external-testers'; betaBlocking = $true; status = 'blocked' })
	} | ConvertTo-Json -Depth 8
	Set-Content -LiteralPath (Join-Path $repository 'product-status.json') -Value $blockedStatus -Encoding utf8
	Invoke-TestGit -Arguments @('add', 'product-status.json')
	Invoke-TestGit -Arguments @('commit', '-m', 'blocked beta source')
	$blockedHead = (& git -C $repository rev-parse HEAD).Trim()
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $blockedHead)
	& git -C $repository -c gpg.format=ssh -c "user.signingkey=$keyPath" tag -s -a v0.1.0-beta.2 -m 'blocked beta release'
	if ($LASTEXITCODE -ne 0) { throw 'Unable to create blocked beta test tag' }
	$blockedBeta = Invoke-Verifier -Tag 'v0.1.0-beta.2' -MainCommit $blockedHead
	if ($blockedBeta.ExitCode -eq 0 -or $blockedBeta.Output -notmatch 'betaEligible is not true') {
		throw "Blocked beta release source was not rejected:`n$($blockedBeta.Output)"
	}

	$openGateStatus = @{
		product = @{ betaEligible = $true }
		delivery = @{ betaRelease = @{ tag = 'v0.1.0-beta.3' } }
		gates = @(@{ id = 'real-jcef-accessibility'; betaBlocking = $true; status = 'blocked' })
	} | ConvertTo-Json -Depth 8
	Set-Content -LiteralPath (Join-Path $repository 'product-status.json') -Value $openGateStatus -Encoding utf8
	Invoke-TestGit -Arguments @('add', 'product-status.json')
	Invoke-TestGit -Arguments @('commit', '-m', 'beta with open gate')
	$openGateHead = (& git -C $repository rev-parse HEAD).Trim()
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $openGateHead)
	& git -C $repository -c gpg.format=ssh -c "user.signingkey=$keyPath" tag -s -a v0.1.0-beta.3 -m 'beta with open gate'
	if ($LASTEXITCODE -ne 0) { throw 'Unable to create open-gate beta test tag' }
	$openGateBeta = Invoke-Verifier -Tag 'v0.1.0-beta.3' -MainCommit $openGateHead
	if ($openGateBeta.ExitCode -eq 0 -or $openGateBeta.Output -notmatch 'blocked by betaBlocking gates') {
		throw "Beta release with an open blocker was not rejected:`n$($openGateBeta.Output)"
	}

	Invoke-TestGit -Arguments @('checkout', '--detach', $head)
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $head)
	$invalidChannel = Invoke-Verifier -Tag 'v0.1.0-rc.1' -MainCommit $head
	if ($invalidChannel.ExitCode -eq 0 -or $invalidChannel.Output -notmatch 'must match vX.Y.Z') {
		throw 'Unsupported release channel tag was not rejected'
	}
	$unsigned = Invoke-Verifier -Tag 'v0.1.0-preview.4' -MainCommit $head
	if ($unsigned.ExitCode -eq 0 -or $unsigned.Output -notmatch 'no valid signature') {
		throw 'Unsigned annotated release tag was not rejected'
	}
	$stale = Invoke-Verifier -Tag 'v0.1.0-preview.3' -MainCommit ('0' * 40)
	if ($stale.ExitCode -eq 0 -or $stale.Output -notmatch 'is not the latest main commit') {
		throw 'Release source that differs from main was not rejected'
	}
	& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'New-ReleaseMetadata.tests.ps1')
	if ($LASTEXITCODE -ne 0) { throw 'Beta exact-binary promotion tests failed' }
	Write-Output 'Release source verification tests passed.'
} finally {
	if (Test-Path -LiteralPath $testRoot) {
		Remove-Item -LiteralPath $testRoot -Recurse -Force
	}
}
