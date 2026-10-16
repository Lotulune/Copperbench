package dev.copperbench.installer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UpdatePreparationTest {
    @TempDir Path directory;
    private UpdateManifest fixture() throws Exception {
        Path payload = directory.resolve("release"); Files.createDirectories(payload.resolve("jdk/bin"));
        Files.writeString(payload.resolve("app.txt"), "new app"); Files.writeString(payload.resolve("jdk/bin/java"), "unchanged runtime");
        Files.writeString(payload.resolve("copperbench-setup.jar"), "setup");
        Path descriptor = UpdatePackageBuilder.build(payload, "0.2.0", "windows-x64", directory.resolve("assets"));
        try (InputStream in = Files.newInputStream(descriptor)) { return UpdateManifest.read(in); }
    }
    private Path installed() throws Exception {
        Path installed = directory.resolve("installed"); Files.createDirectories(installed.resolve("jdk/bin"));
        Files.writeString(installed.resolve("app.txt"), "old app"); Files.writeString(installed.resolve("jdk/bin/java"), "unchanged runtime");
        return installed;
    }
    @Test void downloadsOnlyChangedComponentsAndCompletesAnInPlaceUpgrade() throws Exception {
        var manifest = fixture(); Path old = installed();
        Path stage = Files.createDirectory(directory.resolve("stage"));
        var changed = UpdatePreparation.changedParts(manifest, old, () -> false);
        assertEquals(1, changed.size()); assertTrue(changed.getFirst().name().contains("application"));
        List<String> fetched = new ArrayList<>();
        UpdatePreparation.prepare(manifest, changed, old, stage, (name, out, size) -> {
            fetched.add(name); Files.copy(directory.resolve("assets").resolve(name), out);
        }, () -> false, ignored -> {});
        assertEquals(changed.stream().map(UpdateManifest.Part::name).toList(), fetched);
        assertEquals("unchanged runtime", Files.readString(stage.resolve("payload/jdk/bin/java")));
        // Register a minimal older inventory so obsolete files are managed and removed.
        Properties receipt = new Properties(); receipt.setProperty("target", old.toString()); receipt.setProperty("scope", "user"); receipt.setProperty("product", "dev.copperbench.studio");
        try (Writer writer = Files.newBufferedWriter(old.resolve(InstallEngine.RECEIPT))) { receipt.store(writer, "fixture"); }
        Files.writeString(old.resolve("obsolete.jar"), "old"); Files.writeString(old.resolve("notes.txt"), "personal data");
        Files.writeString(old.resolve(InstallEngine.MANIFEST), "app.txt\nobsolete.jar\njdk\njdk/bin\njdk/bin/java\n");
        var result = new InstallEngine().install(new InstallEngine.Plan(stage.resolve("payload"), stage.resolve("payload.tsv"),
                stage.resolve("payload/copperbench-setup.jar"), old, "0.2.0", "zh", "dark", "user", false, false), () -> false, ignored -> {}, ignored -> {});
        assertNull(result.previousVersion()); assertFalse(Files.exists(old.resolve("obsolete.jar")));
        assertEquals("new app", Files.readString(old.resolve("app.txt"))); assertEquals("personal data", Files.readString(old.resolve("notes.txt")));
    }
    @Test void corruptedComponentNeverChangesTheInstalledVersion() throws Exception {
        var manifest = fixture(); Path old = installed(); var changed = UpdatePreparation.changedParts(manifest, old, () -> false);
        assertThrows(IOException.class, () -> UpdatePreparation.prepare(manifest, changed, old, Files.createDirectory(directory.resolve("stage")),
                (name, out, size) -> Files.writeString(out, "corrupted download"), () -> false, ignored -> {}));
        assertEquals("old app", Files.readString(old.resolve("app.txt")));
    }
    @Test void reusedFilesAreRecheckedAfterTheDownloadPlan() throws Exception {
        var manifest = fixture(); Path old = installed(); var changed = UpdatePreparation.changedParts(manifest, old, () -> false);
        Files.writeString(old.resolve("jdk/bin/java"), "modified after check");
        assertThrows(IOException.class, () -> UpdatePreparation.prepare(manifest, changed, old, Files.createDirectory(directory.resolve("stage")),
                (name, out, size) -> Files.copy(directory.resolve("assets").resolve(name), out), () -> false, ignored -> {}));
        assertEquals("old app", Files.readString(old.resolve("app.txt")));
    }
    @Test void rejectsRootTraversalAndCancellation() throws Exception {
        String name = Base64.getEncoder().encodeToString("../outside".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> InstallEngine.parseManifest(List.of("D\t" + name + "\t-\t0\t493")));
        var manifest = fixture(); Path old = installed();
        assertThrows(java.util.concurrent.CancellationException.class, () -> UpdatePreparation.changedParts(manifest, old, () -> true));
        assertEquals("old app", Files.readString(old.resolve("app.txt")));
    }
    @Test void comparesNumericVersionsAndRejectsPrereleaseOrMalformedValues() {
        assertTrue(UpdateManifest.compareVersions("0.1.10", "0.1.9") > 0);
        assertTrue(UpdateManifest.compareVersions("0.2.0", "0.10.0") < 0);
        assertEquals(0, UpdateManifest.compareVersions("1.0.0", "1.0.0"));
        assertThrows(IllegalArgumentException.class, () -> UpdateManifest.compareVersions("1.0.0-beta", "1.0.0"));
    }
    @Test void rejectsShortOrOversizedTransfersAndNeverAcceptsPlainHttp() throws Exception {
        var uri = java.net.URI.create("https://example.invalid/component.zip");
        assertThrows(IOException.class, () -> UpdatePreparation.download(uri, directory.resolve("short.zip"), 5,
                () -> false, ignored -> {}, ignored -> new ByteArrayInputStream(new byte[3])));
        assertThrows(IOException.class, () -> UpdatePreparation.download(uri, directory.resolve("large.zip"), 5,
                () -> false, ignored -> {}, ignored -> new ByteArrayInputStream(new byte[8])));
        assertThrows(IOException.class, () -> UpdatePreparation.open(java.net.URI.create("http://localhost/component.zip")));
    }
}
