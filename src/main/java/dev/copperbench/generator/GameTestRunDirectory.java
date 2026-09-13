package dev.copperbench.generator;

import com.google.gson.JsonObject;
import dev.copperbench.platform.RuntimePlatform;
import dev.copperbench.platform.UserDataPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Keeps the Windows wrapper below its path limit while retaining a workspace-local evidence pointer. */
final class GameTestRunDirectory {
    private static final int WINDOWS_WRAPPER_BUDGET = 240;

    private GameTestRunDirectory() {}

    static Path select(Path workspace, UUID taskId) throws IOException {
        return select(workspace, taskId, RuntimePlatform.current(), UserDataPaths.current().cache());
    }

    static Path select(Path workspace, UUID taskId, RuntimePlatform platform, Path cache) throws IOException {
        Path local = local(workspace, taskId).resolve("workspace");
        if (platform.operatingSystem() != RuntimePlatform.OperatingSystem.WINDOWS || wrapperLength(local) < WINDOWS_WRAPPER_BUDGET)
            return local;
        Path shortened = cache.toAbsolutePath().normalize().resolve("task-runs").resolve(taskId.toString()).resolve("workspace");
        if (shortened.startsWith(workspace.toAbsolutePath().normalize()))
            throw new GameTestSupport.TestSetupException("GAMETEST_PATH_TOO_LONG",
                    "The short task cache must be outside the workspace. Choose a shorter workspace or an external Copperbench data directory.");
        if (wrapperLength(shortened) >= WINDOWS_WRAPPER_BUDGET)
            throw new GameTestSupport.TestSetupException("GAMETEST_PATH_TOO_LONG",
                    "Both the workspace and Copperbench task cache paths are too deep for the Windows Gradle wrapper. Use a shorter workspace or Copperbench data directory.");
        WorkspaceExecutionSnapshot.rejectLinks(shortened);
        return shortened;
    }

    static void record(Path workspace, UUID taskId, WorkspaceExecutionSnapshot.Snapshot snapshot) throws IOException {
        Path record = local(workspace, taskId).resolve("execution-location.json");
        WorkspaceExecutionSnapshot.rejectLinks(record);
        Files.createDirectories(record.getParent());
        JsonObject location = new JsonObject();
        location.addProperty("schemaVersion", "1.0");
        location.addProperty("taskId", taskId.toString());
        location.addProperty("executionRoot", snapshot.root().toString());
        location.addProperty("shortPathFallback", !snapshot.root().equals(record.resolveSibling("workspace")));
        location.add("sourceSnapshot", snapshot.projection());
        Files.writeString(record, location.toString() + "\n", StandardOpenOption.CREATE_NEW);
    }

    private static Path local(Path workspace, UUID taskId) {
        return workspace.toAbsolutePath().normalize().resolve(".copperbench/task-runs/run_gametest").resolve(taskId.toString());
    }

    private static int wrapperLength(Path root) {
        return root.resolve("gradle/wrapper/gradle-wrapper.jar").toString().length();
    }
}
