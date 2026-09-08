$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$output = Join-Path $repo 'evidence/stage14/2026-09-07/eight-track'
New-Item -ItemType Directory -Force $output | Out-Null
$info = [Diagnostics.ProcessStartInfo]::new()
$info.FileName = 'git'; $info.WorkingDirectory = $repo
$info.UseShellExecute = $false; $info.RedirectStandardOutput = $true
$info.StandardOutputEncoding = [Text.UTF8Encoding]::new()
foreach($arg in @('diff','--binary','--','src/main/java','plugins')) { $info.ArgumentList.Add($arg) }
$p = [Diagnostics.Process]::Start($info)
$patch = $p.StandardOutput.ReadToEnd(); $p.WaitForExit()
if ($p.ExitCode -ne 0) { throw 'Could not capture source delta' }
$patchFile = Join-Path $output 'tested-source-delta.patch'
[IO.File]::WriteAllText($patchFile, $patch, [Text.UTF8Encoding]::new($false))
$snapshot = [ordered]@{
    baselineCommit = (git rev-parse HEAD).Trim()
    sourceDeltaSha256 = (Get-FileHash $patchFile -Algorithm SHA256).Hash
    scope = 'Existing Stage14A dirty implementation, captured before cross-track tests; not immutable HEAD alone'
    capturedAt = [DateTimeOffset]::UtcNow.ToString('o')
    runtime = 'Windows coding-tool host; production source harness, not installed product or standalone Codex'
}
$snapshot | ConvertTo-Json | Set-Content (Join-Path $output 'source-snapshot.json') -Encoding utf8
$snapshot | ConvertTo-Json -Compress
