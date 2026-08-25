[CmdletBinding()]
param(
	[Parameter(Mandatory)]
	[string] $Tag,
	[Parameter(Mandatory)]
	[string] $ReleaseDirectory
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
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

$metadata = [ordered]@{
	product = 'Copperbench'
	tag = $Tag
	commit = $commit
	builtAtUtc = [DateTime]::UtcNow.ToString('o')
	platform = 'windows-x64'
	signing = 'unsigned'
	workflow = $env:GITHUB_WORKFLOW
	workflowRun = $env:GITHUB_RUN_ID
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $releaseRoot 'RELEASE-METADATA.json') -Encoding UTF8

$files = Get-ChildItem -LiteralPath $releaseRoot -File |
	Where-Object Name -ne 'SHA256SUMS.txt' |
	Sort-Object Name
$hashLines = foreach ($file in $files) {
	$hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
	"$hash  $($file.Name)"
}
$hashLines | Set-Content -LiteralPath (Join-Path $releaseRoot 'SHA256SUMS.txt') -Encoding UTF8
