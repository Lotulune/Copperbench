param([string]$RunRoot)
$ErrorActionPreference = 'Stop'
$out = Join-Path $RunRoot 'resource-samples.jsonl'
$stopFile = Join-Path $RunRoot 'resource-monitor.stop'
while (-not (Test-Path -LiteralPath $stopFile)) {
    $os = Get-CimInstance Win32_OperatingSystem
    $vms = @(Get-VM -Name 'Copperbench-G7','Copperbench-Stage15-Linux' | ForEach-Object { @{name=$_.Name;state=[string]$_.State;assignedBytes=$_.MemoryAssigned;maximumBytes=$_.MemoryMaximum} })
    $java = @(Get-Process -Name java,javaw,copperbench -ErrorAction SilentlyContinue)
    $row = [ordered]@{at=[DateTime]::UtcNow.ToString('o');availableBytes=[long]$os.FreePhysicalMemory*1024;diskDFreeBytes=(Get-PSDrive D).Free;virtualMachines=$vms;javaProcessCount=$java.Count;javaWorkingSetBytes=($java | Measure-Object WorkingSet64 -Sum).Sum}
    $row | ConvertTo-Json -Compress -Depth 5 | Add-Content -LiteralPath $out -Encoding UTF8
    Start-Sleep -Seconds 10
}
