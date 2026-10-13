<#-- Copyright (C) 2026 Copperbench contributors. GPL-3.0-or-later. -->
package ${package}.block;

public final class ${name}Block extends net.minecraft.world.level.block.<#if data.rotationMode == 5>RotatedPillarBlock<#else>Block</#if> {
    public ${name}Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        super(properties.strength(${data.hardness?c}f, ${data.resistance?c}f));
    }
}
