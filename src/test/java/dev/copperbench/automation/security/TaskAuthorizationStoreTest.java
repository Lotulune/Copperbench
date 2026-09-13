package dev.copperbench.automation.security;

import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskAuthorizationStoreTest {
    @TempDir Path temp;
    private static final Instant NOW = Instant.parse("2026-09-11T03:00:00Z");
    private TaskAuthorizationStore store(Instant time) { return new TaskAuthorizationStore(temp.resolve("private"), Clock.fixed(time, ZoneOffset.UTC)); }

    @Test void authoritySurvivesRestartAndIsLimitedToRootAndOperations() throws Exception {
        Path root = temp.resolve("task");
        var grant = store(NOW).issue(Actor.UI, true, "Trial", root, List.of("create", "build", "test"), 3600, false);
        String id = grant.get("id").getAsString();
        var restarted = store(NOW.plusSeconds(1));
        assertTrue(restarted.authorize(id, root.resolve("new-mod"), Operation.CREATE_WORKSPACE).allowed());
        assertTrue(restarted.authorize(id, root, Operation.RUN_GAMETEST).allowed());
        assertEquals("TASK_AUTHORIZATION_OPERATION_DENIED", restarted.authorize(id, root, Operation.RESTORE_RECOVERY_POINT).code());
        assertEquals("TASK_AUTHORIZATION_SCOPE_MISMATCH", restarted.authorize(id, root.resolve("../task-other"), Operation.BUILD_WORKSPACE).code());
        assertEquals("TASK_AUTHORIZATION_SCOPE_MISMATCH", restarted.authorize(id, temp, Operation.BUILD_WORKSPACE).code());
        assertEquals(1, restarted.list(root).size());
        assertTrue(restarted.list(temp.resolve("unrelated")).isEmpty());
        assertFalse(grant.toString().contains("signature"));
        assertFalse(grant.toString().contains("signing.key"));
    }

    @Test void expirationAndRevocationDenyEverySubsequentRequest() throws Exception {
        Path root = temp.resolve("task");
        String id = store(NOW).issue(Actor.UI, true, "Trial", root, List.of("edit"), 60, false).get("id").getAsString();
        assertTrue(store(NOW.plusSeconds(59)).authorize(id, root, Operation.CREATE_RECOVERY_POINT).allowed());
        assertEquals("TASK_AUTHORIZATION_EXPIRED", store(NOW.plusSeconds(60)).authorize(id, root, Operation.CREATE_RECOVERY_POINT).code());
        assertFalse(store(NOW.plusSeconds(60)).list(root).getFirst().get("active").getAsBoolean());
        assertThrows(TaskAuthorizationStore.AuthorizationException.class, () -> store(NOW).revoke(id, temp.resolve("other")));
        store(NOW.plusSeconds(30)).revoke(id, root);
        assertEquals("TASK_AUTHORIZATION_REVOKED", store(NOW.plusSeconds(31)).authorize(id, root, Operation.CREATE_RECOVERY_POINT).code());
        assertTrue(store(NOW).list(root).getFirst().get("revoked").getAsBoolean());
    }

    @Test void callerFlagsAndWorkspaceFilesCannotIssueOrForgeAuthority() throws Exception {
        Path root = temp.resolve("task");
        for (Actor actor : List.of(Actor.MCP, Actor.HEADLESS, Actor.SYSTEM)) {
            var error = assertThrows(TaskAuthorizationStore.AuthorizationException.class,
                    () -> store(NOW).issue(actor, true, "Forged", root, List.of("restore"), 3600, false));
            assertEquals("TASK_AUTHORIZATION_USER_ONLY", error.code());
        }
        assertThrows(TaskAuthorizationStore.AuthorizationException.class,
                () -> store(NOW).issue(Actor.UI, false, "Unconfirmed", root, List.of("edit"), 3600, false));
        String id = store(NOW).issue(Actor.UI, true, "Trial", root, List.of("test"), 3600, false).get("id").getAsString();
        Path file = temp.resolve("private/" + id + ".json");
        var envelope = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        envelope.getAsJsonObject("grant").getAsJsonArray("capabilities").add("restore");
        Files.writeString(file, envelope.toString());
        assertEquals("TASK_AUTHORIZATION_INVALID", store(NOW).authorize(id, root, Operation.RESTORE_RECOVERY_POINT).code());
        assertTrue(store(NOW).list(root).isEmpty());
        assertEquals("TASK_AUTHORIZATION_INVALID", store(NOW).authorize("../../other", root, Operation.BUILD_WORKSPACE).code());
    }

    @Test void dedicatedServerNeedsSeparateEulaAndUnsafeScopesCannotBeApproved() throws Exception {
        Path root = temp.resolve("task");
        var error = assertThrows(TaskAuthorizationStore.AuthorizationException.class,
                () -> store(NOW).issue(Actor.UI, true, "Server", root, List.of("run_server"), 3600, false));
        assertEquals("SERVER_EULA_APPROVAL_REQUIRED", error.code());
        String id = store(NOW).issue(Actor.UI, true, "Server", root, List.of("run_server", "test"), 3600, true).get("id").getAsString();
        assertTrue(store(NOW).authorize(id, root, Operation.RUN_SERVER).serverEulaAccepted());
        assertTrue(store(NOW).authorize(id, root, Operation.RUN_SERVER).allowed());
        assertThrows(java.io.IOException.class, () -> store(NOW).issue(Actor.UI, true, "Bad", root.getRoot(), List.of("edit"), 3600, false));
        assertThrows(java.io.IOException.class, () -> store(NOW).issue(Actor.UI, true, "Bad", Path.of("relative"), List.of("edit"), 3600, false));
        assertThrows(java.io.IOException.class, () -> store(NOW).issue(Actor.UI, true, "Bad", root, List.of("external_publish"), 3600, false));
        assertThrows(java.io.IOException.class, () -> store(NOW).issue(Actor.UI, true, "Bad", root, List.of("edit"), 86401, false));
    }

    @Test void linksCannotRedirectAnApprovedDirectoryOutsideItsScope() throws Exception {
        Path root = temp.resolve("task"), outside = temp.resolve("outside"), link = root.resolve("escape");
        Files.createDirectories(root); Files.createDirectories(outside);
        String id = store(NOW).issue(Actor.UI, true, "Trial", root, List.of("build"), 3600, false).get("id").getAsString();
        if (java.io.File.separatorChar == '\\') {
            Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), outside.toString()).redirectErrorStream(true).start();
            String detail = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.waitFor(), detail);
        } else Files.createSymbolicLink(link, outside);
        assertEquals("TASK_AUTHORIZATION_INVALID", store(NOW).authorize(id, link.resolve("mod"), Operation.BUILD_WORKSPACE).code());
        assertThrows(java.io.IOException.class, () -> store(NOW).issue(Actor.UI, true, "Escape", link, List.of("build"), 3600, false));
        assertThrows(java.io.IOException.class, () -> dev.copperbench.generator.WorkspaceExecutionSnapshot.capture(
                root, temp.resolve("snapshot"), java.util.UUID.randomUUID(), 0, Clock.systemUTC(), () -> false));
    }
}
