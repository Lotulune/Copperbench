/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.migration;

import com.google.gson.JsonParser;
import dev.copperbench.migration.MigrationReport.Disposition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UpstreamWorkspaceImportServiceTest {

	@TempDir Path temp;

	private final UpstreamWorkspaceImportService service = new UpstreamWorkspaceImportService();

	@Test void previewPreservesUnknownFieldsAndDoesNotRequireACopy() throws Exception {
		Path source = upstream();
		String before = WorkspaceTreeHasher.hash(source);
		MigrationReport report = service.preview(source);
		assertEquals(before, WorkspaceTreeHasher.hash(source));
		assertEquals(Disposition.MANUAL, item(report, "/futurePluginBlob").disposition());
		assertEquals(Disposition.SUPPORTED, item(report, "/elements/trail_lamp").disposition());
		assertEquals(Disposition.SUPPORTED, item(report, "/elements/trail_golem").disposition());
	}

	@Test void executeCopiesToANewDirectoryAndLeavesSourceBytesUnchanged() throws Exception {
		Path source = upstream();
		String before = Files.readString(source.resolve("workspace.mcreator"));
		Path target = temp.resolve("imported");
		MigrationReport report = service.execute(source, target);
		assertTrue(report.sourceUnchanged());
		assertEquals(before, Files.readString(source.resolve("workspace.mcreator")));
		assertTrue(Files.isRegularFile(target.resolve("workspace.mcreator")));
		assertTrue(Files.isRegularFile(target.resolve(".copperbench/import/report.json")));
		assertEquals("fabric-1.21.1",
				JsonParser.parseString(Files.readString(target.resolve("workspace.mcreator"))).getAsJsonObject()
						.getAsJsonObject("workspaceSettings").get("currentGenerator").getAsString());
	}

	@Test void missingWorkspaceFileIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> service.preview(temp.resolve("empty")));
	}

	@Test void copyingPreservesExecutableWorkspaceScripts() throws Exception {
		Path source = upstream();
		assumeTrue(Files.getFileAttributeView(source, PosixFileAttributeView.class) != null,
				"POSIX permissions are unavailable in this test environment");
		Path script = Files.writeString(source.resolve("gradlew"), "#!/bin/sh\nexit 0\n");
		Set<PosixFilePermission> permissions = Set.of(PosixFilePermission.OWNER_READ,
				PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
		Files.setPosixFilePermissions(script, permissions);
		Path target = temp.resolve("imported");

		service.execute(source, target);

		assertEquals(permissions, Files.getPosixFilePermissions(target.resolve("gradlew")));
		assertEquals(Files.readString(script), Files.readString(target.resolve("gradlew")));
	}

	@Test void previewAndExecuteRejectSourceFileLinksBeforeCreatingTheTarget() throws Exception {
		Path source = upstream();
		String descriptorBefore = Files.readString(source.resolve("workspace.mcreator"));
		Path outside = temp.resolve("external-document.txt");
		Files.writeString(outside, "private fixture content");
		createFileLink(source.resolve("copied-secret.txt"), outside);
		Path target = temp.resolve("imported");

		assertThrows(IOException.class, () -> service.preview(source));
		assertThrows(IOException.class, () -> service.execute(source, target));

		assertFalse(Files.exists(target), "A rejected source must not leave a partial import");
		assertEquals(descriptorBefore, Files.readString(source.resolve("workspace.mcreator")));
		assertEquals("private fixture content", Files.readString(outside));
	}

	@Test void executeRejectsSiblingTargetLinkedIntoAnExcludedSourceDirectory() throws Exception {
		Path source = upstream();
		Path internal = Files.createDirectory(source.resolve("internal.tmp"));
		Path target = temp.resolve("imported");
		createDirectoryLink(target, internal);
		String before = WorkspaceTreeHasher.hash(source);

		assertThrows(IOException.class, () -> service.execute(source, target));

		assertEquals(before, WorkspaceTreeHasher.hash(source));
		try (var children = Files.list(internal)) {
			assertTrue(children.findAny().isEmpty(), "Source hash exclusions must not hide import writes");
		}
	}

	@Test void executeRejectsTargetWithARedirectedAncestorBeforeCreatingMissingParents() throws Exception {
		Path source = upstream();
		Path outside = Files.createDirectory(temp.resolve("outside"));
		Path linkedParent = temp.resolve("linked-output");
		createDirectoryLink(linkedParent, outside);
		Path target = linkedParent.resolve("not-created/imported");
		String before = WorkspaceTreeHasher.hash(source);

		assertThrows(IOException.class, () -> service.execute(source, target));

		assertFalse(Files.exists(outside.resolve("not-created")), "Reject before creating directories through a link");
		assertEquals(before, WorkspaceTreeHasher.hash(source));
	}

	@Test void previewAndExecuteRejectAnExternallyLinkedWorkspaceDescriptor() throws Exception {
		Path source = upstream();
		Path descriptor = source.resolve("workspace.mcreator");
		Path outside = Files.createDirectory(temp.resolve("outside"));
		Path externalDescriptor = Files.move(descriptor, outside.resolve("workspace.mcreator"));
		Files.writeString(outside.resolve("external-document.txt"), "private fixture content");
		String before = Files.readString(externalDescriptor);
		createFileLink(descriptor, externalDescriptor);
		Path target = temp.resolve("imported");

		assertThrows(IOException.class, () -> service.preview(source));
		assertThrows(IOException.class, () -> service.execute(source, target));

		assertFalse(Files.exists(target), "An external descriptor must never select its parent as the import source");
		assertEquals(before, Files.readString(externalDescriptor));
		assertEquals("private fixture content", Files.readString(outside.resolve("external-document.txt")));
	}

	@Test void executeCreatesMissingTargetParentsForAnOrdinarySiblingDirectory() throws Exception {
		Path source = upstream();
		String before = WorkspaceTreeHasher.hash(source);
		Path target = temp.resolve("new-parent/nested/imported");

		MigrationReport report = service.execute(source, target);

		assertTrue(report.complete());
		assertTrue(report.sourceUnchanged());
		assertEquals(before, WorkspaceTreeHasher.hash(source));
		assertEquals(Files.readString(source.resolve("workspace.mcreator")),
				Files.readString(target.resolve("workspace.mcreator")));
		assertTrue(Files.isRegularFile(target.resolve(".copperbench/import/report.json")));
	}

	@Test void executeAcceptsAnExistingEmptyTargetDirectory() throws Exception {
		Path source = upstream();
		String before = WorkspaceTreeHasher.hash(source);
		Path target = Files.createDirectory(temp.resolve("existing-import"));

		MigrationReport report = service.execute(source, target);

		assertTrue(report.complete());
		assertTrue(report.sourceUnchanged());
		assertEquals(before, WorkspaceTreeHasher.hash(source));
		assertEquals(Files.readString(source.resolve("workspace.mcreator")),
				Files.readString(target.resolve("workspace.mcreator")));
		assertTrue(Files.isRegularFile(target.resolve(".copperbench/import/report.json")));
	}

	private static void createFileLink(Path link, Path target) {
		try {
			Files.createSymbolicLink(link, target);
		} catch (UnsupportedOperationException | IOException exception) {
			abort("File symbolic links are unavailable in this test environment: " + exception.getMessage());
		}
	}

	private static void createDirectoryLink(Path link, Path target) throws Exception {
		if (java.io.File.separatorChar == '\\') {
			Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString())
					.redirectErrorStream(true).start();
			String detail = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			assertEquals(0, process.waitFor(), detail);
		} else {
			Files.createSymbolicLink(link, target);
		}
	}

	private Path upstream() throws Exception {
		Path source = temp.resolve("upstream");
		Files.createDirectories(source.resolve("elements"));
		Files.writeString(source.resolve("workspace.mcreator"), """
				{
				  "workspaceSettings": { "modName": "Copper Trails", "currentGenerator": "fabric-1.21.1" },
				  "futurePluginBlob": { "keep": true }
				}
				""", StandardCharsets.UTF_8);
		Files.writeString(source.resolve("elements/trail_lamp.mod.json"), "{\"_type\":\"block\"}",
				StandardCharsets.UTF_8);
		Files.writeString(source.resolve("elements/trail_golem.mod.json"), "{\"_type\":\"livingentity\"}",
				StandardCharsets.UTF_8);
		return source;
	}

	private static MigrationReport.MigrationItem item(MigrationReport report, String path) {
		return report.items().stream().filter(candidate -> candidate.path().equals(path)).findFirst().orElseThrow();
	}
}
