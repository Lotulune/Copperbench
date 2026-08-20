[CmdletBinding()]
param(
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$bundledJava = Join-Path $RepositoryRoot 'jdk\jbr25_win_64'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-8\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$summary = [ordered]@{ schemaVersion = '1.0'; kind = 'preview-track-runclient-probe-runner'; generatorId = 'fabric-26.1.2'; probeRan = $false; runClientSucceeded = $false; goldenClaimed = $false }
$startedAt = Get-Date
$previousJavaHome = $env:JAVA_HOME
try {
	$env:JAVA_HOME = (Resolve-Path $bundledJava).Path
	$env:Path = "$env:JAVA_HOME\bin;$env:Path"
	Push-Location $RepositoryRoot
	try {
		& .\gradlew.bat 'test' '--tests' 'dev.copperbench.generator.fabric.Fabric261RunClientProbeTest' '-Dcopperbench.fabric261RunClient=true' '--no-daemon'
		if ($LASTEXITCODE -ne 0) { throw "Fabric 26.1 runClient probe failed with exit $LASTEXITCODE" }
	} finally { Pop-Location }
	$probe = Get-Content -Raw -LiteralPath (Join-Path $evidenceDir 'fabric-261-runclient.json') | ConvertFrom-Json
	$summary.probeRan = [bool]$probe.generateSucceeded
	$summary.runClientSucceeded = [bool]$probe.runClientSucceeded
	$summary.probeEvidence = Join-Path $evidenceDir 'fabric-261-runclient.json'
	if (-not $probe.generateSucceeded) { throw 'Fabric 26.1 generate did not succeed' }
} catch {
	$summary.error = $_.Exception.Message
	throw
} finally {
	$env:JAVA_HOME = $previousJavaHome
	$summary.completedAt = (Get-Date).ToString('o')
	$summary.durationSeconds = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
	($summary | ConvertTo-Json -Depth 5) | Set-Content -LiteralPath (Join-Path $evidenceDir 'fabric-261-runclient-runner.json') -Encoding utf8
	Write-Output ("evidence=" + (Join-Path $evidenceDir 'fabric-261-runclient-runner.json'))
}
