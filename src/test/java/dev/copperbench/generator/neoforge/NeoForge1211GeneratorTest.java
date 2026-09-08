/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.BundledJdkLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForge1211GeneratorTest {

	@TempDir Path output;

	@Test void generatesTheParityBlockItemRecipeProcedureAndResources() throws Exception {
		Path distribution = Path.of(".").toAbsolutePath().normalize();
		var generator = new NeoForge1211Generator(distribution);

		var result = generator.generate(output, NeoForge1211GoldenWorkspace.create());

		assertEquals(NeoForge1211Generator.GENERATOR_ID, result.generatorId());
		assertEquals("copper_trails", result.modId());
		assertTrue(result.generatedPaths().contains("src/main/resources/META-INF/neoforge.mods.toml"));
		assertTrue(Files.readString(output.resolve("src/main/resources/META-INF/neoforge.mods.toml"))
				.contains("type=\"required\""));
		assertTrue(result.generatedPaths().contains("src/main/java/dev/coppertrails/CopperTrailsMod.java"));
		assertTrue(result.generatedPaths().contains("src/main/resources/data/copper_trails/recipe/trail_lamp.json"));
		assertFalse(Files.exists(output.resolve("src/main/resources/fabric.mod.json")));
		assertTrue(Files.readString(output.resolve("build.gradle")).contains("net.neoforged.moddev"));
		String properties = Files.readString(output.resolve("gradle.properties"));
		assertTrue(properties.contains("neoforge_version=21.1.232"));
		String expectedJdk = BundledJdkLocator.locate(distribution, 21).toString().replace('\\', '/');
		assertTrue(properties.contains("org.gradle.java.installations.paths=" + expectedJdk));
		assertTrue(Files.readString(output.resolve("settings.gradle"))
				.contains("org.gradle.toolchains.foojay-resolver-convention"));
		assertTrue(Files.readString(output.resolve("gradle/wrapper/gradle-wrapper.properties"))
				.contains("mirrors.huaweicloud.com/gradle/gradle-9.7.0-bin.zip"));
		assertTrue(Files.readString(output.resolve("src/main/java/dev/coppertrails/CopperTrailsMod.java"))
				.contains("COPPERBENCH_STAGE5_NEOFORGE_READY"));
		assertTrue(Files.size(output.resolve(
				"src/main/resources/assets/copper_trails/textures/block/trail_lamp.png")) > 0);
	}

	@Test void materializedPluginWorkspaceKeepsBuildFilesAndRestoresMissingGradleRuntime() throws Exception {
		Path distribution = output.resolve("distribution");
		Path workspace = output.resolve("workspace");
		Files.createDirectories(distribution.resolve("gradle/wrapper"));
		Files.writeString(distribution.resolve("gradlew"), "unix-wrapper");
		Files.writeString(distribution.resolve("gradlew.bat"), "windows-wrapper");
		Files.write(distribution.resolve("gradle/wrapper/gradle-wrapper.jar"), new byte[] { 1, 2, 3 });
		Files.createDirectories(workspace.resolve("src/main/java/example"));
		Files.writeString(workspace.resolve("stage13.mcreator"), "{}\n");
		Files.writeString(workspace.resolve("src/main/java/example/UserCode.java"), "final class UserCode {}\n");
		String userBuild = "plugins { id 'example.neoforge-owned' version '1.0' }\n";
		Files.writeString(workspace.resolve("build.gradle"), userBuild);

		var generator = new NeoForge1211Generator(distribution, NeoForge1211Generator.Profile.NEOFORGE_1211,
				() -> distribution.resolve("jdk"));
		generator.generate(workspace, NeoForge1211GoldenWorkspace.create());

		assertEquals(userBuild, Files.readString(workspace.resolve("build.gradle")));
		assertEquals("windows-wrapper", Files.readString(workspace.resolve("gradlew.bat")));
		assertTrue(Files.isRegularFile(workspace.resolve("gradle/wrapper/gradle-wrapper.jar")));
		assertTrue(Files.readString(workspace.resolve("gradle/wrapper/gradle-wrapper.properties"))
				.contains("mirrors.huaweicloud.com/gradle/gradle-9.7.0-bin.zip"));
		assertEquals("final class UserCode {}\n",
				Files.readString(workspace.resolve("src/main/java/example/UserCode.java")));
	}

	@Test void installedJava21SidecarIsWrittenIntoGeneratedToolchainProperties() throws Exception {
		Path distribution = output.resolve("distribution");
		Path workspace = output.resolve("workspace");
		Path installedJdk = distribution.resolve("jdk21");
		Files.createDirectories(installedJdk.resolve("bin"));
		Files.write(installedJdk.resolve("bin/java.exe"), new byte[] { 0 });
		Files.createDirectories(distribution.resolve("gradle/wrapper"));
		Files.writeString(distribution.resolve("gradlew"), "placeholder");
		Files.writeString(distribution.resolve("gradlew.bat"), "placeholder");
		Files.write(distribution.resolve("gradle/wrapper/gradle-wrapper.jar"), new byte[] { 0 });

		var generator = new NeoForge1211Generator(distribution, NeoForge1211Generator.Profile.NEOFORGE_1211);
		generator.generate(workspace, NeoForge1211GoldenWorkspace.create());

		String properties = Files.readString(workspace.resolve("gradle.properties"));
		String expected = installedJdk.toAbsolutePath().normalize().toString().replace('\\', '/');
		assertTrue(properties.contains("org.gradle.java.installations.paths=" + expected));
		assertFalse(properties.contains("jdk/jdk21_win_64"));
	}

	@Test void deterministicNumericValidationRepairsSurviveNeoForgeCodeMapping() {
		WorkspaceState valid = NeoForge1211GoldenWorkspace.create();
		var elements = new ArrayList<>(valid.elements());
		var item = elements.get(1);
		var values = item.values();
		values.getAsJsonObject("fields").addProperty("maxStackSize", 99);
		elements.set(1, new WorkspaceState.Element(item.id(), item.type(), item.name(), item.displayName(),
				item.state(), item.ownership(), item.updatedAt(), values));
		WorkspaceState broken = new WorkspaceState(valid.id(), valid.name(), valid.kind(), valid.revision(),
				valid.dirty(), valid.generator(), valid.upstreamDocument(), elements);

		var issue = new NeoForge1211Generator(Path.of(".").toAbsolutePath().normalize()).validate(broken).stream()
				.filter(candidate -> candidate.code().equals("NEOFORGE_ITEM_STACK_INVALID"))
				.findFirst().orElseThrow();

		assertEquals(item.id(), issue.elementId());
		assertEquals("/elements/" + item.id() + "/values/fields/maxStackSize", issue.path());
		assertEquals(64, issue.repairValue().getAsInt());
	}

	@Test void procedureNodeAndPortIdentitySurvivesNeoForgeValidationMapping() {
		WorkspaceState valid = NeoForge1211GoldenWorkspace.create();
		var elements = new ArrayList<>(valid.elements());
		int procedureIndex = -1;
		for (int index = 0; index < elements.size(); index++) {
			if (elements.get(index).type().equals("procedure")) {
				procedureIndex = index;
				break;
			}
		}
		assertTrue(procedureIndex >= 0);
		var procedure = elements.get(procedureIndex);
		JsonObject values = procedure.values();
		UUID triggerId = UUID.fromString("00000000-0000-4000-8000-000000000971");
		UUID callId = UUID.fromString("00000000-0000-4000-8000-000000000972");
		JsonObject ir = new JsonObject();
		ir.addProperty("schemaVersion", "1.0");
		ir.addProperty("trigger", "no_ext_trigger");
		JsonArray nodes = new JsonArray();
		JsonObject trigger = new JsonObject();
		trigger.addProperty("id", triggerId.toString());
		trigger.addProperty("type", "event_trigger");
		trigger.addProperty("kind", "statement");
		JsonObject triggerFields = new JsonObject();
		triggerFields.addProperty("trigger", "no_ext_trigger");
		trigger.add("fields", triggerFields);
		trigger.add("inputs", new JsonObject());
		nodes.add(trigger);
		JsonObject call = new JsonObject();
		call.addProperty("id", callId.toString());
		call.addProperty("type", "call_procedure");
		call.addProperty("kind", "statement");
		JsonObject callFields = new JsonObject();
		callFields.addProperty("procedureId", "");
		call.add("fields", callFields);
		call.add("inputs", new JsonObject());
		nodes.add(call);
		ir.add("nodes", nodes);
		ir.add("dependencies", new JsonArray());
		values.add("procedureIr", ir);
		elements.set(procedureIndex, new WorkspaceState.Element(procedure.id(), procedure.type(), procedure.name(),
				procedure.displayName(), procedure.state(), procedure.ownership(), procedure.updatedAt(), values));
		WorkspaceState broken = new WorkspaceState(valid.id(), valid.name(), valid.kind(), valid.revision(), valid.dirty(),
				valid.generator(), valid.upstreamDocument(), elements);

		var issue = new NeoForge1211Generator(Path.of(".").toAbsolutePath().normalize()).validate(broken).stream()
				.filter(candidate -> candidate.code().equals("PROCEDURE_CALL_TARGET_REQUIRED"))
				.findFirst().orElseThrow();

		assertEquals(procedure.id(), issue.elementId());
		assertEquals("/elements/" + procedure.id() + "/procedureIr/nodes/" + callId + "/ports/procedureId",
				issue.path());
	}
}
