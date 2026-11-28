package dev.copperbench.core.workspace.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleProjectSettingsTest {

	@TempDir Path root;

	private void metadata(String json) throws Exception {
		Path file = root.resolve(GradleProjectWorkspaceSession.METADATA_FILE);
		Files.createDirectories(file.getParent());
		Files.writeString(file, json, StandardCharsets.UTF_8);
	}

	@Test void missingOrUnreadableSettingsMeanTheDefaults() throws Exception {
		var defaults = new GradleProjectSettings(null, false);
		assertEquals(defaults, GradleProjectSettings.read(root));
		metadata("not json");
		assertEquals(defaults, GradleProjectSettings.read(root));
		metadata("{\"settings\":{\"gitIgnore\":\"maybe\",\"gradleUserHome\":true}}");
		assertEquals(defaults, GradleProjectSettings.read(root), "unknown values fall back instead of failing a build");
		metadata("{\"settings\":{\"gitIgnore\":\"declined\",\"gradleUserHome\":\"environment\"}}");
		assertEquals(new GradleProjectSettings(GradleProjectSettings.DECLINED, true), GradleProjectSettings.read(root));
	}

	@Test void recognizesTheUsualWaysToIgnoreTheFolder() throws Exception {
		assertFalse(GradleProjectSettings.ignoredByGit(root), "no .gitignore");
		for (String line : new String[] { ".copperbench/", "/.copperbench", ".copperbench", "  .copperbench/**  ", "/.copperbench/*" }) {
			Files.writeString(root.resolve(".gitignore"), "build/\n" + line + "\n");
			assertTrue(GradleProjectSettings.ignoredByGit(root), line);
		}
		for (String line : new String[] { "# .copperbench/", "!.copperbench/", ".copperbench-old/", "src/.copperbench/" }) {
			Files.writeString(root.resolve(".gitignore"), "build/\n" + line + "\n");
			assertFalse(GradleProjectSettings.ignoredByGit(root), line);
		}
	}

	@Test void appendingKeepsTheFilesLineEndingsAndAddsTheEntryOnce() throws Exception {
		Files.writeString(root.resolve(".gitignore"), "build/\r\n.gradle");
		GradleProjectSettings.addToGitIgnore(root);
		assertEquals("build/\r\n.gradle\r\n.copperbench/\r\n", Files.readString(root.resolve(".gitignore")));
		GradleProjectSettings.addToGitIgnore(root);
		assertEquals("build/\r\n.gradle\r\n.copperbench/\r\n", Files.readString(root.resolve(".gitignore")), "already ignored");

		Files.writeString(root.resolve(".gitignore"), "build/\n");
		GradleProjectSettings.addToGitIgnore(root);
		assertEquals("build/\n.copperbench/\n", Files.readString(root.resolve(".gitignore")));

		Files.delete(root.resolve(".gitignore"));
		GradleProjectSettings.addToGitIgnore(root);
		assertEquals(".copperbench/\n", Files.readString(root.resolve(".gitignore")));
	}

	@Test void aRepositoryIsFoundFromTheProjectOrAnyParent() throws Exception {
		Path project = Files.createDirectories(root.resolve("repo/mods/emberkit"));
		org.junit.jupiter.api.Assumptions.assumeFalse(GradleProjectSettings.inGitRepository(project),
				"the temporary directory is itself inside a Git working tree");
		Files.createDirectory(root.resolve("repo/.git"));
		assertTrue(GradleProjectSettings.inGitRepository(project));
		// A worktree or submodule has a .git file instead of a directory.
		Path worktree = Files.createDirectories(root.resolve("worktree"));
		Files.writeString(worktree.resolve(".git"), "gitdir: ../repo/.git/worktrees/w\n");
		assertTrue(GradleProjectSettings.inGitRepository(worktree));
	}
}
