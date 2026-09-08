param([Parameter(Mandatory=$true)][string]$Track)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ('projects/' + $Track)))
$stopped = @()
foreach($proc in Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'") {
    if ($proc.CommandLine -and ($proc.CommandLine.Contains($root) -or $proc.CommandLine.Contains($root.Replace('\','/')))) {
        Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
        $stopped += $proc.ProcessId
    }
}
[ordered]@{track=$Track;stopped=$stopped;rule='Only Java command lines containing this unique disposable track directory';checkedAt=[DateTimeOffset]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content (Join-Path $root 'runtime-cleanup.json') -Encoding utf8
Write-Output ('STOPPED_UNIQUE_FIXTURE_RUNTIME '+$Track+' count='+$stopped.Count)
