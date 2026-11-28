package dev.copperbench.core.workspace.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceMutationGateway;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.ProductMetadataManager;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.core.workspace.UnknownFieldPreservingJsonStore;
import dev.copperbench.core.workspace.WorkspaceFileLease;
import dev.copperbench.core.workspace.WorkspaceFiles;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import dev.copperbench.core.workspace.WorkspaceWriteLockedException;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import dev.copperbench.history.JGitLocalHistoryService;
import dev.copperbench.history.LocalHistoryException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Attaches the application service to a Gradle project Copperbench did not create (ADR-0020). Identity and revision
 * live in {@code .copperbench/workspace.json}; every file stays hand-written and no project file is changed by opening.
 */
public final class GradleProjectWorkspaceSession implements dev.copperbench.core.application.WorkspaceSession {

	public static final String METADATA_FILE = ".copperbench/workspace.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final UUID workspaceId;
	private final Path root;
	private final WorkspaceApplicationService service;
	private final WorkspaceTaskGateway tasks;
	private final JGitLocalHistoryService history;
	private final WorkspaceFileLease lease;
	private final UnknownFieldPreservingJsonStore documents;

	private GradleProjectWorkspaceSession(UUID workspaceId, Path root, WorkspaceApplicationService service,
			WorkspaceTaskGateway tasks, JGitLocalHistoryService history, WorkspaceFileLease lease,
			UnknownFieldPreservingJsonStore documents) {
		this.workspaceId = workspaceId;
		this.root = root;
		this.service = service;
		this.tasks = tasks;
		this.history = history;
		this.lease = lease;
		this.documents = documents;
	}

	/** Creates the session's task gateway over its revision store and canonical project root. */
	@FunctionalInterface public interface TaskFactory {
		WorkspaceTaskGateway create(RevisionedWorkspaceStore store, Path root);
	}

	/**
	 * Opens the project for this session. Only {@code .copperbench/} is written, and only one session can hold it.
	 * The session owns the gateway {@code taskFactory} creates and closes it before its history and lease.
	 *
	 * @throws GradleProjectDetector.NotAProjectException when the folder is not a Gradle project to adopt
	 * @throws WorkspaceWriteLockedException when another session already has the project open
	 */
	public static GradleProjectWorkspaceSession open(Path folder, TaskFactory taskFactory, Clock clock, Supplier<UUID> ids)
			throws IOException {
		Path root = WorkspaceFiles.requireDirectory(folder);
		GradleProjectIdentity identity = GradleProjectDetector.inspect(root);
		WorkspaceExecutionSnapshot.rejectLinks(root.resolve(METADATA_FILE));
		WorkspaceFileLease lease = WorkspaceFileLease.tryAcquire(root).orElseThrow(() -> new WorkspaceWriteLockedException(root));
		JGitLocalHistoryService history = null;
		WorkspaceTaskGateway tasks = null;
		try {
			var documents = new UnknownFieldPreservingJsonStore();
			var manager = new ProductMetadataManager(documents);
			Path metadataFile = root.resolve(METADATA_FILE);
			if (!Files.exists(metadataFile, LinkOption.NOFOLLOW_LINKS)) {
				JsonObject initial = new JsonObject();
				initial.addProperty("schemaVersion", "1.0");
				initial.addProperty("kind", GradleProjectOperations.KIND);
				Files.writeString(metadataFile, GSON.toJson(initial) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			}
			requireProjectMetadata(documents.read(metadataFile));
			ProductMetadataManager.Metadata metadata = manager.loadOrCreate(metadataFile, ids, lease);
			UUID workspaceId = metadata.workspaceId();
			RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
			store.register(state(workspaceId, metadata.revision(), identity));
			history = JGitLocalHistoryService.open(root, clock);
			tasks = taskFactory.create(store, root);
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks,
					new HandWrittenFiles(manager, metadataFile, workspaceId, lease), history,
					ignored -> state(workspaceId, manager.loadOrCreate(metadataFile, workspaceId, lease).revision(),
							GradleProjectDetector.inspect(root)),
					ignored -> root, clock, ids);
			return new GradleProjectWorkspaceSession(workspaceId, root, service, tasks, history, lease, documents);
		} catch (LocalHistoryException | RuntimeException | IOException failure) {
			try {
				close(tasks, history, lease);
			} catch (IOException | RuntimeException cleanup) {
				failure.addSuppressed(cleanup);
			}
			if (failure instanceof IOException unreadable) throw unreadable;
			if (failure instanceof ProductMetadataManager.UnsupportedMetadataVersionException newer) throw newer;
			throw new IOException("Could not open the Gradle project: " + failure.getMessage(), failure);
		}
	}

	/** Tasks stop before the history and lease they work under are released. */
	private static void close(WorkspaceTaskGateway tasks, JGitLocalHistoryService history, WorkspaceFileLease lease) throws IOException {
		try {
			if (tasks instanceof AutoCloseable closeable) closeable.close();
		} catch (IOException | RuntimeException failure) {
			throw failure;
		} catch (Exception failure) {
			throw new IOException("Could not stop the project's tasks", failure);
		} finally {
			try {
				if (history != null) history.close();
			} finally {
				lease.close();
			}
		}
	}

	private static void requireProjectMetadata(JsonObject document) throws IOException {
		JsonElement kind = document.get("kind");
		if (kind == null || !kind.isJsonPrimitive() || !GradleProjectOperations.KIND.equals(kind.getAsString()))
			throw new IOException(METADATA_FILE + " does not describe a Gradle project.");
	}

	private static WorkspaceState state(UUID workspaceId, long revision, GradleProjectIdentity identity) {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", identity.generatorId() != null ? identity.generatorId() : "gradle-project");
		generator.addProperty("loader", identity.loader());
		generator.addProperty("minecraftVersion", identity.minecraftVersion());
		generator.addProperty("displayName", displayName(identity));
		generator.addProperty("state", identity.supported() ? "ready"
				: identity.generatorId() != null ? "partial" : "incompatible");
		JsonObject document = new JsonObject();
		if (identity.modId() != null) {
			JsonObject product = new JsonObject();
			product.addProperty("modId", identity.modId());
			document.add("copperbench", product);
		}
		JsonArray problems = new JsonArray();
		for (GradleProjectIdentity.Problem problem : identity.problems()) {
			JsonObject item = new JsonObject();
			item.addProperty("code", problem.code());
			item.addProperty("message", problem.message());
			problems.add(item);
		}
		JsonObject project = new JsonObject();
		project.add("problems", problems);
		document.add("gradleProject", project);
		return new WorkspaceState(workspaceId, identity.name(), GradleProjectOperations.KIND, revision, false, generator,
				document, List.<Element>of());
	}

	private static String displayName(GradleProjectIdentity identity) {
		String loader = switch (identity.loader()) {
			case "fabric" -> "Fabric";
			case "neoforge" -> "NeoForge";
			default -> "Gradle";
		};
		return identity.minecraftVersion().equals(GradleProjectIdentity.UNKNOWN) ? loader
				: String.format(Locale.ROOT, "%s %s", loader, identity.minecraftVersion());
	}

	public UUID workspaceId() {
		return workspaceId;
	}

	public Path root() {
		return root;
	}

	public WorkspaceApplicationService service() {
		return service;
	}

	/** The project's own choices; see {@link GradleProjectSettings}. */
	public GradleProjectSettings settings() {
		return GradleProjectSettings.read(root);
	}

	/**
	 * Records the answer to the {@code .gitignore} question (ADR-0020 decision 7). Adding the entry first takes a
	 * recovery point; declining leaves the project's files as they are.
	 */
	public synchronized void decideGitIgnore(boolean add) throws IOException {
		if (add) {
			try {
				history.createRecoveryPoint(new dev.copperbench.history.RecoveryPointRequest(
						"Before adding .copperbench/ to .gitignore", Actor.UI, ""));
			} catch (LocalHistoryException failure) {
				throw new IOException("Could not create a recovery point before changing .gitignore", failure);
			}
			GradleProjectSettings.addToGitIgnore(root);
		}
		updateSettings("gitIgnore", add ? GradleProjectSettings.ADDED : GradleProjectSettings.DECLINED);
	}

	/** Chooses the Gradle user home the project's builds use (ADR-0020 decision 8); the product's is the default. */
	public synchronized void useEnvironmentGradleHome(boolean environment) throws IOException {
		updateSettings("gradleUserHome", environment ? "environment" : "product");
	}

	private void updateSettings(String key, String value) throws IOException {
		Path metadataFile = root.resolve(METADATA_FILE);
		lease.requireValidFor(metadataFile);
		documents.updateDocument(metadataFile, document -> {
			JsonObject settings = document.has(GradleProjectSettings.KEY) && document.get(GradleProjectSettings.KEY).isJsonObject()
					? document.getAsJsonObject(GradleProjectSettings.KEY) : new JsonObject();
			settings.addProperty(key, value);
			document.add(GradleProjectSettings.KEY, settings);
			return document;
		});
	}

	public HeadlessWorkspaceEntryAdapter headlessEntry(PermissionProfile permission) {
		return new HeadlessWorkspaceEntryAdapter(service, permission);
	}

	public McpWorkspaceEntryAdapter mcpEntry(PermissionProfile permission) {
		return new McpWorkspaceEntryAdapter(service, permission);
	}

	public WorkspaceEntryAdapter uiEntry() {
		return new WorkspaceEntryAdapter(service, new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
	}

	@Override public void close() throws IOException {
		close(tasks, history, lease);
	}

	/** Files have no generator owner; only the durable revision follows source, asset and history transactions. */
	private record HandWrittenFiles(ProductMetadataManager manager, Path metadataFile, UUID workspaceId,
			WorkspaceFileLease lease) implements WorkspaceMutationGateway {

		@Override public void persist(WorkspaceState before, WorkspaceState after, Operation operation, Element affectedElement) {
			throw new UnsupportedOperationException("Adopted Gradle projects have no structured elements");
		}

		@Override public void persistWorkspaceData(WorkspaceState before, WorkspaceState after, Operation operation) {
			throw new UnsupportedOperationException("Adopted Gradle projects have no structured registries");
		}

		@Override public void persistWorkspacePlan(WorkspaceState before, WorkspaceState after, List<Operation> operations) {
			throw new UnsupportedOperationException("Adopted Gradle projects have no structured plans");
		}

		@Override public void persistRestoredRevision(WorkspaceState restored, long newRevision) throws Exception {
			manager.synchronizeRevision(metadataFile, workspaceId, newRevision, lease);
		}
	}
}
