<#-- @formatter:off -->
package ${package}.enchantment;

public class ${name}Enchantment extends Enchantment {

	public ${name}Enchantment(EquipmentSlot... slots) {
		super(Enchantment.Rarity.COMMON, EnchantmentCategory.BREAKABLE, slots);
	}

	<#if data.maxLevel != 1>
	@Override public int getMaxLevel() {
		return ${data.maxLevel};
	}
	</#if>

	<#if data.damageModifier != 0>
	@Override public int getDamageProtection(int level, DamageSource source) {
		return level * ${data.damageModifier};
	}
	</#if>

	@Override public boolean canEnchant(ItemStack itemstack) {
		return true;
	}

	<#if data.isTreasureEnchantment>
	@Override public boolean isTreasureOnly() {
		return true;
	}
	</#if>

	<#if data.isCurse>
	@Override public boolean isCurse() {
		return true;
	}
	</#if>

	<#if !data.canGenerateInLootTables>
	@Override public boolean isDiscoverable() {
		return false;
	}
	</#if>

	<#if !data.canVillagerTrade>
	@Override public boolean isTradeable() {
		return false;
	}
	</#if>
}
<#-- @formatter:on -->
