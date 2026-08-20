package dev.copperbench.core.application;

import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandOutcome;
import dev.copperbench.core.contract.UiCore.PermissionProfile;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.QueryResult;
import dev.copperbench.core.contract.UiCore.RequestContext;

/** Legacy Swing boundary; Swing code supplies intent but does not own domain rules. */
public final class LegacyWorkspaceEntryAdapter {

	private final WorkspaceEntryAdapter delegate;

	public LegacyWorkspaceEntryAdapter(WorkspaceApplicationService service) {
		this.delegate = new WorkspaceEntryAdapter(service,
				new RequestContext(Actor.LEGACY_UI, PermissionProfile.WORKSPACE));
	}

	public CommandOutcome execute(Command command) {
		return delegate.execute(command);
	}

	public QueryResult query(Query query) {
		return delegate.query(query);
	}
}
