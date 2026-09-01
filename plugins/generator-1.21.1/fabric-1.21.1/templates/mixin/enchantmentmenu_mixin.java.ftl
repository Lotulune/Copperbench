<#--
 # This file is part of Fabric-Generator-MCreator.
 # Copyright (C) 2020-2026, Goldorion, opensource contributors
 #
 # Fabric-Generator-MCreator is free software: you can redistribute it and/or modify
 # it under the terms of the GNU General Public License as published by
 # the Free Software Foundation, either version 3 of the License, or
 # (at your option) any later version.
-->

<#-- @formatter:off -->
package ${package}.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {

	@Unique
	private static final HashMap<Block, Integer> VALUES = new HashMap<>() {{
		put(Blocks.BOOKSHELF, 1);
		<#list w.getGElementsOfType('block')?filter(e -> e.enchantPowerBonus gt 0) as block>
		put(${JavaModName}Blocks.${block.getModElement().getRegistryNameUpper()}, ${block.enchantPowerBonus?round});
		</#list>
	}};

	@WrapOperation(method = "slotsChanged(Lnet/minecraft/world/Container;)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/EnchantingTableBlock;isValidBookShelf(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Z"))
	private boolean copperbench$includeCustomEnchantPower(Level level, BlockPos tablePos, BlockPos offset, Operation<Boolean> original) {
		if (original.call(level, tablePos, offset))
			return true;
		Block block = level.getBlockState(tablePos.offset(offset)).getBlock();
		if (!VALUES.containsKey(block) || VALUES.get(block) <= 0)
			return false;
		return level.getBlockState(tablePos.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2)).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER);
	}

}
<#-- @formatter:on -->
