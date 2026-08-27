[CmdletBinding()]
param(
	[string] $OutputDirectory = 'build\ai-live-evals'
)

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

function Invoke-LiveEval([string] $Profile) {
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
			if ($gradleProcess.HasExited) { throw "AI eval server exited early. See $runDirectory" }
			if ((Get-Date) -gt $deadline) { throw "AI eval server did not start within 180 seconds. See $runDirectory" }
			Start-Sleep -Milliseconds 250
		}
		& python (Join-Path $repositoryRoot 'scripts\run-ai-live-evals.py') $connectionPath `
			--mode $Profile --output (Join-Path $outputPath ($Profile + '.json'))
		if ($LASTEXITCODE -ne 0) { throw "AI live evals failed for profile $Profile" }
	} finally {
		if ($gradleProcess) { Stop-ProcessTree -ProcessId $gradleProcess.Id }
	}
}

Invoke-LiveEval 'workspace'
Invoke-LiveEval 'read_only'

$workspaceResult = Get-Content -Raw (Join-Path $outputPath 'workspace.json') | ConvertFrom-Json
$readOnlyResult = Get-Content -Raw (Join-Path $outputPath 'read_only.json') | ConvertFrom-Json
$passed = [int]$workspaceResult.passed + [int]$readOnlyResult.passed
if ($passed -ne 10) { throw "Expected 10 passing live eval cases, got $passed" }
[ordered]@{ cases = 10; passed = $passed; failed = 0 } | ConvertTo-Json | `
	Set-Content -Encoding utf8 (Join-Path $outputPath 'summary.json')
Write-Host "AI live evals passed: $passed/10"
