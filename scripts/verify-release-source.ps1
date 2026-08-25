[CmdletBinding()]
param(
	[Parameter(Mandatory)]
	[string] $Tag
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
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
