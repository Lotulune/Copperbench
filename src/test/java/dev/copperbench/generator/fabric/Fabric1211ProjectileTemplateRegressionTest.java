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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fabric1211ProjectileTemplateRegressionTest {

	@Test void projectileUsesThe1211InGroundField() throws Exception {
		Path template = Path.of("plugins/generator-1.21.1/fabric-1.21.1/templates/projectile/projectile.java.ftl");
		String source = Files.readString(template);

		assertTrue(source.contains("if (this.inGround)"), template.toString());
		assertFalse(source.contains("this.isInGround()"), template.toString());
	}

	@Test void rendererRegistryUsesTheUnambiguousModernFabricApiType() throws Exception {
		Path template = Path.of(
				"plugins/generator-1.21.1/fabric-1.21.1/templates/elementinits/entityrenderers.java.ftl");
		String source = Files.readString(template);

		assertTrue(source.contains(
				"net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register("), template.toString());
		assertFalse(source.contains("\tEntityRendererRegistry.register("),
				"1.21.1 must not leave renderer registry resolution to Fabric API compatibility imports");
	}
}
