package dev.copperbench.assets;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable, wire-safe identity and metadata for one workspace asset. */
public record AssetDescriptor(String id, String relativePath, AssetCategory category, long size, String sha256,
		String mediaType, Instant updatedAt) {

	public AssetDescriptor {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(relativePath, "relativePath");
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(sha256, "sha256");
		Objects.requireNonNull(mediaType, "mediaType");
		Objects.requireNonNull(updatedAt, "updatedAt");
		if (!relativePath.equals(normalize(relativePath)) || relativePath.startsWith("/")
				|| Path.of(relativePath).isAbsolute() || Path.of(relativePath).startsWith(".."))
			throw new IllegalArgumentException("relativePath must be normalized and workspace-relative");
		if (!id.matches("asset:[0-9a-f]{64}"))
			throw new IllegalArgumentException("id must be the stable normalized-path identity");
		if (sha256.length() != 64 || !sha256.matches("[0-9a-f]{64}"))
			throw new IllegalArgumentException("sha256 must be lowercase hexadecimal");
		if (size < 0)
			throw new IllegalArgumentException("size must not be negative");
	}

	public static AssetDescriptor fromFile(Path workspaceRoot, Path file) throws IOException {
		Path root = workspaceRoot.toRealPath();
		Path realFile = file.toRealPath();
		if (!realFile.startsWith(root) || !Files.isRegularFile(realFile))
			throw new IllegalArgumentException("Asset path is outside the workspace or is not a file");
		String relativePath = normalize(root.relativize(realFile).toString());
		String hash = sha256(realFile);
		return new AssetDescriptor("asset:" + digest(relativePath.getBytes(StandardCharsets.UTF_8)), relativePath,
				AssetCategory.fromRelativePath(relativePath),
				Files.size(realFile), hash, mediaType(realFile), Files.getLastModifiedTime(realFile).toInstant());
	}

	private static String normalize(String path) {
		return Path.of(path.replace('\\', '/')).normalize().toString().replace('\\', '/');
	}

	private static String sha256(Path file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = Files.newInputStream(file)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = input.read(buffer)) != -1)
					digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) {
			throw new AssertionError("JVM must provide SHA-256", exception);
		}
	}

	private static String digest(byte[] value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
		} catch (NoSuchAlgorithmException exception) {
			throw new AssertionError("JVM must provide SHA-256", exception);
		}
	}

	private static String mediaType(Path file) {
		String name = file.getFileName().toString().toLowerCase();
		return switch (name.substring(name.lastIndexOf('.') + 1)) {
			case "json", "mcmeta", "bbmodel" -> "application/json";
			case "png" -> "image/png";
			case "jpg", "jpeg" -> "image/jpeg";
			case "ogg" -> "audio/ogg";
			case "wav" -> "audio/wav";
			case "zip" -> "application/zip";
			default -> "application/octet-stream";
		};
	}
}
