[CmdletBinding()]
param(
    [ValidatePattern('^Copperbench-Stage17-[A-Za-z0-9-]+$')]
    [string]$VmName = 'Copperbench-Stage17-Linux',
    [ValidatePattern('^[a-z_][a-z0-9_-]*$')]
    [string]$GuestUser = 'stage17',
    [string]$IdentityFile = 'D:/Hyper-V/Stage17-Linux/stage17_ed25519',
    [string]$KnownHostsFile = 'D:/Hyper-V/Stage17-Linux/known_hosts',
    [string]$HostKeyAlias = '172.20.86.21'
)

$ErrorActionPreference = 'Stop'
$identity = (Resolve-Path -LiteralPath $IdentityFile).Path
$knownHosts = (Resolve-Path -LiteralPath $KnownHostsFile).Path
$vm = Get-VM -Name $VmName
if ($vm.State -ne 'Running') { throw 'The test VM is not running. No VM state was changed.' }
$adapters = @(Get-VMNetworkAdapter -VMName $VmName | Where-Object SwitchName)
if ($adapters.Count -ne 1) { throw 'Expected one connected test NIC; select its network manually.' }
$adapter = $adapters[0]
$interfaces = @(Get-NetIPInterface -AddressFamily IPv6 -InterfaceAlias "vEthernet ($($adapter.SwitchName))")
if ($interfaces.Count -ne 1) { throw 'The host switch has no unique IPv6 interface.' }
$mac = $adapter.MacAddress
if ($mac -notmatch '^[0-9A-Fa-f]{12}$') { throw 'The VM did not report a valid Ethernet MAC address.' }
$bytes = @(for ($index = 0; $index -lt 12; $index += 2) { [Convert]::ToByte($mac.Substring($index, 2), 16) })
$eui64 = @($bytes[0] -bxor 2; $bytes[1]; $bytes[2]; 255; 254; $bytes[3]; $bytes[4]; $bytes[5])
$groups = @(for ($index = 0; $index -lt 8; $index += 2) { '{0:x2}{1:x2}' -f $eui64[$index], $eui64[$index + 1] })
$linkLocal = 'fe80::' + ($groups -join ':') + '%' + $interfaces[0].InterfaceIndex
# HostKeyAlias pins the already trusted VM key across DHCP changes. Never auto-accept a new key.
$sshArguments = @('-i', $identity, '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=8',
    '-o', 'ServerAliveInterval=5', '-o', 'ServerAliveCountMax=2', '-o', 'StrictHostKeyChecking=yes',
    '-o', "HostKeyAlias=$HostKeyAlias", '-o', "UserKnownHostsFile=$knownHosts")
$raw = & ssh -6 @sshArguments "$GuestUser@$linkLocal" 'ip -j -4 address show scope global'
if ($LASTEXITCODE -ne 0) {
    throw 'Pinned-key IPv6 SSH failed. This guest may not use EUI-64 link-local addressing; inspect its console. No settings were changed.'
}
$addresses = @($raw | ConvertFrom-Json | ForEach-Object {
    $_.addr_info | Where-Object { $_.family -eq 'inet' -and $_.scope -eq 'global' } | ForEach-Object local
})
if ($addresses.Count -ne 1) { throw 'Expected one guest global IPv4 address; inspect the returned NICs manually.' }
$parsed = $null
if (-not [Net.IPAddress]::TryParse($addresses[0], [ref]$parsed) -or
    $parsed.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) { throw 'Invalid guest IPv4 result.' }
$confirmation = & ssh -4 @sshArguments "$GuestUser@$($parsed.IPAddressToString)" 'printf connected'
if ($LASTEXITCODE -ne 0 -or $confirmation -ne 'connected') {
    throw "Guest IPv4 $parsed is not reachable with the pinned host key. IPv6 recovery address: $linkLocal. No settings were changed."
}
[ordered]@{
    vmName = $VmName
    verifiedAt = (Get-Date).ToUniversalTime().ToString('o')
    ipv4 = $parsed.IPAddressToString
    ipv6RecoveryAddress = $linkLocal
    interfaceIndex = $interfaces[0].InterfaceIndex
    hostKeyAlias = $HostKeyAlias
    strictHostKeyChecking = $true
    ipv4SshVerified = $true
    settingsChanged = $false
} | ConvertTo-Json
