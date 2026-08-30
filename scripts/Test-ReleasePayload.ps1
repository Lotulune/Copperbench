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
$hashEntries = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $releaseRoot 'SHA256SUMS.txt')) {
	if ([string]::IsNullOrWhiteSpace($line)) { continue }
	if ($line -notmatch '^(?<sha>[0-9a-fA-F]{64})  (?<name>.+)$') {
		throw "SHA256SUMS.txt contains an invalid entry: $line"
	}
	$name = [string]$Matches.name
	if ($hashEntries.ContainsKey($name)) {
		throw "SHA256SUMS.txt contains duplicate entry: $name"
	}
	$hashEntries[$name] = ([string]$Matches.sha).ToLowerInvariant()
}
$hashedFiles = @($files | Where-Object Name -NE 'SHA256SUMS.txt')
foreach ($file in $hashedFiles) {
	if (-not $hashEntries.ContainsKey($file.Name)) {
		throw "SHA256SUMS.txt does not cover $($file.Name)"
	}
	$actualHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
	if ($actualHash -ne $hashEntries[$file.Name]) {
		throw "SHA256SUMS.txt hash mismatch for $($file.Name). expected=$($hashEntries[$file.Name]) actual=$actualHash"
	}
}
if ($hashEntries.Count -ne $hashedFiles.Count) {
	$knownNames = @($hashedFiles | ForEach-Object Name)
	$unknown = @($hashEntries.Keys | Where-Object { $_ -notin $knownNames })
	throw "SHA256SUMS.txt contains entries for missing payload files: $($unknown -join ', ')"
}

$metadata = Get-Content -LiteralPath (Join-Path $releaseRoot 'RELEASE-METADATA.json') -Raw | ConvertFrom-Json -Depth 32
$productStatus = Get-Content -LiteralPath (Join-Path $releaseRoot 'product-status.json') -Raw | ConvertFrom-Json -Depth 32
if ([string]$metadata.tag -match '-beta\.\d+$') {
	$candidate = $productStatus.delivery.betaRelease.candidateRelease
	if ($null -eq $candidate) {
		throw 'Beta release payload is missing candidateRelease status metadata'
	}
	if ([string]$metadata.binarySource.mode -ne 'promoted-tested-candidate' -or
		[string]$metadata.binarySource.tag -ne [string]$candidate.tag -or
		[string]$metadata.binarySource.commit -ne [string]$candidate.sourceCommit) {
		throw 'Beta release metadata does not identify the declared tested candidate binary source'
	}
	foreach ($role in 'exe', 'zip', 'msix', 'sbom') {
		$descriptor = $candidate.assets.$role
		$name = [string]$descriptor.name
		$file = $files | Where-Object Name -EQ $name | Select-Object -First 1
		if (-not $file) {
			throw "Beta release payload is missing tested candidate $role asset: $name"
		}
		$actualHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
		if ($actualHash -ne [string]$descriptor.sha256) {
			throw "Beta release $role asset differs from tested candidate. expected=$($descriptor.sha256) actual=$actualHash"
		}
	}
}
Write-Output "releaseFiles=$($files.Count)"
