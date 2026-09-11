package dev.copperbench.automation.security;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import dev.copperbench.platform.PrivatePathPermissions;
import net.mcreator.io.UserFolderManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persistent user-issued, root-scoped task authority. IDs supplement authentication; they do not replace it. */
public final class TaskAuthorizationStore {
    public static final Set<String> CAPABILITIES = Set.of("create", "edit", "build", "test", "run_client", "run_server", "restore");
    public static final List<String> DEFAULT_CAPABILITIES = List.of("create", "edit", "build", "test", "run_client");
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Object FILE_LOCK = new Object();
    private final Path directory;
    private final Clock clock;

    public TaskAuthorizationStore(Path directory, Clock clock) {
        this.directory = directory.toAbsolutePath().normalize(); this.clock = clock;
    }
    public static TaskAuthorizationStore productDefault(Clock clock) {
        return new TaskAuthorizationStore(UserFolderManager.getConfigFolder().toPath().resolve("task-authorizations"), clock);
    }

    public record Decision(boolean allowed, String code, boolean serverEulaAccepted) {}
    public static final class AuthorizationException extends IOException {
        private final String code;
        public AuthorizationException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }

    /** Call only at a trusted local UI boundary after displaying the scope to the user. */
    public JsonObject issue(Actor actor, boolean userApproved, String label, Path root, List<String> capabilities,
            long ttlSeconds, boolean serverEulaAccepted) throws IOException {
        if ((actor != Actor.UI && actor != Actor.LEGACY_UI) || !userApproved)
            throw new AuthorizationException("TASK_AUTHORIZATION_USER_ONLY", "Only the local user can issue task authority");
        if (label == null || label.isBlank() || label.length() > 120 || ttlSeconds < 60 || ttlSeconds > 86400
                || capabilities == null || capabilities.isEmpty() || !CAPABILITIES.containsAll(capabilities))
            throw new AuthorizationException("TASK_AUTHORIZATION_INVALID", "Provide a task label, known capabilities and a lifetime from 60 to 86400 seconds");
        Path scope = canonicalRoot(root);
        if (scope.getParent() == null) throw new AuthorizationException("TASK_AUTHORIZATION_INVALID", "An entire filesystem is not a task root");
        if (capabilities.contains("run_server") && !serverEulaAccepted)
            throw new AuthorizationException("SERVER_EULA_APPROVAL_REQUIRED", "Dedicated server authority requires separate Minecraft EULA acceptance");
        JsonObject grant = new JsonObject();
        grant.addProperty("schemaVersion", "1.0"); grant.addProperty("id", UUID.randomUUID().toString());
        grant.addProperty("label", label.strip()); grant.addProperty("root", scope.toString());
        JsonArray operations = new JsonArray(); capabilities.stream().distinct().sorted().forEach(operations::add);
        grant.add("capabilities", operations); grant.addProperty("issuedAt", clock.instant().toString());
        grant.addProperty("expiresAt", clock.instant().plusSeconds(ttlSeconds).toString());
        grant.addProperty("serverEulaAccepted", serverEulaAccepted && capabilities.contains("run_server"));
        grant.addProperty("revoked", false);
        synchronized (FILE_LOCK) { write(grant, true); }
        return projection(grant);
    }

    public Decision authorize(String id, Path target, Operation operation) {
        return authorize(id, target, capability(operation));
    }
    public Decision authorize(String id, Path target, String capability) {
        if (id == null || id.isBlank()) return new Decision(false, "TASK_AUTHORIZATION_REQUIRED", false);
        try {
            JsonObject grant = read(id);
            if (grant.get("revoked").getAsBoolean()) return new Decision(false, "TASK_AUTHORIZATION_REVOKED", false);
            if (!clock.instant().isBefore(Instant.parse(grant.get("expiresAt").getAsString())))
                return new Decision(false, "TASK_AUTHORIZATION_EXPIRED", false);
            Path scope = canonicalRoot(Path.of(grant.get("root").getAsString()));
            Path requested = canonicalRoot(target);
            if (!requested.startsWith(scope)) return new Decision(false, "TASK_AUTHORIZATION_SCOPE_MISMATCH", false);
            boolean allowed = capability != null && grant.getAsJsonArray("capabilities").asList().stream()
                    .anyMatch(value -> value.getAsString().equals(capability));
            return new Decision(allowed, allowed ? "ALLOWED" : "TASK_AUTHORIZATION_OPERATION_DENIED",
                    grant.get("serverEulaAccepted").getAsBoolean());
        } catch (Exception exception) {
            return new Decision(false, "TASK_AUTHORIZATION_INVALID", false);
        }
    }

    public List<JsonObject> list(Path target) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        WorkspaceExecutionSnapshot.rejectLinks(directory);
        Path path = target == null ? null : canonicalRoot(target);
        List<JsonObject> result = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).limit(1000).toList()) {
                try {
                    JsonObject grant = read(file.getFileName().toString().replace(".json", ""));
                    Path scope = canonicalRoot(Path.of(grant.get("root").getAsString()));
                    if (path == null || path.startsWith(scope)) result.add(projection(grant));
                } catch (IOException | RuntimeException ignored) { /* invalid authority is never advertised as usable */ }
            }
        }
        return List.copyOf(result);
    }

    /** Revocation can only reduce authority. A workspace client may revoke grants covering that workspace. */
    public JsonObject revoke(String id, Path target) throws IOException {
        synchronized (FILE_LOCK) {
            JsonObject grant = read(id);
            if (target != null && !canonicalRoot(target).startsWith(canonicalRoot(Path.of(grant.get("root").getAsString()))))
                throw new AuthorizationException("TASK_AUTHORIZATION_SCOPE_MISMATCH", "Authority belongs to another task root");
            grant.addProperty("revoked", true); grant.addProperty("revokedAt", clock.instant().toString());
            write(grant, false); return projection(grant);
        }
    }

    public static String capability(Operation operation) {
        return switch (operation) {
            case CREATE_WORKSPACE -> "create";
            case CREATE_MOD_ELEMENT, UPDATE_MOD_ELEMENT, DELETE_MOD_ELEMENT, UPDATE_PROCEDURE,
                    SET_MOD_ELEMENT_SOURCE_MANAGEMENT, CREATE_REGISTRY_ENTRY, UPDATE_REGISTRY_ENTRY,
                    DELETE_REGISTRY_ENTRY, RENAME_REGISTRY_ENTRY, APPLY_WORKSPACE_PLAN, IMPORT_ASSET,
                    IMPORT_ASSET_BATCH, MOVE_ASSET, CREATE_RECOVERY_POINT, PUBLISH_DATAGEN_OUTPUT, PREPARE_GAME_TESTS -> "edit";
            case BUILD_WORKSPACE, GENERATE_WORKSPACE -> "build";
            case VALIDATE_WORKSPACE, RUN_GAMETEST, RUN_DATAGEN -> "test";
            case RUN_CLIENT -> "run_client";
            case RUN_SERVER -> "run_server";
            case RESTORE_RECOVERY_POINT -> "restore";
            default -> null;
        };
    }

    private static Path canonicalRoot(Path root) throws IOException {
        if (root == null || !root.isAbsolute()) throw new IOException("Task paths must be absolute");
        Path path = root.toAbsolutePath().normalize();
        return WorkspaceExecutionSnapshot.canonicalPath(path);
    }

    private JsonObject read(String id) throws IOException {
        UUID.fromString(id);
        Path path = directory.resolve(id + ".json");
        WorkspaceExecutionSnapshot.rejectLinks(path);
        if (!Files.isRegularFile(path) || Files.size(path) > 64 * 1024) throw new IOException("Authority not found");
        JsonObject envelope = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        JsonObject grant = envelope.getAsJsonObject("grant");
        if (!id.equals(grant.get("id").getAsString()) || !"1.0".equals(grant.get("schemaVersion").getAsString()))
            throw new IOException("Invalid authority identity");
        byte[] expected = sign(grant, false);
        if (!MessageDigest.isEqual(expected, Base64.getDecoder().decode(envelope.get("signature").getAsString())))
            throw new IOException("Authority signature mismatch");
        return grant;
    }

    private void write(JsonObject grant, boolean create) throws IOException {
        WorkspaceExecutionSnapshot.rejectLinks(directory);
        PrivatePathPermissions.createPrivateDirectory(directory);
        JsonObject envelope = new JsonObject(); envelope.add("grant", grant);
        envelope.addProperty("signature", Base64.getEncoder().encodeToString(sign(grant, true)));
        Path target = directory.resolve(grant.get("id").getAsString() + ".json");
        WorkspaceExecutionSnapshot.rejectLinks(target);
        Path pending = directory.resolve("pending-" + UUID.randomUUID() + ".tmp");
        Files.writeString(pending, JSON.toJson(envelope), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        PrivatePathPermissions.makePrivateFile(pending);
        try {
            if (create) Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
            else Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            if (create) Files.move(pending, target); else Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
        }
        PrivatePathPermissions.makePrivateFile(target);
    }

    private byte[] sign(JsonObject grant, boolean mayCreateKey) throws IOException {
        Path keyPath = directory.resolve("signing.key"); WorkspaceExecutionSnapshot.rejectLinks(keyPath);
        if (!Files.exists(keyPath) && mayCreateKey) {
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            try { Files.write(keyPath, bytes, StandardOpenOption.CREATE_NEW); }
            catch (java.nio.file.FileAlreadyExistsException ignored) { }
            PrivatePathPermissions.makePrivateFile(keyPath);
        }
        if (!Files.isRegularFile(keyPath) || Files.size(keyPath) != 32) throw new IOException("Authority signing key unavailable");
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(Files.readAllBytes(keyPath), "HmacSHA256"));
            return mac.doFinal(JSON.toJson(grant).getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) { throw new IOException(exception); }
    }

    private JsonObject projection(JsonObject grant) {
        JsonObject value = grant.deepCopy();
        value.addProperty("active", !grant.get("revoked").getAsBoolean()
                && clock.instant().isBefore(Instant.parse(grant.get("expiresAt").getAsString())));
        return value;
    }
}
