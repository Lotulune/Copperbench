<#-- @formatter:off -->

/*
 * MCreator note: This file will be REGENERATED on each build.
 */

package ${package}.init;

public class ${JavaModName}Enchantments {

	<#list enchantments as enchantment>
	public static Enchantment ${enchantment.getModElement().getRegistryNameUpper()};
	</#list>

	public static void load() {
		<#list enchantments as enchantment>
		${enchantment.getModElement().getRegistryNameUpper()} = Registry.register(BuiltInRegistries.ENCHANTMENT,
				new ResourceLocation(${JavaModName}.MODID, "${enchantment.getModElement().getRegistryName()}"),
				new ${enchantment.getModElement().getName()}Enchantment());
		</#list>
	}
}
<#-- @formatter:on -->
