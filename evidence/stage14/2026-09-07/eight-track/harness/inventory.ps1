$ErrorActionPreference = 'Stop'
$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new()
$tracks = @(
 'plugins/generator-1.20.1/fabric-1.20.1',
 'plugins/generator-1.21.1/fabric-1.21.1',
 'plugins/generator-fabric-26.1.2/fabric-26.1.2',
 'plugins/generator-fabric-26.2/fabric-26.2',
 'plugins/generator-1.20.1/neoforge-1.20.1',
 'plugins/generator-1.21.1/neoforge-1.21.1',
 'plugins/generator-26.1.x/neoforge-26.1.2',
 'plugins/generator-26.2/neoforge-26.2'
)
foreach ($track in $tracks) {
 Write-Output ('==== ' + $track)
 Get-Content ($track + '/workspacebase/build.gradle') -TotalCount 70
 Select-String -Path ($track + '/generator.yaml') -Pattern '^buildfileversion:|sync_task' -Context 0,1
 Get-ChildItem ($track + '/templates') -Recurse -File -Filter '*item*.ftl' | Select-String -Pattern 'public .*use\(' -Context 0,4
 Get-Content ($track + '/templates/modbase/mod.java.ftl') -ErrorAction SilentlyContinue | Select-String -Pattern 'public .*Mod|public \$|IEventBus|ModLoadingContext|Start of user|onInitialize|@Mod' -Context 1,3
}
