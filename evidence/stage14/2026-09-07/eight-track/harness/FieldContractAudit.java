package dev.copperbench.headless;

import com.google.gson.*;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;

/** Replays the documented public maxStackSize contract, separately from the upstream storage spelling. */
public final class FieldContractAudit {
    public static void main(String[] args) throws Exception {
        TrackAudit.track = "fabric-1.21.1";
        TrackAudit.initialize();
        JsonArray results = new JsonArray();
        for (String track : List.of("fabric-1.20.1", "fabric-1.21.1", "fabric-26.1.2", "fabric-26.2",
                "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2")) {
            Path root = TrackAudit.AUDIT.resolve("field-contract").resolve(track);
            Files.createDirectories(root);
            WorkspaceSettings settings = new WorkspaceSettings("field_contract");
            settings.setModName("Field Contract"); settings.setVersion("1.0.0"); settings.setCurrentGenerator(track);
            try (Workspace workspace = Workspace.createWorkspace(root.resolve("field_contract.mcreator").toFile(), settings)) {
                if (!workspace.getGenerator().generateBase()) throw new IllegalStateException("generate base failed " + track);
                try (var session = MCreatorWorkspaceSession.attach(workspace, UUID.randomUUID(),
                        new InMemoryWorkspaceTaskGateway(Clock.systemUTC(), UUID::randomUUID), Clock.systemUTC(), UUID::randomUUID)) {
                    JsonObject values = new JsonObject(); values.addProperty("texture", "minecraft:amethyst_shard");
                    JsonObject fields = new JsonObject(); fields.addProperty("maxStackSize", 1); values.add("fields", fields);
                    JsonObject create = new JsonObject(); create.addProperty("clientMutationId", UUID.randomUUID().toString());
                    create.addProperty("elementType", "item"); create.addProperty("name", "probe_item"); create.add("initialValues", values);
                    var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 0, Operation.CREATE_MOD_ELEMENT, create));
                    JsonObject row = new JsonObject(); row.addProperty("track", track);
                    row.addProperty("publicContract", "docs/ai/agent-playbook.md initialValues.fields.maxStackSize; generator-repair fixture /fields/maxStackSize");
                    row.addProperty("createStatus", created.result().status()); row.addProperty("createRequested", 1);
                    row.addProperty("actualAfterCreate", ((net.mcreator.element.types.Item)workspace.getModElementByName("probe_item").getGeneratableElement()).stackSize);
                    String id = created.result().data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
                    JsonObject change = new JsonObject(); change.addProperty("path", "/fields/maxStackSize"); change.addProperty("value", 7);
                    JsonArray edits = new JsonArray(); edits.add(change);
                    JsonObject update = new JsonObject(); update.addProperty("clientMutationId", UUID.randomUUID().toString());
                    update.addProperty("elementId", id); update.add("changes", edits);
                    var updated = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), 1, Operation.UPDATE_MOD_ELEMENT, update));
                    row.addProperty("updateStatus", updated.result().status()); row.addProperty("updateRequested", 7);
                    row.addProperty("actualAfterUpdate", ((net.mcreator.element.types.Item)workspace.getModElementByName("probe_item").getGeneratableElement()).stackSize);
                    results.add(row);
                    System.out.println("FIELD_CONTRACT " + row);
                }
            }
        }
        Files.writeString(TrackAudit.AUDIT.resolve("field-contract.json"), TrackAudit.JSON.toJson(results));
        System.exit(0);
    }
}
