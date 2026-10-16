package dev.copperbench.generator;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Production export/input lifecycle with explicit archive-task process receipts; no real Gradle or game process. */
class OrdinaryExportDeliveryTest {
    @TempDir Path root;
    private static final Clock CLOCK = Clock.systemUTC();
    private static final WorkspaceState WORKSPACE = Fabric1211GoldenWorkspace.create();

    @Test void exportsTheDeclaredCurrentArchiveDespiteOlderVersionSourcesDevAndPreviousDeliveryJars() throws Exception {
        source();
        for (String file : List.of("a.jar", "mod-1.0.jar", "mod-2.0-sources.jar", "mod-2.0-dev.jar"))
            write(root.resolve("build/libs/" + file), "old or auxiliary " + file);
        Path artifact = root.resolve("build/libs/mod-2.0.jar");
        try (var gateway = gateway((directory, arguments, timeout, log) -> {
            write(artifact, "current declared artifact");
            GradleArchiveOutputFixture.record(arguments, artifact);
            return new GradleProcessRunner.ProcessResult(0, false);
        })) {
            JsonObject task = completed(gateway, gateway.start(WORKSPACE.id(), Operation.EXPORT_WORKSPACE,
                    payload("build/libs/copperbench-delivery/current.jar")));
            assertEquals("succeeded", task.get("state").getAsString(), task.toString());
            assertEquals("current declared artifact", Files.readString(root.resolve("build/libs/copperbench-delivery/current.jar")));
            assertEquals("current", fact(task).get("status").getAsString());
        }
    }

    @Test void exportsTheDeclaredArchiveFromACustomBuildDirectoryWithoutBuildLibs() throws Exception {
        source();
        Path artifact = root.resolve("build/distributions/custom-mod.jar");
        try (var gateway = gateway((directory, arguments, timeout, log) -> {
            write(artifact, "declared custom archive");
            GradleArchiveOutputFixture.record(arguments, artifact);
            return new GradleProcessRunner.ProcessResult(0, false);
        })) {
            JsonObject task = completed(gateway, gateway.start(WORKSPACE.id(), Operation.EXPORT_WORKSPACE,
                    payload("exports/custom-mod.jar")));
            assertEquals("succeeded", task.get("state").getAsString(), task.toString());
            assertFalse(Files.exists(root.resolve("build/libs")));
            assertEquals(Files.readString(artifact), Files.readString(root.resolve("exports/custom-mod.jar")));
            assertEquals("current", fact(task).get("status").getAsString());
        }
    }

    @Test void newNestedCustomDeliveryAndRepeatedOwnedDeliveryKeepEarlierBuildAndClientInputsCurrent() throws Exception {
        source(); AtomicInteger version = new AtomicInteger(); List<JsonObject> tasks = new ArrayList<>();
        try (var gateway = gateway((directory, arguments, timeout, log) -> {
            Path artifact = root.resolve("build/libs/current.jar"); write(artifact, "version " + version.incrementAndGet());
            GradleArchiveOutputFixture.record(arguments, artifact);
            return new GradleProcessRunner.ProcessResult(0, true);
        })) {
            for (Operation operation : List.of(Operation.BUILD_WORKSPACE, Operation.RUN_CLIENT, Operation.RUN_CLIENT))
                tasks.add(completed(gateway, gateway.start(WORKSPACE.id(), operation, new JsonObject())));
            String before = WorkspaceExecutionSnapshot.fingerprint(root, () -> false);
            String destination = "exports/new-parent/current.jar";
            tasks.add(completed(gateway, gateway.start(WORKSPACE.id(), Operation.EXPORT_WORKSPACE, payload(destination))));
            assertEquals(before, WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
            assertEquals("version 4", Files.readString(root.resolve(destination)));
            tasks.add(completed(gateway, gateway.start(WORKSPACE.id(), Operation.EXPORT_WORKSPACE, payload(destination))));
            assertEquals("version 5", Files.readString(root.resolve(destination)));
            JsonObject observation = WorkspaceTaskInputIdentity.observe(root, WORKSPACE.id(), tasks, CLOCK);
            for (JsonObject task : tasks) {
                assertEquals("succeeded", task.get("state").getAsString());
                assertEquals("current", observation.getAsJsonObject("tasks").getAsJsonObject(task.get("id").getAsString())
                        .get("status").getAsString());
            }
        }
    }

    @Test void preservesAnUnownedOrExternallyModifiedCustomDestinationAndKeepsItInTheInputHash() throws Exception {
        source(); Path destination = root.resolve("exports/current.jar"); write(destination, "authored dependency");
        String authored = WorkspaceExecutionSnapshot.fingerprint(root, () -> false);
        assertThrows(IOException.class, () -> WorkspaceDeliveryOutputs.prepare(root, payload("exports/current.jar")));
        assertEquals("authored dependency", Files.readString(destination));
        Files.delete(destination);
        Path artifact = root.resolve("build/libs/current.jar"); write(artifact, "built artifact");
        WorkspaceDeliveryOutputs.prepare(root, payload("exports/current.jar")).publish(artifact);
        String owned = WorkspaceExecutionSnapshot.fingerprint(root, () -> false);
        write(destination, "external edit to delivery");
        assertNotEquals(owned, WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
        assertNotEquals(authored, owned);
        assertThrows(IOException.class, () -> WorkspaceDeliveryOutputs.prepare(root, payload("exports/current.jar")));
        assertEquals("external edit to delivery", Files.readString(destination));
    }

    @Test void cannotTurnAuthoredSourceRootsIntoIgnoredDeliveryFiles() throws Exception {
        source();
        for (String destination : List.of("src/main/resources/output.jar", "elements/output.jar", "gradle/output.jar", "buildscripts/output.jar",
                "lib/output.jar", "libs/output.jar", "exports/.git/output.jar")) {
            assertThrows(IOException.class, () -> WorkspaceDeliveryOutputs.prepare(root, payload(destination)), destination);
            assertFalse(Files.exists(root.resolve(destination)));
        }
        write(root.resolve("exports/independently-authored.jar"), "one");
        String first = WorkspaceExecutionSnapshot.fingerprint(root, () -> false);
        write(root.resolve("exports/independently-authored.jar"), "two");
        assertNotEquals(first, WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
    }

    @Test void publishingItsOwnOutputDoesNotHideADifferentAuthoredFileEdit() throws Exception {
        Path source = source(); Path artifact = root.resolve("build/libs/current.jar"); write(artifact, "built artifact");
        var delivery = WorkspaceDeliveryOutputs.prepare(root, payload("exports/fresh/current.jar"));
        UUID id = UUID.randomUUID(); JsonObject snapshot;
        try (var monitor = WorkspaceTaskInputIdentity.monitor(root)) {
            snapshot = WorkspaceTaskInputIdentity.start(root, WORKSPACE.id(), id, WORKSPACE.revision(), CLOCK, () -> false);
            monitor.publishedOutput(delivery.publish(artifact));
            write(source, "class Manual { int changedDuringExport; }\n");
            WorkspaceTaskInputIdentity.finish(root, WORKSPACE.id(), id, snapshot, true, monitor, CLOCK, () -> false);
        }
        JsonObject task = new JsonObject(); task.addProperty("id", id.toString()); task.addProperty("state", "succeeded"); task.add("sourceSnapshot", snapshot);
        assertEquals("changed_during_task", fact(task).get("status").getAsString());
    }

    @Test void missingOrChangedArchiveTaskReceiptCannotFallBackToAnExistingJar() throws Exception {
        source(); write(root.resolve("build/libs/a.jar"), "old artifact");
        try (var gateway = gateway((directory, arguments, timeout, log) -> new GradleProcessRunner.ProcessResult(0, false))) {
            JsonObject task = completed(gateway, gateway.start(WORKSPACE.id(), Operation.EXPORT_WORKSPACE, payload("exports/current.jar")));
            assertEquals("failed", task.get("state").getAsString());
            assertFalse(Files.exists(root.resolve("exports/current.jar")));
        }
        var request = GradleArchiveOutput.prepare(root, UUID.randomUUID(), List.of("remapJar", "jar"));
        GradleArchiveOutputFixture.record(request.arguments(List.of("build")), root.resolve("build/libs/a.jar"));
        write(root.resolve("build/libs/a.jar"), "changed after task receipt");
        assertThrows(IOException.class, request::artifact);
    }

    @Test void publishKeepsTheArchiveReceiptHashBindingUntilTheBytesAreCopied() throws Exception {
        source(); Path artifact = root.resolve("build/libs/current.jar"); write(artifact, "recorded bytes");
        String declared = WorkspaceExecutionSnapshot.sha256(artifact);
        var delivery = WorkspaceDeliveryOutputs.prepare(root, payload("exports/current.jar"));
        write(artifact, "changed after archive receipt");
        assertThrows(IOException.class, () -> delivery.publish(artifact, declared));
        assertFalse(Files.exists(root.resolve("exports/current.jar")));
    }

    @Test void exportingToTheActualArchiveItselfIsAllowedWithoutClaimingOtherExistingBuildFiles() throws Exception {
        source(); Path artifact = root.resolve("build/libs/current.jar"); write(artifact, "previous archive");
        var sameTarget = WorkspaceDeliveryOutputs.prepare(root, payload("build/libs/current.jar"));
        write(artifact, "new declared archive");
        String hash = WorkspaceExecutionSnapshot.sha256(artifact);
        assertEquals(artifact, sameTarget.publish(artifact, hash));
        assertEquals("new declared archive", Files.readString(artifact));
        Path unrelated = root.resolve("build/libs/unclaimed.jar"); write(unrelated, "preserve unclaimed build file");
        var otherTarget = WorkspaceDeliveryOutputs.prepare(root, payload("build/libs/unclaimed.jar"));
        assertThrows(IOException.class, () -> otherTarget.publish(artifact, hash));
        assertEquals("preserve unclaimed build file", Files.readString(unrelated));
    }

    @Test void redirectedCustomParentsNeverWriteToTheirExternalTarget() throws Exception {
        Path workspace = root.resolve("workspace"), external = root.resolve("external");
        Files.createDirectories(workspace); Files.createDirectories(external);
        Path redirect = workspace.resolve("exports");
        try { Files.createSymbolicLink(redirect, external); }
        catch (UnsupportedOperationException | IOException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links are unavailable in this test environment");
        }
        assertThrows(IOException.class, () -> WorkspaceDeliveryOutputs.prepare(workspace, payload("exports/current.jar")));
        assertFalse(Files.exists(external.resolve("current.jar")));
    }

    @Test void anOwnershipReceiptCannotExcludeAnAuthoredSourceRoot() throws Exception {
        Path source = root.resolve("src/main/resources/manual.jar"); write(source, "authored resource bytes");
        JsonObject file = new JsonObject(); file.addProperty("path", "src/main/resources/manual.jar");
        file.addProperty("sha256", WorkspaceExecutionSnapshot.sha256(source));
        var files = new com.google.gson.JsonArray(); files.add(file);
        JsonObject receipt = new JsonObject(); receipt.addProperty("schemaVersion", "1.0"); receipt.add("files", files);
        write(root.resolve(".copperbench/delivery-outputs.json"), receipt.toString());
        assertThrows(IOException.class, () -> WorkspaceExecutionSnapshot.fingerprint(root, () -> false));
        assertEquals("authored resource bytes", Files.readString(source));
    }

    private GradleWorkspaceTaskGateway gateway(GradleProcessRunner processes) {
        RevisionedWorkspaceStore store = new RevisionedWorkspaceStore(); store.register(WORKSPACE.copy());
        GradleWorkspaceBackend backend = new GradleWorkspaceBackend() {
            public String displayName() { return "Declared archive fixture"; }
            public String diagnosticPrefix() { return "DECLARED_ARCHIVE"; }
            public List<ValidationIssue> validate(WorkspaceState workspace) { return List.of(); }
            public GenerationResult generate(Path target, WorkspaceState workspace) { return new GenerationResult("fabric-1.21.1", "fixture", List.of()); }
        };
        return new GradleWorkspaceTaskGateway(store, ignored -> root, backend, CLOCK, UUID::randomUUID, processes);
    }

    private JsonObject fact(JsonObject task) throws Exception {
        return WorkspaceTaskInputIdentity.observe(root, WORKSPACE.id(), List.of(task), CLOCK).getAsJsonObject("tasks")
                .getAsJsonObject(task.get("id").getAsString());
    }
    private Path source() throws Exception { Path source = root.resolve("src/main/java/Manual.java"); write(source, "class Manual {}\n"); return source; }
    private static void write(Path file, String text) throws Exception { Files.createDirectories(file.getParent()); Files.writeString(file, text, StandardCharsets.UTF_8); }
    private static JsonObject payload(String output) { JsonObject result = new JsonObject(); result.addProperty("output", output); return result; }
    private static JsonObject completed(GradleWorkspaceTaskGateway gateway, JsonObject started) throws Exception {
        UUID id = UUID.fromString(started.get("id").getAsString()); long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        do {
            JsonObject current = gateway.find(WORKSPACE.id(), id).orElseThrow();
            if (!List.of("queued", "running").contains(current.get("state").getAsString())) return current;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("The isolated delivery task did not complete");
    }
}
