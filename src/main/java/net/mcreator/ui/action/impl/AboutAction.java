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
import dev.copperbench.shell.ProductDialogs;
import dev.copperbench.shell.ProductUpdateDialog;
import static dev.copperbench.shell.UiLocalePreferences.text;
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
		JDialog dialog = new net.mcreator.ui.dialogs.MCreatorDialog(parent, text("帮助与关于", "Help & about"), true);
		JPanel content = new JPanel(new BorderLayout(0, 20));
		content.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));
		JPanel heading = new JPanel(new BorderLayout(16, 0));
		heading.add(new JLabel(AppIcon.getAppIcon(64, 64)), BorderLayout.WEST);
		JLabel name = new JLabel(ProductIdentity.NAME + " " + ProductIdentity.VERSION);
		name.setFont(name.getFont().deriveFont(Font.BOLD, 22f));
		heading.add(name, BorderLayout.CENTER);
		content.add(heading, BorderLayout.NORTH);
		JPanel middle = new JPanel(new BorderLayout(0, 20));
		JTextArea description = ProductDialogs.body(text("基于 MCreator ", "Based on MCreator ")
				+ (Launcher.version != null ? Launcher.version.getFullString() : "2026.2")
				+ text(" 的独立 GPL-3.0 衍生项目。", ", an independent GPL-3.0 derivative.")
				+ "\n\n" + text("构建版本：", "Build: ") + ProductIdentity.displayVersion()
				+ (WindowsPackage.isRunningAsMSIX() ? " (MSIX)" : "")
				+ "\n" + text("系统架构：", "Architecture: ") + OS.getArchitecture()
				+ " / " + OS.getBundledJVMBits() + text(" 位 JVM", "-bit JVM")
				+ "\n\n" + text("MCreator 与 Pylo 的名称和标识归各自权利人所有。", "MCreator and Pylo names and marks belong to their respective owners."));
		description.setColumns(40); description.setRows(7);
		middle.add(description, BorderLayout.NORTH);
		JPanel links = new JPanel(new GridLayout(0, 2, 10, 10));
		addLink(links, text("使用指南", "User guide"), () -> openUserGuide(dialog));
		addLink(links, text("检查更新", "Check for updates"), () -> { dialog.dispose(); ProductUpdateDialog.show(parent); });
		addLink(links, text("上游源码", "Upstream source"), () -> DesktopUtils.browseSafe("https://github.com/MCreator/MCreator/tree/2026.2.33518"));
		addLink(links, text("GPL-3.0 许可证", "GPL-3.0 license"), () -> showLicenseWindow(dialog));
		addLink(links, text("第三方许可证", "Third-party licenses"), () -> {
			try { Desktop.getDesktop().open(new File("./license")); }
			catch (IOException error) {
				ProductDialogs.show(dialog, L10N.t("dialog.about.third_party.message"),
						L10N.t("dialog.about.third_party.title"), JOptionPane.INFORMATION_MESSAGE,
						new Object[]{text("确定", "OK")}, text("确定", "OK"));
			}
		});
		middle.add(links, BorderLayout.CENTER);
		content.add(middle, BorderLayout.CENTER);
		JButton close = new JButton(text("关闭", "Close"));
		close.addActionListener(_ -> dialog.dispose());
		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); footer.add(close);
		content.add(footer, BorderLayout.SOUTH);
		dialog.setContentPane(content); dialog.getRootPane().setDefaultButton(close);
		dialog.pack(); dialog.setResizable(true); dialog.setMinimumSize(dialog.getSize());
		dialog.setLocationRelativeTo(parent); dialog.setVisible(true);
	}

	private static void addLink(JPanel panel, String label, Runnable action) {
		JButton button = new JButton(label);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setMargin(new Insets(12, 14, 12, 14));
		button.addActionListener(_ -> action.run());
		panel.add(button);
	}

	static void openUserGuide(Window parent) {
		var guide = UserGuideLocator.resolve(Path.of(System.getProperty("user.dir", ".")));
		if (guide.isPresent()) {
			DesktopUtils.openSafe(guide.get().toFile());
			return;
		}
		JOptionPane.showMessageDialog(parent,
				text("未找到使用指南，请检查安装目录中的 docs/user/README.md。", "The user guide was not found. Check docs/user/README.md in the installation folder."),
				text("使用指南", "User guide"), JOptionPane.INFORMATION_MESSAGE);
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
		Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		gradlesp.setPreferredSize(new Dimension(Math.min(570, screen.width - 160), Math.min(570, screen.height - 200)));
		gradlesp.getHorizontalScrollBar().setValue(0);
		gradlesp.getVerticalScrollBar().setValue(0);
		licenseText.setCaretPosition(0);
		JOptionPane.showOptionDialog(parent, gradlesp, L10N.t("dialog.about.eula.title"), JOptionPane.DEFAULT_OPTION,
				JOptionPane.PLAIN_MESSAGE, null, new Object[] { L10N.t("common.close") }, L10N.t("common.close"));
	}

}
