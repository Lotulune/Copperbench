package dev.copperbench.headless;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CompletableFuture;

/** A taskbar-visible approval window for product commands without a desktop owner. */
final class LocalApprovalWindow {
    private LocalApprovalWindow() {}

    static boolean confirm(String title, String message) {
        if (GraphicsEnvironment.isHeadless()) return false;
        if (SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("CLI approval must be requested outside the event dispatch thread");
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(title);
            try {
                String allow = "Allow", cancel = "Cancel";
                JOptionPane pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE,
                        JOptionPane.YES_NO_OPTION, null, new Object[]{allow, cancel}, cancel);
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.setContentPane(pane);
                frame.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosed(WindowEvent event) { answer.complete(false); }
                });
                pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, event -> {
                    Object value = pane.getValue();
                    if (value == JOptionPane.UNINITIALIZED_VALUE) return;
                    answer.complete(allow.equals(value));
                    frame.dispose();
                });
                frame.getRootPane().registerKeyboardAction(_ -> frame.dispose(),
                        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                        javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
                frame.pack();
                frame.setLocationRelativeTo(null);
                // A top-level owner also exposes this command in the taskbar and desktop automation.
                // A parentless JOptionPane otherwise has only a hidden shared owner.
                frame.setVisible(true);
                frame.toFront();
                pane.selectInitialValue();
            } catch (Throwable failure) {
                frame.dispose();
                answer.completeExceptionally(failure);
            }
        });
        return answer.join();
    }
}
