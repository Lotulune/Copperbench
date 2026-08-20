package dev.copperbench.core.workspace.mcreator;

import dev.copperbench.core.contract.UiCore.Operation;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import net.mcreator.workspace.Workspace;

/** Transaction-scoped compatibility hook for B-level Java plugins. */
@FunctionalInterface public interface WorkspaceMutationObserver {

	void afterMutation(Workspace workspace, WorkspaceState before, WorkspaceState after, Operation operation,
			Element affectedElement) throws Exception;
}
