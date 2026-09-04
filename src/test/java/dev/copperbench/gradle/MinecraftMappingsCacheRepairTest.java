package dev.copperbench.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftMappingsCacheRepairTest {

	@TempDir
	Path temp;

	@Test
	void removesTruncatedMappingsButKeepsValidMappings() throws Exception {
		Path version = versionDirectory();
		byte[] client = "truncated".getBytes(StandardCharsets.UTF_8);
		byte[] server = "complete-server".getBytes(StandardCharsets.UTF_8);
		Files.write(version.resolve("client.txt"), client);
		Files.write(version.resolve("server.txt"), server);
		writeMetadata(version, client.length + 10L, sha1(client), server.length, sha1(server));

		assertEquals(1, MinecraftMappingsCacheRepair.repairCorruptMappings(temp));
		assertFalse(Files.exists(version.resolve("client.txt")));
		assertTrue(Files.exists(version.resolve("server.txt")));
	}

	@Test
	void removesSameSizeMappingsWithWrongSha1() throws Exception {
		Path version = versionDirectory();
		byte[] client = "same-size".getBytes(StandardCharsets.UTF_8);
		Files.write(version.resolve("client.txt"), client);
		writeMetadata(version, client.length, "0000000000000000000000000000000000000000", -1, null);

		assertEquals(1, MinecraftMappingsCacheRepair.repairCorruptMappings(temp));
		assertFalse(Files.exists(version.resolve("client.txt")));
	}

	private Path versionDirectory() throws Exception {
		Path version = temp.resolve(".gradle/caches/minecraft/versions/1.20.1");
		Files.createDirectories(version);
		return version;
	}

	private static void writeMetadata(Path version, long clientSize, String clientSha1,
			long serverSize, String serverSha1) throws Exception {
		String client = "\"client_mappings\":{" + downloadFields(clientSize, clientSha1) + "}";
		String server = serverSize >= 0
				? ",\"server_mappings\":{" + downloadFields(serverSize, serverSha1) + "}"
				: "";
		Files.writeString(version.resolve("metadata.json"), "{\"downloads\":{" + client + server + "}}",
				StandardCharsets.UTF_8);
	}

	private static String downloadFields(long size, String sha1) {
		return "\"size\":" + size + ",\"sha1\":\"" + sha1 + "\"";
	}

	private static String sha1(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
	}
}
