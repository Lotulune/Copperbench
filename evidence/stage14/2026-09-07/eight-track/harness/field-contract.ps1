$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$jdk = [IO.Path]::GetFullPath((Join-Path $repo '../../jdk/jbr25_win_64'))
$cp = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'classpath.txt'))
$cp = (Join-Path $PSScriptRoot 'classes')+';'+$cp
$env:MCREATOR_HOME = Join-Path $PSScriptRoot 'field-contract-user'
$env:TEMP = Join-Path $PSScriptRoot 'tmp/field-contract'; $env:TMP=$env:TEMP
New-Item -ItemType Directory -Force $env:TEMP | Out-Null
& "$jdk/bin/javac.exe" -encoding UTF-8 -cp $cp -d (Join-Path $PSScriptRoot 'classes') (Join-Path $PSScriptRoot 'FieldContractAudit.java')
if($LASTEXITCODE -ne 0) {exit $LASTEXITCODE}
& "$jdk/bin/java.exe" --add-opens java.base/java.lang=ALL-UNNAMED --enable-native-access=ALL-UNNAMED '-Djava.awt.headless=true' '-Dfile.encoding=UTF-8' -Xmx1024m -cp $cp dev.copperbench.headless.FieldContractAudit
exit $LASTEXITCODE
