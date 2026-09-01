<#-- Shared JSON/item-name helpers required by the 1.20.1 NeoForge templates. -->
<#function handleExtension mappedBlock customelement>
    <#assign extension = mappedBlock?keep_after_last(".")?replace("body", "chestplate")?replace("legs", "leggings")>
    <#if extension == "wall">
        <#if customelement?ends_with("hanging_sign")>
            <#return customelement?remove_ending("hanging_sign") + "wall_hanging_sign">
        <#elseif customelement?ends_with("sign")>
            <#return customelement?remove_ending("sign") + "wall_sign">
        <#else>
            <#return "wall_" + customelement>
        </#if>
    <#else>
        <#return (extension?has_content)?then(customelement + "_" + extension, customelement)>
    </#if>
</#function>

<#function mappedMCItemToRegistryName mappedBlock acceptTags=false>
    <#if mappedBlock.getUnmappedValue().startsWith("CUSTOM:")>
        <#assign customelement = generator.getRegistryNameFromFullName(mappedBlock.getUnmappedValue())!""/>
        <#if customelement?has_content>
            <#assign extension = mappedBlock?keep_after_last(".")?replace("body", "chestplate")?replace("legs", "leggings")>
            <#return "${modid}:" + ((extension?has_content)?then(customelement + "_" + extension, customelement))>
        <#else>
            <#return "minecraft:air">
        </#if>
    <#elseif mappedBlock.getUnmappedValue().startsWith("TAG:")>
        <#if acceptTags>
            <#return "#" + mappedBlock.getUnmappedValue().replace("TAG:", "")?lower_case>
        <#else>
            <#return "minecraft:air">
        </#if>
    <#else>
        <#assign mapped = generator.map(mappedBlock.getUnmappedValue(), "blocksitems", 1) />
        <#if mapped.startsWith("#")>
            <#if acceptTags><#return mapped><#else><#return "minecraft:air"></#if>
        <#elseif mapped.contains(":")>
            <#return mapped>
        <#else>
            <#return "minecraft:" + mapped>
        </#if>
    </#if>
</#function>
