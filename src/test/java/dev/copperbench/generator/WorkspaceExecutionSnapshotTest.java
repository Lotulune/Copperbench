package dev.copperbench.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceExecutionSnapshotTest {
    @TempDir Path temp;

    @Test void copiesNativeSourcesBuildScriptsTestsAndEntrypointsButExcludesWorldsAndCredentials() throws Exception {
        Path root = temp.resolve("project");
        String[] inputs = {"example.mcreator", "build.gradle", "settings.gradle", "gradlew", "gradle/wrapper/gradle-wrapper.jar",
                "src/main/java/custom/Native.java", "src/gametest/java/custom/Behavior.java", "copperbench-tests.json",
                "src/main/resources/fabric.mod.json", "src/main/resources/assets/example/lang/zh_cn.json",
                "src/main/resources/data/run/recipe.json"};
        for (String input : inputs) write(root.resolve(input), "bytes:" + input);
        for (String excluded : new String[]{".git", ".env", ".copperbench/mcp-connection.json", ".gradle/cache",
                "build/libs/old.jar", "run/saves/user-world/level.dat", "runs/server/world/level.dat"})
            write(root.resolve(excluded), "private");
        Path destination = root.resolve(".copperbench/task-runs/run_gametest/one/workspace");
        var snapshot = WorkspaceExecutionSnapshot.capture(root, destination, UUID.randomUUID(), 7, Clock.systemUTC(), () -> false);
        assertEquals(inputs.length, snapshot.files().size());
        for (String input : inputs) assertEquals(Files.readString(root.resolve(input)), Files.readString(destination.resolve(input)));
        assertFalse(Files.exists(destination.resolve("run")));
        assertFalse(Files.exists(destination.resolve(".copperbench")));
        assertFalse(Files.exists(destination.resolve(".env")));
        assertTrue(Files.readString(snapshot.manifestPath()).contains(snapshot.sha256()));
        var second = WorkspaceExecutionSnapshot.capture(root, root.resolve(".copperbench/task-runs/two/workspace"),
                UUID.randomUUID(), 7, Clock.systemUTC(), () -> false);
        assertEquals(snapshot.sha256(), second.sha256());
    }

    @Test void detectsConcurrentNativeEditAndCannotPublishAMixedSnapshot() throws Exception {
        Path root = temp.resolve("project");
        Path first = root.resolve("a.java"), second = root.resolve("b.java");
        write(first, "original-a"); write(second, "original-b");
        Path target = root.resolve(".copperbench/task-runs/one/workspace");
        var failure = assertThrows(WorkspaceExecutionSnapshot.SnapshotException.class, () ->
                WorkspaceExecutionSnapshot.capture(root, target, UUID.randomUUID(), 0, Clock.systemUTC(), () -> {
                    if (Files.exists(target.resolve("a.java"))) {
                        try { Files.writeString(first, "newer-a"); } catch (Exception e) { throw new RuntimeException(e); }
                    }
                    return false;
                }));
        assertEquals("WORKSPACE_SNAPSHOT_CHANGED", failure.code());
        assertFalse(Files.exists(target.resolveSibling("source-manifest.json")));
        assertEquals("newer-a", Files.readString(first));
    }

    @Test void refusesExistingDestinationAndCancellation() throws Exception {
        Path root = temp.resolve("project"); write(root.resolve("build.gradle"), "native");
        Path target = temp.resolve("target"); Files.createDirectories(target);
        assertThrows(WorkspaceExecutionSnapshot.SnapshotException.class, () ->
                WorkspaceExecutionSnapshot.capture(root, target, UUID.randomUUID(), 0, Clock.systemUTC(), () -> false));
        var failure = assertThrows(WorkspaceExecutionSnapshot.SnapshotException.class, () ->
                WorkspaceExecutionSnapshot.capture(root, temp.resolve("cancelled"), UUID.randomUUID(), 0, Clock.systemUTC(), () -> true));
        assertEquals("WORKSPACE_SNAPSHOT_CANCELLED", failure.code());
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent()); Files.writeString(path, content);
    }
}
