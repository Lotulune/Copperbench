package dev.copperbench.headless;

import dev.copperbench.shell.ProductDialogs;
import dev.copperbench.shell.UiLocalePreferences;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.GraphicsEnvironment;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CompletableFuture;

/** A taskbar-visible approval window for product commands without a desktop owner. */
final class LocalApprovalWindow {
    private LocalApprovalWindow() {}

    static boolean confirm(String title, String message) {
        return confirm(title, message, UiLocalePreferences.read());
    }

    static boolean confirm(String title, String message, String locale) {
        if (GraphicsEnvironment.isHeadless()) return false;
        if (SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("CLI approval must be requested outside the event dispatch thread");
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(title);
            try {
                String allow = TaskAuthorizationCopy.allow(locale), cancel = TaskAuthorizationCopy.cancel(locale);
                JOptionPane pane = ProductDialogs.pane(message, JOptionPane.QUESTION_MESSAGE, new Object[]{cancel, allow}, cancel);
                // Wrapped paths and translated paragraphs must not push the
                // decision buttons outside the window or available screen.
                var screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                int contentWidth = Math.min(560, Math.max(200, screen.width - 160));
                int contentHeight = Math.min(420, Math.max(80, screen.height - 200));
                JTextArea body = (JTextArea) pane.getMessage();
                body.setColumns(0);
                body.setSize(contentWidth, Integer.MAX_VALUE);
                JScrollPane scroll = new JScrollPane(body, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                scroll.setBorder(null);
                scroll.setOpaque(false);
                scroll.getViewport().setOpaque(false);
                scroll.setPreferredSize(new Dimension(contentWidth, Math.min(contentHeight, body.getPreferredSize().height + 8)));
                scroll.getVerticalScrollBar().setUnitIncrement(16);
                body.setCaretPosition(0);
                pane.setMessage(scroll);
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
