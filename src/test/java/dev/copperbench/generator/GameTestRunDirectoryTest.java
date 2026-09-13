package dev.copperbench.generator;

import com.google.gson.JsonParser;
import dev.copperbench.platform.RuntimePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameTestRunDirectoryTest {
    @TempDir Path temp;
    private static final RuntimePlatform WINDOWS = RuntimePlatform.detect("Windows 11", "amd64");

    @Test void longWindowsTaskUsesPrivateCacheAndLeavesTraceableWorkspaceEvidence() throws Exception {
        Path root = temp.resolve("a".repeat(80)).resolve("b".repeat(40));
        Files.createDirectories(root);
        Files.writeString(root.resolve("native.java"), "native bytes");
        UUID taskId = UUID.randomUUID();
        Path target = GameTestRunDirectory.select(root, taskId, WINDOWS, temp.resolve("cache"));
        assertFalse(target.startsWith(root));
        assertTrue(target.resolve("gradle/wrapper/gradle-wrapper.jar").toString().length() < 240);
        var snapshot = WorkspaceExecutionSnapshot.capture(root, target, UUID.randomUUID(), 3, Clock.systemUTC(), () -> false);
        GameTestRunDirectory.record(root, taskId, snapshot);
        Path record = root.resolve(".copperbench/task-runs/run_gametest").resolve(taskId.toString()).resolve("execution-location.json");
        var location = JsonParser.parseString(Files.readString(record)).getAsJsonObject();
        assertTrue(location.get("shortPathFallback").getAsBoolean());
        assertEquals(target.toString(), location.get("executionRoot").getAsString());
        assertEquals(snapshot.sha256(), location.getAsJsonObject("sourceSnapshot").get("sha256").getAsString());
        assertEquals("native bytes", Files.readString(target.resolve("native.java")));
        assertEquals(snapshot.sha256(), WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
    }

    @Test void linuxKeepsItsExistingTaskLayoutEvenWithDeepDirectories() throws Exception {
        Path root = temp.resolve("long".repeat(35));
        Path selected = GameTestRunDirectory.select(root, UUID.randomUUID(), RuntimePlatform.detect("Linux", "amd64"), temp);
        assertTrue(selected.startsWith(root));
    }

    @Test void excessiveCacheDepthFailsBeforeCreatingAnything() {
        Path deep = temp.resolve("long".repeat(45));
        var failure = assertThrows(GameTestSupport.TestSetupException.class, () ->
                GameTestRunDirectory.select(deep, UUID.randomUUID(), WINDOWS, deep));
        assertEquals("GAMETEST_PATH_TOO_LONG", failure.code());
        assertFalse(Files.exists(deep));
    }

    @Test void cacheOverrideInsideWorkspaceCannotBecomeSnapshotInput() {
        Path root = temp.resolve("a".repeat(140));
        var failure = assertThrows(GameTestSupport.TestSetupException.class, () ->
                GameTestRunDirectory.select(root, UUID.randomUUID(), WINDOWS, root));
        assertEquals("GAMETEST_PATH_TOO_LONG", failure.code());
        assertTrue(failure.getMessage().contains("outside the workspace"));
        assertFalse(Files.exists(root));
    }
}
