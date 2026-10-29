package dev.copperbench.assets;

import com.google.gson.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only static appearance. All bytes come from the selected workspace or its verified local resource catalogs. */
public final class ModelPreviewService {
    private static final int MAX_MODEL = 256 * 1024, MAX_IMAGE = 2 * 1024 * 1024, MAX_TEXTURES = 32;
    private final AssetWorkspaceService workspace;
    private final List<AssetDescriptor> assets;
    private final List<String> javaModelKeys;
    private LocalResourceIndex external;
    private MinecraftModelResolver resolver;
    private final Map<String, byte[]> inline = new HashMap<>();

    public ModelPreviewService(Path root, List<AssetDescriptor> assets) {
        this(root, assets, List.of());
    }
    public ModelPreviewService(Path root, List<AssetDescriptor> assets, List<String> javaModelKeys) {
        workspace = new AssetWorkspaceService(root); this.assets = assets; this.javaModelKeys = List.copyOf(javaModelKeys);
    }

    public JsonObject asset(AssetDescriptor asset, JsonObject document) throws IOException {
        if (asset.relativePath().toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
            return completeMesh(BbmodelPreview.parse(document, asset.relativePath(), this), asset.relativePath());
        JsonObject model = document.deepCopy();
        if (asset.relativePath().startsWith("models/")) applyMapping(model, asset.relativePath(), "default", null, "block");
        return scene(model, asset.relativePath());
    }

    public JsonObject asset(AssetDescriptor asset, byte[] content) throws IOException {
        String path = asset.relativePath(), lower = path.toLowerCase(Locale.ROOT);
        String source = new String(content, StandardCharsets.UTF_8);
        if (lower.endsWith(".obj")) return completeMesh(ObjModelPreview.parse(source, path, this, Map.of()), path);
        if (lower.endsWith(".java")) return javaModel(source, path, null);
        return asset(asset, JsonParser.parseString(source).getAsJsonObject());
    }

    public JsonObject element(String type, String name, String namespace, JsonObject values) throws IOException {
        if (type.equals("livingentity")) {
            String selected = string(values, "mobModelName", "");
            AssetDescriptor asset = javaAsset(selected);
            if (asset == null) return unavailable("model_missing");
            String skin = texture(string(values, "mobModelTexture", ""), namespace, "entities");
            return javaModel(new String(read(asset, 2 * 1024 * 1024), StandardCharsets.UTF_8), asset.relativePath(), skin.isBlank() ? null : skin);
        }
        if (!Set.of("block", "item", "tool", "rangeditem", "food", "plant").contains(type)) return unavailable("element_type_unsupported");
        String section = type.equals("block") || type.equals("plant") ? "block" : "item";
        String source = "src/main/resources/assets/" + namespace + "/models/" + section + "/" + name + ".json";
        String bound = string(values, "modelResource", "");
        if (!bound.isBlank()) return scene(parent(MinecraftModelResolver.resourceId(bound)), source);
        int render = values.has("renderType") && !values.get("renderType").isJsonNull() ? values.get("renderType").getAsInt() : type.equals("plant") ? 12 : section.equals("block") ? 10 : 0;
        boolean custom = render == (section.equals("block") ? 2 : 1);
        String texture = texture(string(values, "texture", ""), namespace, section);
        if (values.has("textureBase64") && !string(values, "textureBase64", "").isBlank()) {
            String encoded = values.get("textureBase64").getAsString();
            if (encoded.length() > MAX_IMAGE * 4 / 3 + 8) throw new IOException("Texture exceeds the preview limit");
            inline.put("@inline", Base64.getDecoder().decode(encoded)); texture = "@inline";
        }
        if (custom) {
            String[] selected = string(values, "customModelName", "").split(":", 2);
            String path = "models/" + selected[0].trim() + ".json";
            AssetDescriptor asset = find(path);
            if (asset == null) return unavailable("model_missing");
            JsonObject model = readModel(asset);
            JsonObject bindings = model.has("textures") && model.get("textures").isJsonObject() ? model.getAsJsonObject("textures") : new JsonObject();
            bindings.addProperty("all", texture); model.add("textures", bindings);
            applyMapping(model, path, selected.length == 2 ? selected[1].trim() : "default", namespace, section);
            return scene(model, source);
        }
        if (render == (section.equals("block") ? 3 : 2)) {
            String[] selected = string(values, "customModelName", "").split(":", 2);
            AssetDescriptor asset = find("models/" + selected[0].trim() + ".obj");
            if (asset == null) return unavailable("model_missing");
            JsonObject mapping = new JsonObject(); applyMapping(mapping, asset.relativePath(), selected.length == 2 ? selected[1].trim() : "default", namespace, section);
            Map<String, String> overrides = new HashMap<>();
            if (mapping.has("textures")) mapping.getAsJsonObject("textures").entrySet().forEach(e -> overrides.put(e.getKey(), e.getValue().getAsString()));
            return completeMesh(ObjModelPreview.parse(new String(read(asset, 2 * 1024 * 1024), StandardCharsets.UTF_8), asset.relativePath(), this, overrides), source);
        }
        if (section.equals("item") && render == 3) {
            AssetDescriptor asset = javaAsset(string(values, "customModelName", ""));
            if (asset == null) return unavailable("model_missing");
            return javaModel(new String(read(asset, 2 * 1024 * 1024), StandardCharsets.UTF_8), asset.relativePath(), texture.isBlank() ? null : texture);
        }
        if (section.equals("item")) {
            if (render != 0) return unavailable("model_format_unsupported");
            JsonObject model = parent("minecraft:item/generated"); JsonObject textures = new JsonObject();
            textures.addProperty("layer0", texture); model.add("textures", textures); return scene(model, source);
        }
        String base = string(values, "blockBase", "");
        String modelId;
        if (!base.isBlank() && !base.equals("Default")) {
            modelId = switch (base) {
                case "Slab" -> "slab"; case "Stairs" -> "stairs"; case "Leaves" -> "leaves";
                case "Fence" -> "fence_post"; case "Wall" -> "template_wall_post"; case "TrapDoor" -> "template_orientable_trapdoor_bottom";
                case "Pane" -> "template_glass_pane_post"; case "Door" -> "door_bottom_left"; case "FenceGate" -> "template_fence_gate";
                case "PressurePlate" -> "pressure_plate_up"; case "Button" -> "button";
                default -> null;
            };
            if (modelId == null) return unavailable("model_format_unsupported");
        } else {
            modelId = switch (render) {
                case 10 -> "cube"; case 11, 4 -> "cube_all"; case 110 -> "leaves";
                case 12, 120 -> "cross"; case 13 -> "crop"; case 14 -> "grass_block";
                case 15, 150 -> "flower_pot_cross"; default -> null;
            };
            if (modelId == null) return unavailable("model_format_unsupported");
        }
        JsonObject model = parent("minecraft:block/" + modelId), textures = new JsonObject();
        for (String key : List.of("all", "cross", "crop", "plant", "texture", "particle", "side", "bottom", "end")) textures.addProperty(key, texture);
        for (var face : Map.of("down", "texture", "up", "textureTop", "north", "textureFront", "east", "textureLeft", "south", "textureBack", "west", "textureRight").entrySet()) {
            String raw = string(values, face.getValue(), ""); textures.addProperty(face.getKey(), raw.isBlank() || face.getValue().equals("texture") ? texture : texture(raw, namespace, "block"));
        }
        textures.add("top", textures.get("up").deepCopy());
        if (!base.isBlank()) textures.add("side", textures.get("north").deepCopy());
        textures.add("front", textures.get("north").deepCopy()); textures.add("overlay", textures.get("east").deepCopy());
        model.add("textures", textures);
        if (type.equals("plant") && "double".equals(string(values, "plantType", "")) && Set.of("cross", "crop").contains(modelId)) {
            JsonArray elements = new JsonArray();
            for (int half = 0; half < 2; half++) for (JsonElement raw : PreviewModels.template("minecraft:block/" + modelId).getAsJsonArray("elements")) {
                JsonObject part = raw.getAsJsonObject().deepCopy();
                for (String coordinate : List.of("from", "to")) part.getAsJsonArray(coordinate).set(1, new JsonPrimitive(part.getAsJsonArray(coordinate).get(1).getAsDouble() + half * 16));
                if (part.has("rotation")) part.getAsJsonObject("rotation").getAsJsonArray("origin").set(1, new JsonPrimitive(8 + half * 16));
                for (var face : part.getAsJsonObject("faces").entrySet()) face.getValue().getAsJsonObject().addProperty("texture", half == 0 ? "#bottom" : "#top");
                elements.add(part);
            }
            String bottom = string(values, "textureBottom", "");
            textures.addProperty("bottom", bottom.isBlank() ? texture : texture(bottom, namespace, "block")); textures.addProperty("top", texture);
            model.remove("parent"); model.add("elements", elements);
        }
        return scene(model, source);
    }

    private void initializeResolver() {
        if (resolver != null) return;
        external = LocalResourceIndex.discover(workspace.workspaceRoot());
        resolver = new MinecraftModelResolver(workspace.workspaceRoot(), external, assets);
    }

    private JsonObject scene(JsonObject document, String source) throws IOException {
        JsonObject model;
        try { model = inherit(document, source, new LinkedHashSet<>()); }
        catch (MissingModel error) { return unavailable(error.getMessage()); }
        if (model.has("loader") || model.has("minecraft:geometry")) return unavailable("model_format_unsupported");
        JsonArray elements = model.has("elements") ? model.getAsJsonArray("elements") : new JsonArray();
        if (elements.size() > 512) return unavailable("model_too_complex");
        boolean generated = elements.isEmpty() && model.has("previewGenerated");
        if (elements.isEmpty() && !generated) return unavailable("model_geometry_missing");
        JsonObject scene = new JsonObject(); scene.addProperty("kind", generated ? "item" : "model");
        JsonObject bindings = model.has("textures") ? model.getAsJsonObject("textures") : new JsonObject();
        JsonObject images = new JsonObject(); JsonArray missing = new JsonArray(), layers = new JsonArray();
        LinkedHashSet<String> needed = new LinkedHashSet<>();
        if (generated) {
            for (int i = 0; i < 5; i++) if (bindings.has("layer" + i)) { String key = resolveTexture("#layer" + i, bindings); needed.add(key); layers.add(key); }
            if (layers.isEmpty()) return unavailable("texture_missing");
        } else {
            for (JsonElement raw : elements) {
                JsonObject element = raw.getAsJsonObject();
                if (!element.has("faces")) continue;
                for (var face : element.getAsJsonObject("faces").entrySet()) {
                    JsonObject value = face.getValue().getAsJsonObject();
                    String key = resolveTexture(string(value, "texture", ""), bindings); needed.add(key); value.addProperty("texture", key);
                }
            }
        }
        if (needed.size() > MAX_TEXTURES) return unavailable("model_too_complex");
        int totalBytes = 0;
        for (String key : needed) {
            JsonObject image = image(key, source);
            if (image == null || image.has("reason")) { missing.add(key); continue; }
            totalBytes += image.get("size").getAsInt();
            if (totalBytes > 8 * 1024 * 1024) return unavailable("model_too_complex");
            images.add(key, image);
        }
        scene.add("elements", elements); scene.add("textures", images); scene.add("layers", layers); scene.add("missingTextures", missing);
        if (model.has("previewRotationX")) scene.add("rotationX", model.get("previewRotationX"));
        scene.addProperty("fingerprint", hash(scene.toString().getBytes(StandardCharsets.UTF_8)));
        return scene;
    }

    private JsonObject inherit(JsonObject document, String source, Set<String> visiting) throws IOException {
        JsonObject model = document.deepCopy();
        if (!model.has("parent")) return model;
        String id = MinecraftModelResolver.resourceId(model.get("parent").getAsString());
        if (!visiting.add(id) || visiting.size() > 32) throw new MissingModel("model_parent_cycle");
        initializeResolver();
        var resource = resolver.lookup("models", id, source);
        JsonObject parent = resource.resolved() ? resource.model() : null;
        // Standard static templates can be inspected even before the first Gradle/client download.
        if (parent == null && external.unavailableDependencyContext() == null
                && !resource.source().contains("pack_order")) parent = PreviewModels.template(id);
        if (id.equals("minecraft:builtin/generated")) parent = PreviewModels.template(id);
        if (parent == null) throw new MissingModel("model_parent_missing");
        parent = inherit(parent, resource.path() == null ? source : resource.path(), visiting);
        JsonObject bindings = parent.has("textures") ? parent.getAsJsonObject("textures").deepCopy() : new JsonObject();
        if (model.has("textures")) model.getAsJsonObject("textures").entrySet().forEach(e -> bindings.add(e.getKey(), e.getValue().deepCopy()));
        model.add("textures", bindings);
        for (String property : List.of("elements", "previewGenerated", "previewRotationX", "loader")) if (!model.has(property) && parent.has(property)) model.add(property, parent.get(property).deepCopy());
        visiting.remove(id); return model;
    }

    private String resolveTexture(String raw, JsonObject bindings) throws IOException {
        Set<String> aliases = new HashSet<>(); String value = raw;
        while (value.startsWith("#")) {
            if (!aliases.add(value)) throw new IOException("Model texture variables contain a cycle");
            JsonElement binding = bindings.get(value.substring(1));
            if (binding == null) return value;
            value = binding.getAsString();
        }
        return value;
    }

    private JsonObject image(String raw, String source) throws IOException {
        byte[] bytes; String path = raw, catalogPath = null;
        if (inline.containsKey(raw)) bytes = inline.get(raw);
        else if (raw.startsWith("@file/")) {
            path = raw.substring(6); AssetDescriptor descriptor = find(path);
            if (descriptor == null) return null;
            bytes = read(descriptor, MAX_IMAGE);
        }
        else {
            if (raw.isBlank() || raw.startsWith("#")) return null;
            initializeResolver();
            var resource = resolver.lookup("textures", raw, source);
            if (!resource.resolved()) return null;
            path = resource.path();
            if (resource.assetId() != null) {
                AssetDescriptor descriptor = assets.stream().filter(a -> a.id().equals(resource.assetId())).findFirst().orElseThrow();
                bytes = read(descriptor, MAX_IMAGE);
            } else { bytes = external.readTexture(path, MAX_IMAGE); catalogPath = path; }
        }
        if (bytes == null) return null;
        if (bytes.length > MAX_IMAGE) throw new IOException("Texture exceeds the preview limit");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Texture image could not be decoded");
            var reader = readers.next();
            try {
                reader.setInput(input, true); int width = reader.getWidth(0), height = reader.getHeight(0);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (width < 1 || height < 1 || width > 2048 || height > 2048 || !Set.of("png", "jpeg", "jpg").contains(format)) throw new IOException("Texture dimensions or format are unsupported");
                JsonObject result = new JsonObject(); result.addProperty("assetId", AssetDescriptor.stableIdForPath(path));
                result.addProperty("relativePath", path); result.addProperty("sha256", hash(bytes)); result.addProperty("size", bytes.length);
                result.addProperty("mediaType", format.equals("png") ? "image/png" : "image/jpeg");
                result.addProperty("base64", Base64.getEncoder().encodeToString(bytes)); result.addProperty("width", width); result.addProperty("height", height);
                AssetDescriptor metadata = find(path + ".mcmeta");
                byte[] animation = metadata != null ? read(metadata, 16 * 1024) : catalogPath != null ? external.readTexture(catalogPath + ".mcmeta", 16 * 1024) : null;
                if (animation != null) {
                    JsonObject meta = JsonParser.parseString(new String(animation, StandardCharsets.UTF_8)).getAsJsonObject();
                    if (meta.has("animation")) {
                        JsonObject info = meta.getAsJsonObject("animation");
                        int w = info.has("width") ? info.get("width").getAsInt() : info.has("height") ? width : Math.min(width, height);
                        int h = info.has("height") ? info.get("height").getAsInt() : info.has("width") ? height : Math.min(width, height);
                        int index = 0;
                        if (info.has("frames") && !info.getAsJsonArray("frames").isEmpty()) {
                            JsonElement first = info.getAsJsonArray("frames").get(0); index = first.isJsonObject() ? first.getAsJsonObject().get("index").getAsInt() : first.getAsInt();
                        }
                        if (w < 1 || h < 1 || width % w != 0 || height % h != 0 || index < 0 || index >= (width / w) * (height / h)) throw new IOException("Invalid texture animation frame");
                        JsonObject frame = new JsonObject(); frame.addProperty("x", index % (width / w) * w); frame.addProperty("y", index / (width / w) * h); frame.addProperty("width", w); frame.addProperty("height", h);
                        result.add("frame", frame);
                    }
                }
                return result;
            } finally { reader.dispose(); }
        }
    }

    private void applyMapping(JsonObject model, String path, String variation, String namespace, String section) throws IOException {
        AssetDescriptor mapping = find(path + ".textures");
        if (mapping == null) return;
        JsonObject mappings = JsonParser.parseString(new String(read(mapping, MAX_MODEL), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("mappings");
        if (mappings == null) return;
        JsonObject selected = mappings.has(variation) ? mappings.getAsJsonObject(variation) : mappings.getAsJsonObject("default");
        if (selected == null || !selected.has("map")) return;
        JsonObject bindings = model.has("textures") ? model.getAsJsonObject("textures") : new JsonObject();
        for (var entry : selected.getAsJsonObject("map").entrySet()) {
            if (namespace == null && !entry.getValue().getAsString().contains(":")) continue;
            bindings.addProperty(entry.getKey(), texture(entry.getValue().getAsString(), namespace, section));
        }
        model.add("textures", bindings);
    }
    private AssetDescriptor find(String path) { return assets.stream().filter(a -> a.relativePath().equals(path)).findFirst().orElse(null); }

    private AssetDescriptor javaAsset(String name) throws IOException {
        if (name.isBlank()) return null;
        if (!javaModelKeys.isEmpty()) {
            for (String key : javaModelKeys) { AssetDescriptor file = find("models/" + (key.equals("legacy") ? "" : key + "/") + name + ".java"); if (file != null) return file; }
            return null;
        }
        if (name.endsWith(".java")) return find(name.startsWith("models/") ? name : "models/" + name);
        AssetDescriptor direct = find("models/" + name + ".java");
        if (direct != null) return direct;
        List<AssetDescriptor> candidates = assets.stream().filter(a -> a.relativePath().startsWith("models/") && a.relativePath().endsWith("/" + name + ".java")).toList();
        if (candidates.size() > 1) throw new IOException("The generator's Java model version is unavailable. Select the model file in Assets and Models to preview it.");
        return candidates.isEmpty() ? null : candidates.getFirst();
    }
    private JsonObject javaModel(String code, String path, String texture) throws IOException {
        try { return completeMesh(JavaModelPreview.parse(code, texture), path); }
        catch (JavaModelPreview.UnsupportedGeometry error) {
            JsonObject result = unavailable("java_geometry_unsupported"); result.addProperty("detail", error.getMessage()); return result;
        }
    }
    private JsonObject completeMesh(JsonObject scene, String source) throws IOException {
        JsonObject images = new JsonObject(); JsonArray missing = new JsonArray(); Set<String> needed = new LinkedHashSet<>();
        boolean untextured = false;
        for (JsonElement raw : scene.getAsJsonArray("meshes")) {
            JsonObject mesh = raw.getAsJsonObject();
            if (mesh.has("texture")) needed.add(mesh.get("texture").getAsString()); else untextured = true;
        }
        if (needed.size() > MAX_TEXTURES) return unavailable("model_too_complex");
        int size = 0;
        for (String key : needed) {
            JsonObject image = image(key, source);
            if (image == null) { missing.add(key); continue; }
            size += image.get("size").getAsInt(); if (size > 8 * 1024 * 1024) return unavailable("model_too_complex");
            images.add(key, image);
        }
        scene.add("textures", images); scene.add("missingTextures", missing); scene.addProperty("untextured", untextured);
        scene.addProperty("fingerprint", hash(scene.toString().getBytes(StandardCharsets.UTF_8))); return scene;
    }

    /** Texture references never authorize reads outside the indexed workspace or network requests. */
    String textureReference(String source, String raw) throws IOException {
        if (raw == null || raw.isBlank()) return null;
        if (raw.startsWith("data:")) {
            int comma = raw.indexOf(',');
            if (comma < 0 || !Set.of("data:image/png;base64", "data:image/jpeg;base64").contains(raw.substring(0, comma)) || raw.length() > 2_800_000)
                throw new IOException("Unsupported embedded texture");
            byte[] bytes = Base64.getDecoder().decode(raw.substring(comma + 1));
            if (bytes.length > MAX_IMAGE) throw new IOException("Texture exceeds the preview limit");
            String key = "preview/embedded-" + hash(bytes) + ".png"; inline.put(key, bytes); return key;
        }
        if (raw.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && !raw.matches("^[a-zA-Z]:[/\\\\].*")) return MinecraftModelResolver.resourceId(raw);
        AssetDescriptor file = referenced(source, raw); return file == null ? null : "@file/" + file.relativePath();
    }
    AssetDescriptor referenced(String source, String reference) {
        try {
            if (reference == null || reference.contains("://")) return null;
            Path root = workspace.workspaceRoot(), raw = Path.of(reference.replace('\\', '/'));
            Path candidate = raw.isAbsolute() ? raw.normalize() : root.resolve(source).getParent().resolve(raw).normalize();
            if (!candidate.startsWith(root)) return null;
            return find(root.relativize(candidate).toString().replace('\\', '/'));
        } catch (InvalidPathException error) { return null; }
    }
    byte[] readReferenced(AssetDescriptor asset, int limit) throws IOException { return read(asset, limit); }
    private JsonObject readModel(AssetDescriptor asset) throws IOException { return JsonParser.parseString(new String(read(asset, MAX_MODEL), StandardCharsets.UTF_8)).getAsJsonObject(); }
    private byte[] read(AssetDescriptor asset, int limit) throws IOException {
        if (asset.size() > limit) throw new IOException("Asset exceeds the preview limit");
        Path file = workspace.resolveAuthorizedPath(asset.relativePath()); byte[] bytes;
        try (var in = Files.newInputStream(file)) { bytes = in.readNBytes(limit + 1); }
        if (bytes.length > limit || bytes.length != asset.size() || !hash(bytes).equals(asset.sha256())) throw new IOException("Asset changed while preparing its preview");
        return bytes;
    }
    private static String texture(String raw, String namespace, String section) {
        if (raw.isBlank()) return "";
        String id = raw.contains(":") ? raw : namespace + ":" + raw;
        int colon = id.indexOf(':'); String path = id.substring(colon + 1).replaceFirst("\\.png$", "");
        if (!path.startsWith("block/") && !path.startsWith("item/") && !path.startsWith("entity/") && !path.startsWith("entities/")) path = section + "/" + path;
        return MinecraftModelResolver.resourceId(id.substring(0, colon + 1) + path);
    }
    public static JsonObject unavailable(String reason) { JsonObject result = new JsonObject(); result.addProperty("kind", "unavailable"); result.addProperty("reason", reason); return result; }
    private static JsonObject parent(String id) { JsonObject model = new JsonObject(); model.addProperty("parent", id); return model; }
    private static String string(JsonObject object, String name, String fallback) { JsonElement value = object.get(name); return value == null || value.isJsonNull() ? fallback : value.getAsString(); }
    private static String hash(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); } }
    private static final class MissingModel extends IOException { MissingModel(String reason) { super(reason); } }
}
