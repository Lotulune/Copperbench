package dev.copperbench.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourcePackExportServiceTest {
	@TempDir Path temp;

	@Test void exportsDeterministicZipWithSortedEntriesAndStableDigest() throws IOException {
		Path pack = temp.resolve("resource-pack");
		Files.createDirectories(pack.resolve("assets/copperbench/textures/block"));
		Files.createDirectories(pack.resolve("assets/copperbench/models"));
		Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":34,\"description\":\"Copper\"}}");
		Files.writeString(pack.resolve("assets/copperbench/models/copper_lamp.json"), "{\"parent\":\"block/cube_all\"}");
		Files.write(pack.resolve("assets/copperbench/textures/block/copper_lamp.png"), new byte[] { 1, 2, 3 });
		var service = new ResourcePackExportService(new AssetWorkspaceService(temp));
		var first = service.export("resource-pack", "exports/first.zip");
		var second = service.export("resource-pack", "exports/second.zip");
		assertEquals(first.sha256(), second.sha256());
		assertArrayEquals(Files.readAllBytes(temp.resolve("exports/first.zip")), Files.readAllBytes(temp.resolve("exports/second.zip")));
		try (ZipFile zip = new ZipFile(temp.resolve("exports/first.zip").toFile())) {
			assertEquals(List.of("assets/copperbench/models/copper_lamp.json", "assets/copperbench/textures/block/copper_lamp.png", "pack.mcmeta"),
					zip.stream().map(entry -> entry.getName()).toList());
		}
	}

	@Test void rejectsMissingMetadataAndWorkspaceEscape() throws IOException {
		Path pack = temp.resolve("resource-pack");
		Files.createDirectories(pack);
		var service = new ResourcePackExportService(new AssetWorkspaceService(temp));
		assertThrows(AssetPathViolationException.class, () -> service.export("resource-pack", "out.zip"));
		assertThrows(AssetPathViolationException.class, () -> service.export("../outside", "out.zip"));
	}
}
