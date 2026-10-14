/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;

/** Theme-aware painting on standard Swing controls, retaining their keyboard/accessibility behavior. */
final class InstallerControls {
    private final Color surface, foreground, muted, border, accent, selected, onAccent;

    InstallerControls(boolean dark) {
        surface = Color.decode(dark ? "#262924" : "#ffffff");
        foreground = Color.decode(dark ? "#e6e8e2" : "#242622");
        muted = Color.decode(dark ? "#b1b6ac" : "#596052");
        border = Color.decode(dark ? "#62685c" : "#899080");
        accent = Color.decode(dark ? "#e2aa7a" : "#985029");
        selected = Color.decode(dark ? "#3b332b" : "#f4e9dd");
        onAccent = Color.decode(dark ? "#241a12" : "#ffffff");
    }

    void combo(JComboBox<String> box, int width) {
        box.setFont(InstallerFonts.font(Font.PLAIN, 13));
        box.setForeground(foreground); box.setBackground(surface); box.setOpaque(false);
        box.setBorder(new ControlBorder()); box.setPreferredSize(new Dimension(width, 38));
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        box.setUI(new BasicComboBoxUI() {
            @Override public void paint(Graphics graphics, JComponent component) {
                Graphics2D g = smooth(graphics);
                g.setColor(surface); g.fillRoundRect(1, 1, component.getWidth() - 2, component.getHeight() - 2, 12, 12); g.dispose();
                super.paint(graphics, component);
            }
            @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { }
            @Override protected JButton createArrowButton() {
                JButton arrow = new JButton() {
                    @Override protected void paintComponent(Graphics graphics) {
                        Graphics2D g = smooth(graphics);
                        g.setColor(isEnabled() ? muted : border); g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int x = getWidth() / 2, y = getHeight() / 2;
                        Path2D chevron = new Path2D.Float();
                        chevron.moveTo(x - 4, y - 2); chevron.lineTo(x, y + 2); chevron.lineTo(x + 4, y - 2);
                        g.draw(chevron); g.dispose();
                    }
                };
                arrow.setOpaque(false); arrow.setContentAreaFilled(false); arrow.setBorder(new EmptyBorder(0, 5, 0, 5));
                arrow.setFocusable(false); return arrow;
            }
            @Override protected ComboPopup createPopup() {
                BasicComboPopup popup = new BasicComboPopup(comboBox);
                popup.setBorder(new CompoundBorder(new LineBorder(border, 1, true), new EmptyBorder(5, 5, 5, 5)));
                popup.setOpaque(true); popup.setBackground(surface); popup.getList().setBackground(surface);
                return popup;
            }
        });
        box.setRenderer((list, value, index, highlighted, focused) -> {
            boolean inMenu = index >= 0;
            JPanel row = new JPanel(new BorderLayout(12, 0)) {
                @Override protected void paintComponent(Graphics graphics) {
                    if (inMenu && highlighted) {
                        Graphics2D g = smooth(graphics); g.setColor(selected);
                        g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8); g.dispose();
                    }
                }
            };
            row.setOpaque(false); row.setBorder(new EmptyBorder(inMenu ? 8 : 0, 10, inMenu ? 8 : 0, 8));
            boolean chosen = Objects.equals(value, box.getSelectedItem());
            JLabel text = new JLabel(String.valueOf(value)); text.setFont(InstallerFonts.font(inMenu && chosen ? Font.BOLD : Font.PLAIN, 13));
            text.setForeground(!box.isEnabled() ? muted : inMenu && chosen ? accent : foreground); row.add(text, BorderLayout.CENTER);
            if (inMenu) {
                JLabel marker = new JLabel(new Icon() {
                    public int getIconWidth() { return 16; }
                    public int getIconHeight() { return 16; }
                    public void paintIcon(Component c, Graphics graphics, int x, int y) {
                        if (chosen) { Graphics2D g = smooth(graphics); tick(g, x + 8, y + 8, accent); g.dispose(); }
                    }
                });
                row.add(marker, BorderLayout.EAST);
            }
            return row;
        });
        repaintOnFocus(box);
    }

    void checkbox(JCheckBox box) {
        box.setFont(InstallerFonts.font(Font.PLAIN, 14)); box.setForeground(foreground); box.setOpaque(false);
        box.setBorder(new EmptyBorder(6, 0, 6, 6)); box.setIconTextGap(9); box.setRolloverEnabled(true);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        box.setUI(new BasicCheckBoxUI() {
            @Override protected void paintFocus(Graphics g, Rectangle rectangle, Dimension size) { }
            @Override protected void paintText(Graphics g, AbstractButton button, Rectangle text, String value) {
                paintLabel(g, button, text, value, button.isEnabled() ? foreground : muted);
            }
        });
        Icon icon = new Icon() {
            public int getIconWidth() { return 24; }
            public int getIconHeight() { return 24; }
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = smooth(graphics);
                if (!box.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.45f));
                if (box.hasFocus()) { g.setColor(accent); g.setStroke(new BasicStroke(1.5f)); g.draw(new RoundRectangle2D.Float(x + .75f, y + .75f, 22.5f, 22.5f, 8, 8)); }
                g.setColor(box.isSelected() ? accent : surface); g.fillRoundRect(x + 3, y + 3, 18, 18, 5, 5);
                g.setColor(box.isSelected() || box.getModel().isRollover() ? accent : border);
                g.setStroke(new BasicStroke(1.4f)); g.drawRoundRect(x + 3, y + 3, 18, 18, 5, 5);
                if (box.isSelected()) tick(g, x + 12, y + 12, onAccent);
                g.dispose();
            }
        };
        box.setIcon(icon); box.setSelectedIcon(icon); box.setDisabledIcon(icon); box.setDisabledSelectedIcon(icon);
        repaintOnFocus(box);
    }

    void field(JTextField field) {
        field.setBackground(surface); field.setBorder(new CompoundBorder(new ControlBorder(), new EmptyBorder(10, 10, 10, 10)));
        repaintOnFocus(field);
    }

    void button(JButton button, boolean primary) {
        button.setFont(InstallerFonts.font(Font.BOLD, 15));
        button.setForeground(primary ? onAccent : foreground); button.setBackground(primary ? accent : surface);
        button.setOpaque(false); button.setContentAreaFilled(false); button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(11, 20, 11, 20));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setUI(new BasicButtonUI() {
            @Override public void paint(Graphics graphics, JComponent component) {
                Graphics2D g = smooth(graphics);
                Color fill = !button.isEnabled() ? surface : primary ? accent : button.getModel().isRollover() ? selected : surface;
                if (button.getModel().isPressed() && button.isEnabled()) fill = fill.darker();
                g.setColor(fill); g.fillRoundRect(1, 1, button.getWidth() - 2, button.getHeight() - 2, 12, 12);
                g.setColor(button.hasFocus() ? accent : button.isEnabled() ? primary ? accent : border : muted.darker());
                g.setStroke(new BasicStroke(button.hasFocus() ? 2f : 1f));
                g.drawRoundRect(1, 1, button.getWidth() - 3, button.getHeight() - 3, 12, 12);
                if (button.hasFocus() && primary && button.isEnabled()) {
                    g.setColor(onAccent); g.drawRoundRect(4, 4, button.getWidth() - 9, button.getHeight() - 9, 8, 8);
                }
                g.dispose(); super.paint(graphics, component);
            }
            @Override protected void paintText(Graphics g, AbstractButton b, Rectangle text, String value) {
                paintLabel(g, b, text, value, b.isEnabled() ? b.getForeground() : muted);
            }
        });
        repaintOnFocus(button);
    }

    private static void paintLabel(Graphics graphics, AbstractButton button, Rectangle text, String value, Color color) {
        Graphics2D g = smooth(graphics); g.setColor(color);
        BasicGraphicsUtils.drawStringUnderlineCharAt(g, value, button.getDisplayedMnemonicIndex(), text.x, text.y + g.getFontMetrics().getAscent());
        g.dispose();
    }

    private final class ControlBorder extends AbstractBorder {
        @Override public Insets getBorderInsets(Component component) { return new Insets(2, 2, 2, 2); }
        @Override public Insets getBorderInsets(Component component, Insets insets) { insets.set(2, 2, 2, 2); return insets; }
        @Override public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = smooth(graphics);
            boolean focused = component.hasFocus() || component instanceof JComboBox<?> combo && combo.isPopupVisible();
            if (!component.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.45f));
            g.setColor(focused ? accent : border); g.setStroke(new BasicStroke(focused ? 2f : 1f));
            g.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 12, 12); g.dispose();
        }
    }

    private static void repaintOnFocus(JComponent component) {
        component.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { component.repaint(); }
            @Override public void focusLost(FocusEvent event) { component.repaint(); }
        });
    }

    private static Graphics2D smooth(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static void tick(Graphics2D g, int centerX, int centerY, Color color) {
        g.setColor(color); g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D path = new Path2D.Float(); path.moveTo(centerX - 5, centerY); path.lineTo(centerX - 2, centerY + 3.5); path.lineTo(centerX + 5, centerY - 3.5); g.draw(path);
    }
}
