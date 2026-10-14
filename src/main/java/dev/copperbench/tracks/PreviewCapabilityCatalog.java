package dev.copperbench.tracks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reviewed implementation evidence, separate from published support and per-request authority. */
public final class PreviewCapabilityCatalog {
    public static final Set<String> ITEM_FIELDS = Set.of("name", "displayName", "description",
            "texture", "stackSize", "maxStackSize", "rarity", "immuneToFire", "fuelBurnTime", "compostingLayers");
    public static final Set<String> BLOCK_FIELDS = Set.of("name", "displayName", "description", "texture",
            "hardness", "resistance", "rotationMode", "maxStackSize", "rarity");
    public static final Set<String> RECIPE_FIELDS = Set.of("name", "displayName", "description", "recipeType",
            "recipeShapeless", "recipeSlots", "recipeReturnStack", "recipeRetstackSize", "cookingTime", "xpReward",
            "smeltingInputStack", "smeltingReturnStack", "blastingInputStack", "blastingReturnStack",
            "smokingInputStack", "smokingReturnStack");
    public static final Set<String> FUNCTION_FIELDS = Set.of("name", "displayName", "description", "namespace", "code", "commands");
    public static final Set<String> CODE_FIELDS = Set.of("name", "displayName", "description", "code", "codeFiles", "sourceFingerprints");
    public static final List<UiCore.Operation> NATIVE_OPERATIONS = List.of(
            UiCore.Operation.CREATE_WORKSPACE, UiCore.Operation.UPDATE_WORKSPACE_FILE,
            UiCore.Operation.VALIDATE_WORKSPACE, UiCore.Operation.GENERATE_WORKSPACE,
            UiCore.Operation.BUILD_WORKSPACE, UiCore.Operation.RUN_CLIENT, UiCore.Operation.RUN_SERVER,
            UiCore.Operation.RUN_DATAGEN, UiCore.Operation.RUN_GAMETEST, UiCore.Operation.PREPARE_GAME_TESTS,
            UiCore.Operation.EXPORT_WORKSPACE);

    /** Evidence is supplied by the product composition, never by an API request. */
    public record Evidence(String environmentEvidence, Map<UiCore.Operation, String> operations,
            Map<String, Set<String>> fields, String supportStage) {
        public Evidence {
            operations = Map.copyOf(operations);
            fields = fields.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
            if (!Set.of("registered", "native_preview", "scoped_support").contains(supportStage))
                throw new IllegalArgumentException("Unknown preview support stage");
            if (operations.values().stream().anyMatch(value -> value == null || value.isBlank()))
                throw new IllegalArgumentException("Enabled operations need an evidence reference");
            if (!operations.isEmpty() && (environmentEvidence == null || environmentEvidence.isBlank()))
                throw new IllegalArgumentException("Operation evidence requires environment qualification");
        }
    }

    private final Map<String, Evidence> evidence;

    public PreviewCapabilityCatalog(Map<String, Evidence> evidence) { this.evidence = Map.copyOf(evidence); }

    public static PreviewCapabilityCatalog official() {
        return new PreviewCapabilityCatalog(Map.of(
                "fabric-26.3", new Evidence("p263-ticket-03-fabric-environment-2026-10-12", structuredOperations(Map.ofEntries(
                        Map.entry(UiCore.Operation.CREATE_WORKSPACE, "p263-ticket-07-fabric-native-build-2026-10-12"),
                        Map.entry(UiCore.Operation.UPDATE_WORKSPACE_FILE, "p263-ticket-07-fabric-native-build-2026-10-12"),
                        Map.entry(UiCore.Operation.VALIDATE_WORKSPACE, "p263-ticket-07-fabric-native-build-2026-10-12"),
                        Map.entry(UiCore.Operation.GENERATE_WORKSPACE, "p263-ticket-07-fabric-native-build-2026-10-12"),
                        Map.entry(UiCore.Operation.RUN_CLIENT, "p263-ticket-32-fabric-client-qualification-2026-10-13"),
                        Map.entry(UiCore.Operation.RUN_SERVER, "p263-ticket-07-fabric-native-build-2026-10-12"),
                        Map.entry(UiCore.Operation.PREPARE_GAME_TESTS, "p263-ticket-09-fabric-native-acceptance-2026-10-12"),
                        Map.entry(UiCore.Operation.RUN_GAMETEST, "p263-ticket-09-fabric-native-acceptance-2026-10-12"),
                        Map.entry(UiCore.Operation.EXPORT_WORKSPACE, "p263-ticket-09-fabric-native-acceptance-2026-10-12"),
                        Map.entry(UiCore.Operation.BUILD_WORKSPACE, "p263-ticket-07-fabric-native-build-2026-10-12"),
                        Map.entry(UiCore.Operation.CREATE_MOD_ELEMENT, "p263-ticket-22-fabric-code-2026-10-13"))),
                        Map.of("item", ITEM_FIELDS, "block", BLOCK_FIELDS, "recipe", RECIPE_FIELDS, "function", FUNCTION_FIELDS, "code", CODE_FIELDS), "registered"),
                "neoforge-26.3", new Evidence("p263-ticket-04-neoforge-environment-2026-10-12", structuredOperations(Map.ofEntries(
                        Map.entry(UiCore.Operation.CREATE_WORKSPACE, "p263-ticket-08-neoforge-native-build-2026-10-12"),
                        Map.entry(UiCore.Operation.UPDATE_WORKSPACE_FILE, "p263-ticket-08-neoforge-native-build-2026-10-12"),
                        Map.entry(UiCore.Operation.VALIDATE_WORKSPACE, "p263-ticket-08-neoforge-native-build-2026-10-12"),
                        Map.entry(UiCore.Operation.GENERATE_WORKSPACE, "p263-ticket-08-neoforge-native-build-2026-10-12"),
                        Map.entry(UiCore.Operation.RUN_CLIENT, "p263-ticket-33-neoforge-client-qualification-2026-10-13"),
                        Map.entry(UiCore.Operation.RUN_SERVER, "p263-ticket-08-neoforge-native-build-2026-10-12"),
                        Map.entry(UiCore.Operation.PREPARE_GAME_TESTS, "p263-ticket-10-neoforge-native-acceptance-2026-10-12"),
                        Map.entry(UiCore.Operation.RUN_GAMETEST, "p263-ticket-10-neoforge-native-acceptance-2026-10-12"),
                        Map.entry(UiCore.Operation.EXPORT_WORKSPACE, "p263-ticket-10-neoforge-native-acceptance-2026-10-12"),
                        Map.entry(UiCore.Operation.BUILD_WORKSPACE, "p263-ticket-08-neoforge-native-build-2026-10-12"),
                        Map.entry(UiCore.Operation.CREATE_MOD_ELEMENT, "p263-ticket-23-neoforge-code-2026-10-13"))),
                        Map.of("item", ITEM_FIELDS, "block", BLOCK_FIELDS, "recipe", RECIPE_FIELDS, "function", FUNCTION_FIELDS, "code", CODE_FIELDS), "registered")));
    }

    private static Map<UiCore.Operation, String> structuredOperations(Map<UiCore.Operation, String> nativeOperations) {
        var result = new java.util.LinkedHashMap<>(nativeOperations);
        result.put(UiCore.Operation.UPDATE_MOD_ELEMENT, nativeOperations.get(UiCore.Operation.CREATE_MOD_ELEMENT));
        return Map.copyOf(result);
    }

    /** Verified writable fields; an empty set never grants a structured operation. */
    public Set<String> fields(String generatorId, String type) {
        Evidence target = evidence.get(generatorId);
        return target == null ? Set.of() : target.fields().getOrDefault(type, Set.of());
    }

    /** Empty means implementation evidence permits this operation; permissions still apply. */
    public String reason(String generatorId, UiCore.Operation operation) {
        Evidence target = evidence.get(generatorId);
        if (target == null || target.environmentEvidence() == null || target.environmentEvidence().isBlank())
            return "PREVIEW_ENVIRONMENT_UNVERIFIED";
        if (!target.operations().containsKey(operation))
            return NATIVE_OPERATIONS.contains(operation) ? "PREVIEW_NATIVE_OPERATION_UNVERIFIED" : "PREVIEW_OPERATION_UNSUPPORTED";
        return "";
    }

    public JsonObject projection(String generatorId) {
        Evidence target = evidence.get(generatorId);
        JsonObject value = new JsonObject();
        value.addProperty("supportStage", target == null ? "registered" : target.supportStage());
        boolean environment = target != null && target.environmentEvidence() != null && !target.environmentEvidence().isBlank();
        value.addProperty("environmentQualified", environment);
        value.addProperty("environmentEvidence", environment ? target.environmentEvidence() : "");
        JsonArray operations = new JsonArray();
        var offered = new java.util.LinkedHashSet<>(NATIVE_OPERATIONS);
        if (target != null) offered.addAll(target.operations().keySet());
        for (UiCore.Operation operation : offered) {
            JsonObject item = new JsonObject();
            item.add("operation", UiCore.wireGson().toJsonTree(operation));
            String reason = reason(generatorId, operation);
            item.addProperty("ready", reason.isEmpty());
            item.addProperty("reasonCode", reason);
            item.addProperty("evidence", target == null ? "" : target.operations().getOrDefault(operation, ""));
            operations.add(item);
        }
        value.add("operations", operations);
        JsonArray types = new JsonArray();
        for (String type : List.of("item", "block", "recipe", "function", "code")) {
            JsonObject item = new JsonObject();
            item.addProperty("elementType", type);
            Set<String> fields = target == null ? Set.of() : target.fields().getOrDefault(type, Set.of());
            item.addProperty("supported", !fields.isEmpty());
            item.addProperty("reasonCode", fields.isEmpty() ? "PREVIEW_ELEMENT_UNVERIFIED" : "");
            JsonArray names = new JsonArray(); fields.stream().sorted().forEach(names::add);
            item.add("fields", names);
            types.add(item);
        }
        value.add("elementTypes", types);
        return value;
    }
}
