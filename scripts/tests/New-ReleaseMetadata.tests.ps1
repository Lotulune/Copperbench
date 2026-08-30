[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$metadataScript = (Resolve-Path (Join-Path $PSScriptRoot '..\New-ReleaseMetadata.ps1')).Path
$payloadScript = (Resolve-Path (Join-Path $PSScriptRoot '..\Test-ReleasePayload.ps1')).Path
$testRoot = Join-Path $repositoryRoot ".tmp\beta-binary-promotion-test-$([guid]::NewGuid())"
$fakeRepository = Join-Path $testRoot 'repository'
$candidateDirectory = Join-Path $testRoot 'candidate'
$releaseDirectory = Join-Path $fakeRepository 'build\release'

function Invoke-TestGit {
	param([string[]] $Arguments)
	& git -C $fakeRepository @Arguments | Out-Null
	if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') failed" }
}

function Get-LowerHash {
	param([Parameter(Mandatory)][string] $Path)
	return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Write-TestFile {
	param(
		[Parameter(Mandatory)][string] $Path,
		[Parameter(Mandatory)][string] $Content
	)
	[IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
	[IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
}

function Write-ReleasePrerequisites {
	param([Parameter(Mandatory)] $Status)
	[IO.Directory]::CreateDirectory($releaseDirectory) | Out-Null
	$Status | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $releaseDirectory 'product-status.json') -Encoding utf8
	Write-TestFile -Path (Join-Path $releaseDirectory 'LICENSE.txt') -Content 'test license'
	Write-TestFile -Path (Join-Path $releaseDirectory 'LICENSE-ADDITIONAL-TERMS.md') -Content 'test additional terms'
	Write-TestFile -Path (Join-Path $releaseDirectory 'THIRD_PARTY_NOTICES.md') -Content 'test notices'
	Write-TestFile -Path (Join-Path $releaseDirectory 'Fresh Build.exe') -Content 'fresh exe must be replaced'
	Write-TestFile -Path (Join-Path $releaseDirectory 'Fresh Build.zip') -Content 'fresh zip must be replaced'
	Write-TestFile -Path (Join-Path $releaseDirectory 'Fresh Build.msix') -Content 'fresh msix must be replaced'
	Write-TestFile -Path (Join-Path $releaseDirectory 'copperbench.spdx.json') -Content '{"fresh":true}'
}

[IO.Directory]::CreateDirectory($fakeRepository) | Out-Null
[IO.Directory]::CreateDirectory($candidateDirectory) | Out-Null
try {
	Invoke-TestGit -Arguments @('init')
	Invoke-TestGit -Arguments @('config', 'user.name', 'Promotion Test')
	Invoke-TestGit -Arguments @('config', 'user.email', 'promotion-test@example.invalid')
	Write-TestFile -Path (Join-Path $fakeRepository 'README.md') -Content 'candidate source'
	Invoke-TestGit -Arguments @('add', '.')
	Invoke-TestGit -Arguments @('commit', '-m', 'candidate source')
	$candidateCommit = (& git -C $fakeRepository rev-parse HEAD).Trim()

	$candidateNames = [ordered]@{
		exe = 'Copperbench.0.1.0.Windows.64bit.exe'
		zip = 'Copperbench.0.1.0.Windows.64bit.zip'
		msix = 'Copperbench.0.1.0.Windows.64bit.msix'
		sbom = 'copperbench.spdx.json'
	}
	Write-TestFile -Path (Join-Path $candidateDirectory $candidateNames.exe) -Content 'tested candidate exe bytes'
	Write-TestFile -Path (Join-Path $candidateDirectory $candidateNames.zip) -Content 'tested candidate zip bytes'
	Write-TestFile -Path (Join-Path $candidateDirectory $candidateNames.msix) -Content 'tested candidate msix bytes'
	Write-TestFile -Path (Join-Path $candidateDirectory $candidateNames.sbom) -Content '{"testedCandidate":true}'

	$assets = [ordered]@{}
	foreach ($role in $candidateNames.Keys) {
		$name = $candidateNames[$role]
		$assets[$role] = [ordered]@{
			name = $name
			sha256 = Get-LowerHash -Path (Join-Path $candidateDirectory $name)
		}
	}
	$status = [ordered]@{
		product = [ordered]@{ betaEligible = $true }
		delivery = [ordered]@{
			betaRelease = [ordered]@{
				tag = 'v0.1.0-beta.1'
				status = 'ready'
				candidateRelease = [ordered]@{
					tag = 'v0.1.0-preview.4'
					sourceCommit = $candidateCommit
					assets = $assets
				}
			}
		}
	}
	$status | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $fakeRepository 'product-status.json') -Encoding utf8
	Invoke-TestGit -Arguments @('add', 'product-status.json')
	Invoke-TestGit -Arguments @('commit', '-m', 'beta control status')

	Write-ReleasePrerequisites -Status $status
	& pwsh -NoProfile -File $metadataScript `
		-Tag 'v0.1.0-beta.1' `
		-ReleaseDirectory $releaseDirectory `
		-RepositoryRoot $fakeRepository `
		-CandidateAssetDirectory $candidateDirectory
	if ($LASTEXITCODE -ne 0) { throw 'Beta candidate metadata generation failed' }

	foreach ($role in $candidateNames.Keys) {
		$name = $candidateNames[$role]
		$actual = Get-LowerHash -Path (Join-Path $releaseDirectory $name)
		if ($actual -ne $assets[$role].sha256) {
			throw "Promoted $role bytes differ from tested candidate"
		}
	}
	if (Get-ChildItem -LiteralPath $releaseDirectory -File | Where-Object Name -Like 'Fresh Build.*') {
		throw 'Freshly rebuilt Windows binaries were not removed before candidate promotion'
	}
	$metadata = Get-Content -LiteralPath (Join-Path $releaseDirectory 'RELEASE-METADATA.json') -Raw | ConvertFrom-Json -Depth 32
	if ([string]$metadata.binarySource.mode -ne 'promoted-tested-candidate' -or
		[string]$metadata.binarySource.tag -ne 'v0.1.0-preview.4' -or
		[string]$metadata.binarySource.commit -ne $candidateCommit) {
		throw 'Release metadata does not preserve the tested candidate provenance chain'
	}
	& pwsh -NoProfile -File $payloadScript -ReleaseDirectory $releaseDirectory
	if ($LASTEXITCODE -ne 0) { throw 'Promoted Beta payload verification failed' }
	Write-TestFile -Path (Join-Path $releaseDirectory 'THIRD_PARTY_NOTICES.md') -Content 'tampered after SHA256SUMS generation'
	$checksumFailure = @(& pwsh -NoProfile -File $payloadScript -ReleaseDirectory $releaseDirectory 2>&1)
	if ($LASTEXITCODE -eq 0 -or ($checksumFailure -join "`n") -notmatch 'SHA256SUMS.txt hash mismatch') {
		throw "Tampered payload file was not rejected by SHA256SUMS verification:`n$($checksumFailure -join "`n")"
	}

	Remove-Item -LiteralPath $releaseDirectory -Recurse -Force
	$badStatus = $status | ConvertTo-Json -Depth 12 | ConvertFrom-Json -Depth 12
	$badStatus.delivery.betaRelease.candidateRelease.assets.exe.sha256 = ('0' * 64)
	$badStatus | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $fakeRepository 'product-status.json') -Encoding utf8
	Write-ReleasePrerequisites -Status $badStatus
	$failure = @(& pwsh -NoProfile -File $metadataScript `
		-Tag 'v0.1.0-beta.1' `
		-ReleaseDirectory $releaseDirectory `
		-RepositoryRoot $fakeRepository `
		-CandidateAssetDirectory $candidateDirectory 2>&1)
	if ($LASTEXITCODE -eq 0 -or ($failure -join "`n") -notmatch 'asset SHA-256 mismatch') {
		throw "Wrong candidate hash was not rejected:`n$($failure -join "`n")"
	}

	Write-Output 'Beta exact-binary promotion tests passed.'
} finally {
	if (Test-Path -LiteralPath $testRoot) {
		Remove-Item -LiteralPath $testRoot -Recurse -Force
	}
}
