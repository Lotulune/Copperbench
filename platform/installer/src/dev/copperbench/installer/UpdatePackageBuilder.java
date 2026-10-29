/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.zip.*;

/** Creates release assets from the exact exported distribution, without a previous release dependency. */
public final class UpdatePackageBuilder {
    public static void main(String[] args) throws Exception { build(Path.of(args[0]), args[1], args[2], Path.of(args[3])); }
    public static Path build(Path payload, String version, String platform, Path output) throws IOException {
        UpdateManifest.compareVersions(version, version);
        if (!Set.of("windows-x64", "linux-x64").contains(platform)) throw new IOException("Unknown update platform");
        Files.createDirectories(output);
        Map<String, List<Path>> groups = new TreeMap<>();
        try (var paths = Files.walk(payload)) {
            for (Path path : paths.sorted().toList()) {
                if (path.equals(payload)) continue;
                String name = payload.relativize(path).toString().replace('\\', '/');
                String group = name.startsWith("jdk/") || name.equals("jdk") ? "jdk"
                        : name.startsWith("jdk21/") || name.equals("jdk21") ? "jdk21"
                        : name.startsWith("gradle-dists/") ? name.split("/")[1]
                        : name.startsWith("lib/") && !name.matches("lib/(?:mcreator|copperbench)[^/]*\\.jar") ? "libraries" : "application";
                groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(path);
            }
        }
        Properties manifest = new Properties(); manifest.setProperty("schema", "1"); manifest.setProperty("product", "dev.copperbench.studio");
        manifest.setProperty("version", version); manifest.setProperty("platform", platform); manifest.setProperty("parts", "" + groups.size());
        int index = 0;
        for (var group : groups.entrySet()) {
            String name = "copperbench-update-" + platform + "-" + group.getKey() + ".zip";
            Path archive = output.resolve(name); StringBuilder inventory = new StringBuilder();
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                for (Path path : group.getValue()) {
                    String relative = payload.relativize(path).toString().replace('\\', '/');
                    boolean link = Files.isSymbolicLink(path), directory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
                    int mode = directory ? 493 : 420;
                    if (!link && path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                        mode = 0; var permissions = Files.getPosixFilePermissions(path); var bits = PosixFilePermission.values();
                        for (int i = 0; i < bits.length; i++) if (permissions.contains(bits[i])) mode |= 1 << (8 - i);
                    }
                    if (platform.equals("linux-x64") && relative.matches("(?:copperbench\\.sh|gradlew|jdk(?:21)?/bin/.*|jdk(?:21)?/lib/(?:jspawnhelper|jexec|jcef_helper|cef_server)|gradle-dists/.*/bin/gradle)")) mode |= 73;
                    inventory.append(link ? "L" : directory ? "D" : "F").append('\t').append(encoded(relative)).append('\t')
                            .append(link ? encoded(Files.readSymbolicLink(path).toString().replace('\\', '/')) : directory ? "-" : UpdateManifest.sha256(path))
                            .append('\t').append(link || directory ? 0 : Files.size(path)).append('\t').append(mode).append('\n');
                    if (!link && !directory) { ZipEntry entry = new ZipEntry(relative); entry.setTime(0); zip.putNextEntry(entry); Files.copy(path, zip); zip.closeEntry(); }
                }
            }
            String key = "part." + index++ + ".";
            manifest.setProperty(key + "name", name); manifest.setProperty(key + "sha256", UpdateManifest.sha256(archive));
            manifest.setProperty(key + "bytes", "" + Files.size(archive)); manifest.setProperty(key + "inventory", encoded(inventory.toString()));
        }
        Path descriptor = output.resolve("copperbench-update-" + platform + ".properties");
        try (Writer writer = Files.newBufferedWriter(descriptor, StandardCharsets.UTF_8)) { manifest.store(writer, "Copperbench component update"); }
        try (InputStream in = Files.newInputStream(descriptor)) { UpdateManifest.read(in); }
        return descriptor;
    }
    private static String encoded(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
}
