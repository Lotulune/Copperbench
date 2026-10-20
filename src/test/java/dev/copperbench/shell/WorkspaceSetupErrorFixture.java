package dev.copperbench.shell;

import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.laf.themes.ThemeManager;
import net.mcreator.util.TerribleModuleHacks;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Renders the real native option-pane/button UI without changing mirror settings or running Gradle. */
public final class WorkspaceSetupErrorFixture {
    public static void main(String[] args) throws Exception {
        Path output = Files.createDirectories(Path.of(args[0]).toAbsolutePath());
        boolean dark = args.length > 1 && args[1].equals("dark");
        TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
        TerribleModuleHacks.openMCreatorRequirements();
        McreatorTestRuntime.ensureInitialized();
        PreferencesManager.PREFERENCES.hidden.uiTheme.set(dark ? "default_dark" : "default_light");
        ThemeManager.loadThemes(); ThemeManager.applySelectedTheme();
        SwingUtilities.invokeLater(() -> {
            Object[] options = {"<html>配置国内源并重试<br><small>Use China mirrors and retry",
                    "<html>重新配置工作区<br>我已经解决了问题", "<html>打开偏好设置<br>设置后重新运行",
                    "<html>复制错误到剪贴板<br>并关闭此工作区", "关闭此工作区"};
            String message = "<html><h2>工作区配置失败</h2>工作区配置失败。根据出现的建议尝试修复错误并重新配置。<br><br>"
                    + "若您在中国大陆，请选择<b>配置国内源并重试</b>，将 Gradle 和 Maven 下载切换到国内镜像。<br><br>";
            JOptionPane pane = new JOptionPane(message, JOptionPane.ERROR_MESSAGE, JOptionPane.DEFAULT_OPTION, null, options, options[0]);
            JDialog dialog = pane.createDialog(null, "Copperbench · 工作区配置失败");
            Timer capture = new Timer(1600, event -> {
                try {
                    double scale = dialog.getGraphicsConfiguration().getDefaultTransform().getScaleX();
                    var image = new BufferedImage((int)Math.ceil(dialog.getWidth()*scale), (int)Math.ceil(dialog.getHeight()*scale), BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics(); graphics.scale(scale,scale); dialog.paint(graphics); graphics.dispose();
                    Path file = output.resolve(dark ? "setup-error-dark.png" : "setup-error-light.png");
                    ImageIO.write(image,"png",file.toFile()); System.out.println("CAPTURE="+file+" scale="+scale);
                    dialog.dispose(); System.out.println("SETUP_ERROR_FIXTURE_EXIT_OK"); System.exit(0);
                } catch(Exception failure) { throw new RuntimeException(failure); }
            }); capture.setRepeats(false); capture.start();
            dialog.setVisible(true);
        });
    }
}
