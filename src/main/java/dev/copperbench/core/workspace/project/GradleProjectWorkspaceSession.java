package dev.copperbench.core.workspace.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Attaches the application service to a Gradle project Copperbench did not create (ADR-0020). Identity and revision
 * live in {@code .copperbench/workspace.json}; every file stays hand-written and no project file is changed by opening.
 */
public final class GradleProjectWorkspaceSession implements AutoCloseable {

	public static final String METADATA_FILE = ".copperbench/workspace.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final UUID workspaceId;
	private final Path root;
	private final WorkspaceApplicationService service;
	private final JGitLocalHistoryService history;
	private final WorkspaceFileLease lease;

	private GradleProjectWorkspaceSession(UUID workspaceId, Path root, WorkspaceApplicationService service,
			JGitLocalHistoryService history, WorkspaceFileLease lease) {
		this.workspaceId = workspaceId;
		this.root = root;
		this.service = service;
		this.history = history;
		this.lease = lease;
	}

	/**
	 * Opens the project for this session. Only {@code .copperbench/} is written, and only one session can hold it.
	 *
	 * @throws GradleProjectDetector.NotAProjectException when the folder is not a Gradle project to adopt
	 * @throws WorkspaceWriteLockedException when another session already has the project open
	 */
	public static GradleProjectWorkspaceSession open(Path folder, Clock clock, Supplier<UUID> ids) throws IOException {
		Path root = WorkspaceFiles.requireDirectory(folder);
		GradleProjectIdentity identity = GradleProjectDetector.inspect(root);
		WorkspaceExecutionSnapshot.rejectLinks(root.resolve(METADATA_FILE));
		WorkspaceFileLease lease = WorkspaceFileLease.tryAcquire(root).orElseThrow(() -> new WorkspaceWriteLockedException(root));
		JGitLocalHistoryService history = null;
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
			WorkspaceApplicationService service = new WorkspaceApplicationService(store, new NoTasks(),
					new HandWrittenFiles(manager, metadataFile, workspaceId, lease), history,
					ignored -> state(workspaceId, manager.loadOrCreate(metadataFile, workspaceId, lease).revision(),
							GradleProjectDetector.inspect(root)),
					ignored -> root, clock, ids);
			return new GradleProjectWorkspaceSession(workspaceId, root, service, history, lease);
		} catch (LocalHistoryException | JsonParseException | IllegalStateException | IOException failure) {
			try {
				if (history != null) history.close();
			} finally {
				lease.close();
			}
			if (failure instanceof IOException unreadable) throw unreadable;
			if (failure instanceof ProductMetadataManager.UnsupportedMetadataVersionException newer) throw newer;
			throw new IOException("Could not open the Gradle project: " + failure.getMessage(), failure);
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
		try {
			history.close();
		} finally {
			lease.close();
		}
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

	/** Building and running adopted projects follows in the next stage; nothing is started meanwhile. */
	private static final class NoTasks implements WorkspaceTaskGateway {
		@Override public JsonObject start(UUID workspaceId, Operation operation, JsonObject payload) {
			throw new IllegalArgumentException(GradleProjectOperations.TASKS_UNAVAILABLE);
		}

		@Override public Optional<JsonObject> find(UUID workspaceId, UUID taskId) {
			return Optional.empty();
		}

		@Override public List<JsonObject> active(UUID workspaceId) {
			return List.of();
		}

		@Override public Optional<JsonObject> cancel(UUID workspaceId, UUID taskId) {
			return Optional.empty();
		}
	}
}
