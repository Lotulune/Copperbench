<#--
 # This file is part of Fabric-Generator-MCreator.
 # Copyright (C) 2012-2020, Pylo
 # Copyright (C) 2020-2026, Pylo, opensource contributors
 # Copyright (C) 2020-2026, Goldorion, opensource contributors
 -->

<#-- @formatter:off -->

/*
 * MCreator note: This file will be REGENERATED on each build.
 */

package ${package}.init;

@Environment(EnvType.CLIENT) public class ${JavaModName}ArmorModels {
	public static void clientLoad() {
		<#list armors as armor>
		${armor.getModElement().getName()}Armor.clientLoad();
		</#list>
	}
}
<#-- @formatter:on -->
