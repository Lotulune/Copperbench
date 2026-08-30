[CmdletBinding()]
param(
	[Parameter(Mandatory)]
	[string] $Tag,
	[string] $ExpectedMainCommit,
	[string] $RepositoryRoot = (Join-Path $PSScriptRoot '..'),
	[string] $AllowedSignersFile = '.github/release-signers'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$signersCandidate = if ([IO.Path]::IsPathRooted($AllowedSignersFile)) {
	$AllowedSignersFile
} else {
	Join-Path $repositoryRoot $AllowedSignersFile
}
$signersPath = [IO.Path]::GetFullPath($signersCandidate)
if (-not (Test-Path -LiteralPath $signersPath -PathType Leaf)) {
	throw "Release signer allowlist is missing: $signersPath"
}
Push-Location $repositoryRoot
try {
	if ($Tag -notmatch '^v\d+\.\d+\.\d+(?:-(?:preview|beta)\.\d+)?$') {
		throw "Release tag '$Tag' must match vX.Y.Z, vX.Y.Z-preview.N, or vX.Y.Z-beta.N"
	}
	$changes = @(git status --porcelain=v1 --untracked-files=all)
	if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect Git worktree status' }
	if ($changes.Count -gt 0) {
		throw "Release source is not clean:`n$($changes -join "`n")"
	}
	$head = (git rev-parse HEAD).Trim()
	$tagCommit = (git rev-parse "$Tag^{commit}").Trim()
	if ($LASTEXITCODE -ne 0 -or $head -ne $tagCommit) {
		throw "Tag $Tag does not resolve to checked-out HEAD $head"
	}
	$tagType = (git cat-file -t "refs/tags/$Tag").Trim()
	if ($LASTEXITCODE -ne 0 -or $tagType -ne 'tag') {
		throw "Release tag $Tag must be an annotated tag"
	}
	$signatureResult = @(git -c "gpg.ssh.allowedSignersFile=$signersPath" verify-tag $Tag 2>&1)
	if ($LASTEXITCODE -ne 0) {
		throw "Release tag $Tag has no valid signature from an allowed signer:`n$($signatureResult -join "`n")"
	}
	if ([string]::IsNullOrWhiteSpace($ExpectedMainCommit)) {
		$ExpectedMainCommit = (git rev-parse refs/remotes/origin/main).Trim()
		if ($LASTEXITCODE -ne 0) { throw 'Unable to resolve origin/main' }
	}
	if ($ExpectedMainCommit -notmatch '^[0-9a-fA-F]{40}$') {
		throw "Expected main commit is not a full SHA: $ExpectedMainCommit"
	}
	if ($head -ne $ExpectedMainCommit.ToLowerInvariant()) {
		throw "Release source $head is not the latest main commit $ExpectedMainCommit"
	}
	$productConfig = Get-Content -LiteralPath 'src/main/resources/mcreator.conf' -Raw
	if ($productConfig -notmatch '(?m)^product\.version=(?<version>\d+\.\d+\.\d+)\s*$') {
		throw 'Could not read product.version from mcreator.conf'
	}
	$productVersion = [string]$Matches.version
	if ($Tag -notlike "v$productVersion*") {
		throw "Tag $Tag does not match product version $productVersion"
	}
	if ($Tag -match '-beta\.\d+$') {
		$productStatusPath = Join-Path $repositoryRoot 'product-status.json'
		if (-not (Test-Path -LiteralPath $productStatusPath -PathType Leaf)) {
			throw 'Beta release source is missing product-status.json'
		}
		try {
			$productStatus = Get-Content -LiteralPath $productStatusPath -Raw | ConvertFrom-Json -Depth 32
		} catch {
			throw "Beta release product-status.json is invalid: $($_.Exception.Message)"
		}
		$declaredBetaTag = [string]$productStatus.delivery.betaRelease.tag
		if ($declaredBetaTag -ne $Tag) {
			throw "Beta release tag $Tag does not match product-status beta target $declaredBetaTag"
		}
		if ($productStatus.product.betaEligible -ne $true) {
			throw "Beta release $Tag is blocked because product-status betaEligible is not true"
		}
		$betaGates = @($productStatus.gates | Where-Object { $_.betaBlocking -eq $true })
		if ($betaGates.Count -eq 0) {
			throw "Beta release $Tag is blocked because product-status declares no betaBlocking gates"
		}
		$openBetaGates = @($betaGates | Where-Object { $_.status -ne 'passed' })
		if ($openBetaGates.Count -gt 0) {
			$gateSummary = ($openBetaGates | ForEach-Object { "$($_.id):$($_.status)" }) -join ', '
			throw "Beta release $Tag is blocked by betaBlocking gates: $gateSummary"
		}

		$candidate = $productStatus.delivery.betaRelease.candidateRelease
		if ($null -eq $candidate) {
			throw "Beta release $Tag is missing delivery.betaRelease.candidateRelease"
		}
		$candidateTag = [string]$candidate.tag
		$candidateCommit = [string]$candidate.sourceCommit
		if ($candidateTag -notmatch '^v\d+\.\d+\.\d+-preview\.\d+$' -or $candidateTag -notlike "v$productVersion-preview.*") {
			throw "Beta candidate release tag is invalid for product ${productVersion}: $candidateTag"
		}
		if ($candidateTag -eq $Tag) {
			throw 'Beta candidate release tag must differ from the final Beta tag'
		}
		if ($candidateCommit -notmatch '^[0-9a-f]{40}$') {
			throw 'Beta candidate sourceCommit must be a full lowercase Git SHA'
		}
		$candidateTagCommit = (git rev-parse "$candidateTag^{commit}" 2>$null).Trim()
		if ($LASTEXITCODE -ne 0 -or $candidateTagCommit -ne $candidateCommit) {
			throw "Beta candidate tag $candidateTag does not resolve to declared sourceCommit $candidateCommit"
		}
		$candidateTagType = (git cat-file -t "refs/tags/$candidateTag" 2>$null).Trim()
		if ($LASTEXITCODE -ne 0 -or $candidateTagType -ne 'tag') {
			throw "Beta candidate tag $candidateTag must be an annotated tag"
		}
		$candidateSignature = @(git -c "gpg.ssh.allowedSignersFile=$signersPath" verify-tag $candidateTag 2>&1)
		if ($LASTEXITCODE -ne 0) {
			throw "Beta candidate tag $candidateTag has no valid signature from an allowed signer:`n$($candidateSignature -join "`n")"
		}
		& git merge-base --is-ancestor $candidateCommit $head 2>$null
		if ($LASTEXITCODE -ne 0) {
			throw "Beta candidate source $candidateCommit is not an ancestor of release source $head"
		}
		$promotionDelta = @(git diff --name-only "$candidateCommit..$head")
		if ($LASTEXITCODE -ne 0) {
			throw 'Unable to inspect Beta candidate-to-release source delta'
		}
		$disallowedPromotionFiles = @($promotionDelta | Where-Object {
			$path = ([string]$_).Replace('\', '/')
			-not ($path -eq 'product-status.json' -or $path -eq 'PRD-NEXT.md' -or
				$path -eq 'docs/remaining-work.md' -or
				$path.StartsWith('docs/testing/', [StringComparison]::Ordinal) -or
				$path.StartsWith('docs/releases/', [StringComparison]::Ordinal) -or
				$path.StartsWith('evidence/', [StringComparison]::Ordinal))
		})
		if ($disallowedPromotionFiles.Count -gt 0) {
			throw "Beta release source contains build-affecting changes after the tested candidate: $($disallowedPromotionFiles -join ', ')"
		}

		$expectedAssets = [ordered]@{ exe = '.exe'; zip = '.zip'; msix = '.msix'; sbom = '.json' }
		$assetNames = @()
		foreach ($role in $expectedAssets.Keys) {
			$descriptor = $candidate.assets.$role
			$name = [string]$descriptor.name
			$sha256 = [string]$descriptor.sha256
			$invalidFileNameChars = [IO.Path]::GetInvalidFileNameChars()
			if ([string]::IsNullOrWhiteSpace($name) -or [IO.Path]::IsPathRooted($name) -or
				[IO.Path]::GetFileName($name) -ne $name -or $name -in '.', '..' -or
				$name.IndexOfAny($invalidFileNameChars) -ge 0 -or $name.Contains('/') -or $name.Contains('\') -or
				$name.EndsWith(' ') -or $name.EndsWith('.') -or
				$name -match '^(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)') {
				throw "Beta candidate $role asset name must be a plain Windows-safe file name: $name"
			}
			if ([string]::IsNullOrWhiteSpace($name) -or [IO.Path]::GetExtension($name) -ne $expectedAssets[$role]) {
				throw "Beta candidate $role asset name is invalid: $name"
			}
			if ($sha256 -notmatch '^[0-9a-f]{64}$') {
				throw "Beta candidate $role asset SHA-256 is invalid"
			}
			$assetNames += $name
		}
		if ([string]$candidate.assets.sbom.name -ne 'copperbench.spdx.json') {
			throw 'Beta candidate SBOM asset must be named copperbench.spdx.json'
		}
		if (($assetNames | Select-Object -Unique).Count -ne $assetNames.Count) {
			throw 'Beta candidate asset names must be unique'
		}

		$candidateExeSha256 = [string]$candidate.assets.exe.sha256
		$externalVerifier = Join-Path $PSScriptRoot 'verify-external-tester-evidence.mjs'
		$externalEvidenceDir = Join-Path $repositoryRoot 'evidence\stage-9\external-testers'
		$nodeCommand = Get-Command node -ErrorAction SilentlyContinue
		if (-not $nodeCommand -or -not (Test-Path -LiteralPath $externalVerifier -PathType Leaf)) {
			throw 'Beta release requires Node.js and the external tester evidence verifier'
		}
		$externalEvidenceResult = @(& $nodeCommand.Source $externalVerifier `
			'--evidence-dir' $externalEvidenceDir `
			'--require-complete' `
			'--expected-commit' $candidateCommit `
			'--expected-installer-sha256' $candidateExeSha256 2>&1)
		if ($LASTEXITCODE -ne 0) {
			throw "Beta release external tester evidence is not complete for the declared candidate:`n$($externalEvidenceResult -join "`n")"
		}

		$cleanWindowsGate = @($betaGates | Where-Object { $_.id -eq 'clean-windows-11-stage9' })
		if ($cleanWindowsGate.Count -ne 1) {
			throw 'Beta release requires exactly one clean-windows-11-stage9 gate'
		}
		$matchingFinalRcEvidence = @()
		foreach ($evidence in @($cleanWindowsGate[0].evidence)) {
			$evidencePathText = [string]$evidence
			if ([string]::IsNullOrWhiteSpace($evidencePathText) -or $evidencePathText -match '^https://') { continue }
			$evidencePath = if ([IO.Path]::IsPathRooted($evidencePathText)) {
				$evidencePathText
			} else {
				Join-Path $repositoryRoot $evidencePathText
			}
			if (-not (Test-Path -LiteralPath $evidencePath -PathType Leaf) -or [IO.Path]::GetExtension($evidencePath) -ne '.json') { continue }
			try {
				$machineEvidence = Get-Content -LiteralPath $evidencePath -Raw | ConvertFrom-Json -Depth 32
			} catch {
				throw "Beta release cannot parse clean-windows machine evidence $evidencePathText`: $($_.Exception.Message)"
			}
			if ([string]$machineEvidence.kind -ne 'stage9-clean-windows11-upgrade-offline-retention') { continue }
			if ($machineEvidence.finalRcReplayRequested -eq $true -and
				$machineEvidence.finalRcSourceWorktreeClean -eq $true -and
				$machineEvidence.passed -eq $true -and
				$machineEvidence.testMarkersRemoved -eq $true -and
				$machineEvidence.finalRcReplayRequired -eq $false -and
				$machineEvidence.gatePromotionReady -eq $true -and
				[string]$machineEvidence.finalRcSourceCommit -eq $candidateCommit -and
				[string]$machineEvidence.currentInstallerSha256 -eq $candidateExeSha256) {
				$matchingFinalRcEvidence += $evidencePathText
			}
		}
		if ($matchingFinalRcEvidence.Count -eq 0) {
			throw 'Beta release has no gatePromotionReady G9.5 final-RC evidence bound to the declared candidate commit and EXE SHA-256'
		}
	}
	Write-Output "releaseSource=$head"
} finally {
	Pop-Location
}
