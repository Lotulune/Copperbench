[CmdletBinding()]
param([switch]$FromStdin)

$ErrorActionPreference = 'Stop'
if (-not $IsWindows) {
    throw 'This credential helper requires Windows DPAPI. On Linux supply TYPESAFE_API_KEY in the MCP process environment.'
}
$credentialDir = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'minecraft-control-bridge'
$credentialPath = Join-Path $credentialDir 'typesafe-key.dpapi'

if ($FromStdin) {
    # For automation: pipe the value in memory, never pass it as a command argument.
    $keyText = [Console]::In.ReadLine()
    if ([string]::IsNullOrWhiteSpace($keyText)) { throw 'No API key supplied.' }
    $secureKey = ConvertTo-SecureString -String $keyText -AsPlainText -Force
    $keyText = $null
} else {
    $secureKey = Read-Host 'TypeSafe API key (hidden)' -AsSecureString
}
try {
    if ($secureKey.Length -eq 0) { throw 'No API key supplied.' }
    $null = New-Item -ItemType Directory -Path $credentialDir -Force
    ConvertFrom-SecureString -SecureString $secureKey | Set-Content -LiteralPath $credentialPath -Encoding utf8
} finally {
    $secureKey.Dispose()
}
Write-Output 'Jev key saved with Windows current-user DPAPI encryption. Reconnect the MCP server to load it.'
