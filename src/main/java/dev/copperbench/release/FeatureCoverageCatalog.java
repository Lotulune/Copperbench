/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Honest first-release feature surfaces. This is a development-stage audit,
 * not a claim that every upstream MCreator tool lives in the new UI.
 */
public final class FeatureCoverageCatalog {

	public enum Surface {
		NEW_UI, LEGACY_WINDOW, HEADLESS_MCP, UNSUPPORTED, NOT_APPLICABLE, DEFERRED
	}

	public record Item(String id, String area, Surface surface, String notes) {
	}

	public static final List<Item> ITEMS = List.of(
			new Item("workspace_lifecycle", "T01", Surface.NEW_UI,
					"Create, open, and import upstream workspaces through the shared application service."),
			new Item("local_history", "T02", Surface.NEW_UI,
					"Local recovery points and restore. Existing Git remotes are not rewritten."),
			new Item("slice_elements", "T03", Surface.NEW_UI,
					"First-party editing covers block, item, recipe, procedure, function, loot table, and advancement. The final three passed the eight-generator golden build but remain Stage 9 development preview until their editor and remaining product gates pass."),
			new Item("other_mod_elements", "T03", Surface.UNSUPPORTED,
					"Living entities and other upstream types are outside the first-party slice. See elementCoverage: imported types stay read-only; create/update are rejected."),
			new Item("assets_blockbench", "T04", Surface.NEW_UI,
					"Asset graph and Blockbench round trip exist. Fabric 1.21.1 runClient loaded file/copper_ready_pack.zip in ResourceManager."),
			new Item("mcp_automation", "T05", Surface.HEADLESS_MCP,
					"First-party local MCP with read_only, workspace, and full_access profiles."),
			new Item("generate_source", "T06", Surface.NEW_UI,
					"The Stage 8 block/item/recipe/procedure slice has all-track evidence. Stage 9 function/loot table/advancement generation and compilation passed all eight generators."),
			new Item("managed_build", "T07", Surface.NEW_UI,
					"Managed build, server, datagen, and GameTest tasks exist. Datagen publishes only after staged diff confirmation. All-track Stage 9 server readiness remains pending."),
			new Item("run_client", "T08", Surface.NEW_UI,
					"runClient evidence exists for Fabric/NeoForge 26.2, 26.1.2, 1.21.1, and 1.20.1."),
			new Item("diagnostics", "T09", Surface.NEW_UI,
					"Structured diagnostics and reason codes are shared by UI, MCP, and headless."),
			new Item("loader_migration", "T10", Surface.NEW_UI,
					"Copy-only same-version Fabric↔NeoForge migration. Source trees stay unchanged."),
			new Item("resource_pack_export", "T10", Surface.NEW_UI,
					"Publish batches export ZIP and prepare run/resourcepacks without auto-launching. Fabric 1.21.1 ResourceManager listed file/copper_ready_pack.zip."),
			new Item("window_chrome_jcef", "U4", Surface.NEW_UI,
					"Playwright help/About/DPI/hit-test is 100/100. Real JCEF product-shell reported React chrome regions, native HTMAXBUTTON=9 Snap hit-test, and WM_DPICHANGED 144 (DPR 1.5)."),
			new Item("java_plugins", "plugins", Surface.LEGACY_WINDOW,
					"Java plugins are opt-in full trust. Swing UI plugins stay in a legacy window. list_installed_plugins returns the live first-party and user plugin inventory without loading Java."),
			new Item("remote_mcp", "excluded", Surface.NOT_APPLICABLE, "Remote MCP is out of first-release scope."),
			new Item("linux_macos", "excluded", Surface.NOT_APPLICABLE,
					"Linux and macOS installers are out of first-release scope."),
			new Item("windows_10", "excluded", Surface.NOT_APPLICABLE,
					"Windows 10 is out of first-release scope. The supported desktop is Windows 11 x64."),
			new Item("accounts_cloud", "excluded", Surface.NOT_APPLICABLE,
					"Accounts, cloud sync, and vendor chat are out of first-release scope."),
			new Item("code_signing", "release", Surface.NOT_APPLICABLE,
					"Unsigned GitHub Releases are the first public distribution. Authenticode is optional and not a release gate. jsign 7.4 stays wired."),
			new Item("clean_machine_install", "release", Surface.NEW_UI,
					"Windows 11 Hyper-V guest silent install/upgrade/uninstall preserved workspace and .copperbench. With the NIC disconnected, copperbench.exe remained running through the 10-second guest check."));

	private FeatureCoverageCatalog() {
	}

	public static JsonArray toJson() {
		JsonArray items = new JsonArray();
		for (Item item : ITEMS) {
			JsonObject json = new JsonObject();
			json.addProperty("id", item.id());
			json.addProperty("area", item.area());
			json.addProperty("surface", item.surface().name().toLowerCase());
			json.addProperty("notes", item.notes());
			items.add(json);
		}
		return items;
	}
}
