/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core.workspace;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.tracks.VersionTrackCatalog;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import net.mcreator.workspace.WorkspaceFolderManager;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Domain service that creates a new workspace from the visual new-workspace form.
 * It validates the generator against the version-track catalog and the loaded
 * generator cache, then delegates workspace construction to the upstream
 * Workspace.createWorkspace entry point so the new flow and the Swing dialog
 * produce identical workspace files.
 */
public final class WorkspaceCreationService {

	/** Result of a creation attempt; diagnostics are stable codes, never Java exception text. */
	public record CreationResult(boolean complete, String workspaceFile, String generatorId,
			List<String> diagnostics) {
	}

	private static final Pattern MOD_ID = Pattern.compile("^(?=.{2,32}$)[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$");
	private static final Pattern MOD_NAME = Pattern.compile("^\\S.{0,63}$");
	private static final Pattern PACKAGE_NAME = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$");

	private final VersionTrackCatalog catalog;

	public WorkspaceCreationService() {
		this(VersionTrackCatalog.official());
	}

	public WorkspaceCreationService(VersionTrackCatalog catalog) {
		this.catalog = Objects.requireNonNull(catalog);
	}

	/** Lists the generators offered by the visual new-workspace flow: catalog track first, then cache check. */
	public List<JsonObject> listGenerators() {
		Map<String, JsonObject> byId = new LinkedHashMap<>();
		for (VersionTrackCatalog.Track track : catalog.tracks()) {
			for (VersionTrackCatalog.LoaderStatus loader : track.loaders()) {
				if (loader.status() != VersionTrackCatalog.SupportStatus.SUPPORTED)
					continue;
				JsonObject item = new JsonObject();
				item.addProperty("generatorId", loader.generatorId());
				item.addProperty("loader", loader.loader().name().toLowerCase(Locale.ROOT));
				item.addProperty("minecraftVersion", loader.minecraftVersion());
				item.addProperty("trackId", track.id().name().toLowerCase(Locale.ROOT));
				item.addProperty("displayName", loader.generatorId());
				item.addProperty("dynamic", track.dynamic());
				GeneratorConfiguration configuration = generatorConfiguration(loader.generatorId());
				item.addProperty("available", configuration != null);
				item.addProperty("workspaceGeneratorName",
						configuration == null ? loader.generatorId() : configuration.getGeneratorName());
				byId.put(loader.generatorId(), item);
			}
		}
		return List.copyOf(byId.values());
	}

	/**
	 * Validates the form and creates the workspace. The caller supplies the raw
	 * form values; this method owns all domain rules so the UI never re-implements
	 * them.
	 */
	public CreationResult create(String generatorId, String modName, String modId, String packageName,
			String workspaceFolderPath, String version) {
		Objects.requireNonNull(generatorId);
		Objects.requireNonNull(modName);
		Objects.requireNonNull(modId);
		List<String> diagnostics = validate(generatorId, modName, modId, packageName, workspaceFolderPath);
		if (!diagnostics.isEmpty())
			return new CreationResult(false, null, generatorId, diagnostics);

		Path workspaceFolder = Path.of(workspaceFolderPath).toAbsolutePath().normalize();
		if (Files.exists(workspaceFolder) && !isEmptyDirectory(workspaceFolder))
			return new CreationResult(false, null, generatorId, List.of("WORKSPACE_FOLDER_NOT_EMPTY"));

		VersionTrackCatalog.CapabilityDecision decision = catalog.decision(generatorId);
		if (!decision.generatable())
			return new CreationResult(false, null, generatorId, List.of("UNSUPPORTED_GENERATOR"));

		GeneratorConfiguration configuration = generatorConfiguration(generatorId);
		if (configuration == null)
			return new CreationResult(false, null, generatorId, List.of("GENERATOR_NOT_INSTALLED"));

		WorkspaceSettings settings = new WorkspaceSettings(modId);
		settings.setModName(modName);
		settings.setVersion(version == null || version.isBlank() ? "1.0.0" : version);
		settings.setCurrentGenerator(configuration.getGeneratorName());
		settings.setModElementsPackage(packageName);

		File workspaceFile = workspaceFolder.resolve(modId + ".mcreator").toFile();
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile, settings)) {
			return new CreationResult(true, workspaceFile.getAbsolutePath(), generatorId, List.of());
		} catch (RuntimeException exception) {
			return new CreationResult(false, null, generatorId, List.of("WORKSPACE_CREATE_FAILED"));
		}
	}

	private List<String> validate(String generatorId, String modName, String modId, String packageName,
			String workspaceFolderPath) {
		List<String> diagnostics = new ArrayList<>();
		if (!catalog.firstPartyGenerator(generatorId))
			diagnostics.add("UNSUPPORTED_GENERATOR");
		if (!MOD_NAME.matcher(modName).matches())
			diagnostics.add("MOD_NAME_INVALID");
		if (!MOD_ID.matcher(modId).matches())
			diagnostics.add("MOD_ID_INVALID");
		if (packageName == null || !PACKAGE_NAME.matcher(packageName).matches())
			diagnostics.add("PACKAGE_NAME_INVALID");
		if (workspaceFolderPath == null || workspaceFolderPath.isBlank())
			diagnostics.add("WORKSPACE_FOLDER_REQUIRED");
		else {
			Path folder = Path.of(workspaceFolderPath).toAbsolutePath().normalize();
			Path suggestedRoot = WorkspaceFolderManager.getSuggestedWorkspaceFoldersRoot().toPath().toAbsolutePath()
					.normalize();
			if (!folder.startsWith(suggestedRoot))
				diagnostics.add("WORKSPACE_FOLDER_OUTSIDE_ROOT");
		}
		return List.copyOf(diagnostics);
	}

	private static boolean isEmptyDirectory(Path folder) {
		try (var children = Files.list(folder)) {
			return children.findAny().isEmpty();
		} catch (IOException exception) {
			return false;
		}
	}

	@Nullable private static GeneratorConfiguration generatorConfiguration(String generatorId) {
		GeneratorConfiguration configuration = Generator.GENERATOR_CACHE.get(generatorId);
		return configuration == null ? null : configuration;
	}

	/** Projection used by the list_new_workspace_generators query. */
	public JsonObject toProjection() {
		JsonObject projection = new JsonObject();
		projection.addProperty("schemaVersion", "1.0");
		JsonArray items = new JsonArray();
		listGenerators().forEach(items::add);
		projection.add("generators", items);
		projection.addProperty("suggestedWorkspaceFoldersRoot",
				WorkspaceFolderManager.getSuggestedWorkspaceFoldersRoot().getAbsolutePath());
		return projection;
	}
}
