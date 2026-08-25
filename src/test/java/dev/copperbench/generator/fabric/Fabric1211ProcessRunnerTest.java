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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fabric1211ProcessRunnerTest {

	@Test void recognizesRootAndQualifiedRunClientTasks() {
		assertTrue(Fabric1211ProcessRunner.isClientRun(List.of("runClient")));
		assertTrue(Fabric1211ProcessRunner.isClientRun(List.of(":packloader:runClient")));
		assertFalse(Fabric1211ProcessRunner.isClientRun(List.of("build")));
	}

	@Test void recognizesRootAndQualifiedRunServerTasks() {
		assertTrue(Fabric1211ProcessRunner.isServerRun(List.of("runServer")));
		assertTrue(Fabric1211ProcessRunner.isServerRun(List.of(":server:runServer")));
		assertFalse(Fabric1211ProcessRunner.isServerRun(List.of("runDatagen")));
	}
}
