<#include "recipe-reference.ftl">
<#assign prefix = data.recipeType?lower_case>
{
    "type": "minecraft:${prefix}",
    "ingredient": "${recipeItemId(data[prefix + 'InputStack'])}",
    "result": { "id": "${recipeItemId(data[prefix + 'ReturnStack'])}", "count": ${data.recipeRetstackSize} },
    "experience": ${data.xpReward?c},
    "cookingtime": ${data.cookingTime}
}
