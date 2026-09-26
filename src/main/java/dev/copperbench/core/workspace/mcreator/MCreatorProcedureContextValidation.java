package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.procedure.ProcedureIr;
import net.mcreator.blockly.data.BlocklyLoader;
import net.mcreator.ui.blockly.BlocklyEditorType;
import net.mcreator.workspace.Workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only validation against this runtime's loaded trigger catalog and active generator. */
final class MCreatorProcedureContextValidation {
    private MCreatorProcedureContextValidation() { }

    static JsonArray catalog(Workspace workspace) {
        JsonArray catalog = new JsonArray();
        var loader = BlocklyLoader.INSTANCE;
        var triggers = loader == null ? null : loader.getExternalTriggerLoader(BlocklyEditorType.PROCEDURE);
        if (triggers == null) return catalog;
        var supported = workspace.getGenerator().getGeneratorStats().getBlocklyTriggers(BlocklyEditorType.PROCEDURE);
        var enabledApis = workspace.getWorkspaceSettings().getMCreatorDependencies();
        for (var trigger : triggers.getExternalTriggers()) {
            if (!trigger.getID().equals("no_ext_trigger") && !supported.contains(trigger.getID())) continue;
            if (trigger.required_apis != null && !enabledApis.containsAll(trigger.required_apis)) continue;
            JsonObject item = new JsonObject();
            item.addProperty("id", trigger.getID());
            JsonObject label = new JsonObject();
            label.addProperty("key", "trigger." + trigger.getID());
            label.addProperty("fallback", trigger.getName());
            item.add("label", label);
            JsonArray dependencies = new JsonArray();
            if (trigger.dependencies_provided != null) for (var dependency : trigger.dependencies_provided) {
                JsonObject value = new JsonObject();
                value.addProperty("name", dependency.name());
                value.addProperty("type", dependency.getRawType());
                dependencies.add(value);
            }
            item.add("dependencies", dependencies);
            catalog.add(item);
        }
        return catalog;
    }

    static List<ProcedureIr.ValidationIssue> validate(Workspace workspace, ProcedureIr ir) {
        if (ir.trigger().equals("no_ext_trigger")) return List.of();
        UUID triggerNode = ir.nodes().stream().filter(node -> node.type().equals("event_trigger"))
                .map(ProcedureIr.Node::id).findFirst().orElse(null);
        var loader = BlocklyLoader.INSTANCE;
        var triggers = loader == null ? null : loader.getExternalTriggerLoader(BlocklyEditorType.PROCEDURE);
        if (triggers == null) return List.of(issue("PROCEDURE_CONTEXT_CATALOG_UNAVAILABLE",
                "The active trigger catalog is unavailable. Reload the product runtime before generating.", triggerNode, "trigger"));
        var trigger = triggers.getExternalTriggers().stream().filter(candidate -> candidate.getID().equals(ir.trigger())).findFirst().orElse(null);
        if (trigger == null) return List.of(issue("PROCEDURE_TRIGGER_UNKNOWN",
                "External trigger is not present in the loaded plugin catalog: " + ir.trigger(), triggerNode, "trigger"));
        List<ProcedureIr.ValidationIssue> issues = new ArrayList<>();
        if (!workspace.getGenerator().getGeneratorStats().getBlocklyTriggers(BlocklyEditorType.PROCEDURE).contains(ir.trigger()))
            issues.add(issue("PROCEDURE_TRIGGER_UNSUPPORTED", "The active generator does not support trigger " + ir.trigger() + ".", triggerNode, "trigger"));
        if (trigger.required_apis != null) for (String api : trigger.required_apis) {
            if (!workspace.getWorkspaceSettings().getMCreatorDependencies().contains(api))
                issues.add(issue("PROCEDURE_TRIGGER_API_REQUIRED", "Trigger " + ir.trigger() + " requires enabled API " + api + ".", triggerNode, "trigger"));
        }
        for (var node : ir.nodes()) {
            var dependency = directContext(node);
            if (dependency == null) continue;
            boolean provided = trigger.dependencies_provided != null && trigger.dependencies_provided.stream().anyMatch(candidate ->
                    dependency.getKey().equals(candidate.name()) && dependency.getValue().equals(candidate.getRawType()));
            if (!provided) issues.add(issue("PROCEDURE_CONTEXT_MISSING", "Trigger " + ir.trigger() + " does not provide "
                    + dependency.getKey() + " of type " + dependency.getValue() + ". Use a compatible trigger or supply context through a caller.", node.id(), null));
        }
        return List.copyOf(issues);
    }

    private static Map.Entry<String, String> directContext(ProcedureIr.Node node) {
        if (node.unknown()) return null;
        return switch (node.type()) {
            case "entity_from_deps" -> Map.entry("entity", "entity");
            case "source_entity_from_deps" -> Map.entry("sourceentity", "entity");
            case "immediate_source_entity_from_deps" -> Map.entry("immediatesourceentity", "entity");
            case "coord_x" -> Map.entry("x", "number");
            case "coord_y" -> Map.entry("y", "number");
            case "coord_z" -> Map.entry("z", "number");
            default -> null;
        };
    }

    private static ProcedureIr.ValidationIssue issue(String code, String message, UUID node, String port) {
        return new ProcedureIr.ValidationIssue(code, message, node, port, true);
    }
}
