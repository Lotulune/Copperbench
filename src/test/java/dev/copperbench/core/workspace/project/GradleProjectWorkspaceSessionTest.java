package dev.copperbench.core.workspace.project;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandResult;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.QueryResult;
import dev.copperbench.core.workspace.WorkspaceWriteLockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleProjectWorkspaceSessionTest {

	private static final String SOURCE = "src/main/java/com/example/emberkit/EmberKit.java";

	@TempDir Path temp;
	private Path root;

	/** The layout an agent without Copperbench produced in the comparison runs. */
	@BeforeEach void project() throws Exception {
		root = Files.createDirectory(temp.resolve("ember-kit"));
		write("gradlew", "#!/bin/sh\n");
		write("gradlew.bat", "@echo off\r\n");
		write("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.7.0-bin.zip\n");
		write("gradle/wrapper/gradle-wrapper.jar", "jar");
		write("settings.gradle", "rootProject.name = 'emberkit'\n");
		write("build.gradle", "dependencies {\n    minecraft \"com.mojang:minecraft:${project.minecraft_version}\"\n}\n");
		write("gradle.properties", "minecraft_version=1.21.1\nloader_version=0.19.3\n");
		write("src/main/resources/fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"emberkit\",\"version\":\"${version}\",\"name\":\"Ember Kit\"}\n");
		write(SOURCE, "package com.example.emberkit;\n\npublic final class EmberKit {}\n");
		write("src/main/resources/assets/emberkit/models/item/ember.json", "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"emberkit:item/ember\"}}\n");
		write("src/main/resources/assets/emberkit/textures/item/ember.png", "not a real image");
		write("src/main/resources/assets/emberkit/textures/item/unused.png", "not a real image");
		write("build/libs/emberkit-0.1.0.jar", "built");
	}

	private void write(String relative, String text) throws Exception {
		Path target = root.resolve(relative);
		Files.createDirectories(target.getParent());
		Files.writeString(target, text, StandardCharsets.UTF_8);
	}

	private GradleProjectWorkspaceSession open() throws Exception {
		return GradleProjectWorkspaceSession.open(root, Clock.systemUTC(), UUID::randomUUID);
	}

	private static QueryResult query(GradleProjectWorkspaceSession session, Operation operation, JsonObject payload) {
		return session.uiEntry().query(Query.of(UUID.randomUUID(), session.workspaceId(), operation, payload));
	}

	private static CommandResult command(GradleProjectWorkspaceSession session, long revision, Operation operation, JsonObject payload) {
		return session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, operation, payload)).result();
	}

	private static JsonObject payload(String... pairs) {
		JsonObject payload = new JsonObject();
		for (int index = 0; index < pairs.length; index += 2) payload.addProperty(pairs[index], pairs[index + 1]);
		return payload;
	}

	private static JsonObject workspace(GradleProjectWorkspaceSession session) {
		QueryResult result = query(session, Operation.GET_WORKBENCH, new JsonObject());
		assertEquals("succeeded", result.status(), result.diagnostics().toString());
		return result.data().getAsJsonObject().getAsJsonObject("workspace");
	}

	private CommandResult edit(GradleProjectWorkspaceSession session, long revision, String path, String text) throws Exception {
		String sha = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(path))));
		return command(session, revision, Operation.UPDATE_WORKSPACE_FILE, payload("relativePath", path, "content", text,
				"expectedSha256", sha, "clientMutationId", UUID.randomUUID().toString()));
	}

	@Test void openingWritesOnlyCopperbenchMetadataAndDescribesTheProject() throws Exception {
		var before = projectFiles();
		UUID id;
		try (var session = open()) {
			id = session.workspaceId();
			JsonObject workspace = workspace(session);
			assertEquals("gradle_project", workspace.get("kind").getAsString());
			assertEquals("Ember Kit", workspace.get("name").getAsString());
			assertEquals("fabric-1.21.1", workspace.getAsJsonObject("generator").get("id").getAsString());
			assertEquals("ready", workspace.getAsJsonObject("generator").get("state").getAsString());
			assertEquals("emberkit", workspace.getAsJsonObject("gradleProject").get("modId").getAsString());
			assertTrue(workspace.getAsJsonObject("gradleProject").getAsJsonArray("problems").isEmpty());
		}
		assertEquals(before, projectFiles(), "opening and closing must not change the project's files");
		JsonObject metadata = JsonParser.parseString(Files.readString(root.resolve(GradleProjectWorkspaceSession.METADATA_FILE))).getAsJsonObject();
		assertEquals("gradle_project", metadata.get("kind").getAsString());
		assertEquals(id.toString(), metadata.getAsJsonObject("dev.copperbench").get("workspaceId").getAsString());
		try (var session = open()) {
			assertEquals(id, session.workspaceId(), "the identity survives reopening");
		}
	}

	@Test void everyProjectFileIsHandWrittenAndEditsAreRestorable() throws Exception {
		String original = Files.readString(root.resolve(SOURCE));
		String point;
		try (var session = open()) {
			JsonObject read = query(session, Operation.READ_WORKSPACE_FILE, payload("relativePath", SOURCE)).data().getAsJsonObject();
			assertEquals("manual", read.get("ownership").getAsString());
			assertTrue(read.get("editable").getAsBoolean());
			JsonObject build = query(session, Operation.READ_WORKSPACE_FILE, payload("relativePath", "build.gradle")).data().getAsJsonObject();
			assertTrue(build.get("editable").getAsBoolean(), "the user may edit the build script; Copperbench does not");

			CommandResult edited = edit(session, 0, SOURCE, original.replace("EmberKit {}", "EmberKit { int level; }"));
			assertEquals("committed", edited.status(), edited.diagnostics().toString());
			assertEquals(1, edited.newRevision());
			point = edited.recoveryPointId();
			assertNotNull(point);
		}
		try (var session = open()) {
			assertEquals(1, workspace(session).get("revision").getAsLong(), "the revision survives reopening");
			JsonObject restore = payload("recoveryPointId", point);
			assertEquals("USER_APPROVAL_REQUIRED", command(session, 1, Operation.RESTORE_RECOVERY_POINT, restore).diagnostics().getFirst().code());
			restore.addProperty("userApproved", true);
			CommandResult restored = command(session, 1, Operation.RESTORE_RECOVERY_POINT, restore);
			assertEquals("committed", restored.status(), restored.diagnostics().toString());
			assertEquals(original, Files.readString(root.resolve(SOURCE)));
			assertEquals(2, workspace(session).get("revision").getAsLong());
		}
		try (var session = open()) {
			assertEquals(2, workspace(session).get("revision").getAsLong());
		}
	}

	@Test void structuredOperationsAndTasksAreRefusedWithAReason() throws Exception {
		try (var session = open()) {
			JsonObject create = payload("elementType", "item", "name", "ember");
			assertEquals(GradleProjectOperations.UNAVAILABLE,
					command(session, 0, Operation.CREATE_MOD_ELEMENT, create).diagnostics().getFirst().code());
			assertEquals(GradleProjectOperations.UNAVAILABLE,
					query(session, Operation.GET_MOD_ELEMENT_FIELD_CONTRACT, payload("elementType", "item")).diagnostics().getFirst().code());
			assertEquals(GradleProjectOperations.UNAVAILABLE,
					command(session, 0, Operation.CREATE_REGISTRY_ENTRY, payload("registry", "tags")).diagnostics().getFirst().code());
			for (Operation task : List.of(Operation.BUILD_WORKSPACE, Operation.RUN_CLIENT, Operation.RUN_GAMETEST, Operation.PREPARE_GAME_TESTS))
				assertEquals(GradleProjectOperations.TASKS_UNAVAILABLE,
						command(session, 0, task, new JsonObject()).diagnostics().getFirst().code(), task.name());
			assertTrue(query(session, Operation.LIST_MOD_ELEMENTS, JsonParser.parseString("{\"page\":1,\"pageSize\":50}").getAsJsonObject())
					.data().getAsJsonObject().getAsJsonArray("items").isEmpty());

			JsonObject workbench = query(session, Operation.GET_WORKBENCH, new JsonObject()).data().getAsJsonObject();
			for (var capability : workbench.getAsJsonArray("capabilities")) {
				assertEquals("unavailable", capability.getAsJsonObject().get("availability").getAsString(), capability.toString());
				assertTrue(capability.getAsJsonObject().get("reasonCode").getAsString().startsWith("GRADLE_PROJECT_"), capability.toString());
			}
			JsonObject copy = session.service().versionCopyUpgrade(session.workspaceId(), UUID.randomUUID(),
					JsonParser.parseString("{\"supportedVersions\":[\"1\"],\"operation\":\"preview\",\"targetGeneratorId\":\"fabric-26.2\"}").getAsJsonObject(),
					new dev.copperbench.core.contract.UiCore.RequestContext(dev.copperbench.core.contract.UiCore.Actor.UI,
							dev.copperbench.core.contract.UiCore.PermissionProfile.WORKSPACE));
			assertEquals("rejected", copy.get("status").getAsString());
			assertTrue(copy.toString().contains(GradleProjectOperations.UNAVAILABLE));
			assertEquals(0, workspace(session).get("revision").getAsLong());
		}
	}

	@Test void assetsAreListedButNeverOfferedAsCleanupCandidates() throws Exception {
		try (var session = open()) {
			QueryResult assets = query(session, Operation.LIST_ASSETS, JsonParser.parseString("{\"page\":1,\"pageSize\":50}").getAsJsonObject());
			assertEquals("succeeded", assets.status(), assets.diagnostics().toString());
			assertTrue(assets.data().toString().contains("textures/item/unused.png"));
			assertFalse(assets.data().toString().contains("build/libs"));

			String unusedId = new dev.copperbench.assets.AssetWorkspaceService(root)
					.findByRelativePath("src/main/resources/assets/emberkit/textures/item/unused.png").orElseThrow().id();
			JsonObject request = JsonParser.parseString("{\"assetIds\":[\"" + unusedId + "\"]}").getAsJsonObject();
			QueryResult preview = query(session, Operation.PREVIEW_ASSET_DELETE, request);
			assertEquals("succeeded", preview.status(), preview.diagnostics().toString());
			assertTrue(preview.data().getAsJsonObject().get("requiresReferenceConfirmation").getAsBoolean(),
					"no element describes usage in arbitrary code, so nothing is a verified cleanup candidate");
		}
	}

	@Test void unsupportedProjectsStayReadableAndEditable() throws Exception {
		write("gradle.properties", "minecraft_version=1.21.4\n");
		write("settings.gradle", "include ':common'\n");
		try (var session = open()) {
			JsonObject workspace = workspace(session);
			assertEquals("incompatible", workspace.getAsJsonObject("generator").get("state").getAsString());
			assertEquals("gradle-project", workspace.getAsJsonObject("generator").get("id").getAsString());
			assertEquals(List.of(GradleProjectDetector.MULTI_MODULE, GradleProjectDetector.VERSION_UNSUPPORTED),
					workspace.getAsJsonObject("gradleProject").getAsJsonArray("problems").asList().stream()
							.map(problem -> problem.getAsJsonObject().get("code").getAsString()).toList());
			assertEquals("committed", edit(session, 0, SOURCE, "package com.example.emberkit;\n").status());
		}
	}

	@Test void editingTheBuildFilesRefreshesTheIdentity() throws Exception {
		try (var session = open()) {
			CommandResult edited = edit(session, 0, "gradle.properties", "minecraft_version=26.2\n");
			assertEquals("committed", edited.status(), edited.diagnostics().toString());
			assertEquals("fabric-26.2", workspace(session).getAsJsonObject("generator").get("id").getAsString());
		}
	}

	@Test void onlyOneSessionHoldsAProject() throws Exception {
		try (var ignored = open()) {
			assertThrows(WorkspaceWriteLockedException.class, this::open);
		}
		open().close();
	}

	@Test void metadataOfAnotherKindIsNotTakenOver() throws Exception {
		write(GradleProjectWorkspaceSession.METADATA_FILE, "{\"schemaVersion\":\"1.0\",\"kind\":\"something_else\"}");
		assertThrows(java.io.IOException.class, this::open);
		assertEquals("{\"schemaVersion\":\"1.0\",\"kind\":\"something_else\"}", Files.readString(root.resolve(GradleProjectWorkspaceSession.METADATA_FILE)));
		Files.delete(root.resolve(GradleProjectWorkspaceSession.METADATA_FILE));
		open().close();
	}

	@Test void aHyphenatedFabricModIdIsKept() throws Exception {
		write("src/main/resources/fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"ember-kit\",\"name\":\"Ember Kit\"}\n");
		try (var session = open()) {
			assertEquals("ember-kit", workspace(session).getAsJsonObject("gradleProject").get("modId").getAsString());
		}
	}

	private TreeMap<String, String> projectFiles() throws Exception {
		var result = new TreeMap<String, String>();
		try (var paths = Files.walk(root)) {
			for (Path path : paths.toList()) {
				String relative = root.relativize(path).toString().replace('\\', '/');
				if (relative.equals(".copperbench") || relative.startsWith(".copperbench/")) continue;
				result.put(relative, Files.isRegularFile(path) ? Files.getLastModifiedTime(path) + " " + Files.readString(path) : "directory");
			}
		}
		return result;
	}
}
