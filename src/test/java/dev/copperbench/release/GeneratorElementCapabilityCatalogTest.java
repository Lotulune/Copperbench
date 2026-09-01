/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import dev.copperbench.testing.McreatorTestRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorElementCapabilityCatalogTest {

	@Test void bedrockAndUnknownTypesHaveExplicitReasonCodes() {
		var bedrock = GeneratorElementCapabilityCatalog.decision("fabric-1.21.1", "beblock");
		assertFalse(bedrock.generatable());
		assertEquals("BEDROCK_ADDON_NOT_APPLICABLE", bedrock.reasonCode());

		var unknown = GeneratorElementCapabilityCatalog.decision("fabric-1.21.1", "not_a_type");
		assertFalse(unknown.generatable());
		assertEquals("ELEMENT_TYPE_NOT_IN_SLICE", unknown.reasonCode());
	}

	@Test void loadedGeneratorReportsSupportedJavaTypesExplicitly() throws Exception {
		McreatorTestRuntime.ensureInitialized();
		var living = GeneratorElementCapabilityCatalog.decision("fabric-1.21.1", "livingentity");
		assertTrue(living.generatable(), living.message());
		assertEquals("GENERATOR_ELEMENT_TYPE_SUPPORTED", living.reasonCode());

		var missingGenerator = GeneratorElementCapabilityCatalog.decision("not-a-generator", "livingentity");
		assertFalse(missingGenerator.generatable());
		assertEquals("GENERATOR_NOT_LOADED", missingGenerator.reasonCode());
	}
}
