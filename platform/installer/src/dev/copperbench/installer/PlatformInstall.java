/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

final class PlatformInstall {
    static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    record Existing(Path path, String scope, boolean legacy, boolean deb) { }
    static Path defaultTarget() {
        return WINDOWS ? Path.of(System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("user.home") + "/AppData/Local"), "Programs", "Copperbench")
                : Path.of(System.getProperty("user.home"), ".local", "opt", "copperbench");
    }
    static Path xdg(String value, Path fallback) {
        if (value != null && !value.isBlank()) {
            try { Path path = Path.of(value); if (path.isAbsolute()) return path.normalize(); }
            catch (InvalidPathException ignored) { }
        }
        return fallback;
    }
    static Path config() { return xdg(System.getenv("XDG_CONFIG_HOME"), Path.of(System.getProperty("user.home"), ".config")).resolve("copperbench"); }
    static Path applications() { return xdg(System.getenv("XDG_DATA_HOME"), Path.of(System.getProperty("user.home"), ".local/share")).resolve("applications"); }
    static Path preferences() {
        Path fallback = WINDOWS ? Path.of(System.getProperty("user.home"), ".copperbench") : config();
        return xdg(System.getenv("COPPERBENCH_HOME"), xdg(System.getenv("MCREATOR_HOME"), fallback));
    }
    static String preference(Path installed, String userFile, String installFile, Set<String> allowed, String fallback) {
        for (Path file : List.of(preferences().resolve(userFile), installed.resolve(installFile))) {
            try { String value = Files.readString(file, StandardCharsets.UTF_8).trim(); if (allowed.contains(value)) return value; }
            catch (IOException ignored) { }
        }
        return fallback;
    }

    static Existing detect() throws IOException {
        if (WINDOWS) {
            for (String hive : List.of("HKCU", "HKLM")) for (String view : List.of("64", "32")) {
                String key = hive + "\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Copperbench";
                String location = registry(key, "InstallLocation", view);
                if (location.isBlank()) {
                    String command = registry(key, "UninstallString", view).replaceAll("^\"|\"$", "");
                    if (command.toLowerCase(Locale.ROOT).endsWith("\\uninstall.exe")) location = command.substring(0, command.length() - 14);
                }
                if (!location.isBlank()) {
                    Path path = Path.of(location).toAbsolutePath().normalize();
                    if (Files.isRegularFile(path.resolve("copperbench.exe")))
                        return new Existing(path, hive.equals("HKLM") ? "system" : "user", !Files.exists(path.resolve(InstallEngine.RECEIPT)), false);
                }
            }
        } else {
            Path locationFile = config().resolve("install-location.txt");
            Path target = Files.isRegularFile(locationFile) ? xdg(Files.readString(locationFile).trim(), defaultTarget()) : defaultTarget();
            if (Files.isRegularFile(target.resolve(InstallEngine.RECEIPT))) {
                Properties receipt = InstallEngine.readReceipt(target);
                return new Existing(target, receipt.getProperty("scope"), false, "system".equals(receipt.getProperty("scope")));
            }
            if (Files.isExecutable(Path.of("/usr/bin/dpkg-query"))) {
                Command result = command(List.of("/usr/bin/dpkg-query", "-W", "-f=${Status}", "copperbench"));
                if (result.code == 0 && result.output.contains("install ok installed")) return new Existing(Path.of("/opt/copperbench"), "system", true, true);
            }
        }
        return null;
    }
    static void checkPlatform() throws IOException {
        if (WINDOWS) {
            String build = registry("HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "CurrentBuild", "64");
            if (!build.matches("[0-9]+") || Integer.parseInt(build) < 22000 || System.getenv("ProgramW6432") == null)
                throw new IOException("Windows 11 x64 is required.");
        } else {
            String os = Files.readString(Path.of("/etc/os-release"));
            if (!os.matches("(?s).*\\bID=\"?ubuntu\"?\\s.*") || !os.contains("VERSION_ID=\"24.04\"") || !System.getProperty("os.arch").equals("amd64"))
                throw new IOException("Ubuntu 24.04 LTS x86_64 is required.");
        }
    }
    static void ensureStopped(Path target) throws IOException {
        for (ProcessHandle process : ProcessHandle.allProcesses().toList()) {
            Optional<String> executable = process.info().command();
            if (process.pid() != ProcessHandle.current().pid() && executable.isPresent()) {
                try { if (Path.of(executable.get()).toAbsolutePath().normalize().startsWith(target)) throw new IOException("Close Copperbench and its running Minecraft clients before upgrading."); }
                catch (InvalidPathException ignored) { }
            }
        }
    }
    private static String registry(String key, String value, String view) throws IOException {
        Command result = command(List.of("reg.exe", "query", key, "/v", value, "/reg:" + view));
        if (result.code != 0) return "";
        for (String line : result.output.split("\\R")) {
            int at = line.indexOf("REG_SZ");
            if (at >= 0) return line.substring(at + 6).trim();
        }
        return "";
    }
    record Command(int code, String output) { }
    static Command command(List<String> arguments) throws IOException {
        Process process = new ProcessBuilder(arguments).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), Charset.forName(System.getProperty("native.encoding", "UTF-8")));
        try { return new Command(process.waitFor(), output); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IOException("Installation was interrupted", exception); }
    }
    static void register(InstallEngine.Plan plan, Path registrar, Path scratch) throws IOException {
        if (WINDOWS) {
            Path ini = scratch.resolve("register.ini");
            writeIni(ini, Map.of("Target", plan.target().toString(), "Scope", plan.scope(), "Desktop", Boolean.toString(plan.desktop()), "Version", plan.version()));
            Command locations = command(List.of(registrar.toString(), "/LOCATIONS=" + ini));
            if (locations.code != 0) throw new IOException("Windows could not resolve the application shortcuts.");
            Properties paths = new Properties();
            try (Reader reader = Files.newBufferedReader(ini, StandardCharsets.UTF_16LE)) {
                // INI paths contain backslashes, which java.util.Properties treats as escapes.
                for (String line : new BufferedReader(reader).lines().toList()) {
                    int equals = line.indexOf('='); if (equals > 0) paths.setProperty(line.substring(0, equals), line.substring(equals + 1));
                }
            }
            Path programs = Path.of(paths.getProperty("Programs")), desktop = Path.of(paths.getProperty("DesktopPath"));
            List<Path> shortcuts = new ArrayList<>(List.of(programs.resolve("Copperbench/Copperbench.lnk")));
            if (plan.desktop()) shortcuts.add(desktop.resolve("Copperbench.lnk"));
            Map<Path, byte[]> before = snapshot(shortcuts);
            String key = (plan.scope().equals("system") ? "HKLM" : "HKCU") + "\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Copperbench";
            boolean registered = command(List.of("reg.exe", "query", key, "/reg:64")).code == 0;
            Path regBackup = scratch.resolve("registration.reg");
            if (registered && command(List.of("reg.exe", "export", key, regBackup.toString(), "/y", "/reg:64")).code != 0)
                throw new IOException("Could not back up the existing Windows registration.");
            try {
                Command result = command(List.of(registrar.toString(), "/REGISTER=" + ini));
                if (result.code != 0) throw new IOException("Windows could not register the installation (" + result.code + ").");
            } catch (IOException error) {
                try {
                    Command removed = command(List.of("reg.exe", "delete", key, "/f", "/reg:64"));
                    if (removed.code != 0 && command(List.of("reg.exe", "query", key, "/reg:64")).code == 0)
                        throw new IOException("Could not restore the Windows registration.");
                    if (registered && command(List.of("reg.exe", "import", regBackup.toString(), "/reg:64")).code != 0)
                        throw new IOException("Could not restore the previous Windows registration.");
                } catch (IOException rollback) { error.addSuppressed(rollback); }
                try { restore(before); } catch (IOException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        } else {
            List<Path> files = List.of(applications().resolve("copperbench.desktop"), applications().resolve("copperbench-uninstall.desktop"), config().resolve("install-location.txt"));
            Map<Path, byte[]> before = snapshot(files);
            String target = plan.target().toString();
            String launcher = desktopQuote(target + "/copperbench.sh");
            String uninstall = desktopQuote(target + "/jdk/bin/java") + " -jar " + desktopQuote(target + "/copperbench-setup.jar") + " --uninstall";
            try {
                Files.createDirectories(applications()); Files.createDirectories(config());
                writeDesktop(files.get(0), "Copperbench", "Copperbench", launcher, target);
                writeDesktop(files.get(1), "Uninstall Copperbench", "卸载 Copperbench", uninstall, target);
                Files.writeString(files.get(2), target + "\n", StandardCharsets.UTF_8);
            } catch (IOException error) {
                try { restore(before); } catch (IOException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        }
    }
    private static Map<Path, byte[]> snapshot(List<Path> paths) throws IOException {
        Map<Path, byte[]> files = new LinkedHashMap<>();
        for (Path path : paths) {
            InstallEngine.ensureNoLinks(path);
            files.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
        }
        return files;
    }
    private static void restore(Map<Path, byte[]> files) throws IOException {
        IOException failure = null;
        for (var file : files.entrySet()) {
            try { if (file.getValue() == null) Files.deleteIfExists(file.getKey()); else Files.write(file.getKey(), file.getValue()); }
            catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (failure != null) throw failure;
    }
    private static void writeDesktop(Path file, String name, String chinese, String exec, String target) throws IOException {
        Files.writeString(file, "[Desktop Entry]\nType=Application\nName=" + name + "\nName[zh_CN]=" + chinese
                + "\nComment=Create Minecraft mods with visual tools and agents\nComment[zh_CN]=可视化管理项目，与 Agent 协作创建 Minecraft 模组\nExec="
                + exec + "\nIcon=" + target.replace("\\", "\\\\") + "/icon.png\nTerminal=false\nCategories=Development;IDE;\n", StandardCharsets.UTF_8);
    }
    static String desktopQuote(String text) {
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) throw new IllegalArgumentException("Invalid desktop path");
        return "\"" + text.replace("\\", "\\\\\\\\").replace("\"", "\\\\\"").replace("`", "\\\\`").replace("$", "\\\\$").replace("%", "%%") + "\"";
    }
    static void writeIni(Path file, Map<String, String> fields) throws IOException {
        StringBuilder contents = new StringBuilder("\uFEFF[Install]\r\n");
        for (var field : fields.entrySet()) {
            if (field.getValue().contains("\n") || field.getValue().contains("\r") || field.getValue().contains("\"")) throw new IOException("Invalid installation path");
            contents.append(field.getKey()).append('=').append(field.getValue()).append("\r\n");
        }
        Files.writeString(file, contents, StandardCharsets.UTF_16LE);
    }
    static void unregisterLinux(Path target) throws IOException {
        for (String name : List.of("copperbench.desktop", "copperbench-uninstall.desktop")) {
            Path file = applications().resolve(name);
            if (Files.isRegularFile(file) && Files.readString(file).contains(desktopQuote(target.resolve("copperbench.sh").toString()))) Files.delete(file);
            else if (name.contains("uninstall") && Files.isRegularFile(file) && Files.readString(file).contains(desktopQuote(target.resolve("copperbench-setup.jar").toString()))) Files.delete(file);
        }
        Path location = config().resolve("install-location.txt");
        if (Files.isRegularFile(location) && Files.readString(location).trim().equals(target.toString())) Files.delete(location);
    }
}
