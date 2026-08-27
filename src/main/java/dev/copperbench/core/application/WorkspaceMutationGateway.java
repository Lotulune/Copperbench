package dev.copperbench.core.application;

import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;

import java.util.List;

/** Participates in a validated content transaction before its new revision becomes visible. */
@FunctionalInterface public interface WorkspaceMutationGateway {

	void persist(WorkspaceState before, WorkspaceState after, Operation operation, Element affectedElement)
			throws Exception;

	/** Persists a workspace-level structured-data mutation such as variables, tags, or language keys. */
	default void persistWorkspaceData(WorkspaceState before, WorkspaceState after, Operation operation)
			throws Exception {
	}

	/** Persists a validated multi-operation plan as one durable workspace transaction. */
	default void persistWorkspacePlan(WorkspaceState before, WorkspaceState after, List<Operation> operations)
			throws Exception {
	}

	/** Synchronizes durable product metadata after local history replaced workspace files. */
	default void persistRestoredRevision(WorkspaceState restored, long newRevision) throws Exception {
	}

	public static WorkspaceMutationGateway noOp() {
		return (_, _, _, _) -> {
		};
	}
}
