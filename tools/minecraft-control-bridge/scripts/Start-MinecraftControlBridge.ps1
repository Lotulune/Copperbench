[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$bridgeRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = Split-Path -Parent (Split-Path -Parent $bridgeRoot)
$bridgePython = Join-Path $bridgeRoot '.venv/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $bridgePython -PathType Leaf)) {
    throw 'Bridge virtual environment is missing. Follow tools/minecraft-control-bridge/README.md installation steps.'
}

# Load only into this process and its child. No global environment changes.
$previousKey = $env:TYPESAFE_API_KEY
try {
    if ([string]::IsNullOrWhiteSpace($env:TYPESAFE_API_KEY)) {
        $credentialPath = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'minecraft-control-bridge/typesafe-key.dpapi'
        if (Test-Path -LiteralPath $credentialPath -PathType Leaf) {
            try {
                $encryptedKey = (Get-Content -LiteralPath $credentialPath -Raw).Trim()
                $secureKey = ConvertTo-SecureString -String $encryptedKey
                try {
                    $env:TYPESAFE_API_KEY = [System.Net.NetworkCredential]::new('', $secureKey).Password
                } finally {
                    $secureKey.Dispose()
                }
            } catch {
                # Keep desktop tools usable even when Jev credentials cannot be read.
                [Console]::Error.WriteLine('Jev credential unavailable. Run Set-JevKey.ps1 under the current Windows account.')
            }
        }
    }
    # Never print banners on stdout: it belongs exclusively to MCP JSON-RPC.
    & $bridgePython -m minecraft_control_bridge serve --evidence-dir (Join-Path $projectRoot 'output/minecraft-validation')
    $bridgeExitCode = $LASTEXITCODE
} finally {
    $env:TYPESAFE_API_KEY = $previousKey
}
exit $bridgeExitCode
