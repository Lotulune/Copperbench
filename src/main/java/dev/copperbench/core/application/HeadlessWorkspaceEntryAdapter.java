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
	private final WorkspaceApplicationService service;

	public HeadlessWorkspaceEntryAdapter(WorkspaceApplicationService service, PermissionProfile permission) {
		this.delegate = new WorkspaceEntryAdapter(service, new RequestContext(Actor.HEADLESS, permission));
		this.service = service;
	}

	/**
	 * Grants a local file for a later {@code preview_asset_import}. Native hosts are owner-only local processes of the
	 * same OS user, so the path they name is that caller's own selection, like the desktop file picker. The grant only
	 * lets the preview read that file; the import still needs the preview's plan token, a recovery point and the
	 * session's command permission or task authorization.
	 */
	public WorkspaceApplicationService.AssetImportSelectionGrant grantAssetImportSource(java.nio.file.Path source) {
		if (source == null || !source.isAbsolute())
			throw new dev.copperbench.assets.AssetImportService.AssetImportException("ASSET_IMPORT_SOURCE_UNAVAILABLE",
					"The import source must be an absolute local file path");
		java.nio.file.Path real;
		try { real = source.toRealPath(); }
		catch (java.io.IOException exception) {
			throw new dev.copperbench.assets.AssetImportService.AssetImportException("ASSET_IMPORT_SOURCE_UNAVAILABLE",
					"The import source is unavailable", exception);
		}
		// Session credentials and other private metadata are JSON too; keep them out of shareable workspaces.
		if (privateCopperbenchData(real))
			throw new dev.copperbench.assets.AssetImportService.AssetImportException("ASSET_IMPORT_SOURCE_PRIVATE",
					"Files in Copperbench's private data folders cannot be imported as assets");
		return service.grantAssetImportSource(real);
	}

	private static boolean privateCopperbenchData(java.nio.file.Path real) {
		for (java.nio.file.Path part : real)
			if (part.toString().equalsIgnoreCase(".copperbench")) return true;
		var paths = dev.copperbench.platform.UserDataPaths.current();
		for (java.nio.file.Path directory : java.util.List.of(paths.data(), paths.config(), paths.cache(), paths.state(), paths.runtime())) {
			java.nio.file.Path resolved = directory;
			try { resolved = directory.toRealPath(); } catch (java.io.IOException ignored) { }
			if (real.startsWith(resolved)) return true;
		}
		return false;
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
