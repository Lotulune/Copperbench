package dev.copperbench.assets;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import net.mcreator.io.UserFolderManager;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipFile;

/** Read-only version-specific vanilla catalog. Absence of a local catalog is not proof of absence. */
final class LocalResourceIndex {
    record Resolution(String state, String source, String version) {}
    private final Set<String> resources;
    private final String version;
    private final String source;
    private final boolean complete;
    private final Path archive;
    private final String fingerprint;
    private final DependencyResourceIndex dependencies;

    LocalResourceIndex(Set<String> resources, String version, String source, boolean complete) {
        this(resources, version, source, complete, null, null, DependencyResourceIndex.unavailable("runtime_dependency_snapshot_unavailable"));
    }

    private LocalResourceIndex(Set<String> resources, String version, String source, boolean complete,
                               Path archive, String fingerprint, DependencyResourceIndex dependencies) {
        this.resources = Set.copyOf(resources); this.version = version; this.source = source; this.complete = complete;
        this.archive = archive; this.fingerprint = fingerprint;
        this.dependencies = dependencies;
    }

    static LocalResourceIndex discover(Path root) {
        String version = "unknown";
        DependencyResourceIndex dependencies = DependencyResourceIndex.unavailable("runtime_dependency_snapshot_unavailable");
        try (var files = Files.list(root)) {
            List<Path> workspaces = files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".mcreator")).toList();
            if (workspaces.size() != 1) return unavailable(version);
            var document = JsonParser.parseString(Files.readString(workspaces.getFirst())).getAsJsonObject();
            String generator = document.getAsJsonObject("workspaceSettings").get("currentGenerator").getAsString();
            if (!generator.matches("(?:fabric|neoforge)-(?:1\\.20\\.1|1\\.21\\.1|26\\.1\\.2|26\\.2)")) return unavailable(version);
            version = generator.substring(generator.indexOf('-') + 1);
            dependencies = DependencyResourceIndex.discover(root, generator);
            List<Path> homes = new ArrayList<>();
            homes.add(root.resolve(".gradle"));
            String configured = System.getenv("COPPERBENCH_GRADLE_USER_HOME");
            if (configured != null && !configured.isBlank()) homes.add(Path.of(configured));
            String gradleHome = System.getenv("GRADLE_USER_HOME");
            if (gradleHome != null && !gradleHome.isBlank()) homes.add(Path.of(gradleHome));
            homes.add(UserFolderManager.getGradleHome().toPath());
            List<Path> catalogs = new ArrayList<>();
            for (Path home : homes) {
                Path loom = home.resolve("caches/fabric-loom/" + version + "/minecraft-client.jar");
                Path neoform = home.resolve("caches/neoformruntime/artifacts/minecraft_" + version + "_client.jar");
                if (generator.startsWith("neoforge-")) { catalogs.add(neoform); catalogs.add(loom); }
                else { catalogs.add(loom); catalogs.add(neoform); }
            }
            for (Path client : catalogs) {
                if (!Files.isRegularFile(client)) continue;
                String fingerprint = dev.copperbench.generator.WorkspaceExecutionSnapshot.sha256(client);
                try (ZipFile zip = new ZipFile(client.toFile())) {
                    var versionEntry = zip.getEntry("version.json");
                    if (versionEntry == null) continue;
                    try (var input = zip.getInputStream(versionEntry)) {
                        var identity = JsonParser.parseString(new String(input.readNBytes(64 * 1024), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                        if (!version.equals(identity.get("id").getAsString())) continue;
                    }
                    Set<String> names = new HashSet<>();
                    zip.stream().filter(e -> !e.isDirectory() && e.getName().startsWith("assets/minecraft/"))
                            .forEach(e -> names.add(e.getName()));
                    // A server-only or truncated archive must not become a complete client resource catalog.
                    if (!names.contains("assets/minecraft/models/block/cube_all.json")
                            || !names.contains("assets/minecraft/models/item/generated.json")) continue;
                    if (!fingerprint.equals(dev.copperbench.generator.WorkspaceExecutionSnapshot.sha256(client))) continue;
                    return new LocalResourceIndex(names, version, client.getFileName() + " sha256="
                            + fingerprint, true, client, fingerprint, dependencies);
                } catch (IOException ignored) { /* Report unverified if no readable matching catalog exists. */ }
            }
        } catch (IOException | RuntimeException ignored) { /* No network or cache mutation on health queries. */ }
        return new LocalResourceIndex(Set.of(), version, "none", false, null, null, dependencies);
    }

    static LocalResourceIndex unavailable(String version) { return new LocalResourceIndex(Set.of(), version, "none", false); }

    /** Reads only the model selected from this exact catalog; never downloads or rewrites external resources. */
    JsonObject readModel(String resource) throws IOException {
        var dependency = dependencies.resolve(resource);
        if (dependency != null && dependency.state().equals("dependency_resolved")) return dependencies.readModel(resource);
        if (archive == null || !resources.contains(resource)) return null;
        if (!fingerprint.equals(dev.copperbench.generator.WorkspaceExecutionSnapshot.sha256(archive)))
            throw new IOException("Resource catalog changed while it was being inspected");
        byte[] bytes;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entry = zip.getEntry(resource);
            if (entry == null) throw new IOException("Resource disappeared from its catalog");
            try (var input = zip.getInputStream(entry)) { bytes = input.readNBytes(1024 * 1024 + 1); }
        }
        if (bytes.length > 1024 * 1024) throw new IOException("Model exceeds the inspection size limit");
        if (!fingerprint.equals(dev.copperbench.generator.WorkspaceExecutionSnapshot.sha256(archive)))
            throw new IOException("Resource catalog changed while it was being inspected");
        return JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
    }

    Resolution resolve(String target, String ownNamespace) {
        int offset = target.indexOf("assets/");
        String resource = offset < 0 ? target : target.substring(offset);
        if (dependencies.invalidatesPreviousContext()) return new Resolution("unverified", dependencies.reason(), version);
        var dependency = dependencies.resolve(resource);
        if (dependency != null) return dependency;
        if (resource.startsWith("assets/minecraft/") && complete)
            return new Resolution(resources.contains(resource) ? "vanilla_resolved" : "missing", source, version);
        if (resource.startsWith("assets/" + ownNamespace + "/") && !ownNamespace.equals("minecraft"))
            return new Resolution("missing", "workspace", version);
        if (!resource.startsWith("assets/minecraft/") && dependencies.available())
            return new Resolution("missing", "resolved_runtime_dependency_catalog", version);
        return new Resolution("unverified", dependencies.available() ? "external_catalog_unavailable" : dependencies.reason(), version);
    }

    boolean workspaceOverlap(String resource) { return dependencies.overlapsWorkspace(resource); }
    String unavailableDependencyContext() { return dependencies.invalidatesPreviousContext() ? dependencies.reason() : null; }
}
