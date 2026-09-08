param([switch]$SkipProductCompile)
$ErrorActionPreference = 'Stop'
$repo = (Get-Location).Path
$root = [IO.Path]::GetFullPath((Join-Path $repo '../..'))
$env:JAVA_HOME = Join-Path $root 'jdk/jbr25_win_64'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$env:TEMP = Join-Path $repo '.tmp/stage14-track-audit/tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force $env:TEMP | Out-Null
if (-not $SkipProductCompile) {
    & ./gradlew.bat -I .tmp/stage14-track-audit/runtime.gradle prepareTrackAuditRuntime --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
$cp = [IO.File]::ReadAllText((Join-Path $repo '.tmp/stage14-track-audit/classpath.txt'))
& "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $cp -d .tmp/stage14-track-audit/classes .tmp/stage14-track-audit/TrackAudit.java
exit $LASTEXITCODE
