package dev.copperbench.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.workspace.project.GradleProjectSettings;
import dev.copperbench.core.workspace.project.GradleProjectWorkspaceSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JcefGradleProjectBridgeTransportTest {

	@TempDir Path temp;
	private Path root;

	@BeforeEach void project() throws Exception {
		root = Files.createDirectory(temp.resolve("emberkit"));
		write("gradlew", "#!/bin/sh\n");
		write("gradlew.bat", "@echo off\r\n");
		write("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.7.0-bin.zip\n");
		write("gradle/wrapper/gradle-wrapper.jar", "jar");
		write("settings.gradle", "rootProject.name = 'emberkit'\n");
		write("build.gradle", "dependencies {\n    minecraft \"com.mojang:minecraft:${project.minecraft_version}\"\n}\n");
		write("gradle.properties", "minecraft_version=1.21.1\nloader_version=0.19.3\n");
		write("src/main/resources/fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"emberkit\",\"name\":\"Ember Kit\"}\n");
	}

	private void write(String relative, String text) throws Exception {
		Path target = root.resolve(relative);
		Files.createDirectories(target.getParent());
		Files.writeString(target, text, StandardCharsets.UTF_8);
	}

	private GradleProjectWorkspaceSession open() throws Exception {
		return GradleProjectWorkspaceSession.open(root, (store, folder) -> new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID),
				Clock.systemUTC(), UUID::randomUUID);
	}

	private static JsonObject request(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	@Test void statusReportsTheChoicesWithoutChangingAnything() throws Exception {
		try (var session = open()) {
			JsonObject status = JcefGradleProjectBridgeTransport.handle(session, request("{\"operation\":\"status\"}"));
			assertEquals(session.root().toString(), status.get("projectRoot").getAsString());
			JsonObject gitIgnore = status.getAsJsonObject("gitIgnore");
			assertFalse(gitIgnore.get("ignored").getAsBoolean());
			assertTrue(gitIgnore.get("decision").isJsonNull());
			JsonObject gradle = status.getAsJsonObject("gradleUserHome");
			assertEquals("product", gradle.get("mode").getAsString());
			assertEquals(net.mcreator.io.UserFolderManager.getGradleHome().toPath().toAbsolutePath().normalize().toString(),
					gradle.get("product").getAsString());
			assertEquals(GradleProjectSettings.environmentGradleUserHome().toString(), gradle.get("environment").getAsString());
			assertFalse(Files.exists(root.resolve(".gitignore")));
		}
	}

	@Test void eachChoiceReturnsTheStateItLeft() throws Exception {
		try (var session = open()) {
			JsonObject added = JcefGradleProjectBridgeTransport.handle(session, request("{\"operation\":\"git_ignore\",\"add\":true}"));
			assertEquals("added", added.getAsJsonObject("gitIgnore").get("decision").getAsString());
			assertTrue(added.getAsJsonObject("gitIgnore").get("ignored").getAsBoolean());
			JsonObject environment = JcefGradleProjectBridgeTransport.handle(session,
					request("{\"operation\":\"gradle_user_home\",\"mode\":\"environment\"}"));
			assertEquals("environment", environment.getAsJsonObject("gradleUserHome").get("mode").getAsString());
			assertTrue(session.settings().environmentGradleHome());
		}
	}

	@Test void malformedRequestsChangeNothing() throws Exception {
		try (var session = open()) {
			for (String invalid : new String[] {
					"{}", "{\"operation\":\"delete_project\"}", "{\"operation\":\"status\",\"extra\":1}",
					"{\"operation\":\"git_ignore\"}", "{\"operation\":\"git_ignore\",\"add\":\"true\"}",
					"{\"operation\":\"git_ignore\",\"add\":true,\"path\":\"../x\"}",
					"{\"operation\":\"gradle_user_home\",\"mode\":\"C:/elsewhere\"}", "{\"operation\":\"gradle_user_home\",\"mode\":true}",
					"{\"operation\":[\"status\"]}" })
				assertThrows(IllegalArgumentException.class, () -> JcefGradleProjectBridgeTransport.handle(session, request(invalid)), invalid);
			assertEquals(new GradleProjectSettings(null, false), session.settings());
			assertFalse(Files.exists(root.resolve(".gitignore")));
		}
	}

	@Test void bootstrapUsesTheProjectSettingsPrefix() {
		String script = JcefGradleProjectBridgeTransport.generateBootstrapScript();
		assertTrue(script.contains("__COPPERBENCH_GRADLE_PROJECT_HOST__"));
		assertTrue(script.contains("\"" + JcefGradleProjectBridgeTransport.QUERY_PREFIX + "\""));
	}
}
