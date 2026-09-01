<#-- @formatter:off -->
package ${package}.client;

<#-- Fabric/Minecraft 1.20.1 predates the GPU render-pipeline contract used by the
     newer custom skybox implementation. Keep a stable client hook so generated
     mod initialization remains source-compatible without emitting newer APIs. -->
@Environment(EnvType.CLIENT)
public class ${JavaModName}SkyboxRenderer {
	public static void renderSky() {
		// Custom skybox rendering is unavailable on the Fabric 1.20.1 render contract.
	}
}
<#-- @formatter:on -->
