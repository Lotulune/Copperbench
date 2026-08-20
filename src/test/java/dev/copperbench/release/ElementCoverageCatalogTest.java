package dev.copperbench.release;

import com.google.gson.JsonObject;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
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

class ElementCoverageCatalogTest {

	private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111140");
	private static final UUID LIVING = UUID.fromString("11111111-1111-4111-8111-111111111141");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC);

	@Test void firstPartySliceExcludesLivingEntitiesAndBedrockAddonTypes() {
		assertTrue(ElementCoverageCatalog.isFirstParty("block"));
		assertTrue(ElementCoverageCatalog.isFirstParty("procedure"));
		assertFalse(ElementCoverageCatalog.isFirstParty("livingentity"));
		assertFalse(ElementCoverageCatalog.isFirstParty("gui"));
		assertFalse(ElementCoverageCatalog.isFirstParty("beblock"));
		JsonObject json = ElementCoverageCatalog.toJson();
		assertEquals(4, json.getAsJsonArray("firstPartySlice").size());
		assertTrue(json.getAsJsonArray("unsupportedInNewUi").toString().contains("livingentity"));
		assertTrue(json.getAsJsonArray("bedrockAddonNotApplicable").toString().contains("beentity"));
	}

	@Test void importedLivingEntityIsListedReadOnlyAndCannotBeUpdated() {
		WorkspaceApplicationService service = service();
		var list = service.query(Query.of(uuid(1), WORKSPACE_ID, Operation.LIST_MOD_ELEMENTS, listPayload()),
				new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE));
		assertEquals("succeeded", list.status());
		JsonObject item = list.data().getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject();
		assertEquals("livingentity", item.get("type").getAsString());
		assertFalse(item.get("firstParty").getAsBoolean());

		var editor = service.query(Query.of(uuid(2), WORKSPACE_ID, Operation.GET_MOD_ELEMENT_EDITOR, editorPayload()),
				new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE));
		assertEquals("succeeded", editor.status());
		assertTrue(editor.diagnostics().stream().anyMatch(d -> "ELEMENT_TYPE_OUTSIDE_FIRST_PARTY_SLICE".equals(d.code())));
		assertTrue(editor.data().getAsJsonObject().getAsJsonArray("sections").get(0).getAsJsonObject()
				.getAsJsonArray("fields").get(0).getAsJsonObject().get("readOnly").getAsBoolean());

		JsonObject change = new JsonObject();
		change.addProperty("path", "/displayName");
		change.addProperty("value", "Nope");
		JsonObject update = new JsonObject();
		update.addProperty("clientMutationId", uuid(3).toString());
		update.addProperty("elementId", LIVING.toString());
		com.google.gson.JsonArray changes = new com.google.gson.JsonArray();
		changes.add(change);
		update.add("changes", changes);
		var outcome = service.execute(Command.of(uuid(4), WORKSPACE_ID, 0, Operation.UPDATE_MOD_ELEMENT, update),
				new RequestContext(Actor.HEADLESS, PermissionProfile.WORKSPACE));
		assertEquals("rejected", outcome.result().status());
		assertEquals("ELEMENT_TYPE_OUTSIDE_FIRST_PARTY_SLICE",
				outcome.result().diagnostics().get(0).code());
	}

	private static WorkspaceApplicationService service() {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		Element living = new Element(LIVING, "livingentity", "copper_golem", "Copper Golem", "valid", "generated",
				Instant.parse("2026-08-20T12:00:00Z"), new JsonObject());
		store.register(new WorkspaceState(WORKSPACE_ID, "Copper Trails", "mod", 0, false, generator, new JsonObject(),
				List.of(living)));
		AtomicLong sequence = new AtomicLong(900);
		return new WorkspaceApplicationService(store,
				new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(sequence.getAndIncrement())), CLOCK,
				() -> uuid(sequence.getAndIncrement()));
	}

	private static JsonObject listPayload() {
		JsonObject payload = new JsonObject();
		payload.addProperty("page", 1);
		payload.addProperty("pageSize", 20);
		payload.addProperty("search", "");
		payload.add("types", new com.google.gson.JsonArray());
		payload.add("states", new com.google.gson.JsonArray());
		return payload;
	}

	private static JsonObject editorPayload() {
		JsonObject payload = new JsonObject();
		payload.addProperty("elementId", LIVING.toString());
		return payload;
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
