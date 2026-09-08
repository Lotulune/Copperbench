/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package dev.copperbench.diagnostics;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceMutationGateway;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicitly-invoked exporter for the Stage 13 installed-product Diagnostics replay.
 *
 * <p>The fixture is generated through the real MCreator workspace/session path, then copied and broken by changing
 * only Copperbench's stored Core value for {@code maxStackSize}. Normal test runs do not execute this exporter.</p>
 */
@EnabledIfEnvironmentVariable(named = "COPPERBENCH_STAGE13_DIAGNOSTICS_FIXTURE", matches = "true")
class Stage13DiagnosticsFixtureExportTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC);
	private static final UUID WORKSPACE_ID = UUID.fromString("13131313-1313-4313-8313-131313131313");
	private static final String ELEMENT_NAME = "diagnostic_compass";

	@BeforeAll static void initializeUpstreamRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test void exportsProductReadableValidAndBrokenWorkspaces() throws Exception {
		Path output = Path.of(System.getProperty("copperbench.stage13.diagnostics.fixture.output",
				"build/stage13-diagnostics-fixture")).toAbsolutePath().normalize();
		deleteTree(output);
		Path valid = output.resolve("valid");
		Files.createDirectories(valid);

		WorkspaceSettings settings = new WorkspaceSettings("stage13_diagnostics");
		settings.setModName("Stage 13 Diagnostics");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("fabric-1.21.1");
		Path workspaceFile = valid.resolve("stage13_diagnostics.mcreator");
		AtomicLong ids = new AtomicLong(13_000);
		String elementId;
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings)) {
			assertTrue(workspace.getGenerator().generateBase());
			try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace, WORKSPACE_ID,
					new InMemoryWorkspaceTaskGateway(CLOCK, () -> uuid(ids.incrementAndGet())), CLOCK,
					() -> uuid(ids.incrementAndGet()))) {
				JsonObject fields = new JsonObject();
				fields.addProperty("maxStackSize", 16);
				fields.addProperty("fabric_exclusive", true);
				JsonObject values = new JsonObject();
				values.addProperty("displayName", "Diagnostic Compass");
				values.addProperty("texture", "minecraft:compass");
				values.add("fields", fields);
				JsonObject payload = new JsonObject();
				payload.addProperty("clientMutationId", uuid(13_001).toString());
				payload.addProperty("elementType", "item");
				payload.addProperty("name", ELEMENT_NAME);
				payload.add("initialValues", values);
				var created = session.uiEntry().execute(Command.of(uuid(13_002), session.workspaceId(), 0,
						Operation.CREATE_MOD_ELEMENT, payload));
				assertEquals("committed", created.result().status(), created.result().diagnostics().toString());
				elementId = created.result().data().getAsJsonObject().getAsJsonObject("element")
						.get("id").getAsString();
			}
		}

		Path elementFile = valid.resolve("elements").resolve(ELEMENT_NAME + ".mod.json");
		assertTrue(Files.isRegularFile(elementFile));
		JsonObject validWorkspace = JsonParser.parseString(Files.readString(workspaceFile)).getAsJsonObject();
		JsonObject validElementRecord = findElementRecord(validWorkspace, ELEMENT_NAME);
		assertNotNull(validElementRecord, "Copperbench Mod Element record must be present in the exported workspace");
		JsonObject storedValues = validElementRecord.getAsJsonObject("metadata")
				.getAsJsonObject(MCreatorWorkspaceMutationGateway.ELEMENT_VALUES_METADATA);
		assertEquals(16, storedValues.getAsJsonObject("fields").get("maxStackSize").getAsInt());

		Path broken = output.resolve("broken");
		copyTree(valid, broken);
		Path brokenWorkspaceFile = broken.resolve("stage13_diagnostics.mcreator");
		JsonObject brokenWorkspace = JsonParser.parseString(Files.readString(brokenWorkspaceFile)).getAsJsonObject();
		JsonObject brokenElementRecord = findElementRecord(brokenWorkspace, ELEMENT_NAME);
		assertNotNull(brokenElementRecord);
		brokenElementRecord.getAsJsonObject("metadata")
				.getAsJsonObject(MCreatorWorkspaceMutationGateway.ELEMENT_VALUES_METADATA)
				.getAsJsonObject("fields").addProperty("maxStackSize", 0);
		Files.writeString(brokenWorkspaceFile, new GsonBuilder().setPrettyPrinting().create().toJson(brokenWorkspace),
				StandardCharsets.UTF_8);

		JsonObject manifest = new JsonObject();
		manifest.addProperty("schemaVersion", "1.0");
		manifest.addProperty("workspaceFile", "stage13_diagnostics.mcreator");
		manifest.addProperty("elementFile", "elements/" + ELEMENT_NAME + ".mod.json");
		manifest.addProperty("valueStorage", "workspace.mcreator/mod_elements[].metadata/dev.copperbench.values");
		manifest.addProperty("elementId", elementId);
		manifest.addProperty("elementName", ELEMENT_NAME);
		manifest.addProperty("diagnosticCode", "FABRIC_ITEM_STACK_INVALID");
		manifest.addProperty("diagnosticPath", "/elements/" + elementId + "/values/fields/maxStackSize");
		manifest.addProperty("actionTarget", "/fields/maxStackSize");
		manifest.addProperty("brokenValue", 0);
		manifest.addProperty("repairValue", 1);
		Files.writeString(output.resolve("manifest.json"), manifest.toString(), StandardCharsets.UTF_8);
	}

	private static JsonObject findElementRecord(JsonObject workspace, String elementName) {
		if (!workspace.has("mod_elements") || !workspace.get("mod_elements").isJsonArray()) return null;
		for (JsonElement raw : workspace.getAsJsonArray("mod_elements")) {
			if (!raw.isJsonObject()) continue;
			JsonObject element = raw.getAsJsonObject();
			if (element.has("name") && elementName.equals(element.get("name").getAsString())) return element;
		}
		return null;
	}

	private static void copyTree(Path source, Path target) throws IOException {
		try (var paths = Files.walk(source)) {
			for (Path path : paths.toList()) {
				Path destination = target.resolve(source.relativize(path).toString());
				if (Files.isDirectory(path)) Files.createDirectories(destination);
				else {
					Files.createDirectories(destination.getParent());
					Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				}
			}
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
		}
	}

	private static UUID uuid(long suffix) {
		return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", suffix));
	}
}
