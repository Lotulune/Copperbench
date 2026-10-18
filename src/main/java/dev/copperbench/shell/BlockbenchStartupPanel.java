package dev.copperbench.shell;

import dev.copperbench.assets.BlockbenchConfiguration;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.util.DesktopUtils;

import javax.swing.*;
import java.awt.*;
import static dev.copperbench.shell.UiLocalePreferences.text;

/** Optional editor setup available before a workspace exists. */
public final class BlockbenchStartupPanel extends JPanel {

	public BlockbenchStartupPanel(BlockbenchConfiguration configuration, Runnable openGuide) {
		super(new BorderLayout(0, 4));
		setOpaque(false);
		JTextArea introduction = new JTextArea(UiLocalePreferences.text("自定义模型可使用 Blockbench，也可稍后设置。", "Use Blockbench for custom models, or set it up later."));
		introduction.setFont(UIManager.getFont("Label.font"));
		introduction.setEditable(false);
		introduction.setOpaque(false);
		introduction.setLineWrap(true);
		introduction.setWrapStyleWord(true);
		introduction.setColumns(26);
		introduction.setRows(2);
		introduction.setName("blockbench-startup-introduction");
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		actions.setOpaque(false);
		JButton guide = new JButton(UiLocalePreferences.text("建模工具设置", "Modeling tools"));
		guide.setName("blockbench-startup-guide");
		guide.addActionListener(_ -> openGuide.run());
		actions.add(guide);
		actions.add(Box.createHorizontalStrut(6));
		JButton skip = new JButton(UiLocalePreferences.text("稍后设置", "Later"));
		skip.setName("blockbench-startup-skip");
		skip.addActionListener(_ -> {
			try {
				configuration.dismissOnboarding();
				introduction.setVisible(false);
				skip.setVisible(false);
				revalidate();
				repaint();
			} catch (RuntimeException exception) {
				introduction.setText(text("偏好保存失败，请稍后重试。仍可继续打开或创建工作区。", "Could not save the preference. You can still open or create a workspace."));
			}
		});
		actions.add(skip);
		boolean dismissed = configuration.onboardingDismissed();
		introduction.setVisible(!dismissed);
		skip.setVisible(!dismissed);
		addHierarchyListener(event -> {
			if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
				boolean hidden = configuration.onboardingDismissed();
				introduction.setVisible(!hidden);
				skip.setVisible(!hidden);
			}
		});
		add(introduction, BorderLayout.NORTH);
		add(actions, BorderLayout.CENTER);
	}

	public static void showGuide(Component owner) {
		JPanel content = new JPanel(new BorderLayout(0, 12));
		JLabel explanation = new JLabel("<html><div style='width:340px'>"
				+ text("Blockbench 是独立的可选建模工具。普通模组开发无需先安装。<br><br>"
				+ "手工编辑只需桌面编辑器；AI 建模还需按社区项目说明安装 MCP 插件。"
				+ "两者均为 GPLv3 项目，社区插件并非 Blockbench 官方 MCP。"
				+ "Copperbench 不捆绑或自动安装它们。<br><br>"
				+ "已有安装可直接复用；非标准位置可在下方选择。打开工作区后，"
				+ "在资产中心检测连接、创建建模任务并审核回导结果。",
				"Blockbench is an optional, standalone modeling tool. You can create mods without it.<br><br>"
				+ "Manual editing needs the desktop editor. AI modeling also needs the community MCP plugin. "
				+ "Both are GPLv3 projects; the plugin is not an official Blockbench MCP service. "
				+ "Copperbench does not bundle or install either.<br><br>"
				+ "Reuse an existing installation or choose its location below. After opening a workspace, "
				+ "use Assets to check connections, create modeling tasks, and review imports.")
				+ "</div></html>");
		content.add(explanation, BorderLayout.NORTH);
		JPanel actions = new JPanel(new GridLayout(0, 1, 0, 6));
		JButton download = new JButton(text("打开 Blockbench 官方下载页", "Open the official Blockbench download page"));
		download.addActionListener(_ -> DesktopUtils.browseSafe("https://www.blockbench.net/"));
		actions.add(download);
		JButton plugin = new JButton(text("打开社区 MCP 插件说明", "Open the community MCP plugin guide"));
		plugin.addActionListener(_ -> DesktopUtils.browseSafe("https://github.com/jasonjgardner/blockbench-mcp-plugin"));
		actions.add(plugin);
		JLabel status = new JLabel(text("不会自动安装软件或修改 Agent 配置。", "No automatic installs or Agent configuration changes."));
		JButton select = new JButton(text("选择已有 Blockbench 安装位置", "Choose an existing Blockbench installation"));
		select.addActionListener(_ -> {
			var file = FileDialogs.getOpenDialog(SwingUtilities.getWindowAncestor(content), new String[] {});
			if (file == null) { status.setText(text("已取消选择，原设置保持不变。", "Selection cancelled. Your settings are unchanged.")); return; }
			try {
				BlockbenchConfiguration.productDefault().select(file.toPath());
				status.setText(text("安装位置已保存，可继续创建或打开工作区。", "Location saved. You can create or open a workspace."));
			} catch (RuntimeException exception) { status.setText(text("所选文件无法验证为 Blockbench，请重新选择。", "Could not verify this file as Blockbench. Choose another file.")); }
		});
		actions.add(select);
		content.add(actions, BorderLayout.CENTER);
		content.add(status, BorderLayout.SOUTH);
		JOptionPane.showMessageDialog(owner, content, text("Blockbench · 可选建模工具", "Blockbench · Optional modeling tool"), JOptionPane.PLAIN_MESSAGE);
	}
}
