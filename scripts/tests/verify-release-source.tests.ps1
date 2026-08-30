[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$verifier = (Resolve-Path (Join-Path $PSScriptRoot '..\verify-release-source.ps1')).Path
$testRoot = Join-Path ([IO.Path]::GetTempPath()) "copperbench-release-source-$([guid]::NewGuid())"
$repository = Join-Path $testRoot 'repository'
$keyPath = Join-Path $testRoot 'release-key'

function Invoke-TestGit {
	param([string[]] $Arguments)
	& git -C $repository @Arguments | Out-Null
	if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') failed" }
}

function Invoke-Verifier {
	param([string] $Tag, [string] $MainCommit)
	$output = @(& pwsh -NoProfile -File $verifier -Tag $Tag -ExpectedMainCommit $MainCommit -RepositoryRoot $repository 2>&1)
	return @{ ExitCode = $LASTEXITCODE; Output = $output -join "`n" }
}

[IO.Directory]::CreateDirectory($repository) | Out-Null
try {
	& ssh-keygen -q -t ed25519 -N '' -f $keyPath
	if ($LASTEXITCODE -ne 0) { throw 'Unable to generate temporary SSH signing key' }
	Invoke-TestGit -Arguments @('init')
	Invoke-TestGit -Arguments @('config', 'user.name', 'Release Test')
	Invoke-TestGit -Arguments @('config', 'user.email', 'release-test@example.invalid')
	[IO.Directory]::CreateDirectory((Join-Path $repository 'src/main/resources')) | Out-Null
	[IO.Directory]::CreateDirectory((Join-Path $repository '.github')) | Out-Null
	Set-Content -LiteralPath (Join-Path $repository 'src/main/resources/mcreator.conf') -Value 'product.version=0.1.0' -Encoding utf8
	$publicKey = (Get-Content -LiteralPath "$keyPath.pub" -Raw).Trim()
	Set-Content -LiteralPath (Join-Path $repository '.github/release-signers') -Value "release-test@example.invalid $publicKey" -Encoding utf8
	Invoke-TestGit -Arguments @('add', '.')
	Invoke-TestGit -Arguments @('commit', '-m', 'release source')
	$head = (& git -C $repository rev-parse HEAD).Trim()
	Invoke-TestGit -Arguments @('update-ref', 'refs/remotes/origin/main', $head)
	& git -C $repository -c gpg.format=ssh -c "user.signingkey=$keyPath" tag -s -a v0.1.0-preview.3 -m 'signed release'
	if ($LASTEXITCODE -ne 0) { throw 'Unable to create signed test tag' }
	& git -C $repository -c gpg.format=ssh -c "user.signingkey=$keyPath" tag -s -a v0.1.0-beta.1 -m 'signed beta release'
	if ($LASTEXITCODE -ne 0) { throw 'Unable to create signed beta test tag' }
	Invoke-TestGit -Arguments @('tag', '-a', 'v0.1.0-preview.4', '-m', 'unsigned release')

	$valid = Invoke-Verifier -Tag 'v0.1.0-preview.3' -MainCommit $head
	if ($valid.ExitCode -ne 0 -or $valid.Output -notmatch "releaseSource=$head") {
		throw "Valid signed release source was rejected:`n$($valid.Output)"
	}
	$validBeta = Invoke-Verifier -Tag 'v0.1.0-beta.1' -MainCommit $head
	if ($validBeta.ExitCode -ne 0 -or $validBeta.Output -notmatch "releaseSource=$head") {
		throw "Valid signed beta release source was rejected:`n$($validBeta.Output)"
	}
	$invalidChannel = Invoke-Verifier -Tag 'v0.1.0-rc.1' -MainCommit $head
	if ($invalidChannel.ExitCode -eq 0 -or $invalidChannel.Output -notmatch 'must match vX.Y.Z') {
		throw 'Unsupported release channel tag was not rejected'
	}
	$unsigned = Invoke-Verifier -Tag 'v0.1.0-preview.4' -MainCommit $head
	if ($unsigned.ExitCode -eq 0 -or $unsigned.Output -notmatch 'no valid signature') {
		throw 'Unsigned annotated release tag was not rejected'
	}
	$stale = Invoke-Verifier -Tag 'v0.1.0-preview.3' -MainCommit ('0' * 40)
	if ($stale.ExitCode -eq 0 -or $stale.Output -notmatch 'is not the latest main commit') {
		throw 'Release source that differs from main was not rejected'
	}
	Write-Output 'Release source verification tests passed.'
} finally {
	if (Test-Path -LiteralPath $testRoot) {
		Remove-Item -LiteralPath $testRoot -Recurse -Force
	}
}
