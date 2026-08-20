package dev.copperbench.core.application;

import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandOutcome;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.QueryResult;
import dev.copperbench.core.contract.UiCore.RequestContext;

/** Thin named adapter proving that entry points do not own domain behavior. */
public final class WorkspaceEntryAdapter {

	private final WorkspaceApplicationService service;
	private final RequestContext context;

	public WorkspaceEntryAdapter(WorkspaceApplicationService service, RequestContext context) {
		this.service = service;
		this.context = context;
	}

	public CommandOutcome execute(Command command) {
		return service.execute(command, context);
	}

	public QueryResult query(Query query) {
		return service.query(query, context);
	}
}
