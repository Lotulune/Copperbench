package dev.copperbench.core.workspace.mcreator;

import com.google.gson.*;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.application.WorkspacePlanArtifact;
import dev.copperbench.core.application.WorkspaceSourceConflictException;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.release.GeneratorElementCapabilityCatalog;
import net.mcreator.element.BaseType;
import net.mcreator.element.util.GEValidator;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.element.parts.AIPathNodeType;
import net.mcreator.element.parts.AchievementEntry;
import net.mcreator.element.parts.IWorkspaceDependent;
import net.mcreator.element.parts.ItemUseAnimation;
import net.mcreator.element.parts.MItemBlock;
import net.mcreator.element.parts.MapColor;
import net.mcreator.element.parts.NoteBlockInstrument;
import net.mcreator.element.parts.Sound;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.parts.procedure.RetvalProcedure;
import net.mcreator.element.types.Block;
import net.mcreator.element.types.Achievement;
import net.mcreator.element.types.CustomElement;
import net.mcreator.element.types.Function;
import net.mcreator.element.types.Item;
import net.mcreator.element.types.LootTable;
import net.mcreator.element.types.Procedure;
import net.mcreator.element.types.Projectile;
import net.mcreator.element.types.Recipe;
import net.mcreator.generator.GeneratorTemplate;
import net.mcreator.generator.mapping.MappableElement;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFileManager;
import net.mcreator.workspace.elements.ModElement;

import java.io.IOException;
import java.io.File;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Transaction participant for all first-party Java elements backed by the upstream model classes. */
public final class MCreatorWorkspaceMutationGateway implements WorkspaceMutationGateway {

	public static final String ELEMENT_ID_METADATA = "dev.copperbench.elementId";
	public static final String ELEMENT_VALUES_METADATA = "dev.copperbench.values";
	static final String CODE_FILES_METADATA = "dev.copperbench.codeFiles";
	private static final String EMPTY_PROCEDURE_XML = "<xml xmlns=\"https://developers.google.com/blockly/xml\">"
			+ "<block type=\"event_trigger\" deletable=\"false\" x=\"40\" y=\"40\">"
			+ "<field name=\"trigger\">no_ext_trigger</field></block></xml>";
	private static final String EMPTY_ADVANCEMENT_TRIGGER_XML = "<xml xmlns=\"https://developers.google.com/blockly/xml\">"
			+ "<block type=\"advancement_trigger\" deletable=\"false\" x=\"40\" y=\"80\">"
			+ "<next><shadow type=\"custom_trigger\"></shadow></next></block></xml>";
	private static final Gson GENERIC_FIELD_GSON = genericFieldGson();

	private final Workspace workspace;
	private final UUID workspaceId;
	private final List<WorkspaceMutationObserver> observers;

	private static Gson genericFieldGson() {
		GsonBuilder builder = new GsonBuilder().disableHtmlEscaping().setStrictness(Strictness.LENIENT)
				.registerTypeAdapter(Color.class, new JsonDeserializer<Color>() {
					@Override public Color deserialize(JsonElement json, java.lang.reflect.Type type,
							JsonDeserializationContext context) throws JsonParseException {
						if (json == null || json.isJsonNull()) return null;
						int value = json.isJsonObject() ? json.getAsJsonObject().get("value").getAsInt() : json.getAsInt();
						return new Color(value, true);
					}
				});
		RetvalProcedure.GSON_ADAPTERS.forEach(builder::registerTypeAdapter);
		builder.registerTypeHierarchyAdapter(MappableElement.class, new MappableElement.GSONAdapter());
		return builder.create();
	}

	private List<Path> plannedRollbackPaths(WorkspaceState before, WorkspaceState after) {
		Set<Path> paths = new LinkedHashSet<>();
		if (workspace.getGenerator() != null && workspace.getGenerator().getGeneratorConfiguration() != null) {
			workspace.getGenerator().getModBaseGeneratorTemplatesList().stream()
					.map(GeneratorTemplate::getFile).map(File::toPath)
					.map(path -> path.toAbsolutePath().normalize()).forEach(paths::add);
			AtomicInteger templateId = new AtomicInteger();
			for (ModElementType<?> type : workspace.getGenerator().getGeneratorConfiguration().getGeneratorStats()
					.getSupportedModElementTypes())
				workspace.getGenerator().getGlobalTemplatesListForModElementType(type, templateId).stream()
						.map(GeneratorTemplate::getFile).map(File::toPath)
						.map(path -> path.toAbsolutePath().normalize()).forEach(paths::add);
			for (BaseType type : BaseType.values())
				workspace.getGenerator().getGlobalTemplatesListForDefinition(
						workspace.getGenerator().getGeneratorConfiguration().getDefinitionsProvider().getBaseTypeDefinition(type),
						templateId).stream().map(GeneratorTemplate::getFile).map(File::toPath)
						.map(path -> path.toAbsolutePath().normalize()).forEach(paths::add);
			if (workspace.getMetadata("files") instanceof List<?> tracked)
				for (Object relative : tracked)
					paths.add(workspace.getWorkspaceFolder().toPath().resolve(relative.toString())
							.toAbsolutePath().normalize());
		}

		for (Element element : after.elements()) {
			Element previous = before.element(element.id());
			if (previous != null && sameContent(previous, element)) continue;
			ModElement existing = find(element.id());
			if ("code".equals(element.type())) {
				Path primary;
				if (existing == null) {
					primary = plannedCodePrimary(element);
				} else {
					List<Path> managed = managedCodeBundlePaths(existing,
							workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize());
					primary = primaryCodeSource(existing, managed);
				}
				paths.add(primary.toAbsolutePath().normalize());
				for (CodeBundleFile file : codeBundleFiles(element.values(), primary.getParent(), primary).values())
					paths.add(file.path().toAbsolutePath().normalize());
			} else if (!element.ownership().equals("manual")) {
				paths.addAll(predictedGeneratedPaths(element));
			}
		}
		return List.copyOf(paths);
	}

	private List<Path> predictedGeneratedPaths(Element element) {
		if (workspace.getGenerator() == null || workspace.getGenerator().getGeneratorConfiguration() == null)
			return List.of();
		ModElement detached = new ModElement(workspace, element.name(), modElementType(element.type()));
		detached.setCodeLock(element.ownership().equals("manual"));
		GeneratableElement definition = newDefinition(detached, element);
		return workspace.getGenerator().getModElementGeneratorTemplatesList(definition).stream()
				.map(GeneratorTemplate::getFile)
				.map(File::toPath)
				.map(path -> path.toAbsolutePath().normalize())
				.distinct()
				.toList();
	}

	private static List<Path> associatedPaths(ModElement element) {
		return element.getAssociatedFiles().stream().map(File::toPath)
				.map(path -> path.toAbsolutePath().normalize()).toList();
	}

	private void assertPlannedGeneratedPathAvailable(Path path, Element owner, WorkspaceState after,
			Path workspaceRoot) {
		Path normalized = path.toAbsolutePath().normalize();
		if (!Files.exists(normalized)) return;
		String key = pathKey(normalized);
		for (ModElement current : workspace.getModElements()) {
			if (!ownedPathKeys(current, workspaceRoot).contains(key)) continue;
			UUID currentId = currentElementId(current);
			if (currentId.equals(owner.id()) || after.element(currentId) == null) return;
			throw new IllegalStateException("Generated path is already owned by element '" + current.getName()
					+ "': " + displayPath(workspaceRoot, normalized));
		}
		throw new IllegalStateException("Generated path already exists outside Copperbench ownership: "
				+ displayPath(workspaceRoot, normalized));
	}

	private Set<String> ownedPathKeys(ModElement element, Path workspaceRoot) {
		Set<String> result = new LinkedHashSet<>();
		for (File file : element.getAssociatedFiles()) result.add(pathKey(file.toPath()));
		for (Path path : managedCodeBundlePaths(element, workspaceRoot)) result.add(pathKey(path));
		return result;
	}

	private UUID currentElementId(ModElement element) {
		Object stored = element.getMetadata(ELEMENT_ID_METADATA);
		if (stored != null) {
			try {
				return UUID.fromString(String.valueOf(stored));
			} catch (IllegalArgumentException ignored) {
				// Fall back to the deterministic legacy identity below.
			}
		}
		return MCreatorWorkspaceStateMapper.elementId(workspaceId, element);
	}

	private static String displayPath(Path workspaceRoot, Path path) {
		Path normalized = path.toAbsolutePath().normalize();
		return normalized.startsWith(workspaceRoot) ? workspaceRoot.relativize(normalized).toString() : normalized.toString();
	}

	public MCreatorWorkspaceMutationGateway(Workspace workspace, UUID workspaceId) {
		this(workspace, workspaceId, List.of());
	}

	public MCreatorWorkspaceMutationGateway(Workspace workspace, UUID workspaceId,
			List<WorkspaceMutationObserver> observers) {
		this.workspace = workspace;
		this.workspaceId = workspaceId;
		this.observers = List.copyOf(observers);
	}

	@Override public void validateWorkspacePlan(WorkspaceState before, WorkspaceState after) {
		Map<String, PlannedSourceOwner> claims = new LinkedHashMap<>();
		Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		for (Element element : after.elements()) {
			ModElement existing = find(element.id());
			if (!"code".equals(element.type())) {
				List<Path> planned = element.ownership().equals("manual") && existing != null
						? associatedPaths(existing) : predictedGeneratedPaths(element);
				if (planned.isEmpty() && existing != null) planned = associatedPaths(existing);
				for (Path path : planned) {
					assertPlannedGeneratedPathAvailable(path, element, after, workspaceRoot);
					claimPlannedPath(claims, workspaceRoot, element, path);
				}
				continue;
			}

			Path primary;
			if (existing != null) {
				List<Path> managed = managedCodeBundlePaths(existing, workspaceRoot);
				for (File file : existing.getAssociatedFiles()) {
					Path path = file.toPath().toAbsolutePath().normalize();
					if (managed.stream().noneMatch(managedPath -> pathKey(managedPath).equals(pathKey(path))))
						claimPlannedPath(claims, workspaceRoot, element, path);
				}
				primary = primaryCodeSource(existing, managed);
				Element previous = before.element(element.id());
				if (previous != null) validateCodeSourceChanges(previous, element, primary);
			} else {
				primary = plannedCodePrimary(element);
				assertUnmanagedSourcePathFree(primary, workspaceRoot);
				claimPlannedPath(claims, workspaceRoot, element, primary);
			}

			Map<String, CodeBundleFile> plannedBundle = codeBundleFiles(element.values(), primary.getParent(), primary);
			Map<String, CodeBundleFile> previousBundle = existing == null || before.element(element.id()) == null ? Map.of()
					: codeBundleFiles(before.element(element.id()).values(), primary.getParent(), primary);
			for (var entry : plannedBundle.entrySet()) {
				if (!previousBundle.containsKey(entry.getKey()))
					assertUnmanagedSourcePathFree(entry.getValue().path(), workspaceRoot);
				CodeBundleFile file = entry.getValue();
				claimPlannedPath(claims, workspaceRoot, element, file.path());
			}
		}
	}

	@Override public void validateWorkspacePlan(WorkspaceState before, WorkspaceState after,
			List<WorkspacePlanArtifact> artifacts) throws Exception {
		validateWorkspacePlan(before, after);
		if (artifacts == null || artifacts.isEmpty()) return;
		Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		Set<String> plannedPaths = new LinkedHashSet<>();
		for (Path path : plannedRollbackPaths(before, after)) plannedPaths.add(pathKey(path));
		for (WorkspacePlanArtifact artifact : artifacts) {
			Path target = artifact.resolve(workspaceRoot);
			assertArtifactTargetSafe(workspaceRoot, target);
			if (plannedPaths.contains(pathKey(target)))
				throw new IllegalStateException("Template asset path conflicts with generated workspace output: "
						+ displayPath(workspaceRoot, target));
			if (!Files.exists(target)) continue;
			if (!Files.isRegularFile(target) || !artifact.sha256().equals(fingerprint(target)))
				throw new IllegalStateException("Template asset path already exists with different content: "
						+ displayPath(workspaceRoot, target));
		}
	}

	private Path plannedCodePrimary(Element element) {
		if (workspace.getGenerator() == null || workspace.getGenerator().getSourceRoot() == null)
			throw new IllegalStateException("A generator source root is required to validate code ownership");
		return workspace.getGenerator().getSourceRoot().toPath().toAbsolutePath().normalize()
				.resolve(workspace.getWorkspaceSettings().getModElementsPackage().replace('.', File.separatorChar))
				.resolve(element.name() + ".java").normalize();
	}

	private void assertUnmanagedSourcePathFree(Path path, Path workspaceRoot) {
		if (!Files.exists(path)) return;
		Path normalized = path.toAbsolutePath().normalize();
		String displayPath = normalized.startsWith(workspaceRoot)
				? workspaceRoot.relativize(normalized).toString() : normalized.toString();
		throw new IllegalStateException("Source path already exists outside the planned element ownership: " + displayPath);
	}

	private Path primaryCodeSource(ModElement modElement, List<Path> managedBundlePaths) {
		return modElement.getAssociatedFiles().stream()
				.map(File::toPath)
				.map(path -> path.toAbsolutePath().normalize())
				.filter(path -> path.getFileName().toString().endsWith(".java"))
				.filter(path -> managedBundlePaths.stream()
						.noneMatch(managed -> pathKey(managed).equals(pathKey(path))))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"The code element has no primary generated Java source: " + modElement.getName()));
	}

	private void claimPlannedPath(Map<String, PlannedSourceOwner> claims, Path workspaceRoot, Element owner,
			Path path) {
		Path normalized = path.toAbsolutePath().normalize();
		String key = pathKey(normalized);
		PlannedSourceOwner previous = claims.putIfAbsent(key, new PlannedSourceOwner(owner.id(), owner.name()));
		if (previous == null || previous.elementId().equals(owner.id())) return;
		String displayPath = normalized.startsWith(workspaceRoot)
				? workspaceRoot.relativize(normalized).toString() : normalized.toString();
		throw new IllegalStateException("Managed source path conflict between elements '" + previous.name()
				+ "' and '" + owner.name() + "': " + displayPath);
	}

	@Override public void persist(WorkspaceState before, WorkspaceState after, Operation operation,
			Element affectedElement) throws Exception {
		ModElement existing = find(affectedElement.id());
		Element previous = before.element(affectedElement.id());
		String storedName = existing == null ? affectedElement.name() : existing.getName();
		FileSnapshot snapshot = FileSnapshot.capture(workspace, storedName, existing);
		try {
			switch (operation) {
				case CREATE_MOD_ELEMENT -> create(affectedElement);
				case UPDATE_MOD_ELEMENT, SET_MOD_ELEMENT_SOURCE_MANAGEMENT, UPDATE_PROCEDURE ->
						update(existing, previous, affectedElement);
				case DELETE_MOD_ELEMENT -> {
					delete(existing, true);
					generateWorkspaceBaseIfReady();
				}
				default -> throw new IllegalArgumentException("Operation is not a content mutation: " + operation);
			}
			if ("code".equals(affectedElement.type()) && after.element(affectedElement.id()) != null)
				after.replaceElement(affectedElement);
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();
			for (WorkspaceMutationObserver observer : observers)
				observer.afterMutation(workspace, before, after, operation, affectedElement);
			workspace.getFileManager().advanceProductRevision(before.id(), before.revision());
		} catch (Exception exception) {
			if (operation == Operation.CREATE_MOD_ELEMENT && existing == null) {
				try {
					cleanupFailedCreate(find(affectedElement.id()));
				} catch (Exception cleanupFailure) {
					exception.addSuppressed(cleanupFailure);
				}
			}
			try {
				snapshot.restore();
				workspace.reloadFromFileSystem();
			} catch (Exception rollbackFailure) {
				exception.addSuppressed(rollbackFailure);
			}
			throw exception;
		}
	}

	private void cleanupFailedCreate(ModElement failed) throws IOException {
		if (failed == null) return;
		Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		Set<String> protectedPaths = new LinkedHashSet<>();
		for (ModElement other : workspace.getModElements()) {
			if (other == failed) continue;
			for (File file : other.getAssociatedFiles()) protectedPaths.add(pathKey(file.toPath()));
			for (Path path : managedCodeBundlePaths(other, workspaceRoot)) protectedPaths.add(pathKey(path));
		}
		for (File file : failed.getAssociatedFiles()) {
			Path path = file.toPath().toAbsolutePath().normalize();
			if (!path.startsWith(workspaceRoot) || protectedPaths.contains(pathKey(path))) continue;
			Files.deleteIfExists(path);
		}
	}

	@Override public void persistWorkspacePlan(WorkspaceState before, WorkspaceState after, List<Operation> operations)
			throws Exception {
		persistWorkspacePlan(before, after, operations, List.of());
	}

	@Override public void persistWorkspacePlan(WorkspaceState before, WorkspaceState after, List<Operation> operations,
			List<WorkspacePlanArtifact> artifacts) throws Exception {
		List<Path> plannedPaths = new java.util.ArrayList<>(plannedRollbackPaths(before, after));
		if (artifacts != null) for (WorkspacePlanArtifact artifact : artifacts)
			plannedPaths.add(artifact.resolve(workspace.getWorkspaceFolder().toPath()));
		FileSnapshot snapshot = FileSnapshot.capturePlan(workspace, before, after, workspaceId,
				plannedPaths);
		try {
			if (!before.registries().equals(after.registries()))
				MCreatorWorkspaceRegistryMapper.synchronize(workspace, after.registries());

			Map<UUID, Element> beforeElements = new LinkedHashMap<>();
			for (Element element : before.elements()) beforeElements.put(element.id(), element);
			Map<UUID, Element> afterElements = new LinkedHashMap<>();
			for (Element element : after.elements()) afterElements.put(element.id(), element);

			for (Element element : beforeElements.values())
				if (!afterElements.containsKey(element.id())) delete(find(element.id()), false);
			for (Element element : afterElements.values()) {
				Element previous = beforeElements.get(element.id());
				if (previous == null) create(element);
				else if (!sameContent(previous, element)) update(find(element.id()), previous, element);
				if ("code".equals(element.type()) && after.element(element.id()) != null)
					after.replaceElement(element);
			}
			// Deletes remove element-owned files and localization/tag links immediately, but generator-owned
			// base registries/imports are shared across the workspace. Refresh them once from the final plan
			// state so a delete-only plan cannot leave imports or registrations pointing at removed elements.
			generateWorkspaceBaseIfReady();
			if (artifacts != null) for (WorkspacePlanArtifact artifact : artifacts)
				writePlanArtifact(artifact);

			workspace.getFileManager().saveWorkspaceDirectlyAndWait();
			workspace.getFileManager().advanceProductRevision(before.id(), before.revision(), after.registries());
		} catch (Exception exception) {
			try {
				snapshot.restore();
				workspace.reloadFromFileSystem();
			} catch (Exception rollbackFailure) {
				exception.addSuppressed(rollbackFailure);
			}
			throw exception;
		}
	}


	private static void assertArtifactTargetSafe(Path workspaceRoot, Path target) throws IOException {
		Path root = workspaceRoot.toAbsolutePath().normalize();
		Path normalized = target.toAbsolutePath().normalize();
		if (!normalized.startsWith(root)) throw new IOException("Template asset path escapes the workspace");
		Path rootReal = root.toRealPath();
		Path ancestor = normalized.getParent();
		while (ancestor != null && !Files.exists(ancestor)) ancestor = ancestor.getParent();
		if (ancestor != null && !ancestor.toRealPath().startsWith(rootReal))
			throw new IOException("Template asset path traverses a link outside the workspace");
	}

	private void writePlanArtifact(WorkspacePlanArtifact artifact) throws IOException {
		Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		Path target = artifact.resolve(workspaceRoot);
		assertArtifactTargetSafe(workspaceRoot, target);
		if (Files.isRegularFile(target) && artifact.sha256().equals(fingerprint(target))) return;
		Files.createDirectories(target.getParent());
		Path temporary = Files.createTempFile(target.getParent(), ".copperbench-template-", ".tmp");
		try {
			Files.write(temporary, artifact.content());
			try {
				Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
		if (!artifact.sha256().equals(fingerprint(target)))
			throw new IOException("Template asset verification failed after write: "
					+ displayPath(workspaceRoot, target));
	}
	private static boolean sameContent(Element left, Element right) {
		return left.type().equals(right.type()) && left.name().equals(right.name())
				&& left.displayName().equals(right.displayName()) && left.state().equals(right.state())
				&& left.ownership().equals(right.ownership()) && left.values().equals(right.values());
	}

	@Override public void persistRestoredRevision(WorkspaceState restored, long newRevision) throws Exception {
		workspace.getFileManager().synchronizeProductRevision(workspaceId, newRevision);
	}

	@Override public void persistWorkspaceData(WorkspaceState before, WorkspaceState after, Operation operation)
			throws Exception {
		FileSnapshot snapshot = FileSnapshot.capture(workspace, "__workspace_registry__", null);
		try {
			MCreatorWorkspaceRegistryMapper.synchronize(workspace, after.registries());
			for (Element element : after.elements()) {
				Element previous = before.element(element.id());
				if (previous != null && !previous.values().equals(element.values()))
					update(find(element.id()), previous, element);
			}
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();
			workspace.getFileManager().advanceProductRevision(before.id(), before.revision(), after.registries());
		} catch (Exception exception) {
			try {
				snapshot.restore();
				workspace.reloadFromFileSystem();
			} catch (Exception rollbackFailure) {
				exception.addSuppressed(rollbackFailure);
			}
			throw exception;
		}
	}

	private void create(Element element) {
		if (workspace.getModElementByName(element.name()) != null)
			throw new IllegalStateException("Element already exists in upstream workspace: " + element.name());
		if ("code".equals(element.type())) {
			Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
			Path primary = plannedCodePrimary(element);
			assertUnmanagedSourcePathFree(primary, workspaceRoot);
			for (CodeBundleFile file : codeBundleFiles(element.values(), primary.getParent(), primary).values())
				assertUnmanagedSourcePathFree(file.path(), workspaceRoot);
		} else if (!element.ownership().equals("manual")) {
			Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
			for (Path path : predictedGeneratedPaths(element)) assertUnmanagedSourcePathFree(path, workspaceRoot);
		}

		ModElement modElement = new ModElement(workspace, element.name(), modElementType(element.type()));
		storeProductMetadata(modElement, element);
		GeneratableElement definition = newDefinition(modElement, element);
		workspace.addModElement(modElement);
		persistGeneratedElement(modElement, null, element, definition);
	}

	private void update(ModElement modElement, Element previous, Element element) {
		if (modElement == null)
			throw new IllegalStateException("Element is missing from upstream workspace: " + element.id());
		GeneratableElement definition = modElement.getGeneratableElement();
		if (definition == null || !element.type().equals(modElement.getTypeString()))
			throw new IllegalStateException("Element type does not match the upstream definition: " + element.id());
		if (!element.type().equals("code") && !element.ownership().equals("manual"))
			validateGeneratedUpdatePaths(modElement, element);
		storeProductMetadata(modElement, element);
		updateDefinition(definition, element);
		if (!element.type().equals("code")) modElement.setCodeLock(element.ownership().equals("manual"));
		workspace.markDirty();
		persistGeneratedElement(modElement, previous, element, definition);
	}

	private void validateGeneratedUpdatePaths(ModElement owner, Element element) {
		Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		Set<String> current = new LinkedHashSet<>();
		for (File file : owner.getAssociatedFiles()) current.add(pathKey(file.toPath()));
		for (Path path : predictedGeneratedPaths(element)) {
			assertCodePathAvailable(owner, path);
			if (!current.contains(pathKey(path)) && Files.exists(path))
				throw new IllegalStateException("Generated path already exists outside this element's ownership: "
						+ displayPath(workspaceRoot, path));
		}
	}

	private void persistGeneratedElement(ModElement modElement, Element previous, Element element,
			GeneratableElement definition) {
		if (definition instanceof CustomElement) {
			persistCustomCode(modElement, previous, element, definition);
			generateWorkspaceBaseIfReady();
			return;
		}
		workspace.getModElementManager().storeModElement(definition);
		generateRegisteredSources(definition);
	}

	private void generateRegisteredSources(GeneratableElement definition) {
		if (!generatorWorkspaceReady())
			return;
		String generatorId = workspace.getWorkspaceSettings().getCurrentGenerator();
		String type = definition.getModElement().getType().getRegistryName();
		var decision = GeneratorElementCapabilityCatalog.decision(generatorId, type);
		if (!decision.generatable())
			return;
		try {
			GEValidator.validateAndTryToCorrect(definition, null);
		} catch (GEValidator.ValidationException | AssertionError exception) {
			throw new IllegalStateException("Upstream element validation failed for "
					+ definition.getModElement().getName(), exception);
		}
		try {
			workspace.getGenerator().generateBase();
			if (!workspace.getGenerator().generateElement(definition)) {
				workspace.getGenerator().removeElementFilesAndWorkspaceLinks(definition);
				throw new IllegalStateException("Upstream source generation failed for "
						+ definition.getModElement().getName());
			}
			// The first base generation intentionally runs before element generation so upstream element
			// templates can resolve existing base classes. A newly created element's own Java class does
			// not exist in the import tree until generateElement() writes it, though. Refresh the base once
			// more so shared registries/init files can import that new class before the next real Gradle build.
			workspace.getGenerator().generateBase();
		} catch (RuntimeException exception) {
			try {
				workspace.getGenerator().removeElementFilesAndWorkspaceLinks(definition);
			} catch (RuntimeException cleanup) {
				exception.addSuppressed(cleanup);
			}
			throw exception;
		}
	}

	private void generateWorkspaceBaseIfReady() {
		if (generatorWorkspaceReady())
			workspace.getGenerator().generateBase();
	}

	private boolean generatorWorkspaceReady() {
		if (workspace.getGenerator() == null || workspace.getGenerator().getGeneratorConfiguration() == null)
			return false;
		java.io.File sourceRoot = workspace.getGenerator().getSourceRoot();
		return sourceRoot != null && sourceRoot.isDirectory();
	}

	private ModElementType<?> modElementType(String type) {
		ModElementType<?> result = switch (type) {
			case "block" -> ModElementType.BLOCK;
			case "item" -> ModElementType.ITEM;
			case "recipe" -> ModElementType.RECIPE;
			case "procedure" -> ModElementType.PROCEDURE;
			case "function" -> ModElementType.FUNCTION;
			case "loottable" -> ModElementType.LOOTTABLE;
			case "achievement" -> ModElementType.ADVANCEMENT;
			default -> ModElementTypeLoader.getModElementType(type);
		};
		if (result == null)
			throw new IllegalStateException("Element type is not registered: " + type);
		return result;
	}

	private GeneratableElement newDefinition(ModElement modElement, Element element) {
		return switch (element.type()) {
			case "block" -> newBlock(modElement, element);
			case "item" -> newItem(modElement, element);
			case "recipe" -> newRecipe(modElement, element);
			case "procedure" -> {
				Procedure procedure = new Procedure(modElement);
				procedure.procedurexml = procedureXml(element.values());
				yield procedure;
			}
			case "projectile" -> newProjectile(modElement, element);
			case "function" -> newFunction(modElement, element);
			case "loottable" -> newLootTable(modElement, element);
			case "achievement" -> newAchievement(modElement, element);
			default -> newGenericDefinition(modElement, element);
		};
	}

	private GeneratableElement newGenericDefinition(ModElement modElement, Element element) {
		try {
			Class<? extends GeneratableElement> storageClass = modElementType(element.type()).getModElementStorageClass();
			GeneratableElement definition = storageClass.getConstructor(ModElement.class).newInstance(modElement);
			applyGenericValues(definition, element);
			return definition;
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to instantiate upstream element type: " + element.type(), exception);
		}
	}

	private Block newBlock(ModElement modElement, Element element) {
		Block block = new Block(modElement);
		block.name = element.displayName();
		block.customModelName = "Normal";
		block.transparencyType = "SOLID";
		block.colorOnMap = new MapColor(workspace, "DEFAULT");
		block.noteBlockInstrument = new NoteBlockInstrument(workspace, "harp");
		block.aiPathNodeType = new AIPathNodeType(workspace, "DEFAULT");
		block.boundingBoxes.clear();
		block.inventoryStackSize = 99;
		block.frequencyPerChunks = 10;
		block.frequencyOnChunk = 16;
		block.maxGenerateHeight = 64;
		return block;
	}

	private Item newItem(ModElement modElement, Element element) {
		Item item = new Item(modElement);
		item.customModelName = "Normal";
		item.stackSize = 64;
		item.toolType = 1;
		item.animation = new ItemUseAnimation(workspace, "eat");
		applyItem(item, element);
		return item;
	}

	private void applyItem(Item item, Element element) {
		JsonObject values = element.values();
		item.name = element.displayName();
		item.texture = new TextureHolder(workspace, string(values, "texture", "minecraft:barrier"));
		int stackSize = integer(values, "stackSize", item.stackSize);
		stackSize = integer(values, "maxStackSize", stackSize);
		if (values.has("fields") && values.get("fields").isJsonObject())
			stackSize = integer(values.getAsJsonObject("fields"), "maxStackSize", stackSize);
		item.stackSize = stackSize;
	}

	private Projectile newProjectile(ModElement modElement, Element element) {
		Projectile projectile = new Projectile(modElement);
		applyProjectileDefaults(projectile, element);
		return projectile;
	}

	private void applyProjectileDefaults(Projectile projectile, Element element) {
		JsonObject values = element.values();
		projectile.projectileItem = new MItemBlock(workspace, string(values, "projectileItem", "Items.ARROW"));
		projectile.entityModel = string(values, "entityModel", "Default");
		projectile.customModelTexture = string(values, "customModelTexture", "");
		projectile.actionSound = new Sound(workspace, string(values, "actionSound", ""));
		projectile.power = decimal(values, "power", 1.0);
		projectile.damage = decimal(values, "damage", 5.0);
		projectile.knockback = integer(values, "knockback", 5);
		projectile.showParticles = bool(values, "showParticles", false);
		projectile.disableGravity = bool(values, "disableGravity", false);
		projectile.igniteFire = bool(values, "igniteFire", false);
		projectile.disableDiscarding = bool(values, "disableDiscarding", false);
		projectile.onHitsBlock = procedureReference(values, "onHitsBlock");
		projectile.onHitsPlayer = procedureReference(values, "onHitsPlayer");
		projectile.onHitsEntity = procedureReference(values, "onHitsEntity");
		projectile.onFlyingTick = procedureReference(values, "onFlyingTick");
	}

	private net.mcreator.element.parts.procedure.Procedure procedureReference(JsonObject values, String key) {
		String name = string(values, key, "");
		if (name.isBlank()) return null;
		ModElement target = workspace.getModElementByName(name);
		if (target == null || !target.getType().equals(ModElementType.PROCEDURE))
			throw new IllegalStateException("Procedure reference " + key + " does not target a Procedure element: " + name);
		return new net.mcreator.element.parts.procedure.Procedure(name);
	}

	private Recipe newRecipe(ModElement modElement, Element element) {
		Recipe recipe = new Recipe(modElement);
		recipe.name = element.name();
		recipe.recipeType = "Crafting";
		recipe.recipeSlots = new MItemBlock[9];
		for (int index = 0; index < recipe.recipeSlots.length; index++)
			recipe.recipeSlots[index] = new MItemBlock(workspace, "");
		recipe.recipeReturnStack = new MItemBlock(workspace, "");
		return recipe;
	}

	private Function newFunction(ModElement modElement, Element element) {
		Function function = new Function(modElement);
		applyFunction(function, element);
		return function;
	}

	private void applyFunction(Function function, Element element) {
		JsonObject values = element.values();
		function.name = string(values, "name", element.name()).toLowerCase(java.util.Locale.ROOT);
		function.namespace = string(values, "namespace", "mod");
		if (values.has("commands") && values.get("commands").isJsonArray()) {
			List<String> commands = new java.util.ArrayList<>();
			values.getAsJsonArray("commands").forEach(command -> commands.add(command.getAsString()));
			function.code = String.join("\n", commands) + (commands.isEmpty() ? "" : "\n");
		} else function.code = string(values, "code", "# New Copperbench function\n");
	}

	private LootTable newLootTable(ModElement modElement, Element element) {
		LootTable lootTable = new LootTable(modElement);
		applyLootTable(lootTable, element);
		return lootTable;
	}

	private void applyLootTable(LootTable lootTable, Element element) {
		JsonObject values = element.values();
		lootTable.name = string(values, "name", element.name()).toLowerCase(java.util.Locale.ROOT);
		lootTable.namespace = string(values, "namespace", "mod");
		lootTable.type = string(values, "type", "Generic");
		lootTable.pools = new java.util.ArrayList<>();
		if (!values.has("pools") || !values.get("pools").isJsonArray()) return;
		for (var rawPool : values.getAsJsonArray("pools")) {
			JsonObject value = rawPool.getAsJsonObject();
			LootTable.Pool pool = new LootTable.Pool();
			pool.minrolls = integer(value, "minRolls", 1);
			pool.maxrolls = integer(value, "maxRolls", pool.minrolls);
			pool.hasbonusrolls = bool(value, "hasBonusRolls", false);
			pool.minbonusrolls = integer(value, "minBonusRolls", 0);
			pool.maxbonusrolls = integer(value, "maxBonusRolls", pool.minbonusrolls);
			for (var rawEntry : array(value, "entries")) {
				JsonObject source = rawEntry.getAsJsonObject();
				LootTable.Pool.Entry entry = new LootTable.Pool.Entry();
				entry.type = string(source, "type", "item");
				entry.item = new MItemBlock(workspace, string(source, "item", "Blocks.STONE"));
				entry.weight = integer(source, "weight", 1);
				entry.minCount = integer(source, "minCount", 1);
				entry.maxCount = integer(source, "maxCount", entry.minCount);
				entry.minEnchantmentLevel = integer(source, "minEnchantmentLevel", 0);
				entry.maxEnchantmentLevel = integer(source, "maxEnchantmentLevel", entry.minEnchantmentLevel);
				entry.affectedByFortune = bool(source, "affectedByFortune", false);
				entry.explosionDecay = bool(source, "explosionDecay", false);
				entry.silkTouchMode = integer(source, "silkTouchMode", 0);
				pool.entries.add(entry);
			}
			lootTable.pools.add(pool);
		}
	}

	private Achievement newAchievement(ModElement modElement, Element element) {
		Achievement achievement = new Achievement(modElement);
		applyAchievement(achievement, element);
		return achievement;
	}

	private void applyAchievement(Achievement achievement, Element element) {
		JsonObject values = element.values();
		achievement.achievementName = string(values, "title", element.displayName());
		achievement.achievementDescription = string(values, "description", "");
		achievement.achievementIcon = new MItemBlock(workspace, string(values, "icon", "Blocks.STONE"));
		achievement.background = string(values, "background", "Default");
		achievement.disableDisplay = bool(values, "disableDisplay", false);
		achievement.showPopup = bool(values, "showPopup", true);
		achievement.announceToChat = bool(values, "announceToChat", true);
		achievement.hideIfNotCompleted = bool(values, "hideIfNotCompleted", false);
		achievement.rewardLoot = strings(values, "rewardLoot");
		achievement.rewardRecipes = strings(values, "rewardRecipes");
		String rewardFunction = string(values, "rewardFunction", "");
		achievement.rewardFunction = rewardFunction.isBlank() ? null : rewardFunction;
		achievement.rewardXP = integer(values, "rewardXP", 0);
		achievement.achievementType = string(values, "frame", "task");
		achievement.parent = new AchievementEntry(workspace, string(values, "parent", "ROOT"));
		achievement.triggerxml = string(values, "triggerxml", EMPTY_ADVANCEMENT_TRIGGER_XML);
	}

	private void updateDefinition(GeneratableElement definition, Element element) {
			switch (definition) {
			case Block block -> block.name = element.displayName();
			case Item item -> applyItem(item, element);
			case Recipe recipe -> recipe.name = element.name();
			case Procedure procedure -> procedure.procedurexml = procedureXml(element.values());
			case Projectile projectile -> applyProjectileDefaults(projectile, element);
			case Function function -> applyFunction(function, element);
			case LootTable lootTable -> applyLootTable(lootTable, element);
			case Achievement achievement -> applyAchievement(achievement, element);
			default -> applyGenericValues(definition, element);
		}
	}

	/**
	 * The upstream model classes expose their editable fields as public members. Mapping only
	 * fields present in the Copperbench value object keeps type-specific defaults intact while
	 * allowing newly added upstream fields to round-trip without another gateway switch.
	 */
	private void applyGenericValues(GeneratableElement definition, Element element) {
		JsonObject values = element.values();
		for (Field field : definition.getClass().getFields()) {
			if (Modifier.isStatic(field.getModifiers())) continue;
			com.google.gson.JsonElement raw = values.get(field.getName());
			if ((raw == null || raw.isJsonNull()) && values.has("fields") && values.get("fields").isJsonObject())
				raw = values.getAsJsonObject("fields").get(field.getName());
			if (raw == null || raw.isJsonNull()) continue;
			try {
				Object value = GENERIC_FIELD_GSON.fromJson(raw, field.getGenericType());
				IWorkspaceDependent.processWorkspaceDependentObjects(value,
						workspaceDependent -> workspaceDependent.setWorkspace(workspace));
				field.set(definition, value);
			} catch (RuntimeException | IllegalAccessException ignored) {
				// Complex workspace-dependent values retain the upstream default; raw values stay in metadata.
			}
		}
		try {
			Field name = definition.getClass().getField("name");
			if (name.getType() == String.class && values.has("displayName"))
				name.set(definition, element.displayName());
			else if (!values.has("name") && name.getType() == String.class
					&& (name.get(definition) == null || ((String) name.get(definition)).isBlank()))
				name.set(definition, element.displayName());
		} catch (NoSuchFieldException | IllegalAccessException ignored) {
			// Some upstream types intentionally do not expose a name field.
		}
		fillMissingStringDefaults(definition);
	}

	private void fillMissingStringDefaults(GeneratableElement definition) {
		for (Field field : definition.getClass().getFields()) {
			if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class)
				continue;
			try {
				if (field.get(definition) != null)
					continue;
				var options = field.getAnnotation(net.mcreator.element.types.interfaces.LimitedOptions.class);
				field.set(definition, options != null && options.value().length > 0 ? options.value()[0] : "");
			} catch (IllegalAccessException ignored) {
				// Keep the upstream constructor default when the field cannot be written.
			}
		}
	}

	private void persistCustomCode(ModElement modElement, Element previous, Element element,
			GeneratableElement definition) {
		if (workspace.getGenerator() == null)
			throw new IllegalStateException("A generator is required to persist a code element");
		if (!generatorWorkspaceReady())
			return;
		if (modElement.getAssociatedFiles().isEmpty() && !workspace.getGenerator().generateElement(definition))
			throw new IllegalStateException("The generator could not create the code element source file");
		List<File> associated = new java.util.ArrayList<>(modElement.getAssociatedFiles());
		File source = associated.stream().filter(file -> file.getName().endsWith(".java")).findFirst()
				.orElseThrow(() -> new IllegalStateException("The code element has no generated Java source file"));
		if (previous != null) validateCodeSourceChanges(previous, element, source.toPath());
		String code = element.values().has("code") && element.values().get("code").isJsonPrimitive()
				? element.values().get("code").getAsString() : null;
		boolean primaryChanged = previous == null || !sameJsonMember(previous.values(), element.values(), "code");
		if (primaryChanged && code != null) {
			assertCodePathAvailable(modElement, source.toPath());
			try {
				Files.writeString(source.toPath(), code, java.nio.charset.StandardCharsets.UTF_8);
			} catch (IOException exception) {
				throw new IllegalStateException("Unable to write the code element source file", exception);
			}
		}
		persistCodeBundle(modElement, previous, element, source.toPath(), associated);
		synchronizeCodeMetadataFromDisk(modElement, element, source.toPath());
		modElement.setCodeLock(true);
	}

	private void persistCodeBundle(ModElement modElement, Element previousElement, Element element, Path primarySource,
			List<File> associated) {
		Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		Path primary = primarySource.toAbsolutePath().normalize();
		Path base = primary.getParent();
		List<Path> previousManagedPaths = managedCodeBundlePaths(modElement, workspaceRoot);
		Map<String, CodeBundleFile> previousFiles = previousElement == null ? Map.of()
				: codeBundleFiles(previousElement.values(), base, primary);
		Map<String, CodeBundleFile> nextFiles = codeBundleFiles(element.values(), base, primary);

		// Validate the complete desired ownership set before deleting or writing anything. This makes an
		// ownership conflict atomic even when the payload contains several helper files.
		for (var entry : nextFiles.entrySet()) {
			CodeBundleFile file = entry.getValue();
			assertCodePathAvailable(modElement, file.path());
			if (!previousFiles.containsKey(entry.getKey()) && Files.exists(file.path()))
				throw new IllegalStateException("Code bundle path already exists outside this element's managed source set: "
						+ workspaceRoot.relativize(file.path()));
		}
		for (Path path : previousManagedPaths) {
			if (!nextFiles.containsKey(pathKey(path))) assertCodePathAvailable(modElement, path);
		}

		for (Path path : previousManagedPaths) {
			if (nextFiles.containsKey(pathKey(path))) continue;
			try {
				Files.deleteIfExists(path);
			} catch (IOException exception) {
				throw new IllegalStateException("Unable to remove stale code bundle file " + path.getFileName(), exception);
			}
		}

		List<File> retained = associated.stream()
				.filter(file -> previousManagedPaths.stream()
						.noneMatch(path -> pathKey(path).equals(pathKey(file.toPath()))))
				.collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
		List<String> storedPaths = new java.util.ArrayList<>();
		for (var entry : nextFiles.entrySet()) {
			CodeBundleFile file = entry.getValue();
			CodeBundleFile old = previousFiles.get(entry.getKey());
			if (old == null || !old.code().equals(file.code())) {
				try {
					Files.createDirectories(file.path().getParent());
					Files.writeString(file.path(), file.code(), java.nio.charset.StandardCharsets.UTF_8);
				} catch (IOException exception) {
					throw new IllegalStateException("Unable to write code bundle file " + file.path().getFileName(), exception);
				}
			}
			retained.add(file.path().toFile());
			storedPaths.add(workspaceRoot.relativize(file.path()).toString().replace(File.separator, "/"));
		}
		modElement.setAssociatedFiles(retained);
		modElement.putMetadata(CODE_FILES_METADATA, storedPaths);
	}

	private Map<String, CodeBundleFile> codeBundleFiles(JsonObject values, Path base, Path primarySource) {
		Map<String, CodeBundleFile> result = new LinkedHashMap<>();
		JsonArray files = values.has("codeFiles") && values.get("codeFiles").isJsonArray()
				? values.getAsJsonArray("codeFiles") : new JsonArray();
		for (JsonElement raw : files) {
			if (!raw.isJsonObject()) throw new IllegalStateException("Each code bundle file must be an object");
			JsonObject file = raw.getAsJsonObject();
			if (!file.has("path") || !file.get("path").isJsonPrimitive()
					|| !file.has("code") || !file.get("code").isJsonPrimitive())
				throw new IllegalStateException("Each code bundle file requires path and code");
			Path target = base.resolve(file.get("path").getAsString()).toAbsolutePath().normalize();
			if (!target.startsWith(base) || pathKey(target).equals(pathKey(primarySource)))
				throw new IllegalStateException(
						"Code bundle path escapes the generated source package or replaces the primary source");
			String key = pathKey(target);
			if (result.containsKey(key))
				throw new IllegalStateException("Multiple code bundle entries claim the same physical path: "
						+ file.get("path").getAsString());
			result.put(key, new CodeBundleFile(target, file.get("code").getAsString()));
		}
		return result;
	}

	private List<Path> managedCodeBundlePaths(ModElement modElement, Path workspaceRoot) {
		List<Path> result = new java.util.ArrayList<>();
		Object stored = modElement.getMetadata(CODE_FILES_METADATA);
		if (stored instanceof List<?> paths) {
			for (Object value : paths) {
				Path path = workspaceRoot.resolve(value.toString()).toAbsolutePath().normalize();
				if (path.startsWith(workspaceRoot)) result.add(path);
			}
		}
		return result;
	}

	private void assertCodePathAvailable(ModElement owner, Path candidate) {
		Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		String candidateKey = pathKey(candidate);
		for (ModElement other : workspace.getModElements()) {
			if (other == owner) continue;
			Set<String> otherPaths = new LinkedHashSet<>();
			for (File associated : other.getAssociatedFiles()) otherPaths.add(pathKey(associated.toPath()));
			for (Path path : managedCodeBundlePaths(other, workspaceRoot)) otherPaths.add(pathKey(path));
			if (otherPaths.contains(candidateKey))
				throw new IllegalStateException("Managed source path is already owned by element '"
						+ other.getName() + "': " + workspaceRoot.relativize(candidate.toAbsolutePath().normalize()));
		}
	}

	private void synchronizeCodeMetadataFromDisk(ModElement modElement, Element element, Path primarySource) {
		JsonObject liveValues = element.values().deepCopy();
		JsonObject fingerprints = new JsonObject();
		try {
			if (liveValues.has("code") && Files.isRegularFile(primarySource)) {
				liveValues.addProperty("code", Files.readString(primarySource, java.nio.charset.StandardCharsets.UTF_8));
				fingerprints.addProperty("$primary", fingerprint(primarySource));
			}
			if (liveValues.has("codeFiles") && liveValues.get("codeFiles").isJsonArray()) {
				Path base = primarySource.toAbsolutePath().normalize().getParent();
				JsonArray refreshed = new JsonArray();
				for (JsonElement raw : liveValues.getAsJsonArray("codeFiles")) {
					JsonObject file = raw.getAsJsonObject().deepCopy();
					String relative = file.get("path").getAsString().replace('\\', '/');
					Path target = base.resolve(relative).toAbsolutePath().normalize();
					if (Files.isRegularFile(target)) {
						file.addProperty("code", Files.readString(target, java.nio.charset.StandardCharsets.UTF_8));
						fingerprints.addProperty(relative, fingerprint(target));
					}
					refreshed.add(file);
				}
				liveValues.add("codeFiles", refreshed);
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to synchronize live code metadata from disk", exception);
		}
		liveValues.add("sourceFingerprints", fingerprints);
		element.values().entrySet().clear();
		for (var entry : liveValues.entrySet()) element.values().add(entry.getKey(), entry.getValue().deepCopy());
		modElement.putMetadata(ELEMENT_VALUES_METADATA,
				WorkspaceFileManager.gson.fromJson(liveValues, Object.class));
	}

	private void validateCodeSourceChanges(Element previous, Element element, Path primarySource) {
		Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		Path primary = primarySource.toAbsolutePath().normalize();
		if (!sameJsonMember(previous.values(), element.values(), "code")) {
			String expected = expectedFingerprint(element.values(), previous.values(), "$primary",
					string(previous.values(), "code", ""));
			assertSourceFingerprint(primary, expected, "/code", workspaceRoot);
		}

		Map<String, CodeBundleFile> previousFiles = codeBundleFiles(previous.values(), primary.getParent(), primary);
		Map<String, CodeBundleFile> nextFiles = codeBundleFiles(element.values(), primary.getParent(), primary);
		for (var entry : previousFiles.entrySet()) {
			CodeBundleFile old = entry.getValue();
			CodeBundleFile next = nextFiles.get(entry.getKey());
			if (next != null && old.code().equals(next.code())) continue;
			String relative = relativeCodePath(primary.getParent(), old.path());
			String expected = expectedFingerprint(element.values(), previous.values(), relative, old.code());
			assertSourceFingerprint(old.path(), expected, "/codeFiles", workspaceRoot);
		}
	}

	private static String expectedFingerprint(JsonObject currentValues, JsonObject previousValues, String key,
			String fallbackCode) {
		String current = fingerprintValue(currentValues, key);
		if (current != null) return current;
		String previous = fingerprintValue(previousValues, key);
		return previous != null ? previous
				: fingerprint(fallbackCode.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static String fingerprintValue(JsonObject values, String key) {
		if (!values.has("sourceFingerprints") || !values.get("sourceFingerprints").isJsonObject()) return null;
		JsonElement raw = values.getAsJsonObject("sourceFingerprints").get(key);
		return raw != null && raw.isJsonPrimitive() ? raw.getAsString() : null;
	}

	private void assertSourceFingerprint(Path path, String expected, String fieldPath, Path workspaceRoot) {
		String actual;
		try {
			actual = Files.isRegularFile(path) ? fingerprint(path) : "missing";
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to fingerprint source file " + path, exception);
		}
		if (expected.equals(actual)) return;
		Path normalized = path.toAbsolutePath().normalize();
		String displayPath = normalized.startsWith(workspaceRoot)
				? workspaceRoot.relativize(normalized).toString().replace(File.separatorChar, '/') : normalized.toString();
		throw new WorkspaceSourceConflictException(fieldPath, displayPath, expected, actual);
	}

	private static String relativeCodePath(Path base, Path path) {
		return base.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString()
				.replace(File.separatorChar, '/');
	}

	private static String fingerprint(Path path) throws IOException {
		return fingerprint(Files.readAllBytes(path));
	}

	private static String fingerprint(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new AssertionError("JVM must provide SHA-256", exception);
		}
	}

	private static boolean sameJsonMember(JsonObject left, JsonObject right, String key) {
		JsonElement leftValue = left.has(key) ? left.get(key) : JsonNull.INSTANCE;
		JsonElement rightValue = right.has(key) ? right.get(key) : JsonNull.INSTANCE;
		return leftValue.equals(rightValue);
	}

	private static String pathKey(Path path) {
		String normalized = path.toAbsolutePath().normalize().toString();
		return File.separatorChar == '\\' ? normalized.toLowerCase(Locale.ROOT) : normalized;
	}

	private record CodeBundleFile(Path path, String code) {
	}

	private record PlannedSourceOwner(UUID elementId, String name) {
	}

	private void delete(ModElement modElement, boolean checkpoint) {
		if (modElement == null)
			throw new IllegalStateException("Element is missing from upstream workspace");
		if (checkpoint)
			workspace.getHistoryManager().importantCheckpoint("copperbench_before_delete", modElement.getName());
		workspace.removeModElement(modElement);
	}

	private void storeProductMetadata(ModElement modElement, Element element) {
		modElement.putMetadata(ELEMENT_ID_METADATA, element.id().toString());
		modElement.putMetadata(ELEMENT_VALUES_METADATA,
				WorkspaceFileManager.gson.fromJson(element.values(), Object.class));
	}

	private String procedureXml(JsonObject values) {
		return values.has("procedurexml") && values.get("procedurexml").isJsonPrimitive()
				? values.get("procedurexml").getAsString() : EMPTY_PROCEDURE_XML;
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
	}

	private static int integer(JsonObject object, String key, int fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
	}

	private static double decimal(JsonObject object, String key, double fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsDouble() : fallback;
	}

	private static boolean bool(JsonObject object, String key, boolean fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
	}

	private static com.google.gson.JsonArray array(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new com.google.gson.JsonArray();
	}

	private static List<String> strings(JsonObject object, String key) {
		List<String> result = new java.util.ArrayList<>();
		array(object, key).forEach(value -> result.add(value.getAsString()));
		return result;
	}

	private ModElement find(UUID elementId) {
		for (ModElement element : workspace.getModElements()) {
			Object storedId = element.getMetadata(ELEMENT_ID_METADATA);
			if (storedId != null && elementId.toString().equals(String.valueOf(storedId)))
				return element;
			UUID derived = MCreatorWorkspaceStateMapper.elementId(workspaceId, element);
			if (derived.equals(elementId))
				return element;
		}
		return null;
	}

	private record FileSnapshot(Map<Path, byte[]> files) {

		private static FileSnapshot capture(Workspace workspace, String elementName, ModElement existing)
				throws IOException {
			Path root = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
			Map<Path, byte[]> files = new LinkedHashMap<>();
			capture(files, root, workspace.getFileManager().getWorkspaceFile().toPath());
			capture(files, root, workspace.getFolderManager().getModElementsDir().toPath()
					.resolve(elementName + ".mod.json"));
			if (existing != null) {
				for (var associated : existing.getAssociatedFiles())
					capture(files, root, associated.toPath());
			}
			return new FileSnapshot(files);
		}

		private static FileSnapshot capturePlan(Workspace workspace, WorkspaceState before, WorkspaceState after,
				UUID workspaceId, List<Path> plannedPaths) throws IOException {
			Path root = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
			Map<Path, byte[]> files = new LinkedHashMap<>();
			capture(files, root, workspace.getFileManager().getWorkspaceFile().toPath());
			Map<UUID, Element> beforeElements = new LinkedHashMap<>();
			for (Element element : before.elements()) beforeElements.put(element.id(), element);
			Map<UUID, Element> afterElements = new LinkedHashMap<>();
			for (Element element : after.elements()) afterElements.put(element.id(), element);

			for (Element previous : beforeElements.values()) {
				Element next = afterElements.get(previous.id());
				if (next != null && sameContent(previous, next)) continue;
				ModElement existing = find(workspace, workspaceId, previous.id());
				capture(files, root, workspace.getFolderManager().getModElementsDir().toPath()
						.resolve(previous.name() + ".mod.json"));
				if (existing != null)
					for (var associated : existing.getAssociatedFiles()) capture(files, root, associated.toPath());
			}
			for (Element next : afterElements.values()) {
				if (beforeElements.containsKey(next.id())) continue;
				capture(files, root, workspace.getFolderManager().getModElementsDir().toPath()
						.resolve(next.name() + ".mod.json"));
			}
			for (Path planned : plannedPaths) capture(files, root, planned);
			return new FileSnapshot(files);
		}

		private static ModElement find(Workspace workspace, UUID workspaceId, UUID elementId) {
			for (ModElement element : workspace.getModElements()) {
				Object storedId = element.getMetadata(ELEMENT_ID_METADATA);
				if (storedId != null && elementId.toString().equals(String.valueOf(storedId))) return element;
				if (MCreatorWorkspaceStateMapper.elementId(workspaceId, element).equals(elementId)) return element;
			}
			return null;
		}

		private static void capture(Map<Path, byte[]> files, Path root, Path candidate) throws IOException {
			Path normalized = candidate.toAbsolutePath().normalize();
			if (!normalized.startsWith(root))
				throw new IOException("Workspace mutation referenced a file outside the workspace: " + normalized);
			files.putIfAbsent(normalized, Files.isRegularFile(normalized) ? Files.readAllBytes(normalized) : null);
		}

		private void restore() throws IOException {
			for (var entry : files.entrySet()) {
				if (entry.getValue() == null) {
					Files.deleteIfExists(entry.getKey());
				} else {
					Files.createDirectories(entry.getKey().getParent());
					Files.write(entry.getKey(), entry.getValue());
				}
			}
		}
	}
}
