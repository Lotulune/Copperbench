param([Parameter(Mandatory)][string]$EvidencePath, [Parameter(Mandatory)][string]$StopPath)
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $EvidencePath) { throw 'Evidence already exists' }
$deadline = [DateTimeOffset]::UtcNow.AddHours(4)
do {
    $os = Get-CimInstance Win32_OperatingSystem
    $vms = @(Get-VM -Name 'Copperbench-G7','Copperbench-Stage15-Linux' | ForEach-Object {
        [ordered]@{name=$_.Name; state=[string]$_.State; assignedBytes=$_.MemoryAssigned; demandBytes=$_.MemoryDemand}
    })
    [ordered]@{at=[DateTimeOffset]::UtcNow.ToString('o'); hostAvailableBytes=[long]$os.FreePhysicalMemory * 1024;
        hostTotalBytes=[long]$os.TotalVisibleMemorySize * 1024; vms=$vms} |
        ConvertTo-Json -Depth 5 -Compress | Add-Content -LiteralPath $EvidencePath -Encoding UTF8
    Start-Sleep -Seconds 10
} while (!(Test-Path -LiteralPath $StopPath) -and [DateTimeOffset]::UtcNow -lt $deadline)
