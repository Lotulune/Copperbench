/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator.neoforge;

import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

class NeoForge261RunClientProbeTest {

	@TempDir Path workspace;

	@Test
	@Timeout(value = 50, unit = TimeUnit.MINUTES)
	@EnabledIfSystemProperty(named = "copperbench.neoforge261RunClient", matches = "true")
	void recordsHonestNeoForge261RunClientOutcome() throws Exception {
		NeoForge262RunClientProbeTest.recordRunClient(workspace, NeoForge1211Generator.Profile.NEOFORGE_261,
				Fabric1211GoldenWorkspace.createNeoForge261(), "neoforge-261-runclient");
	}
}
