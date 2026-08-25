/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2012-2020, Pylo
 * Copyright (C) 2020-2026, Pylo, opensource contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.io;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfigurationTest {

	@Test void rollingLogHasFallbackBeforeLoggingSystemInitializes() throws Exception {
		try (var stream = LoggingConfigurationTest.class.getResourceAsStream("/log4j2.xml")) {
			assertNotNull(stream);
			String configuration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			assertTrue(configuration.contains("${sys:log_directory:-${sys:java.io.tmpdir}}"));
			assertTrue(configuration.contains("${logRoot}/logs/mcreator.log"));
			assertFalse(configuration.contains("${sys:log_directory}/logs"));
		}
	}

}
