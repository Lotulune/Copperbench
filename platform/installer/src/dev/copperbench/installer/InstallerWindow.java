/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

final class InstallerWindow extends JFrame {
    private static final String FONT = PlatformInstall.WINDOWS ? "Microsoft YaHei UI" : Font.SANS_SERIF;
    private final Map<String, String> options;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private PlatformInstall.Existing existing;
    private String locale = "zh".equals(Locale.getDefault().getLanguage()) ? "zh" : "en";
    private String theme = "dark";
    private String location = PlatformInstall.defaultTarget().toString();
    private boolean desktop = true, accepted, running, committed, complete, initializationFailed;
    private final boolean uninstall;
    private String error = "", phase = "prepare";
    private Path backup;
    private JPanel root, form;
    private JLabel status;
    private JTextField path;
    private JButton install, cancel;
    private JProgressBar progress;
    private BufferedImage landscape;
    private BufferedImage productIcon;

    InstallerWindow(Map<String, String> options) {
        super("Copperbench");
        this.options = options;
        uninstall = options.containsKey("uninstall");
        try {
            landscape = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/landscape.png")));
            productIcon = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/icon.png")));
            setIconImage(productIcon);
            if (!options.containsKey("preview")) existing = PlatformInstall.detect();
            if (existing != null) location = existing.path().toString();
            if (uninstall) location = InstallerMain.ownJar().getParent().toString();
            if (!options.containsKey("preview")) {
                locale = PlatformInstall.preference(Path.of(location), "ui-language.txt", "install-language.txt", Set.of("zh", "en"), locale);
                theme = PlatformInstall.preference(Path.of(location), "installer-theme.txt", "install-theme.txt", Set.of("dark", "light"), theme);
            }
        } catch (Exception exception) { error = localizedError(exception); initializationFailed = true; }
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent event) { close(); } });
        setMinimumSize(new Dimension(900, 660));
        setSize(1040, 700);
        setLocationRelativeTo(null);
        render();
    }
    String t(String zh, String en) { return locale.equals("zh") ? zh : en; }
    private Color bg() { return Color.decode(theme.equals("dark") ? "#1c1d1c" : "#f7f5f0"); }
    private Color fg() { return Color.decode(theme.equals("dark") ? "#e6e8e2" : "#242622"); }
    private Color muted() { return Color.decode(theme.equals("dark") ? "#b1b6ac" : "#596052"); }
    private Color copper() { return Color.decode(theme.equals("dark") ? "#e2aa7a" : "#985029"); }
    private JPanel panel(LayoutManager layout) { JPanel p = new JPanel(layout); p.setOpaque(false); return p; }
    private JLabel label(String text, int size, Color color, boolean bold) {
        JLabel l = new JLabel(text); l.setFont(new Font(FONT, bold ? Font.BOLD : Font.PLAIN, size)); l.setForeground(color); return l;
    }
    private JTextArea paragraph(String text) {
        JTextArea area = new JTextArea(text); area.setLineWrap(true); area.setWrapStyleWord(true); area.setEditable(false); area.setOpaque(false);
        area.setFont(new Font(FONT, Font.PLAIN, 14)); area.setForeground(muted()); area.setFocusable(false); return area;
    }
    private JButton button(String title, boolean primary) {
        JButton b = new JButton(title); b.setFont(new Font(FONT, Font.BOLD, 15));
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override protected void paintFocus(Graphics g, AbstractButton button, Rectangle view, Rectangle text, Rectangle icon) {
                g.setColor(button.getForeground()); g.drawRect(4, 4, button.getWidth() - 9, button.getHeight() - 11);
            }
        }); b.setOpaque(true); b.setContentAreaFilled(true);
        b.setBackground(primary ? copper() : bg()); b.setForeground(primary ? (theme.equals("dark") ? Color.decode("#241a12") : Color.WHITE) : fg());
        b.setBorder(new CompoundBorder(new MatteBorder(1, 1, 4, 1, primary ? copper().darker() : muted()), new EmptyBorder(10, 20, 8, 20)));
        return b;
    }
    private void render() {
        Locale selectedLocale = locale.equals("zh") ? Locale.SIMPLIFIED_CHINESE : Locale.US;
        Locale.setDefault(selectedLocale); JComponent.setDefaultLocale(selectedLocale);
        root = new JPanel(new BorderLayout()); root.setBackground(bg());
        root.setBorder(new CompoundBorder(new MatteBorder(3, 1, 1, 1, copper()), new EmptyBorder(20, 24, 24, 24)));
        JPanel header = panel(new BorderLayout(20, 0)); header.setBorder(new EmptyBorder(0, 0, 20, 0));
        JLabel brand = label("Copperbench", 25, copper(), true);
        if (productIcon != null) {
            brand.setIcon(new ImageIcon(productIcon.getScaledInstance(42, 42, Image.SCALE_SMOOTH)));
            brand.setIconTextGap(12);
        }
        header.add(brand, BorderLayout.WEST);
        JPanel choices = panel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        JComboBox<String> languages = new JComboBox<>(new String[]{"简体中文", "English"});
        languages.setSelectedIndex(locale.equals("zh") ? 0 : 1); languages.setEnabled(!running);
        languages.getAccessibleContext().setAccessibleName("Language / 语言"); languages.setName("installer-language");
        languages.addActionListener(e -> { remember(); locale = languages.getSelectedIndex() == 0 ? "zh" : "en"; render(); });
        JComboBox<String> themes = new JComboBox<>(new String[]{t("深色", "Dark"), t("浅色", "Light")});
        themes.setSelectedIndex(theme.equals("dark") ? 0 : 1); themes.setEnabled(!running);
        themes.getAccessibleContext().setAccessibleName(t("主题", "Theme")); themes.setName("installer-theme");
        themes.addActionListener(e -> { remember(); theme = themes.getSelectedIndex() == 0 ? "dark" : "light"; render(); });
        for (JComboBox<String> box : List.of(languages, themes)) {
            box.setFont(new Font(FONT, Font.PLAIN, 14)); box.setBackground(bg()); box.setForeground(fg());
            box.setBorder(new LineBorder(muted())); box.setPreferredSize(new Dimension(box == languages ? 130 : 95, 38)); choices.add(box);
        }
        header.add(choices, BorderLayout.EAST); root.add(header, BorderLayout.NORTH);
        JPanel body = panel(new BorderLayout(30, 0));
        body.add(new Landscape(), BorderLayout.WEST);
        form = panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints(); c.gridx = 0; c.gridy = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.NORTHWEST;
        addForm(label(complete ? t("03  完成", "03  COMPLETE") : running ? t("02  安装", "02  INSTALL") : t("01  准备", "01  PREPARE"), 13, copper(), true), c, 18);
        String heading = uninstall ? t("卸载 Copperbench", "Uninstall Copperbench") : complete ? t("安装完成", "Ready to create") : t("安装 Copperbench", "Install Copperbench");
        addForm(label(heading, 27, fg(), true), c, 12);
        addForm(paragraph(complete ? t("Copperbench 已准备就绪。", "Copperbench is ready to open.") : uninstall ? t("移除应用程序，保留你的工作区与个人设置。", "Remove the application. Your workspaces and personal settings will be kept.")
                : t("开始创造你的 Minecraft 模组", "Start creating your Minecraft mods")), c, 22);
        if (!complete) {
            addForm(paragraph(existing != null && existing.scope().equals("system") ? t("升级现有的系统安装 · 将按需请求系统授权", "Upgrade the existing system installation · System authorization may be requested")
                    : t("仅为当前用户安装 · 无需管理员权限", "Install for the current user · No administrator access needed")), c, 14);
            JLabel pathLabel = label(t("安装位置", "Install location"), 14, fg(), true); addForm(pathLabel, c, 8);
            JPanel pathRow = panel(new BorderLayout(8, 0));
            path = new JTextField(location); path.setName("installer-location"); path.setFont(new Font(FONT, Font.PLAIN, 13));
            path.setBackground(bg()); path.setForeground(fg()); path.setCaretColor(fg()); path.setBorder(new CompoundBorder(new LineBorder(muted()), new EmptyBorder(10, 8, 10, 8)));
            path.setEditable(!running && !uninstall && existing == null); pathLabel.setLabelFor(path);
            JButton browse = button(t("浏览…", "Browse…"), false); browse.setEnabled(path.isEditable());
            browse.addActionListener(e -> { JFileChooser chooser = new JFileChooser(); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); chooser.setDialogTitle(t("选择父目录", "Choose parent folder"));
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) { location = chooser.getSelectedFile().toPath().resolve("Copperbench").toString(); path.setText(location); } });
            pathRow.add(path, BorderLayout.CENTER); pathRow.add(browse, BorderLayout.EAST); addForm(pathRow, c, 14);
            if (PlatformInstall.WINDOWS && !uninstall) {
                JCheckBox shortcut = new JCheckBox(t("创建桌面快捷方式", "Create a desktop shortcut"), desktop); shortcut.setOpaque(false); shortcut.setForeground(fg()); shortcut.setEnabled(!running);
                shortcut.addActionListener(e -> desktop = shortcut.isSelected()); addForm(shortcut, c, 8);
            }
            if (!uninstall) {
                JCheckBox license = new JCheckBox(t("我已阅读并接受许可协议", "I have read and accept the license"), accepted); license.setOpaque(false); license.setForeground(fg()); license.setEnabled(!running);
                license.addActionListener(e -> { accepted = license.isSelected(); install.setEnabled(accepted && !running && !initializationFailed); });
                JPanel licenseRow = panel(new BorderLayout()); licenseRow.add(license, BorderLayout.CENTER);
                JButton read = button(t("查看", "Read"), false); read.addActionListener(e -> showLicense()); licenseRow.add(read, BorderLayout.EAST); addForm(licenseRow, c, 14);
            }
        } else {
            addForm(paragraph(location), c, 12);
            if (backup != null) addForm(paragraph(t("升级已完成，但临时文件被占用，未能清理：\n", "Upgrade complete, but temporary files could not be removed:\n") + backup), c, 14);
        }
        if (!error.isEmpty()) {
            addForm(paragraph(t("操作未完成，请查看详情后重试。", "Could not complete the operation. Review the details and retry.")), c, 8);
            JTextArea details = new JTextArea(error, 3, 28); details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
            details.setFont(new Font(FONT, Font.PLAIN, 12)); details.setBackground(bg()); details.setForeground(fg()); addForm(new JScrollPane(details), c, 12);
        }
        status = label(phaseText(), 13, muted(), false); status.getAccessibleContext().setAccessibleName(phaseText()); addForm(status, c, 10);
        progress = new JProgressBar(0, 100); progress.setForeground(copper()); progress.setBackground(bg()); progress.setVisible(running); progress.setStringPainted(true); addForm(progress, c, 14);
        c.weighty = 1; addForm(Box.createVerticalGlue(), c, 0); c.weighty = 0;
        JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        cancel = button(complete ? t("关闭", "Close") : t("取消", "Cancel"), false); cancel.addActionListener(e -> close()); cancel.setEnabled(!committed || complete);
        install = button(complete ? t("打开 Copperbench", "Open Copperbench") : uninstall ? t("卸载", "Uninstall") : t("开始安装", "Install"), true);
        install.setName("installer-primary"); install.setEnabled(!initializationFailed && !running && (accepted || complete || uninstall)); install.addActionListener(e -> { if (complete) launch(); else begin(); });
        actions.add(cancel); actions.add(install); addForm(actions, c, 0);
        body.add(form, BorderLayout.CENTER); root.add(body, BorderLayout.CENTER); setContentPane(root); getRootPane().setDefaultButton(install); revalidate(); repaint();
    }
    private void addForm(Component component, GridBagConstraints c, int below) { c.insets = new Insets(0, 0, below, 0); form.add(component, c); c.gridy++; }
    private void remember() { if (path != null && !complete) location = path.getText(); }
    private String phaseText() {
        return switch (phase) {
            case "copy" -> t("正在复制并校验文件…", "Copying and verifying files…");
            case "activate" -> t("正在完成安装，请稍候…", "Finishing installation. Please wait…");
            case "cleanup" -> t("正在清理旧版程序…", "Removing the previous application files…");
            case "package" -> t("正在准备系统升级包…", "Preparing the system upgrade package…");
            case "authorize" -> t("正在等待系统授权并安装…", "Waiting for system authorization and installing…");
            case "complete" -> t("安装完成", "Installation complete");
            default -> t("离线完整包 · 安装主程序无需联网", "Complete offline package · No download during installation");
        };
    }
    private void begin() {
        remember(); cancelled.set(false); error = ""; running = true; committed = false; phase = "copy"; render();
        new SwingWorker<InstallEngine.Result, InstallEngine.Progress>() {
            @Override protected InstallEngine.Result doInBackground() throws Exception {
                if (uninstall) {
                    Path target = Path.of(location);
                    PlatformInstall.ensureStopped(target);
                    Properties receipt = InstallEngine.readReceipt(target);
                    if ("system".equals(receipt.getProperty("scope")) && !PlatformInstall.WINDOWS) {
                        publish(new InstallEngine.Progress("authorize", 0, 1));
                        PlatformInstall.Command result = PlatformInstall.command(List.of("/usr/bin/pkexec", "/usr/bin/dpkg", "--remove", "copperbench"));
                        if (result.code() != 0) throw new IOException(result.output());
                    } else { InstallEngine.uninstall(target); PlatformInstall.unregisterLinux(target); }
                    return new InstallEngine.Result(target, null);
                }
                InstallEngine.Plan plan = new InstallEngine.Plan(Path.of(options.get("payload")), Path.of(options.get("manifest")), InstallerMain.ownJar(), Path.of(location),
                        options.get("version"), locale, theme, existing == null ? "user" : existing.scope(), desktop, existing != null && existing.legacy());
                Path registrar = options.containsKey("registrar") ? Path.of(options.get("registrar")) : null;
                return InstallerMain.execute(plan, registrar, existing != null && existing.deb(), cancelled, this::publish);
            }
            @Override protected void process(List<InstallEngine.Progress> values) {
                InstallEngine.Progress value = values.get(values.size() - 1); phase = value.phase();
                committed = phase.equals("activate") || phase.equals("authorize"); cancel.setEnabled(!committed);
                progress.setValue((int) Math.min(100, value.completed() * 100 / Math.max(1, value.total()))); status.setText(phaseText());
            }
            @Override protected void done() {
                running = false; committed = false;
                try { InstallEngine.Result result = get(); backup = result.previousVersion(); complete = true; phase = "complete";
                    if (uninstall) { dispose(); return; }
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    error = cause instanceof CancellationException ? t("安装已取消，原安装未被替换。", "Installation cancelled. The previous installation was not replaced.") : localizedError(cause); phase = "prepare";
                }
                render();
            }
        }.execute();
    }
    private String localizedError(Throwable cause) {
        String detail = Objects.toString(cause.getMessage(), cause.toString());
        String guidance = t("请检查安装位置的写入权限，关闭 Copperbench 后重试。", "Check that the install folder is writable, close Copperbench, and retry.");
        if (detail.contains("verification failed") || detail.contains("Missing package")) guidance = t("安装包文件不完整或校验失败，请重新下载完整安装包。", "The package is incomplete or failed verification. Download the complete package again.");
        else if (detail.contains("free space")) guidance = t("安装位置空间不足，请释放空间或选择其他位置。", "Free up space or choose another installation location.");
        else if (detail.contains("already exists") || detail.contains("recognized Copperbench")) guidance = t("这个目录无法识别为可升级的 Copperbench 安装，请选择新的空目录。", "This folder is not a recognized installation. Choose a new folder.");
        else if (detail.contains("workspaces outside")) guidance = t("请先将工作区移出程序安装目录，再进行升级。", "Move your workspaces outside the application folder before upgrading.");
        else if (detail.contains("Close Copperbench")) guidance = t("请保存工作，关闭 Copperbench 和它启动的 Minecraft 客户端后重试。", "Save your work, close Copperbench and its Minecraft clients, then retry.");
        else if (detail.contains("authorization was cancelled")) guidance = t("系统授权未完成。请重试并在系统窗口中确认授权。", "System authorization did not complete. Retry and approve the system prompt.");
        else if (detail.contains("is required")) guidance = t("此安装包支持 Windows 11 x64 或 Ubuntu 24.04 LTS x86_64。请检查系统版本。", "Check your OS version. This package requires Windows 11 x64 or Ubuntu 24.04 LTS x86_64.");
        StringBuilder result = new StringBuilder(guidance).append("\n\n").append(detail);
        for (Throwable suppressed : cause.getSuppressed()) result.append("\n").append(suppressed);
        return result.toString();
    }
    private void close() {
        if (!running) { dispose(); return; }
        if (committed) return;
        Object[] actions = {t("继续安装", "Keep installing"), t("取消安装", "Cancel installation")};
        if (JOptionPane.showOptionDialog(this, t("取消后会清理本次复制的文件，原安装不变。", "Cancel and remove files copied during this installation? The previous installation will stay in place."),
                "Copperbench", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, actions, actions[0]) == 1) {
            cancelled.set(true); cancel.setEnabled(false); status.setText(t("正在取消…", "Cancelling…"));
        }
    }
    private void showLicense() {
        try {
            JTabbedPane tabs = new JTabbedPane();
            for (String file : List.of("LICENSE.txt", "LICENSE-ADDITIONAL-TERMS.md")) {
                try (InputStream stream = getClass().getResourceAsStream("/" + file)) {
                    JTextArea text = new JTextArea(new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8), 20, 64);
                    text.setEditable(false); text.setCaretPosition(0); text.setLineWrap(true); text.setWrapStyleWord(true);
                    tabs.addTab(file.equals("LICENSE.txt") ? "GPL v3" : t("附加条款", "Additional terms"), new JScrollPane(text));
                }
            }
            JOptionPane.showOptionDialog(this, tabs, t("许可协议", "License agreement"), JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[]{t("关闭", "Close")}, null);
        } catch (IOException exception) { error = exception.getMessage(); render(); }
    }
    private void launch() {
        try { new ProcessBuilder(Path.of(location, PlatformInstall.WINDOWS ? "copperbench.exe" : "copperbench.sh").toString()).directory(Path.of(location).toFile()).start(); dispose(); }
        catch (IOException exception) { error = exception.getMessage(); render(); }
    }
    private final class Landscape extends JPanel {
        Landscape() { setPreferredSize(new Dimension(330, 500)); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics); Graphics2D g = (Graphics2D) graphics.create();
            if (landscape != null) { double scale = Math.max((double) getWidth() / landscape.getWidth(), (double) getHeight() / landscape.getHeight());
                int w = (int) (landscape.getWidth() * scale), h = (int) (landscape.getHeight() * scale); g.drawImage(landscape, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null); }
            g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 170), 0, 150, new Color(0, 0, 0, 0))); g.fillRect(0, 0, getWidth(), 150);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.decode("#f4d4ae")); g.setFont(new Font(FONT, Font.BOLD, 23));
            g.drawString(t("从想法到方块世界", "BUILD YOUR WORLD"), 22, 42);
            g.setColor(new Color(12, 18, 12, 200)); g.fillRect(0, getHeight() - 94, getWidth(), 94);
            g.setColor(Color.decode("#e6e8e2")); g.setFont(new Font(FONT, Font.PLAIN, 14));
            g.drawString(t("可视化项目管理", "Visual project management"), 22, getHeight() - 61);
            g.drawString(t("Agent 协作 · Minecraft 模组创作", "Agents · Minecraft mod creation"), 22, getHeight() - 31); g.dispose();
        }
    }
    static void preview(Path output) throws Exception {
        Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> {
            InstallerWindow window = new InstallerWindow(Map.of("preview", "true"));
            try {
                for (String language : List.of("zh", "en")) for (String theme : List.of("dark", "light")) {
                    window.locale = language; window.theme = theme; window.render(); window.addNotify(); window.validate();
                    BufferedImage image = new BufferedImage(window.root.getWidth(), window.root.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics(); window.root.printAll(graphics); graphics.dispose();
                    try { ImageIO.write(image, "png", output.resolve("installer-" + language + "-" + theme + ".png").toFile()); } catch (IOException e) { throw new UncheckedIOException(e); }
                }
            } finally { window.dispose(); }
        });
    }
}
