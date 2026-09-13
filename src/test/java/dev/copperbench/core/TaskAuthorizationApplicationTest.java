package dev.copperbench.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.automation.security.TaskAuthorizationStore;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskAuthorizationApplicationTest {
    @TempDir Path temp;
    private final Clock clock = Clock.systemUTC();
    private final UUID workspaceId = UUID.randomUUID();
    private Path root() { return temp.resolve("workspace"); }
    private TaskAuthorizationStore authorizations() { return new TaskAuthorizationStore(temp.resolve("private"), clock); }
    private WorkspaceState state() {
        JsonObject generator = new JsonObject(); generator.addProperty("id", "fabric-1.21.1"); generator.addProperty("state", "ready");
        return new WorkspaceState(workspaceId, "Test mod", "mod", 0, false, generator, new JsonObject(), List.of());
    }
    private WorkspaceApplicationService service() {
        RevisionedWorkspaceStore store = new RevisionedWorkspaceStore(); store.register(state());
        return new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID),
                WorkspaceMutationGateway.noOp(), null, null, ignored -> root(), clock, UUID::randomUUID, authorizations());
    }
    private Command command(Operation operation, JsonObject payload) {
        payload.addProperty("clientMutationId", UUID.randomUUID().toString());
        return Command.of(UUID.randomUUID(), workspaceId, 0, operation, payload);
    }
    private static RequestContext context(Actor actor) { return new RequestContext(actor, PermissionProfile.WORKSPACE); }

    @Test void onlyLocalUiCanIssueAndStaleRevisionsDoNotCreateAuthority() throws Exception {
        var service = service();
        JsonObject payload = new JsonObject(); payload.addProperty("label", "Development"); payload.addProperty("root", root().toString());
        payload.addProperty("ttlSeconds", 3600); payload.addProperty("userApproved", true);
        JsonArray caps = new JsonArray(); caps.add("edit"); caps.add("build"); payload.add("capabilities", caps);
        assertEquals("rejected", service.execute(command(Operation.CREATE_TASK_AUTHORIZATION, payload), context(Actor.MCP)).result().status());
        assertTrue(authorizations().list(root()).isEmpty());
        var stale = Command.of(UUID.randomUUID(), workspaceId, 5, Operation.CREATE_TASK_AUTHORIZATION, payload);
        var rejected = service.execute(stale, context(Actor.UI)).result();
        assertEquals("rejected", rejected.status());
        assertNotNull(rejected.conflict());
        assertTrue(authorizations().list(root()).isEmpty());
        assertEquals("completed", service.execute(command(Operation.CREATE_TASK_AUTHORIZATION, payload), context(Actor.UI)).result().status());
        assertEquals(1, authorizations().list(root()).size());
    }

    @Test void delegatedServerWorksAcrossEntriesButRevokedGrantAndReadOnlyProfileDeny() throws Exception {
        String id = authorizations().issue(Actor.UI, true, "Server trial", root(), List.of("run_server"), 3600, true).get("id").getAsString();
        JsonObject payload = new JsonObject(); payload.addProperty("taskAuthorizationId", id); payload.addProperty("scope", "workspace");
        var service = service();
        assertEquals("accepted", service.execute(command(Operation.RUN_SERVER, payload), context(Actor.MCP)).result().status());
        assertEquals("accepted", service().execute(command(Operation.RUN_SERVER, payload), context(Actor.HEADLESS)).result().status());
        assertEquals("rejected", service.execute(command(Operation.RUN_SERVER, payload), new RequestContext(Actor.MCP, PermissionProfile.READ_ONLY)).result().status());
        assertEquals("rejected", service.execute(command(Operation.BUILD_WORKSPACE, payload), context(Actor.MCP)).result().status());
        JsonObject revoke = new JsonObject(); revoke.addProperty("authorizationId", id);
        assertEquals("completed", service.execute(command(Operation.REVOKE_TASK_AUTHORIZATION, revoke), context(Actor.MCP)).result().status());
        assertEquals("TASK_AUTHORIZATION_REVOKED", service().execute(command(Operation.RUN_SERVER, payload), context(Actor.HEADLESS)).result().diagnostics().getFirst().code());
        JsonObject forged = new JsonObject(); forged.addProperty("userApproved", true); forged.addProperty("eulaAccepted", true);
        assertEquals("rejected", service.execute(command(Operation.RUN_SERVER, forged), context(Actor.HEADLESS)).result().status());
    }

    @Test void delegatedRestoreActuallyRestoresAndStillCreatesSafetyHistory() throws Exception {
        Files.createDirectories(root()); Files.writeString(root().resolve("workspace.mcreator"), "{}");
        Files.writeString(root().resolve("native.java"), "before");
        var history = JGitLocalHistoryService.open(root(), clock);
        var point = history.createRecoveryPoint(new dev.copperbench.history.RecoveryPointRequest("Before", Actor.UI, ""));
        Files.writeString(root().resolve("native.java"), "after");
        RevisionedWorkspaceStore store = new RevisionedWorkspaceStore(); store.register(state());
        var service = new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID),
                WorkspaceMutationGateway.noOp(), history, ignored -> state(), ignored -> root(), clock, UUID::randomUUID, authorizations());
        String id = authorizations().issue(Actor.UI, true, "Restore trial", root(), List.of("restore"), 3600, false).get("id").getAsString();
        JsonObject payload = new JsonObject(); payload.addProperty("taskAuthorizationId", id); payload.addProperty("recoveryPointId", point.id());
        var result = service.execute(command(Operation.RESTORE_RECOVERY_POINT, payload), context(Actor.HEADLESS)).result();
        assertEquals("committed", result.status(), result.toString());
        assertEquals("before", Files.readString(root().resolve("native.java")));
        assertEquals(1, result.newRevision());
        assertNotNull(result.recoveryPointId());
    }
}
