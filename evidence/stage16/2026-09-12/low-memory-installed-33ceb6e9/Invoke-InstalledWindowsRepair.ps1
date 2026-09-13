$ErrorActionPreference = 'Stop'
$trialRoot = 'C:\Temp\Stage16LowMemory-33ceb6e9'
$trialSource = Join-Path $trialRoot 'source'
$trialWorkspaceRoot = Join-Path $trialRoot 'repair-workspace'
$trialEvidence = Join-Path $trialRoot 'repair-evidence'
$trialProduct = 'C:\Copperbench-Stage16-33ceb6e9\copperbench.exe'
$trialUtf8 = New-Object Text.UTF8Encoding($false)
if ((Test-Path -LiteralPath $trialSource) -or (Test-Path -LiteralPath $trialWorkspaceRoot) -or (Test-Path -LiteralPath $trialEvidence)) { throw 'Refusing to overwrite an earlier replay.' }
if ((Get-FileHash -LiteralPath (Join-Path $trialRoot 'resonance-token-source.zip') -Algorithm SHA256).Hash.ToLowerInvariant() -ne '8ddeb1cdf34bcba6a738ab3b8cbac7750160199356710049e9af700185523051') { throw 'Source archive digest mismatch.' }
Expand-Archive -LiteralPath (Join-Path $trialRoot 'resonance-token-source.zip') -DestinationPath $trialSource
Copy-Item -LiteralPath $trialSource -Destination $trialWorkspaceRoot -Recurse
New-Item -ItemType Directory -Path $trialEvidence | Out-Null
$trialSourceHashes = @{}
Get-ChildItem -LiteralPath $trialSource -Recurse -File | ForEach-Object { $trialSourceHashes[$_.FullName.Substring($trialSource.Length + 1)] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
$trialSteps = New-Object Collections.ArrayList
$trialSummary = [ordered]@{schemaVersion='1.0';kind='stage16-installed-windows-low-memory-repair';sourceCommit='33ceb6e9a68aab2b2fcd9153d6c4f23f4d85e6c5';startedAt=[DateTimeOffset]::UtcNow.ToString('o');status='running';productExe=$trialProduct;productExeSha256=(Get-FileHash -LiteralPath $trialProduct -Algorithm SHA256).Hash.ToLowerInvariant();applicationJarSha256=(Get-FileHash -LiteralPath 'C:\Copperbench-Stage16-33ceb6e9\lib\copperbench.jar' -Algorithm SHA256).Hash.ToLowerInvariant();freshAgent=$false;authorizedCreationExercised=$false;desktopAcceptanceExercised=$false;cacheState='Existing guest caches, new source copy; not cold-cache certification';memoryProfile='Hyper-V dynamic: startup/max 4 GiB, minimum 3 GiB';steps=$trialSteps}
function Save-TrialSummary { [IO.File]::WriteAllText((Join-Path $trialEvidence 'summary.json'),($trialSummary | ConvertTo-Json -Depth 90),$trialUtf8) }
function Invoke-TrialProduct([string]$Name,[string[]]$Arguments) {
    foreach($trialArgument in $Arguments) { if ($trialArgument -notmatch '^[A-Za-z0-9_:.\\/=-]+$') { throw 'Unexpected argument character; explicit quoting needed.' } }
    $trialInfo = New-Object Diagnostics.ProcessStartInfo
    $trialInfo.FileName=$trialProduct
    $trialInfo.Arguments=[string]::Join(' ',$Arguments)
    $trialInfo.WorkingDirectory=$trialWorkspaceRoot
    $trialInfo.UseShellExecute=$false
    $trialInfo.CreateNoWindow=$true
    $trialInfo.RedirectStandardOutput=$true
    $trialInfo.RedirectStandardError=$true
    $trialInfo.StandardOutputEncoding=$trialUtf8
    $trialInfo.StandardErrorEncoding=$trialUtf8
    foreach($trialKey in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','JAVA_OPTS','GRADLE_OPTS','COPPERBENCH_STAGE5_GRADLE_EXECUTABLE')) { $trialInfo.EnvironmentVariables.Remove($trialKey) }
    $trialTimer=[Diagnostics.Stopwatch]::StartNew()
    $trialStep=[ordered]@{name=$Name;arguments=$Arguments;startedAt=[DateTimeOffset]::UtcNow.ToString('o');firstOutputMs=$null;callerRuntimeOptionsCleared=$true}
    [void]$trialSteps.Add($trialStep)
    Save-TrialSummary
    $trialProcess=[Diagnostics.Process]::Start($trialInfo)
    $trialStep.processId=$trialProcess.Id
    $trialErrors=$trialProcess.StandardError.ReadToEndAsync()
    $trialWriter=New-Object IO.StreamWriter((Join-Path $trialEvidence ($Name+'.jsonl')),$false,$trialUtf8)
    $trialWriter.AutoFlush=$true
    $trialLast=$null
    try {
        $trialPending=$trialProcess.StandardOutput.ReadLineAsync()
        while ($true) {
            if ($trialTimer.Elapsed.TotalSeconds -gt 1200) { throw ('Product timeout: '+$Name) }
            if (-not $trialPending.Wait(200)) { continue }
            $trialLine=$trialPending.GetAwaiter().GetResult()
            if ($null -eq $trialLine) { break }
            if ($null -eq $trialStep.firstOutputMs) { $trialStep.firstOutputMs=$trialTimer.ElapsedMilliseconds }
            $trialWriter.WriteLine($trialLine)
            $trialLast=$trialLine | ConvertFrom-Json
            $trialPending=$trialProcess.StandardOutput.ReadLineAsync()
        }
        if (-not $trialProcess.WaitForExit(15000)) { throw ('Product did not exit: '+$Name) }
    } finally {
        $trialWriter.Dispose()
        if (-not $trialProcess.HasExited) { $trialProcess.Kill();$trialProcess.WaitForExit() }
        [IO.File]::WriteAllText((Join-Path $trialEvidence ($Name+'.stderr.log')),$trialErrors.GetAwaiter().GetResult(),$trialUtf8)
        $trialStep.completedAt=[DateTimeOffset]::UtcNow.ToString('o')
        $trialStep.elapsedMs=$trialTimer.ElapsedMilliseconds
        $trialStep.processExitCode=$trialProcess.ExitCode
        Save-TrialSummary
    }
    if ($null -eq $trialLast -or $trialLast.exitCode -ne $trialProcess.ExitCode) { throw ('Missing or inconsistent terminal JSON: '+$Name) }
    Write-Host ($Name+': exit='+$trialProcess.ExitCode+', elapsedMs='+$trialStep.elapsedMs)
    return $trialLast
}
function Assert-TrialSuccess($Result,[string]$Name) { if ($Result.exitCode -ne 0 -or $Result.status -ne 'succeeded') { throw ($Name+' failed; inspect JSONL.') } }
try {
    Assert-TrialSuccess (Invoke-TrialProduct '01-discovery' @('bootstrap','list-generators')) 'Discovery'
    $trialDenial=Invoke-TrialProduct '02-no-authorization' @('bootstrap','create-workspace','--generator-id','fabric-1.21.1','--mod-name','Stage16ApprovalProbe','--mod-id','stage16_approval_probe','--workspace-folder',(Join-Path $trialRoot 'unapproved'),'--no-prompt','true')
    if ($trialDenial.code -ne 'USER_APPROVAL_REQUIRED' -or $trialDenial.exitCode -eq 0 -or (Test-Path -LiteralPath (Join-Path $trialRoot 'unapproved'))) { throw 'Noninteractive authorization denial failed.' }
    $trialBase=@('headless','--workspace',(Join-Path $trialWorkspaceRoot 'resonance_token.mcreator'))
    $trialEnvironment=Invoke-TrialProduct '03-environment' ($trialBase+@('environment'))
    Assert-TrialSuccess $trialEnvironment 'Environment'
    $trialSummary.environment=$trialEnvironment.data
    $trialProbe=Join-Path $trialWorkspaceRoot 'src\main\java\copperbench\trial\Stage16MemoryCompileProbe.java'
    New-Item -ItemType Directory -Path (Split-Path -Parent $trialProbe) -Force | Out-Null
    [IO.File]::WriteAllText($trialProbe,'package copperbench.trial; public final class Stage16MemoryCompileProbe { int value = STAGE16_EXPECTED_COMPILE_FAILURE; }',$trialUtf8)
    $trialFailed=Invoke-TrialProduct '04-compile-failure' ($trialBase+@('build','--stream','true'))
    if ($trialFailed.exitCode -eq 0 -or @($trialFailed.diagnostics | Where-Object {$_.code -eq 'JAVA_COMPILE_ERROR' -and $_.path -like '*Stage16MemoryCompileProbe.java'}).Count -eq 0) { throw 'Failure did not identify the injected source error.' }
    [IO.File]::WriteAllText($trialProbe,'package copperbench.trial; public final class Stage16MemoryCompileProbe { int value = 16; }',$trialUtf8)
    Assert-TrialSuccess (Invoke-TrialProduct '05-repaired-build' ($trialBase+@('build','--stream','true'))) 'Repaired build'
    $trialAccepted=Invoke-TrialProduct '06-accepted' ($trialBase+@('run-gametest','--stream','true'))
    Assert-TrialSuccess $trialAccepted 'GameTest'
    $trialVerification=$trialAccepted.task.verification
    if ($trialVerification.status -ne 'passed' -or -not $trialVerification.sourceCurrentAtCompletion -or $trialVerification.acceptanceExecuted -lt 5) { throw 'Missing current-source behavioral verification.' }
    if ((Get-FileHash -LiteralPath $trialVerification.artifactPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $trialVerification.artifactSha256) { throw 'Artifact changed after verification.' }
    if ((Get-FileHash -LiteralPath $trialVerification.reportPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $trialVerification.reportSha256) { throw 'Report changed after verification.' }
    Copy-Item -LiteralPath $trialVerification.artifactPath -Destination (Join-Path $trialEvidence 'tested-mod.jar')
    Copy-Item -LiteralPath $trialVerification.reportPath -Destination (Join-Path $trialEvidence 'gametest.xml')
    $trialSummary.verification=$trialVerification
    foreach($trialRelative in $trialSourceHashes.Keys) { if ((Get-FileHash -LiteralPath (Join-Path $trialSource $trialRelative) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $trialSourceHashes[$trialRelative]) { throw 'Original source changed.' } }
    $trialSummary.sourceUnchanged=$true
    $trialSummary.status='passed'
} catch { $trialSummary.status='failed';$trialSummary.error=$_.Exception.Message;throw } finally { $trialSummary.completedAt=[DateTimeOffset]::UtcNow.ToString('o');Save-TrialSummary }
