[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script = (Resolve-Path (Join-Path $PSScriptRoot '..\Test-ReleaseAssets.ps1')).Path
$testRoot = Join-Path $repositoryRoot ".tmp\release-assets-test-$([guid]::NewGuid())"
$releaseDirectory = Join-Path $testRoot 'release'
$releaseJsonPath = Join-Path $testRoot 'release.json'

function Write-TestFile {
	param([string] $Name, [string] $Content)
	[IO.File]::WriteAllText((Join-Path $releaseDirectory $Name), $Content, [Text.UTF8Encoding]::new($false))
}

[IO.Directory]::CreateDirectory($releaseDirectory) | Out-Null
try {
	$names = @(
		'Copperbench.0.1.0.Windows.64bit.exe',
		'Copperbench.0.1.0.Windows.64bit.zip',
		'Copperbench.0.1.0.Windows.64bit.msix',
		'SHA256SUMS.txt',
		'RELEASE-METADATA.json',
		'copperbench.spdx.json',
		'product-status.json',
		'LICENSE.txt',
		'LICENSE-ADDITIONAL-TERMS.md',
		'THIRD_PARTY_NOTICES.md'
	)
	foreach ($name in $names) {
		Write-TestFile -Name $name -Content "test payload for $name"
	}

	$assets = @($names | ForEach-Object {
		$path = Join-Path $releaseDirectory $_
		[ordered]@{
			name = $_
			state = 'uploaded'
			digest = 'sha256:' + (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
		}
	})
	$release = [ordered]@{
		tag_name = 'v0.1.0-beta.1'
		draft = $true
		assets = $assets
	}
	$release | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $releaseJsonPath -Encoding utf8

	& pwsh -NoProfile -File $script -Tag 'v0.1.0-beta.1' -ReleaseDirectory $releaseDirectory -ReleaseJsonPath $releaseJsonPath
	if ($LASTEXITCODE -ne 0) { throw 'Exact remote draft asset digest verification should pass' }

	$tampered = Get-Content -LiteralPath $releaseJsonPath -Raw | ConvertFrom-Json -Depth 8
	$tampered.assets[0].digest = 'sha256:' + ('0' * 64)
	$tampered | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $releaseJsonPath -Encoding utf8
	$digestFailure = @(& pwsh -NoProfile -File $script -Tag 'v0.1.0-beta.1' -ReleaseDirectory $releaseDirectory -ReleaseJsonPath $releaseJsonPath 2>&1)
	if ($LASTEXITCODE -eq 0 -or ($digestFailure -join "`n") -notmatch 'digest mismatch') {
		throw "Remote digest mismatch was not rejected:`n$($digestFailure -join "`n")"
	}

	$tampered.assets[0].digest = 'sha256:' + (Get-FileHash -LiteralPath (Join-Path $releaseDirectory $names[0]) -Algorithm SHA256).Hash.ToLowerInvariant()
	$tampered.assets += [pscustomobject]@{
		name = 'stale-extra.txt'
		state = 'uploaded'
		digest = 'sha256:' + ('1' * 64)
	}
	$tampered | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $releaseJsonPath -Encoding utf8
	$extraFailure = @(& pwsh -NoProfile -File $script -Tag 'v0.1.0-beta.1' -ReleaseDirectory $releaseDirectory -ReleaseJsonPath $releaseJsonPath 2>&1)
	if ($LASTEXITCODE -eq 0 -or ($extraFailure -join "`n") -notmatch 'assets not present in the local verified payload') {
		throw "Stale remote draft asset was not rejected:`n$($extraFailure -join "`n")"
	}

	Write-Output 'Draft release asset digest tests passed.'
} finally {
	if (Test-Path -LiteralPath $testRoot) {
		Remove-Item -LiteralPath $testRoot -Recurse -Force
	}
}
