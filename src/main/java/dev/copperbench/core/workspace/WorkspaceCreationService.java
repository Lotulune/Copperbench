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
import dev.copperbench.generator.BundledJdkLocator;
import dev.copperbench.gradle.GradleDistributionPool;
import dev.copperbench.gradle.MinecraftMappingsCacheRepair;
import dev.copperbench.platform.RuntimePlatform;
import dev.copperbench.tracks.VersionTrackCatalog;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.gradle.GradleUtils;
import net.mcreator.io.UserFolderManager;
import net.mcreator.preferences.PreferencesManager;
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

	public static final String RESOURCE_PACK_GENERATOR_ID = "resourcepack-1.21.1";
	private static final Object GRADLE_SETUP_LOCK = new Object();

	/** Result of a creation attempt; diagnostics are stable codes, never Java exception text. */
	public record CreationResult(boolean complete, String workspaceFile, String generatorId,
			List<String> diagnostics, String detail) {
		public CreationResult(boolean complete, String workspaceFile, String generatorId, List<String> diagnostics) {
			this(complete, workspaceFile, generatorId, diagnostics, null);
		}
	}

	private void setupJavaWorkspace(Workspace workspace, String generatorId) {
		VersionTrackCatalog.LoaderStatus track = catalog.findGenerator(generatorId)
				.orElseThrow(() -> new WorkspaceBaseGenerationException("UNSUPPORTED_GENERATOR"));
		Path javaHome;
		try {
			javaHome = BundledJdkLocator.locate(distributionRoot, track.javaRelease());
		} catch (RuntimeException exception) {
			throw new WorkspaceBaseGenerationException("BUNDLED_JDK_MISSING");
		}

		GradleDistributionPool.seedForWorkspace(workspace);
		synchronized (GRADLE_SETUP_LOCK) {
			File previousJavaHome = PreferencesManager.PREFERENCES.hidden.java_home.get();
			try {
				PreferencesManager.PREFERENCES.hidden.java_home.set(javaExecutable(javaHome).toFile());
				var connection = GradleUtils.getGradleProjectConnection(workspace);
				if (connection == null)
					throw new WorkspaceBaseGenerationException("WORKSPACE_GRADLE_SYNC_FAILED");
				try {
					GradleUtils.getGradleSyncLauncher(workspace.getGeneratorConfiguration(), connection).run();
				} catch (RuntimeException firstFailure) {
					int repaired = MinecraftMappingsCacheRepair.repairCorruptMappings(
							workspace.getWorkspaceFolder().toPath());
					if (repaired <= 0)
						throw new WorkspaceBaseGenerationException("WORKSPACE_GRADLE_SYNC_FAILED", firstFailure);
					GradleUtils.getGradleSyncLauncher(workspace.getGeneratorConfiguration(), connection).run();
				}
				try {
					workspace.getGenerator().reloadGradleCaches();
				} catch (RuntimeException exception) {
					throw new WorkspaceBaseGenerationException("WORKSPACE_GRADLE_CACHE_FAILED", exception);
				}
				if (!workspace.getGenerator().generateBase())
					throw new WorkspaceBaseGenerationException("WORKSPACE_BASE_GENERATION_FAILED");
				WorkspaceGeneratorSetup.completeSetup(workspace.getGenerator());
				workspace.getGenerator().runResourceSetupTasks();
			} finally {
				PreferencesManager.PREFERENCES.hidden.java_home.set(previousJavaHome);
			}
		}
	}

	private static Path javaExecutable(Path javaHome) {
		return javaHome.resolve("bin").resolve(RuntimePlatform.current().javaExecutableName());
	}

	private static final class WorkspaceBaseGenerationException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final String diagnostic;

		private WorkspaceBaseGenerationException(String diagnostic) {
			this.diagnostic = diagnostic;
		}

		private WorkspaceBaseGenerationException(String diagnostic, Throwable cause) {
			super(cause);
			this.diagnostic = diagnostic;
		}
	}

	private static final Pattern MOD_ID = Pattern.compile("^(?=.{2,32}$)[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$");
	private static final Pattern MOD_NAME = Pattern.compile("^\\S.{0,63}$");
	private static final Pattern PACKAGE_NAME = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$");

	private final VersionTrackCatalog catalog;
	private final Path distributionRoot;

	public WorkspaceCreationService() {
		this(VersionTrackCatalog.official(), Path.of(System.getProperty("user.dir")));
	}

	public WorkspaceCreationService(VersionTrackCatalog catalog) {
		this(catalog, Path.of(System.getProperty("user.dir")));
	}

	public WorkspaceCreationService(VersionTrackCatalog catalog, Path distributionRoot) {
		this.catalog = Objects.requireNonNull(catalog);
		this.distributionRoot = Objects.requireNonNull(distributionRoot).toAbsolutePath().normalize();
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
		addResourcePackGenerator(byId);
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
		List<String> diagnostics = validateCreation(generatorId, modName, modId, packageName, workspaceFolderPath);
		if (!diagnostics.isEmpty())
			return new CreationResult(false, null, generatorId, diagnostics);

		Path workspaceFolder = Path.of(workspaceFolderPath).toAbsolutePath().normalize();
		GeneratorConfiguration configuration = generatorConfiguration(generatorId);

		WorkspaceSettings settings = new WorkspaceSettings(modId);
		settings.setModName(modName);
		settings.setVersion(version == null || version.isBlank() ? "1.0.0" : version);
		settings.setCurrentGenerator(configuration.getGeneratorName());
		if (packageName != null && !packageName.isBlank())
			settings.setModElementsPackage(packageName);

		File workspaceFile = workspaceFolder.resolve(modId + ".mcreator").toFile();
		boolean preserveWorkspaceFolder = Files.exists(workspaceFolder);
		try (Workspace workspace = Workspace.createWorkspace(workspaceFile, settings)) {
			try {
				WorkspaceGeneratorSetup.setupWorkspaceBaseOrThrow(workspace);
				GradleUtils.updateMCreatorBuildFile(workspace);
				if (isResourcePackGenerator(generatorId))
					setupResourcePackWorkspace(workspace, workspaceFolder);
				else
					setupJavaWorkspace(workspace, generatorId);
			} catch (RuntimeException exception) {
				if (exception instanceof WorkspaceBaseGenerationException)
					throw exception;
				throw new WorkspaceSkeletonSetupException(exception);
			}
			return new CreationResult(true, workspaceFile.getAbsolutePath(), generatorId, List.of());
		} catch (WorkspaceBaseGenerationException exception) {
			return failedCreation(generatorId, workspaceFolder, preserveWorkspaceFolder,
					exception.diagnostic, exception);
		} catch (WorkspaceSkeletonSetupException exception) {
			return failedCreation(generatorId, workspaceFolder, preserveWorkspaceFolder,
					"WORKSPACE_SKELETON_SETUP_FAILED", exception);
		} catch (RuntimeException exception) {
			return failedCreation(generatorId, workspaceFolder, preserveWorkspaceFolder, "WORKSPACE_CREATE_FAILED", exception);
		}
	}

	/**
	 * Performs the complete non-writing validation used by {@link #create}. Hosts
	 * that need a trusted user confirmation can call this before prompting, then
	 * call {@code create} only after the user approves. Keeping the preflight in
	 * this service prevents CLI/MCP/UI hosts from inventing divergent creation
	 * rules.
	 */
	public List<String> validateCreation(String generatorId, String modName, String modId, String packageName,
			String workspaceFolderPath) {
		Objects.requireNonNull(generatorId);
		Objects.requireNonNull(modName);
		Objects.requireNonNull(modId);
		List<String> diagnostics = new ArrayList<>(
				validate(generatorId, modName, modId, packageName, workspaceFolderPath));
		if (!diagnostics.isEmpty())
			return List.copyOf(diagnostics);

		Path workspaceFolder = Path.of(workspaceFolderPath).toAbsolutePath().normalize();
		if (Files.exists(workspaceFolder) && !isEmptyDirectory(workspaceFolder))
			diagnostics.add("WORKSPACE_FOLDER_NOT_EMPTY");

		VersionTrackCatalog.CapabilityDecision decision = catalog.decision(generatorId);
		if (!isResourcePackGenerator(generatorId) && !decision.generatable())
			diagnostics.add("UNSUPPORTED_GENERATOR");
		else if (generatorConfiguration(generatorId) == null)
			diagnostics.add("GENERATOR_NOT_INSTALLED");

		return List.copyOf(diagnostics);
	}

	private static CreationResult failedCreation(String generatorId, Path workspaceFolder,
			boolean preserveWorkspaceFolder, String diagnostic, Throwable failure) {
		List<String> diagnostics = new ArrayList<>();
		diagnostics.add(diagnostic);
		try {
			cleanupPartialWorkspace(workspaceFolder, preserveWorkspaceFolder);
		} catch (IOException exception) {
			diagnostics.add("WORKSPACE_CLEANUP_FAILED");
		}
		return new CreationResult(false, null, generatorId, List.copyOf(diagnostics), failureDetail(failure));
	}

	static String failureDetail(Throwable failure) {
		StringBuilder details = new StringBuilder();
		var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
		for (Throwable current = failure; current != null && seen.add(current) && seen.size() <= 8; current = current.getCause()) {
			if (current.getMessage() == null || current.getMessage().isBlank()) continue;
			if (!details.isEmpty()) details.append("\nCaused by: ");
			details.append(current.getMessage());
		}
		String safe = dev.copperbench.automation.audit.SensitiveDataRedactor.redact(details.toString())
				.replaceAll("(?i)(https?://)[^\\s/@]+:[^\\s/@]+@", "$1[REDACTED]@");
		return safe.length() > 4000 ? safe.substring(0, 4000) + "…" : safe;
	}

	private static void cleanupPartialWorkspace(Path workspaceFolder, boolean preserveWorkspaceFolder)
			throws IOException {
		Path normalized = workspaceFolder.toAbsolutePath().normalize();
		if (!Files.exists(normalized)) return;
		try (var paths = Files.walk(normalized)) {
			for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
				if (preserveWorkspaceFolder && path.equals(normalized)) continue;
				Files.deleteIfExists(path);
			}
		}
	}

	private void addResourcePackGenerator(Map<String, JsonObject> byId) {
		GeneratorConfiguration configuration = generatorConfiguration(RESOURCE_PACK_GENERATOR_ID);
		JsonObject item = new JsonObject();
		item.addProperty("generatorId", RESOURCE_PACK_GENERATOR_ID);
		item.addProperty("loader", "resource_pack");
		item.addProperty("minecraftVersion", "1.21.1");
		item.addProperty("trackId", "resource_pack");
		item.addProperty("displayName", "Resource Pack 1.21.1");
		item.addProperty("dynamic", false);
		item.addProperty("available", configuration != null);
		item.addProperty("workspaceGeneratorName",
				configuration == null ? RESOURCE_PACK_GENERATOR_ID : configuration.getGeneratorName());
		byId.put(RESOURCE_PACK_GENERATOR_ID, item);
	}

	private static boolean isResourcePackGenerator(String generatorId) {
		return RESOURCE_PACK_GENERATOR_ID.equals(generatorId);
	}

	private static void setupResourcePackWorkspace(Workspace workspace, Path workspaceFolder) {
		if (!workspace.getGenerator().generateBase())
			throw new WorkspaceSkeletonSetupException();
		WorkspaceGeneratorSetup.completeSetup(workspace.getGenerator());
		workspace.getGenerator().runResourceSetupTasks();
		if (!Files.isRegularFile(workspaceFolder.resolve("src/main/pack.mcmeta"))
				|| !Files.isRegularFile(workspaceFolder.resolve("src/main/pack.png")))
			throw new WorkspaceSkeletonSetupException();
	}

	private static final class WorkspaceSkeletonSetupException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private WorkspaceSkeletonSetupException() {
		}

		private WorkspaceSkeletonSetupException(Throwable cause) {
			super(cause);
		}
	}

	private List<String> validate(String generatorId, String modName, String modId, String packageName,
			String workspaceFolderPath) {
		List<String> diagnostics = new ArrayList<>();
		if (!isResourcePackGenerator(generatorId) && !catalog.firstPartyGenerator(generatorId))
			diagnostics.add("UNSUPPORTED_GENERATOR");
		if (!MOD_NAME.matcher(modName).matches())
			diagnostics.add("MOD_NAME_INVALID");
		if (!MOD_ID.matcher(modId).matches())
			diagnostics.add("MOD_ID_INVALID");
		if (!isResourcePackGenerator(generatorId) && (packageName == null || !PACKAGE_NAME.matcher(packageName).matches()))
			diagnostics.add("PACKAGE_NAME_INVALID");
		if (workspaceFolderPath == null || workspaceFolderPath.isBlank())
			diagnostics.add("WORKSPACE_FOLDER_REQUIRED");
		else {
			Path folder = Path.of(workspaceFolderPath).toAbsolutePath().normalize();
			if (!Path.of(workspaceFolderPath).isAbsolute() || folder.getParent() == null)
				diagnostics.add("WORKSPACE_FOLDER_OUTSIDE_ROOT");
			else {
				try { dev.copperbench.generator.WorkspaceExecutionSnapshot.rejectLinks(folder); }
				catch (IOException exception) { diagnostics.add("WORKSPACE_FOLDER_OUTSIDE_ROOT"); }
			}
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
