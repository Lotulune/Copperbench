[CmdletBinding()]
param(
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$bundledJava = Join-Path $RepositoryRoot 'jdk\jbr25_win_64'
$stamp = Get-Date -Format 'yyyy-MM-dd'
$evidenceDir = Join-Path $RepositoryRoot "evidence\stage-8\$stamp"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$previousJavaHome = $env:JAVA_HOME
try {
	$env:JAVA_HOME = (Resolve-Path $bundledJava).Path
	$env:Path = "$env:JAVA_HOME\bin;$env:Path"
	Push-Location $RepositoryRoot
	try {
		& .\gradlew.bat 'test' `
			'--tests' 'dev.copperbench.assets.ResourcePack1211ClientLoadProbeTest' `
			'-Dcopperbench.resourcePack1211Client=true' `
			'--no-daemon'
		if ($LASTEXITCODE -ne 0) { throw "Resource pack client load probe failed with exit $LASTEXITCODE" }
	} finally { Pop-Location }
	$probe = Join-Path $evidenceDir 'resource-pack-1211-client.json'
	if (-not (Test-Path -LiteralPath $probe)) { throw "Missing evidence: $probe" }
	Write-Output ("evidence=" + $probe)
} finally {
	$env:JAVA_HOME = $previousJavaHome
}
