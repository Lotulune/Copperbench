[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$VmName = 'Copperbench-Stage17-Linux',
    [string]$EvidenceRoot = 'D:\Hyper-V\Stage17-Linux',
    [string]$OpenSsl = 'C:\Program Files\Git\usr\bin\openssl.exe'
)
$ErrorActionPreference = 'Stop'
# Explicitly authorized disposable test VM only. Never select or format a host disk.
$vm = Get-VM -Name $VmName -ErrorAction Stop
if ($VmName -notlike 'Copperbench-Stage17-*' -or $vm.State -ne 'Off') {
    throw 'Expected an off Stage17 disposable VM.'
}
$root = [IO.Path]::GetFullPath($EvidenceRoot)
$drives = @(Get-VMHardDiskDrive -VMName $VmName)
if ($drives.Count -ne 1 -or -not $drives[0].Path.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The VM must have exactly one disposable disk under EvidenceRoot.'
}
$seed = Join-Path $root 'stage17-cidata.vhdx'
$key = Join-Path $root 'stage17_ed25519'
if ((Test-Path -LiteralPath $seed) -or (Test-Path -LiteralPath $key)) { throw 'Refusing to overwrite seed or credentials.' }
if (-not (Test-Path -LiteralPath $OpenSsl)) { throw 'OpenSSL is needed to hash a generated guest password.' }
if (-not $PSCmdlet.ShouldProcess($VmName, 'Prepare unattended Ubuntu installation on its disposable virtual disk')) { return }

$password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$passwordFile = Join-Path $root 'guest-password.txt'
Set-Content -LiteralPath $passwordFile -Value $password -Encoding utf8NoBOM
$passwordHash = ($password | & $OpenSsl passwd -6 -stdin).Trim()
if ($LASTEXITCODE -ne 0 -or -not $passwordHash.StartsWith('$6$')) { throw 'Password hashing failed.' }
& ssh-keygen -t ed25519 -f $key -N '' -C 'stage17-local-test' -q
if ($LASTEXITCODE -ne 0) { throw 'SSH key generation failed.' }
$publicKey = (Get-Content -LiteralPath ($key + '.pub') -Raw).Trim()
foreach ($secret in @($key, $passwordFile)) {
    & icacls $secret /inheritance:r /grant:r '*S-1-5-32-544:F' '*S-1-5-18:F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Credential ACL failed.' }
}
$userData = @'
#cloud-config
autoinstall:
  version: 1
  locale: en_US.UTF-8
  keyboard: {layout: us}
  refresh-installer: {update: false}
  source: {id: ubuntu-desktop, search_drivers: false}
  drivers: {install: false}
  codecs: {install: false}
  oem: {install: false}
  identity:
    hostname: copperbench-stage17
    username: stage17
    password: '__PASSWORD_HASH__'
  ssh:
    install-server: true
    allow-pw: false
    authorized-keys:
      - '__SSH_KEY__'
  storage:
    layout:
      name: direct
      match: {size: largest}
  shutdown: poweroff
  late-commands:
    - |
      curtin in-target --target=/target -- sh -c 'mkdir -p /var/local/stage17; for tool in java gradle git; do if command -v "$tool"; then echo "Unexpected preinstalled tool: $tool" >&2; exit 42; else echo "$tool=absent"; fi; done > /var/local/stage17/preinstall-system-tooling.txt'
    - |
      printf 'stage17 ALL=(ALL) NOPASSWD:ALL\n' > /target/etc/sudoers.d/stage17-test
      chmod 440 /target/etc/sudoers.d/stage17-test
      sed -i '/^\[daemon\]/a AutomaticLoginEnable=true\nAutomaticLogin=stage17' /target/etc/gdm3/custom.conf
'@
$userData = $userData.Replace('__PASSWORD_HASH__', $passwordHash).Replace('__SSH_KEY__', $publicKey)
New-VHD -Path $seed -Dynamic -SizeBytes 64MB | Out-Null
$mounted = $false
try {
    $disk = Mount-VHD -Path $seed -PassThru | Get-Disk
    $mounted = $true
    if ($disk.Size -ne 64MB) { throw 'Unexpected seed disk size.' }
    $partition = Initialize-Disk -Number $disk.Number -PartitionStyle MBR -PassThru |
        New-Partition -UseMaximumSize -AssignDriveLetter
    Format-Volume -Partition $partition -FileSystem FAT -NewFileSystemLabel CIDATA -Confirm:$false | Out-Null
    $seedRoot = "$($partition.DriveLetter):\"
    Set-Content -LiteralPath (Join-Path $seedRoot 'user-data') -Value $userData -Encoding utf8NoBOM
    Set-Content -LiteralPath (Join-Path $seedRoot 'meta-data') -Value 'instance-id: copperbench-stage17-install' -Encoding utf8NoBOM
} finally {
    if ($mounted) { Dismount-VHD -Path $seed }
}
Add-VMHardDiskDrive -VMName $VmName -Path $seed
@{
    vmName = $VmName; seed = $seed; sshPublicKeyFingerprint = (& ssh-keygen -lf ($key + '.pub'))
    automatedIdentity = $true; sshKeyOnly = $true; guestAutoLogin = $true
    disposableGuestDiskInstallationAuthorized = $true; formalSupportClaim = $false
    cleanDesktopBaselineStillRequired = $true
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root 'stage17-seed-record.json') -Encoding utf8
Write-Output "seed=$seed"
