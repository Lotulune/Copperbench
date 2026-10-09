[CmdletBinding()]
param(
	[string] $OutputDirectory = 'build\ai-live-evals'
)

# S18-05: legacy script/output names are retained for automation compatibility.
# This is a real HTTP MCP protocol-contract run with fixture tasks and mutations.
$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$bundledJavaHome = Join-Path $repositoryRoot 'jdk\jbr25_win_64'
$javaHome = if (Test-Path -LiteralPath (Join-Path $bundledJavaHome 'bin\java.exe')) {
	(Resolve-Path $bundledJavaHome).Path
} elseif ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
	(Resolve-Path $env:JAVA_HOME).Path
} else {
	throw 'JDK 25 was not found in jdk\jbr25_win_64 or JAVA_HOME'
}
$outputPath = if ([IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory } else { Join-Path $repositoryRoot $OutputDirectory }
[IO.Directory]::CreateDirectory($outputPath) | Out-Null

function Stop-ProcessTree([int] $ProcessId) {
	$children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue
	foreach ($child in $children) { Stop-ProcessTree -ProcessId $child.ProcessId }
	Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Invoke-ProtocolContractEval([string] $Profile) {
	$runDirectory = Join-Path $repositoryRoot ("build\ai-live-eval-" + $Profile + '-' + [Guid]::NewGuid().ToString('N'))
	$connectionPath = Join-Path $runDirectory 'connection.json'
	[IO.Directory]::CreateDirectory($runDirectory) | Out-Null
	$gradleProcess = $null
	try {
		$gradleProcess = Start-Process -FilePath (Join-Path $repositoryRoot 'gradlew.bat') `
			-ArgumentList @('runMcpConformanceServer', '--no-daemon', "--args=$runDirectory,$Profile") `
			-WorkingDirectory $repositoryRoot -Environment @{ JAVA_HOME = $javaHome } `
			-RedirectStandardOutput (Join-Path $runDirectory 'server.out.log') `
			-RedirectStandardError (Join-Path $runDirectory 'server.err.log') -WindowStyle Hidden -PassThru
		$deadline = (Get-Date).AddSeconds(180)
		while (-not (Test-Path -LiteralPath $connectionPath)) {
			if ($gradleProcess.HasExited) { throw "MCP contract eval test host exited early. See $runDirectory" }
			if ((Get-Date) -gt $deadline) { throw "MCP contract eval test host did not start within 180 seconds. See $runDirectory" }
			Start-Sleep -Milliseconds 250
		}
		& python (Join-Path $repositoryRoot 'scripts\run-ai-live-evals.py') $connectionPath `
			--mode $Profile --output (Join-Path $outputPath ($Profile + '.json'))
		if ($LASTEXITCODE -ne 0) { throw "MCP protocol-contract evals failed for profile $Profile" }
	} finally {
		if ($gradleProcess) { Stop-ProcessTree -ProcessId $gradleProcess.Id }
		Get-ChildItem -LiteralPath $runDirectory -File -Filter '*.log' -ErrorAction SilentlyContinue |
			ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $outputPath ($Profile + '-' + $_.Name)) -Force }
	}
}

Invoke-ProtocolContractEval 'workspace'
Invoke-ProtocolContractEval 'read_only'

$workspaceResult = Get-Content -Raw (Join-Path $outputPath 'workspace.json') | ConvertFrom-Json
$readOnlyResult = Get-Content -Raw (Join-Path $outputPath 'read_only.json') | ConvertFrom-Json
foreach ($result in @($workspaceResult, $readOnlyResult)) {
	if ($result.scope.kind -ne 'protocol-contract' -or $result.scope.transport -ne 'loopback-http-mcp' -or
		$result.scope.host -ne 'McpConformanceServerMain' -or $result.scope.taskGateway -ne 'InMemoryWorkspaceTaskGateway' -or
		$result.scope.workspaceMutation -ne 'no-op') {
		throw 'The eval report must declare the loopback MCP fixture contract scope'
	}
	foreach ($unsupported in @('modelDriven', 'realBuild', 'repairLoop', 'transportReconnect', 'gameplay')) {
		if ($result.scope.$unsupported -isnot [bool] -or $result.scope.$unsupported) {
			throw "The contract harness cannot report $unsupported coverage"
		}
	}
	if ([int]$result.failed -ne 0) { throw 'A protocol-contract profile contains failed cases' }
}
$passed = [int]$workspaceResult.passed + [int]$readOnlyResult.passed
if ([int]$workspaceResult.passed -ne 9 -or [int]$readOnlyResult.passed -ne 1) {
	throw "Expected 9 workspace and 1 read-only protocol-contract cases, got $passed total"
}
[ordered]@{
	cases = 10
	passed = $passed
	failed = 0
	schemaVersion = $workspaceResult.schemaVersion
	suite = $workspaceResult.suite
	displayName = $workspaceResult.displayName
	scope = $workspaceResult.scope
} | ConvertTo-Json -Depth 6 | `
	Set-Content -Encoding utf8 (Join-Path $outputPath 'summary.json')
Write-Host "MCP protocol-contract evals passed: $passed/10 (fixture tasks; no real build, repair, or transport reconnect)"
