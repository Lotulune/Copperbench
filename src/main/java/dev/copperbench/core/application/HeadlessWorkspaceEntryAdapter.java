package dev.copperbench.core.application;

import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandOutcome;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.QueryResult;
import dev.copperbench.core.contract.UiCore.RequestContext;

/** Headless boundary with an explicit permission profile selected by the host. */
public final class HeadlessWorkspaceEntryAdapter {

	private final WorkspaceEntryAdapter delegate;

	public HeadlessWorkspaceEntryAdapter(WorkspaceApplicationService service, PermissionProfile permission) {
		this.delegate = new WorkspaceEntryAdapter(service, new RequestContext(Actor.HEADLESS, permission));
	}

	public CommandOutcome execute(Command command) {
		return delegate.execute(command);
	}

	public QueryResult query(Query query) {
		return delegate.query(query);
	}
}
