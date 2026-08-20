package dev.copperbench.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackClientLoadServiceTest {

	@TempDir Path temp;

	@Test void preparesZipAndClientOptionsWithoutLaunchingMinecraft() throws Exception {
		Path pack = temp.resolve("resource-pack");
		Files.createDirectories(pack.resolve("assets/copperbench"));
		Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":34,\"description\":\"Copper\"}}");
		Files.writeString(pack.resolve("assets/copperbench/lang.json"), "{\"item.copper.lamp\":\"Lamp\"}");
		AssetWorkspaceService assets = new AssetWorkspaceService(temp);
		ResourcePackClientLoadService service = new ResourcePackClientLoadService(assets,
				new ResourcePackExportService(assets));
		var prepared = service.prepare("resource-pack", "copper.zip");
		assertEquals(34, prepared.packFormat());
		assertTrue(prepared.readyForClient());
		assertTrue(Files.isRegularFile(temp.resolve("run/resourcepacks/copper.zip")));
		assertTrue(Files.readString(temp.resolve("run/options.txt")).contains("file/copper.zip"));
		try (ZipFile zip = new ZipFile(temp.resolve("run/resourcepacks/copper.zip").toFile())) {
			assertTrue(zip.getEntry("pack.mcmeta") != null);
		}
	}

	@Test void rejectsMissingPackFormat() throws Exception {
		Path pack = temp.resolve("resource-pack");
		Files.createDirectories(pack);
		Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Copper\"}}");
		AssetWorkspaceService assets = new AssetWorkspaceService(temp);
		ResourcePackClientLoadService service = new ResourcePackClientLoadService(assets,
				new ResourcePackExportService(assets));
		assertThrows(AssetPathViolationException.class, () -> service.prepare("resource-pack", "copper.zip"));
	}
}
