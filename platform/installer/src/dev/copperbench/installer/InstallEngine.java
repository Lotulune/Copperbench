/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Offline, staged installation. No application services are loaded by this entry point. */
public final class InstallEngine {
    public static final String RECEIPT = ".copperbench-install.properties";
    public static final String MANIFEST = ".copperbench-files";
    public record Plan(Path payload, Path manifest, Path installerJar, Path target, String version,
                       String language, String theme, String scope, boolean desktop, boolean legacy) {
        public Plan {
            payload = payload.toAbsolutePath().normalize();
            manifest = manifest.toAbsolutePath().normalize();
            installerJar = installerJar.toAbsolutePath().normalize();
            target = target.toAbsolutePath().normalize();
            if (!Set.of("zh", "en").contains(language) || !Set.of("dark", "light").contains(theme)
                    || !Set.of("user", "system").contains(scope)) throw new IllegalArgumentException("Invalid installation options");
            if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalArgumentException("Invalid product version");
            if (target.getParent() == null || target.startsWith(payload) || payload.startsWith(target))
                throw new IllegalArgumentException("Install location overlaps the installation package");
            if (target.toString().contains("\n") || target.toString().contains("\r"))
                throw new IllegalArgumentException("Invalid installation path");
        }
    }
    public record Entry(String type, String name, String digest, long size, int permissions) { }
    public record Progress(String phase, long completed, long total) { }
    public record Result(Path target, Path previousVersion) { }
    @FunctionalInterface public interface Integration { void register(Plan plan) throws IOException; }

    public static List<Entry> readManifest(Path file) throws IOException {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\t", -1);
            try {
                if (fields.length != 5 || !Set.of("F", "D", "L").contains(fields[0])) throw new IllegalArgumentException();
                String name = new String(Base64.getDecoder().decode(fields[1]), StandardCharsets.UTF_8);
                Path relative = Path.of(name);
                if (relative.isAbsolute() || name.contains("\\") || name.indexOf(':') >= 0 || name.indexOf('\0') >= 0
                        || name.isBlank() || relative.normalize().startsWith("..") || !relative.equals(relative.normalize()) || !seen.add(name))
                    throw new IllegalArgumentException();
                long size = Long.parseLong(fields[3]);
                int mode = Integer.parseInt(fields[4]);
                if (size < 0 || mode < 0 || mode > 511 || (fields[0].equals("F") && !fields[2].matches("[a-f0-9]{64}")))
                    throw new IllegalArgumentException();
                entries.add(new Entry(fields[0], name, fields[2], size, mode));
            } catch (IllegalArgumentException exception) { throw new IOException("The installation file list is invalid", exception); }
        }
        if (entries.isEmpty()) throw new IOException("The installation package is empty");
        return List.copyOf(entries);
    }

    public Result install(Plan plan, BooleanSupplier cancelled, Consumer<Progress> progress, Integration integration) throws IOException {
        List<Entry> entries = readManifest(plan.manifest());
        long total = entries.stream().mapToLong(Entry::size).sum();
        Path target = plan.target(), parent = target.getParent();
        ensureNoLinks(parent);
        if (Files.exists(target)) {
            ensureNoLinks(target);
            if (Files.exists(target.resolve(RECEIPT), LinkOption.NOFOLLOW_LINKS)) {
                Properties receipt = readReceipt(target);
                if (!plan.scope().equals(receipt.getProperty("scope"))) throw new IOException("The installation scope cannot change during an upgrade.");
            } else if (!plan.legacy()) {
                throw new IOException("The selected folder already exists. Choose a new installation folder.");
            }
            // Shipped examples are managed payload. User-created workspaces must be moved explicitly.
            Set<String> managed = Files.isRegularFile(target.resolve(MANIFEST), LinkOption.NOFOLLOW_LINKS)
                    ? new HashSet<>(Files.readAllLines(target.resolve(MANIFEST), StandardCharsets.UTF_8)) : Set.of();
            try (var paths = Files.walk(target)) {
                for (Path path : paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mcreator")
                        && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                    String name = target.relativize(path).toString().replace('\\', '/');
                    if (managed.contains(name)) continue;
                    Entry packaged = entries.stream().filter(e -> e.type().equals("F") && e.name().equals(name)).findFirst().orElse(null);
                    if (plan.legacy() && packaged != null
                            && HexFormat.of().formatHex(sha256().digest(Files.readAllBytes(path))).equals(packaged.digest())) continue;
                    throw new IOException("Move workspaces outside the application folder before upgrading.");
                }
            }
        }
        Files.createDirectories(parent);
        if (Files.getFileStore(parent).getUsableSpace() < total + 64L * 1024 * 1024)
            throw new IOException("There is not enough free space in the installation location.");
        Path stage = Files.createTempDirectory(parent, ".copperbench-install-");
        Path backup = null;
        boolean published = false;
        long done = 0;
        List<String> owned = new ArrayList<>();
        try {
            for (Entry entry : entries) {
                checkCancelled(cancelled);
                Path input = plan.payload().resolve(entry.name()), output = stage.resolve(entry.name());
                ensureNoLinks(input.getParent());
                ensureNoLinks(output.getParent());
                switch (entry.type()) {
                    case "D" -> Files.createDirectories(output);
                    case "L" -> {
                        if (!Files.isSymbolicLink(input)) throw new IOException("Missing package link: " + entry.name());
                        Path link = Files.readSymbolicLink(input);
                        String expected = new String(Base64.getDecoder().decode(entry.digest()), StandardCharsets.UTF_8);
                        if (!link.toString().replace('\\', '/').equals(expected) || link.isAbsolute()
                                || !input.getParent().resolve(link).normalize().startsWith(plan.payload()))
                            throw new IOException("Unsafe package link: " + entry.name());
                        Files.createDirectories(output.getParent());
                        Files.createSymbolicLink(output, link);
                    }
                    case "F" -> {
                        if (!Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing package file: " + entry.name());
                        Files.createDirectories(output.getParent());
                        MessageDigest digest = sha256();
                        long copied = 0;
                        try (InputStream in = Files.newInputStream(input); OutputStream out = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
                            byte[] buffer = new byte[128 * 1024];
                            for (int count; (count = in.read(buffer)) != -1;) {
                                checkCancelled(cancelled);
                                out.write(buffer, 0, count); digest.update(buffer, 0, count); copied += count;
                                progress.accept(new Progress("copy", done + copied, total));
                            }
                        }
                        if (copied != entry.size() || !HexFormat.of().formatHex(digest.digest()).equals(entry.digest()))
                            throw new IOException("Package verification failed: " + entry.name());
                        done += copied;
                    }
                    default -> throw new IOException("Unsupported package entry");
                }
                if (!entry.type().equals("L")) setPermissions(output, entry.permissions());
                owned.add(entry.name());
            }
            Files.copy(plan.installerJar(), stage.resolve("copperbench-setup.jar"));
            Files.writeString(stage.resolve("install-language.txt"), plan.language() + "\n", StandardCharsets.UTF_8);
            Files.writeString(stage.resolve("install-theme.txt"), plan.theme() + "\n", StandardCharsets.UTF_8);
            Properties receipt = new Properties();
            receipt.setProperty("product", "dev.copperbench.studio"); receipt.setProperty("version", plan.version());
            receipt.setProperty("scope", plan.scope()); receipt.setProperty("target", target.toString());
            try (Writer writer = Files.newBufferedWriter(stage.resolve(RECEIPT), StandardCharsets.UTF_8)) { receipt.store(writer, "Copperbench installation"); }
            owned.addAll(List.of("copperbench-setup.jar", "install-language.txt", "install-theme.txt", RECEIPT, MANIFEST, "uninstall.exe"));
            if (System.getProperty("os.name").startsWith("Windows")) owned.add(".copperbench-files-windows");
            owned.sort(Comparator.comparingInt(String::length).reversed());
            Files.write(stage.resolve(MANIFEST), owned, StandardCharsets.UTF_8);
            if (System.getProperty("os.name").startsWith("Windows"))
                Files.writeString(stage.resolve(".copperbench-files-windows"), String.join("\r\n", owned) + "\r\n", StandardCharsets.UTF_16LE);
            if (Files.isRegularFile(target.resolve(MANIFEST))) preserveUnmanaged(target, stage);
            checkCancelled(cancelled);
            progress.accept(new Progress("activate", total, total));
            // Moves are on the same filesystem. A busy Windows installation fails here before replacement.
            if (Files.exists(target)) {
                backup = parent.resolve("Copperbench-previous-" + UUID.randomUUID());
                Files.move(target, backup);
            }
            Files.move(stage, target);
            published = true;
            integration.register(plan);
            progress.accept(new Progress("complete", total, total));
            return new Result(target, backup);
        } catch (IOException | RuntimeException error) {
            if (published) {
                try { removeCreatedTree(target); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            }
            if (backup != null && !Files.exists(target)) {
                try { Files.move(backup, target); } catch (IOException restore) { error.addSuppressed(restore); }
            }
            throw error;
        } finally { if (Files.exists(stage)) removeCreatedTree(stage); }
    }

    private static void preserveUnmanaged(Path existing, Path stage) throws IOException {
        Set<String> owned = new HashSet<>(Files.readAllLines(existing.resolve(MANIFEST), StandardCharsets.UTF_8));
        try (var paths = Files.walk(existing)) {
            for (Path path : paths.toList()) {
                Path relative = existing.relativize(path);
                if (relative.toString().isEmpty() || owned.contains(relative.toString().replace('\\', '/'))) continue;
                Path to = stage.resolve(relative);
                if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) continue;
                ensureNoLinks(path.getParent());
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(to);
                else {
                    Files.createDirectories(to.getParent());
                    Files.copy(path, to, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    public static void uninstall(Path target) throws IOException {
        target = target.toAbsolutePath().normalize();
        readReceipt(target);
        List<String> owned = Files.readAllLines(target.resolve(MANIFEST), StandardCharsets.UTF_8);
        // Validate the entire list before deleting anything, including the receipt itself.
        for (String name : owned) {
            Path file = target.resolve(name).normalize();
            if (name.isBlank() || !file.startsWith(target) || file.equals(target)) throw new IOException("Invalid uninstall file list");
            ensureNoLinks(file.getParent());
        }
        owned.sort(Comparator.comparingInt(String::length).reversed());
        for (String name : owned) {
            Path file = target.resolve(name).normalize();
            try { Files.deleteIfExists(file); } catch (DirectoryNotEmptyException ignored) { /* Keep files added by the user. */ }
        }
        try { Files.deleteIfExists(target); } catch (DirectoryNotEmptyException ignored) { }
    }

    static Properties readReceipt(Path target) throws IOException {
        ensureNoLinks(target);
        if (!Files.isRegularFile(target.resolve(RECEIPT), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("This folder is not a recognized Copperbench installation.");
        Properties receipt = new Properties();
        try (Reader reader = Files.newBufferedReader(target.resolve(RECEIPT), StandardCharsets.UTF_8)) { receipt.load(reader); }
        if (!"dev.copperbench.studio".equals(receipt.getProperty("product")) || !target.toString().equals(receipt.getProperty("target"))
                || !Set.of("user", "system").contains(receipt.getProperty("scope", "")))
            throw new IOException("This folder is not a recognized Copperbench installation.");
        return receipt;
    }

    public static void ensureNoLinks(Path path) throws IOException {
        for (Path p = path.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) continue;
            if (Files.isSymbolicLink(p) || Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()
                    || !p.toRealPath().equals(p.toRealPath(LinkOption.NOFOLLOW_LINKS)))
                throw new IOException("Installation paths must not pass through a symbolic link: " + p);
        }
    }
    private static void removeCreatedTree(Path path) throws IOException {
        try (var paths = Files.walk(path)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
    private static void checkCancelled(BooleanSupplier cancelled) { if (cancelled.getAsBoolean()) throw new CancellationException(); }
    private static MessageDigest sha256() { try { return MessageDigest.getInstance("SHA-256"); } catch (Exception error) { throw new IllegalStateException(error); } }
    private static void setPermissions(Path file, int mode) throws IOException {
        if (!file.getFileSystem().supportedFileAttributeViews().contains("posix")) return;
        PosixFilePermission[] permissions = PosixFilePermission.values();
        Set<PosixFilePermission> values = EnumSet.noneOf(PosixFilePermission.class);
        for (int i = 0; i < permissions.length; i++) if ((mode & (1 << (8 - i))) != 0) values.add(permissions[i]);
        Files.setPosixFilePermissions(file, values);
    }
}
