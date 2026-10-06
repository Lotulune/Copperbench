/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.migration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import dev.copperbench.migration.MigrationReport.Disposition;
import dev.copperbench.tracks.VersionTrackCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

class LoaderMigrationServiceTest {

	@TempDir Path temp;

	private final LoaderMigrationService service = new LoaderMigrationService(VersionTrackCatalog.official());

	@Test void previewMarksAllJavaTypesSupportedAndLoaderExclusiveFieldsManual() {
		MigrationReport report = service.preview(workspace(true), "neoforge-1.21.1");
		assertTrue(report.complete());
		assertEquals(null, report.semanticComparison());
		assertEquals(Disposition.SUPPORTED, item(report, "/elements/" + id(1)).disposition());
		assertEquals(Disposition.MANUAL, item(report, "/elements/" + id(2)).disposition());
		assertEquals(Disposition.SUPPORTED, item(report, "/elements/" + id(3)).disposition());
		assertEquals(Disposition.MANUAL, item(report, "/upstream/plugin.example").disposition());
	}

	@Test void unavailableTargetIsBlockedAndDoesNotWriteACopy() throws Exception {
		Path target = temp.resolve("blocked-copy");
		MigrationReport report = service.execute(workspace(false), "neoforge-26.3", null, target);
		assertFalse(report.complete());
		assertEquals("UNSUPPORTED_GENERATOR", item(report, "/generator").reasonCode());
		assertFalse(Files.exists(target.resolve("migration-report.json")));
	}

	@Test void executesA1201CopyBetweenFirstPartyMaintenanceLoaders() throws Exception {
		Path target = temp.resolve("copy-1201");
		MigrationReport report = service.execute(Fabric1211GoldenWorkspace.create1201(), "neoforge-1.20.1", null,
				target);
		assertTrue(report.complete());
		assertTrue(report.sourceUnchanged());
		assertEquals(null, report.semanticComparison());
		assertTrue(Files.isRegularFile(target.resolve("migration-report.json")));
	}

	@Test void executeCopiesToANewDirectoryAndLeavesTheSourceHashUnchanged() throws Exception {
		Path source = temp.resolve("source");
		Files.createDirectories(source.resolve("elements"));
		Files.writeString(source.resolve("workspace.mcreator"), """
				{"workspaceSettings":{"modName":"Copper Trails","currentGenerator":"fabric-1.21.1"},"plugin.example":{"keep":true}}
				""", StandardCharsets.UTF_8);
		Files.writeString(source.resolve("elements/trail_lamp.mod.json"), "{\"_type\":\"block\"}", StandardCharsets.UTF_8);
		String before = WorkspaceTreeHasher.hash(source);
		Path first = temp.resolve("copy-a");
		Path second = temp.resolve("copy-b");
		WorkspaceState state = workspace(false);
		MigrationReport one = service.execute(state, "neoforge-1.21.1", source, first);
		MigrationReport two = service.execute(state, "neoforge-1.21.1", source, second);
		assertTrue(one.sourceUnchanged());
		assertTrue(two.sourceUnchanged());
		assertEquals(before, WorkspaceTreeHasher.hash(source));
		assertEquals(one.items(), two.items());
		JsonObject rewritten = JsonParser.parseString(Files.readString(first.resolve("workspace.mcreator")))
				.getAsJsonObject();
		assertEquals("neoforge-1.21.1", rewritten.getAsJsonObject("workspaceSettings").get("currentGenerator")
				.getAsString());
		assertTrue(rewritten.getAsJsonObject("plugin.example").get("keep").getAsBoolean());
		assertEquals(before, WorkspaceTreeHasher.hash(source));
		assertTrue(one.semanticComparison().generatorChanged());
		assertTrue(one.semanticComparison().workspaceMetadataPreserved());
		assertEquals(1, one.semanticComparison().preservedElementCount());
		assertEquals(0, one.semanticComparison().changedElementCount());
		assertEquals(0, one.semanticComparison().addedElementCount());
		assertEquals(0, one.semanticComparison().removedElementCount());
		assertEquals(1, one.semanticComparison().changes().size());
		assertEquals("/generator", one.semanticComparison().changes().getFirst().path());
		JsonObject persistedReport = JsonParser.parseString(Files.readString(first.resolve("migration-report.json")))
				.getAsJsonObject();
		assertTrue(persistedReport.getAsJsonObject("semanticComparison").get("generatorChanged").getAsBoolean());
		assertEquals(1, persistedReport.getAsJsonObject("semanticComparison").get("preservedElementCount").getAsInt());
	}

	@Test void executeRejectsSourceFileLinksBeforeCreatingTheTarget() throws Exception {
		Path source = sourceWorkspace();
		String descriptorBefore = Files.readString(source.resolve("workspace.mcreator"));
		Path outside = temp.resolve("external-document.txt");
		Files.writeString(outside, "private fixture content");
		createFileLink(source.resolve("copied-secret.txt"), outside);
		Path target = temp.resolve("copy");

		assertThrows(IOException.class, () -> service.execute(workspace(false), "neoforge-1.21.1", source, target));

		assertFalse(Files.exists(target), "A rejected source must not leave a partial migration");
		assertEquals(descriptorBefore, Files.readString(source.resolve("workspace.mcreator")));
		assertEquals("private fixture content", Files.readString(outside));
	}

	@Test void executeRejectsSiblingTargetLinkedIntoAnExcludedSourceDirectory() throws Exception {
		Path source = sourceWorkspace();
		Path internal = Files.createDirectory(source.resolve("internal.tmp"));
		Path target = temp.resolve("copy");
		createDirectoryLink(target, internal);
		String before = WorkspaceTreeHasher.hash(source);

		assertThrows(IOException.class, () -> service.execute(workspace(false), "neoforge-1.21.1", source, target));

		assertEquals(before, WorkspaceTreeHasher.hash(source));
		try (var children = Files.list(internal)) {
			assertTrue(children.findAny().isEmpty(), "Source hash exclusions must not hide migration writes");
		}
	}

	@Test void executeRejectsTargetWithARedirectedAncestorBeforeCreatingMissingParents() throws Exception {
		Path source = sourceWorkspace();
		Path outside = Files.createDirectory(temp.resolve("outside"));
		Path linkedParent = temp.resolve("linked-output");
		createDirectoryLink(linkedParent, outside);
		Path target = linkedParent.resolve("not-created/copy");
		String before = WorkspaceTreeHasher.hash(source);

		assertThrows(IOException.class, () -> service.execute(workspace(false), "neoforge-1.21.1", source, target));

		assertFalse(Files.exists(outside.resolve("not-created")), "Reject before creating directories through a link");
		assertEquals(before, WorkspaceTreeHasher.hash(source));
	}

	@Test void executeRejectsWorkspaceDescriptorsLinkedOutsideTheSource() throws Exception {
		Path source = sourceWorkspace();
		Path descriptor = source.resolve("workspace.mcreator");
		Path outside = Files.createDirectory(temp.resolve("outside"));
		Path externalDescriptor = Files.move(descriptor, outside.resolve("workspace.mcreator"));
		String before = Files.readString(externalDescriptor);
		createFileLink(descriptor, externalDescriptor);
		Path target = temp.resolve("copy");

		assertThrows(IOException.class, () -> service.execute(workspace(false), "neoforge-1.21.1", source, target));

		assertFalse(Files.exists(target));
		assertEquals(before, Files.readString(externalDescriptor));
	}

	@Test void executeCreatesMissingTargetParentsForAnOrdinarySiblingDirectory() throws Exception {
		Path source = sourceWorkspace();
		String before = WorkspaceTreeHasher.hash(source);
		Path target = temp.resolve("new-parent/nested/copy");

		MigrationReport report = service.execute(workspace(false), "neoforge-1.21.1", source, target);

		assertTrue(report.complete());
		assertTrue(report.sourceUnchanged());
		assertEquals(before, WorkspaceTreeHasher.hash(source));
		assertEquals("neoforge-1.21.1", JsonParser.parseString(Files.readString(target.resolve("workspace.mcreator")))
				.getAsJsonObject().getAsJsonObject("workspaceSettings").get("currentGenerator").getAsString());
		assertTrue(Files.isRegularFile(target.resolve("migration-report.json")));
	}

	@Test void executeAcceptsAnExistingEmptyTargetDirectory() throws Exception {
		Path source = sourceWorkspace();
		String before = WorkspaceTreeHasher.hash(source);
		Path target = Files.createDirectory(temp.resolve("existing-copy"));

		MigrationReport report = service.execute(workspace(false), "neoforge-1.21.1", source, target);

		assertTrue(report.complete());
		assertTrue(report.sourceUnchanged());
		assertEquals(before, WorkspaceTreeHasher.hash(source));
		assertEquals("neoforge-1.21.1", JsonParser.parseString(Files.readString(target.resolve("workspace.mcreator")))
				.getAsJsonObject().getAsJsonObject("workspaceSettings").get("currentGenerator").getAsString());
		assertTrue(Files.isRegularFile(target.resolve("migration-report.json")));
	}

	private Path sourceWorkspace() throws IOException {
		Path source = Files.createDirectory(temp.resolve("source"));
		Files.writeString(source.resolve("workspace.mcreator"), """
				{"workspaceSettings":{"modName":"Copper Trails","currentGenerator":"fabric-1.21.1"}}
				""", StandardCharsets.UTF_8);
		return source;
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

	private static MigrationReport.MigrationItem item(MigrationReport report, String path) {
		return report.items().stream().filter(candidate -> candidate.path().equals(path)).findFirst().orElseThrow();
	}

	private static WorkspaceState workspace(boolean withExclusive) {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", "fabric-1.21.1");
		generator.addProperty("loader", "fabric");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Fabric 1.21.1");
		generator.addProperty("state", "ready");
		JsonObject document = new JsonObject();
		JsonObject plugin = new JsonObject();
		plugin.addProperty("keep", true);
		document.add("plugin.example", plugin);
		JsonObject lampFields = new JsonObject();
		lampFields.addProperty("hardness", 3.0);
		JsonObject compassFields = new JsonObject();
		compassFields.addProperty("maxStackSize", 16);
		if (withExclusive)
			compassFields.addProperty("fabric_exclusive", true);
		Instant now = Instant.parse("2026-08-19T00:00:00Z");
		return new WorkspaceState(UUID.fromString("11111111-1111-4111-8111-111111111111"), "Copper Trails", "mod",
				1, false, generator, document, List.of(
						element(1, "block", "trail_lamp", lampFields, now),
						element(2, "item", "trail_compass", compassFields, now),
						element(3, "livingentity", "trail_golem", new JsonObject(), now)));
	}

	private static Element element(long suffix, String type, String name, JsonObject fields, Instant now) {
		JsonObject values = new JsonObject();
		values.add("fields", fields);
		return new Element(id(suffix), type, name, name, "valid", "generated", now, values);
	}

	private static UUID id(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
