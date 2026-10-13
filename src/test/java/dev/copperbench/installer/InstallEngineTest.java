package dev.copperbench.installer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class InstallEngineTest {
    @TempDir Path directory;
    private InstallEngine.Plan plan(String contents) throws Exception {
        Path payload = directory.resolve("payload"); Files.createDirectories(payload);
        Files.writeString(payload.resolve("app.txt"), contents);
        Path jar = directory.resolve("setup.jar"); Files.writeString(jar, "test installer");
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents.getBytes(StandardCharsets.UTF_8)));
        Path manifest = directory.resolve("payload.tsv");
        Files.writeString(manifest, "F\t" + Base64.getEncoder().encodeToString("app.txt".getBytes(StandardCharsets.UTF_8)) + "\t" + digest + "\t" + contents.getBytes(StandardCharsets.UTF_8).length + "\t420\n");
        return new InstallEngine.Plan(payload, manifest, jar, directory.resolve("安装位置 with spaces"), "0.1.4", "zh", "dark", "user", false, false);
    }
    private InstallEngine.Result install(InstallEngine.Plan plan) throws IOException {
        return new InstallEngine().install(plan, () -> false, ignored -> {}, ignored -> {});
    }
    @Test void verifiesFilesAndWritesLanguageWithoutTouchingWorkspaces() throws Exception {
        InstallEngine.Plan plan = plan("new version");
        Path workspace = directory.resolve("my-project"); Files.createDirectory(workspace); Files.writeString(workspace.resolve("workspace.mcreator"), "keep");
        assertNull(install(plan).previousVersion());
        assertEquals("new version", Files.readString(plan.target().resolve("app.txt")));
        assertEquals("zh\n", Files.readString(plan.target().resolve("install-language.txt")));
        assertEquals("keep", Files.readString(workspace.resolve("workspace.mcreator")));
    }
    @Test void aCorruptPackageNeverPublishesAnInstallation() throws Exception {
        InstallEngine.Plan plan = plan("expected"); Files.writeString(plan.payload().resolve("app.txt"), "tampered");
        assertThrows(IOException.class, () -> install(plan));
        assertFalse(Files.exists(plan.target()));
    }
    @Test void failedRegistrationRollsBackAnExistingInstallation() throws Exception {
        InstallEngine.Plan first = plan("old"); install(first);
        InstallEngine.Plan next = plan("new");
        assertThrows(IOException.class, () -> new InstallEngine().install(next, () -> false, ignored -> {}, ignored -> { throw new IOException("registration failed"); }));
        assertEquals("old", Files.readString(next.target().resolve("app.txt")));
    }
    @Test void upgradePreservesUnmanagedFilesAndKeepsPreviousVersion() throws Exception {
        InstallEngine.Plan first = plan("old"); install(first);
        Files.writeString(first.target().resolve("personal-notes.txt"), "keep");
        InstallEngine.Result result = install(plan("new"));
        assertEquals("keep", Files.readString(result.target().resolve("personal-notes.txt")));
        assertEquals("old", Files.readString(result.previousVersion().resolve("app.txt")));
    }
    @Test void cancellationDuringCopyLeavesTheOldInstallationInPlace() throws Exception {
        InstallEngine.Plan first = plan("old"); install(first);
        AtomicBoolean cancelled = new AtomicBoolean(); InstallEngine.Plan next = plan("new");
        assertThrows(CancellationException.class, () -> new InstallEngine().install(next, cancelled::get, state -> cancelled.set(true), ignored -> {}));
        assertEquals("old", Files.readString(next.target().resolve("app.txt")));
    }
    @Test void refusesAnUnrelatedTargetOrWorkspaceInsideTheAppFolder() throws Exception {
        InstallEngine.Plan plan = plan("new"); Files.createDirectory(plan.target());
        Files.writeString(plan.target().resolve("unrelated.txt"), "keep");
        assertThrows(IOException.class, () -> install(plan));
        Files.delete(plan.target().resolve("unrelated.txt")); Files.delete(plan.target()); install(plan);
        Files.writeString(plan.target().resolve("project.mcreator"), "keep");
        assertThrows(IOException.class, () -> install(plan));
        assertEquals("keep", Files.readString(plan.target().resolve("project.mcreator")));
    }
    @Test void rejectsTraversalInTheManifestBeforeAnyCopy() throws Exception {
        InstallEngine.Plan plan = plan("new");
        String record = Files.readString(plan.manifest()).replace(Base64.getEncoder().encodeToString("app.txt".getBytes()), Base64.getEncoder().encodeToString("../escape.txt".getBytes()));
        Files.writeString(plan.manifest(), record);
        assertThrows(IOException.class, () -> install(plan));
        assertFalse(Files.exists(directory.resolve("escape.txt")));
    }
    @Test void uninstallRemovesOnlyRecordedFiles() throws Exception {
        InstallEngine.Plan plan = plan("new"); install(plan); Files.writeString(plan.target().resolve("user-file.txt"), "keep");
        InstallEngine.uninstall(plan.target());
        assertFalse(Files.exists(plan.target().resolve("app.txt")));
        assertEquals("keep", Files.readString(plan.target().resolve("user-file.txt")));
    }
    @Test void upgradeRejectsAReceiptForAnotherProductOrLocation() throws Exception {
        InstallEngine.Plan plan = plan("old"); install(plan);
        Path receipt = plan.target().resolve(InstallEngine.RECEIPT);
        String valid = Files.readString(receipt);
        Files.writeString(receipt, valid.replace("dev.copperbench.studio", "another.product"));
        assertThrows(IOException.class, () -> install(plan("new")));
        assertEquals("old", Files.readString(plan.target().resolve("app.txt")));
        Files.writeString(receipt, valid.replace("target=", "target=other"));
        assertThrows(IOException.class, () -> install(plan("new")));
    }
    @Test void invalidUninstallListIsRejectedBeforeDeletingAnyFiles() throws Exception {
        InstallEngine.Plan plan = plan("new"); install(plan);
        Files.writeString(plan.target().resolve(InstallEngine.MANIFEST), "app.txt\n../outside.txt\n");
        assertThrows(IOException.class, () -> InstallEngine.uninstall(plan.target()));
        assertEquals("new", Files.readString(plan.target().resolve("app.txt")));
    }
    @Test void packagedExampleWorkspacesDoNotBlockUpgrades() throws Exception {
        InstallEngine.Plan plan = plan("example");
        Files.move(plan.payload().resolve("app.txt"), plan.payload().resolve("example.mcreator"));
        Files.writeString(plan.manifest(), Files.readString(plan.manifest()).replace(
                Base64.getEncoder().encodeToString("app.txt".getBytes(StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString("example.mcreator".getBytes(StandardCharsets.UTF_8))));
        install(plan);
        assertNotNull(install(plan).previousVersion());
    }
    @Test void ignoresRelativeXdgValuesAndEscapesDesktopCommandArguments() {
        Path fallback = directory.resolve("config");
        assertEquals(fallback, PlatformInstall.xdg("relative/config", fallback));
        assertEquals(fallback, PlatformInstall.xdg("", fallback));
        assertEquals(fallback, PlatformInstall.xdg(fallback.toString(), directory));
        assertEquals("\"/home/user/a %% b\"", PlatformInstall.desktopQuote("/home/user/a % b"));
        assertThrows(IllegalArgumentException.class, () -> PlatformInstall.desktopQuote("/home/user/\nExec=bad"));
    }
}
