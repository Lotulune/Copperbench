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

		JLabel message = new JLabel("""
				<html>
				<div style='width:460px'>
				<h2>您是否在中国大陆？</h2>
				<p>创建工作区时需要下载 Gradle 和 Maven 依赖。官方地址 <code>services.gradle.org</code> 在中国大陆经常很慢或超时。</p>
				<p>选择“是”后，Copperbench 会帮你配置：</p>
				<ul>
				<li>Gradle 发行版 → 华为云镜像</li>
				<li>Maven Central / Gradle Plugin Portal → 阿里云镜像</li>
				<li>Minecraft 库（libraries.minecraft.net）→ BMCLAPI</li>
				</ul>
				<p>Fabric Maven 与 NeoForge 专用仓库仍走官方地址。之后可在偏好设置的 Gradle 页更改。</p>
				<hr>
				<h2>Are you in mainland China?</h2>
				<p>Workspace setup downloads Gradle and Maven artifacts. Official <code>services.gradle.org</code> often times out on networks in China.</p>
				<p>Choosing Yes configures Huawei Cloud for Gradle distributions, Aliyun for Maven Central / Plugin Portal, and BMCLAPI for Minecraft libraries.</p>
				<p>Fabric Maven and NeoForge specialised repositories stay official. You can change this later in Preferences → Gradle.</p>
				</div>
				""");
		message.setForeground(Theme.current().getForegroundColor());
		message.setPreferredSize(new Dimension(520, 440));

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
		content.add(message, BorderLayout.CENTER);
		content.add(buttons, BorderLayout.SOUTH);

		dialog.add(content);
		if (isChineseOs())
			dialog.getRootPane().setDefaultButton(yes);
		else
			dialog.getRootPane().setDefaultButton(no);
		dialog.pack();
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	private static boolean isChineseOs() {
		Locale os = L10N.getOSLocale();
		return os != null && "zh".equalsIgnoreCase(os.getLanguage());
	}

}
