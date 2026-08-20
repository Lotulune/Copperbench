[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$PackagePath
)

$ErrorActionPreference = 'Stop'
$lockPath = Join-Path $RepositoryRoot 'compliance\branding-assets.lock.json'
$lock = Get-Content -Raw -LiteralPath $lockPath | ConvertFrom-Json
$failures = [System.Collections.Generic.List[string]]::new()

foreach ($asset in $lock.assets) {
    $path = Join-Path $RepositoryRoot ($asset.path -replace '/', '\')
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $failures.Add("Missing replacement asset: $($asset.path)")
        continue
    }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    if ($hash -eq $asset.upstreamSha256) {
        $failures.Add("Upstream protected asset is still present: $($asset.path)")
    }
    if ($hash -ne $asset.replacementSha256) {
        $failures.Add("Replacement asset drifted without updating the lock: $($asset.path)")
    }
}

$windowsTextFiles = @(
    'platform\windows\windows.gradle',
    'platform\windows\installer\install.nsi',
    'platform\windows\msix\AppxManifest.xml'
)
$blockedPatterns = @('Pylo', 'Pylo.MCreator', 'mcreator.exe', 'Name "MCreator', 'BrandingText "MCreator')
foreach ($relativePath in $windowsTextFiles) {
    $content = Get-Content -Raw -LiteralPath (Join-Path $RepositoryRoot $relativePath)
    foreach ($pattern in $blockedPatterns) {
        if ($content.Contains($pattern, [StringComparison]::OrdinalIgnoreCase)) {
            $failures.Add("Blocked distribution brand text '$pattern' in $relativePath")
        }
    }
}

$fabricPlugin = Join-Path $RepositoryRoot 'plugins\generator-fabric-26.1.2\plugin.json'
if (-not (Test-Path -LiteralPath $fabricPlugin -PathType Leaf)) {
    $failures.Add('Built-in Fabric generator plugin is missing')
}

if ($PackagePath) {
    $resolvedPackage = (Resolve-Path -LiteralPath $PackagePath).Path
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedPackage)
    try {
        $entries = @($archive.Entries | ForEach-Object FullName)
        foreach ($required in @('copperbench.exe', 'lib/copperbench.jar', 'plugins/generator-fabric-26.1.2.zip')) {
            if (-not ($entries | Where-Object { $_.Replace('\', '/').EndsWith('/' + $required) })) {
                $failures.Add("Package is missing $required")
            }
        }
        if ($entries | Where-Object { $_ -match '(?i)(^|/)mcreator\.exe$' }) {
            $failures.Add('Package contains the upstream executable name mcreator.exe')
        }
    } finally {
        $archive.Dispose()
    }
}

$result = [ordered]@{
    product = $lock.product
    status = if ($failures.Count -eq 0) { 'passed' } else { 'failed' }
    assetsChecked = $lock.assets.Count
    packageChecked = [bool]$PackagePath
    failures = @($failures)
}
$result | ConvertTo-Json -Depth 5
if ($failures.Count -gt 0) { exit 1 }
