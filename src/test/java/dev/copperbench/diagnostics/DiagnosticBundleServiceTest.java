/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.diagnostics;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticBundleServiceTest {

	@TempDir Path root;

	@Test void defaultBundleRedactsSensitiveDataAndExcludesWorkspaceFiles() throws Exception {
		Path logs = Files.createDirectories(root.resolve("logs"));
		Path workspace = Files.createDirectories(root.resolve("workspace"));
		Files.writeString(logs.resolve("mcreator.log"),
				"Authorization: Bearer secret-token password=hunter2 C:\\Users\\alice\\private\\mod.java");
		Files.writeString(workspace.resolve("example.mcreator"), "private workspace content");
		JsonObject tasks = new JsonObject();
		tasks.addProperty("externalPath", "D:\\mods\\private.gradle");
		tasks.addProperty("unixPath", "/opt/projects/private.gradle");
		var service = service(logs, workspace, tasks);

		var result = service.export(UUID.fromString("11111111-1111-4111-8111-111111111111"), false);

		try (ZipFile zip = new ZipFile(result.path().toFile())) {
			assertTrue(zip.getEntry("environment.json") != null);
			assertTrue(zip.getEntry("tasks.json") != null);
			assertTrue(zip.getEntry("logs/mcreator.log") != null);
			assertTrue(zip.stream().noneMatch(entry -> entry.getName().startsWith("reproduction/")));
			String log = new String(zip.getInputStream(zip.getEntry("logs/mcreator.log")).readAllBytes());
			assertFalse(log.contains("secret-token"));
			assertFalse(log.contains("hunter2"));
			assertFalse(log.contains("alice"));
			assertFalse(log.contains("C:\\Users"));
			String task = new String(zip.getInputStream(zip.getEntry("tasks.json")).readAllBytes());
			assertFalse(task.contains("D:\\mods"));
			assertFalse(task.contains("/opt/projects"));
		}
	}

	@Test void explicitConsentIncludesOnlyBoundedReproductionFiles() throws Exception {
		Path logs = Files.createDirectories(root.resolve("logs"));
		Path workspace = Files.createDirectories(root.resolve("workspace"));
		Files.writeString(workspace.resolve("example.mcreator"), "workspace descriptor");
		Files.writeString(workspace.resolve("Element.mod.json"), "{}");
		Files.createDirectories(workspace.resolve("build"));
		Files.writeString(workspace.resolve("build/secret.java"), "excluded");

		var result = service(logs, workspace, new JsonObject()).export(null, true);

		assertEquals(2, result.reproductionFileCount());
		try (ZipFile zip = new ZipFile(result.path().toFile())) {
			assertTrue(zip.getEntry("reproduction/example.mcreator") != null);
			assertTrue(zip.getEntry("reproduction/Element.mod.json") != null);
			assertTrue(zip.getEntry("reproduction/build/secret.java") == null);
			String manifest = new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes());
			assertTrue(manifest.contains("\"userConfirmedWorkspaceFiles\": true"));
		}
	}

	@Test void repeatedExportsNeverOverwriteAPreviousBundle() throws Exception {
		Path logs = Files.createDirectories(root.resolve("logs"));
		Path workspace = Files.createDirectories(root.resolve("workspace"));
		var service = service(logs, workspace, new JsonObject());

		var first = service.export(null, false);
		var second = service.export(null, false);

		assertFalse(first.path().equals(second.path()));
		assertTrue(Files.isRegularFile(first.path()));
		assertTrue(Files.isRegularFile(second.path()));
	}

	@Test void aggregateLogInputIsBounded() throws Exception {
		Path logs = Files.createDirectories(root.resolve("logs"));
		Path workspace = Files.createDirectories(root.resolve("workspace"));
		for (int index = 0; index < 140; index++) {
			Files.writeString(logs.resolve(String.format("rotated-%03d.log", index)), "log-" + index);
		}

		var result = service(logs, workspace, new JsonObject()).export(null, false);

		try (ZipFile zip = new ZipFile(result.path().toFile())) {
			long includedLogs = zip.stream().filter(entry -> entry.getName().startsWith("logs/")).count();
			assertTrue(includedLogs <= 128);
		}
	}

	private DiagnosticBundleService service(Path logs, Path workspace, JsonObject tasks) {
		return new DiagnosticBundleService(root.resolve("output"), logs, workspace, () -> tasks,
				Clock.fixed(Instant.parse("2026-08-29T01:00:00Z"), ZoneOffset.UTC));
	}
}
