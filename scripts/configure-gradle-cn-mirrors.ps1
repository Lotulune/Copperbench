[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$gradleHome = Join-Path $env:USERPROFILE '.gradle'
$initDirectory = Join-Path $gradleHome 'init.d'
$initPath = Join-Path $initDirectory 'aliyun.gradle'
$propertiesPath = Join-Path $gradleHome 'gradle.properties'
$timestamp = Get-Date -Format 'yyyyMMddHHmmss'

New-Item -ItemType Directory -Force -Path $initDirectory | Out-Null

$initScript = @'
// Redirect official Maven Central / Plugin Portal / Google to Aliyun.
// Third-party repositories such as NeoForge and Minecraft stay unchanged.

def mirrors = [
        'https://repo.maven.apache.org/maven2'   : 'https://maven.aliyun.com/repository/central',
        'http://repo.maven.apache.org/maven2'    : 'https://maven.aliyun.com/repository/central',
        'https://repo1.maven.org/maven2'         : 'https://maven.aliyun.com/repository/central',
        'http://repo1.maven.org/maven2'          : 'https://maven.aliyun.com/repository/central',
        'https://plugins.gradle.org/m2'          : 'https://maven.aliyun.com/repository/gradle-plugin',
        'https://dl.google.com/dl/android/maven2': 'https://maven.aliyun.com/repository/google',
        'https://maven.google.com'               : 'https://maven.aliyun.com/repository/google',
        'https://jcenter.bintray.com'            : 'https://maven.aliyun.com/repository/public'
]

def normalize = { String url -> url == null ? '' : url.replaceAll(/\/+$/, '') }
def rewrite = { repo ->
    if (!(repo instanceof MavenArtifactRepository)) return
    def current = normalize(repo.url?.toString())
    if (!current) return
    mirrors.each { from, to ->
        def source = normalize(from)
        if (current == source || current.startsWith(source + '/')) repo.setUrl(to)
    }
}
def hook = { repositories ->
    repositories.withType(MavenArtifactRepository).configureEach { repo -> rewrite(repo) }
    repositories.whenObjectAdded { repo -> rewrite(repo) }
}

beforeSettings { settings ->
    settings.pluginManagement {
        repositories {
            maven { name = 'AliyunPublic'; url = 'https://maven.aliyun.com/repository/public' }
            maven { name = 'AliyunGradlePlugin'; url = 'https://maven.aliyun.com/repository/gradle-plugin' }
            maven { name = 'AliyunGoogle'; url = 'https://maven.aliyun.com/repository/google' }
        }
    }
}

settingsEvaluated { settings ->
    hook(settings.pluginManagement.repositories)
    hook(settings.dependencyResolutionManagement.repositories)
}

allprojects { project ->
    project.buildscript { hook(repositories) }
    hook(project.repositories)
}
'@

$changed = [ordered]@{ initScript = $false; gradleProperties = $false }
if (!(Test-Path -LiteralPath $initPath) -or (Get-Content -LiteralPath $initPath -Raw -Encoding UTF8) -ne $initScript) {
    if (Test-Path -LiteralPath $initPath) {
        Copy-Item -LiteralPath $initPath -Destination "$initPath.bak-$timestamp"
    }
    Set-Content -LiteralPath $initPath -Value $initScript -Encoding UTF8 -NoNewline
    $changed.initScript = $true
}

$propertyLines = if (Test-Path -LiteralPath $propertiesPath) {
    [System.Collections.Generic.List[string]](Get-Content -LiteralPath $propertiesPath -Encoding UTF8)
} else {
    [System.Collections.Generic.List[string]]::new()
}
$nonProxyKey = 'systemProp.http.nonProxyHosts'
$requiredHosts = @(
    'localhost', '127.0.0.1', '*.aliyun.com', 'maven.aliyun.com',
    '*.cloud.tencent.com', 'mirrors.cloud.tencent.com',
    '*.huaweicloud.com', 'mirrors.huaweicloud.com', 'repo.huaweicloud.com',
    '*.tsinghua.edu.cn', 'mirrors.tuna.tsinghua.edu.cn'
)
$propertyIndex = -1
for ($index = 0; $index -lt $propertyLines.Count; $index++) {
    if ($propertyLines[$index].StartsWith("$nonProxyKey=")) {
        $propertyIndex = $index
        break
    }
}
$existingHosts = if ($propertyIndex -ge 0) {
    $propertyLines[$propertyIndex].Substring($nonProxyKey.Length + 1).Split('|',
        [System.StringSplitOptions]::RemoveEmptyEntries)
} else {
    @()
}
$mergedHosts = [System.Collections.Generic.List[string]]::new()
foreach ($hostName in @($existingHosts) + $requiredHosts) {
    if (!$mergedHosts.Contains($hostName)) { $mergedHosts.Add($hostName) }
}
$newNonProxyLine = "$nonProxyKey=$($mergedHosts -join '|')"
if ($propertyIndex -lt 0 -or $propertyLines[$propertyIndex] -ne $newNonProxyLine) {
    if (Test-Path -LiteralPath $propertiesPath) {
        Copy-Item -LiteralPath $propertiesPath -Destination "$propertiesPath.bak-$timestamp"
    }
    if ($propertyIndex -ge 0) { $propertyLines[$propertyIndex] = $newNonProxyLine }
    else { $propertyLines.Add($newNonProxyLine) }
    Set-Content -LiteralPath $propertiesPath -Value $propertyLines -Encoding UTF8
    $changed.gradleProperties = $true
}

[pscustomobject]@{
    gradleHome = $gradleHome
    initScript = $initPath
    gradleProperties = $propertiesPath
    changed = $changed
} | ConvertTo-Json -Depth 3
