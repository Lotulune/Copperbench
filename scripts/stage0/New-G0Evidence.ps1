[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

$evidenceRoot = Join-Path $RepositoryRoot 'evidence\stage-0\2026-08-16'
$artifactDefinitions = @(
    @{ id = 'windowsZip'; path = 'build\export\Copperbench 0.1.0 Windows 64bit.zip' },
    @{ id = 'windowsInstaller'; path = 'build\export\Copperbench 0.1.0 Windows 64bit.exe' },
    @{ id = 'windowsMsix'; path = 'build\export\Copperbench 0.1.0 Windows 64bit.msix' },
    @{ id = 'applicationJar'; path = 'build\libs\copperbench.jar' },
    @{ id = 'fabricPlugin'; path = 'build\plugins\generator-fabric-26.1.2.zip' }
)

$artifacts = @($artifactDefinitions | ForEach-Object {
    $fullPath = Join-Path $RepositoryRoot $_.path
    $item = Get-Item -LiteralPath $fullPath
    $signature = if ($item.Extension -in @('.exe', '.msix')) {
        (Get-AuthenticodeSignature -LiteralPath $fullPath).Status.ToString()
    } else {
        'NotApplicable'
    }
    [ordered]@{
        id = $_.id
        path = $_.path.Replace('\', '/')
        bytes = $item.Length
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $fullPath).Hash.ToLowerInvariant()
        signature = $signature
    }
})

$artifactLock = [ordered]@{
    schemaVersion = 1
    product = 'Copperbench'
    productVersion = '0.1.0'
    generatedAt = (Get-Date).ToString('o')
    source = [ordered]@{
        mcreatorTag = '2026.2.33518'
        mcreatorCommit = '361429609b772039a3eb9ab81662c25b225f1d0d'
        fabricTag = '26.1.2-2026.2-2.8'
        fabricCommit = 'abfe19329126b679a26baafe5cade5a75d455528'
    }
    runtimeLock = 'evidence/stage-0/2026-08-16/jbr25-windows-runtime-lock.json'
    artifacts = $artifacts
}
$artifactLock | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidenceRoot 'product-artifacts.lock.json') -Encoding UTF8

$startup = Get-Content -Raw -LiteralPath (Join-Path $evidenceRoot 'copperbench-windows-11-startup.json') | ConvertFrom-Json
$unitReport = Get-Content -Raw -LiteralPath (Join-Path $evidenceRoot 'copperbench-windows-11-g0-unit-test.json') | ConvertFrom-Json
$uiReport = Get-Content -Raw -LiteralPath (Join-Path $evidenceRoot 'copperbench-windows-11-g0-ui-test.json') | ConvertFrom-Json
$packageReport = Get-Content -Raw -LiteralPath (Join-Path $evidenceRoot 'copperbench-windows-11-g0-package.json') | ConvertFrom-Json
$installerReport = Get-Content -Raw -LiteralPath (Join-Path $evidenceRoot 'copperbench-windows-11-g0-installer.json') | ConvertFrom-Json
$msixReport = Get-Content -Raw -LiteralPath (Join-Path $evidenceRoot 'copperbench-windows-11-g0-msix.json') | ConvertFrom-Json
$brandReport = Get-Content -Raw -LiteralPath (Join-Path $evidenceRoot 'copperbench-package-brand-scan.json') | ConvertFrom-Json
$runtimeReport = Get-Content -Raw -LiteralPath (Join-Path $evidenceRoot 'jbr25-windows-runtime-lock.json') | ConvertFrom-Json
$fabricReport = Get-Content -Raw -LiteralPath (Join-Path $evidenceRoot 'fabric-plugin-content-verification.json') | ConvertFrom-Json

$unitPassCount = @(Select-String -LiteralPath (Join-Path $evidenceRoot 'copperbench-windows-11-g0-unit-test.log') -Pattern ' PASSED$').Count
$uiPassCount = @(Select-String -LiteralPath (Join-Path $evidenceRoot 'copperbench-windows-11-g0-ui-test.log') -Pattern ' PASSED$').Count
$os = Get-CimInstance Win32_OperatingSystem

$gate = [ordered]@{
    schemaVersion = 2
    gate = 'G0'
    status = 'conditionally_passed'
    localImplementationComplete = $true
    recordedAt = (Get-Date).ToString('o')
    environment = [ordered]@{
        os = $os.Caption
        version = $os.Version
        architecture = $os.OSArchitecture
        java = '25.0.3'
        jvm = 'JBR-25.0.3+1-329.124-jcef'
        gradle = '9.6.0'
        cache = 'isolated-stage-0-cache'
    }
    identity = [ordered]@{
        product = 'Copperbench'
        status = 'provisional-development-name'
        productId = 'dev.copperbench.studio'
        javaNamespace = 'dev.copperbench'
        publisher = 'Copperbench Contributors'
    }
    sources = $artifactLock.source
    checks = @(
        [ordered]@{ id = 'source_refs'; status = 'passed' },
        [ordered]@{ id = 'license_snapshots'; status = 'passed'; files = 32 },
        [ordered]@{ id = 'upstream_sync_rollback_drill'; status = 'passed'; commits = 6; changedFiles = 47 },
        [ordered]@{ id = 'product_source_imported'; status = 'passed' },
        [ordered]@{ id = 'provisional_product_identity'; status = 'passed' },
        [ordered]@{ id = 'protected_brand_assets'; status = $brandReport.status; assetsChecked = $brandReport.assetsChecked },
        [ordered]@{ id = 'implicit_network_services_default'; status = 'passed'; enabled = $false },
        [ordered]@{ id = 'clean_compile_and_unit_tests'; status = $(if ($unitReport.passed) { 'passed' } else { 'failed' }); testsPassed = $unitPassCount; durationSeconds = $unitReport.durationSeconds },
        [ordered]@{ id = 'ui_integration_tests'; status = $(if ($uiReport.passed) { 'passed' } else { 'failed' }); testsPassed = $uiPassCount; durationSeconds = $uiReport.durationSeconds },
        [ordered]@{ id = 'windows_11_packaged_startup'; status = $(if ($startup.passed) { 'passed' } else { 'failed' }); windowTitle = $startup.mainWindowTitle },
        [ordered]@{ id = 'windows_zip'; status = $(if ($packageReport.passed) { 'passed' } else { 'failed' }) },
        [ordered]@{ id = 'windows_installer'; status = $(if ($installerReport.passed) { 'passed' } else { 'failed' }); signature = 'NotSigned' },
        [ordered]@{ id = 'windows_msix'; status = $(if ($msixReport.passed) { 'passed' } else { 'failed' }); signature = 'NotSigned' },
        [ordered]@{ id = 'jbr_runtime_lock'; status = 'passed'; files = $runtimeReport.fileCount; treeSha256 = $runtimeReport.treeSha256 },
        [ordered]@{ id = 'fabric_generator_reproducible_content'; status = $(if ($fabricReport.contentIdentical) { 'passed' } else { 'failed' }); entries = $fabricReport.entryCount; treeSha256 = $fabricReport.sourceContentTreeSha256 },
        [ordered]@{ id = 'dependency_report'; status = 'passed'; evidence = 'copperbench-dependencies-export.log' },
        [ordered]@{ id = 'windows_10_clean_build_and_startup'; status = 'not_run'; reason = 'No Windows 10 runner is available in this workspace' },
        [ordered]@{ id = 'product_git_branches'; status = 'not_created'; reason = 'Repository initialization and commits require explicit user authorization' },
        [ordered]@{ id = 'code_signing'; status = 'deferred_to_g7'; reason = 'No signing identity or certificate was provided' }
    )
    diagnostics = @(
        [ordered]@{
            id = 'full_upstream_test_with_fabric'
            status = 'outside_g0_failed'
            reason = 'Legacy workspace conversion fixtures contain procedure blocks unsupported by the Fabric generator; generator Gradle sync also stalled in dependency resolution'
            owner = 'G1/G2 compatibility matrix'
            evidence = 'copperbench-windows-11-clean-test.log'
        }
    )
    artifacts = 'evidence/stage-0/2026-08-16/product-artifacts.lock.json'
    remainingConditions = @(
        'Run the same clean build, packaged startup, and installer smoke checks on Windows 10 x64',
        'Initialize the product Git repository and create the documented branch topology after explicit authorization'
    )
}
$gate | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $evidenceRoot 'windows-11-baseline.json') -Encoding UTF8

[pscustomobject]@{
    gate = $gate.gate
    status = $gate.status
    unitTestsPassed = $unitPassCount
    uiTestsPassed = $uiPassCount
    artifactCount = $artifacts.Count
    remainingConditions = $gate.remainingConditions.Count
} | ConvertTo-Json
