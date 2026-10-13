package dev.copperbench.migration;

import com.google.gson.*;
import dev.copperbench.core.workspace.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VersionCopyUpgradeServiceTest {
    @TempDir Path root;
    @Test void unknownTypesAndFieldsRemainExplicitManualTasksWithTheirOriginalValues() throws Exception {
        Files.writeString(root.resolve("source.mcreator"), "{\"workspaceSettings\":{\"currentGenerator\":\"fabric-26.2\"},\"plugin.future\":{\"keep\":true}}");
        Files.createDirectories(root.resolve("elements"));
        Files.writeString(root.resolve("elements/OldBiome.mod.json"), "{\"_type\":\"biome\",\"definition\":{\"temperature\":0.75,\"futureField\":[1,2]}}");
        var element = new WorkspaceState.Element(UUID.randomUUID(), "biome", "old_biome", "Old Biome", "valid", "manual", Instant.EPOCH, new JsonObject());
        var source = new WorkspaceState(UUID.randomUUID(), "Original", "mod", 7, false,
                JsonParser.parseString("{\"id\":\"fabric-26.2\"}").getAsJsonObject(), new JsonObject(), List.of(element));
        String before = WorkspaceTreeHasher.hash(root);
        var plan = new VersionCopyUpgradeService().preview(source, root, "fabric-26.3", e -> JsonParser.parseString("{\"internalName\":\"OldBiome\",\"registryName\":\"old_biome\"}").getAsJsonObject());
        var entries = plan.getAsJsonArray("entries").asList().stream().map(JsonElement::getAsJsonObject).toList();
        var field = entries.stream().filter(e -> e.get("path").getAsString().endsWith("/futureField")).findFirst().orElseThrow();
        assertEquals("unsupported", field.get("disposition").getAsString()); assertEquals("[1,2]", field.get("sourceValue").toString());
        assertTrue(plan.toString().contains("plugin.future")); assertTrue(plan.toString().contains("OldBiome")); assertTrue(plan.toString().contains(element.id().toString()));
        assertEquals(before, WorkspaceTreeHasher.hash(root)); assertFalse(plan.get("executionAvailable").getAsBoolean());
        Path evidence = Path.of("output/p263-24/core-ui"); Files.createDirectories(evidence); Files.writeString(evidence.resolve("unsupported-plan.json"), plan.toString());
    }

    @Test void fingerprintCanReadALeasedWorkspaceButStillIncludesWorldsAndUserLockFiles() throws Exception {
        Files.createDirectories(root.resolve("run/saves/Test World"));
        Files.writeString(root.resolve("run/saves/Test World/level.dat"), "original world fixture");
        Files.writeString(root.resolve("workspace.write.lock"), "a user file with the same basename");
        try (var lease = WorkspaceFileLease.tryAcquire(root).orElseThrow()) {
            String before = WorkspaceTreeHasher.hash(root);
            Files.writeString(root.resolve("workspace.write.lock"), "changed user data");
            String changed = WorkspaceTreeHasher.hash(root); assertNotEquals(before, changed);
            Files.writeString(root.resolve("run/saves/Test World/level.dat"), "changed world fixture");
            assertNotEquals(changed, WorkspaceTreeHasher.hash(root));
        }
    }

    @Test void malformedOrLinkedDefinitionsCannotProduceAPlan() throws Exception {
        Files.writeString(root.resolve("source.mcreator"), "{\"workspaceSettings\":{\"currentGenerator\":\"neoforge-26.2\"}}");
        var state = new WorkspaceState(UUID.randomUUID(), "Source", "mod", 0, false,
                JsonParser.parseString("{\"id\":\"fabric-26.2\"}").getAsJsonObject(), new JsonObject(), List.of());
        assertThrows(java.io.IOException.class, () -> new VersionCopyUpgradeService().preview(state, root, "fabric-26.3", e -> new JsonObject()));
        Files.writeString(root.resolve("source.mcreator"), "{\"workspaceSettings\":{\"currentGenerator\":\"fabric-26.2\"}}");
        Files.createSymbolicLink(root.resolve("linked.mcreator"), root.resolve("source.mcreator"));
        assertThrows(java.io.IOException.class, () -> new VersionCopyUpgradeService().preview(state, root, "fabric-26.3", e -> new JsonObject()));
    }
}
