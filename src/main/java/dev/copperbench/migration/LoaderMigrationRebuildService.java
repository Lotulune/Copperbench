/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;
import dev.copperbench.tracks.VersionTrackCatalog;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Validates and generates the copy-only migration target with the destination
 * first-party generator. A rebuild failure does not rewrite the source workspace.
 */
public final class LoaderMigrationRebuildService {

	private final VersionTrackCatalog catalog;
	private final Path distributionRoot;

	public LoaderMigrationRebuildService(VersionTrackCatalog catalog, Path distributionRoot) {
		this.catalog = Objects.requireNonNull(catalog);
		this.distributionRoot = Objects.requireNonNull(distributionRoot).toAbsolutePath().normalize();
	}

	public RebuildResult rebuild(WorkspaceState source, String targetGeneratorId, Path targetRoot) {
		Objects.requireNonNull(source);
		Objects.requireNonNull(targetGeneratorId);
		Objects.requireNonNull(targetRoot);
		if (!catalog.migratable(generatorId(source), targetGeneratorId)) {
			return RebuildResult.skipped(targetGeneratorId, "VERSION_TRACK_NOT_REBUILDABLE",
					"The target generator is not a first-party Fabric/NeoForge pair that can be rebuilt.");
		}
		WorkspaceState target = source.withGenerator(retarget(source.generator(), targetGeneratorId));
		try {
			Fabric1211Generator.Profile fabric = fabricProfile(targetGeneratorId);
			if (fabric != null) {
				Fabric1211Generator generator = new Fabric1211Generator(distributionRoot, fabric);
				var issues = generator.validate(target);
				if (!issues.isEmpty())
					return RebuildResult.failed(targetGeneratorId, issues.getFirst().code(), issues.getFirst().message());
				var generated = generator.generateMigrationTarget(targetRoot, target);
				return RebuildResult.generated(generated.generatorId(), generated.modId(), generated.generatedPaths());
			}
			NeoForge1211Generator.Profile neoForge = neoForgeProfile(targetGeneratorId);
			if (neoForge != null) {
				NeoForge1211Generator generator = new NeoForge1211Generator(distributionRoot, neoForge);
				var issues = generator.validate(target);
				if (!issues.isEmpty())
					return RebuildResult.failed(targetGeneratorId, issues.getFirst().code(), issues.getFirst().message());
				var generated = generator.generateMigrationTarget(targetRoot, target);
				return RebuildResult.generated(generated.generatorId(), generated.modId(), generated.generatedPaths());
			}
			return RebuildResult.skipped(targetGeneratorId, "VERSION_TRACK_NOT_REBUILDABLE",
					"No first-party rebuild generator is registered for " + targetGeneratorId);
		} catch (Exception exception) {
			return RebuildResult.failed(targetGeneratorId, "MIGRATION_REBUILD_FAILED",
					exception.getMessage() == null ? "Target generation failed." : exception.getMessage(), exception);
		}
	}

	private static Fabric1211Generator.Profile fabricProfile(String generatorId) {
		return switch (generatorId) {
			case "fabric-1.21.1" -> Fabric1211Generator.Profile.FABRIC_1211;
			case "fabric-26.1.2" -> Fabric1211Generator.Profile.FABRIC_261;
			case "fabric-26.2" -> Fabric1211Generator.Profile.FABRIC_262;
			case "fabric-1.20.1" -> Fabric1211Generator.Profile.FABRIC_1201;
			default -> null;
		};
	}

	private static NeoForge1211Generator.Profile neoForgeProfile(String generatorId) {
		return switch (generatorId) {
			case "neoforge-1.21.1" -> NeoForge1211Generator.Profile.NEOFORGE_1211;
			case "neoforge-26.1.2" -> NeoForge1211Generator.Profile.NEOFORGE_261;
			case "neoforge-26.2" -> NeoForge1211Generator.Profile.NEOFORGE_262;
			case "neoforge-1.20.1" -> NeoForge1211Generator.Profile.NEOFORGE_1201;
			default -> null;
		};
	}

	private static String generatorId(WorkspaceState source) {
		return source.generator().has("id") && source.generator().get("id").isJsonPrimitive()
				? source.generator().get("id").getAsString() : "";
	}

	private static JsonObject retarget(JsonObject sourceGenerator, String targetGeneratorId) {
		JsonObject generator = sourceGenerator.deepCopy();
		generator.addProperty("id", targetGeneratorId);
		int separator = targetGeneratorId.indexOf('-');
		String loader = separator < 0 ? targetGeneratorId : targetGeneratorId.substring(0, separator);
		generator.addProperty("loader", loader);
		generator.addProperty("displayName", Character.toUpperCase(loader.charAt(0)) + loader.substring(1) + " "
				+ (separator < 0 ? targetGeneratorId : targetGeneratorId.substring(separator + 1)));
		generator.addProperty("state", "ready");
		return generator;
	}

	public record RebuildResult(String status, String generatorId, String modId, String reasonCode, String message,
			List<String> generatedPaths, Throwable cause) {
		public RebuildResult {
			generatedPaths = generatedPaths == null ? List.of() : List.copyOf(generatedPaths);
		}

		static RebuildResult generated(String generatorId, String modId, List<String> generatedPaths) {
			return new RebuildResult("generated", generatorId, modId, "MIGRATION_REBUILT",
					"The target copy was generated with the destination generator.", generatedPaths, null);
		}

		static RebuildResult skipped(String generatorId, String reasonCode, String message) {
			return new RebuildResult("skipped", generatorId, "", reasonCode, message, List.of(), null);
		}

		static RebuildResult failed(String generatorId, String reasonCode, String message) {
			return failed(generatorId, reasonCode, message, null);
		}

		static RebuildResult failed(String generatorId, String reasonCode, String message, Throwable cause) {
			return new RebuildResult("failed", generatorId, "", reasonCode, message, List.of(), cause);
		}

		public boolean generated() {
			return "generated".equals(status);
		}

		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("status", status);
			json.addProperty("generatorId", generatorId);
			json.addProperty("modId", modId);
			json.addProperty("reasonCode", reasonCode);
			json.addProperty("message", "failed".equals(status)
					? "Target generation failed. See the diagnostic and application logs."
					: message);
			JsonArray paths = new JsonArray();
			generatedPaths.forEach(paths::add);
			json.add("generatedPaths", paths);
			return json;
		}
	}
}
