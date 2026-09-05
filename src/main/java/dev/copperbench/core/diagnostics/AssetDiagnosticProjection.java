/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core.diagnostics;

import com.google.gson.JsonObject;
import dev.copperbench.assets.AssetDescriptor;
import dev.copperbench.assets.AssetDiagnostic;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.core.contract.UiCore.ActionHint;
import dev.copperbench.core.contract.UiCore.Diagnostic;
import dev.copperbench.core.contract.UiCore.LocalizedText;

import java.util.List;

/** Projects asset-index diagnostics into the shared UI/Core/MCP diagnostic contract. */
public final class AssetDiagnosticProjection {

	private AssetDiagnosticProjection() {
	}

	public static Diagnostic project(AssetDiagnostic diagnostic) {
		String assetId = AssetDescriptor.stableIdForPath(diagnostic.sourcePath());
		JsonObject args = new JsonObject();
		args.addProperty("sourcePath", diagnostic.sourcePath());
		if (diagnostic.targetPath() != null) args.addProperty("targetPath", diagnostic.targetPath());
		args.addProperty("detail", diagnostic.message());
		String key = switch (diagnostic.code()) {
			case "INVALID_ASSET_DOCUMENT" -> "diagnostic.asset_invalid_document";
			case "REFERENCE_PATH_ESCAPE" -> "diagnostic.asset_reference_path_escape";
			case "MISSING_ASSET_REFERENCE" -> "diagnostic.asset_missing_reference";
			default -> "diagnostic.asset_issue";
		};
		String fallback = switch (diagnostic.code()) {
			case "INVALID_ASSET_DOCUMENT" -> "Asset document {sourcePath} is invalid: {detail}";
			case "REFERENCE_PATH_ESCAPE" -> "Asset {sourcePath} contains a reference outside the workspace: {targetPath}";
			case "MISSING_ASSET_REFERENCE" -> "Asset {sourcePath} references missing asset {targetPath}.";
			default -> "Asset {sourcePath} requires review: {detail}";
		};
		UiCore.Severity severity = switch (diagnostic.severity()) {
			case INFO -> UiCore.Severity.INFO;
			case WARNING -> UiCore.Severity.WARNING;
			case ERROR -> UiCore.Severity.ERROR;
		};
		return new Diagnostic(diagnostic.code(), severity, LocalizedText.of(key, fallback, args),
				"/assets/" + assetId, null, true,
				List.of(new ActionHint("open_asset", LocalizedText.of("action.open_asset", "Open asset"),
						"open_asset", assetId)));
	}
}
