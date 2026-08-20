package dev.copperbench.assets;

import dev.copperbench.core.contract.UiCore.Actor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetPublishBatchServiceTest {

	@TempDir Path temp;

	@Test void createWritesDeterministicZipAndAListableManifest() throws Exception {
		pack();
		AssetWorkspaceService assets = new AssetWorkspaceService(temp);
		AssetPublishBatchService service = new AssetPublishBatchService(assets, new ResourcePackExportService(assets),
				null, Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
		var first = service.create("copper-pack", "resource-pack", "exports/first.zip", Actor.HEADLESS, "t1");
		var second = service.create("copper-pack", "resource-pack", "exports/second.zip", Actor.HEADLESS, "t2");
		assertEquals(first.sha256(), second.sha256());
		assertEquals(1, service.list().size());
		assertEquals("copper-pack", service.list().getFirst().name());
		assertTrue(Files.isRegularFile(temp.resolve(".copperbench/publish-batches/copper-pack.json")));
	}

	@Test void rejectsInvalidBatchNames() throws Exception {
		pack();
		AssetWorkspaceService assets = new AssetWorkspaceService(temp);
		AssetPublishBatchService service = new AssetPublishBatchService(assets, new ResourcePackExportService(assets),
				null, Clock.systemUTC());
		assertThrows(AssetPathViolationException.class,
				() -> service.create("Bad Name", "resource-pack", "exports/out.zip", Actor.UI, "t"));
	}

	private void pack() throws Exception {
		Path pack = temp.resolve("resource-pack");
		Files.createDirectories(pack.resolve("assets/copperbench/textures"));
		Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":34,\"description\":\"Copper\"}}");
		Files.write(pack.resolve("assets/copperbench/textures/lamp.png"), new byte[] { 1, 2, 3 });
	}
}
