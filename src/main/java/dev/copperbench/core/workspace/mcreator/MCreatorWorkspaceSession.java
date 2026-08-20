package dev.copperbench.core.workspace.mcreator;

import dev.copperbench.core.application.HeadlessWorkspaceEntryAdapter;
import dev.copperbench.core.application.LegacyWorkspaceEntryAdapter;
import dev.copperbench.core.application.McpWorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceApplicationService;
import dev.copperbench.core.application.WorkspaceEntryAdapter;
import dev.copperbench.core.application.WorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.RequestContext;
import dev.copperbench.core.workspace.ProductMetadataManager;
import dev.copperbench.core.workspace.RevisionedWorkspaceStore;
import net.mcreator.workspace.Workspace;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** Attaches the shared application service to an already opened, writer-leased upstream workspace. */
public final class MCreatorWorkspaceSession implements AutoCloseable {

	private final UUID workspaceId;
	private final WorkspaceApplicationService service;
	private final AutoCloseable ownedTasks;

	private MCreatorWorkspaceSession(UUID workspaceId, WorkspaceApplicationService service, AutoCloseable ownedTasks) {
		this.workspaceId = workspaceId;
		this.service = service;
		this.ownedTasks = ownedTasks;
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
		return create(workspace, metadata, tasks, clock, ids, observers, null);
	}

	/** Creates the task gateway after registering the workspace so both layers share one revision store. */
	public static MCreatorWorkspaceSession attach(Workspace workspace,
			Function<RevisionedWorkspaceStore, WorkspaceTaskGateway> taskFactory, Clock clock, Supplier<UUID> ids)
			throws IOException {
		ProductMetadataManager.Metadata metadata = workspace.getFileManager().loadOrCreateProductMetadata(ids);
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(new MCreatorWorkspaceStateMapper().map(workspace, metadata));
		WorkspaceTaskGateway tasks = taskFactory.apply(store);
		WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks,
				new MCreatorWorkspaceMutationGateway(workspace, metadata.workspaceId(), List.of()), clock, ids);
		return new MCreatorWorkspaceSession(metadata.workspaceId(), service,
				tasks instanceof AutoCloseable closeable ? closeable : null);
	}

	private static MCreatorWorkspaceSession create(Workspace workspace, ProductMetadataManager.Metadata metadata,
			WorkspaceTaskGateway tasks, Clock clock, Supplier<UUID> ids, List<WorkspaceMutationObserver> observers,
			AutoCloseable ownedTasks) throws IOException {
		RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
		store.register(new MCreatorWorkspaceStateMapper().map(workspace, metadata));
		WorkspaceApplicationService service = new WorkspaceApplicationService(store, tasks,
				new MCreatorWorkspaceMutationGateway(workspace, metadata.workspaceId(), observers), clock, ids);
		return new MCreatorWorkspaceSession(metadata.workspaceId(), service, ownedTasks);
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
		if (ownedTasks == null)
			return;
		try {
			ownedTasks.close();
		} catch (RuntimeException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to close workspace task gateway", exception);
		}
	}

}
