/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.headless;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessProductLauncherTest {

	@TempDir Path tempDir;

	@Test void invalidProductArgumentsReturnOneMachineReadableFailureEnvelope() {
		StringWriter buffer = new StringWriter();
		int exitCode = HeadlessProductLauncher.run(new String[] { "validate" }, new PrintWriter(buffer, true));

		assertEquals(HeadlessExitCode.INVALID_ARGUMENTS.code(), exitCode);
		String payload = buffer.toString().trim();
		assertEquals(1, payload.lines().count(), payload);
		JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
		assertEquals("headless_product_start", json.get("operation").getAsString());
		assertEquals("failed", json.get("status").getAsString());
		assertEquals("HEADLESS_INVALID_ARGUMENTS", json.get("code").getAsString());
		assertEquals(HeadlessExitCode.INVALID_ARGUMENTS.code(), json.get("exitCode").getAsInt());
		assertTrue(json.getAsJsonArray("diagnostics").size() > 0);
	}

	@Test void parserRequiresAnExistingMcreatorFileAndPreservesCommandArguments() throws Exception {
		Path workspace = tempDir.resolve("agent-test.mcreator");
		Files.writeString(workspace, "{}");

		HeadlessProductLauncher.Invocation invocation = HeadlessProductLauncher.parse(
				new String[] { "--workspace", workspace.toString(), "build", "--example", "value" });

		assertEquals(workspace.toAbsolutePath().normalize(), invocation.workspace());
		assertArrayEquals(new String[] { "build", "--example", "value" }, invocation.commandArguments());
	}
}
