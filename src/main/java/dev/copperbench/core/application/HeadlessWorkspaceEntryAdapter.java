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
		return delegate.executeAndPublish(command);
	}

	public QueryResult query(Query query) {
		return delegate.query(query);
	}

	public com.google.gson.JsonObject acceptance(java.util.UUID requestId, java.util.UUID workspaceId, com.google.gson.JsonObject request) {
		return delegate.acceptance(requestId, workspaceId, request);
	}
	/** Versioned preview extension, using the same immutable caller permissions. */
	public com.google.gson.JsonObject preview(java.util.UUID requestId, java.util.UUID workspaceId, com.google.gson.JsonObject request) {
		return delegate.preview(requestId, workspaceId, request);
	}

	/** Read-only version-copy upgrade planning with per-request negotiation. */
	public com.google.gson.JsonObject versionCopyUpgrade(java.util.UUID requestId, java.util.UUID workspaceId, com.google.gson.JsonObject request) {
		return delegate.versionCopyUpgrade(requestId, workspaceId, request);
	}

}
