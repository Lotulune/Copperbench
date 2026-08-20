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
import dev.copperbench.ProductIdentity;
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
import dev.copperbench.release.ReleaseManifest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage8G7GateTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T18:00:00Z"), ZoneOffset.UTC);

	@Test void releaseNotesQuerySharesTheOfficialManifestWithoutClaimingG7Passed() {
		assertFalse(ProductIdentity.IMPLICIT_NETWORK_SERVICES_ENABLED);
		WorkspaceApplicationService service = service();
		var result = service.query(Query.of(uuid(1), WORKSPACE_ID, Operation.GET_RELEASE_NOTES, new JsonObject()),
				new RequestContext(Actor.HEADLESS, PermissionProfile.READ_ONLY));
		assertEquals("succeeded", result.status());
		JsonObject data = result.data().getAsJsonObject();
		assertEquals(ReleaseManifest.official(), data);
		assertEquals("in_progress", data.getAsJsonObject("g7").get("status").getAsString());
		assertFalse(data.getAsJsonObject("privacy").get("accountsRequired").getAsBoolean());
		assertEquals(8, data.getAsJsonObject("claims").getAsJsonArray("goldenCompileClaimed").size());
	}

	@Test void listInstalledPluginsReturnsFirstPartyInventoryWithoutLoadingJava() {
		WorkspaceApplicationService service = service();
		var result = service.query(Query.of(uuid(2), WORKSPACE_ID, Operation.LIST_INSTALLED_PLUGINS, new JsonObject()),
				new RequestContext(Actor.HEADLESS, PermissionProfile.READ_ONLY));
		assertEquals("succeeded", result.status());
		JsonObject data = result.data().getAsJsonObject();
		assertFalse(data.get("loadsJava").getAsBoolean());
		assertTrue(data.getAsJsonArray("plugins").size() >= 8);
	}

	private WorkspaceApplicationService service() {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator, new JsonObject(),
				List.of()));
		AtomicLong sequence = new AtomicLong(800);
		return new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(sequence.getAndIncrement())),
				WorkspaceMutationGateway.noOp(), CLOCK, () -> uuid(sequence.getAndIncrement()));
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
