<#--
 # This file is part of Fabric-Generator-MCreator.
 # Copyright (C) 2020-2023, Goldorion, opensource contributors
 #
 # Fabric-Generator-MCreator is free software: you can redistribute it and/or modify
 # it under the terms of the GNU Lesser General Public License as published by
 # the Free Software Foundation, either version 3 of the License, or
 # (at your option) any later version.

 # Fabric-Generator-MCreator is distributed in the hope that it will be useful,
 # but WITHOUT ANY WARRANTY; without even the implied warranty of
 # MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 # GNU Lesser General Public License for more details.
 #
 # You should have received a copy of the GNU Lesser General Public License
 # along with Fabric-Generator-MCreator.  If not, see <https://www.gnu.org/licenses/>.
-->

<#-- @formatter:off -->

/*
 *	MCreator note: This file will be REGENERATED on each build.
 */

package ${package}.init;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ${JavaModName}EntityRenderers {

	public static void load() {
		<#list entities as entity>
			<#if entity.getModElement().getTypeString() == "projectile">
				<#if entity.isCustomModel()>
					EntityRendererRegistry.register(${JavaModName}Entities.${entity.getModElement().getRegistryNameUpper()}, ${entity.getModElement().getName()}Renderer::new);
				<#else>
					EntityRendererRegistry.register(${JavaModName}Entities.${entity.getModElement().getRegistryNameUpper()}, ThrownItemRenderer::new);
				</#if>
			<#elseif entity.getModElement().getTypeString() == "livingentity">
				EntityRendererRegistry.register(${JavaModName}Entities.${entity.getModElement().getRegistryNameUpper()}, ${entity.getModElement().getName()}Renderer::new);
				<#if entity.hasCustomProjectile()>
					EntityRendererRegistry.register(${JavaModName}Entities.${entity.getModElement().getRegistryNameUpper()}_PROJECTILE, ThrownItemRenderer::new);
				</#if>
			<#elseif entity.getModElement().getTypeString() == "specialentity">
				EntityRendererRegistry.register(${JavaModName}Entities.${entity.getModElement().getRegistryNameUpper()}, context -> new BoatRenderer(context, ${entity.isBoatChestVariant()?c}) {
					@Override public ResourceLocation getTextureLocation(Boat boat) {
						return new ResourceLocation("${modid}:textures/entity/<#if entity.isBoatChestVariant()>chest_boat<#else>boat</#if>/${entity.getModElement().getRegistryName()}.png");
					}
				});
			</#if>
		</#list>
	}
}
<#-- @formatter:on -->