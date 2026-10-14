<#include "recipe-reference.ftl">
{
    <#if data.recipeShapeless>
    "type": "minecraft:crafting_shapeless",
    "ingredients": [<#list data.recipeSlots?filter(slot -> !slot.isEmpty()) as slot>"${recipeItemId(slot)}"<#sep>,</#list>],
    <#else>
    "type": "minecraft:crafting_shaped",
    <#assign rows = data.getOptimisedRecipe()>
    <#assign keys = data.getPatternKeys()>
    "pattern": [<#list rows as row>"<#list row as slot><#if slot.isEmpty()> <#else>${keys.get(slot)}</#if></#list>"<#sep>,</#list>],
    "key": {<#list keys.keySet() as slot>"${keys.get(slot)}": "${recipeItemId(slot)}"<#sep>,</#list>},
    </#if>
    "result": { "id": "${recipeItemId(data.recipeReturnStack)}", "count": ${data.recipeRetstackSize} }
}
