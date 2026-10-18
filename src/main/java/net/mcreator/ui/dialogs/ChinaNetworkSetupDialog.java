/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.dialogs;

import dev.copperbench.ProductIdentity;
import dev.copperbench.shell.ProductDialogs;
import static dev.copperbench.shell.UiLocalePreferences.text;
import dev.copperbench.network.ChinaMirrorService;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.component.util.ThreadUtil;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.util.TestUtil;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/** First-run prompt that asks whether to configure mainland-China package mirrors. */
public final class ChinaNetworkSetupDialog {

	private ChinaNetworkSetupDialog() {
	}

	public static void promptIfNeeded(@Nullable Window parent) {
		if (TestUtil.isTestingEnvironment() || GraphicsEnvironment.isHeadless())
			return;
		if (PreferencesManager.PREFERENCES == null || ChinaMirrorService.hasBeenPrompted())
			return;
		ThreadUtil.runOnSwingThreadAndWait(() -> show(parent));
	}

	private static void show(@Nullable Window parent) {
		MCreatorDialog dialog = new MCreatorDialog(parent, ProductIdentity.NAME, true);
		dialog.setClosable(false);

		JLabel heading = new JLabel(text("使用中国大陆下载镜像？", "Use download mirrors in mainland China?"));
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
		JTextArea message = ProductDialogs.body(text(
				"镜像可改善中国大陆网络下的依赖下载速度。\n\n启用后，将使用：\nGradle 发行版：华为云\nMaven Central / Plugin Portal：阿里云\nMinecraft 库：BMCLAPI\n\nFabric 和 NeoForge 专用仓库继续使用官方地址。你可以随时在偏好设置 → Gradle 中更改。",
				"Mirrors can improve dependency downloads on mainland China networks.\n\nWhen enabled, downloads use:\nGradle distributions: Huawei Cloud\nMaven Central / Plugin Portal: Aliyun\nMinecraft libraries: BMCLAPI\n\nFabric and NeoForge repositories keep their official addresses. Change this later in Preferences → Gradle."));
		message.setColumns(42); message.setRows(12);
		JScrollPane scroll = new JScrollPane(message);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		JButton yes = new JButton(L10N.t("dialog.china_network.yes"));
		JButton no = new JButton(L10N.t("dialog.china_network.no"));
		yes.addActionListener(_ -> {
			ChinaMirrorService.rememberChoice(true);
			dialog.dispose();
		});
		no.addActionListener(_ -> {
			ChinaMirrorService.rememberChoice(false);
			dialog.dispose();
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setOpaque(false);
		buttons.add(no);
		buttons.add(yes);

		JPanel content = new JPanel(new BorderLayout(0, 16));
		content.setOpaque(false);
		content.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
		content.add(heading, BorderLayout.NORTH);
		content.add(scroll, BorderLayout.CENTER);
		content.add(buttons, BorderLayout.SOUTH);

		dialog.add(content);
		if (isChineseOs())
			dialog.getRootPane().setDefaultButton(yes);
		else
			dialog.getRootPane().setDefaultButton(no);
		dialog.pack();
		Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		dialog.setSize(Math.min(dialog.getWidth(), screen.width - 64), Math.min(dialog.getHeight(), screen.height - 64));
		dialog.setResizable(true);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	private static boolean isChineseOs() {
		Locale os = L10N.getOSLocale();
		return os != null && "zh".equalsIgnoreCase(os.getLanguage());
	}

}
