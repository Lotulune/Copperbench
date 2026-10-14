package dev.copperbench.headless;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real build through the product launcher and its public Python SDK, never an in-memory gateway. */
@EnabledIfSystemProperty(named = "copperbench.stage3.fabricBuild", matches = "true")
class NativeBuildRecoveryTest {
	@Test void failedProbesDoNotPoisonTheProductBuild() throws Exception {
		McreatorTestRuntime.ensureInitialized();
		Path evidence = Files.createDirectories(Path.of("build/native-build-recovery", UUID.randomUUID().toString()).toAbsolutePath());
		Path root = Files.createDirectory(evidence.resolve("workspace"));
		Path document = root.resolve("xiucopper.mcreator");
		WorkspaceSettings settings = new WorkspaceSettings("xiucopper");
		settings.setModName("Xiucopper"); settings.setVersion("1.0.0"); settings.setCurrentGenerator("fabric-1.21.1");
		try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings)) {
			WorkspaceGeneratorSetup.setupWorkspaceBaseOrThrow(workspace);
			net.mcreator.gradle.GradleUtils.updateMCreatorBuildFile(workspace);
			assertTrue(workspace.getGenerator().generateBase());
		}
		JsonObject config = new JsonObject();
		JsonArray launcher = new JsonArray();
		launcher.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
		launcher.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
		launcher.add("--enable-native-access=ALL-UNNAMED,jcef");
		launcher.add("-cp");
		launcher.add(System.getProperty("copperbench.test.runtimeClasspath"));
		launcher.add("net.mcreator.Launcher");
		config.add("launcher", launcher);
		config.addProperty("workspace", document.toString()); config.addProperty("cwd", System.getProperty("user.dir"));
		Path configFile = evidence.resolve("config.json"); Files.writeString(configFile, config.toString());
		Path log = evidence.resolve("python.log");
		Process process = new ProcessBuilder("python", "sdk/python/native_build_recovery_smoke.py", configFile.toString())
				.redirectErrorStream(true).redirectOutput(log.toFile()).start();
		try {
			boolean completed = process.waitFor(30, TimeUnit.MINUTES);
			assertTrue(completed, "Product build timed out: " + log);
			assertEquals(0, process.exitValue(), () -> {
				try { return Files.readString(log); } catch (Exception ignored) { return log.toString(); }
			});
		} finally {
			if (process.isAlive()) { process.descendants().forEach(ProcessHandle::destroy); process.destroyForcibly(); }
			System.out.println("NATIVE_BUILD_RECOVERY_EVIDENCE=" + evidence);
		}
	}
}
