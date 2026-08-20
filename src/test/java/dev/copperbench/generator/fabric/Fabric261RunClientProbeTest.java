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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

class Fabric261RunClientProbeTest {

	@TempDir Path workspace;

	@Test
	@Timeout(value = 50, unit = TimeUnit.MINUTES)
	@EnabledIfSystemProperty(named = "copperbench.fabric261RunClient", matches = "true")
	void recordsHonestFabric261RunClientOutcome() throws Exception {
		Fabric262RunClientProbeTest.recordRunClient(workspace, Fabric1211Generator.Profile.FABRIC_261,
				Fabric1211GoldenWorkspace.create261(), "fabric-261-runclient");
	}
}
