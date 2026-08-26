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
	if ($Tag -notmatch '^v\d+\.\d+\.\d+(?:-preview\.\d+)?$') {
		throw "Release tag '$Tag' must match vX.Y.Z or vX.Y.Z-preview.N"
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
	if ($Tag -notlike "v$($Matches.version)*") {
		throw "Tag $Tag does not match product version $($Matches.version)"
	}
	Write-Output "releaseSource=$head"
} finally {
	Pop-Location
}
