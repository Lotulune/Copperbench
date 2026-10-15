/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;

/** Workspace-local presentation preferences and references, never completion or acceptance facts. */
public final class FirstCreationPreferences {
    public static final String GENERATOR_ID = "fabric-1.21.1";
    public static final int MAX_BYTES = 64 * 1024;
    private FirstCreationPreferences() {}

    public static void initialize(Path workspaceFile, String modId) throws IOException {
        JsonObject binding = new JsonObject();
        binding.addProperty("workspaceFile", workspaceFile.toAbsolutePath().normalize().toString());
        binding.addProperty("modId", modId);
        binding.addProperty("showOnOpen", true);
        binding.add("clients", new JsonArray());
        JsonObject record = record(checked(binding, workspaceFile));
        Path target = target(workspaceFile);
        Files.createDirectories(target.getParent());
        target(workspaceFile);
        Files.writeString(target, record.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    public static Optional<JsonObject> read(Path workspaceFile) throws IOException {
        Path target = target(workspaceFile);
        if (!Files.exists(target)) return Optional.empty();
        try {
            byte[] bytes;
            try (var input = Files.newInputStream(target)) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES) throw new IOException("First-creation references exceed 64 KiB; the existing file was preserved");
            JsonObject record = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!record.has("version") || !record.get("version").isJsonPrimitive()
                    || !record.getAsJsonPrimitive("version").isNumber() || !"1".equals(record.get("version").getAsString()) || !record.has("binding"))
                throw new IllegalArgumentException("Unsupported first-creation preference version");
            return Optional.of(checked(record.getAsJsonObject("binding"), workspaceFile));
        } catch (RuntimeException exception) {
            throw new IOException("First-creation references are invalid; the existing file was preserved", exception);
        }
    }

    public static synchronized void save(Path workspaceFile, JsonObject binding) throws IOException {
        // Refuse to replace corrupt data with an empty/default state after a failed read.
        read(workspaceFile);
        JsonObject record;
        try { record = record(checked(binding, workspaceFile)); }
        catch (RuntimeException exception) { throw new IOException("Invalid first-creation references", exception); }
        Path target = target(workspaceFile);
        Files.createDirectories(target.getParent());
        target(workspaceFile);
        String serialized = record.toString();
        if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("First-creation references exceed 64 KiB");
        Path temporary = Files.createTempFile(target.getParent(), "first-creation-", ".tmp");
        try {
            Files.writeString(temporary, serialized, StandardCharsets.UTF_8);
            target(workspaceFile);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static JsonObject record(JsonObject binding) {
        JsonObject result = new JsonObject(); result.addProperty("version", 1); result.add("binding", binding); return result;
    }

    private static Path target(Path workspaceFile) throws IOException {
        Path file = WorkspaceExecutionSnapshot.canonicalPath(workspaceFile);
        if (!file.getFileName().toString().endsWith(".mcreator") || !Files.isRegularFile(file))
            throw new IOException("The bound workspace file does not exist");
        Path directory = file.getParent().resolve(".copperbench");
        Path target = directory.resolve("first-creation.json");
        Path canonicalTarget = WorkspaceExecutionSnapshot.canonicalPath(target);
        if (!canonicalTarget.startsWith(file.getParent()) || !canonicalTarget.getParent().equals(directory))
            throw new IOException("First-creation preferences escaped the bound workspace");
        return target;
    }

    private static JsonObject checked(JsonObject value, Path workspaceFile) {
        String suppliedFile = string(value, "workspaceFile");
        if (!Path.of(suppliedFile).toAbsolutePath().normalize().equals(workspaceFile.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("The references belong to another workspace");
        String modId = string(value, "modId");
        if (!modId.matches("[a-z][a-z0-9_]{1,31}")) throw new IllegalArgumentException("Invalid mod ID reference");
        JsonObject clean = new JsonObject();
        clean.addProperty("workspaceFile", workspaceFile.toAbsolutePath().normalize().toString());
        clean.addProperty("modId", modId);
        if (value.has("workspaceId")) clean.addProperty("workspaceId", uuid(value, "workspaceId"));
        if (value.has("elementId")) clean.addProperty("elementId", uuid(value, "elementId"));
        if (!value.has("showOnOpen") || !value.get("showOnOpen").isJsonPrimitive()
                || !value.getAsJsonPrimitive("showOnOpen").isBoolean()) throw new IllegalArgumentException("Invalid display preference");
        clean.addProperty("showOnOpen", value.get("showOnOpen").getAsBoolean());
        if (value.has("build") && !value.get("build").isJsonNull()) clean.add("build", task(value.getAsJsonObject("build")));
        if (value.has("export") && !value.get("export").isJsonNull()) clean.add("export", task(value.getAsJsonObject("export")));
        JsonArray clients = new JsonArray();
        if (!value.has("clients") || !value.get("clients").isJsonArray()) throw new IllegalArgumentException("Invalid client task references");
        for (var client : value.getAsJsonArray("clients")) clients.add(task(client.getAsJsonObject()));
        clean.add("clients", clients);
        return clean;
    }

    private static JsonObject task(JsonObject value) {
        JsonObject clean = new JsonObject(); clean.addProperty("id", uuid(value, "id"));
        if (value.has("inputRevision")) {
            var revision = value.get("inputRevision");
            if (!revision.isJsonPrimitive() || !revision.getAsJsonPrimitive().isNumber()
                    || !revision.getAsString().matches("[0-9]+")) throw new IllegalArgumentException("Invalid input revision reference");
            clean.addProperty("inputRevision", revision.getAsLong());
        }
        if (value.has("output")) clean.addProperty("output", string(value, "output"));
        return clean;
    }

    private static String uuid(JsonObject object, String key) {
        String value = string(object, key);
        if (!UUID.fromString(value).toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Invalid UUID reference");
        return value;
    }

    private static String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString()
                || object.get(key).getAsString().isBlank()) throw new IllegalArgumentException("Missing reference: " + key);
        return object.get(key).getAsString();
    }
}
