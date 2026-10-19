[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'This check requires Windows Shell link support.' }
$fixtureRoot = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $fixtureRoot) { throw 'Choose a new fixture directory.' }
New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
function Assert-InFixture([string]$Candidate) {
    $fullPath = [IO.Path]::GetFullPath($Candidate)
    if (-not $fullPath.StartsWith($fixtureRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Fixture path escaped the selected directory.'
    }
}
$installed = Join-Path $fixtureRoot 'application'
$backup = Join-Path $fixtureRoot 'previous-application'
$shortcutPath = Join-Path $fixtureRoot 'Copperbench.lnk'
$targetFile = Join-Path $installed 'application.txt'
New-Item -ItemType Directory -Path $installed | Out-Null
Set-Content -LiteralPath $targetFile -Value 'old payload' -Encoding utf8
$wsh = New-Object -ComObject WScript.Shell
$shortcut = $wsh.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $targetFile
$shortcut.WorkingDirectory = $installed
$shortcut.Arguments = '--user-choice'
$shortcut.Description = 'User-customized shortcut'
$shortcut.Save()
$beforeHash = (Get-FileHash -LiteralPath $shortcutPath -Algorithm SHA256).Hash
$beforeTime = (Get-Item -LiteralPath $shortcutPath).LastWriteTimeUtc

# Model the installer's directory swap and old-payload cleanup; never run a target.
Assert-InFixture $installed
Assert-InFixture $backup
[IO.Directory]::Move($installed, $backup)
New-Item -ItemType Directory -Path $installed | Out-Null
Set-Content -LiteralPath $targetFile -Value 'new payload' -Encoding utf8
Assert-InFixture ((Resolve-Path -LiteralPath $backup).Path)
Remove-Item -LiteralPath $backup -Recurse -Force

$shell = New-Object -ComObject Shell.Application
$link = $shell.NameSpace($fixtureRoot).ParseName('Copperbench.lnk').GetLink
$link.Resolve(1) # SLR_NO_UI; no repair dialog or target execution.
$resolved = [IO.Path]::GetFullPath($link.Path)
if (-not $resolved.Equals($targetFile, [StringComparison]::OrdinalIgnoreCase)) { throw 'The shortcut no longer resolves to the current installation.' }
if ((Get-Content -LiteralPath $resolved -Raw).Trim() -ne 'new payload') { throw 'The shortcut resolved to the old payload.' }
$afterHash = (Get-FileHash -LiteralPath $shortcutPath -Algorithm SHA256).Hash
if ($afterHash -ne $beforeHash -or (Get-Item -LiteralPath $shortcutPath).LastWriteTimeUtc -ne $beforeTime) { throw 'Resolving the unchanged shortcut rewrote it.' }
$preserved = $wsh.CreateShortcut($shortcutPath)
if ($preserved.Arguments -ne '--user-choice' -or $preserved.Description -ne 'User-customized shortcut') { throw 'Shortcut customization was lost.' }
$result = [ordered]@{status='passed';shortcutSha256=$afterHash.ToLowerInvariant();resolvedTarget=$resolved;customizationPreserved=$true;shortcutRewritten=$false;scope='Isolated Shell link resolution after a directory swap; no registrar, target program, system registration or antivirus UI was executed.'} | ConvertTo-Json
$result | Set-Content -LiteralPath (Join-Path $fixtureRoot 'result.json') -Encoding utf8
Write-Output $result
