package dev.copperbench.procedure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.UnknownFieldPreservingJsonStore;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceMutationGateway;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceStateMapper;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.UUID;

/** Read-only bridge from product call identities to the names required by generator templates. */
public final class WorkspaceProcedureTargets {
    private WorkspaceProcedureTargets() {}

    public static String requestedTarget(Element block) {
        JsonObject fields = new JsonObject();
        for (Element field : net.mcreator.util.XMLUtil.getChildrenWithName(block, "field"))
            fields.addProperty(field.getAttribute("name"), field.getTextContent());
        return ProcedureIrCodec.callTarget(fields);
    }

    public static String resolveName(Workspace workspace, String reference) {
        if (reference == null || reference.isBlank()) return null;
        UUID requested = uuid(reference);
        UUID workspaceId = requested == null ? null : workspaceId(workspace);
        var matches = new ArrayList<ModElement>();
        for (ModElement element : workspace.getModElements()) {
            if (!element.getTypeString().equals("procedure")) continue;
            boolean matchesTarget;
            if (requested == null) {
                String projectedName = element.getName().matches("^[a-z][a-z0-9_]{0,63}$")
                        ? element.getName() : element.getRegistryName();
                matchesTarget = reference.equals(element.getName()) || reference.equals(projectedName);
            } else {
                Object stored = element.getMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_ID_METADATA);
                UUID identity = stored == null ? null : uuid(String.valueOf(stored));
                if (identity == null && workspaceId != null)
                    identity = MCreatorWorkspaceStateMapper.elementId(workspaceId, element);
                matchesTarget = requested.equals(identity);
            }
            if (matchesTarget) matches.add(element);
        }
        return matches.size() == 1 ? matches.getFirst().getName() : null;
    }

    private static UUID workspaceId(Workspace workspace) {
        // Never loadOrCreate metadata during generation lookup: a lookup must not advance or rewrite the workspace.
        try {
            var document = JsonParser.parseString(Files.readString(workspace.getFileManager().getWorkspaceFile().toPath())).getAsJsonObject();
            var metadata = document.getAsJsonObject(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE);
            return metadata == null ? null : uuid(metadata.get("workspaceId").getAsString());
        } catch (Exception unavailable) { return null; }
    }

    private static UUID uuid(String text) {
        try { UUID id = UUID.fromString(text); return id.toString().equals(text) ? id : null; }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
