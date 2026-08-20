package dev.copperbench.core.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstalledPluginInventoryServiceTest {

	@TempDir Path temporaryDirectory;

	@Test void listsFirstPartyAndClassifiesThirdPartyWithoutLoadingJava() throws Exception {
		Path root = temporaryDirectory.resolve("plugins");
		Files.createDirectories(root);
		Files.createDirectories(root.resolve("generator-1.21.1"));
		Files.writeString(root.resolve("generator-1.21.1/plugin.json"),
				"{\"id\":\"generator-1.21.1\",\"info\":{\"name\":\"Generator 1.21.1\",\"version\":\"1.0\"}}",
				StandardCharsets.UTF_8);
		Path third = root.resolve("third-swing");
		Files.createDirectories(third);
		Files.writeString(third.resolve("plugin.json"),
				"{\"id\":\"third-swing\",\"javaplugin\":\"fixtures.Swing\",\"info\":{\"name\":\"Third Swing\",\"version\":\"0.2\"}}",
				StandardCharsets.UTF_8);
		Files.writeString(third.resolve("Swing.java"),
				"package fixtures; import javax.swing.JPanel; public class Swing extends JPanel {}",
				StandardCharsets.UTF_8);

		JsonObject listed = new InstalledPluginInventoryService(List.of(root), 2026002L, 2026002L).list();
		assertFalse(listed.get("loadsJava").getAsBoolean());
		JsonArray plugins = listed.getAsJsonArray("plugins");
		assertEquals(2, plugins.size());
		JsonObject first = plugins.get(0).getAsJsonObject();
		assertEquals("generator-1.21.1", first.get("pluginId").getAsString());
		assertTrue(first.get("firstParty").getAsBoolean());
		assertEquals("A", first.get("level").getAsString());
		JsonObject thirdParty = plugins.get(1).getAsJsonObject();
		assertEquals("third-swing", thirdParty.get("pluginId").getAsString());
		assertFalse(thirdParty.get("firstParty").getAsBoolean());
		assertEquals("C", thirdParty.get("level").getAsString());
		assertEquals("LEGACY_SWING_WINDOW", thirdParty.get("route").getAsString());
		assertTrue(thirdParty.get("containsJavaCode").getAsBoolean());
	}
}
