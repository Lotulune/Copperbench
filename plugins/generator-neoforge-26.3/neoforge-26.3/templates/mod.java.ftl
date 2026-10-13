<#-- Copyright (C) 2026 Copperbench contributors. GPL-3.0-or-later. -->
package ${package};

@net.neoforged.fml.common.Mod("${modid}")
public class ${JavaModName} {
    public static final String MODID = "${modid}";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MODID);
    private static final net.neoforged.neoforge.registries.DeferredRegister.Blocks BLOCKS =
        net.neoforged.neoforge.registries.DeferredRegister.createBlocks(MODID);
    private static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS =
        net.neoforged.neoforge.registries.DeferredRegister.createItems(MODID);

    public ${JavaModName}(net.neoforged.bus.api.IEventBus modBus) {
        <#list w.getGElementsOfType('block') as block>
        var ${block.getModElement().getName()}Block = BLOCKS.registerBlock("${block.getModElement().getRegistryName()}",
            ${package}.block.${block.getModElement().getName()}Block::new);
        ITEMS.registerSimpleBlockItem("${block.getModElement().getRegistryName()}", ${block.getModElement().getName()}Block,
            properties -> properties.stacksTo(${block.maxStackSize}).rarity(net.minecraft.world.item.Rarity.${block.rarity}));
        </#list>
        <#list w.getGElementsOfType('item') as item>
        ITEMS.registerItem("${item.getModElement().getRegistryName()}", ${package}.item.${item.getModElement().getName()}Item::new);
        </#list>
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        LOGGER.info("COPPERBENCH_P263_NEOFORGE_INITIALIZED");
        // Start of user code block mod init
        // End of user code block mod init
    }
}
