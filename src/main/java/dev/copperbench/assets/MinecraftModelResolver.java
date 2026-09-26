package dev.copperbench.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** One read-only model/texture resolver shared by health inspection and import preview. */
final class MinecraftModelResolver {
    record Resource(String state, String source, String version, String path, String assetId, JsonObject model) {
        boolean resolved() { return state.endsWith("_resolved"); }
    }
    record Issue(String code, String kind, String pointer, String target, String message, boolean unverified) {}
    record Inspection(JsonObject model, List<Issue> issues) {}

    private final Path root;
    private final LocalResourceIndex external;
    private final Map<String, AssetDescriptor> assets;
    private final Map<String, JsonObject> proposedModels;
    private final Set<String> proposedTextures;
    private final Map<String, Resource> lookups = new HashMap<>();

    MinecraftModelResolver(Path root, LocalResourceIndex external, List<AssetDescriptor> assets) {
        this(root, external, assets, Map.of(), Set.of());
    }

    MinecraftModelResolver(Path root, LocalResourceIndex external, List<AssetDescriptor> assets,
                           Map<String, JsonObject> proposedModels, Set<String> proposedTextures) {
        this.root = root;
        this.external = external;
        this.assets = new HashMap<>();
        assets.forEach(asset -> this.assets.put(asset.relativePath(), asset));
        this.proposedModels = Map.copyOf(proposedModels);
        this.proposedTextures = Set.copyOf(proposedTextures);
    }

    static String resourceId(String raw) {
        if (raw == null || raw.length() > 512) throw new IllegalArgumentException("Invalid resource identifier");
        String id = raw.contains(":") ? raw : "minecraft:" + raw;
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid resource identifier");
        String path = id.substring(id.indexOf(':') + 1);
        if (path.startsWith("/") || path.endsWith("/")) throw new IllegalArgumentException("Invalid resource identifier");
        for (String part : path.split("/", -1))
            if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IllegalArgumentException("Invalid resource identifier");
        return id;
    }

    static String modelId(String sourcePath) {
        int assets = sourcePath.indexOf("assets/");
        if (assets < 0 || !sourcePath.endsWith(".json")) return null;
        String relative = sourcePath.substring(assets + 7);
        int slash = relative.indexOf('/');
        if (slash < 1 || !relative.substring(slash + 1).startsWith("models/")) return null;
        return relative.substring(0, slash) + ":" + relative.substring(slash + 8, relative.length() - 5);
    }

    Resource lookup(String kind, String raw, String sourcePath) {
        String id = resourceId(raw);
        String key = kind + ":" + id + ":" + resourceRoot(sourcePath);
        return lookups.computeIfAbsent(key, ignored -> load(kind, id, sourcePath));
    }

    private Resource load(String kind, String id, String sourcePath) {
        String namespace = id.substring(0, id.indexOf(':'));
        String path = id.substring(id.indexOf(':') + 1);
        String relative = "assets/" + namespace + "/" + kind + "/" + path + (kind.equals("models") ? ".json" : ".png");
        if (external.unavailableDependencyContext() != null)
            return new Resource("unverified", external.unavailableDependencyContext(), null, relative, null, null);
        LinkedHashSet<String> prefixes = new LinkedHashSet<>(List.of(resourceRoot(sourcePath), "src/main/resources/", "src/main/", ""));
        boolean inWorkspace = prefixes.stream().anyMatch(prefix -> assets.containsKey(prefix + relative))
                || (kind.equals("models") && proposedModels.containsKey(id)) || (kind.equals("textures") && proposedTextures.contains(id));
        if (inWorkspace && external.workspaceOverlap(relative))
            return new Resource("unverified", "workspace_and_dependency_pack_order_unverified", null, relative, null, null);
        if (kind.equals("models") && proposedModels.containsKey(id))
            return new Resource("workspace_resolved", "import_preview", null, relative, null, proposedModels.get(id).deepCopy());
        if (kind.equals("textures") && proposedTextures.contains(id))
            return new Resource("workspace_resolved", "import_preview", null, relative, null, null);
        // These are renderer sentinels, not files in the vanilla JAR.
        if (kind.equals("models") && Set.of("minecraft:builtin/generated", "minecraft:builtin/entity").contains(id))
            return new Resource("vanilla_resolved", "minecraft_builtin", null, relative, null, new JsonObject());
        for (String prefix : prefixes) {
            AssetDescriptor asset = assets.get(prefix + relative);
            if (asset == null) continue;
            try {
                Path file = root.resolve(asset.relativePath()).toRealPath();
                if (!file.startsWith(root.toRealPath())) throw new IOException("Resource escapes the workspace");
                JsonObject model = null;
                if (kind.equals("models")) {
                    if (Files.size(file) > 1024 * 1024) throw new IOException("Model exceeds the inspection limit");
                    model = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                    if (!asset.sha256().equals(dev.copperbench.generator.WorkspaceExecutionSnapshot.sha256(file)))
                        throw new IOException("Model changed during inspection");
                }
                return new Resource("workspace_resolved", "workspace sha256=" + asset.sha256(), null,
                        asset.relativePath(), asset.id(), model);
            } catch (IOException exception) {
                return new Resource("unverified", "workspace_resource_unreadable", null, asset.relativePath(), asset.id(), null);
            } catch (RuntimeException exception) {
                return new Resource("invalid", "invalid_model_document", null, asset.relativePath(), asset.id(), null);
            }
        }
        String ownNamespace = namespace(sourcePath);
        var found = external.resolve(relative, ownNamespace);
        JsonObject model = null;
        if (found.state().endsWith("_resolved") && kind.equals("models")) {
            try { model = external.readModel(relative); }
            catch (IOException exception) { return new Resource("unverified", "external_catalog_changed_or_unreadable", found.version(), relative, null, null); }
            catch (RuntimeException exception) { return new Resource("invalid", found.source(), found.version(), relative, null, null); }
            if (model == null) return new Resource("unverified", found.source(), found.version(), relative, null, null);
        }
        return new Resource(found.state(), found.source(), found.version(), relative, null, model);
    }

    Inspection inspect(String id, JsonObject model, String sourcePath, boolean requireBoundVariables) {
        List<Issue> issues = new ArrayList<>();
        JsonObject effective = inherit(resourceId(id), model, sourcePath, new LinkedHashSet<>(), issues);
        JsonObject textures = effective.has("textures") && effective.get("textures").isJsonObject()
                ? effective.getAsJsonObject("textures") : new JsonObject();
        if (effective.has("textures") && !effective.get("textures").isJsonObject()) {
            issues.add(new Issue("INVALID_ASSET_DOCUMENT", "textures", "/textures", null, "Model textures must be an object", false));
            return new Inspection(effective, List.copyOf(issues));
        }
        // Parent templates may expose variables which are supplied by their children.
        // Always validate literal resources and cycles; require a binding for renderable entry models.
        for (var entry : textures.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                issues.add(new Issue("INVALID_ASSET_DOCUMENT", "textures", "/textures/" + pointer(entry.getKey()), null,
                        "Texture values must be resource identifiers or variables", false));
                continue;
            }
            validateTexture(entry.getValue().getAsString(), textures, sourcePath, "/textures/" + pointer(entry.getKey()),
                    requireBoundVariables, issues);
        }
        if (effective.has("elements") && effective.get("elements").isJsonArray()) {
            int index = 0;
            for (JsonElement element : effective.getAsJsonArray("elements")) {
                if (element.isJsonObject() && element.getAsJsonObject().has("faces")
                        && element.getAsJsonObject().get("faces").isJsonObject()) {
                    for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                        String location = "/elements/" + index + "/faces/" + pointer(face.getKey()) + "/texture";
                        if (face.getValue().isJsonObject() && face.getValue().getAsJsonObject().has("texture")) {
                            var value = face.getValue().getAsJsonObject().get("texture");
                            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
                                validateTexture(value.getAsString(), textures, sourcePath, location, requireBoundVariables, issues);
                            else issues.add(new Issue("INVALID_ASSET_DOCUMENT", "textures", location, null, "Face texture must be a string", false));
                        }
                    }
                }
                index++;
            }
        }
        List<Issue> located = issues.stream().map(issue -> {
            if (model.has("parent") && !containsPointer(model, issue.pointer()))
                return new Issue(issue.code(), issue.kind(), "/parent", issue.target(),
                        issue.message() + " (inherited location " + issue.pointer() + ")", issue.unverified());
            return issue;
        }).toList();
        return new Inspection(effective, List.copyOf(new LinkedHashSet<>(located)));
    }

    String fingerprint() {
        StringBuilder value = new StringBuilder();
        new TreeMap<>(lookups).forEach((key, resource) -> value.append(key).append('\n').append(resource.state())
                .append('\n').append(resource.source()).append('\n').append(resource.version()).append('\n'));
        return BlockbenchModelingService.hash(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private JsonObject inherit(String id, JsonObject model, String sourcePath, Set<String> visiting, List<Issue> issues) {
        if (!visiting.add(id)) {
            issues.add(new Issue("MODEL_PARENT_CYCLE", "models", "/parent", id, "Parent model inheritance contains a cycle: " + String.join(" -> ", visiting) + " -> " + id, false));
            return new JsonObject();
        }
        if (visiting.size() > 64) {
            issues.add(new Issue("MODEL_PARENT_DEPTH", "models", "/parent", id, "Model inheritance exceeds 64 levels", false));
            visiting.remove(id);
            return new JsonObject();
        }
        JsonObject result = model.deepCopy();
        if (model.has("parent")) {
            try {
                JsonElement value = model.get("parent");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
                String parent = resourceId(value.getAsString());
                Resource resource = lookup("models", parent, sourcePath);
                if (!resource.resolved()) issues.add(issue(resource, "models", "/parent"));
                else {
                    JsonObject inherited = inherit(parent, resource.model(), resource.path(), visiting, issues);
                    if (inherited.has("textures") && inherited.get("textures").isJsonObject()) {
                        JsonObject combined = inherited.getAsJsonObject("textures").deepCopy();
                        if (model.has("textures") && model.get("textures").isJsonObject())
                            model.getAsJsonObject("textures").entrySet().forEach(entry -> combined.add(entry.getKey(), entry.getValue().deepCopy()));
                        if (!model.has("textures") || model.get("textures").isJsonObject()) result.add("textures", combined);
                    }
                    if (!model.has("elements") && inherited.has("elements")) result.add("elements", inherited.get("elements").deepCopy());
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                issues.add(new Issue("INVALID_RESOURCE_NAME", "models", "/parent", null, "Parent must be a valid resource identifier", false));
            }
        }
        visiting.remove(id);
        return result;
    }

    private void validateTexture(String raw, JsonObject textures, String sourcePath, String pointer,
                                 boolean requireBound, List<Issue> issues) {
        String value = raw;
        Set<String> aliases = new LinkedHashSet<>();
        while (value.startsWith("#")) {
            String variable = value.substring(1);
            if (!aliases.add(variable)) {
                issues.add(new Issue("MODEL_TEXTURE_CYCLE", "textures", pointer, value, "Texture variables contain a cycle", false));
                return;
            }
            JsonElement binding = textures.get(variable);
            if (binding == null) {
                boolean parentUnverified = issues.stream().anyMatch(issue -> issue.kind().equals("models") && issue.unverified());
                if (requireBound && !parentUnverified) issues.add(new Issue("MODEL_TEXTURE_VARIABLE_MISSING", "textures", pointer, value,
                        "Texture variable has no binding in the complete parent chain: " + variable, false));
                return;
            }
            if (!binding.isJsonPrimitive() || !binding.getAsJsonPrimitive().isString()) return;
            value = binding.getAsString();
        }
        try {
            Resource resource = lookup("textures", value, sourcePath);
            if (!resource.resolved()) issues.add(issue(resource, "textures", pointer));
        } catch (IllegalArgumentException exception) {
            issues.add(new Issue("INVALID_RESOURCE_NAME", "textures", pointer, value, "Texture must be a valid resource identifier", false));
        }
    }

    private static Issue issue(Resource resource, String kind, String pointer) {
        return new Issue(resource.state().equals("unverified") ? "EXTERNAL_ASSET_REFERENCE_UNVERIFIED"
                : resource.state().equals("invalid") ? "INVALID_ASSET_DOCUMENT" : "MISSING_ASSET_REFERENCE", kind, pointer, resource.path(),
                resource.state().equals("unverified") ? "Resource index is unavailable, changed or unreadable; resolve dependencies and inspect again: " + resource.source()
                        : "Resource could not be resolved from " + resource.source() + ": " + resource.path(), resource.state().equals("unverified"));
    }

    private static String resourceRoot(String path) { int index = path.indexOf("assets/"); return index < 0 ? "" : path.substring(0, index); }
    private static String namespace(String path) {
        int index = path.indexOf("assets/");
        if (index < 0) return "minecraft";
        String tail = path.substring(index + 7); int slash = tail.indexOf('/');
        return slash < 0 ? "minecraft" : tail.substring(0, slash);
    }
    private static String pointer(String key) { return key.replace("~", "~0").replace("/", "~1"); }

    private static boolean containsPointer(JsonElement value, String pointer) {
        for (String encoded : pointer.substring(1).split("/")) {
            String part = encoded.replace("~1", "/").replace("~0", "~");
            if (value == null) return false;
            if (value.isJsonObject()) value = value.getAsJsonObject().get(part);
            else if (value.isJsonArray()) {
                try { value = value.getAsJsonArray().get(Integer.parseInt(part)); }
                catch (NumberFormatException | IndexOutOfBoundsException exception) { return false; }
            } else return false;
        }
        return value != null;
    }
}
