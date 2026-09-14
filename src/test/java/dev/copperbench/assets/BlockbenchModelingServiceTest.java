package dev.copperbench.assets;

import com.google.gson.*;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchModelingServiceTest {
	@TempDir Path root;
	private JGitLocalHistoryService history;
	private final UUID workspaceId = UUID.randomUUID();
	private BlockbenchModelingService service;
	private static final String MODEL = "{\"meta\":{\"format_version\":\"5.0\",\"model_format\":\"java_block\"},"
			+ "\"elements\":[{\"uuid\":\"cube-1\",\"from\":[0,0,0],\"to\":[16,16,16]}],\"textures\":[]}";
	@BeforeEach void setup() throws Exception {
		history = JGitLocalHistoryService.open(root, Clock.systemUTC());
		service = new BlockbenchModelingService(root, workspaceId, history, Clock.systemUTC());
	}
	@AfterEach void close() { history.close(); }

	@Test void newModelIsDurableAndFinishesWithoutLaunchingOrImporting() throws Exception {
		UUID id = UUID.randomUUID();
		var task = service.begin(id, null, "models/example.bbmodel", 4, Actor.MCP);
		assertEquals(4, task.get("openedRevision").getAsLong());
		assertFalse(Files.exists(root.resolve("models/example.bbmodel")));
		assertEquals(1, history.listRecoveryPoints().size());
		assertEquals(task, service.begin(id, null, "models/example.bbmodel", 4, Actor.MCP));
		assertEquals(1, history.listRecoveryPoints().size());
		assertCode("MODEL_EMPTY", () -> service.finish(id, task.get("editSha256").getAsString(), Actor.MCP));
		Files.writeString(Path.of(task.get("editPath").getAsString()), MODEL);
		var reopened = new BlockbenchModelingService(root, workspaceId, history, Clock.systemUTC());
		var saved = reopened.get(id);
		assertTrue(saved.get("hasSavedChanges").getAsBoolean());
		var complete = reopened.finish(id, saved.get("editSha256").getAsString(), Actor.UI);
		assertEquals("ready_to_import", complete.get("state").getAsString());
		assertFalse(complete.get("imported").getAsBoolean());
		assertTrue(Files.isRegularFile(Path.of(complete.get("candidatePath").getAsString())));
		assertFalse(Files.exists(root.resolve("models/example.bbmodel")));
		assertEquals(complete, reopened.finish(id, saved.get("editSha256").getAsString(), Actor.MCP));
		assertEquals(2, complete.getAsJsonArray("activity").size());
		assertEquals(1, reopened.list().getAsJsonArray("tasks").size());
	}

	@Test void existingModelEmbedsRelativeTexturesAndPreservesOriginalBytes() throws Exception {
		Path model = root.resolve("models/lamp.bbmodel");
		Files.createDirectories(model.getParent());
		var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, 0xff00ff00);
		javax.imageio.ImageIO.write(image, "png", model.resolveSibling("lamp.png").toFile());
		String content = MODEL.replace("\"textures\":[]", "\"textures\":[{\"uuid\":\"texture-1\",\"relative_path\":\"lamp.png\"}]");
		Files.writeString(model, content);
		String assetId = new AssetWorkspaceService(root).findByRelativePath("models/lamp.bbmodel").orElseThrow().id();
		UUID id = UUID.randomUUID();
		var task = service.begin(id, assetId, null, 0, Actor.UI);
		String copy = Files.readString(Path.of(task.get("editPath").getAsString()));
		assertTrue(copy.contains("data:image/png;base64,"));
		assertFalse(copy.contains("relative_path"));
		assertEquals(content, Files.readString(model));
		var finished = service.finish(id, task.get("editSha256").getAsString(), Actor.UI);
		assertEquals("ready_to_import", finished.get("state").getAsString());
		assertEquals(content, Files.readString(model));
	}

	@Test void preservesTheCheckedInNativeBlockbenchSaveThroughCandidateCompletion() throws Exception {
		Path model = root.resolve("models/native_lantern.bbmodel");
		Files.createDirectories(model.getParent());
		try (var input = getClass().getResourceAsStream("/assets/blockbench/signal_lantern_saved_5_1_6.bbmodel")) {
			assertNotNull(input);
			Files.copy(input, model);
		}
		byte[] original = Files.readAllBytes(model);
		String assetId = new AssetWorkspaceService(root).findByRelativePath("models/native_lantern.bbmodel").orElseThrow().id();
		UUID id = UUID.randomUUID();
		var task = service.begin(id, assetId, null, 0, Actor.MCP);
		var finished = service.finish(id, task.get("editSha256").getAsString(), Actor.MCP);
		JsonObject candidate = JsonParser.parseString(Files.readString(Path.of(finished.get("candidatePath").getAsString()))).getAsJsonObject();
		assertEquals(13, candidate.getAsJsonArray("elements").size());
		var semantics = BbmodelDocument.parse(candidate);
		assertEquals(78, semantics.faceBindings().size());
		assertEquals(1, semantics.textures().size());
		assertTrue(semantics.issues().isEmpty());
		assertArrayEquals(original, Files.readAllBytes(model));
	}

	@Test void sourceConflictAndOccupiedNewTargetPreserveTheEditingCopy() throws Exception {
		var task = service.begin(UUID.randomUUID(), null, "models/new.bbmodel", 0, Actor.MCP);
		Path edit = Path.of(task.get("editPath").getAsString());
		Files.writeString(edit, MODEL);
		Files.createDirectories(root.resolve("models"));
		Files.writeString(root.resolve("models/new.bbmodel"), "user-owned-content");
		UUID id = UUID.fromString(task.get("taskId").getAsString());
		var current = service.get(id);
		assertTrue(current.get("sourceChanged").getAsBoolean());
		assertCode("MODEL_SOURCE_CONFLICT", () -> service.finish(id, current.get("editSha256").getAsString(), Actor.MCP));
		assertEquals(MODEL, Files.readString(edit));
		assertEquals("user-owned-content", Files.readString(root.resolve("models/new.bbmodel")));
	}

	@Test void staleHashBadJsonAndMissingTextureCanBeFixedWithoutLosingTask() throws Exception {
		UUID id = UUID.randomUUID();
		var task = service.begin(id, null, "models/edit.bbmodel", 0, Actor.MCP);
		Path edit = Path.of(task.get("editPath").getAsString());
		Files.writeString(edit, "not json");
		assertCode("MODEL_EDIT_CHANGED", () -> service.finish(id, task.get("editSha256").getAsString(), Actor.MCP));
		assertCode("MODEL_JSON_INVALID", () -> finishCurrent(id));
		Files.writeString(edit, MODEL.replace("\"textures\":[]", "\"textures\":[{\"path\":\"missing.png\"}]"));
		assertCode("MODEL_TEXTURE_UNAVAILABLE", () -> finishCurrent(id));
		Files.writeString(edit, MODEL.replace("\"to\":[16,16,16]", "\"to\":[16]"));
		assertCode("MODEL_GEOMETRY_INVALID", () -> finishCurrent(id));
		Files.writeString(edit, MODEL);
		assertEquals("ready_to_import", finishCurrent(id).get("state").getAsString());
	}

	@Test void cancelPreservesFilesAndReleasesTargetWhileTaskIdsRemainIdempotent() throws Exception {
		UUID id = UUID.randomUUID();
		var task = service.begin(id, null, "models/edit.bbmodel", 0, Actor.MCP);
		assertCode("MODEL_TARGET_BUSY", () -> service.begin(UUID.randomUUID(), null, "models/edit.bbmodel", 0, Actor.MCP));
		assertCode("MODEL_TASK_ID_REUSED", () -> service.begin(id, null, "models/other.bbmodel", 0, Actor.MCP));
		var cancelled = service.cancel(id, Actor.MCP);
		assertEquals(cancelled, service.cancel(id, Actor.MCP));
		assertTrue(Files.exists(Path.of(task.get("editPath").getAsString())));
		assertCode("MODEL_TASK_CANCELLED", () -> finishCurrent(id));
		assertEquals("editing", service.begin(UUID.randomUUID(), null, "models/edit.bbmodel", 0, Actor.UI).get("state").getAsString());
	}

	@Test void neverReadsExternalTexturesOrOverwritesAnInterruptedPreparation() throws Exception {
		UUID id = UUID.randomUUID();
		Path directory = root.resolve(".copperbench/modeling-tasks/" + id);
		Files.createDirectories(directory);
		Files.writeString(directory.resolve("precious.txt"), "preserve");
		assertCode("MODEL_TASK_INCOMPLETE", () -> service.begin(id, null, "models/edit.bbmodel", 0, Actor.MCP));
		assertEquals("preserve", Files.readString(directory.resolve("precious.txt")));
		UUID next = UUID.randomUUID();
		var task = service.begin(next, null, "models/another.bbmodel", 0, Actor.MCP);
		Files.writeString(Path.of(task.get("editPath").getAsString()), MODEL.replace("\"textures\":[]", "\"textures\":[\"https://example.com/private.png\"]"));
		assertCode("MODEL_TEXTURE_UNAVAILABLE", () -> finishCurrent(next));
		assertCode("MODEL_TARGET_INVALID", () -> service.begin(UUID.randomUUID(), null, "models/../../escape.bbmodel", 0, Actor.MCP));
	}

	@Test void rejectsCandidateChangesAndCrossWorkspaceTaskRecords() throws Exception {
		UUID id = UUID.randomUUID();
		var task = service.begin(id, null, "models/edit.bbmodel", 0, Actor.MCP);
		Files.writeString(Path.of(task.get("editPath").getAsString()), MODEL);
		var finished = finishCurrent(id);
		Files.writeString(Path.of(finished.get("candidatePath").getAsString()), "tampered");
		assertCode("MODEL_CANDIDATE_CHANGED", () -> finishCurrent(id));
		var other = new BlockbenchModelingService(root, UUID.randomUUID(), history, Clock.systemUTC());
		assertCode("MODEL_TASK_INVALID", () -> other.get(id));
	}

	private JsonObject finishCurrent(UUID id) { return service.finish(id, service.get(id).get("editSha256").getAsString(), Actor.MCP); }
	private static void assertCode(String code, org.junit.jupiter.api.function.Executable action) {
		assertEquals(code, assertThrows(BlockbenchBridgeException.class, action).code());
	}
}
