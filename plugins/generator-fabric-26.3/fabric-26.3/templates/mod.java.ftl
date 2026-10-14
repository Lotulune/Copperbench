<#-- Copyright (C) 2026 Copperbench contributors. GPL-3.0-or-later. -->
package ${package};

public class ${JavaModName} implements net.fabricmc.api.ModInitializer {
    public static final String MODID = "${modid}";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MODID);

    @Override public void onInitialize() {
        <#list w.getGElementsOfType('block') as block>
        var ${block.getModElement().getName()}BlockKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(MODID, "${block.getModElement().getRegistryName()}"));
        var ${block.getModElement().getName()}Block = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.BLOCK,
            ${block.getModElement().getName()}BlockKey, new ${package}.block.${block.getModElement().getName()}Block(
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().setId(${block.getModElement().getName()}BlockKey)));
        var ${block.getModElement().getName()}ItemKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(MODID, "${block.getModElement().getRegistryName()}"));
        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM, ${block.getModElement().getName()}ItemKey,
            new net.minecraft.world.item.BlockItem(${block.getModElement().getName()}Block,
                new net.minecraft.world.item.Item.Properties().setId(${block.getModElement().getName()}ItemKey)
                    .useBlockDescriptionPrefix().stacksTo(${block.maxStackSize}).rarity(net.minecraft.world.item.Rarity.${block.rarity})));
        </#list>
        <#list w.getGElementsOfType('item') as item>
        var ${item.getModElement().getName()}Key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(MODID, "${item.getModElement().getRegistryName()}"));
        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM, ${item.getModElement().getName()}Key,
            new ${package}.item.${item.getModElement().getName()}Item(new net.minecraft.world.item.Item.Properties().setId(${item.getModElement().getName()}Key)));
        </#list>
        LOGGER.info("COPPERBENCH_P263_FABRIC_INITIALIZED");
        // Start of user code block mod init
        // End of user code block mod init
    }
}
