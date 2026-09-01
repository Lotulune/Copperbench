/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorStats;

import java.util.Objects;

/**
 * Explicit generator × Java mod-element capability decisions. Unsupported
 * combinations return a stable reason code instead of being skipped silently.
 */
public final class GeneratorElementCapabilityCatalog {

	public record Decision(String generatorId, String elementType, boolean generatable, String reasonCode,
			String message) {
		public Decision {
			Objects.requireNonNull(generatorId);
			Objects.requireNonNull(elementType);
			Objects.requireNonNull(reasonCode);
			Objects.requireNonNull(message);
		}
	}

	private GeneratorElementCapabilityCatalog() {
	}

	public static Decision decision(String generatorId, String elementType) {
		String generator = generatorId == null ? "" : generatorId;
		String type = elementType == null ? "" : elementType;
		if (ElementCoverageCatalog.BEDROCK_ADDON_NOT_APPLICABLE.contains(type))
			return unsupported(generator, type, "BEDROCK_ADDON_NOT_APPLICABLE",
					"Bedrock add-on types are outside the Stage 11 Java catalog.");
		if (!ElementCoverageCatalog.isFirstParty(type))
			return unsupported(generator, type, "ELEMENT_TYPE_NOT_IN_SLICE",
					"This element type is outside the supported Java catalog.");
		GeneratorConfiguration configuration = Generator.GENERATOR_CACHE.get(generator);
		if (configuration == null)
			return unsupported(generator, type, "GENERATOR_NOT_LOADED",
					"Generator configuration is not loaded: " + generator);
		ModElementType<?> upstreamType;
		try {
			upstreamType = ModElementTypeLoader.getModElementType(type);
		} catch (IllegalArgumentException exception) {
			return unsupported(generator, type, "ELEMENT_TYPE_NOT_REGISTERED",
					"No upstream mod element type is registered for " + type + ".");
		}
		GeneratorStats.CoverageStatus coverage = configuration.getGeneratorStats().getModElementTypeCoverageInfo()
				.get(upstreamType);
		if (coverage == null || coverage == GeneratorStats.CoverageStatus.NONE)
			return unsupported(generator, type, "GENERATOR_ELEMENT_TYPE_UNSUPPORTED",
					generator + " does not declare templates for " + type + ".");
		return new Decision(generator, type, true, "GENERATOR_ELEMENT_TYPE_SUPPORTED",
				generator + " can generate Minecraft sources for " + type + ".");
	}

	private static Decision unsupported(String generatorId, String elementType, String reasonCode, String message) {
		return new Decision(generatorId, elementType, false, reasonCode, message);
	}
}
