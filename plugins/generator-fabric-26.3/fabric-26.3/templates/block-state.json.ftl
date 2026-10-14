{
  "variants": {
    <#if data.rotationMode == 5>
    "axis=x": { "model": "${modid}:block/${registryname}", "x": 90, "y": 90 },
    "axis=y": { "model": "${modid}:block/${registryname}" },
    "axis=z": { "model": "${modid}:block/${registryname}", "x": 90 }
    <#else>
    "": { "model": "${modid}:block/${registryname}" }
    </#if>
  }
}
