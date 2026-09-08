$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$jdk = [IO.Path]::GetFullPath((Join-Path $repo '../../jdk/jdk21_win_64'))
$source = Join-Path $repo 'examples/agent-native/survey-pulse'
$output = Join-Path $PSScriptRoot 'pure-classes'
New-Item -ItemType Directory -Force $output | Out-Null
$files = @(
    (Join-Path $source 'src/main/java/dev/example/surveypulse/OreScanner.java'),
    (Join-Path $source 'src/main/java/dev/example/surveypulse/ScanResult.java'),
    (Join-Path $source 'src/contractTest/java/dev/example/surveypulse/ScannerContractTest.java')
)
& "$jdk/bin/javac.exe" --release 17 -encoding UTF-8 -d $output $files
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$lines = & "$jdk/bin/java.exe" -cp $output dev.example.surveypulse.ScannerContractTest
$code = $LASTEXITCODE
$lines | Set-Content (Join-Path $PSScriptRoot 'pure-contract.log') -Encoding utf8
$result = [ordered]@{scope='Shared scanner logic only; identical helper files used in each track; no Minecraft interaction, item registration or network test';compileRelease=17;runtime=21;exitCode=$code;assertionsPassed=@($lines | Where-Object { $_ -match '^PASS ' }).Count;finishedAt=[DateTimeOffset]::UtcNow.ToString('o')}
$result | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'pure-contract.json') -Encoding utf8
$result | ConvertTo-Json -Compress
exit $code
