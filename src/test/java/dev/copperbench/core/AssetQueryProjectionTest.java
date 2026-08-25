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
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetQueryProjectionTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void listAssetsReadsTheWorkspaceAndReturnsReferenceDiagnostics(@TempDir Path temp) throws Exception {
		Path model = temp.resolve("assets/copperbench/models/block/lamp.json");
		Path texture = temp.resolve("assets/copperbench/textures/block/lamp.png");
		Files.createDirectories(model.getParent());
		Files.createDirectories(texture.getParent());
		Files.writeString(model, "{\"textures\":{\"all\":\"copperbench:block/lamp\"},"
				+ "\"missing\":\"copperbench:block/missing\"}");
		Files.write(texture, new byte[] { 1, 2, 3 });

		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copperbench", "mod", 0, false, generator,
				new JsonObject(), List.of()));

		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, UUID::randomUUID), WorkspaceMutationGateway.noOp(), null, null,
				ignored -> temp, CLOCK, UUID::randomUUID);
		var result = service.query(Query.of(UUID.randomUUID(), WORKSPACE_ID, Operation.LIST_ASSETS, new JsonObject()),
				new RequestContext(Actor.UI, PermissionProfile.READ_ONLY));

		assertEquals("succeeded", result.status());
		JsonObject projection = result.data().getAsJsonObject();
		assertEquals("1.0", projection.get("schemaVersion").getAsString());
		assertEquals(2, projection.getAsJsonArray("assets").size());
		assertTrue(projection.getAsJsonArray("assets").toString().contains("assets/copperbench/models/block/lamp.json"));
		assertTrue(projection.getAsJsonArray("assets").get(0).getAsJsonObject().has("updatedAt"));
		assertEquals(1, projection.getAsJsonArray("references").size());
		assertEquals("assets/copperbench/textures/block/lamp.png",
				projection.getAsJsonArray("references").get(0).getAsJsonObject().get("targetPath").getAsString());
		assertTrue(projection.getAsJsonArray("diagnostics").toString().contains("MISSING_ASSET_REFERENCE"));
	}
}
