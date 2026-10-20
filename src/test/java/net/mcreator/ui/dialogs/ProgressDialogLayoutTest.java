package net.mcreator.ui.dialogs;

import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.ui.laf.themes.ThemeManager;
import net.mcreator.util.TerribleModuleHacks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ProgressDialogLayoutTest {
    @BeforeAll static void prepare() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Native dialog layout requires a display");
        TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
        TerribleModuleHacks.openMCreatorRequirements();
        McreatorTestRuntime.ensureInitialized();
        ThemeManager.applySelectedTheme();
    }

    @Test void addingAndRemovingDownloadDetailsRemeasuresRowsAlreadyInTheList() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ProgressDialog dialog = new ProgressDialog(null, "配置工作区");
            try {
                var download = new ProgressDialog.ProgressUnit("正在配置 Gradle 项目");
                var next = new ProgressDialog.ProgressUnit("生成基础文件");
                dialog.addProgressUnit(download); dialog.addProgressUnit(next);
                JList<?> list = child(dialog.getContentPane(), JList.class);
                list.setSize(500, 280);
                Rectangle original = list.getCellBounds(0, 0); // Prime BasicListUI's height cache.
                download.setDetail("正在下载：fabric-api-0.114.0.jar · 175.0 KiB / 236.6 KiB");
                download.setPercent(74);
                Rectangle expanded = list.getCellBounds(0, 0);
                assertTrue(expanded.height > original.height, "A late detail line must expand the cached row");
                assertTrue(list.getCellBounds(1, 1).y >= expanded.y + expanded.height, "The next row must move below the details");
                assertRowFits(list, 0, download);
                download.setDetail(null); download.markStateOk();
                Rectangle collapsed = list.getCellBounds(0, 0);
                assertTrue(collapsed.height < expanded.height, "Removing detail must release its extra height");
                assertRowFits(list, 0, download);
            } finally { dialog.dispose(); }
        });
    }

    @Test void workerThreadUpdatesAndLargerFontsKeepTheFullDetailAndPercentageVisible() throws Exception {
        AtomicReference<ProgressDialog> dialog = new AtomicReference<>();
        AtomicReference<ProgressDialog.ProgressUnit> unit = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            dialog.set(new ProgressDialog(null, "Downloading workspace dependencies"));
            unit.set(new ProgressDialog.ProgressUnit("Configuring the Gradle project"));
            dialog.get().addProgressUnit(unit.get());
            child(dialog.get().getContentPane(), JList.class).setSize(500, 280);
            child(dialog.get().getContentPane(), JList.class).getCellBounds(0, 0);
        });
        try {
            // Updates originate outside the EDT in the real Gradle callback.
            unit.get().setDetail("Downloading: fabric-api.jar - 175.0 KiB / 236.6 KiB");
            unit.get().setPercent(74);
            SwingUtilities.invokeAndWait(() -> {
                JList<?> list = child(dialog.get().getContentPane(), JList.class);
                Object oldLabel = UIManager.get("Label.font"), oldProgress = UIManager.get("ProgressBar.font");
                try {
                    Font base = list.getFont();
                    for (float factor : new float[]{1f, 1.25f, 2f}) {
                        UIManager.put("Label.font", base.deriveFont(base.getSize2D() * factor));
                        UIManager.put("ProgressBar.font", base.deriveFont(base.getSize2D() * factor));
                        unit.get().setPercent(74);
                        assertRowFits(list, 0, unit.get());
                    }
                } finally { UIManager.put("Label.font", oldLabel); UIManager.put("ProgressBar.font", oldProgress); }
            });
        } finally { SwingUtilities.invokeAndWait(() -> dialog.get().dispose()); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertRowFits(JList list, int index, ProgressDialog.ProgressUnit unit) {
        Rectangle cell = list.getCellBounds(index, index);
        Component renderer = list.getCellRenderer().getListCellRendererComponent(list, unit, index, false, false);
        renderer.setBounds(0, 0, Math.max(500, list.getPreferredSize().width), cell.height);
        layout(renderer);
        for (JLabel label : children(renderer, JLabel.class)) {
            if (label.getText() == null || label.getText().isBlank()) continue;
            Rectangle bounds = SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), renderer);
            assertTrue(bounds.y >= 0 && bounds.y + bounds.height <= cell.height, label.getText());
            assertTrue(label.getHeight() >= label.getPreferredSize().height, "Text must not be vertically clipped: " + label.getText());
        }
        for (javax.swing.text.JTextComponent text : children(renderer, javax.swing.text.JTextComponent.class)) {
            if (text.getText().isBlank()) continue;
            Rectangle bounds = SwingUtilities.convertRectangle(text.getParent(), text.getBounds(), renderer);
            assertTrue(bounds.y >= 0 && bounds.y + bounds.height <= cell.height, text.getText());
            assertTrue(text.getHeight() >= text.getPreferredSize().height, "Wrapped text must not be vertically clipped: " + text.getText());
        }
        for (JProgressBar bar : children(renderer, JProgressBar.class)) {
            assertTrue(bar.getHeight() >= bar.getFontMetrics(bar.getFont()).getHeight(), "The percentage must fit at larger font sizes");
        }
    }

    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout(); for (Component child : container.getComponents()) layout(child);
        }
    }
    private static <T extends Component> List<T> children(Component component, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(component)) found.add(type.cast(component));
        if (component instanceof Container container) for (Component child : container.getComponents()) found.addAll(children(child, type));
        return found;
    }
    private static <T extends Component> T child(Component component, Class<T> type) { return children(component, type).getFirst(); }
}
