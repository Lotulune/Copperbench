/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.ui.action.impl;

import dev.copperbench.ProductIdentity;
import dev.copperbench.release.UserGuideLocator;
import net.mcreator.Launcher;
import net.mcreator.element.GeneratableElement;
import net.mcreator.io.FileIO;
import net.mcreator.io.OS;
import net.mcreator.io.WindowsPackage;
import net.mcreator.ui.MCreatorApplication;
import net.mcreator.ui.action.ActionRegistry;
import net.mcreator.ui.action.BasicAction;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.init.AppIcon;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.util.DesktopUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class AboutAction extends BasicAction {

	public AboutAction(ActionRegistry actionRegistry) {
		super(actionRegistry, L10N.t("action.about"), evt -> showDialog(actionRegistry.getMCreator()));
	}

	public static void showDialog(Window parent) {
		Object[] options = { "使用说明", "上游源码", "GPL-3.0 许可证", "第三方许可证" };

		JPanel logoPanel = new JPanel(new BorderLayout(24, 24));
		logoPanel.add("North", new JLabel(AppIcon.getAppIcon(128, 128)));
		logoPanel.add("Center", new JLabel(UIRES.SVG.getBuiltIn("logo", 250, (int) (250 * (63 / 350.0)))));
		logoPanel.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 0));

		String versionString = ProductIdentity.displayVersion();
		if (WindowsPackage.isRunningAsMSIX()) {
			versionString += " (MSIX)";
		}

		JLabel aboutLabel = new JLabel("<html><b>" + ProductIdentity.NAME + " " + ProductIdentity.VERSION
				+ "</b><br>基于 MCreator " + Launcher.version.getFullString()
				+ " 的独立 GPL-3.0 衍生项目。<br><br>构建版本：" + versionString + "<br>系统架构：" + OS.getArchitecture()
				+ " / " + OS.getBundledJVMBits() + " 位 JVM<br><br>"
				+ "MCreator 和 Pylo 的名称及标识归各自权利人所有。</html>");

		JComponent dialogPanel = PanelUtils.westAndCenterElement(
				PanelUtils.pullElementUp(PanelUtils.centerInPanel(logoPanel)), aboutLabel, 48, 48);
		dialogPanel.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 32));

		int n = JOptionPane.showOptionDialog(parent, dialogPanel, "关于 " + ProductIdentity.NAME,
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
		if (n == 0) {
			openUserGuide(parent);
		} else if (n == 1) {
			DesktopUtils.browseSafe("https://github.com/MCreator/MCreator/tree/2026.2.33518");
		} else if (n == 2) {
			showLicenseWindow(parent);
		} else if (n == 3) {
			try {
				Desktop.getDesktop().open(new File("./license"));
			} catch (IOException e) {
				JOptionPane.showMessageDialog(parent, L10N.t("dialog.about.third_party.message"),
						L10N.t("dialog.about.third_party.title"), JOptionPane.INFORMATION_MESSAGE);
			}
		}
	}

	static void openUserGuide(Window parent) {
		var guide = UserGuideLocator.resolve(Path.of(System.getProperty("user.dir", ".")));
		if (guide.isPresent()) {
			DesktopUtils.openSafe(guide.get().toFile());
			return;
		}
		JOptionPane.showMessageDialog(parent,
				"未找到开发测试版使用说明（docs/user/README.md 或 user/README.md）。",
				"使用说明", JOptionPane.INFORMATION_MESSAGE);
	}

	public static void showLicenseWindow(Window parent) {
		JTextArea licenseText = new JTextArea();
		JScrollPane gradlesp = new JScrollPane(licenseText);
		licenseText.setEditable(false);
		licenseText.setLineWrap(true);
		licenseText.setFont(Theme.current().getConsoleFont());
		ComponentUtils.deriveFont(licenseText, 12);
		licenseText.setWrapStyleWord(true);
		licenseText.setText(FileIO.readFileToString(new File("./LICENSE.txt"))
				+ "\n\n" + FileIO.readFileToString(new File("./LICENSE-ADDITIONAL-TERMS.md")));
		gradlesp.setPreferredSize(new Dimension(570, 570));
		gradlesp.getHorizontalScrollBar().setValue(0);
		gradlesp.getVerticalScrollBar().setValue(0);
		licenseText.setCaretPosition(0);
		JOptionPane.showOptionDialog(parent, gradlesp, L10N.t("dialog.about.eula.title"), JOptionPane.DEFAULT_OPTION,
				JOptionPane.PLAIN_MESSAGE, null, new Object[] { L10N.t("common.close") }, L10N.t("common.close"));
	}

}
