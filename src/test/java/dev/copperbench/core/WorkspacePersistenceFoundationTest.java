package dev.copperbench.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.UnknownFieldPreservingJsonMerge;
import dev.copperbench.core.workspace.UnknownFieldPreservingJsonStore;
import dev.copperbench.core.workspace.ProductMetadataManager;
import dev.copperbench.core.workspace.WorkspaceFileLease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspacePersistenceFoundationTest {

	@TempDir Path temporaryDirectory;

	@Test void productMetadataRoundTripPreservesUnknownUpstreamAndPluginFields() throws Exception {
		Path workspaceFile = temporaryDirectory.resolve("sample.mcreator");
		String original = """
				{
				  "workspaceSettings": { "modName": "Copper Trails", "futureFlag": true },
				  "plugin.example": { "nested": [1, { "opaque": "keep-me" }] },
				  "unknownTopLevel": 73
				}
				""";
		Files.writeString(workspaceFile, original, StandardCharsets.UTF_8);
		UnknownFieldPreservingJsonStore store = new UnknownFieldPreservingJsonStore();

		store.updateProductMetadata(workspaceFile, metadata -> {
			metadata.addProperty("schemaVersion", 1);
			metadata.addProperty("revision", 42);
			return metadata;
		});

		JsonObject result = store.read(workspaceFile);
		assertTrue(result.getAsJsonObject("workspaceSettings").get("futureFlag").getAsBoolean());
		assertEquals("keep-me", result.getAsJsonObject("plugin.example").getAsJsonArray("nested").get(1)
				.getAsJsonObject().get("opaque").getAsString());
		assertEquals(73, result.get("unknownTopLevel").getAsInt());
		assertEquals(42, result.getAsJsonObject(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE)
				.get("revision").getAsInt());
	}

	@Test void modelSavePreservesNestedAndIdentityMatchedArrayFields() {
		JsonObject existing = JsonParser.parseString("""
				{
				  "settings": { "known": "old", "future": 17 },
				  "elements": [
				    { "name": "second", "known": 2, "pluginField": "keep-second" },
				    { "name": "first", "known": 1, "pluginField": "keep-first" },
				    { "name": "removed", "known": 0, "pluginField": "drop-with-element" }
				  ]
				}
				""").getAsJsonObject();
		JsonObject generated = JsonParser.parseString("""
				{
				  "settings": { "known": "new" },
				  "elements": [
				    { "name": "first", "known": 11 },
				    { "name": "second", "known": 22 }
				  ]
				}
				""").getAsJsonObject();

		JsonObject merged = UnknownFieldPreservingJsonMerge.merge(existing, generated).getAsJsonObject();

		assertEquals("new", merged.getAsJsonObject("settings").get("known").getAsString());
		assertEquals(17, merged.getAsJsonObject("settings").get("future").getAsInt());
		assertEquals("first", merged.getAsJsonArray("elements").get(0).getAsJsonObject().get("name").getAsString());
		assertEquals("keep-first", merged.getAsJsonArray("elements").get(0).getAsJsonObject()
				.get("pluginField").getAsString());
		assertEquals(2, merged.getAsJsonArray("elements").size());
	}

	@Test void knownNullFieldsAreClearedWhileUnknownFieldsRemain() throws Exception {
		Path file = temporaryDirectory.resolve("known-null.json");
		Files.writeString(file, "{\"settings\":{\"known\":\"old\",\"future\":17}}", StandardCharsets.UTF_8);

		String merged = UnknownFieldPreservingJsonMerge.mergeExistingFile(file, "{\"settings\":{}}",
				"{\"settings\":{\"known\":null}}", new com.google.gson.Gson());
		JsonObject result = JsonParser.parseString(merged).getAsJsonObject().getAsJsonObject("settings");

		assertFalse(result.has("known"));
		assertEquals(17, result.get("future").getAsInt());
	}

	@Test void onlyOneWriterLeaseCanBeHeldForAWorkspace() throws Exception {
		try (WorkspaceFileLease first = WorkspaceFileLease.tryAcquire(temporaryDirectory).orElseThrow()) {
			assertFalse(WorkspaceFileLease.tryAcquire(temporaryDirectory).isPresent());
		}
		try (WorkspaceFileLease ignored = WorkspaceFileLease.tryAcquire(temporaryDirectory).orElseThrow()) {
			assertTrue(Files.exists(temporaryDirectory.resolve(".copperbench/workspace.write.lock")));
		}
	}

	@Test void metadataMigrationCreatesStableIdentityAndRejectsStaleRevision() throws Exception {
		Path workspaceFile = temporaryDirectory.resolve("metadata.mcreator");
		Files.writeString(workspaceFile, "{\"upstream\":{\"opaque\":true}}", StandardCharsets.UTF_8);
		ProductMetadataManager manager = new ProductMetadataManager(new UnknownFieldPreservingJsonStore());
		java.util.UUID workspaceId = java.util.UUID.fromString("11111111-1111-4111-8111-111111111119");

		try (WorkspaceFileLease lease = WorkspaceFileLease.tryAcquire(temporaryDirectory).orElseThrow()) {
			ProductMetadataManager.Metadata created = manager.loadOrCreate(workspaceFile, workspaceId, lease);
			ProductMetadataManager.Metadata advanced = manager.advanceRevision(workspaceFile, workspaceId, 0, lease);

			assertEquals(ProductMetadataManager.CURRENT_SCHEMA_VERSION, created.schemaVersion());
			assertEquals(workspaceId, created.workspaceId());
			assertEquals(1, advanced.revision());
			assertThrows(ProductMetadataManager.RevisionConflictException.class,
					() -> manager.advanceRevision(workspaceFile, workspaceId, 0, lease));
		}
		assertTrue(new UnknownFieldPreservingJsonStore().read(workspaceFile).getAsJsonObject("upstream")
				.get("opaque").getAsBoolean());
	}

	@Test void metadataFactoryReusesThePersistedWorkspaceIdentity() throws Exception {
		Path workspaceFile = temporaryDirectory.resolve("stable-identity.mcreator");
		Files.writeString(workspaceFile, "{}", StandardCharsets.UTF_8);
		ProductMetadataManager manager = new ProductMetadataManager(new UnknownFieldPreservingJsonStore());
		java.util.UUID firstId = java.util.UUID.fromString("11111111-1111-4111-8111-111111111120");
		java.util.UUID ignoredReplacement = java.util.UUID.fromString("11111111-1111-4111-8111-111111111121");

		try (WorkspaceFileLease lease = WorkspaceFileLease.tryAcquire(temporaryDirectory).orElseThrow()) {
			ProductMetadataManager.Metadata created = manager.loadOrCreate(workspaceFile, () -> firstId, lease);
			ProductMetadataManager.Metadata reopened = manager.loadOrCreate(workspaceFile,
					() -> ignoredReplacement, lease);

			assertEquals(firstId, created.workspaceId());
			assertEquals(firstId, reopened.workspaceId());
		}
	}
}
