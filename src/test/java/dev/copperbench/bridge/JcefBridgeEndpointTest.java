/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Handshake;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JcefBridgeEndpointTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T05:00:00Z"), ZoneOffset.UTC);

	@Test void handshakeQueryCommandAndEventsUseFrozenV1WireEnvelopes() {
		AtomicLong sequence = new AtomicLong(1_000);
		Supplier<UUID> ids = () -> UUID.fromString("00000000-0000-4000-8000-" +
				String.format("%012d", sequence.getAndIncrement()));
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(workspace());
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, ids), CLOCK, ids);
		List<String> events = new ArrayList<>();
		JcefBridgeEndpoint endpoint = new JcefBridgeEndpoint(new WorkspaceEntryAdapter(service,
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)), events::add);

		JsonObject client = new JsonObject();
		client.addProperty("id", "product_shell");
		client.addProperty("version", "0.1.0");
		String handshake = endpoint.handle(UiCore.wireGson().toJson(new Handshake("handshake", ids.get(),
				List.of("1.0"), client)));
		assertEquals("1.0", JsonParser.parseString(handshake).getAsJsonObject()
				.get("selectedSchemaVersion").getAsString());

		String query = endpoint.handle(UiCore.wireGson().toJson(Query.of(ids.get(), WORKSPACE_ID,
				Operation.GET_WORKBENCH, new JsonObject())));
		assertEquals("1.0", JsonParser.parseString(query).getAsJsonObject().get("schemaVersion").getAsString());

		JsonObject payload = new JsonObject();
		payload.addProperty("clientMutationId", ids.get().toString());
		payload.addProperty("elementType", "item");
		payload.addProperty("name", "trail_marker");
		payload.add("initialValues", new JsonObject());
		String command = endpoint.handle(UiCore.wireGson().toJson(Command.of(ids.get(), WORKSPACE_ID, 0,
				Operation.CREATE_MOD_ELEMENT, payload)));
		assertEquals("committed", JsonParser.parseString(command).getAsJsonObject().get("status").getAsString());
		assertEquals(1, events.size());
		assertEquals("1.0", JsonParser.parseString(events.getFirst()).getAsJsonObject()
				.get("schemaVersion").getAsString());
	}

	@Test void rejectsAnEnvelopeThatDidNotNegotiateV1() {
		JcefBridgeEndpoint endpoint = endpoint();
		String request = "{\"messageType\":\"query\",\"schemaVersion\":\"0.1\"," +
				"\"requestId\":\"00000000-0000-4000-8000-000000001101\"," +
				"\"workspaceId\":\"11111111-1111-4111-8111-111111111111\"," +
				"\"operation\":\"get_workbench\",\"payload\":{}}";
		assertThrows(IllegalArgumentException.class, () -> endpoint.handle(request));
	}

	private static JcefBridgeEndpoint endpoint() {
		Supplier<UUID> ids = UUID::randomUUID;
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(workspace());
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, ids), CLOCK, ids);
		return new JcefBridgeEndpoint(new WorkspaceEntryAdapter(service,
				new RequestContext(Actor.UI, PermissionProfile.WORKSPACE)), ignored -> { });
	}

	private static WorkspaceState workspace() {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		return new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator,
				new JsonObject(), List.of());
	}
}
