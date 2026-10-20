package net.mcreator.ui.dialogs;

import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.laf.themes.ThemeManager;
import net.mcreator.util.TerribleModuleHacks;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Deterministic native progress updates for visual inspection; performs no download or workspace operation. */
public final class ProgressDialogLayoutFixture {
    public static void main(String[] args) throws Exception {
        Path output = Files.createDirectories(Path.of(args[0]).toAbsolutePath());
        TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
        TerribleModuleHacks.openMCreatorRequirements();
        McreatorTestRuntime.ensureInitialized();
        PreferencesManager.PREFERENCES.hidden.uiTheme.set("default_light");
        ThemeManager.loadThemes();
        ThemeManager.applySelectedTheme();
        SwingUtilities.invokeLater(() -> {
            ProgressDialog dialog = new ProgressDialog(null, "对选中的生成器配置工作区");
            dialog.setName("Copperbench Progress Layout Acceptance");
            var files = new ProgressDialog.ProgressUnit("正在配置 Gradle 项目文件");
            var daemon = new ProgressDialog.ProgressUnit("正在停止运行 Gradle 程序");
            var project = new ProgressDialog.ProgressUnit("正在配置 Gradle 项目");
            dialog.addProgressUnit(files); files.markStateOk();
            dialog.addProgressUnit(daemon); daemon.markStateOk(); dialog.addProgressUnit(project);
            Timer update = new Timer(500, event -> {
                project.setDetail("正在下载：fabric-api-0.114.0.jar · 175.0 KiB / 236.6 KiB"); project.setPercent(74);
            }); update.setRepeats(false); update.start();
            Timer capture = new Timer(1800, event -> {
                try {
                    double scale = dialog.getGraphicsConfiguration().getDefaultTransform().getScaleX();
                    var image = new BufferedImage((int)Math.ceil(dialog.getWidth()*scale), (int)Math.ceil(dialog.getHeight()*scale), BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics(); graphics.scale(scale,scale); dialog.paint(graphics); graphics.dispose();
                    ImageIO.write(image,"png",output.resolve("progress-"+scale+".png").toFile());
                    System.out.println("PROGRESS_CAPTURE scale="+scale+" size="+image.getWidth()+"x"+image.getHeight());
                } catch(Exception failure) { throw new RuntimeException(failure); }
            }); capture.setRepeats(false); capture.start();
            Timer close = new Timer(45000, event -> { dialog.hideDialog(); System.out.println("PROGRESS_FIXTURE_EXIT_OK"); System.exit(0); });
            close.setRepeats(false); close.start();
            dialog.setVisible(true);
        });
    }
}
