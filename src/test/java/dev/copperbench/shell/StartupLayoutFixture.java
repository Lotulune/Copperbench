package dev.copperbench.shell;

import net.mcreator.Launcher;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.init.*;
import net.mcreator.ui.laf.themes.ThemeManager;
import net.mcreator.ui.workspace.selector.WorkspaceSelector;
import net.mcreator.util.MCreatorVersionNumber;
import javax.swing.*;
import java.util.Properties;

/** Isolated desktop acceptance surface. Never opens or creates a real workspace. */
public final class StartupLayoutFixture {
    public static void main(String[] args) throws Exception {
        FirstRunOnboarding.initialize();
        PreferencesManager.init();
        UiLocalePreferences.save(args.length > 0 ? args[0] : "zh");
        Properties config = new Properties();
        try (var in = Launcher.class.getResourceAsStream("/mcreator.conf")) { config.load(in); }
        Launcher.version = new MCreatorVersionNumber(config);
        PluginLoader.initInstance(); ThemeManager.loadThemes(); UIRES.preloadImages(); ThemeManager.applySelectedTheme(); L10N.initTranslations();
        SwingUtilities.invokeLater(() -> {
            WorkspaceSelector frame = new WorkspaceSelector(null, (file, force) -> {});
            frame.setTitle("Copperbench Startup Acceptance " + (args.length > 0 ? args[0] : "zh"));
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            if (args.length > 1 && args[1].equals("compact")) frame.setSize(780, 540);
            frame.setVisible(true);
        });
    }
}
