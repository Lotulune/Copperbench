[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$PackagePath,
    [string]$InstallerPath
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

$iconDifference = $null
if ($InstallerPath) {
    Add-Type -AssemblyName System.Drawing
    if (-not ('CopperbenchBrandIconReader' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class CopperbenchBrandIconReader {
    [DllImport("shell32.dll", CharSet=CharSet.Unicode, ExactSpelling=true)]
    public static extern uint ExtractIconExW(string file, int index, [Out] IntPtr[] large, [Out] IntPtr[] small, uint count);
    [DllImport("user32.dll", ExactSpelling=true)]
    public static extern bool DestroyIcon(IntPtr icon);
}
'@
    }
    $resolvedInstaller = (Resolve-Path -LiteralPath $InstallerPath).Path
    $handles = [IntPtr[]]::new(1)
    if ([CopperbenchBrandIconReader]::ExtractIconExW($resolvedInstaller, 0, $handles, $null, 1) -ne 1) {
        $failures.Add('Could not read the installer executable icon')
    } else {
        try {
            $actualIcon = [System.Drawing.Icon]::FromHandle($handles[0])
            $expectedIcon = [System.Drawing.Icon]::new((Join-Path $RepositoryRoot 'src/main/resources/net/mcreator/ui/res/icon.ico'), $actualIcon.Size)
            $actual = $actualIcon.ToBitmap()
            $expected = $expectedIcon.ToBitmap()
            try {
                $iconDifference = 0
                for ($y = 0; $y -lt $actual.Height; $y++) {
                    for ($x = 0; $x -lt $actual.Width; $x++) {
                        $left = $actual.GetPixel($x, $y)
                        $right = $expected.GetPixel($x, $y)
                        $iconDifference = [Math]::Max($iconDifference, [Math]::Abs([int]$left.A - [int]$right.A))
                        foreach ($channel in @('R','G','B')) {
                            $difference = [Math]::Abs([Math]::Round($left.$channel * $left.A / 255) - [Math]::Round($right.$channel * $right.A / 255))
                            $iconDifference = [Math]::Max($iconDifference, $difference)
                        }
                    }
                }
                if ($iconDifference -gt 2) { $failures.Add('Installer executable icon does not match the product icon (including MUI overrides)') }
            } finally { $actual.Dispose(); $expected.Dispose(); $actualIcon.Dispose(); $expectedIcon.Dispose() }
        } finally { [void][CopperbenchBrandIconReader]::DestroyIcon($handles[0]) }
    }
}

$result = [ordered]@{
    product = $lock.product
    status = if ($failures.Count -eq 0) { 'passed' } else { 'failed' }
    assetsChecked = $lock.assets.Count
    packageChecked = [bool]$PackagePath
    installerIconChecked = [bool]$InstallerPath
    installerIconMaxDifference = $iconDifference
    failures = @($failures)
}
$result | ConvertTo-Json -Depth 5
if ($failures.Count -gt 0) { exit 1 }
