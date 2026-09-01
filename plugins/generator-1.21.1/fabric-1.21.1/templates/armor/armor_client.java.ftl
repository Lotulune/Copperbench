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
 -->

<#-- @formatter:off -->
<#assign helmetCustomModel = data.enableHelmet && data.helmetModelName != "Default" && data.getHelmetModel()?? && data.helmetModelPart?has_content>
<#assign bodyCustomModel = data.enableBody && data.bodyModelName != "Default" && data.getBodyModel()?? && data.bodyModelPart?has_content && data.armsModelPartL?has_content && data.armsModelPartR?has_content>
<#assign leggingsCustomModel = data.enableLeggings && data.leggingsModelName != "Default" && data.getLeggingsModel()?? && (data.leggingsModelPartL?has_content || data.leggingsModelPartR?has_content)>
<#assign bootsCustomModel = data.enableBoots && data.bootsModelName != "Default" && data.getBootsModel()?? && data.bootsModelPartL?has_content && data.bootsModelPartR?has_content>

package ${package}.client.renderer.item;

@Environment(EnvType.CLIENT) public class ${name}Armor {

	public static void clientLoad() {
		<#if data.enableHelmet && (helmetCustomModel || (data.helmetModelTexture?has_content && data.helmetModelTexture != "From armor") || data.helmetTranslucency)>
		ArmorRenderer.register(new ArmorRenderer() {
			private HumanoidModel<LivingEntity> armorModel;
			private final ResourceLocation texture = ResourceLocation.parse("<#if data.helmetModelTexture?has_content && data.helmetModelTexture != "From armor">${modid}:textures/entities/${data.helmetModelTexture}<#else>${modid}:textures/models/armor/${data.armorTextureFile}_layer_1.png</#if>");

			@Override public void render(PoseStack poseStack, MultiBufferSource bufferSource, ItemStack stack, LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel<LivingEntity> contextModel) {
				HumanoidModel<LivingEntity> model = contextModel;
				<#if helmetCustomModel>
				if (armorModel == null) {
					armorModel = new HumanoidModel<>(new ModelPart(Collections.emptyList(), Map.of(
						"head", new ${data.helmetModelName}(Minecraft.getInstance().getEntityModels().bakeLayer(${data.helmetModelName}.LAYER_LOCATION)).${data.helmetModelPart},
						"hat", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"body", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"right_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"left_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"right_leg", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"left_leg", new ModelPart(Collections.emptyList(), Collections.emptyMap())
					)));
				}
				contextModel.copyPropertiesTo(armorModel);
				model = armorModel;
				</#if>
				renderPart(poseStack, bufferSource, light, stack, model, texture, ${data.helmetTranslucency?c});
			}
		}, ${JavaModName}Items.${REGISTRYNAME}_HELMET);
		</#if>

		<#if data.enableBody && (bodyCustomModel || (data.bodyModelTexture?has_content && data.bodyModelTexture != "From armor") || data.bodyTranslucency)>
		ArmorRenderer.register(new ArmorRenderer() {
			private HumanoidModel<LivingEntity> armorModel;
			private final ResourceLocation texture = ResourceLocation.parse("<#if data.bodyModelTexture?has_content && data.bodyModelTexture != "From armor">${modid}:textures/entities/${data.bodyModelTexture}<#else>${modid}:textures/models/armor/${data.armorTextureFile}_layer_1.png</#if>");

			@Override public void render(PoseStack poseStack, MultiBufferSource bufferSource, ItemStack stack, LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel<LivingEntity> contextModel) {
				HumanoidModel<LivingEntity> model = contextModel;
				<#if bodyCustomModel>
				if (armorModel == null) {
					${data.bodyModelName} customModel = new ${data.bodyModelName}(Minecraft.getInstance().getEntityModels().bakeLayer(${data.bodyModelName}.LAYER_LOCATION));
					armorModel = new HumanoidModel<>(new ModelPart(Collections.emptyList(), Map.of(
						"body", customModel.${data.bodyModelPart},
						"left_arm", customModel.${data.armsModelPartL},
						"right_arm", customModel.${data.armsModelPartR},
						"head", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"hat", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"right_leg", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"left_leg", new ModelPart(Collections.emptyList(), Collections.emptyMap())
					)));
				}
				contextModel.copyPropertiesTo(armorModel);
				model = armorModel;
				</#if>
				renderPart(poseStack, bufferSource, light, stack, model, texture, ${data.bodyTranslucency?c});
			}
		}, ${JavaModName}Items.${REGISTRYNAME}_CHESTPLATE);
		</#if>

		<#if data.enableLeggings && (leggingsCustomModel || (data.leggingsModelTexture?has_content && data.leggingsModelTexture != "From armor") || data.leggingsTranslucency)>
		ArmorRenderer.register(new ArmorRenderer() {
			private HumanoidModel<LivingEntity> armorModel;
			private final ResourceLocation texture = ResourceLocation.parse("<#if data.leggingsModelTexture?has_content && data.leggingsModelTexture != "From armor">${modid}:textures/entities/${data.leggingsModelTexture}<#else>${modid}:textures/models/armor/${data.armorTextureFile}_layer_2.png</#if>");

			@Override public void render(PoseStack poseStack, MultiBufferSource bufferSource, ItemStack stack, LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel<LivingEntity> contextModel) {
				HumanoidModel<LivingEntity> model = contextModel;
				<#if leggingsCustomModel>
				if (armorModel == null) {
					${data.leggingsModelName} customModel = new ${data.leggingsModelName}(Minecraft.getInstance().getEntityModels().bakeLayer(${data.leggingsModelName}.LAYER_LOCATION));
					armorModel = new HumanoidModel<>(new ModelPart(Collections.emptyList(), Map.of(
						"left_leg", <#if data.leggingsModelPartL?has_content>customModel.${data.leggingsModelPartL}<#else>new ModelPart(Collections.emptyList(), Collections.emptyMap())</#if>,
						"right_leg", <#if data.leggingsModelPartR?has_content>customModel.${data.leggingsModelPartR}<#else>new ModelPart(Collections.emptyList(), Collections.emptyMap())</#if>,
						"head", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"hat", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"body", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"right_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"left_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap())
					)));
				}
				contextModel.copyPropertiesTo(armorModel);
				model = armorModel;
				</#if>
				renderPart(poseStack, bufferSource, light, stack, model, texture, ${data.leggingsTranslucency?c});
			}
		}, ${JavaModName}Items.${REGISTRYNAME}_LEGGINGS);
		</#if>

		<#if data.enableBoots && (bootsCustomModel || (data.bootsModelTexture?has_content && data.bootsModelTexture != "From armor") || data.bootsTranslucency)>
		ArmorRenderer.register(new ArmorRenderer() {
			private HumanoidModel<LivingEntity> armorModel;
			private final ResourceLocation texture = ResourceLocation.parse("<#if data.bootsModelTexture?has_content && data.bootsModelTexture != "From armor">${modid}:textures/entities/${data.bootsModelTexture}<#else>${modid}:textures/models/armor/${data.armorTextureFile}_layer_1.png</#if>");

			@Override public void render(PoseStack poseStack, MultiBufferSource bufferSource, ItemStack stack, LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel<LivingEntity> contextModel) {
				HumanoidModel<LivingEntity> model = contextModel;
				<#if bootsCustomModel>
				if (armorModel == null) {
					${data.bootsModelName} customModel = new ${data.bootsModelName}(Minecraft.getInstance().getEntityModels().bakeLayer(${data.bootsModelName}.LAYER_LOCATION));
					armorModel = new HumanoidModel<>(new ModelPart(Collections.emptyList(), Map.of(
						"left_leg", customModel.${data.bootsModelPartL},
						"right_leg", customModel.${data.bootsModelPartR},
						"head", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"hat", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"body", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"right_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap()),
						"left_arm", new ModelPart(Collections.emptyList(), Collections.emptyMap())
					)));
				}
				contextModel.copyPropertiesTo(armorModel);
				model = armorModel;
				</#if>
				renderPart(poseStack, bufferSource, light, stack, model, texture, ${data.bootsTranslucency?c});
			}
		}, ${JavaModName}Items.${REGISTRYNAME}_BOOTS);
		</#if>
	}

	private static void renderPart(PoseStack poseStack, MultiBufferSource bufferSource, int light, ItemStack stack, HumanoidModel<LivingEntity> model, ResourceLocation texture, boolean translucent) {
		if (translucent) {
			VertexConsumer buffer = ItemRenderer.getArmorFoilBuffer(bufferSource, RenderType.entityTranslucent(texture), stack.hasFoil());
			model.renderToBuffer(poseStack, buffer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		} else {
			ArmorRenderer.renderPart(poseStack, bufferSource, light, stack, model, texture);
		}
	}
}
<#-- @formatter:on -->
