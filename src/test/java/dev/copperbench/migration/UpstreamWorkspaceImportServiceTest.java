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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		assertEquals(Disposition.MANUAL, item(report, "/elements/trail_golem").disposition());
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
