/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.release;

import com.google.gson.*;
import dev.copperbench.ProductIdentity;
import dev.copperbench.installer.*;
import net.mcreator.io.UserFolderManager;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Explicit, user-initiated stable-release checks and component downloads. */
public final class ProductUpdates {
    private static final String REPOSITORY = "https://github.com/Lotulune/Copperbench/releases/download/";
    public record Release(String version, Map<String, URI> assets) {}
    public record Available(Release release, UpdateManifest manifest, List<UpdateManifest.Part> changed, Path target, Properties options) {
        public long downloadBytes() { return changed.stream().mapToLong(UpdateManifest.Part::bytes).sum(); }
    }
    private ProductUpdates() {}
    public static String platform() { return System.getProperty("os.name").startsWith("Windows") ? "windows-x64" : "linux-x64"; }
    public static Optional<Release> selectRelease(JsonArray releases, String platform, String current) throws IOException {
        Release latest = null;
        for (var value : releases) {
            JsonObject release = value.getAsJsonObject();
            if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean()) continue;
            String tag = release.get("tag_name").getAsString();
            String pattern = platform.equals("windows-x64") ? "v[0-9]+\\.[0-9]+\\.[0-9]+" : "v[0-9]+\\.[0-9]+\\.[0-9]+-linux-stable";
            if (!tag.matches(pattern)) continue;
            String version = tag.substring(1).replace("-linux-stable", "");
            if (UpdateManifest.compareVersions(version, current) <= 0 || latest != null && UpdateManifest.compareVersions(version, latest.version()) <= 0) continue;
            Map<String, URI> assets = new HashMap<>();
            for (var asset : release.getAsJsonArray("assets")) {
                String name = asset.getAsJsonObject().get("name").getAsString();
                URI uri = URI.create(asset.getAsJsonObject().get("browser_download_url").getAsString());
                if (!uri.toString().startsWith(REPOSITORY + tag + "/") || uri.getUserInfo() != null || uri.getFragment() != null)
                    throw new IOException("Release contains an unexpected download address");
                assets.put(name, uri);
            }
            latest = new Release(version, Map.copyOf(assets));
        }
        return Optional.ofNullable(latest);
    }
    public static Optional<Release> check() throws IOException {
        try (InputStream in = UpdatePreparation.open(URI.create("https://api.github.com/repos/Lotulune/Copperbench/releases?per_page=30"))) {
            byte[] bytes = in.readNBytes(4 * 1024 * 1024 + 1);
            if (bytes.length > 4 * 1024 * 1024) throw new IOException("Release response is too large");
            return selectRelease(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonArray(), platform(), ProductIdentity.VERSION);
        } catch (RuntimeException error) { throw new IOException("Could not read the update response", error); }
    }
    public static Available inspect(Release release, BooleanSupplier cancelled) throws IOException {
        Path target = installationRoot();
        Properties options = UpdatePreparation.installationOptions(target);
        URI descriptor = release.assets().get("copperbench-update-" + platform() + ".properties");
        if (descriptor == null) throw new IOException("This release does not contain an in-app update package yet.");
        UpdateManifest manifest;
        try (InputStream in = UpdatePreparation.open(descriptor)) { manifest = UpdateManifest.read(in); }
        if (!manifest.version().equals(release.version()) || !manifest.platform().equals(platform())) throw new IOException("The update package belongs to a different version or platform");
        for (var part : manifest.parts()) if (!release.assets().containsKey(part.name())) throw new IOException("The release is missing an update component: " + part.name());
        return new Available(release, manifest, UpdatePreparation.changedParts(manifest, target, cancelled), target, options);
    }
    public static Path prepare(Available available, BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        Path cache = cache(); Files.createDirectories(cache); InstallEngine.ensureNoLinks(cache);
        long size = available.manifest().parts().stream().flatMap(p -> p.entries().stream()).mapToLong(InstallEngine.Entry::size).sum();
        if (Files.getFileStore(cache).getUsableSpace() < size + available.downloadBytes() + 64L * 1024 * 1024)
            throw new IOException("Not enough space to prepare the update");
        Path scratch = Files.createTempDirectory(cache, "update-");
        try {
            UpdatePreparation.prepare(available.manifest(), available.changed(), available.target(), scratch,
                    (name, file, bytes) -> UpdatePreparation.download(available.release().assets().get(name), file, bytes, cancelled, progress), cancelled, ignored -> {});
            Properties request = new Properties(); request.putAll(available.options());
            request.setProperty("version", available.release().version()); request.setProperty("target", available.target().toString());
            request.setProperty("parentPid", Long.toString(ProcessHandle.current().pid()));
            Path file = scratch.resolve("request.properties");
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { request.store(writer, "Copperbench prepared update"); }
            return file;
        } catch (IOException | RuntimeException error) { try { removeScratch(scratch); } catch (IOException cleanup) { error.addSuppressed(cleanup); } throw error; }
    }
    public static void launch(Path request) throws IOException {
        Path root = request.getParent();
        Path java = root.resolve("payload/jdk/bin/" + (platform().equals("windows-x64") ? "javaw.exe" : "java"));
        Process process = new ProcessBuilder(java.toString(), "-cp", root.resolve("payload/copperbench-setup.jar").toString(),
                "dev.copperbench.installer.UpdateInstaller", request.toString()).directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(root.resolve("update.log").toFile()).start();
        Files.writeString(root.resolve("worker.pid"), Long.toString(process.pid()), StandardCharsets.UTF_8);
    }
    public static Path installationRoot() throws IOException {
        try {
            Path code = Path.of(ProductUpdates.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path root = Files.isRegularFile(code) && code.toString().endsWith(".exe") ? code.getParent()
                    : Files.isRegularFile(code) && code.getParent().getFileName().toString().equals("lib") ? code.getParent().getParent() : null;
            if (root == null || !Files.isRegularFile(root.resolve("copperbench-setup.jar"))) throw new IOException("Use an installed Copperbench build to apply updates.");
            return root.toAbsolutePath().normalize();
        } catch (java.net.URISyntaxException error) { throw new IOException("Could not locate the installation", error); }
    }
    private static Path cache() { return UserFolderManager.getFileFromUserFolder("updates").toPath().toAbsolutePath().normalize(); }
    public static void discard(Path request) throws IOException { removeScratch(request.toAbsolutePath().normalize().getParent()); }
    private static void removeScratch(Path path) throws IOException {
        if (!path.getParent().equals(cache()) || !path.getFileName().toString().startsWith("update-")) throw new IOException("Invalid update scratch location");
        InstallEngine.ensureNoLinks(path);
        try (var paths = Files.walk(path)) { for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
    }
    public static void cleanupFinished() {
        Thread.ofVirtual().name("update-cleanup").start(() -> {
            try {
                if (!Files.isDirectory(cache())) return;
                try (var paths = Files.list(cache())) {
                    for (Path path : paths.filter(p -> p.getFileName().toString().startsWith("update-")).toList()) {
                        Path receipt = path.resolve("finished.properties");
                        if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)) continue;
                        Properties result = new Properties(); try (Reader reader = Files.newBufferedReader(receipt)) { result.load(reader); }
                        Optional<ProcessHandle> worker = ProcessHandle.of(Long.parseLong(result.getProperty("pid")));
                        if (worker.isPresent()) worker.get().onExit().get(2, java.util.concurrent.TimeUnit.MINUTES);
                        Files.copy(receipt, cache().resolve("last-result.properties"), StandardCopyOption.REPLACE_EXISTING);
                        removeScratch(path);
                    }
                }
            } catch (Exception error) { org.apache.logging.log4j.LogManager.getLogger(ProductUpdates.class).warn("Could not clean completed update files", error); }
        });
    }
}
