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

package ${package}.client;

@Environment(EnvType.CLIENT)
public class ${JavaModName}SkyboxRenderer {

	<#list dimensions as dimension>
		<#if dimension.enableCustomSkyboxTextures || dimension.enableCustomSunMoonTextures>
			private static final ResourceKey ${dimension.getModElement().getRegistryNameUpper()}
				= ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("${modid}:${dimension.getModElement().getRegistryName()}"));
		</#if>
		<#if dimension.enableCustomSkyboxTextures>
			private static final ResourceLocation ${dimension.getModElement().getRegistryNameUpper()}_SKYBOX
				= ResourceLocation.parse("${modid}:textures/skybox/${dimension.getModElement().getRegistryName()}.png");
		</#if>
		<#if dimension.enableCustomSunMoonTextures>
			private static final ResourceLocation ${dimension.getModElement().getRegistryNameUpper()}_SUN
				= ResourceLocation.parse("${modid}:textures/${dimension.sunTexture}.png");
			private static final ResourceLocation ${dimension.getModElement().getRegistryNameUpper()}_MOON
				= ResourceLocation.parse("${modid}:textures/${dimension.moonTexture}.png");
		</#if>
	</#list>

	public static void renderSky() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			Minecraft mc = Minecraft.getInstance();
			PoseStack poseStack = context.matrixStack();
			if (mc.player == null || poseStack == null) return;
			<#list dimensions as dimension>
				<#if dimension.enableCustomSkyboxTextures || dimension.enableCustomSunMoonTextures>
					if (mc.player.level().dimension() == ${dimension.getModElement().getRegistryNameUpper()}) {
						<#if dimension.enableCustomSkyboxTextures>
							renderCustomSkybox(poseStack, ${dimension.getModElement().getRegistryNameUpper()}_SKYBOX);
						</#if>
						<#if dimension.enableCustomSunMoonTextures>
							renderCustomSun(context, poseStack, mc.player.level(), ${dimension.getModElement().getRegistryNameUpper()}_SUN);
							renderCustomMoon(context, poseStack, mc.player.level(), ${dimension.getModElement().getRegistryNameUpper()}_MOON);
						</#if>
					}
				</#if>
			</#list>
		});
	}

	public static void renderCustomSun(WorldRenderContext context, PoseStack poseStack, Level level, ResourceLocation texture) {
		poseStack.pushPose();
		GlStateManager._enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		GlStateManager._depthMask(false);
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float rainBrightness = 1.0F - level.getRainLevel(partialTick);
		RenderSystem.setShaderColor(1, 1, 1, rainBrightness);
		poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partialTick) * 360.0F));
		Matrix4f matrix = poseStack.last().pose();
		float size = 30.0F;
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, texture);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		buffer.addVertex(matrix, -size, 100.0F, -size).setUv(0.0F, 0.0F);
		buffer.addVertex(matrix, size, 100.0F, -size).setUv(1.0F, 0.0F);
		buffer.addVertex(matrix, size, 100.0F, size).setUv(1.0F, 1.0F);
		buffer.addVertex(matrix, -size, 100.0F, size).setUv(0.0F, 1.0F);
		BufferUploader.drawWithShader(buffer.buildOrThrow());
		RenderSystem.setShaderColor(1, 1, 1, 1);
		GlStateManager._disableBlend();
		RenderSystem.defaultBlendFunc();
		GlStateManager._depthMask(true);
		poseStack.popPose();
	}

	public static void renderCustomMoon(WorldRenderContext context, PoseStack poseStack, Level level, ResourceLocation texture) {
		poseStack.pushPose();
		GlStateManager._enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		GlStateManager._depthMask(false);
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float rainBrightness = 1.0F - level.getRainLevel(partialTick);
		RenderSystem.setShaderColor(1, 1, 1, rainBrightness);
		poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partialTick) * 360.0F));
		Matrix4f matrix = poseStack.last().pose();
		float size = 20.0F;
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, texture);
		int phase = level.getMoonPhase();
		int uIndex = phase % 4;
		int vIndex = phase / 4 % 2;
		float u0 = (float) uIndex / 4.0F;
		float v0 = (float) vIndex / 2.0F;
		float u1 = (float) (uIndex + 1) / 4.0F;
		float v1 = (float) (vIndex + 1) / 2.0F;
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		buffer.addVertex(matrix, -size, -100.0F, size).setUv(u1, v1);
		buffer.addVertex(matrix, size, -100.0F, size).setUv(u0, v1);
		buffer.addVertex(matrix, size, -100.0F, -size).setUv(u0, v0);
		buffer.addVertex(matrix, -size, -100.0F, -size).setUv(u1, v0);
		BufferUploader.drawWithShader(buffer.buildOrThrow());
		RenderSystem.setShaderColor(1, 1, 1, 1);
		GlStateManager._disableBlend();
		RenderSystem.defaultBlendFunc();
		GlStateManager._depthMask(true);
		poseStack.popPose();
	}

	public static void renderCustomSkybox(PoseStack poseStack, ResourceLocation texture) {
		poseStack.pushPose();
		GlStateManager._enableBlend();
		RenderSystem.defaultBlendFunc();
		GlStateManager._depthMask(false);

		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, texture);
		RenderSystem.setShaderColor(1, 1, 1, 1);
		Tesselator tesselator = Tesselator.getInstance();
		float distance = 100.0F;
		float size = 100.0F;
		renderSkyboxQuad(poseStack, tesselator, -size, distance, -size, 1.0F / 4.0F, 1.0F / 3.0F, size, distance, -size, 2.0F / 4.0F, 1.0F / 3.0F, size, distance, size, 2.0F / 4.0F, 0.0F, -size, distance, size, 1.0F / 4.0F, 0.0F);
		renderSkyboxQuad(poseStack, tesselator, -size, -distance, -size, 1.0F / 4.0F, 2.0F / 3.0F, -size, -distance, size, 1.0F / 4.0F, 3.0F / 3.0F, size, -distance, size, 2.0F / 4.0F, 3.0F / 3.0F, size, -distance, -size, 2.0F / 4.0F, 2.0F / 3.0F);
		renderSkyboxQuad(poseStack, tesselator, -distance, -size, size, 0.0F, 2.0F / 3.0F, -distance, -size, -size, 1.0F / 4.0F, 2.0F / 3.0F, -distance, size, -size, 1.0F / 4.0F, 1.0F / 3.0F, -distance, size, size, 0.0F, 1.0F / 3.0F);
		renderSkyboxQuad(poseStack, tesselator, -size, -size, -distance, 1.0F / 4.0F, 2.0F / 3.0F, size, -size, -distance, 2.0F / 4.0F, 2.0F / 3.0F, size, size, -distance, 2.0F / 4.0F, 1.0F / 3.0F, -size, size, -distance, 1.0F / 4.0F, 1.0F / 3.0F);
		renderSkyboxQuad(poseStack, tesselator, distance, -size, -size, 2.0F / 4.0F, 2.0F / 3.0F, distance, -size, size, 3.0F / 4.0F, 2.0F / 3.0F, distance, size, size, 3.0F / 4.0F, 1.0F / 3.0F, distance, size, -size, 2.0F / 4.0F, 1.0F / 3.0F);
		renderSkyboxQuad(poseStack, tesselator, size, -size, distance, 3.0F / 4.0F, 2.0F / 3.0F, -size, -size, distance, 4.0F / 4.0F, 2.0F / 3.0F, -size, size, distance, 4.0F / 4.0F, 1.0F / 3.0F, size, size, distance, 3.0F / 4.0F, 1.0F / 3.0F);
		RenderSystem.setShaderColor(1, 1, 1, 1);
		GlStateManager._depthMask(true);
		GlStateManager._disableBlend();
		poseStack.popPose();
	}

	private static void renderSkyboxQuad(PoseStack poseStack, Tesselator tesselator, float x1, float y1, float z1, float u1, float v1, float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3,
			float u3, float v3, float x4, float y4, float z4, float u4, float v4) {
		Matrix4f matrix = poseStack.last().pose();
		BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		buffer.addVertex(matrix, x1, y1, z1).setUv(u1, v1);
		buffer.addVertex(matrix, x2, y2, z2).setUv(u2, v2);
		buffer.addVertex(matrix, x3, y3, z3).setUv(u3, v3);
		buffer.addVertex(matrix, x4, y4, z4).setUv(u4, v4);
		BufferUploader.drawWithShader(buffer.buildOrThrow());
	}

}

<#-- @formatter:on -->