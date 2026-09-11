$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$create = Get-Content -LiteralPath (Join-Path $root 'scripts\New-Stage15LinuxHyperVGuest.ps1') -Raw
$seed = Get-Content -LiteralPath (Join-Path $root 'scripts\New-Stage15LinuxAutoinstallSeed.ps1') -Raw
$ready = Get-Content -LiteralPath (Join-Path $root 'scripts\verify-stage15-linux-hyperv-ready.ps1') -Raw
$checklist = Get-Content -LiteralPath (Join-Path $root 'docs\testing\stage15-linux-hyperv-checklist.md') -Raw

function Assert-Match([string]$Text, [string]$Pattern, [string]$Message) {
	if ($Text -notmatch $Pattern) { throw $Message }
}
function Assert-NotMatch([string]$Text, [string]$Pattern, [string]$Message) {
	if ($Text -match $Pattern) { throw $Message }
}

Assert-Match $create 'Get-FileHash.+SHA256' 'VM creation must bind the official ISO by SHA-256.'
Assert-Match $create 'ubuntu-24\\\.04.+desktop-amd64' 'VM creation must require the Ubuntu 24.04 Desktop amd64 naming contract.'
Assert-Match $create 'Length -lt 4GB' 'VM creation must reject implausibly small files masquerading as Ubuntu Desktop media.'
Assert-Match $create 'New-VM.+Generation 2' 'Stage 15 Linux guest must use Hyper-V Generation 2.'
Assert-Match $create 'MicrosoftUEFICertificateAuthority' 'Ubuntu guest must retain Linux-compatible Secure Boot.'
Assert-Match $create 'AutomaticCheckpointsEnabled \$false' 'Automatic checkpoints must stay disabled for deterministic clean-guest evidence.'
Assert-Match $create 'cleanGnomeCertificationCompleted = \$false' 'VM creation must never claim clean GNOME certification.'
Assert-Match $create 'defaultIntegrationServicesPreserved = \$true' 'VM creation must record that default Hyper-V integration-service state is preserved.'
Assert-NotMatch $create 'EnableSecureBoot Off' 'Stage 15 guest creation must not disable Secure Boot.'
Assert-NotMatch $create 'Enable-VMIntegrationService' 'VM creation must not enable optional Hyper-V guest services merely for automation convenience.'
Assert-NotMatch $create 'apt(-get)?\s+install.*(java|gradle|git)' 'Host VM creation must not seed forbidden system tooling.'

Assert-Match $seed "NewFileSystemLabel 'CIDATA'" 'Autoinstall seed must use the NoCloud CIDATA volume label.'
Assert-Match $seed 'interactive-sections:\s*\r?\n\s*- identity' 'Guest identity must remain interactive instead of storing credentials.'
Assert-Match $seed 'user-data: \{\}' 'Desktop autoinstall must make identity optional without persisting credentials.'
Assert-Match $seed 'id: ubuntu-desktop' 'Autoinstall seed must select the standard Ubuntu Desktop source.'
Assert-Match $seed 'search_drivers: false' 'Autoinstall seed must not add optional driver packages.'
Assert-Match $seed 'install-server: false' 'Autoinstall seed must not install SSH merely for automation convenience.'
Assert-Match $seed 'for tool in java gradle git; do if command -v' 'Autoinstall must fail on unexpected system development tools.'
Assert-Match $seed 'forbiddenSystemToolsRemoved = \$false' 'Autoinstall must never delete forbidden tooling to manufacture a clean pass.'
Assert-Match $seed 'diskWriteConfirmationBypassed = \$false' 'Autoinstall seed must keep the disk-write confirmation boundary.'
Assert-NotMatch $seed 'password:' 'Autoinstall seed must not persist a guest password.'
Assert-NotMatch $seed 'authorized-keys:\s*\r?\n\s*-\s+ssh-' 'Autoinstall seed must not persist an SSH public key.'

Assert-Match $ready 'createsVirtualMachine = \$false' 'Readiness probe must be read-only with respect to VM creation.'
Assert-Match $ready 'officialIsoSourceStillRequiresMaintainerVerification = \$true' 'Readiness must not infer official ISO provenance from a filename.'
Assert-Match $ready 'isoSizeAccepted' 'Readiness must reject implausibly small ISO files.'
Assert-Match $ready 'vhdRootDriveAvailable' 'Readiness must verify the configured VHD root drive exists.'
Assert-Match $ready 'readyToCreateCleanVm' 'Readiness result must expose the create/no-create boundary.'
Assert-NotMatch $ready '(?m)^\s*(New-VM|New-VHD|Start-VM|Remove-VM)\b' 'Readiness probe must not mutate Hyper-V guests.'

Assert-Match $checklist 'Wayland' 'Checklist must cover GNOME Wayland.'
Assert-Match $checklist 'GNOME on Xorg' 'Checklist must cover GNOME on Xorg.'
Assert-Match $checklist 'java.*gradle.*git' 'Checklist must preserve the no-system-tooling precondition.'
Assert-Match $checklist 'prepared helper.*not.*evidence' 'Checklist must keep prepared helpers distinct from executed evidence.'
Assert-Match $checklist 'CIDATA' 'Checklist must document the optional safe autoinstall seed path.'

Write-Output 'Stage 15 Linux Hyper-V harness contract passed.'
