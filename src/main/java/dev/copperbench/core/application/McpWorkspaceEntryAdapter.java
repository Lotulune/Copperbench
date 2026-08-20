package dev.copperbench.core.application;

import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandOutcome;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.QueryResult;
import dev.copperbench.core.contract.UiCore.RequestContext;

/** Application boundary used by the stage 2 MCP transport after it authenticates a permission profile. */
public final class McpWorkspaceEntryAdapter {

	private final WorkspaceEntryAdapter delegate;

	public McpWorkspaceEntryAdapter(WorkspaceApplicationService service, PermissionProfile permission) {
		this.delegate = new WorkspaceEntryAdapter(service, new RequestContext(Actor.MCP, permission));
	}

	public CommandOutcome execute(Command command) {
		return delegate.execute(command);
	}

	public QueryResult query(Query query) {
		return delegate.query(query);
	}
}
