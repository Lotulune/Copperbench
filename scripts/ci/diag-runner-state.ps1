[CmdletBinding()]
param(
	[Parameter(Mandatory)] [ValidateSet('before', 'after', 'post-upload')] [string] $Phase
)

# Temporary runner diagnostics for the MCP conformance job. Never fails the step.
$ErrorActionPreference = 'Continue'
$snapshotPath = Join-Path $env:RUNNER_TEMP 'diag-processes-before.json'

function Get-ProcessSnapshot {
	Get-CimInstance Win32_Process | ForEach-Object {
		[pscustomobject]@{
			Pid = $_.ProcessId
			ParentPid = $_.ParentProcessId
			Name = $_.Name
			Created = if ($_.CreationDate) { $_.CreationDate.ToString('o') } else { '' }
			CommandLine = $_.CommandLine
		}
	}
}

function Write-Section([string] $Title) { Write-Host "`n===== DIAG[$Phase] $Title =====" }

Write-Section 'disk'
Get-PSDrive -PSProvider FileSystem | Format-Table Name, @{ n = 'FreeGB'; e = { [math]::Round($_.Free / 1GB, 2) } }, @{ n = 'UsedGB'; e = { [math]::Round($_.Used / 1GB, 2) } } -AutoSize | Out-String | Write-Host

Write-Section 'memory'
Get-CimInstance Win32_OperatingSystem | Format-List FreePhysicalMemory, FreeVirtualMemory, TotalVisibleMemorySize | Out-String | Write-Host

$current = @(Get-ProcessSnapshot)
if ($Phase -eq 'before') {
	$current | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $snapshotPath -Encoding UTF8
	Write-Section 'process tree (all)'
	$current | Sort-Object Pid | Format-Table Pid, ParentPid, Name, Created, CommandLine -AutoSize | Out-String -Width 400 | Write-Host
} elseif (Test-Path -LiteralPath $snapshotPath) {
	$before = @(Get-Content -Raw -LiteralPath $snapshotPath | ConvertFrom-Json)
	$beforeKeys = @{}
	foreach ($p in $before) { $beforeKeys["$($p.Pid)|$($p.Created)"] = $p }
	$currentKeys = @{}
	foreach ($p in $current) { $currentKeys["$($p.Pid)|$($p.Created)"] = $p }
	Write-Section 'processes that DISAPPEARED since before-suite snapshot'
	$before | Where-Object { -not $currentKeys.ContainsKey("$($_.Pid)|$($_.Created)") } |
		Format-Table Pid, ParentPid, Name, Created, CommandLine -AutoSize | Out-String -Width 400 | Write-Host
	Write-Section 'processes that are NEW since before-suite snapshot'
	$current | Where-Object { -not $beforeKeys.ContainsKey("$($_.Pid)|$($_.Created)") } |
		Format-Table Pid, ParentPid, Name, Created, CommandLine -AutoSize | Out-String -Width 400 | Write-Host
}

Write-Section 'environment names mentioning NODE / NPM and file-command sizes'
Get-ChildItem Env: | Where-Object Name -Match 'NODE|NPM|UV_' | ForEach-Object { Write-Host "$($_.Name)=$($_.Value)" }
foreach ($name in 'GITHUB_ENV', 'GITHUB_PATH', 'GITHUB_OUTPUT', 'GITHUB_STATE') {
	$path = [Environment]::GetEnvironmentVariable($name)
	if ($path -and (Test-Path -LiteralPath $path)) { Write-Host "$name -> $path ($((Get-Item -LiteralPath $path).Length) bytes)" }
}

Write-Section 'runner bundled node'
$worker = Get-Process -Name 'Runner.Worker' -ErrorAction SilentlyContinue | Select-Object -First 1
$runnerRoot = if ($worker -and $worker.Path) { Split-Path (Split-Path $worker.Path) } else { $null }
Write-Host "Runner.Worker path: $($worker.Path); root: $runnerRoot"
if ($runnerRoot) {
	Get-ChildItem -LiteralPath (Join-Path $runnerRoot 'externals') -Directory -ErrorAction SilentlyContinue | ForEach-Object {
		$node = Join-Path $_.FullName 'bin\node.exe'
		if (Test-Path -LiteralPath $node) {
			$item = Get-Item -LiteralPath $node
			$output = & $node -e "console.log(process.version, process.pid)" 2>&1
			Write-Host "$node size=$($item.Length) exit=$LASTEXITCODE output=$output"
		} else {
			Write-Host "$($_.FullName): no bin\node.exe"
		}
	}
}
$pathNode = Get-Command node.exe -ErrorAction SilentlyContinue
if ($pathNode) {
	$output = & $pathNode.Source -e "console.log(process.version, process.pid)" 2>&1
	Write-Host "PATH node $($pathNode.Source) exit=$LASTEXITCODE output=$output"
}

if ($Phase -ne 'before') {
	Write-Section 'conformance output directory'
	$results = Join-Path $env:GITHUB_WORKSPACE 'build\mcp-conformance-results'
	if (Test-Path -LiteralPath $results) {
		Get-ChildItem -LiteralPath $results -Recurse -File | ForEach-Object { Write-Host ("{0,10} {1}" -f $_.Length, $_.FullName.Substring($results.Length + 1)) }
		Get-ChildItem -LiteralPath $results -File -Filter '*.log' | Where-Object Length -gt 0 | ForEach-Object {
			Write-Host "`n--- $($_.Name) (last 60 lines)"
			Get-Content -LiteralPath $_.FullName -Tail 60 | ForEach-Object { Write-Host $_ }
		}
	} else {
		Write-Host "missing: $results"
	}

	Write-Section 'npx cache package versions'
	$npxRoot = Join-Path $env:LOCALAPPDATA 'npm-cache\_npx'
	Get-ChildItem -LiteralPath $npxRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object {
		Get-ChildItem -LiteralPath (Join-Path $_.FullName 'node_modules') -Filter 'package.json' -Recurse -Depth 2 -ErrorAction SilentlyContinue | ForEach-Object {
			try {
				$package = Get-Content -Raw -LiteralPath $_.FullName | ConvertFrom-Json
				if ($package.name -and $package.version) { "$($package.name)@$($package.version)" }
			} catch { }
		}
	} | Sort-Object -Unique | ForEach-Object { Write-Host $_ }

	Write-Section 'application event log errors (last 15 minutes)'
	Get-WinEvent -FilterHashtable @{ LogName = 'Application'; Level = 1, 2; StartTime = (Get-Date).AddMinutes(-15) } -ErrorAction SilentlyContinue |
		Select-Object -First 20 | ForEach-Object { Write-Host "$($_.TimeCreated.ToString('o')) [$($_.ProviderName) $($_.Id)] $($_.Message)" }

	Write-Section 'system event log errors (last 15 minutes)'
	Get-WinEvent -FilterHashtable @{ LogName = 'System'; Level = 1, 2; StartTime = (Get-Date).AddMinutes(-15) } -ErrorAction SilentlyContinue |
		Select-Object -First 20 | ForEach-Object { Write-Host "$($_.TimeCreated.ToString('o')) [$($_.ProviderName) $($_.Id)] $($_.Message)" }
}

if ($runnerRoot -and $Phase -ne 'before') {
	Write-Section 'runner worker diagnostic log (filtered tail)'
	$diag = Get-ChildItem -LiteralPath (Join-Path $runnerRoot '_diag') -Filter 'Worker_*.log' -ErrorAction SilentlyContinue |
		Sort-Object LastWriteTime -Descending | Select-Object -First 1
	if ($diag) {
		Write-Host "log: $($diag.FullName) ($($diag.Length) bytes)"
		$stream = [IO.FileStream]::new($diag.FullName, 'Open', 'Read', 'ReadWrite')
		try {
			$reader = [IO.StreamReader]::new($stream)
			$lines = $reader.ReadToEnd() -split "`r?`n"
		} finally { $stream.Dispose() }
		$lines | Select-Object -Last 400 |
			Where-Object { $_ -match 'ProcessInvoker|Handler|StepsRunner|exit code|ERR |WARN|Exception|   at |node|Node|upload-artifact|Finished process|Starting process|File name|Arguments' } |
			Where-Object { $_ -notmatch '(?i)token|secret|password|authorization' } |
			Select-Object -Last 150 | ForEach-Object { Write-Host $_ }
	} else {
		Write-Host 'no Worker_*.log found'
	}
}
exit 0
