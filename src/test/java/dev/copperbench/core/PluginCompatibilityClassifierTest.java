package dev.copperbench.core;

import com.google.gson.JsonObject;
import dev.copperbench.core.plugin.PluginCompatibilityClassifier;
import dev.copperbench.core.plugin.PluginCompatibilityClassifier.Level;
import dev.copperbench.core.plugin.PluginCompatibilityClassifier.Route;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginCompatibilityClassifierTest {

	private final PluginCompatibilityClassifier classifier = new PluginCompatibilityClassifier();
	@TempDir Path temporaryDirectory;

	@Test void representativePluginsUseTheirDeclaredCompatibilityRoutes() throws Exception {
		var resource = classifier.assess(resource("a-resource"), 202600100000L, 2026001L);
		var javaLogic = classifier.assess(resource("b-java"), 202600100000L, 2026001L);
		var swing = classifier.assess(resource("c-swing"), 202600100000L, 2026001L);

		assertEquals(Level.A, resource.level());
		assertEquals(Route.RESOURCE_PIPELINE, resource.route());
		assertFalse(resource.containsJavaCode());
		assertEquals(Level.B, javaLogic.level());
		assertEquals(Route.JAVA_COMPATIBILITY_API, javaLogic.route());
		assertEquals(Level.C, swing.level());
		assertEquals(Route.LEGACY_SWING_WINDOW, swing.route());
		assertTrue(swing.limitations().contains("LEGACY_UI_ONLY"));
		assertEquals(64, swing.sha256().length());
	}

	@Test void unsupportedVersionAndBlockedReflectionAreRejected() throws Exception {
		Path unsupported = temporaryDirectory.resolve("unsupported");
		Files.createDirectories(unsupported);
		JsonObject manifest = new JsonObject();
		manifest.addProperty("id", "unsupported");
		manifest.addProperty("javaplugin", "fixtures.Plugin");
		manifest.add("supportedversions", com.google.gson.JsonParser.parseString("[2025001]"));
		Files.writeString(unsupported.resolve("plugin.json"), manifest.toString(), StandardCharsets.UTF_8);
		assertEquals(Level.X, classifier.assess(unsupported, 202600100000L, 2026001L).level());

		Path blocked = temporaryDirectory.resolve("blocked");
		Files.createDirectories(blocked.resolve("fixtures"));
		manifest.remove("supportedversions");
		manifest.addProperty("id", "blocked");
		Files.writeString(blocked.resolve("plugin.json"), manifest.toString(), StandardCharsets.UTF_8);
		Files.writeString(blocked.resolve("fixtures/Plugin.java"),
				"class Plugin { net.mcreator.workspace.Workspace workspace; void run() throws Exception { "
						+ "workspace.getClass().getDeclaredField(\"secret\"); } }",
				StandardCharsets.UTF_8);
		var assessment = classifier.assess(blocked, 202600100000L, 2026001L);
		assertEquals(Level.X, assessment.level());
		assertTrue(assessment.limitations().contains("PLUGIN_USES_BLOCKED_INTERNAL_API"));
	}

	private Path resource(String name) throws Exception {
		return Path.of(getClass().getResource("/plugin-compatibility/" + name).toURI());
	}
}
