package dev.copperbench.gradle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Repairs Mojang mapping files that were cached after an interrupted download.
 * <p>
 * Legacy NeoGradle versions can keep a truncated {@code client.txt} or {@code server.txt}
 * under the workspace-local Gradle cache and subsequently fail inside srgutils instead of
 * downloading the file again. The adjacent Minecraft metadata contains Mojang's expected
 * byte size and SHA-1, so corrupt cache entries can be detected without network access.
 */
public final class MinecraftMappingsCacheRepair {

	private static final Logger LOG = LogManager.getLogger(MinecraftMappingsCacheRepair.class);

	private MinecraftMappingsCacheRepair() {
	}

	/**
	 * Validates cached Mojang mappings below a workspace and deletes only entries whose
	 * published size or SHA-1 does not match the cached Minecraft metadata.
	 *
	 * @return number of corrupt mapping files removed
	 */
	public static int repairCorruptMappings(Path workspaceRoot) {
		Path versionsRoot = workspaceRoot.resolve(".gradle/caches/minecraft/versions");
		if (!Files.isDirectory(versionsRoot))
			return 0;

		int repaired = 0;
		try (DirectoryStream<Path> versions = Files.newDirectoryStream(versionsRoot)) {
			for (Path version : versions) {
				if (Files.isDirectory(version))
					repaired += repairVersion(version);
			}
		} catch (IOException exception) {
			LOG.warn("Failed to inspect Minecraft mappings cache at {}", versionsRoot, exception);
		}
		return repaired;
	}

	static int repairVersion(Path versionRoot) {
		Path metadata = versionRoot.resolve("metadata.json");
		if (!Files.isRegularFile(metadata))
			return 0;

		try {
			JsonObject root = JsonParser.parseString(Files.readString(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
			if (!root.has("downloads") || !root.get("downloads").isJsonObject())
				return 0;

			JsonObject downloads = root.getAsJsonObject("downloads");
			int repaired = 0;
			repaired += repairMapping(versionRoot.resolve("client.txt"), downloads, "client_mappings");
			repaired += repairMapping(versionRoot.resolve("server.txt"), downloads, "server_mappings");
			return repaired;
		} catch (Exception exception) {
			LOG.warn("Failed to validate Minecraft mappings metadata at {}", metadata, exception);
			return 0;
		}
	}

	private static int repairMapping(Path mapping, JsonObject downloads, String metadataKey) throws IOException {
		if (!Files.isRegularFile(mapping) || !downloads.has(metadataKey) || !downloads.get(metadataKey).isJsonObject())
			return 0;

		JsonObject expected = downloads.getAsJsonObject(metadataKey);
		long expectedSize = expected.has("size") ? expected.get("size").getAsLong() : -1;
		String expectedSha1 = expected.has("sha1") ? expected.get("sha1").getAsString() : null;
		String officialUrl = expected.has("url") ? expected.get("url").getAsString() : null;

		long actualSize = Files.size(mapping);
		boolean corrupt = expectedSize >= 0 && actualSize != expectedSize;
		String actualSha1 = null;
		if (!corrupt && expectedSha1 != null && !expectedSha1.isBlank()) {
			actualSha1 = sha1(mapping);
			corrupt = !expectedSha1.equalsIgnoreCase(actualSha1);
		}

		if (!corrupt)
			return 0;

		LOG.warn("Removing corrupt Minecraft mappings cache {} (size {} expected {}, sha1 {} expected {})",
				mapping, actualSize, expectedSize, actualSha1, expectedSha1);
		if (officialUrl != null && repairFromOfficialUrl(mapping, officialUrl, expectedSize, expectedSha1))
			return 1;
		Files.deleteIfExists(mapping);
		return 1;
	}

	private static boolean repairFromOfficialUrl(Path mapping, String officialUrl, long expectedSize,
			String expectedSha1) {
		Path temporary = mapping.resolveSibling(mapping.getFileName() + ".repair-download");
		try {
			URLConnection connection = URI.create(officialUrl).toURL().openConnection(Proxy.NO_PROXY);
			connection.setConnectTimeout(30_000);
			connection.setReadTimeout(120_000);
			try (InputStream input = connection.getInputStream()) {
				Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
			}

			long downloadedSize = Files.size(temporary);
			if (expectedSize >= 0 && downloadedSize != expectedSize)
				throw new IOException("downloaded size " + downloadedSize + " does not match " + expectedSize);
			if (expectedSha1 != null && !expectedSha1.isBlank()) {
				String downloadedSha1 = sha1(temporary);
				if (!expectedSha1.equalsIgnoreCase(downloadedSha1))
					throw new IOException("downloaded SHA-1 " + downloadedSha1 + " does not match " + expectedSha1);
			}

			try {
				Files.move(temporary, mapping, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicMoveFailure) {
				Files.move(temporary, mapping, StandardCopyOption.REPLACE_EXISTING);
			}
			LOG.info("Restored Minecraft mappings cache {} from official metadata URL", mapping);
			return true;
		} catch (Exception exception) {
			LOG.warn("Failed to restore corrupt Minecraft mappings cache {} directly from {}", mapping, officialUrl,
					exception);
			return false;
		} finally {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException ignored) {
			}
		}
	}

	private static String sha1(Path file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			try (InputStream input = Files.newInputStream(file)) {
				byte[] buffer = new byte[64 * 1024];
				int read;
				while ((read = input.read(buffer)) != -1)
					digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-1 is unavailable", exception);
		}
	}
}
