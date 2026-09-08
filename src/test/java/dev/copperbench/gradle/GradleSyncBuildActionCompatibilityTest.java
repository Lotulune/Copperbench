/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.gradle;

import net.mcreator.gradle.GradleSyncBuildAction;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleSyncBuildActionCompatibilityTest {

	@Test void transportedBuildActionRemainsLoadableByJava17AndNewerWorkspaceDaemons() throws Exception {
		String resource = "/" + GradleSyncBuildAction.class.getName().replace('.', '/') + ".class";
		try (InputStream raw = GradleSyncBuildAction.class.getResourceAsStream(resource)) {
			assertNotNull(raw);
			DataInputStream input = new DataInputStream(raw);
			assertEquals(0xCAFEBABE, input.readInt());
			input.readUnsignedShort(); // minor
			int major = input.readUnsignedShort();
			assertTrue(major <= 61, "Transported Gradle BuildAction must stay Java 17-compatible; major=" + major);
		}
	}
}
