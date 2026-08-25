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
 * Honest map of upstream MCreator user tools onto first-release surfaces.
 * This is not a claim that the legacy window reproduces every Swing editor.
 */
public final class UpstreamToolCatalog {

	public enum Surface {
		NEW_UI, LEGACY_WINDOW, UNSUPPORTED, NOT_APPLICABLE
	}

	public record Tool(String id, String upstream, Surface surface, String notes) {
	}

	public static final List<Tool> TOOLS = List.of(
			new Tool("workspace_open_create_import", "File/Open, New, Import workspace", Surface.NEW_UI,
					"Shared application service. Import copies to a sibling directory and preserves unknown fields."),
			new Tool("mod_elements_first_party",
					"Workspace/Mod elements for block, item, recipe, procedure, function, loot table, advancement",
					Surface.NEW_UI, "Seven first-party editable types. The three Stage 9 data types passed the eight-generator golden build but remain development preview until their dedicated editors and remaining product gates pass."),
			new Tool("mod_elements_other", "Living entity, GUI, plant, dimension, and other element editors",
					Surface.UNSUPPORTED,
					"Imported definitions are preserved and listed read-only. Create/update in the new UI are rejected."),
			new Tool("assets_blockbench", "Resources/3D models and animations via Blockbench", Surface.NEW_UI,
					"Asset graph, leases, and the Blockbench process bridge. The editor is not embedded."),
			new Tool("resource_pack_workspace", "Resource pack maker and ZIP export", Surface.NEW_UI,
					"Deterministic ZIP, publish batches, and prepare_resource_pack_client. Fabric 1.21.1 ResourceManager load is claimed."),
			new Tool("textures_sounds_structures", "Resources/Textures, Sounds, Structures, Screenshots",
					Surface.LEGACY_WINDOW,
					"Native texture/sound/structure browsers stay in the legacy Swing window. Blockbench is the first-party model path."),
			new Tool("image_armor_animation_makers", "Tools/Image editor, Armor texture, Animated texture",
					Surface.LEGACY_WINDOW, "Built-in image and armor makers remain Swing. They are not in the React shell."),
			new Tool("tags_variables_localization", "Workspace/Tags, Variables, Localization", Surface.NEW_UI,
					"The Creator Data view provides stable-ID CRUD, reference counts, and rename impact previews. Language CSV/JSON import and export remain pending."),
			new Tool("code_ide_and_file_browser", "Code editor, workspace file browser, reformat/save",
					Surface.LEGACY_WINDOW, "Hand-written source is an advanced exit, not a first-party React IDE."),
			new Tool("pack_makers", "Tools/Material, Ore, Tool, Armor, Wood pack makers", Surface.LEGACY_WINDOW,
					"Wizard pack makers stay in Swing. They are not first-party MCP commands."),
			new Tool("vanilla_data_lists", "Tools/Entity IDs, item/block list, particles, sounds, loot tables",
					Surface.LEGACY_WINDOW, "Read-only vanilla data browsers remain in the legacy window."),
			new Tool("generate_build_run_client", "Build, run client, regenerate code, export JAR", Surface.NEW_UI,
					"Shared tasks. runClient evidence exists for all eight first-party generators."),
			new Tool("run_server_debug_client", "Run server, datagen, and existing GameTest", Surface.NEW_UI,
					"Managed development-preview tasks use isolated run directories. Debug client and arbitrary Gradle task execution are not first-party commands."),
			new Tool("workspace_settings_tab_order", "Workspace settings, creative tab item order",
					Surface.LEGACY_WINDOW, "Full generator/settings dialogs remain Swing."),
			new Tool("preferences", "Preferences dialog", Surface.LEGACY_WINDOW,
					"Privacy defaults are product-owned. The full preference tree stays in Swing."),
			new Tool("local_history", "Local history panel", Surface.NEW_UI,
					"Recovery points and restore through the shared history service. Git remotes are not rewritten."),
			new Tool("plugin_manager", "Plugins panel", Surface.NEW_UI,
					"list_installed_plugins is first-party. Swing UI plugins still open in the legacy window. Java plugins stay opt-in."),
			new Tool("website_community_publish_donate", "Help/Website, community, publish, donate",
					Surface.NOT_APPLICABLE, "No product accounts, store, or implicit network services."),
			new Tool("check_for_updates", "Help/Check for updates and plugin updates", Surface.NOT_APPLICABLE,
					"Implicit network services are disabled. Update checks are not first-release behavior."));

	private UpstreamToolCatalog() {
	}

	public static JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", "1.0");
		root.addProperty("notes",
				"Upstream Swing tools are either in the new UI, opened through the legacy plugin window, explicitly unsupported, or out of first-release scope. The legacy window is not a visual or accessibility promise.");
		JsonArray items = new JsonArray();
		for (Tool tool : TOOLS) {
			JsonObject json = new JsonObject();
			json.addProperty("id", tool.id());
			json.addProperty("upstream", tool.upstream());
			json.addProperty("surface", tool.surface().name().toLowerCase());
			json.addProperty("notes", tool.notes());
			items.add(json);
		}
		root.add("tools", items);
		return root;
	}
}
