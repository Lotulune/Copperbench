param([Parameter(Mandatory = $true)][string]$EvidencePath)
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $EvidencePath) { throw 'Use a new evidence path for every trim.' }
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class Stage16WorkingSetTrim {
    [DllImport("kernel32.dll", SetLastError=true)]
    public static extern IntPtr OpenProcess(uint access, bool inheritHandle, uint processId);
    [DllImport("psapi.dll", SetLastError=true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool EmptyWorkingSet(IntPtr process);
    [DllImport("kernel32.dll", SetLastError=true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool CloseHandle(IntPtr handle);
}
'@
$trialNames = @('chrome','msedge','msedgewebview2','QQ','Weixin','steamwebhelper','steam','Everything')
$trialBefore = Get-CimInstance Win32_OperatingSystem
$trialResults = foreach ($trialProcess in @(Get-Process | Where-Object { $_.ProcessName -in $trialNames -and $_.WorkingSet64 -gt 32MB })) {
    $trialRow = [ordered]@{processId=$trialProcess.Id;processName=$trialProcess.ProcessName;workingSetBeforeBytes=$trialProcess.WorkingSet64;succeeded=$false;errorCode=0;stillRunning=$false;workingSetAfterBytes=$null}
    # QUERY_LIMITED_INFORMATION | SET_QUOTA; no terminate, suspend or injection access.
    $trialHandle = [Stage16WorkingSetTrim]::OpenProcess(0x1100, $false, [uint32]$trialProcess.Id)
    if ($trialHandle -eq [IntPtr]::Zero) {
        $trialRow.errorCode=[Runtime.InteropServices.Marshal]::GetLastWin32Error()
    } else {
        try {
            $trialRow.succeeded=[Stage16WorkingSetTrim]::EmptyWorkingSet($trialHandle)
            if (-not $trialRow.succeeded) { $trialRow.errorCode=[Runtime.InteropServices.Marshal]::GetLastWin32Error() }
        } finally { [void][Stage16WorkingSetTrim]::CloseHandle($trialHandle) }
    }
    $trialCurrent = Get-Process -Id $trialProcess.Id -ErrorAction SilentlyContinue
    if ($trialCurrent) { $trialRow.stillRunning=$true; $trialRow.workingSetAfterBytes=$trialCurrent.WorkingSet64 }
    $trialRow
}
$trialAfter = Get-CimInstance Win32_OperatingSystem
$trialReport = [ordered]@{schemaVersion='1.0';recordedAt=[DateTimeOffset]::UtcNow.ToString('o');method='Windows EmptyWorkingSet on enumerated non-system desktop applications';processesClosed=0;processesSuspended=0;persistentSettingsChanged=$false;freeBytesBefore=[long]$trialBefore.FreePhysicalMemory*1024;freeBytesAfter=[long]$trialAfter.FreePhysicalMemory*1024;processes=@($trialResults)}
$trialReport | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $EvidencePath -Encoding UTF8
[ordered]@{trimmed=@($trialResults | Where-Object {$_.succeeded}).Count;processesClosed=0;availableGiBBefore=[Math]::Round($trialReport.freeBytesBefore/1GB,2);availableGiBAfter=[Math]::Round($trialReport.freeBytesAfter/1GB,2);evidence=$EvidencePath} | ConvertTo-Json
