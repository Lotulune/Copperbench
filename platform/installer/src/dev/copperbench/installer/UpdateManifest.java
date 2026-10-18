/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Versioned update inventory. Parts are independently hashed and independently reusable. */
public record UpdateManifest(String version, String platform, List<Part> parts) {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public record Part(String name, String sha256, long bytes, String inventory, List<InstallEngine.Entry> entries) {}
    public static UpdateManifest read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Update inventory is too large");
        Properties p = new Properties(); p.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
        try {
            if (!"1".equals(p.getProperty("schema")) || !"dev.copperbench.studio".equals(p.getProperty("product"))) throw new IllegalArgumentException();
            String version = p.getProperty("version"), platform = p.getProperty("platform");
            compareVersions(version, version);
            if (!Set.of("windows-x64", "linux-x64").contains(platform)) throw new IllegalArgumentException();
            int count = Integer.parseInt(p.getProperty("parts"));
            if (count < 1 || count > 64) throw new IllegalArgumentException();
            List<Part> parts = new ArrayList<>(); Set<String> names = new HashSet<>();
            StringBuilder all = new StringBuilder(); long total = 0;
            for (int i = 0; i < count; i++) {
                String prefix = "part." + i + ".", name = p.getProperty(prefix + "name"), hash = p.getProperty(prefix + "sha256");
                long size = Long.parseLong(p.getProperty(prefix + "bytes"));
                if (!name.matches("copperbench-update-[a-z0-9.-]+\\.zip") || !names.add(name)
                        || !hash.matches("[a-f0-9]{64}") || size < 1 || size > 4L * 1024 * 1024 * 1024) throw new IllegalArgumentException();
                String inventory = new String(Base64.getDecoder().decode(p.getProperty(prefix + "inventory")), StandardCharsets.UTF_8);
                var entries = InstallEngine.parseManifest(inventory.lines().toList());
                for (var entry : entries) total = Math.addExact(total, entry.size());
                if (total > 32L * 1024 * 1024 * 1024) throw new IllegalArgumentException();
                all.append(inventory).append('\n');
                parts.add(new Part(name, hash, size, inventory, entries));
            }
            InstallEngine.parseManifest(all.toString().lines().toList()); // Reject duplicates across parts, too.
            return new UpdateManifest(version, platform, List.copyOf(parts));
        } catch (RuntimeException error) { throw new IOException("Invalid update inventory", error); }
    }
    public String inventory() { return parts.stream().map(Part::inventory).reduce("", (a, b) -> a + b + "\n"); }
    public static int compareVersions(String left, String right) {
        if (left == null || right == null || !left.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || !right.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))
            throw new IllegalArgumentException("Invalid version");
        String[] a = left.split("\\."), b = right.split("\\.");
        for (int i = 0; i < 3; i++) { int compared = new java.math.BigInteger(a[i]).compareTo(new java.math.BigInteger(b[i])); if (compared != 0) return compared; }
        return 0;
    }
    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) { byte[] buffer = new byte[128 * 1024]; for (int n; (n = in.read(buffer)) >= 0;) hash.update(buffer, 0, n); }
            return HexFormat.of().formatHex(hash.digest());
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    public static boolean matches(Path root, InstallEngine.Entry entry) throws IOException {
        Path path = root.resolve(entry.name()); InstallEngine.ensureNoLinks(path.getParent());
        return switch (entry.type()) {
            case "D" -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
            case "L" -> Files.isSymbolicLink(path) && Base64.getEncoder().encodeToString(Files.readSymbolicLink(path).toString()
                    .replace('\\', '/').getBytes(StandardCharsets.UTF_8)).equals(entry.digest());
            case "F" -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) == entry.size() && sha256(path).equals(entry.digest());
            default -> false;
        };
    }
}
