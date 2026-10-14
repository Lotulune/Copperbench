<#function recipeItemId reference>
    <#assign value = reference.getUnmappedValue()>
    <#if value?starts_with("CUSTOM:")>
        <#assign registry = generator.getRegistryNameFromFullName(value)!"">
        <#if !registry?has_content || registry == "deleted_mod_element"><#stop "Unresolved recipe workspace reference: " + value></#if>
        <#return modid + ":" + registry>
    <#else>
        <#return reference.getMappedValue(1)>
    </#if>
</#function>
