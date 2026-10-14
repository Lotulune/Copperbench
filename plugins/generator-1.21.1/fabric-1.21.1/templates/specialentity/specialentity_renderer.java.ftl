<#--
 # This file is part of Fabric-Generator-MCreator.
 # Copyright (C) 2012-2020, Pylo
 # Copyright (C) 2020-2026, Pylo, opensource contributors
 # Copyright (C) 2020-2026, Goldorion, opensource contributors
 #
 # Fabric-Generator-MCreator is free software: you can redistribute it and/or modify
 # it under the terms of the GNU General Public License as published by
 # the Free Software Foundation, either version 3 of the License, or
 # (at your option) any later version.
 #
 # Fabric-Generator-MCreator is distributed in the hope that it will be useful,
 # but WITHOUT ANY WARRANTY; without even the implied warranty of
 # MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 # GNU General Public License for more details.
 #
 # You should have received a copy of the GNU General Public License
 # along with Fabric-Generator-MCreator. If not, see <https://www.gnu.org/licenses/>.
-->

<#-- @formatter:off -->
package ${package}.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.WaterPatchModel;
import net.minecraft.client.model.<#if data.isBoatChestVariant()>Chest</#if><#if data.isAnyRaft()>Raft<#else>Boat</#if>Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import org.joml.Quaternionf;
import ${package}.init.${JavaModName}Models;

public class ${name}Renderer extends EntityRenderer<Boat> {

	private final ListModel<Boat> model;
	private static final ResourceLocation TEXTURE = ResourceLocation.parse("${modid}:textures/entity/<#if data.isBoatChestVariant()>chest_boat<#else>boat</#if>/${registryname}.png");

	public ${name}Renderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.8f;
		model = new <#if data.isBoatChestVariant()>Chest</#if><#if data.isAnyRaft()>Raft<#else>Boat</#if>Model(context.bakeLayer(${JavaModName}Models.${REGISTRYNAME}_LAYER_LOCATION));
	}

	@Override public void render(Boat boat, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
		poseStack.pushPose();
		poseStack.translate(0, 0.375f, 0);
		poseStack.mulPose(Axis.YP.rotationDegrees(180 - yaw));
		float hurtTime = boat.getHurtTime() - partialTick;
		float damage = Math.max(boat.getDamage() - partialTick, 0);
		if (hurtTime > 0)
			poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(hurtTime) * hurtTime * damage / 10 * boat.getHurtDir()));
		float bubbleAngle = boat.getBubbleAngle(partialTick);
		if (!Mth.equal(bubbleAngle, 0))
			poseStack.mulPose(new Quaternionf().setAngleAxis(bubbleAngle * Mth.DEG_TO_RAD, 1, 0, 1));
		poseStack.scale(-1, -1, 1);
		poseStack.mulPose(Axis.YP.rotationDegrees(90));
		model.setupAnim(boat, partialTick, 0, -0.1f, 0, 0);
		model.renderToBuffer(poseStack, buffers.getBuffer(model.renderType(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
		if (!boat.isUnderWater() && model instanceof WaterPatchModel waterPatchModel)
			waterPatchModel.waterPatch().render(poseStack, buffers.getBuffer(RenderType.waterMask()), packedLight, OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
		super.render(boat, yaw, partialTick, poseStack, buffers, packedLight);
	}

	@Override public ResourceLocation getTextureLocation(Boat boat) {
		return TEXTURE;
	}
}
<#-- @formatter:on -->
