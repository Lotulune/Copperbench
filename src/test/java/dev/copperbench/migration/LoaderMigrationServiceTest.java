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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderMigrationServiceTest {

	@TempDir Path temp;

	private final LoaderMigrationService service = new LoaderMigrationService(VersionTrackCatalog.official());

	@Test void previewMarksAllJavaTypesSupportedAndLoaderExclusiveFieldsManual() {
		MigrationReport report = service.preview(workspace(true), "neoforge-1.21.1");
		assertTrue(report.complete());
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
