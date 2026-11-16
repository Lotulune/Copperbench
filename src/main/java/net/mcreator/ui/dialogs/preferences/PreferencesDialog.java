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

package net.mcreator.ui.dialogs.preferences;

import dev.copperbench.network.ChinaMirrorService;
import net.mcreator.blockly.data.BlocklyLoader;
import net.mcreator.plugin.MCREvent;
import net.mcreator.plugin.events.ui.PreferencesDialogEvent;
import net.mcreator.preferences.PreferencesEntry;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.blockly.BlocklyEditorType;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.dialogs.MCreatorDialog;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.Theme;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class PreferencesDialog extends MCreatorDialog {

	final JPanel preferences = new JPanel();

	private final Map<String, JPanel> sectionPanels = new HashMap<>();

	private final DefaultMutableTreeNode sectionsRoot = new DefaultMutableTreeNode();
	private DefaultMutableTreeNode templatesNode;
	private final DefaultTreeModel sectionsModel = new DefaultTreeModel(sectionsRoot);
	private final JTree sections = new JTree(sectionsModel);
	private final CardLayout preferencesLayout = new CardLayout();

	private final Map<PreferencesEntry<?>, JComponent> entries = new HashMap<>();

	private final JButton apply = L10N.button("action.common.apply");

	private final Window parent;

	public PreferencesDialog(Window parent) {
		this(parent, null);
	}

	public PreferencesDialog(Window parent, @Nullable String selectedTab) {
		super(parent);

		this.parent = parent;

		setModal(true);
		setTitle(dev.copperbench.shell.UiLocalePreferences.text("Copperbench 偏好设置", "Copperbench preferences"));

		sections.setBackground(getBackground());
		sections.setRootVisible(false);
		sections.setShowsRootHandles(true);
		sections.setExpandsSelectedPaths(false);
		sections.setToggleClickCount(1);
		sections.setCellRenderer(new DefaultTreeCellRenderer() {
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
					boolean leaf, int row, boolean hasFocus) {
				super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
				setBorder(new EmptyBorder(4, 8, 4, 10));
				return this;
			}
		});

		preferences.setLayout(preferencesLayout);

		sections.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
		JScrollPane navigation = new JScrollPane(sections);
		navigation.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, lineColor()));
		navigation.setPreferredSize(new Dimension(204, 0));
		add("West", navigation);
		add("Center", preferences);

		sections.setBackground(UIManager.getColor("Panel.background"));
		ComponentUtils.deriveFont(sections, 13);

		JButton ok = L10N.button("dialog.preferences.save");
		JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));

		JButton reset = L10N.button("dialog.preferences.restore_defaults");
		reset.addActionListener(_ -> {
			int option = JOptionPane.showConfirmDialog(null, L10N.t("dialog.preferences.restore_defaults_confirmation"),
					L10N.t("dialog.preferences.restore_defaults"), JOptionPane.YES_NO_OPTION,
					JOptionPane.QUESTION_MESSAGE, null);
			if (option == JOptionPane.YES_OPTION) {
				PreferencesManager.getPreferencesRegistry()
						.forEach((_, entries) -> PreferencesManager.resetFromList(entries));

				dispose();
				new PreferencesDialog(parent, getSelectedSection());
			}
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(ok);
		buttons.add(cancel);
		buttons.add(apply);
		apply.setEnabled(false);

		JPanel buttonsleft = new JPanel();
		buttonsleft.add(reset);

		JPanel buttonBar = PanelUtils.westAndEastElement(buttonsleft, buttons);
		buttonBar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, lineColor()),
				BorderFactory.createEmptyBorder(4, 8, 4, 8)));
		add("South", buttonBar);

		sections.addTreeSelectionListener(_ -> {
			String section = getSelectedSection();
			if (section != null)
				preferencesLayout.show(preferences, section);
		});

		loadSections();

		if (selectedTab != null)
			selectSection(selectedTab);

		ok.addActionListener(_ -> {
			if (savePreferences()) dispose();
		});

		apply.addActionListener(_ -> {
			if (savePreferences()) apply.setEnabled(false);
		});

		cancel.addActionListener(_ -> dispose());

		pack();
		Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		setSize(Math.min(1000, screen.width - 48), Math.min(660, screen.height - 72));
		setMinimumSize(new Dimension(Math.min(900, screen.width - 48), Math.min(420, screen.height - 72)));
		setLocationRelativeTo(parent);
		setVisible(true);
	}

	private void loadSections() {
		// Add preference entries
		PreferencesManager.getPreferencesRegistry().forEach(
				(_, preferences) -> preferences.stream().filter(e -> e.getSection().isVisible()).toList()
						.forEach(entry -> {
							if (!sectionPanels.containsKey(entry.getSectionKey()))
								createPreferenceSection(entry.getSectionKey());
							entries.put(entry, generateEntryComponent(entry, sectionPanels.get(entry.getSectionKey())));
						}));

		new PluginsPanel(this);

		new ThemesPanel(this);

		new EditTemplatesPanel(this, L10N.t("dialog.preferences.page_ui_backgrounds"), "backgrounds", "png", true);

		templatesNode = new DefaultMutableTreeNode(L10N.t("dialog.preferences.templates"));
		sectionsRoot.add(templatesNode);

		addEditTemplatesPanel("texture_templates", "templates/textures/texturemaker", "png");
		addEditTemplatesPanel("armor_templates", "templates/textures/armormaker", "png");

		BlocklyLoader.INSTANCE.getAllBlockLoaders().keySet().stream().filter(type -> type.extension() != null)
				.forEach(this::addEditTemplatesPanel);

		MCREvent.event(new PreferencesDialogEvent.SectionsLoaded(this));

		sections.collapsePath(new TreePath(templatesNode.getPath()));

		if (sectionsRoot.getChildCount() > 0) {
			DefaultMutableTreeNode first = (DefaultMutableTreeNode) sectionsRoot.getChildAt(0);
			sections.setSelectionPath(new TreePath(first.getPath()));
		}
	}

	void addSection(String name) {
		sectionsRoot.add(new DefaultMutableTreeNode(name));
		sectionsModel.reload();
	}

	void addTemplateSection(String name) {
		templatesNode.add(new DefaultMutableTreeNode(name));
		sectionsModel.reload();
	}

	@Nullable private String getSelectedSection() {
		TreePath path = sections.getSelectionPath();
		if (path == null)
			return null;
		DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
		if (!node.isLeaf())
			return null;
		return (String) node.getUserObject();
	}

	private void selectSection(String name) {
		for (int i = 0; i < sectionsRoot.getChildCount(); i++) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) sectionsRoot.getChildAt(i);
			if (node.isLeaf() && name.equals(node.getUserObject())) {
				sections.setSelectionPath(new TreePath(node.getPath()));
				return;
			}
			for (int j = 0; j < node.getChildCount(); j++) {
				DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(j);
				if (name.equals(child.getUserObject())) {
					sections.expandPath(new TreePath(node.getPath()));
					sections.setSelectionPath(new TreePath(child.getPath()));
					return;
				}
			}
		}
	}

	public void addEditTemplatesPanel(BlocklyEditorType type) {
		addEditTemplatesPanel(type.registryName() + "_templates", "templates/" + type.extension(), type.extension());
	}

	public void addEditTemplatesPanel(String translationKey, String folder, String extension) {
		new EditTemplatesPanel(this, L10N.t("dialog.preferences.page_" + translationKey), folder, extension);
	}

	private void createPreferenceSection(String section) {
		String name = L10N.t("preferences.section." + section);
		String description = L10N.t("preferences.section." + section + ".description");
		addSection(name);

		JPanel sectionPanel = new JPanel(new GridBagLayout());
		sectionPanel.setOpaque(false);
		sectionPanel.setBorder(BorderFactory.createLineBorder(lineColor()));

		JLabel titlebar = new JLabel("<html><div style='width:520px'><font style=\"font-size: 16px;\">" + name
				+ "</font><br><font style=\"font-size: 9px; color: gray;\">" + description + "</font></div></html>");

		JScrollPane scrollPane = new JScrollPane(new SheetHolder(sectionPanel));
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(10);
		preferences.add(page(titlebar, scrollPane), name);

		sectionPanels.put(section, sectionPanel);
	}

	private boolean savePreferences() {
		try {
		PreferencesManager.getPreferencesRegistry().forEach((_, preferences) -> preferences.forEach(entry -> {
			if (entries.containsKey(entry))
				entry.setValueFromComponent(entries.get(entry));
		}));
		PreferencesManager.savePreferences();
		ChinaMirrorService.syncUserHome();
		L10N.initTranslations();
		return true;
		} catch (java.io.UncheckedIOException | SecurityException exception) {
			String ok = dev.copperbench.shell.UiLocalePreferences.text("确定", "OK");
			dev.copperbench.shell.ProductDialogs.show(this,
					dev.copperbench.shell.UiLocalePreferences.text("无法保存语言设置，请检查应用数据目录的写入权限。",
							"Could not save the language preference. Check write access to the app data folder."),
					dev.copperbench.shell.UiLocalePreferences.text("保存失败", "Could not save"),
					JOptionPane.ERROR_MESSAGE, new Object[]{ok}, ok);
			return false;
		}
	}

	private JComponent generateEntryComponent(PreferencesEntry<?> entry, JPanel placeInside) {
		String name = L10N.t("preferences." + entry.getSectionKey() + "." + entry.getID());
		String description = L10N.t("preferences." + entry.getSectionKey() + "." + entry.getID() + ".description");
		if (description == null)
			description = "";

		JComponent label = new JLabel("<html><div style='width:300px'>" + name + (description.isEmpty() ?
				"" :
				"<br><small><font color=gray>" + description + "</font></small>") + "</div></html>");
		JPanel row = new JPanel(new BorderLayout(24, 0));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(placeInside.getComponentCount() == 0 ? 0 : 1, 0, 0, 0, lineColor()),
				BorderFactory.createEmptyBorder(9, 16, 9, 14)));
		JComponent component = entry.getComponent(parent, _ -> markChanged());
		if (component != null) {
			// Only one component may hold BorderLayout's center; the unknown-type message below replaces this label.
			row.add("Center", label);
			JPanel wrap = new JPanel(new BorderLayout()) {
				@Override public Dimension getPreferredSize() {
					Dimension retval = super.getPreferredSize();
					retval.height = Math.max(32, retval.height);
					if (!(component instanceof JCheckBox)) {
						retval.width = Math.max(retval.width, 128);
					}
					return retval;
				}
			};
			wrap.setOpaque(false);
			wrap.add("Center", component);
			row.add("East", PanelUtils.totalCenterInPanel(wrap));
		} else {
			row.add("Center", L10N.label("dialog.preferences.unknown_property_type", name));
		}
		placeInside.add(row, getConstraints());
		return component;
	}

	private GridBagConstraints getConstraints() {
		GridBagConstraints cons = new GridBagConstraints();
		cons.anchor = GridBagConstraints.NORTH;
		cons.fill = GridBagConstraints.HORIZONTAL;
		cons.gridx = 0;
		cons.weightx = 1;
		return cons;
	}

	/**
	 * @return The colour of the rules and frames that separate the areas of this dialog.
	 */
	static Color lineColor() {
		Color color = UIManager.getColor("Component.borderColor");
		return color != null ? color : Theme.current().getAltBackgroundColor();
	}

	/**
	 * Lays out one page of the dialog: its heading above a rule, then its content.
	 */
	static JComponent page(JComponent heading, JComponent content) {
		JPanel head = new JPanel(new BorderLayout());
		head.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, lineColor()),
				BorderFactory.createEmptyBorder(14, 16, 12, 16)));
		head.add("Center", heading);
		JPanel page = new JPanel(new BorderLayout());
		page.add("North", head);
		page.add("Center", content);
		return page;
	}

	/**
	 * Keeps a sheet of preference rows at the top left of its scroll pane. The sheet follows the width of the
	 * viewport up to a readable measure and scrolls sideways only when the viewport is narrower than its rows.
	 */
	private static final class SheetHolder extends JPanel implements Scrollable {

		private static final int MAX_SHEET_WIDTH = 760;

		private final JComponent sheet;

		private SheetHolder(JComponent sheet) {
			super(null);
			this.sheet = sheet;
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(16, 16, 20, 16));
			add(sheet);
		}

		@Override public Dimension getPreferredSize() {
			Insets insets = getInsets();
			Dimension size = sheet.getPreferredSize();
			return new Dimension(size.width + insets.left + insets.right, size.height + insets.top + insets.bottom);
		}

		@Override public void doLayout() {
			Insets insets = getInsets();
			Dimension size = sheet.getPreferredSize();
			int width = Math.max(size.width, Math.min(MAX_SHEET_WIDTH, getWidth() - insets.left - insets.right));
			sheet.setBounds(insets.left, insets.top, width, size.height);
		}

		@Override public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
			return 16;
		}

		@Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
			return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
		}

		@Override public boolean getScrollableTracksViewportWidth() {
			return getParent() != null && getParent().getWidth() >= getPreferredSize().width;
		}

		@Override public boolean getScrollableTracksViewportHeight() {
			return false;
		}
	}

	public void markChanged() {
		apply.setEnabled(true);
	}

}
