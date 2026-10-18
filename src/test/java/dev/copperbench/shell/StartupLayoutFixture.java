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
        if (args.length > 2) PreferencesManager.PREFERENCES.hidden.uiTheme.set(args[2].equals("dark") ? "default_dark" : "default_light");
        UiLocalePreferences.save(args.length > 0 ? args[0] : "zh");
        Properties config = new Properties();
        try (var in = Launcher.class.getResourceAsStream("/mcreator.conf")) { config.load(in); }
        Launcher.version = new MCreatorVersionNumber(config);
        PluginLoader.initInstance(); ThemeManager.loadThemes(); UIRES.preloadImages(); ThemeManager.applySelectedTheme(); L10N.initTranslations();
        SwingUtilities.invokeLater(() -> {
            if (args.length > 2 && args[2].equals("switch-dark")) ThemeManager.synchronizeProductAppearance(true);
            if (args.length > 1 && args[1].equals("progress")) {
                var dialog = new net.mcreator.ui.dialogs.ProgressDialog(null,
                        UiLocalePreferences.text("Copperbench 工作区准备", "Copperbench workspace preparation"));
                dialog.setTitle("Copperbench Native Check — core progress / " + (args.length > 0 ? args[0] : "zh"));
                dialog.setClosable(true); dialog.setModal(false);
                var copy = new net.mcreator.ui.dialogs.ProgressDialog.ProgressUnit(UiLocalePreferences.text("正在准备项目文件", "Preparing project files"));
                copy.setDetail("C:/Copperbench/" + "very-long-workspace-directory/".repeat(8) + "generated-resources.json");
                dialog.addProgressUnit(copy); copy.setPercent(42);
                var done = new net.mcreator.ui.dialogs.ProgressDialog.ProgressUnit(UiLocalePreferences.text("资源索引已完成", "Resource indexing completed"));
                dialog.addProgressUnit(done); done.markStateOk();
                var warning = new net.mcreator.ui.dialogs.ProgressDialog.ProgressUnit(UiLocalePreferences.text("需要检查资源引用", "Check resource references"));
                warning.setDetail(UiLocalePreferences.text("发现缺失的纹理引用。请在资源管理器中检查对应文件后重试。", "A referenced texture is missing. Review the resource file and retry."));
                dialog.addProgressUnit(warning); warning.markStateWarning();
                dialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosed(java.awt.event.WindowEvent event) { System.exit(0); } });
                dialog.setVisible(true); return;
            }
            if (args.length > 1 && args[1].equals("splash")) {
                net.mcreator.ui.SplashScreen splash = new net.mcreator.ui.SplashScreen();
                splash.setProgress(25, UiLocalePreferences.text("正在加载界面组件", "Loading interface components"));
                Timer close = new Timer(700, event -> {
                    try {
                        var image = new java.awt.image.BufferedImage(splash.getWidth(), splash.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        var graphics = image.createGraphics(); splash.paint(graphics); graphics.dispose();
                        javax.imageio.ImageIO.write(image, "png", net.mcreator.io.UserFolderManager.getFileFromUserFolder("splash-render.png"));
                        splash.dispose(); System.exit(0);
                    } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                });
                close.setRepeats(false); close.start();
                return;
            }
            WorkspaceSelector frame = new WorkspaceSelector(null, (file, force) -> {});
            frame.setTitle("Copperbench Startup Acceptance " + (args.length > 0 ? args[0] : "zh"));
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            if (args.length > 1 && args[1].equals("compact")) frame.setSize(780, 540);
            frame.setVisible(true);
        });
    }
}
