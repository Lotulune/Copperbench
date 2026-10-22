/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.shell;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import static dev.copperbench.shell.UiLocalePreferences.text;

/** A one-time, keyboard accessible spotlight over the real workspace entry point. */
public final class StartupTour extends JPanel {
    private final JFrame owner;
    private final Component previous;
    private final JComponent target;
    private final JPanel card = new JPanel(new BorderLayout(0, 16)) {
        @Override public void paint(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setComposite(AlphaComposite.SrcOver.derive(Math.max(0, Math.min(1, opacity * stepOpacity))));
            copy.translate(0, animate ? (1 - stepOpacity) * 12 : 0);
            super.paint(copy); copy.dispose();
        }
    };
    private final JLabel heading = new JLabel();
    private final JTextArea description = new JTextArea();
    private final JButton next = new JButton();
    private final Timer animation;
    private final Runnable start;
    private int step;
    private float opacity;
    private float stepOpacity = 1f;
    private long stepStarted;
    private long closingStarted;
    private Runnable afterDismiss = () -> {};
    private final long started = System.nanoTime();
    private final boolean animate = !Boolean.FALSE.equals(Toolkit.getDefaultToolkit().getDesktopProperty("win.clientAreaAnimation"));

    public static void show(JFrame owner, JComponent target, Runnable start) {
        StartupTour tour = new StartupTour(owner, target, start);
        owner.setGlassPane(tour);
        tour.setVisible(true);
        tour.next.requestFocusInWindow();
    }
    private StartupTour(JFrame owner, JComponent target, Runnable start) {
        this.owner = owner; this.previous = owner.getGlassPane(); this.target = target; this.start = start;
        setName("first-run-tour"); setLayout(null); setOpaque(false); setFocusCycleRoot(true);
        addMouseListener(new MouseAdapter() {}); // The mask consumes clicks outside the tour.
        addMouseMotionListener(new MouseMotionAdapter() {});
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xb07848), 2), new EmptyBorder(20, 22, 20, 22)));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 20f));
        card.add(heading, BorderLayout.NORTH);
        description.setFont(UIManager.getFont("Label.font")); description.setEditable(false);
        description.setFocusable(false); description.setOpaque(false); description.setLineWrap(true); description.setWrapStyleWord(true);
        description.setRows(4); card.add(description, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton skip = new JButton(text("跳过引导", "Skip tour"));
        skip.addActionListener(_ -> dismiss()); buttons.add(skip); buttons.add(next);
        next.setName("first-run-next"); next.setMargin(new Insets(9, 16, 9, 16));
        next.addActionListener(_ -> { if (++step < 3) updateStep(); else dismiss(this.start); });
        card.add(buttons, BorderLayout.SOUTH); add(card);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "dismiss");
        getActionMap().put("dismiss", new AbstractAction() { @Override public void actionPerformed(ActionEvent event) { dismiss(); } });
        animation = new Timer(16, _ -> {
            long now = System.nanoTime();
            if (closingStarted != 0) {
                opacity = Math.max(0f, 1f - (now - closingStarted) / 180_000_000f);
                if (opacity == 0) { finishDismiss(); return; }
            } else opacity = Math.min(1f, (now - started) / 240_000_000f);
            float progress = Math.min(1f, (now - stepStarted) / 340_000_000f);
            stepOpacity = 1f - (float) Math.pow(1 - progress, 4);
            repaint();
            if (closingStarted == 0 && opacity == 1 && progress == 1) animationStop();
        });
        updateStep();
        if (animate) animation.start(); else opacity = 1f;
        owner.addWindowListener(new WindowAdapter() { @Override public void windowClosed(WindowEvent event) { animation.stop(); } });
    }
    private void updateStep() {
        stepStarted = System.nanoTime(); stepOpacity = animate ? 0f : 1f;
        heading.setText(new String[] {text("欢迎，开始第一件作品", "Welcome to your first creation"), text("创建并编辑你的方块", "Create and edit your block"), text("运行、检查、交付", "Run, inspect and deliver")}[step]);
        description.setText(new String[] {
                text("从这里新建工作区。我们会准备 Fabric 1.21.1 的首作设置，再由你选择项目保存位置。", "Start with a new workspace here. We will prepare Fabric 1.21.1 settings; you choose where to save your project."),
                text("在工作区中创建 Trail Block，修改名称与硬度，再应用更改。每一步都会指向对应的操作。", "Create Trail Block in your workspace, change its name and hardness, then apply your changes. The guide will point to each action."),
                text("构建后启动 Minecraft 检查方块，最后导出作品。现在开始，也可以跳过并自由探索。", "Build and launch Minecraft to inspect your block, then export your creation. Start now or skip the tour and explore.")}[step]);
        next.setText(step < 2 ? text("下一步", "Next") + "  " + (step + 1) + "/3" : text("开始创作", "Start creating"));
        getAccessibleContext().setAccessibleName(heading.getText());
        revalidate(); repaint();
        if (animate) animation.restart();
    }
    private void animationStop() { animation.stop(); }
    private void dismiss() { dismiss(() -> {}); }
    private void dismiss(Runnable after) {
        if (closingStarted != 0) return;
        afterDismiss = after; next.setEnabled(false);
        if (!animate) { finishDismiss(); return; }
        closingStarted = System.nanoTime(); animation.restart();
    }
    private void finishDismiss() { animation.stop(); setVisible(false); owner.setGlassPane(previous); target.requestFocusInWindow(); afterDismiss.run(); }
    public void focusNext() { next.requestFocusInWindow(); }
    @Override public void doLayout() {
        int width = Math.min(470, getWidth() - 32), height = Math.min(250, getHeight() - 32);
        Rectangle spot = SwingUtilities.convertRectangle(target.getParent(), target.getBounds(), this);
        int y = Math.min(getHeight() - height - 16, spot.y + spot.height + 20);
        card.setBounds(Math.max(16, Math.min(getWidth() - width - 16, spot.x)), Math.max(16, y), width, height);
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Rectangle spot = SwingUtilities.convertRectangle(target.getParent(), target.getBounds(), this);
        var hole = new RoundRectangle2D.Float(spot.x - 6, spot.y - 6, spot.width + 12, spot.height + 12, 14, 14);
        Area mask = new Area(new Rectangle(0, 0, getWidth(), getHeight())); mask.subtract(new Area(hole));
        g.setColor(new Color(12, 16, 22, (int) (175 * opacity))); g.fill(mask);
        g.setColor(new Color(226, 170, 122, (int) (opacity * 230))); g.setStroke(new BasicStroke(2)); g.draw(hole);
        g.dispose();
    }
}
