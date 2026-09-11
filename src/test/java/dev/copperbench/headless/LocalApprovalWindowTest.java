package dev.copperbench.headless;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Frame;
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

    private static void exercise(String choice, boolean expected) throws Exception {
        String title = "Approval window test " + UUID.randomUUID();
        var result = CompletableFuture.supplyAsync(() -> LocalApprovalWindow.confirm(title, "Test fixture only"));
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
                assertEquals("Cancel", pane.getInitialValue());
                assertEquals("Cancel", visible.getRootPane().getDefaultButton().getText());
                if (choice == null) visible.dispose(); else pane.setValue(choice);
            });
            assertEquals(expected, result.get(5, TimeUnit.SECONDS));
        } finally { SwingUtilities.invokeAndWait(visible::dispose); }
    }
}
