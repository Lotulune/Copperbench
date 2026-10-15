package dev.copperbench.headless;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Test-only UI decisions; this class never issues a product authorization. */
@EnabledIfEnvironmentVariable(named = "COPPERBENCH_TEST_APPROVAL_WINDOW", matches = "true")
class LocalApprovalWindowTest {
    @Test @Timeout(20) void cancelIsDefaultAndReturnsFalse() throws Exception { exercise("Cancel", false); }
    @Test @Timeout(20) void closingTheWindowReturnsFalse() throws Exception { exercise(null, false); }
    @Test @Timeout(20) void explicitAllowReturnsTrue() throws Exception { exercise("Allow", true); }
    @Test @Timeout(20) void chineseCancelIsDefault() throws Exception { exercise("zh", "取消", false); }
    @Test @Timeout(20) void chineseAllowReturnsTrue() throws Exception { exercise("zh", "允许", true); }

    private static void exercise(String choice, boolean expected) throws Exception {
        exercise("en", choice, expected);
    }

    private static void exercise(String locale, String choice, boolean expected) throws Exception {
        String title = "Approval window test " + UUID.randomUUID();
        String message = TaskAuthorizationCopy.body(locale, "Test fixture only", Path.of("approval-window-fixture").toAbsolutePath(),
                List.of("create", "edit", "build", "test", "run_client", "run_server", "restore"), 7200);
        var result = CompletableFuture.supplyAsync(() -> LocalApprovalWindow.confirm(title, message, locale));
        JFrame frame = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (frame == null && System.nanoTime() < deadline) {
            JFrame[] found = new JFrame[1];
            SwingUtilities.invokeAndWait(() -> {
                for (Frame candidate : Frame.getFrames())
                    if (candidate instanceof JFrame value && title.equals(value.getTitle()) && value.isShowing()) found[0] = value;
            });
            frame = found[0];
            if (frame == null) Thread.sleep(25);
        }
        assertNotNull(frame, "The CLI approval must expose a visible top-level frame");
        JFrame visible = frame;
        try {
            SwingUtilities.invokeAndWait(() -> {
                assertNull(visible.getOwner(), "Approval must not depend on a hidden shared owner");
                assertTrue(visible.isDisplayable());
                var pane = (JOptionPane) visible.getContentPane();
                assertEquals(TaskAuthorizationCopy.cancel(locale), pane.getInitialValue());
                assertEquals(TaskAuthorizationCopy.cancel(locale), visible.getRootPane().getDefaultButton().getText());
                assertArrayEquals(new String[]{TaskAuthorizationCopy.cancel(locale), TaskAuthorizationCopy.allow(locale)}, pane.getOptions());
                var scroll = assertInstanceOf(JScrollPane.class, pane.getMessage());
                assertEquals(message, assertInstanceOf(JTextArea.class, scroll.getViewport().getView()).getText());
                var screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                assertTrue(visible.getWidth() <= screen.width && visible.getHeight() <= screen.height);
                var button = visible.getRootPane().getDefaultButton();
                var buttonBounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), visible);
                assertTrue(buttonBounds.y + buttonBounds.height <= visible.getHeight(), "Cancel stays inside the packed window");
                if (choice == null) visible.dispose(); else pane.setValue(choice);
            });
            assertEquals(expected, result.get(5, TimeUnit.SECONDS));
        } finally { SwingUtilities.invokeAndWait(visible::dispose); }
    }
}
