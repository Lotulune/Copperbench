$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$dir = Join-Path $repo 'evidence/stage14/2026-09-07/eight-track'
$snapshotPath = Join-Path $dir 'source-snapshot.json'
$snapshot = Get-Content $snapshotPath -Raw | ConvertFrom-Json
$info = [Diagnostics.ProcessStartInfo]::new(); $info.FileName='git'; $info.WorkingDirectory=$repo
$info.UseShellExecute=$false; $info.RedirectStandardOutput=$true; $info.StandardOutputEncoding=[Text.UTF8Encoding]::new()
foreach($arg in @('diff','--binary','--','src/main/java','plugins')) { $info.ArgumentList.Add($arg) }
$p = [Diagnostics.Process]::Start($info); $patch = $p.StandardOutput.ReadToEnd(); $p.WaitForExit()
if($p.ExitCode -ne 0) { throw 'Could not read final source diff' }
$bytes = [Text.UTF8Encoding]::new($false).GetBytes($patch)
$hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
$result = [ordered]@{baselineCommit=$snapshot.baselineCommit;initialDeltaSha256=$snapshot.sourceDeltaSha256;finalDeltaSha256=$hash;productSourceUnchanged=($hash -eq $snapshot.sourceDeltaSha256);checkedAt=[DateTimeOffset]::UtcNow.ToString('o')}
$result | ConvertTo-Json | Set-Content (Join-Path $dir 'source-unchanged.json') -Encoding utf8
$snapshot.scope = 'Existing Stage14A dirty implementation captured during audit; final source-diff comparison determines whether it stayed unchanged'
$snapshot | ConvertTo-Json | Set-Content $snapshotPath -Encoding utf8
$result | ConvertTo-Json -Compress
if (-not $result.productSourceUnchanged) { exit 1 }
