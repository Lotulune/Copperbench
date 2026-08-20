package dev.copperbench.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.contract.UiCore.Actor;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamToolCatalogTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111150");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC);

	@Test void catalogRoutesTagsAndPackMakersToLegacyAndRejectsRemoteUpdates() {
		JsonObject json = UpstreamToolCatalog.toJson();
		JsonArray tools = json.getAsJsonArray("tools");
		assertEquals(19, tools.size());
		assertEquals("legacy_window", surface(tools, "tags_variables_localization"));
		assertEquals("legacy_window", surface(tools, "pack_makers"));
		assertEquals("new_ui", surface(tools, "mod_elements_first_party"));
		assertEquals("unsupported", surface(tools, "mod_elements_other"));
		assertEquals("unsupported", surface(tools, "run_server_debug_client"));
		assertEquals("not_applicable", surface(tools, "check_for_updates"));
	}

	@Test void querySharesTheOfficialCatalog() {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		store.register(new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator, new JsonObject(),
				List.of()));
		AtomicLong sequence = new AtomicLong(910);
		WorkspaceApplicationService service = new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(sequence.getAndIncrement())), CLOCK,
				() -> uuid(sequence.getAndIncrement()));
		var result = service.query(Query.of(uuid(1), WORKSPACE_ID, Operation.GET_UPSTREAM_TOOLS, new JsonObject()),
				new RequestContext(Actor.HEADLESS, PermissionProfile.READ_ONLY));
		assertEquals("succeeded", result.status());
		assertEquals(UpstreamToolCatalog.toJson(), result.data().getAsJsonObject());
		assertTrue(ReleaseManifest.official().has("upstreamTools"));
	}

	private static String surface(JsonArray tools, String id) {
		for (var item : tools)
			if (id.equals(item.getAsJsonObject().get("id").getAsString()))
				return item.getAsJsonObject().get("surface").getAsString();
		throw new AssertionError(id);
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
