[CmdletBinding()]
param(
	[Parameter(Mandatory)]
	[string] $Tag,
	[string] $ReleaseDirectory = (Join-Path $PSScriptRoot '..\build\release'),
	[string] $ReleaseJsonPath = ''
)

$ErrorActionPreference = 'Stop'

function Resolve-RepositorySlug {
	if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_REPOSITORY)) {
		return $env:GITHUB_REPOSITORY.Trim()
	}
	$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
	$remote = (& git -C $repositoryRoot config --get remote.origin.url 2>$null)
	if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($remote)) {
		throw 'Unable to resolve GitHub repository for draft asset verification'
	}
	$remote = ([string]$remote).Trim()
	if ($remote -match 'github\.com[:/](?<slug>[^/]+/[^/.]+)(?:\.git)?$') {
		return $Matches.slug
	}
	throw "Unsupported release remote for draft asset verification: $remote"
}

$releaseRoot = (Resolve-Path -LiteralPath $ReleaseDirectory).Path
if (-not (Test-Path -LiteralPath $releaseRoot -PathType Container)) {
	throw "Release directory does not exist: $ReleaseDirectory"
}

if (-not [string]::IsNullOrWhiteSpace($ReleaseJsonPath)) {
	$release = Get-Content -LiteralPath $ReleaseJsonPath -Raw | ConvertFrom-Json -Depth 32
} else {
	$repositorySlug = Resolve-RepositorySlug
	$escapedTag = [uri]::EscapeDataString($Tag)
	$releaseJson = @(& gh api "repos/$repositorySlug/releases/tags/$escapedTag" 2>&1)
	if ($LASTEXITCODE -ne 0) {
		throw "Unable to inspect draft release $Tag`: $($releaseJson -join "`n")"
	}
	try {
		$release = ($releaseJson -join "`n") | ConvertFrom-Json -Depth 32
	} catch {
		throw "Draft release $Tag returned invalid JSON: $($_.Exception.Message)"
	}
}

if ($release.tag_name -and [string]$release.tag_name -ne $Tag) {
	throw "Draft release JSON tag does not match requested tag $Tag"
}
if ($release.draft -ne $true -and $release.isDraft -ne $true) {
	throw "Release $Tag must remain a draft until asset verification succeeds"
}

$assets = @($release.assets)
$remoteByName = @{}
foreach ($asset in $assets) {
	$name = [string]$asset.name
	if ([string]::IsNullOrWhiteSpace($name)) {
		throw "Draft release $Tag contains an asset with no name"
	}
	if ($remoteByName.ContainsKey($name)) {
		throw "Draft release $Tag contains duplicate asset name: $name"
	}
	if ($asset.state -and [string]$asset.state -ne 'uploaded') {
		throw "Draft release asset $name is not fully uploaded: $($asset.state)"
	}
	$digest = [string]$asset.digest
	if ($digest -notmatch '^sha256:(?<sha>[0-9a-fA-F]{64})$') {
		throw "Draft release asset $name has no usable SHA-256 digest"
	}
	$remoteByName[$name] = ([string]$Matches.sha).ToLowerInvariant()
}

$localFiles = @(Get-ChildItem -LiteralPath $releaseRoot -File | Sort-Object Name)
$names = @($localFiles | ForEach-Object Name)
foreach ($pattern in '\.exe$', '\.zip$', '\.msix$', '^SHA256SUMS\.txt$', '^RELEASE-METADATA\.json$', '^copperbench\.spdx\.json$', '^product-status\.json$', '^LICENSE\.txt$', '^LICENSE-ADDITIONAL-TERMS\.md$', '^THIRD_PARTY_NOTICES\.md$') {
	if (-not ($names | Where-Object { $_ -match $pattern })) {
		throw "Local release payload for $Tag has no asset matching $pattern"
	}
}

foreach ($file in $localFiles) {
	if (-not $remoteByName.ContainsKey($file.Name)) {
		throw "Draft release $Tag is missing uploaded asset $($file.Name)"
	}
	$localHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
	if ($remoteByName[$file.Name] -ne $localHash) {
		throw "Draft release asset digest mismatch for $($file.Name). local=$localHash remote=$($remoteByName[$file.Name])"
	}
}

if ($remoteByName.Count -ne $localFiles.Count) {
	$extra = @($remoteByName.Keys | Where-Object { $_ -notin $names } | Sort-Object)
	throw "Draft release $Tag contains assets not present in the local verified payload: $($extra -join ', ')"
}

Write-Output "verifiedReleaseAssets=$($localFiles.Count)"
