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
			new Tool("workspace_open_create_import", "打开、新建、导入工作区（File / Workspace）", Surface.NEW_UI,
					"导入时创建同级目录副本，并保留未知字段。"),
			new Tool("mod_elements_first_party",
					"模组元素（Mod Elements）：37 种 Java 元素类型",
					Surface.NEW_UI, "所有 Java 元素类型均可通过通用编辑器管理；可生成的功能取决于加载器和版本。"),
			new Tool("mod_elements_other", "生物实体、界面、植物、维度等元素编辑器",
					Surface.NEW_UI,
					"这些 Java 元素类型已纳入内置支持；基岩版专属元素不在此范围内。"),
			new Tool("assets_blockbench", "模型与动画（Blockbench）", Surface.NEW_UI,
					"从资产视图打开独立的 Blockbench 编辑器，并跟踪资源引用。"),
			new Tool("resource_pack_workspace", "资源包制作与 ZIP 导出（Resource Pack）", Surface.NEW_UI,
					"支持可复现 ZIP 导出、发布批次和客户端准备；已有 Fabric 1.21.1 资源管理器加载验证记录。"),
			new Tool("textures_sounds_structures", "纹理、声音、结构与截图（Resources）",
					Surface.LEGACY_WINDOW,
					"纹理、声音和结构浏览器仍在旧版 Swing 窗口中；模型编辑使用 Blockbench。"),
			new Tool("image_armor_animation_makers", "图像编辑器、盔甲纹理与动态纹理（Tools）",
					Surface.LEGACY_WINDOW, "这些制作工具仍在旧版 Swing 窗口中。"),
			new Tool("tags_variables_localization", "标签、变量与本地化（Tags / Variables / Localization）", Surface.NEW_UI,
					"在“变量与数据”中管理条目、预览重命名影响、导入导出 CSV/JSON，并查看缺失或重复翻译。"),
			new Tool("code_ide_and_file_browser", "代码编辑器与工作区文件浏览器（Code Editor）",
					Surface.LEGACY_WINDOW, "手写源码通过旧版编辑器维护；新工作台暂不提供完整代码开发环境。"),
			new Tool("pack_makers", "材料、矿石、工具、盔甲与木材套装向导（Pack Makers）", Surface.LEGACY_WINDOW,
					"套装制作向导仍在旧版 Swing 窗口中，暂不提供对应 MCP 命令。"),
			new Tool("vanilla_data_lists", "原版实体、物品、方块、粒子、声音与战利品表列表",
					Surface.LEGACY_WINDOW, "原版数据的只读浏览器仍在旧版窗口中。"),
			new Tool("generate_build_run_client", "构建、测试客户端、重新生成代码与导出 JAR", Surface.NEW_UI,
					"可在工作台启动任务；八个内置生成器均已有客户端启动（runClient）验证记录。"),
			new Tool("run_server_debug_client", "测试服务端、数据生成（Datagen）与游戏测试（GameTest）", Surface.NEW_UI,
					"预览任务使用隔离运行目录；暂不支持调试客户端或执行任意 Gradle 任务。"),
			new Tool("workspace_settings_tab_order", "工作区设置与创造模式标签页物品排序",
					Surface.LEGACY_WINDOW, "完整的生成器和工作区设置对话框仍在旧版 Swing 窗口中。"),
			new Tool("preferences", "偏好设置（Preferences）", Surface.LEGACY_WINDOW,
					"完整偏好设置仍在旧版 Swing 窗口中，隐私默认值由 Copperbench 管理。"),
			new Tool("local_history", "本地历史（Local History）", Surface.NEW_UI,
					"支持创建恢复点和还原工作区，不改写 Git 远程配置。"),
			new Tool("plugin_manager", "插件管理（Plugins）", Surface.NEW_UI,
					"可查看已安装插件；Swing 界面插件在旧版窗口打开，Java 插件需要手动启用。"),
			new Tool("website_community_publish_donate", "官网、社区、发布与捐赠（Help）",
					Surface.NOT_APPLICABLE, "不提供产品账号、商店或自动联网服务。"),
			new Tool("check_for_updates", "检查产品和插件更新（Check for Updates）", Surface.NOT_APPLICABLE,
					"自动联网服务已禁用，当前版本不提供更新检查。"));

	private UpstreamToolCatalog() {
	}

	public static JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", "1.0");
		root.addProperty("notes",
				"此表说明 MCreator 工具在 Copperbench 中的入口和支持范围。旧版窗口的布局与无障碍支持可能与新工作台不同。");
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
