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

	/** Persists a validated workspace plan plus bounded file artifacts as one durable transaction. */
	default void persistWorkspacePlan(WorkspaceState before, WorkspaceState after, List<Operation> operations,
			List<WorkspacePlanArtifact> artifacts) throws Exception {
		if (artifacts == null || artifacts.isEmpty()) {
			persistWorkspacePlan(before, after, operations);
			return;
		}
		throw new UnsupportedOperationException("This mutation gateway does not support workspace plan artifacts");
	}

	/** Performs mutation-backend preflight checks that must agree between plan, preview, and apply. */
	default void validateWorkspacePlan(WorkspaceState before, WorkspaceState after) throws Exception {
	}

	/** Performs preflight for structured plan mutations plus bounded file artifacts. */
	default void validateWorkspacePlan(WorkspaceState before, WorkspaceState after,
			List<WorkspacePlanArtifact> artifacts) throws Exception {
		validateWorkspacePlan(before, after);
		if (artifacts != null && !artifacts.isEmpty())
			throw new UnsupportedOperationException("This mutation gateway does not support workspace plan artifacts");
	}

	/** Returns whether every bounded file artifact already has the exact durable bytes recorded by the plan. */
	default boolean workspacePlanArtifactsAlreadyApplied(List<WorkspacePlanArtifact> artifacts) throws Exception {
		return artifacts == null || artifacts.isEmpty();
	}

	/** Synchronizes durable product metadata after local history replaced workspace files. */
	default void persistRestoredRevision(WorkspaceState restored, long newRevision) throws Exception {
	}

	public static WorkspaceMutationGateway noOp() {
		return (_, _, _, _) -> {
		};
	}
}
