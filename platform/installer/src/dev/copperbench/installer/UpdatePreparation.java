/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;
import java.util.zip.ZipFile;

/** Downloads changed components and constructs a verified payload outside the running installation. */
public final class UpdatePreparation {
    private UpdatePreparation() {}
    @FunctionalInterface public interface ContentSource { InputStream open(URI uri) throws IOException; }
    @FunctionalInterface public interface Download { void fetch(String name, Path destination, long bytes) throws IOException; }
    public static Properties installationOptions(Path target) throws IOException {
        PlatformInstall.Existing existing = PlatformInstall.detect();
        Properties options;
        if (Files.isRegularFile(target.resolve(InstallEngine.RECEIPT))) options = InstallEngine.readReceipt(target);
        else if (existing != null && existing.path().equals(target)) {
            options = new Properties(); options.setProperty("scope", existing.scope());
        } else throw new IOException("Install Copperbench with the graphical installer before using in-app upgrades.");
        options.setProperty("legacy", Boolean.toString(!Files.exists(target.resolve(InstallEngine.RECEIPT))));
        options.setProperty("deb", Boolean.toString(existing != null && existing.path().equals(target) && existing.deb()));
        options.setProperty("language", PlatformInstall.preference(target, "ui-language.txt", "install-language.txt", Set.of("zh", "en"), "zh"));
        options.setProperty("theme", PlatformInstall.preference(target, "ui-theme.txt", "install-theme.txt", Set.of("light", "dark"), "dark"));
        return options;
    }
    public static List<UpdateManifest.Part> changedParts(UpdateManifest manifest, Path installed, BooleanSupplier cancelled) throws IOException {
        List<UpdateManifest.Part> changed = new ArrayList<>();
        for (var part : manifest.parts()) {
            for (var entry : part.entries()) {
                check(cancelled);
                if (!UpdateManifest.matches(installed, entry)) { changed.add(part); break; }
            }
        }
        return List.copyOf(changed);
    }
    public static void prepare(UpdateManifest manifest, List<UpdateManifest.Part> changed, Path installed, Path scratch,
                               Download download, BooleanSupplier cancelled, Consumer<String> progress) throws IOException {
        InstallEngine.ensureNoLinks(scratch);
        Path payload = scratch.resolve("payload"); Files.createDirectory(payload);
        for (var part : manifest.parts()) {
            check(cancelled); boolean fetch = changed.contains(part);
            Path archive = scratch.resolve(part.name());
            if (fetch) {
                progress.accept(part.name()); download.fetch(part.name(), archive, part.bytes());
                if (Files.size(archive) != part.bytes() || !UpdateManifest.sha256(archive).equals(part.sha256())) throw new IOException("Update checksum mismatch: " + part.name());
            }
            try (ZipFile zip = fetch ? new ZipFile(archive.toFile()) : null) {
                Set<String> expected = new HashSet<>(); part.entries().stream().filter(e -> e.type().equals("F")).forEach(e -> expected.add(e.name()));
                if (zip != null) {
                    var entries = zip.entries(); Set<String> found = new HashSet<>();
                    while (entries.hasMoreElements()) { String name = entries.nextElement().getName(); if (!expected.contains(name) || !found.add(name)) throw new IOException("Unexpected update archive entry: " + name); }
                    if (!found.equals(expected)) throw new IOException("Incomplete update component: " + part.name());
                }
                for (var entry : part.entries()) {
                    check(cancelled);
                    Path output = payload.resolve(entry.name()); InstallEngine.ensureNoLinks(output.getParent()); Files.createDirectories(output.getParent());
                    switch (entry.type()) {
                        case "D" -> Files.createDirectories(output);
                        case "L" -> {
                            Path link = Path.of(new String(Base64.getDecoder().decode(entry.digest()), StandardCharsets.UTF_8));
                            if (link.isAbsolute() || !output.getParent().resolve(link).normalize().startsWith(payload)) throw new IOException("Unsafe update link");
                            Files.createSymbolicLink(output, link);
                        }
                        case "F" -> {
                            if (!fetch && !UpdateManifest.matches(installed, entry)) throw new IOException("Installed files changed while preparing the update. Check for updates again.");
                            try (InputStream in = fetch ? zip.getInputStream(zip.getEntry(entry.name())) : Files.newInputStream(installed.resolve(entry.name()));
                                 OutputStream out = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
                                byte[] buffer = new byte[128 * 1024]; long copied = 0;
                                for (int n; (n = in.read(buffer)) >= 0;) {
                                    check(cancelled); copied += n; if (copied > entry.size()) throw new IOException("Update file exceeds declared size"); out.write(buffer, 0, n);
                                }
                                if (copied != entry.size()) throw new IOException("Incomplete update file: " + entry.name());
                            }
                            if (!UpdateManifest.sha256(output).equals(entry.digest())) throw new IOException("Update file checksum mismatch: " + entry.name());
                        }
                    }
                    if (!entry.type().equals("L")) InstallEngine.setPermissions(output, entry.permissions());
                }
            }
            Files.deleteIfExists(archive);
        }
        Files.writeString(scratch.resolve("payload.tsv"), manifest.inventory(), StandardCharsets.UTF_8);
    }
    public static InputStream open(URI uri) throws IOException {
        for (int redirects = 0; redirects < 6; redirects++) {
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null) throw new IOException("Updates require HTTPS");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(20_000); connection.setReadTimeout(30_000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Copperbench-Updater");
            connection.setRequestProperty("Accept", uri.getHost().equals("api.github.com") ? "application/vnd.github+json" : "application/octet-stream");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location"); connection.disconnect();
                if (location == null) throw new IOException("Update redirect has no location"); uri = uri.resolve(location); continue;
            }
            if (status != 200) { connection.disconnect(); throw new IOException("Update server returned HTTP " + status); }
            return new FilterInputStream(connection.getInputStream()) { @Override public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } } };
        }
        throw new IOException("Too many update redirects");
    }
    public static void download(URI uri, Path file, long limit, BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        download(uri, file, limit, cancelled, progress, UpdatePreparation::open);
    }
    public static void download(URI uri, Path file, long limit, BooleanSupplier cancelled, LongConsumer progress, ContentSource source) throws IOException {
        try (InputStream input = source.open(uri); OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) {
            long read = 0; byte[] buffer = new byte[128 * 1024];
            for (int n; (n = input.read(buffer)) >= 0;) {
                check(cancelled); read += n; if (read > limit) throw new IOException("Update download exceeds declared size");
                out.write(buffer, 0, n); progress.accept(n);
            }
            if (read != limit) throw new IOException("Incomplete update download");
        }
    }
    private static void check(BooleanSupplier cancelled) { if (cancelled.getAsBoolean()) throw new CancellationException(); }
}
