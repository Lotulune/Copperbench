package dev.copperbench.history;

import dev.copperbench.core.workspace.WorkspaceFileLease;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.localhistory.HistoryCheckpoint;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LegacyHistoryRuntimeFilesTest {
    @TempDir Path root;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void checkpointExcludesLiveLeaseAndPreservesExistingRuntimeBytes(boolean previouslyTracked) throws Exception {
        McreatorTestRuntime.ensureInitialized();
        boolean original = PreferencesManager.PREFERENCES.backups.enableLocalHistory.get();
        Path runtime = root.resolve(".copperbench/runtime.json");
        Files.createDirectories(runtime.getParent());
        Files.writeString(runtime, "retained runtime bytes");
        Path source = root.resolve("authored.txt");
        Files.writeString(source, "authored content");
        Path gitDirectory = root.resolve(".mcreator/localHistory");
        if (previouslyTracked) {
            Files.writeString(root.resolve(".copperbench/workspace.write.lock"), "");
            try (var repository = new FileRepositoryBuilder().setGitDir(gitDirectory.toFile()).setBare().build()) {
                repository.create(true);
                var config = repository.getConfig();
                config.setBoolean("core", null, "bare", false);
                config.setString("core", null, "worktree", root.toString());
                config.save();
            }
            try (var repository = new FileRepositoryBuilder().setGitDir(gitDirectory.toFile()).setWorkTree(root.toFile()).build();
                 Git git = new Git(repository)) {
                git.add().addFilepattern(".copperbench").addFilepattern("authored.txt").call();
                git.commit().setMessage("old runtime entries").setAuthor("Fixture", "fixture@example.invalid")
                        .setCommitter("Fixture", "fixture@example.invalid").setSign(false).call();
            }
            Files.writeString(gitDirectory.resolve("info/exclude"), "# Keep custom ignores\n*.private\n.mcreator/\n");
        }
        PreferencesManager.PREFERENCES.backups.enableLocalHistory.set(true);
        var settings = new WorkspaceSettings("history_fixture");
        settings.setCurrentGenerator("fabric-1.21.1");
        settings.setModName("History Fixture");
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("history_fixture.mcreator").toFile(), settings)) {
            CompletableFuture<List<HistoryCheckpoint>> ready = new CompletableFuture<>();
            workspace.getHistoryManager().getCheckpoints(ready::complete);
            assertFalse(ready.get(30, TimeUnit.SECONDS).isEmpty(), "Initial checkpoint must work with the live lease");
            CompletableFuture<Boolean> idle = new CompletableFuture<>();
            workspace.getHistoryManager().setBusyListener(busy -> { if (!busy) idle.complete(true); });
            idle.get(30, TimeUnit.SECONDS);
            workspace.getHistoryManager().setBusyListener(null);
            assertTrue(WorkspaceFileLease.tryAcquire(root).isEmpty(), "The real workspace lease remains held");
            Files.writeString(source, "updated authored content");
            workspace.getHistoryManager().importantCheckpoint("initial");
            try (var repository = new FileRepositoryBuilder().setGitDir(gitDirectory.toFile()).setWorkTree(root.toFile()).build();
                 Git git = new Git(repository);
                 TreeWalk tree = new TreeWalk(repository)) {
                tree.addTree(git.log().call().iterator().next().getTree());
                tree.setRecursive(true);
                boolean authored = false;
                while (tree.next()) {
                    String path = tree.getPathString();
                    assertFalse(path.startsWith(".copperbench/") || path.equals(".git"), path);
                    authored |= path.equals("authored.txt");
                }
                assertTrue(authored, "Normal workspace content must still be checkpointed");
            }
            assertEquals("retained runtime bytes", Files.readString(runtime));
            assertEquals("updated authored content", Files.readString(source));
            if (previouslyTracked)
                assertTrue(Files.readString(gitDirectory.resolve("info/exclude")).startsWith("# Keep custom ignores\n*.private\n"));
        } finally {
            PreferencesManager.PREFERENCES.backups.enableLocalHistory.set(original);
        }
    }
}
