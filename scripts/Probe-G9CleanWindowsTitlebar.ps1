[CmdletBinding()]
param(
	[string]$VmName = 'Copperbench-G7',
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$GuestUser = 'g7admin',
	[string]$PasswordFile = 'D:\Hyper-V\G7\g7admin.password.txt',
	[string]$WorkspaceFile = 'C:\Users\g7admin\MCreatorWorkspaces\guigatedelta\guigatedelta.mcreator',
	[string]$InstallDir = 'C:\Copperbench-G9',
	[ValidateSet('none', 'chromium-test', 'uia-v2')]
	[string]$WmGetObjectMode = 'none',
	[string]$EvidenceOutputPath = ''
)

$ErrorActionPreference = 'Stop'
Import-Module Hyper-V -ErrorAction Stop

$plainPassword = (Get-Content -LiteralPath $PasswordFile -Raw).Trim()
$credential = [pscredential]::new($GuestUser, (ConvertTo-SecureString $plainPassword -AsPlainText -Force))
$guestRoot = 'C:\Temp\Copperbench-G9-TitlebarProbe'
$guestProbePath = Join-Path $guestRoot 'Probe-G9CleanWindowsTitlebar.exe'
$guestResultPath = Join-Path $guestRoot 'result.json'
$taskName = 'Copperbench-G9-TitlebarProbe'
$probeSource = Join-Path $PSScriptRoot 'Probe-G9CleanWindowsTitlebar.cs'
$compiler = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$frameworkRoot = Split-Path -Parent $compiler
$probeBuildRoot = Join-Path $RepositoryRoot '.tmp\g9-titlebar-probe'
$probePath = Join-Path $probeBuildRoot 'Probe-G9CleanWindowsTitlebar.exe'
if (-not (Test-Path -LiteralPath $compiler -PathType Leaf)) {
	throw "C# compiler not found: $compiler"
}
New-Item -ItemType Directory -Force -Path $probeBuildRoot | Out-Null
& $compiler /nologo /target:exe /platform:x64 /optimize+ "/out:$probePath" `
	"/reference:$(Join-Path $frameworkRoot 'WPF\UIAutomationClient.dll')" `
	"/reference:$(Join-Path $frameworkRoot 'WPF\UIAutomationTypes.dll')" `
	"/reference:$(Join-Path $frameworkRoot 'Accessibility.dll')" `
	"/reference:$(Join-Path $frameworkRoot 'System.Runtime.Serialization.dll')" `
	$probeSource
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $probePath -PathType Leaf)) {
	throw "Titlebar probe compilation failed with exit $LASTEXITCODE."
}

$session = New-PSSession -VMName $VmName -Credential $credential
try {
	Invoke-Command -Session $session -ArgumentList $guestRoot, $guestProbePath, $guestResultPath -ScriptBlock {
		param($Root, $ProbePath, $ResultPath)
		New-Item -ItemType Directory -Force -Path $Root | Out-Null
		Remove-Item -LiteralPath $ProbePath, $ResultPath -Force -ErrorAction SilentlyContinue
	}
	Copy-Item -LiteralPath $probePath -Destination $guestProbePath -ToSession $session -Force
	Invoke-Command -Session $session -ArgumentList $taskName, $guestProbePath, $WorkspaceFile, $InstallDir, $guestResultPath, $GuestUser, $WmGetObjectMode -ScriptBlock {
		param($TaskName, $ProbePath, $Workspace, $Install, $ResultPath, $TargetUser, $ActivationMode)
		Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
		$arguments = "--workspace `"$Workspace`" --install-dir `"$Install`" --result `"$ResultPath`" --wm-getobject-mode `"$ActivationMode`""
		$action = New-ScheduledTaskAction -Execute $ProbePath -Argument $arguments
		$principal = New-ScheduledTaskPrincipal -UserId $TargetUser -LogonType Interactive -RunLevel Highest
		Register-ScheduledTask -TaskName $TaskName -Action $action -Principal $principal -Force | Out-Null
		Start-ScheduledTask -TaskName $TaskName
	}

	$deadline = (Get-Date).AddMinutes(3)
	do {
		Start-Sleep -Milliseconds 500
		$result = Invoke-Command -Session $session -ArgumentList $guestResultPath -ScriptBlock {
			param($Path)
			if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
			try {
				$stream = [IO.FileStream]::new($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read,
					[IO.FileShare]::ReadWrite)
				try {
					$reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true, 4096, $true)
					try { $json = $reader.ReadToEnd() }
					finally { $reader.Dispose() }
				} finally {
					$stream.Dispose()
				}
				if ([string]::IsNullOrWhiteSpace($json)) { return $null }
				$json | ConvertFrom-Json -ErrorAction Stop
			} catch {
				$null
			}
		}
	} while (-not $result -and (Get-Date) -lt $deadline)
	if (-not $result) { throw 'Titlebar probe timed out before producing readable JSON.' }
	if (-not [string]::IsNullOrWhiteSpace($EvidenceOutputPath)) {
		$repositoryPrefix = [IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\') + '\'
		$requestedPath = if ([IO.Path]::IsPathRooted($EvidenceOutputPath)) {
			[IO.Path]::GetFullPath($EvidenceOutputPath)
		} else {
			[IO.Path]::GetFullPath((Join-Path $RepositoryRoot $EvidenceOutputPath))
		}
		if (-not $requestedPath.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
			throw "EvidenceOutputPath must stay inside the repository: $requestedPath"
		}
		$rawGuestJson = Invoke-Command -Session $session -ArgumentList $guestResultPath -ScriptBlock {
			param($Path)
			[IO.File]::ReadAllText($Path)
		}
		$parent = Split-Path -Parent $requestedPath
		if (-not [string]::IsNullOrWhiteSpace($parent)) {
			New-Item -ItemType Directory -Force -Path $parent | Out-Null
		}
		[IO.File]::WriteAllText($requestedPath, [string]$rawGuestJson, [Text.UTF8Encoding]::new($false))
	}
	$result | ConvertTo-Json -Depth 6
} finally {
	Invoke-Command -Session $session -ArgumentList $taskName -ScriptBlock {
		param($TaskName)
		Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
	} -ErrorAction SilentlyContinue
	Remove-PSSession $session -ErrorAction SilentlyContinue
}
