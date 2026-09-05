/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetQueryProjectionTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void listAssetsReadsTheWorkspaceAndReturnsReferenceDiagnostics(@TempDir Path temp) throws Exception {
		Path model = temp.resolve("assets/copperbench/models/block/lamp.json");
		Path texture = temp.resolve("assets/copperbench/textures/block/lamp.png");
		Path duplicateTexture = temp.resolve("assets/copperbench/textures/block/lamp_copy.png");
		Path elementOnlyTexture = temp.resolve("assets/copperbench/textures/block/element_only.png");
		Files.createDirectories(model.getParent());
		Files.createDirectories(texture.getParent());
		Files.writeString(model, "{\"textures\":{\"all\":\"copperbench:block/lamp\"},"
				+ "\"missing\":\"copperbench:block/missing\"}");
		Files.write(texture, new byte[] { 1, 2, 3 });
		Files.write(duplicateTexture, new byte[] { 1, 2, 3 });
		Files.write(elementOnlyTexture, new byte[] { 4, 5, 6 });

		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		JsonObject elementValues = new JsonObject();
		elementValues.addProperty("note", "Uses copperbench:block/element_only conditionally");
		WorkspaceState.Element element = new WorkspaceState.Element(
				UUID.fromString("22222222-2222-4222-8222-222222222222"), "block", "holder", "Holder", "ready",
				"workspace", Instant.parse("2026-08-23T00:00:00Z"), elementValues);
		store.register(new WorkspaceState(WORKSPACE_ID, "Copperbench", "mod", 0, false, generator,
				new JsonObject(), List.of(element)));

		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, UUID::randomUUID), WorkspaceMutationGateway.noOp(), null, null,
				ignored -> temp, CLOCK, UUID::randomUUID);
		var result = service.query(Query.of(UUID.randomUUID(), WORKSPACE_ID, Operation.LIST_ASSETS, new JsonObject()),
				new RequestContext(Actor.UI, PermissionProfile.READ_ONLY));

		assertEquals("succeeded", result.status());
		JsonObject projection = result.data().getAsJsonObject();
		assertEquals("1.0", projection.get("schemaVersion").getAsString());
		assertEquals(4, projection.getAsJsonArray("assets").size());
		assertTrue(projection.getAsJsonArray("assets").toString().contains("assets/copperbench/models/block/lamp.json"));
		assertTrue(projection.getAsJsonArray("assets").get(0).getAsJsonObject().has("updatedAt"));
		assertEquals(1, projection.getAsJsonArray("references").size());
		assertEquals("assets/copperbench/textures/block/lamp.png",
				projection.getAsJsonArray("references").get(0).getAsJsonObject().get("targetPath").getAsString());
		JsonObject reference = projection.getAsJsonArray("references").get(0).getAsJsonObject();
		assertEquals("/textures/all", reference.get("sourcePointer").getAsString());
		assertEquals("copperbench:block/lamp", reference.get("rawValue").getAsString());
		assertEquals("textures/", reference.get("expectedPrefix").getAsString());
		assertTrue(projection.getAsJsonArray("diagnostics").toString().contains("MISSING_ASSET_REFERENCE"));
		JsonObject modelAsset = assetByPath(projection, "assets/copperbench/models/block/lamp.json");
		String modelAssetId = modelAsset.get("id").getAsString();
		JsonObject diagnostic = projection.getAsJsonArray("diagnostics").get(0).getAsJsonObject();
		assertEquals("MISSING_ASSET_REFERENCE", diagnostic.get("code").getAsString());
		assertEquals("error", diagnostic.get("severity").getAsString());
		assertEquals("/assets/" + modelAssetId, diagnostic.get("path").getAsString());
		assertEquals(modelAssetId, diagnostic.getAsJsonArray("actions").get(0).getAsJsonObject()
				.get("target").getAsString());
		assertEquals("open_asset", diagnostic.getAsJsonArray("actions").get(0).getAsJsonObject()
				.get("kind").getAsString());
		assertEquals("assets/copperbench/block/missing.json",
				diagnostic.getAsJsonObject("message").getAsJsonObject("args").get("targetPath").getAsString());
		assertEquals("MISSING_ASSET_REFERENCE", result.diagnostics().getFirst().code());
		assertEquals("/assets/" + modelAssetId, result.diagnostics().getFirst().path());
		JsonObject health = projection.getAsJsonObject("health");
		assertEquals(4, health.get("totalAssets").getAsInt());
		assertEquals(1, health.get("errorAssets").getAsInt());
		assertEquals(1, health.get("missingReferences").getAsInt());
		assertEquals(3, health.get("unusedAssets").getAsInt());
		assertEquals(1, health.get("safeUnusedAssets").getAsInt());
		assertEquals(2, health.get("duplicateAssets").getAsInt());
		assertEquals(1, health.get("duplicateGroups").getAsInt());
		assertTrue(projection.getAsJsonArray("assets").toString().contains("DUPLICATE_ASSET_CONTENT"));
		JsonObject modelHealth = projection.getAsJsonArray("assets").get(0).getAsJsonObject().getAsJsonObject("health");
		assertEquals("ERROR", modelHealth.get("status").getAsString());
		assertTrue(modelHealth.getAsJsonArray("issueCodes").toString().contains("MISSING_ASSET_REFERENCE"));
		JsonObject elementOnlyHealth = assetByPath(projection,
				"assets/copperbench/textures/block/element_only.png").getAsJsonObject("health");
		assertTrue(elementOnlyHealth.get("cleanupAssessed").getAsBoolean());
		assertTrue(elementOnlyHealth.get("workspaceReferenceCount").getAsInt() > 0);
		assertFalse(elementOnlyHealth.get("safeUnused").getAsBoolean());
	}

	@Test
	void rawCodeDisablesSafeCleanupClassification(@TempDir Path temp) throws Exception {
		Path texture = temp.resolve("assets/copperbench/textures/gui/orphan.png");
		Files.createDirectories(texture.getParent());
		Files.write(texture, new byte[] { 7, 8, 9 });

		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		JsonObject codeValues = new JsonObject();
		codeValues.addProperty("code", "public final class CustomHooks {}");
		WorkspaceState.Element code = new WorkspaceState.Element(
				UUID.fromString("33333333-3333-4333-8333-333333333333"), "code", "custom_hooks", "Custom hooks",
				"ready", "workspace", Instant.parse("2026-08-23T00:00:00Z"), codeValues);
		store.register(new WorkspaceState(WORKSPACE_ID, "Copperbench", "mod", 0, false, generator,
				new JsonObject(), List.of(code)));

		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, UUID::randomUUID), WorkspaceMutationGateway.noOp(), null, null,
				ignored -> temp, CLOCK, UUID::randomUUID);
		var result = service.query(Query.of(UUID.randomUUID(), WORKSPACE_ID, Operation.LIST_ASSETS, new JsonObject()),
				new RequestContext(Actor.UI, PermissionProfile.READ_ONLY));

		JsonObject projection = result.data().getAsJsonObject();
		JsonObject orphanHealth = assetByPath(projection, "assets/copperbench/textures/gui/orphan.png")
				.getAsJsonObject("health");
		assertTrue(orphanHealth.get("unused").getAsBoolean());
		assertFalse(orphanHealth.get("cleanupAssessed").getAsBoolean());
		assertFalse(orphanHealth.get("safeUnused").getAsBoolean());
		assertEquals(0, projection.getAsJsonObject("health").get("safeUnusedAssets").getAsInt());
	}

	private static JsonObject assetByPath(JsonObject projection, String path) {
		for (var raw : projection.getAsJsonArray("assets")) {
			JsonObject asset = raw.getAsJsonObject();
			if (path.equals(asset.get("relativePath").getAsString())) return asset;
		}
		throw new AssertionError("Asset not found: " + path);
	}
}
