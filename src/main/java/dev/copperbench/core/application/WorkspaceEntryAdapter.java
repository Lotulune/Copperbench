package dev.copperbench.core.application;

import dev.copperbench.core.contract.UiCore.Command;
import dev.copperbench.core.contract.UiCore.CommandOutcome;
import dev.copperbench.core.contract.UiCore.Query;
import dev.copperbench.core.contract.UiCore.QueryResult;
import dev.copperbench.core.contract.UiCore.RequestContext;

import java.util.UUID;
import java.util.function.Consumer;

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

	/** Subscribes to asynchronous events and replays retained events after a sequence. */
	public AutoCloseable subscribeEvents(UUID workspaceId, long afterSequence,
			Consumer<dev.copperbench.core.contract.UiCore.Event> listener) {
		return service.subscribeEvents(workspaceId, afterSequence, listener);
	}
}
