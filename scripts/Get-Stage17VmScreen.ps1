[CmdletBinding()]
param([string]$VmName = 'Copperbench-Stage17-Linux', [string]$OutputPath = 'D:\Hyper-V\Stage17-Linux\screen.png',
    [ValidateRange(320, 3840)][int]$WidthPixels = 1024,
    [ValidateRange(200, 2160)][int]$HeightPixels = 768)
$ErrorActionPreference = 'Stop'
$vm = Get-CimInstance -Namespace root/virtualization/v2 -ClassName Msvm_ComputerSystem -Filter "ElementName='$VmName'"
$svc = Get-CimInstance -Namespace root/virtualization/v2 -ClassName Msvm_VirtualSystemManagementService
$shot = Invoke-CimMethod -InputObject $svc -MethodName GetVirtualSystemThumbnailImage -Arguments @{
    TargetSystem = $vm; WidthPixels = [uint32]$WidthPixels; HeightPixels = [uint32]$HeightPixels
}
if ($shot.ReturnValue -ne 0) { throw "Thumbnail failed: $($shot.ReturnValue)" }
$raw = $OutputPath + '.rgb565'
[IO.File]::WriteAllBytes($raw, $shot.ImageData)
python -c 'from PIL import Image; import struct,sys; d=open(sys.argv[1],"rb").read()[4:]; size=(int(sys.argv[3]),int(sys.argv[4])); assert len(d)==size[0]*size[1]*2,"Unexpected thumbnail dimensions"; im=Image.new("RGB",size); im.putdata([((v>>11)*255//31,((v>>5)&63)*255//63,(v&31)*255//31) for v, in struct.iter_unpack("<H",d)]); im.save(sys.argv[2])' $raw $OutputPath $WidthPixels $HeightPixels
if ($LASTEXITCODE -ne 0) { throw 'Thumbnail conversion failed.' }
Write-Output $OutputPath
