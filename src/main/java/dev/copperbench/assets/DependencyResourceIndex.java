package dev.copperbench.assets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.generator.ResourceDependencyCapture;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Indexes only artifacts observed in the workspace's resolved runtime configurations. */
final class DependencyResourceIndex {
    record Archive(Path path, String sha256, String component, String version) {
        String source() { return "runtime dependency " + component + " sha256=" + sha256; }
    }
    private final Map<String, List<Archive>> entries;
    private final Set<String> uncertain;
    private final boolean available;
    private final String reason;

    private DependencyResourceIndex(Map<String, List<Archive>> entries, Set<String> uncertain, boolean available, String reason) {
        this.entries = Map.copyOf(entries); this.uncertain = Set.copyOf(uncertain); this.available = available; this.reason = reason;
    }

    static DependencyResourceIndex unavailable(String reason) { return new DependencyResourceIndex(Map.of(), Set.of(), false, reason); }

    static DependencyResourceIndex discover(Path root, String generator) {
        Path manifest = root.resolve(ResourceDependencyCapture.INDEX);
        if (!Files.isRegularFile(manifest)) return unavailable("runtime_dependency_snapshot_unavailable");
        try {
            WorkspaceExecutionSnapshot.rejectLinks(manifest);
            if (Files.size(manifest) > 8 * 1024 * 1024) return unavailable("runtime_dependency_snapshot_invalid");
            String snapshotText = Files.readString(manifest);
            JsonObject snapshot = JsonParser.parseString(snapshotText).getAsJsonObject();
            if (!"available".equals(snapshot.get("state").getAsString())) return unavailable("runtime_dependency_snapshot_incomplete");
            if (!generator.equals(snapshot.get("generator").getAsString()) || !snapshot.get("inputSha256").getAsString()
                    .equals(WorkspaceExecutionSnapshot.fingerprint(root, () -> Thread.currentThread().isInterrupted())))
                return unavailable("runtime_dependency_snapshot_stale");
            var artifacts = snapshot.getAsJsonArray("artifacts");
            if (artifacts == null || artifacts.size() > 4096) return unavailable("runtime_dependency_snapshot_invalid");
            Map<String, List<Archive>> entries = new HashMap<>();
            Set<String> uncertain = new HashSet<>();
            for (var raw : artifacts) {
                JsonObject artifact = raw.getAsJsonObject();
                String configuration = artifact.get("configuration").getAsString();
                if (!(configuration.endsWith(":runtimeClasspath") || configuration.endsWith(":clientRuntimeClasspath")))
                    return unavailable("runtime_dependency_configuration_invalid");
                Path path = Path.of(artifact.get("path").getAsString()).toRealPath();
                String hash = artifact.get("sha256").getAsString();
                if (Files.size(path) > 512L * 1024 * 1024 || !hash.matches("[0-9a-f]{64}") || !path.getFileName().toString().endsWith(".jar")
                        || !hash.equals(WorkspaceExecutionSnapshot.sha256(path)))
                    return unavailable("runtime_dependency_artifact_changed");
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    boolean fabric = generator.startsWith("fabric-");
                    if (fabric && zip.getEntry("fabric.mod.json") == null) continue;
                    if (!fabric && zip.getEntry("META-INF/neoforge.mods.toml") == null && zip.getEntry("META-INF/mods.toml") == null) continue;
                    String component = artifact.get("component").getAsString();
                    String version = null;
                    if (component.matches("[A-Za-z0-9_.-]+:[A-Za-z0-9_.+-]+:[A-Za-z0-9_.+-]+")) version = component.substring(component.lastIndexOf(':') + 1);
                    else component = "local_mod_archive";
                    JsonObject metadata = null;
                    if (fabric) {
                        metadata = readJson(zip, "fabric.mod.json");
                        if (metadata.has("environment") && "server".equals(metadata.get("environment").getAsString())) continue;
                        if (metadata.has("version") && metadata.get("version").isJsonPrimitive()) version = metadata.get("version").getAsString();
                    }
                    Archive archive = new Archive(path, hash, component, version);
                    if (zip.size() > 200_000) return unavailable("runtime_dependency_archive_limit");
                    boolean overlays = false;
                    if (zip.getEntry("pack.mcmeta") != null) {
                        JsonObject pack = readJson(zip, "pack.mcmeta");
                        overlays = pack.has("overlays") || pack.has("neoforge:overlays");
                    }
                    final boolean hasOverlays = overlays;
                    zip.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> {
                        String name = entry.getName();
                        if (resourcePath(name)) {
                            entries.computeIfAbsent(name, ignored -> new ArrayList<>()).add(archive);
                            if (hasOverlays) uncertain.add(name);
                        } else {
                            int index = name.indexOf("/assets/");
                            if (index >= 0 && resourcePath(name.substring(index + 1))) uncertain.add(name.substring(index + 1));
                        }
                    });
                    // Nested mods undergo Loader version selection. Record possible resources without guessing activation/order.
                    if (metadata != null && metadata.has("jars")) for (var nested : metadata.getAsJsonArray("jars")) {
                        String nestedPath = nested.getAsJsonObject().get("file").getAsString();
                        collectNestedResources(zip, nestedPath, uncertain);
                    }
                    if (!fabric && zip.getEntry("META-INF/jarjar/metadata.json") != null) {
                        var jarjar = readJson(zip, "META-INF/jarjar/metadata.json");
                        if (jarjar.has("jars")) for (var nested : jarjar.getAsJsonArray("jars"))
                            collectNestedResources(zip, nested.getAsJsonObject().get("path").getAsString(), uncertain);
                    }
                }
                if (!hash.equals(WorkspaceExecutionSnapshot.sha256(path))) return unavailable("runtime_dependency_artifact_changed");
            }
            if (!snapshotText.equals(Files.readString(manifest))) return unavailable("runtime_dependency_snapshot_changed");
            return new DependencyResourceIndex(entries, uncertain, true, "resolved_runtime_classpath");
        } catch (IOException | RuntimeException exception) {
            return unavailable("runtime_dependency_snapshot_unreadable");
        }
    }

    private static JsonObject readJson(ZipFile zip, String path) throws IOException {
        var entry = zip.getEntry(path);
        if (entry == null) throw new IOException("Missing archive metadata");
        try (var input = zip.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024) throw new IOException("Archive metadata exceeds size limit");
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void collectNestedResources(ZipFile zip, String path, Set<String> uncertain) throws IOException {
        var entry = zip.getEntry(path);
        if (entry == null || entry.getSize() > 64 * 1024 * 1024) throw new IOException("Nested archive unavailable or too large");
        try (var input = new ZipInputStream(zip.getInputStream(entry))) {
            long total = 0; int count = 0;
            byte[] buffer = new byte[8192];
            for (var child = input.getNextEntry(); child != null; child = input.getNextEntry()) {
                if (++count > 100_000) throw new IOException("Nested archive entry limit");
                String name = child.getName();
                if (resourcePath(name)) uncertain.add(name);
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > 128 * 1024 * 1024) throw new IOException("Nested archive expansion limit");
                }
            }
        }
    }

    private static boolean resourcePath(String value) {
        return value.matches("assets/[a-z0-9_.-]+/(?:models/.+\\.json|textures/.+\\.png)")
                && !value.contains("/../") && !value.contains("/./") && !value.contains("//");
    }

    LocalResourceIndex.Resolution resolve(String resource) {
        List<Archive> candidates = entries.getOrDefault(resource, List.of());
        if (candidates.size() > 1 || uncertain.contains(resource))
            return new LocalResourceIndex.Resolution("unverified", "runtime_resource_pack_order_or_activation_unverified", null);
        if (candidates.size() == 1) {
            Archive selected = candidates.getFirst();
            return new LocalResourceIndex.Resolution("dependency_resolved", selected.source(), selected.version());
        }
        return null;
    }

    boolean overlapsWorkspace(String resource) { return entries.containsKey(resource) || uncertain.contains(resource); }
    boolean available() { return available; }
    String reason() { return reason; }
    boolean invalidatesPreviousContext() { return !available && !reason.equals("runtime_dependency_snapshot_unavailable"); }

    JsonObject readModel(String resource) throws IOException {
        var candidates = entries.getOrDefault(resource, List.of());
        if (candidates.size() != 1 || uncertain.contains(resource)) return null;
        Archive selected = candidates.getFirst();
        if (!selected.sha256().equals(WorkspaceExecutionSnapshot.sha256(selected.path()))) throw new IOException("Dependency changed");
        JsonObject model;
        try (ZipFile zip = new ZipFile(selected.path().toFile())) { model = readJson(zip, resource); }
        if (!selected.sha256().equals(WorkspaceExecutionSnapshot.sha256(selected.path()))) throw new IOException("Dependency changed");
        return model;
    }
}
