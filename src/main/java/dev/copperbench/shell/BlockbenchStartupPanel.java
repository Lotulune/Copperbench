package dev.copperbench.shell;

import dev.copperbench.assets.BlockbenchConfiguration;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.util.DesktopUtils;

import javax.swing.*;
import java.awt.*;

/** Optional editor setup available before a workspace exists. */
public final class BlockbenchStartupPanel extends JPanel {

	public BlockbenchStartupPanel(BlockbenchConfiguration configuration, Runnable openGuide) {
		super(new BorderLayout(0, 4));
		setOpaque(false);
		JTextArea introduction = new JTextArea(UiLocalePreferences.text("自定义模型可使用 Blockbench，也可稍后设置。", "Use Blockbench for custom models, or set it up later."));
		introduction.setFont(UIManager.getFont("Label.font"));
		introduction.setForeground(ProductTheme.muted());
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
				introduction.setText(UiLocalePreferences.text("偏好保存失败，请稍后重试。仍可继续打开或创建工作区。", "Could not save the preference. You can still open or create a workspace."));
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
		JTextArea explanation = ProductDialogs.body(UiLocalePreferences.text(
				"Blockbench 是可选的建模工具。你可以先创建作品，之后再设置自定义模型。\n\n已有安装可直接复用。AI 建模需要另外安装社区 MCP 插件；打开工作区后，在资产中心连接并导入模型。",
				"Blockbench is an optional modeling tool. You can create your project first and set up custom models later.\n\nReuse an existing installation below. AI modeling also requires the community MCP plugin. Connect and import models from Assets after opening a workspace."));
		explanation.setColumns(40);
		explanation.setRows(7);
		content.add(explanation, BorderLayout.NORTH);
		JPanel actions = new JPanel(new GridLayout(0, 1, 0, 6));
		JButton download = new JButton(UiLocalePreferences.text("打开 Blockbench 下载页", "Open Blockbench downloads"));
		download.addActionListener(_ -> DesktopUtils.browseSafe("https://www.blockbench.net/"));
		actions.add(download);
		JButton plugin = new JButton(UiLocalePreferences.text("打开社区 MCP 插件说明", "Open community MCP plugin guide"));
		plugin.addActionListener(_ -> DesktopUtils.browseSafe("https://github.com/jasonjgardner/blockbench-mcp-plugin"));
		actions.add(plugin);
		JTextArea status = ProductDialogs.body(" ");
		status.setRows(2);
		JButton select = new JButton(UiLocalePreferences.text("选择已有 Blockbench 安装位置", "Choose an existing Blockbench installation"));
		select.addActionListener(_ -> {
			var file = FileDialogs.getOpenDialog(SwingUtilities.getWindowAncestor(content), new String[] {});
			if (file == null) return;
			try {
				BlockbenchConfiguration.productDefault().select(file.toPath());
				status.setText(UiLocalePreferences.text("安装位置已保存。", "Installation location saved."));
			} catch (RuntimeException exception) { status.setText(UiLocalePreferences.text("所选文件不是有效的 Blockbench 安装，请重新选择。", "This file is not a valid Blockbench installation. Choose another file.")); }
		});
		actions.add(select);
		content.add(actions, BorderLayout.CENTER);
		content.add(status, BorderLayout.SOUTH);
		JOptionPane.showMessageDialog(owner, content, UiLocalePreferences.text("Blockbench 建模工具", "Blockbench modeling tools"), JOptionPane.PLAIN_MESSAGE);
	}
}
