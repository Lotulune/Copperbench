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
	private final WorkspaceApplicationService service;

	public McpWorkspaceEntryAdapter(WorkspaceApplicationService service, PermissionProfile permission) {
		this.service = service;
		this.delegate = new WorkspaceEntryAdapter(service, new RequestContext(Actor.MCP, permission));
	}

	/** Creates an immutable permission boundary for a replacement MCP server. */
	public McpWorkspaceEntryAdapter withPermissionProfile(PermissionProfile permission) {
		return new McpWorkspaceEntryAdapter(service, permission);
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
