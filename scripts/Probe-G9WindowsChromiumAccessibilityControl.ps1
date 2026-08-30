[CmdletBinding()]
param(
    [string]$VmName = 'Copperbench-G7',
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$GuestUser = 'g7admin',
    [string]$PasswordFile = 'D:\Hyper-V\G7\g7admin.password.txt',
    [string]$EvidenceOutputPath = ''
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

$compiler = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$frameworkRoot = Split-Path -Parent $compiler
$buildRoot = Join-Path $RepositoryRoot '.tmp\g9-windows-chromium-accessibility-control'
$probeSource = Join-Path $PSScriptRoot 'Probe-G9WindowsChromiumAccessibilityControl.cs'
$fixtureSource = Join-Path $PSScriptRoot 'fixtures\g9-windows-chromium-accessibility-control.html'
$probePath = Join-Path $buildRoot 'Probe-G9WindowsChromiumAccessibilityControl.exe'
New-Item -ItemType Directory -Force -Path $buildRoot | Out-Null

& $compiler /nologo /target:exe /platform:x64 /optimize+ "/out:$probePath" `
    "/reference:$(Join-Path $frameworkRoot 'WPF\UIAutomationClient.dll')" `
    "/reference:$(Join-Path $frameworkRoot 'WPF\UIAutomationTypes.dll')" `
    "/reference:$(Join-Path $frameworkRoot 'Accessibility.dll')" `
    "/reference:$(Join-Path $frameworkRoot 'System.Runtime.Serialization.dll')" `
    $probeSource
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $probePath -PathType Leaf)) {
    throw "Chromium accessibility control probe compilation failed with exit $LASTEXITCODE."
}

$plainPassword = (Get-Content -LiteralPath $PasswordFile -Raw).Trim()
$credential = [pscredential]::new($GuestUser, (ConvertTo-SecureString $plainPassword -AsPlainText -Force))
$guestRoot = 'C:\Temp\Copperbench-G9-ChromiumAccessibilityControl'
$guestProbe = Join-Path $guestRoot 'Probe-G9WindowsChromiumAccessibilityControl.exe'
$guestFixture = Join-Path $guestRoot 'control.html'
$guestResult = Join-Path $guestRoot 'result.json'
$taskName = 'Copperbench-G9-ChromiumAccessibilityControl'

$session = New-PSSession -VMName $VmName -Credential $credential
try {
    $edgePath = Invoke-Command -Session $session -ScriptBlock {
        $candidates = @(
            'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
            'C:\Program Files\Microsoft\Edge\Application\msedge.exe'
        )
        foreach ($candidate in $candidates) {
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        }
        throw 'Microsoft Edge was not found on the clean Windows guest.'
    }

    Invoke-Command -Session $session -ArgumentList $guestRoot, $guestProbe, $guestFixture, $guestResult -ScriptBlock {
        param($Root, $Probe, $Fixture, $Result)
        New-Item -ItemType Directory -Force -Path $Root | Out-Null
        Remove-Item -LiteralPath $Probe, $Fixture, $Result -Force -ErrorAction SilentlyContinue
    }
    Copy-Item -LiteralPath $probePath -Destination $guestProbe -ToSession $session -Force
    Copy-Item -LiteralPath $fixtureSource -Destination $guestFixture -ToSession $session -Force

    Invoke-Command -Session $session -ArgumentList $taskName, $guestProbe, $edgePath, $guestFixture, $guestResult, $GuestUser -ScriptBlock {
        param($TaskName, $Probe, $Edge, $Fixture, $Result, $TargetUser)
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
        $arguments = "--edge `"$Edge`" --html `"$Fixture`" --result `"$Result`""
        $action = New-ScheduledTaskAction -Execute $Probe -Argument $arguments
        $principal = New-ScheduledTaskPrincipal -UserId $TargetUser -LogonType Interactive -RunLevel Highest
        Register-ScheduledTask -TaskName $TaskName -Action $action -Principal $principal -Force | Out-Null
        Start-ScheduledTask -TaskName $TaskName
    }

    $deadline = [DateTime]::UtcNow.AddMinutes(2)
    $resultText = $null
    while ([DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 500
        $resultText = Invoke-Command -Session $session -ArgumentList $guestResult -ScriptBlock {
            param($Result)
            if (-not (Test-Path -LiteralPath $Result -PathType Leaf)) { return $null }
            try {
                $stream = [System.IO.File]::Open($Result, 'Open', 'Read', 'ReadWrite')
                try {
                    $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $true)
                    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
                } finally { $stream.Dispose() }
            } catch { return $null }
        }
        if (-not [string]::IsNullOrWhiteSpace($resultText)) { break }
    }
    if ([string]::IsNullOrWhiteSpace($resultText)) {
        throw 'Chromium accessibility control probe did not produce result JSON before timeout.'
    }

    $resultObject = $resultText | ConvertFrom-Json
    if (-not [string]::IsNullOrWhiteSpace($EvidenceOutputPath)) {
        $output = if ([System.IO.Path]::IsPathRooted($EvidenceOutputPath)) {
            [System.IO.Path]::GetFullPath($EvidenceOutputPath)
        } else {
            [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot $EvidenceOutputPath))
        }
        $root = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\') + '\'
        if (-not $output.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Evidence output must stay inside repository root: $output"
        }
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $output) | Out-Null
        [System.IO.File]::WriteAllText($output, $resultText, [System.Text.UTF8Encoding]::new($false))
    }
    $resultObject | ConvertTo-Json -Depth 8
    if (-not $resultObject.passed) { exit 1 }
} finally {
    try {
        Invoke-Command -Session $session -ArgumentList $taskName -ScriptBlock {
            param($TaskName)
            Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
            Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
        }
    } catch { }
    Remove-PSSession $session
}
