/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exact owned delivery files, excluded from authored inputs only while their receipt hashes still match. */
final class WorkspaceDeliveryOutputs {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_FILES = 4096;
    private static final Set<String> PROTECTED_ROOTS = Set.of("src", "elements", "gradle", "buildscripts", "scripts", "lib", "libs", "plugins",
            "assets", "textures", "models", "resources", "config", ".mcreator", ".copperbench", ".git", ".gradle", ".idea", ".vscode",
            "node_modules", "run", "runs", "logs");
    private WorkspaceDeliveryOutputs() {}

    record Prepared(Path root, Path output, String previousSha256, boolean unownedBuildTarget) {
        Path publish(Path artifact) throws IOException { return publish(artifact, null); }

        Path publish(Path artifact, String declaredSha256) throws IOException {
            Path source = artifact.toAbsolutePath().normalize();
            WorkspaceExecutionSnapshot.rejectLinks(source);
            if (!source.startsWith(root.resolve("build")) || !Files.isRegularFile(source))
                throw new IOException("The export artifact is not a regular workspace build output");
            String sourceSha256 = WorkspaceExecutionSnapshot.sha256(source);
            if (declaredSha256 != null && !declaredSha256.equals(sourceSha256))
                throw new IOException("The build artifact no longer matches its archive-task receipt");
            // The archive task may already write the exact requested build path; no copy or overwrite is needed.
            if (source.equals(output)) {
                record(root, output, sourceSha256);
                return output;
            }
            if (unownedBuildTarget)
                throw new IOException("The existing build destination is not this task's declared artifact; its file was preserved");
            checkUnchanged();
            Path staging = root.resolve(".copperbench/delivery-staging");
            WorkspaceExecutionSnapshot.rejectLinks(staging); Files.createDirectories(staging);
            Path temporary = Files.createTempFile(staging, "delivery-", ".tmp");
            try {
                boolean bundle = output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")
                        && source.getFileName().toString().endsWith(".jar");
                if (bundle) {
                    try (ZipOutputStream archive = new ZipOutputStream(Files.newOutputStream(temporary))) {
                        archive.putNextEntry(new ZipEntry(source.getFileName().toString()));
                        Files.copy(source, archive); archive.closeEntry();
                        Path lock = root.resolve(".copperbench/generator-lock.json");
                        if (Files.isRegularFile(lock)) {
                            WorkspaceExecutionSnapshot.rejectLinks(lock);
                            archive.putNextEntry(new ZipEntry("generator-lock.json"));
                            Files.copy(lock, archive); archive.closeEntry();
                        }
                    }
                } else Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
                if (!sourceSha256.equals(WorkspaceExecutionSnapshot.sha256(source)))
                    throw new IOException("The build artifact changed while exporting");
                String exportedSha256 = WorkspaceExecutionSnapshot.sha256(temporary);
                if (!bundle && !sourceSha256.equals(exportedSha256)) throw new IOException("Exported artifact bytes do not match the build");
                checkUnchanged();
                if (previousSha256 == null) Files.move(temporary, output);
                else replace(temporary, output);
                if (!exportedSha256.equals(WorkspaceExecutionSnapshot.sha256(output)))
                    throw new IOException("The delivery file changed while publishing");
                record(root, output, exportedSha256);
                return output;
            } finally { Files.deleteIfExists(temporary); }
        }

        private void checkUnchanged() throws IOException {
            WorkspaceExecutionSnapshot.rejectLinks(output);
            if (previousSha256 == null) {
                if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new IOException("The export destination appeared after preparation; its file was preserved");
            } else if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)
                    || !previousSha256.equals(WorkspaceExecutionSnapshot.sha256(output)))
                throw new IOException("The existing delivery was modified externally; its file was preserved");
        }
    }

    static Prepared prepare(Path workspaceRoot, JsonObject payload) throws IOException {
        if (!payload.has("output") || !payload.get("output").isJsonPrimitive()
                || !payload.getAsJsonPrimitive("output").isString()) throw new IOException("Export output is required");
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path output = checkedOutput(root, payload.get("output").getAsString());
        Map<Path, String> owned = recorded(root);
        String previous = null; boolean unownedBuildTarget = false;
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) throw new IOException("The export destination is not a regular file");
            previous = owned.get(output);
            if (previous == null && output.startsWith(root.resolve("build"))) unownedBuildTarget = true;
            else if (previous == null || !previous.equals(WorkspaceExecutionSnapshot.sha256(output)))
                throw new IOException("The export destination is not an unchanged Copperbench delivery; choose a new path to preserve the existing file");
        }
        // Parent creation belongs to preparation, before the task starts observing authored input events.
        Files.createDirectories(output.getParent()); WorkspaceExecutionSnapshot.rejectLinks(output);
        return new Prepared(root, output, previous, unownedBuildTarget);
    }

    static Map<Path, String> recorded(Path workspaceRoot) throws IOException {
        Path root = workspaceRoot.toAbsolutePath().normalize(), receipt = receipt(root);
        if (!Files.exists(receipt, LinkOption.NOFOLLOW_LINKS)) return Map.of();
        WorkspaceExecutionSnapshot.rejectLinks(receipt);
        byte[] bytes;
        try (var input = Files.newInputStream(receipt)) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IOException("Delivery ownership receipt exceeds its limit");
        try {
            JsonObject value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"1.0".equals(value.get("schemaVersion").getAsString()) || value.getAsJsonArray("files").size() > MAX_FILES)
                throw new IOException("Delivery ownership receipt has an unsupported format");
            Map<Path, String> result = new LinkedHashMap<>();
            for (var raw : value.getAsJsonArray("files")) {
                JsonObject file = raw.getAsJsonObject(); Path target = checkedOutput(root, file.get("path").getAsString());
                String hash = file.get("sha256").getAsString();
                if (!hash.matches("[a-f0-9]{64}") || result.putIfAbsent(target, hash) != null)
                    throw new IOException("Delivery ownership receipt contains invalid or duplicate files");
            }
            return Map.copyOf(result);
        } catch (RuntimeException invalid) { throw new IOException("Delivery ownership receipt is invalid; its file was preserved", invalid); }
    }

    private static Path checkedOutput(Path root, String relative) throws IOException {
        String value = relative.replace('\\', '/');
        if (value.isBlank() || value.startsWith("/") || value.matches(".*[:\\p{Cntrl}].*")
                || java.util.Arrays.stream(value.split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))
                || !value.toLowerCase(Locale.ROOT).matches(".*\\.(jar|zip)")) throw new IOException("Use a workspace-relative .jar or .zip delivery path");
        Path output = root.resolve(value).normalize();
        if (!output.startsWith(root) || PROTECTED_ROOTS.contains(root.relativize(output).getName(0).toString().toLowerCase(Locale.ROOT)))
            throw new IOException("Delivery paths cannot replace authored sources, dependencies, workspace metadata or runtime files");
        for (Path component : root.relativize(output))
            if (Set.of(".git", ".gradle", ".copperbench", ".mcreator", "node_modules").contains(component.toString().toLowerCase(Locale.ROOT)))
                throw new IOException("Delivery paths cannot enter nested workspace metadata or dependency caches");
        WorkspaceExecutionSnapshot.rejectLinks(output);
        return output;
    }

    private static synchronized void record(Path root, Path output, String hash) throws IOException {
        Map<Path, String> owned = new LinkedHashMap<>(recorded(root)); owned.put(output, hash);
        if (owned.size() > MAX_FILES) throw new IOException("Delivery ownership receipt exceeds its file limit");
        JsonArray files = new JsonArray();
        owned.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            JsonObject file = new JsonObject(); file.addProperty("path", root.relativize(entry.getKey()).toString().replace('\\', '/'));
            file.addProperty("sha256", entry.getValue()); files.add(file);
        });
        JsonObject value = new JsonObject(); value.addProperty("schemaVersion", "1.0"); value.add("files", files);
        String text = value + "\n";
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Delivery ownership receipt exceeds its byte limit");
        Path receipt = receipt(root); Files.createDirectories(receipt.getParent()); WorkspaceExecutionSnapshot.rejectLinks(receipt);
        Path temporary = Files.createTempFile(receipt.getParent(), "delivery-outputs-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            WorkspaceExecutionSnapshot.rejectLinks(receipt); replace(temporary, receipt);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static Path receipt(Path root) { return root.resolve(".copperbench/delivery-outputs.json"); }

    private static void replace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
