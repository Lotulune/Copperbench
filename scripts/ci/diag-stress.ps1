[CmdletBinding()]
param(
	[int] $Iterations = 12
)

# Temporary: repeat the conformance suite and stop at the first sign of runner damage.
$ErrorActionPreference = 'Continue'
$worker = Get-Process -Name 'Runner.Worker' | Select-Object -First 1
$runnerRoot = Split-Path (Split-Path $worker.Path)
$runnerNode = Join-Path $runnerRoot 'externals\node24\bin\node.exe'

function Get-Watched {
	Get-CimInstance Win32_Process | Where-Object {
		$_.Name -in 'explorer.exe', 'winlogon.exe', 'csrss.exe', 'wininit.exe', 'Runner.Worker.exe', 'Runner.Listener.exe', 'hosted-compute-agent', 'SystemPropertiesPerformance.exe' -or
		($_.Name -eq 'conhost.exe' -and $_.ParentProcessId -eq $worker.Id)
	} | ForEach-Object { "$($_.Name)#$($_.ProcessId)" } | Sort-Object
}

$baseline = @(Get-Watched)
Write-Host "STRESS baseline watched processes: $($baseline -join ', ')"
for ($i = 1; $i -le $Iterations; $i++) {
	$started = Get-Date
	& pwsh -NoProfile -File (Join-Path $PSScriptRoot '..\verify-mcp-conformance.ps1') -OutputDirectory "build/mcp-conformance-results-$i" 2>&1 |
		ForEach-Object { if ("$_" -match 'WARNING|Exception|exited before|did not') { Write-Host "  [$i] $_" } }
	$suiteExit = $LASTEXITCODE
	$nodeOutput = & $runnerNode -e "console.log(process.version)" 2>&1
	$nodeExit = $LASTEXITCODE
	$leftover = @(Get-CimInstance Win32_Process | Where-Object { $_.ProcessId -ne $PID -and $_.CommandLine -match 'mcp-conformance-[0-9a-f]{32}|mcp-conformance-proxy|modelcontextprotocol|runMcpConformanceServer' } | ForEach-Object { "$($_.Name)#$($_.ProcessId)" })
	$now = @(Get-Watched)
	$missing = @($baseline | Where-Object { $_ -notin $now })
	Write-Host ("STRESS iteration {0}: suiteExit={1} seconds={2:n0} runnerNodeExit={3} (0x{3:X8}) nodeOutput={4} missingWatched=[{5}] leftover=[{6}]" -f $i, $suiteExit, ((Get-Date) - $started).TotalSeconds, $nodeExit, $nodeOutput, ($missing -join ', '), ($leftover -join ', '))
	if ($suiteExit -ne 0 -or $nodeExit -ne 0 -or $missing.Count -gt 0 -or $leftover.Count -gt 0) {
		Write-Host "STRESS anomaly detected at iteration $i; collecting state"
		& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'diag-runner-state.ps1') -Phase after
		Get-ChildItem "build/mcp-conformance-results-$i" -Filter '*.log' -ErrorAction SilentlyContinue | Where-Object Length -gt 0 | ForEach-Object {
			Write-Host "--- $($_.Name)"; Get-Content $_.FullName -Tail 40 | ForEach-Object { Write-Host $_ }
		}
		exit 1
	}
}
Write-Host "STRESS completed $Iterations iterations without anomaly"
exit 0
