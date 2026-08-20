[CmdletBinding()]
param(
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-8\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

$jsign = Join-Path $RepositoryRoot 'platform\windows\lib\jsign-7.4.jar'
$chain = Join-Path $RepositoryRoot 'codesign-chain.pem'
$exe = Join-Path $RepositoryRoot 'build\export\win64\copperbench.exe'
$installer = Join-Path $RepositoryRoot 'build\export\Copperbench 0.1.0 Windows 64bit.exe'

function Get-SignatureStatus([string]$path) {
	if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
		return 'missing'
	}
	return [string](Get-AuthenticodeSignature -FilePath $path).Status
}

$envPresent = [ordered]@{
	WIN_CERT_KEYSTORE = [bool](Get-Item 'Env:WIN_CERT_KEYSTORE' -ErrorAction SilentlyContinue)
	WIN_CERT_STOREPASS = [bool](Get-Item 'Env:WIN_CERT_STOREPASS' -ErrorAction SilentlyContinue)
	WIN_CERT_KEYNAME = [bool](Get-Item 'Env:WIN_CERT_KEYNAME' -ErrorAction SilentlyContinue)
}

$result = [ordered]@{
	schemaVersion = '1.0'
	kind = 'windows-authenticode-readiness'
	jsignPresent = (Test-Path -LiteralPath $jsign)
	certificateChainPresent = (Test-Path -LiteralPath $chain)
	signingEnvironmentConfigured = ($envPresent.WIN_CERT_KEYSTORE -and $envPresent.WIN_CERT_STOREPASS -and $envPresent.WIN_CERT_KEYNAME)
	environmentFlags = $envPresent
	exportExecutableSignature = Get-SignatureStatus $exe
	installerSignature = Get-SignatureStatus $installer
	signed = $false
	readyToSign = $false
	nextHostStep = 'Unsigned GitHub Releases are the intended first public distribution (ADR-0015). Authenticode is optional: set WIN_CERT_KEYSTORE, WIN_CERT_STOREPASS, WIN_CERT_KEYNAME, and codesign-chain.pem if you later want jsign 7.4 to sign.'
}
$result.readyToSign = [bool]($result.jsignPresent -and $result.signingEnvironmentConfigured -and $result.certificateChainPresent)
$result.signed = ($result.exportExecutableSignature -eq 'Valid' -and $result.installerSignature -eq 'Valid')
if ($result.signed) {
	$result.nextHostStep = 'Production binaries are Authenticode-valid.'
}
elseif ($result.readyToSign) {
	$result.nextHostStep = 'Environment looks complete. Rebuild exportWindowsZip/buildInstallerWin64 so signFile() can attach the certificate.'
}

$evidencePath = Join-Path $evidenceDir 'signing-ready.json'
($result | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $evidencePath -Encoding utf8
Write-Output ("jsignPresent=" + $result.jsignPresent)
Write-Output ("readyToSign=" + $result.readyToSign)
Write-Output ("signed=" + $result.signed)
Write-Output ("evidence=" + $evidencePath)
