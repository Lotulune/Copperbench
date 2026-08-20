package fixtures;

import net.mcreator.plugin.JavaPlugin;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.events.ui.PreferencesDialogEvent;
import net.mcreator.plugin.events.workspace.MCreatorLoadedEvent;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.MCreatorTabs;

import javax.swing.*;

public final class SwingPlugin extends JavaPlugin {

	public SwingPlugin(Plugin plugin) {
		super(plugin);
		addListener(MCreatorLoadedEvent.class, event -> installWorkspaceExtensions(event.getMCreator()));
		addListener(PreferencesDialogEvent.SectionsLoaded.class, event ->
				event.getPreferencesDialog().addEditTemplatesPanel("fixture_c_templates", "templates/fixture-c", "json"));
	}

	private void installWorkspaceExtensions(MCreator mcreator) {
		Runnable install = () -> {
			mcreator.getTabs().addTab(new MCreatorTabs.Tab("Fixture C", createLegacyPanel(),
					"fixture-c-swing-tab", true), false);
			mcreator.getMainMenuBar().add(createLegacyMenu());
			mcreator.getToolBar().add(createLegacyToolBar().getComponent(0));
		};
		if (SwingUtilities.isEventDispatchThread())
			install.run();
		else
			try {
				SwingUtilities.invokeAndWait(install);
			} catch (Exception exception) {
				throw new IllegalStateException("Could not install Fixture C Swing extensions", exception);
			}
	}

	public JPanel createLegacyPanel() {
		JPanel panel = new JPanel();
		panel.setName("fixture-c-swing-panel");
		panel.add(new JLabel("Fixture C Swing workspace panel"));
		return panel;
	}

	public JMenu createLegacyMenu() {
		JMenu menu = new JMenu("Fixture C");
		menu.setName("fixture-c-swing-menu");
		menu.add(new JMenuItem("Fixture C action"));
		return menu;
	}

	public JToolBar createLegacyToolBar() {
		JToolBar toolBar = new JToolBar();
		toolBar.setName("fixture-c-swing-toolbar");
		JButton action = new JButton("Fixture C");
		action.setName("fixture-c-swing-action");
		toolBar.add(action);
		return toolBar;
	}
}
