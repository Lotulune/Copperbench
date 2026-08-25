[CmdletBinding()]
param(
	[Parameter(Mandatory)]
	[string] $Tag
)

$ErrorActionPreference = 'Stop'
$release = gh release view $Tag --json isDraft,assets | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw "Unable to inspect draft release $Tag" }
if (-not $release.isDraft) { throw "Release $Tag must remain a draft until asset verification succeeds" }
$names = @($release.assets | ForEach-Object name)
foreach ($pattern in '\.exe$', '\.zip$', '\.msix$', '^SHA256SUMS\.txt$', '^RELEASE-METADATA\.json$', '^copperbench\.spdx\.json$') {
	if (-not ($names | Where-Object { $_ -match $pattern })) {
		throw "Draft release $Tag has no asset matching $pattern"
	}
}
Write-Output "verifiedReleaseAssets=$($names.Count)"
