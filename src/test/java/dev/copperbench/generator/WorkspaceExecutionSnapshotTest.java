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

    @Test void linkChecksStillRejectJunctionsAboveAndInsideAWalkedWorkspace() throws Exception {
        Path deep = temp.resolve("a/b/c/d/e/f/g/h");
        write(deep.resolve("Source.java"), "class Source {}");
        WorkspaceExecutionSnapshot.rejectLinks(deep.resolve("Source.java"));
        WorkspaceExecutionSnapshot.rejectLinks(deep.resolve("missing/child.json"));
        assertEquals(1, WorkspaceExecutionSnapshot.observeInputIdentity(temp.resolve("a"), () -> false).fileCount());
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"),
                "directory junctions need Windows; symbolic links need extra rights there");
        Path outside = temp.resolve("outside");
        write(outside.resolve("secret.txt"), "outside the workspace");
        Path junction = temp.resolve("a/b/linked");
        Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", junction.toString(), outside.toString())
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes()));
        var below = assertThrows(WorkspaceExecutionSnapshot.SnapshotException.class,
                () -> WorkspaceExecutionSnapshot.rejectLinks(junction.resolve("secret.txt")));
        assertEquals("WORKSPACE_SNAPSHOT_UNSAFE_PATH", below.code());
        assertTrue(below.getMessage().contains(junction.resolve("secret.txt").toString()), below.getMessage());
        assertThrows(WorkspaceExecutionSnapshot.SnapshotException.class,
                () -> WorkspaceExecutionSnapshot.rejectLinks(junction.resolve("new-file.json")));
        assertThrows(WorkspaceExecutionSnapshot.SnapshotException.class,
                () -> WorkspaceExecutionSnapshot.observeInputIdentity(temp.resolve("a"), () -> false));
    }

    @Test void rejectsNewerInputsBeforeReadingLargeContents() throws Exception {
        Path root = temp.resolve("query");
        Path input = root.resolve("reference.bin");
        write(input, "before");
        String fingerprint = WorkspaceExecutionSnapshot.fingerprint(root, () -> false);
        var capturedAt = Files.getLastModifiedTime(input);
        // A sparse file exceeds the content-hashing limit. A stale query must reject it from metadata.
        Path large = root.resolve("later.bin");
        try (var file = Files.newByteChannel(large, java.util.Set.of(java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.SPARSE))) {
            file.position(3L * 1024 * 1024 * 1024);
            file.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
        }
        Files.setLastModifiedTime(large, java.nio.file.attribute.FileTime.fromMillis(capturedAt.toMillis() + 1000));
        assertFalse(WorkspaceExecutionSnapshot.matchesFingerprint(root, fingerprint, capturedAt, () -> false));
    }

    @Test void matchingTimestampsStillRequireFullContentVerificationAndDetectRemovedInputs() throws Exception {
        Path root = temp.resolve("query");
        Path input = root.resolve("build.gradle");
        write(input, "before");
        String fingerprint = WorkspaceExecutionSnapshot.fingerprint(root, () -> false);
        var capturedAt = Files.getLastModifiedTime(input);
        assertTrue(WorkspaceExecutionSnapshot.matchesFingerprint(root, fingerprint, capturedAt, () -> false));
        Files.writeString(input, "edited");
        Files.setLastModifiedTime(input, capturedAt);
        assertFalse(WorkspaceExecutionSnapshot.matchesFingerprint(root, fingerprint, capturedAt, () -> false));
        Files.delete(input);
        assertFalse(WorkspaceExecutionSnapshot.matchesFingerprint(root, fingerprint, capturedAt, () -> false));
    }

    @Test void newerRuntimeFilesDoNotInvalidateInputsAndCancellationIsHonored() throws Exception {
        Path root = temp.resolve("query");
        Path input = root.resolve("build.gradle");
        write(input, "before");
        String fingerprint = WorkspaceExecutionSnapshot.fingerprint(root, () -> false);
        var capturedAt = Files.getLastModifiedTime(input);
        Path runtime = root.resolve(".copperbench/task-runs/result.json");
        write(runtime, "runtime");
        Files.setLastModifiedTime(runtime, java.nio.file.attribute.FileTime.fromMillis(capturedAt.toMillis() + 1000));
        assertTrue(WorkspaceExecutionSnapshot.matchesFingerprint(root, fingerprint, capturedAt, () -> false));
        assertThrows(WorkspaceExecutionSnapshot.SnapshotException.class,
                () -> WorkspaceExecutionSnapshot.matchesFingerprint(root, fingerprint, capturedAt, () -> true));
    }

    @Test void copiesNativeSourcesBuildScriptsTestsAndEntrypointsButExcludesWorldsAndCredentials() throws Exception {
        Path root = temp.resolve("project");
        String[] inputs = {"example.mcreator", "build.gradle", "settings.gradle", "gradlew", "gradle/wrapper/gradle-wrapper.jar",
                "src/main/java/custom/Native.java", "src/gametest/java/custom/Behavior.java", "copperbench-tests.json",
                "src/main/resources/fabric.mod.json", "src/main/resources/assets/example/lang/zh_cn.json",
                "src/main/resources/data/run/recipe.json", "buildSrc/src/main/groovy/Convention.groovy"};
        for (String input : inputs) write(root.resolve(input), "bytes:" + input);
        for (String excluded : new String[]{".git", ".env", ".copperbench/mcp-connection.json", ".gradle/cache",
                ".kotlin/sessions/build.salive", "buildSrc/build/libs/buildSrc.jar", "buildSrc/.gradle/cache",
                "build-logic/build/libs/build-logic.jar", "build/libs/old.jar", "run/saves/user-world/level.dat", "runs/server/world/level.dat"})
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

    @Test void onlyTheReservedScratchLocationIsOutsideTheFingerprintNotLookalikeInputs() throws Exception {
        Path root = temp.resolve("native-project"), scratch = root.resolve(".gradle/copperbench-agent");
        write(root.resolve("build.gradle"), "// project");
        String baseline = WorkspaceExecutionSnapshot.fingerprint(root, () -> false);
        write(scratch.resolve("cb_initial.py"), "script");
        write(scratch.resolve("cb_scratch/receipt.json"), "pending");
        Files.writeString(scratch.resolve("cb_scratch/receipt.json"), "complete");
        Files.delete(scratch.resolve("cb_initial.py"));
        assertEquals(baseline, WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
        for (String input : new String[]{"cb_initial.py", "cb_scratch/receipt.json", "src/main/resources/data/cb_scratch/recipe.json",
                "src/gametest/java/Behavior.java"}) {
            write(root.resolve(input), "authored");
            assertNotEquals(baseline, WorkspaceExecutionSnapshot.fingerprint(root, () -> false), input);
            Files.delete(root.resolve(input));
        }
    }

    @Test void newerOwnedDeliveriesRemainExcludedUntilTheirBytesChange() throws Exception {
        Path root = temp.resolve("delivered");
        Path input = root.resolve("build.gradle");
        write(input, "authored input");
        Path artifact = root.resolve("build/libs/actual.jar");
        write(artifact, "built archive");
        String fingerprint = WorkspaceExecutionSnapshot.fingerprint(root, () -> false);
        var capturedAt = Files.getLastModifiedTime(input);
        var payload = new com.google.gson.JsonObject();
        payload.addProperty("output", "deliveries/result.jar");
        Path delivered = WorkspaceDeliveryOutputs.prepare(root, payload).publish(artifact, null);
        Files.setLastModifiedTime(delivered, java.nio.file.attribute.FileTime.fromMillis(capturedAt.toMillis() + 1000));
        assertEquals(fingerprint, WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
        assertTrue(WorkspaceExecutionSnapshot.matchesFingerprint(root, fingerprint, capturedAt, () -> false));
        Files.writeString(delivered, "user-replaced bytes");
        Files.setLastModifiedTime(delivered, java.nio.file.attribute.FileTime.fromMillis(capturedAt.toMillis() + 1000));
        assertFalse(WorkspaceExecutionSnapshot.matchesFingerprint(root, fingerprint, capturedAt, () -> false));
        assertNotEquals(fingerprint, WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
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

    @Test void historyAndUserSettingsCanChangeWithoutInvalidatingAuthoredInputs() throws Exception {
        Path root = temp.resolve("history-active");
        write(root.resolve("example.mcreator"), "workspace definition");
        write(root.resolve("src/main/java/Native.java"), "native implementation");
        write(root.resolve(".mcreator/custom-authoring.json"), "retain custom input");
        var changes = new java.util.concurrent.atomic.AtomicInteger();
        var snapshot = WorkspaceExecutionSnapshot.capture(root, root.resolve(".copperbench/task-runs/one/workspace"),
                UUID.randomUUID(), 0, Clock.systemUTC(), () -> {
                    try {
                        String value = "internal update " + changes.incrementAndGet();
                        write(root.resolve(".mcreator/localHistory/objects/temporary.tmp"), value);
                        write(root.resolve(".mcreator/workspaceBackups/latest.backup"), value);
                        write(root.resolve(".mcreator/userSettings"), value);
                    } catch (Exception exception) { throw new RuntimeException(exception); }
                    return false;
                });
        assertEquals(3, snapshot.files().size());
        assertEquals("retain custom input", Files.readString(snapshot.root().resolve(".mcreator/custom-authoring.json")));
        assertFalse(Files.exists(snapshot.root().resolve(".mcreator/localHistory")));
        assertFalse(Files.exists(snapshot.root().resolve(".mcreator/workspaceBackups")));
        assertFalse(Files.exists(snapshot.root().resolve(".mcreator/userSettings")));
        write(root.resolve(".mcreator/userSettings"), "another background update");
        assertEquals(snapshot.sha256(), WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
    }

    @Test void disappearingRealSourceFailsWithRetryableSnapshotDiagnostic() throws Exception {
        Path root = temp.resolve("source-removed");
        Path source = root.resolve("Native.java");
        write(source, "native");
        Path target = root.resolve(".copperbench/task-runs/one/workspace");
        var failure = assertThrows(WorkspaceExecutionSnapshot.SnapshotException.class, () ->
                WorkspaceExecutionSnapshot.capture(root, target, UUID.randomUUID(), 0, Clock.systemUTC(), () -> {
                    if (Files.isDirectory(target) && Files.exists(source)) {
                        try { Files.delete(source); } catch (Exception exception) { throw new RuntimeException(exception); }
                    }
                    return false;
                }));
        assertEquals("WORKSPACE_SNAPSHOT_CHANGED", failure.code());
        assertFalse(Files.exists(target.resolveSibling("source-manifest.json")));
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent()); Files.writeString(path, content);
    }
}
