/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.neoforge;

import dev.copperbench.generator.fabric.Fabric1211ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForge1211GoldenRunClientTest {

	@TempDir Path workspace;

	@Test @EnabledIfSystemProperty(named = "copperbench.stage5.neoforgeRunClient", matches = "true")
	void generatedGoldenWorkspaceLoadsInTheNeoForgeClient() throws Exception {
		Path repository = Path.of(".").toAbsolutePath().normalize();
		new NeoForge1211Generator(repository).generate(workspace, NeoForge1211GoldenWorkspace.create());
		List<String> logs = new ArrayList<>();

		var result = Fabric1211ProcessRunner.system("COPPERBENCH_STAGE5_NEOFORGE_READY")
				.run(workspace, List.of("runClient"), Duration.ofMinutes(20), logs::add);
		String output = String.join("\n", logs);

		assertEquals(0, result.exitCode(), output);
		assertTrue(result.readinessMarkerSeen(), output);
		assertTrue(output.contains("COPPERBENCH_STAGE5_NEOFORGE_READY"), output);
	}
}
