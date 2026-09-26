package dev.copperbench.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.function.Consumer;

/** File references in blockstate and client-item documents, separate from codec/registry identifiers. */
final class MinecraftRenderReferences {
    record Reference(String value, String pointer) {}
    private final String source;
    private final Consumer<Reference> references;
    private final Consumer<AssetDiagnostic> diagnostics;

    MinecraftRenderReferences(String source, Consumer<Reference> references, Consumer<AssetDiagnostic> diagnostics) {
        this.source = source;
        this.references = references;
        this.diagnostics = diagnostics;
    }

    static boolean isDocument(String path, String directory) {
        return path.matches("(?:.*/)?assets/[a-z0-9_.-]+/" + directory + "/.+\\.json");
    }

    void item(JsonElement document) {
        JsonObject root = object(document, "");
        if (root != null) itemModel(root.get("model"), "/model", 0);
    }

    void blockstate(JsonElement document) {
        JsonObject root = object(document, "");
        if (root == null) return;
        if (root.has("variants")) {
            JsonObject variants = object(root.get("variants"), "/variants");
            if (variants != null) variants.entrySet().forEach(entry ->
                    blockModel(entry.getValue(), "/variants/" + pointer(entry.getKey())));
        }
        if (root.has("multipart")) array(root.get("multipart"), "/multipart", (entry, location) -> {
            JsonObject part = object(entry, location);
            if (part != null) blockModel(part.get("apply"), location + "/apply");
        });
    }

    private void blockModel(JsonElement node, String location) {
        if (node != null && node.isJsonArray()) {
            array(node, location, (entry, entryLocation) -> {
                JsonObject model = object(entry, entryLocation);
                if (model != null) modelReference(model.get("model"), entryLocation + "/model");
            });
        } else {
            JsonObject model = object(node, location);
            if (model != null) modelReference(model.get("model"), location + "/model");
        }
    }

    private void itemModel(JsonElement node, String location, int depth) {
        if (depth > 64) { invalid(location, "Item model nesting exceeds 64 levels"); return; }
        JsonObject model = object(node, location);
        if (model == null) return;
        String type = identifier(model.get("type"), location + "/type");
        if (type == null) return;
        switch (type) {
            case "minecraft:model" -> modelReference(model.get("model"), location + "/model");
            case "minecraft:composite" -> array(model.get("models"), location + "/models",
                    (child, childLocation) -> itemModel(child, childLocation, depth + 1));
            case "minecraft:condition" -> {
                itemModel(model.get("on_true"), location + "/on_true", depth + 1);
                itemModel(model.get("on_false"), location + "/on_false", depth + 1);
            }
            case "minecraft:select", "minecraft:range_dispatch" -> {
                String key = type.equals("minecraft:select") ? "cases" : "entries";
                array(model.get(key), location + "/" + key, (entry, entryLocation) -> {
                    JsonObject branch = object(entry, entryLocation);
                    if (branch != null) itemModel(branch.get("model"), entryLocation + "/model", depth + 1);
                });
                if (model.has("fallback")) itemModel(model.get("fallback"), location + "/fallback", depth + 1);
            }
            case "minecraft:special" -> {
                modelReference(model.get("base"), location + "/base");
                JsonObject renderer = object(model.get("model"), location + "/model");
                if (renderer != null && identifier(renderer.get("type"), location + "/model/type") != null)
                    unverified(location + "/model", "Special-renderer resources require renderer-specific resolution; the base model is checked separately");
            }
            // These nodes render no fixed model resource from this document.
            case "minecraft:empty", "minecraft:bundle/selected_item" -> { }
            default -> unverified(location + "/type", "Item model codec is not supported by this reference inspector: " + type);
        }
    }

    private void modelReference(JsonElement node, String location) {
        if (node == null || !node.isJsonPrimitive() || !node.getAsJsonPrimitive().isString()) {
            invalid(location, "Model reference must be a resource identifier string");
            return;
        }
        // Keep the literal and pointer; the shared resolver validates resource syntax and resolves its source/version.
        String value = node.getAsString();
        if (identifier(node, location) != null) references.accept(new Reference(value, location));
    }

    private String identifier(JsonElement node, String location) {
        if (node == null || !node.isJsonPrimitive() || !node.getAsJsonPrimitive().isString()) {
            invalid(location, "Expected a resource identifier string");
            return null;
        }
        try { return MinecraftModelResolver.resourceId(node.getAsString()); }
        catch (IllegalArgumentException exception) {
            diagnostics.accept(new AssetDiagnostic("INVALID_RESOURCE_NAME", AssetDiagnostic.Severity.ERROR,
                    source, node.getAsString(), "Invalid resource identifier at " + location, location));
            return null;
        }
    }

    private JsonObject object(JsonElement node, String location) {
        if (node != null && node.isJsonObject()) return node.getAsJsonObject();
        invalid(location, "Expected a JSON object");
        return null;
    }

    private void array(JsonElement node, String location, java.util.function.BiConsumer<JsonElement, String> consumer) {
        if (node == null || !node.isJsonArray()) { invalid(location, "Expected a JSON array"); return; }
        for (int i = 0; i < node.getAsJsonArray().size(); i++) consumer.accept(node.getAsJsonArray().get(i), location + "/" + i);
    }

    private void invalid(String location, String message) {
        diagnostics.accept(new AssetDiagnostic("INVALID_ASSET_DOCUMENT", AssetDiagnostic.Severity.ERROR, source, null, message, location));
    }

    private void unverified(String location, String message) {
        diagnostics.accept(new AssetDiagnostic("ASSET_RENDERER_REFERENCES_UNVERIFIED", AssetDiagnostic.Severity.WARNING, source, null, message, location));
    }

    private static String pointer(String value) { return value.replace("~", "~0").replace("/", "~1"); }
}
