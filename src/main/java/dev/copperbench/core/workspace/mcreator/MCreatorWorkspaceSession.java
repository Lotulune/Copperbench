package dev.copperbench.core.workspace.mcreator;

import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.application.LegacyWorkspaceEntryAdapter;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceStateReloader;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.ProductMetadataManager;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import dev.copperbench.history.JGitLocalHistoryService;
import dev.copperbench.history.LocalHistoryException;
import net.mcreator.workspace.Workspace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** Attaches the shared application service to an already opened, writer-leased upstream workspace. */
public final class MCreatorWorkspaceSession implements AutoCloseable {

	private final UUID workspaceId;
	private final WorkspaceApplicationService service;
	private final List<AutoCloseable> ownedResources;

	private MCreatorWorkspaceSession(UUID workspaceId, WorkspaceApplicationService service,
			List<AutoCloseable> ownedResources) {
		this.workspaceId = workspaceId;
		this.service = service;
		this.ownedResources = List.copyOf(ownedResources);
	}

	public static MCreatorWorkspaceSession attach(Workspace workspace, UUID workspaceId,
			WorkspaceTaskGateway tasks, Clock clock, Supplier<UUID> ids) throws IOException {
		return attach(workspace, workspaceId, tasks, clock, ids, List.of());
	}

	public static MCreatorWorkspaceSession attach(Workspace workspace, UUID workspaceId,
			WorkspaceTaskGateway tasks, Clock clock, Supplier<UUID> ids,
			List<WorkspaceMutationObserver> observers) throws IOException {
		ProductMetadataManager.Metadata metadata = workspace.getFileManager()
				.loadOrCreateProductMetadata(workspaceId);
		return create(workspace, metadata, tasks, clock, ids, observers, List.of());
	}

	/** Creates the task gateway after registering the workspace so both layers share one revision store. */
	public static MCreatorWorkspaceSession attach(Workspace workspace,
			Function<RevisionedWorkspaceStore, WorkspaceTaskGateway> taskFactory, Clock clock, Supplier<UUID> ids)
			throws IOException {
		ProductMetadataManager.Metadata metadata = workspace.getFileManager().loadOrCreateProductMetadata(ids);
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		MCreatorWorkspaceStateMapper mapper = new MCreatorWorkspaceStateMapper();
		store.register(mapper.map(workspace, metadata));
		WorkspaceTaskGateway tasks = taskFactory.apply(store);
		Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		JGitLocalHistoryService history;
		try {
			history = JGitLocalHistoryService.open(workspaceRoot, clock);
		} catch (LocalHistoryException exception) {
			closeQuietly(tasks instanceof AutoCloseable closeable ? closeable : null);
			throw new IOException("Could not initialize Copperbench local history", exception);
		}
		WorkspaceStateReloader reloader = ignored -> {
			workspace.reloadFromFileSystem();
			ProductMetadataManager.Metadata restored = workspace.getFileManager()
					.loadOrCreateProductMetadata(metadata.workspaceId());
			return mapper.map(workspace, restored);
		};
		WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks,
				new MCreatorWorkspaceMutationGateway(workspace, metadata.workspaceId(), List.of()), history, reloader,
				ignored -> workspaceRoot, clock, ids);
		List<AutoCloseable> owned = new ArrayList<>();
		owned.add(history);
		if (tasks instanceof AutoCloseable closeable)
			owned.add(closeable);
		return new MCreatorWorkspaceSession(metadata.workspaceId(), service, owned);
	}

	private static MCreatorWorkspaceSession create(Workspace workspace, ProductMetadataManager.Metadata metadata,
			WorkspaceTaskGateway tasks, Clock clock, Supplier<UUID> ids, List<WorkspaceMutationObserver> observers,
			List<AutoCloseable> ownedResources) throws IOException {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(new MCreatorWorkspaceStateMapper().map(workspace, metadata));
		WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks,
				new MCreatorWorkspaceMutationGateway(workspace, metadata.workspaceId(), observers), clock, ids);
		return new MCreatorWorkspaceSession(metadata.workspaceId(), service, ownedResources);
	}

	public UUID workspaceId() {
		return workspaceId;
	}

	public WorkspaceApplicationService service() {
		return service;
	}

	public LegacyWorkspaceEntryAdapter legacyEntry() {
		return new LegacyWorkspaceEntryAdapter(service);
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

	@Override public void close() {
		List<AutoCloseable> resources = new ArrayList<>(ownedResources);
		Collections.reverse(resources);
		Exception failure = null;
		for (AutoCloseable resource : resources) {
			try {
				resource.close();
			} catch (Exception exception) {
				if (failure == null)
					failure = exception;
				else
					failure.addSuppressed(exception);
			}
		}
		if (failure != null)
			throw new IllegalStateException("Failed to close workspace session resources", failure);
	}

	private static void closeQuietly(AutoCloseable resource) {
		if (resource == null)
			return;
		try {
			resource.close();
		} catch (Exception ignored) {
		}
	}

}
