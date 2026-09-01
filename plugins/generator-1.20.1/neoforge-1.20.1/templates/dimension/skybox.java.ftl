<#-- @formatter:off -->
package ${package}.client;

<#-- RenderLevelStageEvent in NeoForge 1.20.1 exposes the legacy camera/pose API.
     Keep this hook intentionally empty until a version-specific renderer is available. -->
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ${JavaModName}SkyboxRenderer {
	@SubscribeEvent public static void renderSky(RenderLevelStageEvent event) {
		// Custom skybox rendering is unavailable on the 1.20.1 event contract.
	}
}
<#-- @formatter:on -->
