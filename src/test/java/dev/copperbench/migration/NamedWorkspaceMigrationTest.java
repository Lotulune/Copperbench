package dev.copperbench.migration;

import com.google.gson.*;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.tracks.VersionTrackCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NamedWorkspaceMigrationTest {
    @TempDir Path temp;
    Path source() throws Exception {
        Path root = Files.createDirectory(temp.resolve("source"));
        Files.writeString(root.resolve("my_mod.mcreator"), "{\"workspaceSettings\":{\"modName\":\"My Mod\",\"currentGenerator\":\"fabric-1.21.1\"},\"mod_elements\":[]}");
        return root;
    }
    @Test void upstreamImportMustAcceptActualNamedWorkspaceDescriptor() throws Exception {
        Path root = source();
        var service = new UpstreamWorkspaceImportService();
        assertTrue(service.preview(root).complete());
        Path target = temp.resolve("imported");
        assertTrue(service.execute(root, target).complete());
        assertEquals(Files.readString(root.resolve("my_mod.mcreator")), Files.readString(target.resolve("my_mod.mcreator")));
        assertFalse(Files.exists(target.resolve("workspace.mcreator")));
    }

    @Test void ambiguousDescriptorsAreRejectedBeforeWritingAnyCopy() throws Exception {
        Path root = source();
        Files.copy(root.resolve("my_mod.mcreator"), root.resolve("workspace.mcreator"));
        Path target = temp.resolve("target");
        assertThrows(IllegalArgumentException.class, () -> new UpstreamWorkspaceImportService().execute(root, target));
        assertThrows(IllegalArgumentException.class, () -> new LoaderMigrationService(VersionTrackCatalog.official())
                .execute(state(), "neoforge-1.21.1", root, target));
        assertFalse(Files.exists(target));
    }

    static WorkspaceState state() {
        JsonObject generator = JsonParser.parseString("{\"id\":\"fabric-1.21.1\",\"loader\":\"fabric\",\"minecraftVersion\":\"1.21.1\"}").getAsJsonObject();
        return new WorkspaceState(UUID.randomUUID(), "My Mod", "mod", 0, false, generator, new JsonObject(), List.of());
    }
    @Test void loaderMigrationMustRewriteTheActualWorkspaceDescriptor() throws Exception {
        Path root = source();
        JsonObject generator = JsonParser.parseString("{\"id\":\"fabric-1.21.1\",\"loader\":\"fabric\",\"minecraftVersion\":\"1.21.1\"}").getAsJsonObject();
        WorkspaceState state = new WorkspaceState(UUID.randomUUID(), "My Mod", "mod", 0, false, generator, new JsonObject(), List.of());
        Path target = temp.resolve("target"); Exception failure = null;
        try { new LoaderMigrationService(VersionTrackCatalog.official()).execute(state, "neoforge-1.21.1", root, target); }
        catch (Exception exception) { failure = exception; }
        assertNull(failure, "Named workspace descriptors must migrate without fabricating workspace.mcreator");
        assertEquals("neoforge-1.21.1", JsonParser.parseString(Files.readString(target.resolve("my_mod.mcreator"))).getAsJsonObject().getAsJsonObject("workspaceSettings").get("currentGenerator").getAsString());
    }
}
