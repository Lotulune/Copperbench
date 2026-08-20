[CmdletBinding()]
param(
	[string] $OutputDirectory = 'evidence\stage-2\2026-08-17\mcp-conformance-applicable'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaHome = (Resolve-Path (Join-Path $repositoryRoot 'jdk\jbr25_win_64')).Path
$runDirectory = Join-Path $repositoryRoot ("build\mcp-conformance-" + [Guid]::NewGuid().ToString('N'))
$connectionPath = Join-Path $runDirectory 'connection.json'
$outputPath = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
	$OutputDirectory
} else {
	Join-Path $repositoryRoot $OutputDirectory
}
$scenarios = @(
	'server-initialize',
	'logging-set-level',
	'ping',
	'tools-list',
	'server-sse-multiple-streams',
	'dns-rebinding-protection'
)
$gradleProcess = $null
$proxyProcess = $null

function Stop-ProcessTree([int] $ProcessId) {
	$children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue
	foreach ($child in $children) {
		Stop-ProcessTree -ProcessId $child.ProcessId
	}
	Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

try {
	[IO.Directory]::CreateDirectory($runDirectory) | Out-Null
	[IO.Directory]::CreateDirectory($outputPath) | Out-Null
	$gradleProcess = Start-Process -FilePath (Join-Path $repositoryRoot 'gradlew.bat') `
		-ArgumentList @('runMcpConformanceServer', '--no-daemon', "--args=$runDirectory") `
		-WorkingDirectory $repositoryRoot `
		-Environment @{ JAVA_HOME = $javaHome } `
		-RedirectStandardOutput (Join-Path $runDirectory 'server.out.log') `
		-RedirectStandardError (Join-Path $runDirectory 'server.err.log') `
		-WindowStyle Hidden -PassThru

	$deadline = (Get-Date).AddSeconds(45)
	while (-not (Test-Path -LiteralPath $connectionPath)) {
		if ($gradleProcess.HasExited) {
			throw "Conformance server exited early. See $runDirectory"
		}
		if ((Get-Date) -gt $deadline) {
			throw "Conformance server did not start within 45 seconds. See $runDirectory"
		}
		Start-Sleep -Milliseconds 250
	}

	$proxyProcess = Start-Process -FilePath 'node.exe' `
		-ArgumentList @((Join-Path $repositoryRoot 'scripts\mcp-conformance-proxy.mjs'), $connectionPath, '61999') `
		-WorkingDirectory $repositoryRoot `
		-RedirectStandardOutput (Join-Path $runDirectory 'proxy.out.log') `
		-RedirectStandardError (Join-Path $runDirectory 'proxy.err.log') `
		-WindowStyle Hidden -PassThru

	$deadline = (Get-Date).AddSeconds(15)
	do {
		if ($proxyProcess.HasExited) {
			throw "Conformance proxy exited early. See $runDirectory"
		}
		$ready = (Test-Path (Join-Path $runDirectory 'proxy.out.log')) -and
			((Get-Content -Raw (Join-Path $runDirectory 'proxy.out.log')) -match '^READY ')
		if (-not $ready) { Start-Sleep -Milliseconds 200 }
	} while (-not $ready -and (Get-Date) -le $deadline)
	if (-not $ready) { throw "Conformance proxy did not start within 15 seconds. See $runDirectory" }

	$summary = @()
	$totalChecks = 0
	foreach ($scenario in $scenarios) {
		& npx --yes '@modelcontextprotocol/conformance@0.1.16' server `
			--url 'http://127.0.0.1:61999/mcp' `
			--scenario $scenario `
			--spec-version '2025-11-25' `
			--output-dir $outputPath

		# v0.1.16 can terminate with a libuv UV_HANDLE_CLOSING assertion on
		# Windows after writing a successful report, so the report is the
		# authority rather than the process exit code.
		$reportDirectory = Get-ChildItem -LiteralPath $outputPath -Directory |
			Where-Object Name -Like "server-$scenario-*" |
			Sort-Object LastWriteTime -Descending |
			Select-Object -First 1
		if (-not $reportDirectory) { throw "No report was written for $scenario" }
		$checks = Get-Content -Raw (Join-Path $reportDirectory.FullName 'checks.json') | ConvertFrom-Json
		$failures = @($checks | Where-Object status -ne 'SUCCESS')
		if ($failures.Count -gt 0) {
			throw "Conformance scenario $scenario has $($failures.Count) non-success checks"
		}
		$scenarioChecks = @($checks).Count
		$totalChecks += $scenarioChecks
		$summary += [ordered]@{ scenario = $scenario; checks = $scenarioChecks; failures = 0 }
	}

	$result = [ordered]@{
		tool = '@modelcontextprotocol/conformance'
		toolVersion = '0.1.16'
		specVersion = '2025-11-25'
		applicableScenarios = $summary
		checks = $totalChecks
		failures = 0
		windowsCliExitIssue = 'UV_HANDLE_CLOSING may occur after a successful report; checks.json is authoritative.'
	}
	[IO.File]::WriteAllText((Join-Path $outputPath 'summary.json'),
		($result | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
} finally {
	if ($proxyProcess) { Stop-ProcessTree -ProcessId $proxyProcess.Id }
	if ($gradleProcess) { Stop-ProcessTree -ProcessId $gradleProcess.Id }
}
