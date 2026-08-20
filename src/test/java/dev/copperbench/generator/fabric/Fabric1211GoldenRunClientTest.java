/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fabric1211GoldenRunClientTest {

	@TempDir Path workspace;

	@Test @EnabledIfSystemProperty(named = "copperbench.stage3.fabricRunClient", matches = "true")
	void generatedGoldenWorkspaceLoadsInTheFabricClient() throws Exception {
		Path repository = Path.of(".").toAbsolutePath().normalize();
		new Fabric1211Generator(repository).generate(workspace, Fabric1211GoldenWorkspace.create());
		List<String> logs = new ArrayList<>();

		var result = Fabric1211ProcessRunner.system().run(workspace, List.of("runClient"), Duration.ofMinutes(20),
				logs::add);
		String output = String.join("\n", logs);

		assertEquals(0, result.exitCode(), output);
		assertTrue(result.readinessMarkerSeen(), output);
		assertTrue(output.contains("COPPERBENCH_STAGE3_READY"), output);
	}
}
