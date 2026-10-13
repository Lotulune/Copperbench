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

	public CommandOutcome executeAndPublish(Command command) {
		return service.executeAndPublish(command, context);
	}

	public QueryResult query(Query query) {
		return service.query(query, context);
	}

	/** Per-request extension negotiation does not change this adapter's permission profile. */
	public com.google.gson.JsonObject acceptance(UUID requestId, UUID workspaceId, com.google.gson.JsonObject request) {
		return service.acceptance(workspaceId, requestId, request, context);
	}

	/** Subscribes to asynchronous events and replays retained events after a sequence. */
	public AutoCloseable subscribeEvents(UUID workspaceId, long afterSequence,
			Consumer<dev.copperbench.core.contract.UiCore.Event> listener) {
		return service.subscribeEvents(workspaceId, afterSequence, listener);
	}
	/** Versioned preview extension, using the same immutable caller permissions. */
	public com.google.gson.JsonObject preview(java.util.UUID requestId, java.util.UUID workspaceId, com.google.gson.JsonObject request) {
		return service.preview(workspaceId, requestId, request, context);
	}

	/** Read-only version-copy upgrade planning with per-request negotiation. */
	public com.google.gson.JsonObject versionCopyUpgrade(java.util.UUID requestId, java.util.UUID workspaceId, com.google.gson.JsonObject request) {
		return service.versionCopyUpgrade(workspaceId, requestId, request, context);
	}

}
