[CmdletBinding()]
param([string]$VmName = 'Copperbench-Stage17-Linux', [Parameter(Mandatory)][AllowEmptyString()][string]$Text, [switch]$Enter,
    [ValidateRange(0, 1000)][int]$CharacterDelayMs = 0)
$ErrorActionPreference = 'Stop'
if ($VmName -notlike 'Copperbench-Stage17-*') { throw 'Only Stage17 test guests are allowed.' }
$vm = Get-CimInstance -Namespace root/virtualization/v2 -ClassName Msvm_ComputerSystem -Filter "ElementName='$VmName'"
$kb = Get-CimAssociatedInstance -InputObject $vm -ResultClassName Msvm_Keyboard
# TypeText uses the host keyboard layout. Use explicit US virtual keys for the Ubuntu guest.
$plain = "0123456789 -=[]\;',./"
$codes = @(48,49,50,51,52,53,54,55,56,57,32,189,187,219,221,220,186,222,188,190,191)
$shifted = ')!@#$%^&*( _+{}|:"<>?'
foreach ($ch in $Text.ToCharArray()) {
    $shift = $false
    if ($ch -cmatch '[a-zA-Z]') { $key = [int][char]([string]$ch).ToUpperInvariant(); $shift = $ch -cmatch '[A-Z]' }
    elseif ($ch -eq '~') { $key = 192; $shift = $true }
    elseif ($plain.IndexOf($ch) -ge 0) { $key = $codes[$plain.IndexOf($ch)] }
    elseif ($shifted.IndexOf($ch) -ge 0) { $key = $codes[$shifted.IndexOf($ch)]; $shift = $true }
    else { throw "Unsupported keyboard character: $ch" }
    if ($shift) { Invoke-CimMethod -InputObject $kb -MethodName PressKey -Arguments @{keyCode=[uint32]160} | Out-Null }
    Invoke-CimMethod -InputObject $kb -MethodName TypeKey -Arguments @{keyCode=[uint32]$key} | Out-Null
    if ($shift) { Invoke-CimMethod -InputObject $kb -MethodName ReleaseKey -Arguments @{keyCode=[uint32]160} | Out-Null }
    if ($CharacterDelayMs -gt 0) { Start-Sleep -Milliseconds $CharacterDelayMs }
}
if ($Enter) { Invoke-CimMethod -InputObject $kb -MethodName TypeKey -Arguments @{keyCode=[uint32]13} | Out-Null }
