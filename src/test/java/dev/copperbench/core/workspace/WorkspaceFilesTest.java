/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceFilesTest {

	@TempDir Path temp;

	@Test void failedWriterPreservesExistingOutputAndRemovesPartialTemporaryFile() throws Exception {
		Path workspace = Files.createDirectory(temp.resolve("workspace"));
		Path output = Files.writeString(workspace.resolve("output.json"), "previous complete content");

		assertThrows(IOException.class, () -> WorkspaceFiles.writeAtomically(workspace, output, stream -> {
			stream.write("partial new content".getBytes(StandardCharsets.UTF_8));
			throw new IOException("Simulated write failure");
		}));

		assertEquals("previous complete content", Files.readString(output));
		try (var files = Files.list(workspace)) {
			assertEquals(List.of("output.json"), files.map(path -> path.getFileName().toString()).toList());
		}
	}

	@Test void rejectedParentRedirectionCannotDeleteAnOutsideFileDuringCleanup() throws Exception {
		Path workspace = Files.createDirectory(temp.resolve("workspace"));
		Path parent = Files.createDirectory(workspace.resolve("exports"));
		Path outside = Files.createDirectory(temp.resolve("outside"));
		AtomicReference<Path> outsideFile = new AtomicReference<>();

		assertThrows(IOException.class, () -> WorkspaceFiles.writeAtomically(workspace,
				parent.resolve("output.json"), stream -> {
			stream.write("complete content".getBytes(StandardCharsets.UTF_8));
			stream.close();
			Path temporary;
			try (var files = Files.list(parent)) {
				temporary = files.findFirst().orElseThrow();
			}
			outsideFile.set(Files.writeString(outside.resolve(temporary.getFileName()), "outside user content"));
			Files.move(parent, workspace.resolve("moved-exports"));
			createDirectoryLink(parent, outside);
		}));

		assertEquals(outside.toRealPath(), parent.toRealPath(), "The test must reach the redirected-parent state");
		assertEquals("outside user content", Files.readString(outsideFile.get()));
	}

	private static void createDirectoryLink(Path link, Path target) throws IOException {
		if (java.io.File.separatorChar == '\\') {
			Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString())
					.redirectErrorStream(true).start();
			String detail = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			try {
				assertEquals(0, process.waitFor(), detail);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IOException("Directory link setup was interrupted", exception);
			}
		} else {
			Files.createSymbolicLink(link, target);
		}
	}
}
