<#-- Copyright (C) 2026 Copperbench contributors. GPL-3.0-or-later. -->
package ${package}.item;

public final class ${name}Item extends net.minecraft.world.item.Item {
    public ${name}Item(net.minecraft.world.item.Item.Properties properties) {
        super(properties.stacksTo(${data.stackSize})
            .rarity(net.minecraft.world.item.Rarity.${data.rarity})
            <#if data.immuneToFire>.fireResistant()</#if>
            <#if data.fuelBurnTime gt 0>
            .component(net.minecraft.core.component.DataComponents.COOKING_FUEL,
                new net.minecraft.world.item.component.CookingFuel(
                    new net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt.Constant(${data.fuelBurnTime}),
                    new net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat.Constant(1.0f)))
            </#if>
            <#if data.compostingLayers gt 0>
            .component(net.minecraft.core.component.DataComponents.COMPOSTABLE,
                new net.minecraft.world.item.component.Compostable(
                    new net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt.Constant(${data.compostingLayers})))
            </#if>
        );
    }
}
