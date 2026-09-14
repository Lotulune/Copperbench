package dev.copperbench.headless;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.contract.UiCore;

import java.util.UUID;

/** Shared desktop selection, separate from persistent workspace revisions. */
public final class PythonContext {
    private final HeadlessWorkspaceEntryAdapter adapter;
    private final UUID workspaceId;
    private UUID activeElement;
    private String view = "workspace";
    private long selectionVersion;

    public PythonContext(HeadlessWorkspaceEntryAdapter adapter, UUID workspaceId) {
        this.adapter = adapter;
        this.workspaceId = workspaceId;
    }

    public synchronized void select(UUID elementId) {
        if (elementId != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("elementId", elementId.toString());
            var result = adapter.query(UiCore.Query.of(UUID.randomUUID(), workspaceId,
                    UiCore.Operation.GET_MOD_ELEMENT_EDITOR, payload));
            if (!"succeeded".equals(result.status())) throw new IllegalArgumentException("Selected element does not exist");
        }
        if (!java.util.Objects.equals(activeElement, elementId)) {
            activeElement = elementId;
            selectionVersion++;
        }
    }

    public synchronized void updateFromUi(UUID elementId, String view) {
        select(elementId);
        if (view == null || view.length() > 64) throw new IllegalArgumentException("Invalid view");
        this.view = view;
    }

    public synchronized JsonObject snapshot() {
        JsonObject result = new JsonObject();
        result.addProperty("workspaceId", workspaceId.toString());
        result.addProperty("view", view);
        result.addProperty("selectionVersion", selectionVersion);
        result.add("activeElementId", activeElement == null ? JsonNull.INSTANCE
                : new com.google.gson.JsonPrimitive(activeElement.toString()));
        return result;
    }

    public JsonObject dispatch(String operation, JsonObject payload) {
        if (operation.equals("select")) {
            select(payload.has("elementId") && !payload.get("elementId").isJsonNull()
                    ? UUID.fromString(payload.get("elementId").getAsString()) : null);
        } else if (!operation.equals("get")) {
            throw new IllegalArgumentException("Unknown context operation");
        }
        var workbench = adapter.query(UiCore.Query.of(UUID.randomUUID(), workspaceId,
                UiCore.Operation.GET_WORKBENCH, new JsonObject()));
        JsonObject result = new JsonObject();
        result.addProperty("status", workbench.status());
        result.addProperty("revision", workbench.revision());
        result.add("data", snapshot());
        return result;
    }
}
