[CmdletBinding()]
param(
	[Parameter(Mandatory)]
	[string] $Tag,
	[Parameter(Mandatory)]
	[string] $ReleaseDirectory,
	[string] $RepositoryRoot = (Join-Path $PSScriptRoot '..'),
	[string] $CandidateAssetDirectory = ''
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$releaseCandidate = if ([IO.Path]::IsPathRooted($ReleaseDirectory)) {
	$ReleaseDirectory
} else {
	Join-Path $repositoryRoot $ReleaseDirectory
}
$releaseRoot = [IO.Path]::GetFullPath($releaseCandidate)
if (-not $releaseRoot.StartsWith($repositoryRoot, [StringComparison]::OrdinalIgnoreCase)) {
	throw 'Release directory must be inside the repository'
}
[IO.Directory]::CreateDirectory($releaseRoot) | Out-Null
$commit = (git -C $repositoryRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Unable to resolve release commit' }

function Assert-CandidateAssetDescriptor {
	param(
		[Parameter(Mandatory)] $Descriptor,
		[Parameter(Mandatory)] [string] $Role,
		[Parameter(Mandatory)] [string] $ExpectedExtension
	)
	$name = [string]$Descriptor.name
	$sha256 = [string]$Descriptor.sha256
	if ([string]::IsNullOrWhiteSpace($name)) {
		throw "Beta candidate $Role asset name is missing"
	}
	$invalidFileNameChars = [IO.Path]::GetInvalidFileNameChars()
	if ([IO.Path]::IsPathRooted($name) -or [IO.Path]::GetFileName($name) -ne $name -or
		$name -in '.', '..' -or $name.IndexOfAny($invalidFileNameChars) -ge 0 -or
		$name.Contains('/') -or $name.Contains('\')) {
		throw "Beta candidate $Role asset name must be a plain Windows-safe file name: $name"
	}
	if ([IO.Path]::GetExtension($name) -ne $ExpectedExtension) {
		throw "Beta candidate $Role asset must use ${ExpectedExtension}: $name"
	}
	if ($sha256 -notmatch '^[0-9a-f]{64}$') {
		throw "Beta candidate $Role asset SHA-256 is invalid"
	}
}

function Resolve-RepositorySlug {
	if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_REPOSITORY)) {
		return $env:GITHUB_REPOSITORY.Trim()
	}
	$remote = (& git -C $repositoryRoot config --get remote.origin.url 2>$null)
	if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($remote)) {
		throw 'Unable to resolve GitHub repository for Beta candidate asset download'
	}
	$remote = ([string]$remote).Trim()
	if ($remote -match 'github\.com[:/](?<slug>[^/]+/[^/.]+)(?:\.git)?$') {
		return $Matches.slug
	}
	throw "Unsupported release remote for Beta candidate asset download: $remote"
}

$binarySource = [ordered]@{
	mode = 'built-from-release-commit'
	commit = $commit
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
	$candidate = $productStatus.delivery.betaRelease.candidateRelease
	if ($null -eq $candidate) {
		throw 'Beta release requires delivery.betaRelease.candidateRelease'
	}
	$candidateTag = [string]$candidate.tag
	$candidateCommit = [string]$candidate.sourceCommit
	if ($candidateTag -notmatch '^v\d+\.\d+\.\d+-preview\.\d+$') {
		throw "Beta candidate release tag is invalid: $candidateTag"
	}
	if ($candidateCommit -notmatch '^[0-9a-f]{40}$') {
		throw 'Beta candidate sourceCommit must be a full lowercase Git SHA'
	}

	$assetSpecs = [ordered]@{
		exe = @{ Descriptor = $candidate.assets.exe; Extension = '.exe' }
		zip = @{ Descriptor = $candidate.assets.zip; Extension = '.zip' }
		msix = @{ Descriptor = $candidate.assets.msix; Extension = '.msix' }
		sbom = @{ Descriptor = $candidate.assets.sbom; Extension = '.json' }
	}
	foreach ($role in $assetSpecs.Keys) {
		Assert-CandidateAssetDescriptor -Descriptor $assetSpecs[$role].Descriptor -Role $role -ExpectedExtension $assetSpecs[$role].Extension
	}
	if ([string]$candidate.assets.sbom.name -ne 'copperbench.spdx.json') {
		throw 'Beta candidate SBOM asset must be named copperbench.spdx.json'
	}

	Get-ChildItem -LiteralPath $releaseRoot -File | Where-Object {
		$_.Extension -in '.exe', '.zip', '.msix' -or $_.Name -eq 'copperbench.spdx.json'
	} | Remove-Item -Force

	$localCandidateRoot = $null
	if (-not [string]::IsNullOrWhiteSpace($CandidateAssetDirectory)) {
		$localCandidateRoot = (Resolve-Path -LiteralPath $CandidateAssetDirectory).Path
	}
	$repositorySlug = if ($localCandidateRoot) { '' } else { Resolve-RepositorySlug }
	foreach ($role in $assetSpecs.Keys) {
		$descriptor = $assetSpecs[$role].Descriptor
		$name = [string]$descriptor.name
		$destination = [IO.Path]::GetFullPath((Join-Path $releaseRoot $name))
		$releaseRootPrefix = $releaseRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
		if (-not $destination.StartsWith($releaseRootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
			throw "Beta candidate $role asset destination escapes release directory: $name"
		}
		if ($localCandidateRoot) {
			$source = Join-Path $localCandidateRoot $name
			if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
				throw "Beta candidate $role asset is missing from local test directory: $source"
			}
			Copy-Item -LiteralPath $source -Destination $destination -Force
		} else {
			$escapedTag = [uri]::EscapeDataString($candidateTag)
			$escapedName = [uri]::EscapeDataString($name)
			$downloadUri = "https://github.com/$repositorySlug/releases/download/$escapedTag/$escapedName"
			Invoke-WebRequest -Uri $downloadUri -OutFile $destination -UseBasicParsing
		}
		$actualHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
		if ($actualHash -ne [string]$descriptor.sha256) {
			throw "Beta candidate $role asset SHA-256 mismatch. expected=$($descriptor.sha256) actual=$actualHash"
		}
	}

	$binarySource = [ordered]@{
		mode = 'promoted-tested-candidate'
		tag = $candidateTag
		commit = $candidateCommit
		assets = $candidate.assets
	}
}

$metadata = [ordered]@{
	product = 'Copperbench'
	tag = $Tag
	commit = $commit
	binarySource = $binarySource
	builtAtUtc = [DateTime]::UtcNow.ToString('o')
	platform = 'windows-x64'
	signing = 'unsigned'
	workflow = $env:GITHUB_WORKFLOW
	workflowRun = $env:GITHUB_RUN_ID
}
$metadata | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $releaseRoot 'RELEASE-METADATA.json') -Encoding UTF8

$files = Get-ChildItem -LiteralPath $releaseRoot -File |
	Where-Object Name -ne 'SHA256SUMS.txt' |
	Sort-Object Name
$hashLines = foreach ($file in $files) {
	$hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
	"$hash  $($file.Name)"
}
$hashLines | Set-Content -LiteralPath (Join-Path $releaseRoot 'SHA256SUMS.txt') -Encoding UTF8
