[CmdletBinding()]
param(
	[Parameter(Mandatory)]
	[string] $ReleaseDirectory
)

$ErrorActionPreference = 'Stop'
$releaseRoot = (Resolve-Path $ReleaseDirectory).Path
$files = @(Get-ChildItem -LiteralPath $releaseRoot -File)
$requiredExtensions = '.exe', '.zip', '.msix'
foreach ($extension in $requiredExtensions) {
	$binary = $files | Where-Object Extension -EQ $extension | Select-Object -First 1
	if (-not $binary) {
		throw "Release payload is missing a $extension binary"
	}
	if ($binary.Length -eq 0) { throw "Release binary $($binary.Name) is empty" }
}
foreach ($requiredName in 'SHA256SUMS.txt', 'RELEASE-METADATA.json', 'copperbench.spdx.json', 'product-status.json', 'LICENSE.txt', 'LICENSE-ADDITIONAL-TERMS.md', 'THIRD_PARTY_NOTICES.md') {
	if (-not ($files | Where-Object Name -EQ $requiredName)) {
		throw "Release payload is missing $requiredName"
	}
}
$hashEntries = @(Get-Content -LiteralPath (Join-Path $releaseRoot 'SHA256SUMS.txt'))
foreach ($file in $files | Where-Object Name -NE 'SHA256SUMS.txt') {
	if (-not ($hashEntries | Where-Object { $_ -match "  $([regex]::Escape($file.Name))$" })) {
		throw "SHA256SUMS.txt does not cover $($file.Name)"
	}
}
Write-Output "releaseFiles=$($files.Count)"
