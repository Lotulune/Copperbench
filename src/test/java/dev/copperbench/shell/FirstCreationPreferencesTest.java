package dev.copperbench.shell;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FirstCreationPreferencesTest {
    @TempDir Path directory;
    private Path workspace(String name) throws IOException {
        Path root = Files.createDirectories(directory.resolve(name));
        return Files.writeString(root.resolve(name + ".mcreator"), "{}", StandardCharsets.UTF_8);
    }
    private Path preferences(Path file) { return file.getParent().resolve(".copperbench/first-creation.json"); }

    @Test void realReadWriteKeepsOnlyPresentationAndIdentityReferences() throws Exception {
        Path file = workspace("first_creation");
        FirstCreationPreferences.initialize(file, "first_creation");
        JsonObject value = FirstCreationPreferences.read(file).orElseThrow();
        assertEquals(file.toAbsolutePath().toString(), value.get("workspaceFile").getAsString());
        assertTrue(value.get("showOnOpen").getAsBoolean());
        value.addProperty("workspaceId", UUID.randomUUID().toString());
        String elementId = UUID.randomUUID().toString();
        value.addProperty("elementId", elementId); value.addProperty("showOnOpen", false);
        JsonObject task = new JsonObject(); task.addProperty("id", UUID.randomUUID().toString()); task.addProperty("inputRevision", 8);
        task.addProperty("completed", true); value.add("build", task);
        value.addProperty("projectVerified", true); value.addProperty("gameplayPassed", true);
        FirstCreationPreferences.save(file, value);
        JsonObject restored = FirstCreationPreferences.read(file).orElseThrow();
        assertEquals(elementId, restored.get("elementId").getAsString());
        assertFalse(restored.get("showOnOpen").getAsBoolean());
        assertFalse(restored.has("projectVerified")); assertFalse(restored.has("gameplayPassed"));
        assertFalse(restored.getAsJsonObject("build").has("completed"));
        assertEquals(8, restored.getAsJsonObject("build").get("inputRevision").getAsInt());
    }

    @Test void initializationDoesNotOverwriteAnExistingGuide() throws Exception {
        Path file = workspace("first_creation"); FirstCreationPreferences.initialize(file, "first_creation");
        String before = Files.readString(preferences(file));
        assertThrows(IOException.class, () -> FirstCreationPreferences.initialize(file, "another_mod"));
        assertEquals(before, Files.readString(preferences(file)));
    }

    @Test void corruptDataSurvivesBothReadAndAttemptedSave() throws Exception {
        Path file = workspace("first_creation"); FirstCreationPreferences.initialize(file, "first_creation");
        JsonObject valid = FirstCreationPreferences.read(file).orElseThrow();
        Files.writeString(preferences(file), "{broken", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> FirstCreationPreferences.read(file));
        assertThrows(IOException.class, () -> FirstCreationPreferences.save(file, valid));
        assertEquals("{broken", Files.readString(preferences(file)));
    }

    @Test void anotherWorkspaceCannotReplaceTheBoundReferences() throws Exception {
        Path first = workspace("first_creation"), second = workspace("other_creation");
        FirstCreationPreferences.initialize(first, "first_creation"); FirstCreationPreferences.initialize(second, "other_creation");
        String before = Files.readString(preferences(first));
        JsonObject foreign = FirstCreationPreferences.read(second).orElseThrow();
        assertThrows(IOException.class, () -> FirstCreationPreferences.save(first, foreign));
        assertEquals(before, Files.readString(preferences(first)));
        assertEquals("other_creation", FirstCreationPreferences.read(second).orElseThrow().get("modId").getAsString());
    }

    @Test void oversizedReferencesAreRejectedAndPreserved() throws Exception {
        Path file = workspace("first_creation"); FirstCreationPreferences.initialize(file, "first_creation");
        JsonObject value = FirstCreationPreferences.read(file).orElseThrow();
        String oversized = "x".repeat(FirstCreationPreferences.MAX_BYTES + 1);
        Files.writeString(preferences(file), oversized, StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> FirstCreationPreferences.read(file));
        assertThrows(IOException.class, () -> FirstCreationPreferences.save(file, value));
        assertEquals(oversized, Files.readString(preferences(file)));
    }

    @Test void aRedirectedPreferenceDirectoryCannotWriteOutsideTheWorkspace() throws Exception {
        Path file = workspace("first_creation"), external = Files.createDirectories(directory.resolve("outside"));
        Path link = file.getParent().resolve(".copperbench");
        try { Files.createSymbolicLink(link, external); }
        catch (IOException | UnsupportedOperationException exception) { assumeTrue(false, "Symbolic links unavailable: " + exception.getMessage()); }
        try {
            assertThrows(IOException.class, () -> FirstCreationPreferences.initialize(file, "first_creation"));
            assertFalse(Files.exists(external.resolve("first-creation.json")));
        } finally { Files.deleteIfExists(link); }
    }

    @Test @EnabledOnOs(OS.WINDOWS) void windowsJunctionsAreRejectedWithoutWritingTheirTarget() throws Exception {
        Path file = workspace("first_creation"), external = Files.createDirectories(directory.resolve("junction-target"));
        Path link = file.getParent().resolve(".copperbench");
        Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), external.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        try {
            assertThrows(IOException.class, () -> FirstCreationPreferences.initialize(file, "first_creation"));
            assertFalse(Files.exists(external.resolve("first-creation.json")));
        } finally { Files.deleteIfExists(link); }
    }

}
