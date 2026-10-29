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

package net.mcreator.ui.workspace.selector;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import dev.copperbench.ProductIdentity;
import dev.copperbench.shell.ProductTheme;
import static dev.copperbench.shell.UiLocalePreferences.text;
import net.mcreator.Launcher;
import net.mcreator.io.FileIO;
import net.mcreator.io.OS;
import net.mcreator.io.UserFolderManager;
import net.mcreator.io.net.WebIO;
import net.mcreator.plugin.MCREvent;
import net.mcreator.plugin.events.WorkspaceSelectorLoadedEvent;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.MCreatorApplication;
import net.mcreator.ui.SplashScreen;
import net.mcreator.ui.action.impl.AboutAction;
import net.mcreator.ui.component.ImagePanel;
import net.mcreator.ui.component.JEmptyBox;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.ui.dialogs.preferences.PreferencesDialog;
import net.mcreator.ui.dialogs.workspace.NewWorkspaceDialog;
import net.mcreator.ui.init.AppIcon;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.ui.notifications.INotificationConsumer;
import net.mcreator.ui.notifications.NotificationsRenderer;
import net.mcreator.util.DesktopUtils;
import net.mcreator.util.ListUtils;
import net.mcreator.util.StringUtils;
import net.mcreator.util.image.EmptyIcon;
import net.mcreator.util.image.ImageUtils;
import net.mcreator.workspace.ShareableZIPManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class WorkspaceSelector extends JFrame implements DropTargetListener, INotificationConsumer {

	private static final Logger LOG = LogManager.getLogger("Workspace Selector");

	private final CardLayout recentPanes = new CardLayout();
	private final JPanel recentPanel = new JPanel(recentPanes);
	private final WorkspaceOpenListener workspaceOpenListener;
	private RecentWorkspaces recentWorkspaces = new RecentWorkspaces();

	@Nullable private final MCreatorApplication application;

	private final JButton newWorkspace;

	private final JPanel subactions = new JPanel(new GridLayout(-1, 1, 0, 8));

	private final NotificationsRenderer notificationsRenderer;

	private final DefaultListModel<RecentWorkspaceEntry> defaultListModel = new DefaultListModel<>();
	private final JList<RecentWorkspaceEntry> recentsList = new JList<>(defaultListModel) {
		@Override public boolean getScrollableTracksViewportWidth() { return true; }
	};
	private final JPopupMenu recentListPopupMenu;

	public WorkspaceSelector(@Nullable MCreatorApplication application, WorkspaceOpenListener workspaceOpenListener) {
		this.workspaceOpenListener = workspaceOpenListener;
		this.application = application;

		setTitle(ProductIdentity.NAME + " " + ProductIdentity.VERSION);
		setIconImages(AppIcon.getAppIcons());
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

		if (application != null)
			addWindowListener(new WindowAdapter() {
				@Override public void windowClosing(WindowEvent arg0) {
					application.closeApplication();
				}
			});

		JPanel actions = new JPanel(new BorderLayout(0, 6));

		newWorkspace = mainWorkspaceButton(L10N.t("dialog.workspace_selector.new_workspace"), UIRES.get("wrk_add"),
				_ -> {
					NewWorkspaceDialog newWorkspaceDialog = new NewWorkspaceDialog(this);
					if (newWorkspaceDialog.getWorkspaceFile() != null)
						workspaceOpenListener.workspaceOpened(newWorkspaceDialog.getWorkspaceFile());
				});

		actions.add("North", newWorkspace);
		actions.add("Center", subactions);

		addWorkspaceButton(L10N.t("dialog.workspace_selector.open_workspace"), UIRES.get("opnwrk"), _ -> {
			File workspaceFile = FileDialogs.getOpenDialog(this, new String[] { ".mcreator" });
			if (workspaceFile != null && workspaceFile.getParentFile().isDirectory())
				workspaceOpenListener.workspaceOpened(workspaceFile);
		});
		addWorkspaceButton(dev.copperbench.shell.UiLocalePreferences.text("连接外部 Agent", "Connect external Agent"), UIRES.get("info"), _ ->
				dev.copperbench.shell.ProductDialogs.show(this, dev.copperbench.shell.UiLocalePreferences.text(
						"先新建或打开工作区，再进入「工具 → AI 与 MCP」。在那里查看连接地址、配对外部 Agent，并选择已有权限。",
						"Create or open a workspace first, then open Tools → AI & MCP. View the connection address, pair your external Agent and choose permissions there."),
						dev.copperbench.shell.UiLocalePreferences.text("连接外部 Agent", "Connect external Agent"), JOptionPane.INFORMATION_MESSAGE,
						new Object[] { UIManager.getString("OptionPane.okButtonText") }, UIManager.getString("OptionPane.okButtonText")));

		addWorkspaceButton(L10N.t("dialog.workspace_selector.import"), UIRES.get("impfile"), _ -> {
			File file = FileDialogs.getOpenDialog(this, new String[] { ".zip" });
			if (file != null) {
				importWorkspaceFromZip(file);
			}
		});

		JPanel logoPanel = new JPanel(new BorderLayout(20, 0));
		JPanel introduction = new JPanel(new GridLayout(0, 1, 0, 8));
		introduction.setOpaque(false);
		JLabel logo = new JLabel("Copperbench");
		logo.setFont(logo.getFont().deriveFont(Font.BOLD, 30f));
		introduction.add(logo);
		JTextArea subtitle = dev.copperbench.shell.ProductDialogs.body(text("让下一个想法，成为你的 Minecraft 作品。", "Make your next Minecraft creation."));
		subtitle.setRows(2);
		subtitle.setBorder(BorderFactory.createEmptyBorder());
		subtitle.setFocusable(false);
		subtitle.setFont(subtitle.getFont().deriveFont(13f));
		subtitle.setForeground(ProductTheme.muted());
		introduction.add(subtitle);
		logoPanel.add(introduction, BorderLayout.CENTER);
		logoPanel.add(new JLabel(AppIcon.getAppIcon(80, 80)), BorderLayout.EAST);
		logoPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		JButton help = new JButton(text("帮助与关于", "Help & about"), ProductTheme.monochrome(UIRES.get("info")));
		help.setName("help-and-about");
		help.setMargin(new Insets(9, 12, 9, 12));
		help.addActionListener(_ -> AboutAction.showDialog(this));
		footer.add(help);
		JButton prefs = new JButton(L10N.t("dialog.workspace_selector.preferences"), ProductTheme.monochrome(UIRES.get("settings")));
		prefs.setMargin(new Insets(9, 12, 9, 12));
		prefs.addActionListener(_ -> new PreferencesDialog(WorkspaceSelector.this));
		footer.add(prefs);
		footer.setBorder(BorderFactory.createEmptyBorder(8, 24, 16, 24));

		JPanel alignedModelingSetup = new JPanel(new BorderLayout());
		alignedModelingSetup.setOpaque(false);
		alignedModelingSetup.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor")),
				BorderFactory.createEmptyBorder(16, 0, 0, 0)));
		alignedModelingSetup.add(new dev.copperbench.shell.BlockbenchStartupPanel(
				dev.copperbench.assets.BlockbenchConfiguration.productDefault(),
				() -> dev.copperbench.shell.BlockbenchStartupPanel.showGuide(this)), BorderLayout.CENTER);
		JPanel start = new JPanel(new BorderLayout(0, 16));
		start.add(logoPanel, BorderLayout.NORTH);
		start.add(actions, BorderLayout.CENTER);
		start.add(alignedModelingSetup, BorderLayout.SOUTH);
		start.setBorder(BorderFactory.createEmptyBorder(24, 32, 16, 32));
		JPanel centered = new WelcomeViewport();
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		centered.add(start, constraints);
		JScrollPane contentScroll = new JScrollPane(centered);
		contentScroll.setBorder(BorderFactory.createEmptyBorder());
		contentScroll.getVerticalScrollBar().setUnitIncrement(16);
		contentScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		JComponent centerComponent = PanelUtils.centerAndSouthElement(contentScroll, footer);
		notificationsRenderer = new NotificationsRenderer(centerComponent);
		add("Center", centerComponent);

		recentPanel.setBackground(UIManager.getColor("Button.background"));
		recentPanel.setPreferredSize(new Dimension(246, 10));

		JLabel norecentsloaded = L10N.label("dialog.workspace_selector.no_workspaces_loaded");
		norecentsloaded.setForeground(ProductTheme.muted());
		JLabel norecents = L10N.label("dialog.workspace_selector.no_workspaces");
		norecents.setForeground(ProductTheme.muted());

		recentsList.setComponentPopupMenu(recentListPopupMenu = buildRightClickMenu());

		recentsList.setBackground(UIManager.getColor("Button.background"));
		recentsList.setFixedCellHeight(86);
		recentsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		recentsList.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent mouseEvent) {
				if (mouseEvent.getButton() == MouseEvent.BUTTON2) {
					int idx = recentsList.locationToIndex(mouseEvent.getPoint());
					if (idx < 0 || !recentsList.getCellBounds(idx, idx).contains(mouseEvent.getPoint())) return;
					removeRecentWorkspace(defaultListModel.elementAt(idx));
					reloadRecents();
				} else if (mouseEvent.getClickCount() == 2 && recentsList.getSelectedValue() != null) {
					workspaceOpenListener.workspaceOpened(recentsList.getSelectedValue().getPath());
				}
			}
		});
		recentsList.addKeyListener(new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) {
				if (recentsList.getSelectedValue() == null) return;
				if (e.getKeyCode() == KeyEvent.VK_DELETE) {
					Object[] options = { L10N.t("dialog.workspace_selector.delete_workspace.recent_list"),
							L10N.t("dialog.workspace_selector.delete_workspace.workspace"), L10N.t("common.cancel") };
					int n = JOptionPane.showOptionDialog(WorkspaceSelector.this,
							L10N.t("dialog.workspace_selector.delete_workspace.message",
									recentsList.getSelectedValue().getName()),
							L10N.t("dialog.workspace_selector.delete_workspace.title"),
							JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

					if (n == 0) {
						removeRecentWorkspace(recentsList.getSelectedValue());
						reloadRecents();
					} else if (n == 1) {
						int m = JOptionPane.showConfirmDialog(WorkspaceSelector.this,
								L10N.t("dialog.workspace_selector.delete_workspace.confirmation",
										recentsList.getSelectedValue().getName()), L10N.t("common.confirmation"),
								JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
						if (m == JOptionPane.YES_OPTION) {
							FileIO.moveToTrash(recentsList.getSelectedValue().getPath().getParentFile());
							reloadRecents();
						}
					}
				} else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					workspaceOpenListener.workspaceOpened(recentsList.getSelectedValue().getPath());
				}
			}
		});
		recentsList.setCellRenderer(new RecentWorkspacesRenderer());
		JScrollPane recentsScrollPane = new JScrollPane(recentsList);
		recentsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		recentsScrollPane.setBorder(BorderFactory.createEmptyBorder());
		recentsList.setBorder(BorderFactory.createEmptyBorder(0, 6, 12, 6));
		recentPanel.add("recents", recentsScrollPane);
		recentPanel.add("norecentsloaded", PanelUtils.totalCenterInPanel(norecentsloaded));
		recentPanel.add("norecents", PanelUtils.totalCenterInPanel(norecents));
		JPanel sidebar = new JPanel(new BorderLayout(0, 16));
		sidebar.setBackground(UIManager.getColor("Button.background"));
		sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIManager.getColor("Component.borderColor")));
		JLabel recentHeading = new JLabel(text("最近工作区", "Recent workspaces"));
		recentHeading.setFont(recentHeading.getFont().deriveFont(Font.BOLD, 13f));
		recentHeading.setBorder(BorderFactory.createEmptyBorder(28, 20, 0, 12));
		sidebar.add(recentHeading, BorderLayout.NORTH);
		sidebar.add(recentPanel, BorderLayout.CENTER);
		JPanel sidebarFooter = new JPanel(new BorderLayout(0, 12));
		sidebarFooter.setOpaque(false);
		sidebarFooter.setBorder(BorderFactory.createEmptyBorder(0, 16, 22, 16));
		sidebarFooter.add(new dev.copperbench.shell.ProductUpdateButton(), BorderLayout.NORTH);
		JLabel version = new JLabel("Copperbench " + ProductIdentity.VERSION);
		version.setForeground(ProductTheme.muted());
		version.setFont(version.getFont().deriveFont(11f));
		sidebarFooter.add(version, BorderLayout.SOUTH);
		sidebar.add(sidebarFooter, BorderLayout.SOUTH);
		add("West", sidebar);

		new DropTarget(this, DnDConstants.ACTION_MOVE, this, true, null);

		MCREvent.event(new WorkspaceSelectorLoadedEvent(this));

		addWindowListener(new WindowAdapter() {
			@Override public void windowActivated(WindowEvent e) {
				super.windowActivated(e);
				reloadRecents();
				if (getGlassPane() instanceof dev.copperbench.shell.StartupTour tour && tour.isVisible()) tour.focusNext();
				else newWorkspace.requestFocusInWindow();
			}
		});

		Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		setMinimumSize(new Dimension(Math.min(760, screen.width), Math.min(520, screen.height)));
		setSize(Math.min(960, screen.width), Math.min(660, screen.height));
		setResizable(true);
		setLocationRelativeTo(null);

		if (OS.getOS() == OS.MAC && SystemInfo.isMacFullWindowContentSupported) {
			getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
			getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
			getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
			recentPanel.setBorder(BorderFactory.createEmptyBorder(22, 0, 0, 0));
			centerComponent.setBorder(BorderFactory.createEmptyBorder(22, 0, 0, 0));
		}
		addWindowListener(new WindowAdapter() {
			@Override public void windowOpened(WindowEvent event) {
				dev.copperbench.shell.ProductUpdateNotifications.INSTANCE.start();
				if (dev.copperbench.shell.FirstRunOnboarding.claim())
					dev.copperbench.shell.StartupTour.show(WorkspaceSelector.this, newWorkspace, () -> {
						NewWorkspaceDialog dialog = new NewWorkspaceDialog(WorkspaceSelector.this, true);
						if (dialog.getWorkspaceFile() != null) workspaceOpenListener.workspaceOpened(dialog.getWorkspaceFile());
					});
			}
		});
	}

	private void importWorkspaceFromZip(File file) {
		File workspaceDir = FileDialogs.getWorkspaceDirectorySelectDialog(this, null);
		if (workspaceDir != null) {
			ShareableZIPManager.ImportResult workspaceFile = ShareableZIPManager.importZIP(file, workspaceDir, this);
			if (workspaceFile != null)
				workspaceOpenListener.workspaceOpened(workspaceFile.file(), workspaceFile.regenerateRequired());
		}
	}

	public JPopupMenu getRecentListPopupMenu() {
		return recentListPopupMenu;
	}

	private JPopupMenu buildRightClickMenu() {
		JPopupMenu recentListMenu = new JPopupMenu();

		JMenuItem openSelectedWorkspace = new JMenuItem(L10N.t("dialog.workspace_selector.open_workspace_selected"));
		openSelectedWorkspace.addActionListener(_ -> {
			if (recentsList.getSelectedValue() != null) {
				workspaceOpenListener.workspaceOpened(recentsList.getSelectedValue().getPath());
			}
		});
		recentListMenu.add(openSelectedWorkspace);

		recentListMenu.addSeparator();

		JMenuItem deleteFromRecentList = new JMenuItem(
				L10N.t("dialog.workspace_selector.delete_workspace.recent_list"));
		deleteFromRecentList.addActionListener(_ -> {
			if (recentsList.getSelectedValue() != null) {
				removeRecentWorkspace(recentsList.getSelectedValue());
				reloadRecents();
			}
		});
		recentListMenu.add(deleteFromRecentList);

		JMenuItem deleteFromFolder = new JMenuItem(L10N.t("dialog.workspace_selector.delete_workspace.workspace"));
		deleteFromFolder.addActionListener(_ -> {
			if (recentsList.getSelectedValue() != null) {
				int m = JOptionPane.showConfirmDialog(WorkspaceSelector.this,
						L10N.t("dialog.workspace_selector.delete_workspace.confirmation",
								recentsList.getSelectedValue().getName()), L10N.t("common.confirmation"),
						JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				if (m == 0) {
					FileIO.moveToTrash(recentsList.getSelectedValue().getPath().getParentFile());
					reloadRecents();
				}
			}
		});
		recentListMenu.add(deleteFromFolder);

		recentListMenu.addSeparator();

		JMenuItem openInSystemExplorer = new JMenuItem(L10N.t("action.open_workspace_folder"));
		openInSystemExplorer.addActionListener(_ -> {
			if (recentsList.getSelectedValue() != null) {
				DesktopUtils.openSafe(recentsList.getSelectedValue().getPath().getParentFile());
			}
		});
		recentListMenu.add(openInSystemExplorer);

		recentListMenu.addPopupMenuListener(new PopupMenuListener() {
			@Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				recentsList.setSelectedIndex(recentsList.locationToIndex(recentsList.getMousePosition()));
			}

			@Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {

			}

			@Override public void popupMenuCanceled(PopupMenuEvent e) {

			}
		});

		return recentListMenu;
	}

	@Override public void dragEnter(DropTargetDragEvent dtde) {
		processDrag(dtde);
	}

	@Override public void dragOver(DropTargetDragEvent dtde) {
		processDrag(dtde);
	}

	@Override public void dropActionChanged(DropTargetDragEvent dtde) {
	}

	@Override public void dragExit(DropTargetEvent dtde) {
	}

	@Override public void drop(DropTargetDropEvent dtde) {
		Transferable transferable = dtde.getTransferable();
		if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
			dtde.acceptDrop(dtde.getDropAction());
			try {
				List<?> transferData = (List<?>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
				if (!transferData.isEmpty()) {
					Object transfObj = transferData.getFirst();
					if (transfObj instanceof File workspaceFile) {
						if (workspaceFile.getName().endsWith(".mcreator")) {
							workspaceOpenListener.workspaceOpened(workspaceFile);
						} else if (workspaceFile.getName().endsWith(".zip")) {
							importWorkspaceFromZip(workspaceFile);
						} else {
							Toolkit.getDefaultToolkit().beep();
						}
					}
				}
			} catch (Exception ex) {
				LOG.error("Drag and drop failed", ex);
			}
		} else {
			dtde.rejectDrop();
		}
	}

	private void processDrag(DropTargetDragEvent dtde) {
		if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
			dtde.acceptDrag(DnDConstants.ACTION_MOVE);
		} else {
			dtde.rejectDrag();
		}
	}

	public void addOrUpdateRecentWorkspace(RecentWorkspaceEntry recentWorkspaceEntry) {
		if (!recentWorkspaces.getList().contains(recentWorkspaceEntry))
			recentWorkspaces.getList().add(recentWorkspaceEntry);
		else
			recentWorkspaces.getList().get(recentWorkspaces.getList().indexOf(recentWorkspaceEntry))
					.update(recentWorkspaceEntry);

		ListUtils.rearrange(recentWorkspaces.getList(), recentWorkspaceEntry);
		saveRecentWorkspaces();
	}

	private void removeRecentWorkspace(RecentWorkspaceEntry recentWorkspace) {
		recentWorkspaces.getList().remove(recentWorkspace);
		saveRecentWorkspaces();
	}

	private static final Gson gson = new GsonBuilder().setPrettyPrinting().setStrictness(Strictness.LENIENT).create();

	private void saveRecentWorkspaces() {
		String serialized = gson.toJson(recentWorkspaces);
		if (serialized != null && !serialized.isEmpty()) {
			FileIO.writeStringToFile(serialized, UserFolderManager.getFileFromUserFolder("recentworkspaces"));
		}
	}

	private synchronized void reloadRecents() {
		if (UserFolderManager.getFileFromUserFolder("recentworkspaces").isFile()) {
			try {
				recentWorkspaces = gson.fromJson(
						FileIO.readFileToString(UserFolderManager.getFileFromUserFolder("recentworkspaces")),
						RecentWorkspaces.class);
				if (recentWorkspaces != null) {
					List<RecentWorkspaceEntry> recentWorkspacesFiltered = new ArrayList<>();
					for (RecentWorkspaceEntry recentWorkspaceEntry : recentWorkspaces.getList())
						if (recentWorkspaceEntry.getPath().isFile())
							recentWorkspacesFiltered.add(recentWorkspaceEntry);
					recentWorkspaces = new RecentWorkspaces(recentWorkspacesFiltered);
				}
			} catch (Exception e) {
				recentWorkspaces = null;
				LOG.warn("Failed to load recent workspaces", e);
			}
		}

		if (recentWorkspaces != null && !recentWorkspaces.getList().isEmpty()) {
			defaultListModel.removeAllElements();
			recentWorkspaces.getList().forEach(defaultListModel::addElement);
			recentPanes.show(recentPanel, "recents");
		} else if (recentWorkspaces == null) {
			recentPanes.show(recentPanel, "norecentsloaded");
		} else {
			recentPanes.show(recentPanel, "norecents");
		}

		recentPanel.revalidate();
	}

	private JButton mainWorkspaceButton(String text, ImageIcon icon, ActionListener event) {
		JButton button = new JButton(text);
		button.setName("new-workspace");
		button.setFont(button.getFont().deriveFont(Font.BOLD, 15f));
		button.setBackground(ProductTheme.accent());
		button.setForeground(new Color(ProductTheme.dark() ? 0x241a12 : 0xffffff));
		button.setMargin(new Insets(14, 18, 14, 18));
		button.setIcon(ProductTheme.monochrome(new ImageIcon(ImageUtils.resizeAA(icon.getImage(), 28, 28))));
		button.setIconTextGap(16);
		button.addActionListener(event);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return button;
	}

	private static final class WelcomeViewport extends JPanel implements Scrollable {
		WelcomeViewport() { super(new GridBagLayout()); }
		@Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		@Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
		@Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(16, visible.height - 32); }
		@Override public boolean getScrollableTracksViewportWidth() { return true; }
		@Override public boolean getScrollableTracksViewportHeight() { return getParent() != null && getParent().getHeight() > getPreferredSize().height; }
	}

	/** Keeps plugin-provided quick-start actions in the same keyboard-accessible list. */
	public void addWorkspaceButton(String text, ImageIcon icon, ActionListener event) {
		JButton button = new JButton(text);
		button.setFont(button.getFont().deriveFont(13f));
		button.setMargin(new Insets(8, 18, 8, 18));
		button.setIcon(ProductTheme.monochrome(icon));
		button.setIconTextGap(18);
		button.addActionListener(event);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		subactions.add(button);
	}

	private void initWebsitePanel() {
		CompletableFuture<String[]> newsFuture = new CompletableFuture<>();
		MCreatorApplication.WEB_API.getWebsiteNews(newsFuture);
		JLabel nov = new JLabel("<html><font style=\"font-size: 9px;\">" + L10N.t("dialog.workspace_selector.news")
				+ "<br></font><font style=\"font-size: 15px; color: #f5f5f5;\">" + L10N.t(
				"dialog.workspace_selector.webdata.loading"));
		nov.setCursor(new Cursor(Cursor.HAND_CURSOR));
		nov.setForeground(new Color(0xf5f5f5));
		newsFuture.whenComplete((news, _) -> {
			SwingUtilities.invokeLater(() -> {
				if (news != null) {
					nov.setText("<html><font style=\"font-size: 9px;\">" + L10N.t("dialog.workspace_selector.news")
							+ "<br></font><font style=\"font-size: 15px; color: #f5f5f5;\">"
							+ StringUtils.abbreviateString(news[0], 39));
					nov.addMouseListener(new MouseAdapter() {
						@Override public void mouseClicked(MouseEvent en) {
							DesktopUtils.browseSafe(news[1]);
						}
					});
				} else {
					nov.setText("");
				}
			});

			if (news != null && PreferencesManager.PREFERENCES.notifications.showWebsiteNewsNotifications.get()) {
				String id = news[4];

				// Do not show notification the first time
				if (PreferencesManager.PREFERENCES.hidden.lastWebsiteNewsRead.get().isBlank())
					PreferencesManager.PREFERENCES.hidden.lastWebsiteNewsRead.set(id);

				if (!PreferencesManager.PREFERENCES.hidden.lastWebsiteNewsRead.get().equals(id)) {
					ImageIcon newsIcon;
					if (news[3] != null && !news[3].isBlank()) {
						newsIcon = WebIO.getIconFromURL(MCreatorApplication.SERVER_DOMAIN + news[3], 3 * 60, 60, null);
					} else {
						newsIcon = null;
					}

					SwingUtilities.invokeLater(() -> {
						String title = L10N.t("notification.news.title", news[0]);
						String link = news[1];
						String description = StringUtils.justifyText(StringUtils.abbreviateString(news[2], 300), 50,
								"<br>");
						addNotification(title, newsIcon, description,
								new NotificationsRenderer.ActionButton(L10N.t("notification.news.read_more"), _ -> {
									DesktopUtils.browseSafe(link);
									PreferencesManager.PREFERENCES.hidden.lastWebsiteNewsRead.set(id);
								}), new NotificationsRenderer.ActionButton(L10N.t("notification.news.hide"),
										_ -> PreferencesManager.PREFERENCES.hidden.lastWebsiteNewsRead.set(id)));

					});
				}
			}
		});

		CompletableFuture<String[]> motwFuture = new CompletableFuture<>();
		MCreatorApplication.WEB_API.getModOfTheWeekData(motwFuture);
		JLabel lab3 = new JLabel("<html><font style=\"font-size: 9px;\">" + L10N.t("dialog.workspace_selector.motw")
				+ "<br></font><font style=\"font-size: 15px; color: #f5f5f5;\">" + L10N.t(
				"dialog.workspace_selector.webdata.loading"));
		lab3.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
		lab3.setForeground(new Color(0xf5f5f5));
		JLabel lab2 = new JLabel();
		lab2.setIcon(new EmptyIcon(48, 48));
		JComponent motwpan = PanelUtils.westAndEastElement(lab3, lab2);
		motwpan.setCursor(new Cursor(Cursor.HAND_CURSOR));
		motwFuture.whenComplete((motw, _) -> {
			ImageIcon imageIcon;
			if (motw != null)
				imageIcon = WebIO.getIconFromURL(motw[4], 48, 48, null, true);
			else {
				imageIcon = null;
			}

			SwingUtilities.invokeLater(() -> {
				motwpan.addMouseListener(new MouseAdapter() {
					@Override public void mouseClicked(MouseEvent arg0) {
						if (motw != null)
							DesktopUtils.browseSafe(motw[1]);
					}
				});
				if (motw != null) {
					lab3.setText("<html><font style=\"font-size: 9px;\">" + L10N.t("dialog.workspace_selector.motw")
							+ "<br></font><font style=\"font-size: 15px; color: #f5f5f5;\">"
							+ StringUtils.abbreviateString(motw[0], 30) + "&nbsp;&nbsp;&nbsp;&nbsp;");
					lab2.setIcon(imageIcon);
				} else {
					lab3.setText("");
				}
			});
		});

		JComponent south = PanelUtils.westAndEastElement(nov, motwpan, 20, 20);
		south.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

		JPanel soim;
		if (!Launcher.version.isSnapshot()) {
			soim = new ImagePanel(SplashScreen.getSplashImage(true));
			((ImagePanel) soim).setFitToWidth(true);
			((ImagePanel) soim).setOffsetY(-365);
		} else {
			soim = new JPanel();
			soim.setBackground(Theme.current().getSecondAltBackgroundColor());
		}

		soim.setLayout(new BorderLayout());
		soim.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.current().getAltBackgroundColor()));
		soim.add(south);

		add("South", soim);
	}

	@Nonnull public RecentWorkspaces getRecentWorkspaces() {
		if (recentWorkspaces == null)
			this.recentWorkspaces = new RecentWorkspaces();

		return recentWorkspaces;
	}

	@Nullable public MCreatorApplication getApplication() {
		return application;
	}

	@Override public NotificationsRenderer getNotificationsRenderer() {
		return notificationsRenderer;
	}

}
