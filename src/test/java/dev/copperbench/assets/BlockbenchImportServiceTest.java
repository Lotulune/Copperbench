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

class BlockbenchImportServiceTest {
	@TempDir Path root;
	JGitLocalHistoryService history;
	BlockbenchModelingService tasks;
	BlockbenchImportService imports;
	UUID workspace = UUID.randomUUID(), taskId = UUID.randomUUID();
	Path edit;
	JsonArray outputs;
	static final String GAME = "{\"textures\":{\"all\":\"test:block/lamp\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"up\":{\"texture\":\"#all\"}}}]}";
	static final String MODEL_TARGET = "src/main/resources/assets/test/models/custom/lamp.json";
	@BeforeEach void setup() throws Exception {
		history = JGitLocalHistoryService.open(root, Clock.systemUTC());
		tasks = new BlockbenchModelingService(root, workspace, history, Clock.systemUTC());
		imports = new BlockbenchImportService(root, workspace, history, Clock.systemUTC());
		JsonObject task = tasks.begin(taskId, null, "models/lamp.bbmodel", 0, Actor.MCP);
		Path model = Path.of(task.get("editPath").getAsString()); edit = model.getParent();
		Files.writeString(model, "{\"meta\":{\"model_format\":\"java_block\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16]}],\"textures\":[]}");
		tasks.finish(taskId, tasks.get(taskId).get("editSha256").getAsString(), Actor.MCP);
		Files.writeString(edit.resolve("game.json"), GAME);
		javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", edit.resolve("lamp.png").toFile());
		outputs = new JsonArray();
		mapping("game.json", MODEL_TARGET); mapping("lamp.png", "src/main/resources/assets/test/textures/block/lamp.png");
	}
	@AfterEach void close() { history.close(); }
	void mapping(String source, String target) { JsonObject map = new JsonObject(); map.addProperty("sourceRelativePath", source); map.addProperty("targetRelativePath", target); outputs.add(map); }
	@Test void importsSourceGameModelAndPngTogetherAndReplaysAfterReopen() throws Exception {
		var plan = imports.preview(taskId, outputs);
		assertEquals(3, plan.batch().createCount());
		assertFalse(Files.exists(root.resolve(MODEL_TARGET)));
		var result = imports.apply(plan, Actor.MCP, 1);
		assertEquals("imported", result.get("state").getAsString());
		assertTrue(result.get("imported").getAsBoolean());
		assertEquals(GAME, Files.readString(root.resolve(MODEL_TARGET)));
		assertTrue(Files.exists(root.resolve("models/lamp.bbmodel")));
		assertEquals(2, history.listRecoveryPoints().size());
		var reopened = new BlockbenchImportService(root, workspace, history, Clock.systemUTC());
		assertEquals(result, reopened.replay(taskId, plan.token()));
		assertEquals(2, history.listRecoveryPoints().size());
		Files.writeString(root.resolve(MODEL_TARGET), "changed later");
		code("MODEL_IMPORTED_FILES_CHANGED", () -> reopened.replay(taskId, plan.token()));
	}
	@Test void staleExportOrTargetDoesNotWriteAnyFiles() throws Exception {
		var plan = imports.preview(taskId, outputs);
		Files.writeString(edit.resolve("game.json"), GAME.replace("16,16,16", "8,8,8"));
		code("MODEL_IMPORT_STALE", () -> imports.apply(plan, Actor.MCP, 1));
		assertFalse(Files.exists(root.resolve("models/lamp.bbmodel")));
		assertEquals("ready_to_import", tasks.get(taskId).get("state").getAsString());
	}
	@Test void revisionFailureRollsBackFilesAndDoesNotPublishSuccessfulReceipt() throws Exception {
		var plan = imports.preview(taskId, outputs);
		var durableRevision = new java.util.concurrent.atomic.AtomicLong();
		code("MODEL_IMPORT_FAILED", () -> imports.apply(plan, Actor.MCP, 1, new BlockbenchImportService.RevisionCommit() {
			public void commit() throws Exception { durableRevision.set(1); throw new java.io.IOException("metadata write failed"); }
			public void rollback() { durableRevision.set(0); }
		}));
		assertEquals(0, durableRevision.get());
		assertFalse(Files.exists(root.resolve(MODEL_TARGET)));
		assertFalse(Files.exists(root.resolve("models/lamp.bbmodel")));
		assertEquals("ready_to_import", tasks.get(taskId).get("state").getAsString());
	}
	@Test void failedRecoveryMetadataCanBeRetriedWithoutLosingTheJournal() throws Exception {
		var plan = imports.preview(taskId, outputs);
		assertThrows(SimulatedExit.class, () -> imports.apply(plan, Actor.MCP, 1, index -> { if (index == 2) throw new SimulatedExit(); }));
		code("MODEL_IMPORT_RECOVERY_REQUIRED", () -> imports.recover(taskId, Actor.MCP, new BlockbenchImportService.RevisionCommit() {
			public void commit() throws Exception { throw new java.io.IOException("metadata unavailable"); }
			public void rollback() {}
		}));
		assertEquals("importing", tasks.get(taskId).get("state").getAsString());
		assertEquals("ready_to_import", imports.recover(taskId, Actor.MCP).get("state").getAsString());
	}
	@Test void midwayFailureRestoresOnlyBatchTargetsAndPreservesCandidate() throws Exception {
		Files.createDirectories(root.resolve(MODEL_TARGET).getParent());
		Files.writeString(root.resolve(MODEL_TARGET), "old game model");
		Files.writeString(root.resolve("unrelated.txt"), "keep");
		var plan = imports.preview(taskId, outputs);
		code("MODEL_IMPORT_FAILED", () -> imports.apply(plan, Actor.MCP, 1, index -> { if (index == 2) throw new IllegalStateException("injected disk failure"); }));
		assertEquals("old game model", Files.readString(root.resolve(MODEL_TARGET)));
		assertEquals("keep", Files.readString(root.resolve("unrelated.txt")));
		assertFalse(Files.exists(root.resolve("models/lamp.bbmodel")));
		assertEquals("ready_to_import", tasks.get(taskId).get("state").getAsString());
		assertTrue(Files.exists(Path.of(tasks.get(taskId).get("candidatePath").getAsString())));
	}
	@Test void durableJournalRecoversAfterSimulatedProcessExit() throws Exception {
		var plan = imports.preview(taskId, outputs);
		assertThrows(SimulatedExit.class, () -> imports.apply(plan, Actor.MCP, 1, index -> { if (index == 2) throw new SimulatedExit(); }));
		assertEquals("importing", tasks.get(taskId).get("state").getAsString());
		assertTrue(Files.exists(root.resolve(MODEL_TARGET)));
		var reopened = new BlockbenchImportService(root, workspace, history, Clock.systemUTC());
		assertEquals("ready_to_import", reopened.recover(taskId, Actor.MCP).get("state").getAsString());
		assertFalse(Files.exists(root.resolve(MODEL_TARGET)));
		assertFalse(Files.exists(root.resolve("models/lamp.bbmodel")));
	}
	@Test void recoveryRefusesToEraseAnUnrelatedEdit() throws Exception {
		var plan = imports.preview(taskId, outputs);
		assertThrows(SimulatedExit.class, () -> imports.apply(plan, Actor.MCP, 1, index -> { if (index == 2) throw new SimulatedExit(); }));
		Files.writeString(root.resolve(MODEL_TARGET), "user changed this after the crash");
		code("MODEL_RECOVERY_CONFLICT", () -> imports.recover(taskId, Actor.MCP));
		assertTrue(Files.exists(root.resolve("models/lamp.bbmodel")));
		assertEquals("user changed this after the crash", Files.readString(root.resolve(MODEL_TARGET)));
	}
	@Test void rejectsMissingTexturesBadGameGeometryAndEscapingMappings() throws Exception {
		outputs.remove(1);
		code("MODEL_TEXTURE_MISSING", () -> imports.preview(taskId, outputs));
		mapping("lamp.png", "src/main/resources/assets/test/textures/block/lamp.png");
		Files.writeString(edit.resolve("game.json"), GAME.replace("#all", "#missing"));
		code("MODEL_TEXTURE_MISSING", () -> imports.preview(taskId, outputs));
		Files.writeString(edit.resolve("game.json"), GAME.replace("[16,16,16]", "[16]"));
		code("MODEL_GAME_GEOMETRY", () -> imports.preview(taskId, outputs));
		outputs.get(0).getAsJsonObject().addProperty("sourceRelativePath", "../candidate.bbmodel");
		code("MODEL_OUTPUT_PATH", () -> imports.preview(taskId, outputs));
	}
	@Test void rejectsAnExistingPngOutsideTheDefaultBlockAtlas() throws Exception {
		Files.writeString(edit.resolve("game.json"), GAME.replace("test:block/lamp", "test:native_texture"));
		outputs.get(1).getAsJsonObject().addProperty("targetRelativePath", "src/main/resources/assets/test/textures/native_texture.png");
		code("MODEL_TEXTURE_ATLAS_PATH", () -> imports.preview(taskId, outputs));
		assertFalse(Files.exists(root.resolve(MODEL_TARGET)));
		Path existing = root.resolve("src/main/resources/assets/test/textures/native_texture.png");
		Files.createDirectories(existing.getParent());
		Files.copy(edit.resolve("lamp.png"), existing);
		outputs.remove(1);
		code("MODEL_TEXTURE_ATLAS_PATH", () -> imports.preview(taskId, outputs));
	}
	@Test void checksDirectFaceReferencesAndAcceptsItemAtlasTextures() throws Exception {
		Files.writeString(edit.resolve("game.json"), GAME.replace("\"textures\":{\"all\":\"test:block/lamp\"},", "").replace("#all", "test:custom/lamp"));
		outputs.get(1).getAsJsonObject().addProperty("targetRelativePath", "src/main/resources/assets/test/textures/custom/lamp.png");
		code("MODEL_TEXTURE_ATLAS_PATH", () -> imports.preview(taskId, outputs));
		Files.writeString(edit.resolve("game.json"), GAME.replace("test:block/lamp", "test:item/lamp"));
		outputs.get(1).getAsJsonObject().addProperty("targetRelativePath", "src/main/resources/assets/test/textures/item/lamp.png");
		assertEquals(3, imports.preview(taskId, outputs).batch().items().size());
	}
	@Test void validatesBlockstateModelReferencesBeforeWriting() throws Exception {
		Files.writeString(edit.resolve("state.json"), "{\"variants\":{\"\":{\"model\":\"test:custom/missing\"}}}");
		mapping("state.json", "src/main/resources/assets/test/blockstates/lamp.json");
		code("MODEL_REFERENCE_MISSING", () -> imports.preview(taskId, outputs));
		Files.writeString(edit.resolve("state.json"), "{\"variants\":{\"\":{\"model\":\"test:custom/lamp\"}}}");
		assertEquals(4, imports.preview(taskId, outputs).batch().items().size());
	}
	@Test void resolvesInheritedTextureAliasesAndChildOverridesFromAnExistingParent() throws Exception {
		Path parent = root.resolve("src/main/resources/assets/test/models/custom/base.json");
		Files.createDirectories(parent.getParent());
		Files.writeString(parent, GAME.replace("\"all\":\"test:block/lamp\"", "\"all\":\"#surface\",\"surface\":\"test:block/not_exported\""));
		Files.writeString(edit.resolve("game.json"), "{\"parent\":\"test:custom/base\",\"textures\":{\"surface\":\"test:block/lamp\"}}");
		assertEquals(3, imports.preview(taskId, outputs).batch().items().size());
		Files.writeString(edit.resolve("game.json"), "{\"parent\":\"test:custom/base\"}");
		code("MODEL_TEXTURE_MISSING", () -> imports.preview(taskId, outputs));
	}
	@Test void detectsCyclesThroughAnExistingCustomParent() throws Exception {
		Path parent = root.resolve("src/main/resources/assets/test/models/custom/base.json");
		Files.createDirectories(parent.getParent());
		Files.writeString(parent, "{\"parent\":\"test:custom/lamp\"}");
		Files.writeString(edit.resolve("game.json"), "{\"parent\":\"test:custom/base\"}");
		code("MODEL_PARENT_CYCLE", () -> imports.preview(taskId, outputs));
	}
	@Test void rejectsAmbiguousResourceIdsAcrossAssetRoots() {
		mapping("game.json", "assets/test/models/custom/lamp.json");
		code("MODEL_OUTPUT_PATH", () -> imports.preview(taskId, outputs));
	}
	private static void code(String code, org.junit.jupiter.api.function.Executable action) { assertEquals(code, assertThrows(BlockbenchBridgeException.class, action).code()); }
	private static final class SimulatedExit extends Error {}
}
