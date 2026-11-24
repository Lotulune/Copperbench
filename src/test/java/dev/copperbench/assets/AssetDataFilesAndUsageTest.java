package dev.copperbench.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Structure templates join the asset catalog, and textures report what they are for. */
class AssetDataFilesAndUsageTest {
	@TempDir Path root;

	@Test void structureTemplatesUnderDataRootsAreAssetsButDataJsonStaysSource() throws Exception {
		write("src/main/resources/data/trail/structure/hut.nbt");
		write("src/main/data/trail/structure/ruin.nbt");
		write("src/main/resources/data/trail/recipe/lamp.json");
		write("src/main/resources/assets/trail/textures/block/lamp.png");
		List<String> paths = new AssetWorkspaceService(root).list().stream().map(AssetDescriptor::relativePath).toList();
		assertTrue(paths.contains("src/main/resources/data/trail/structure/hut.nbt"), paths.toString());
		assertTrue(paths.contains("src/main/data/trail/structure/ruin.nbt"), paths.toString());
		assertFalse(paths.contains("src/main/resources/data/trail/recipe/lamp.json"), "data JSON is edited as source, not as an asset");
		assertEquals(AssetCategory.STRUCTURE, AssetCategory.fromRelativePath("src/main/data/trail/structure/ruin.nbt"));
	}

	@Test void textureUsageComesFromTheFolderUnderTextures() {
		assertEquals("block", AssetCategory.textureUsage("src/main/resources/assets/trail/textures/block/lamp.png"));
		assertEquals("gui", AssetCategory.textureUsage("assets/trail/textures/gui/sprites/panel.png"));
		assertEquals("entity", AssetCategory.textureUsage("textures/entity/golem.png"));
		assertEquals("other", AssetCategory.textureUsage("assets/trail/textures/custom/thing.png"));
		assertEquals("other", AssetCategory.textureUsage("assets/trail/textures/loose.png"));
		assertNull(AssetCategory.textureUsage("assets/trail/models/block/lamp.json"));
	}

	@Test void importAndMoveAcceptStructuresOnlyInsideDataRoots() {
		assertTrue(AssetWorkspaceService.isDataAsset("src/main/resources/data/trail/structure/hut.nbt"));
		assertTrue(AssetWorkspaceService.isDataAsset("src/main/data/trail/structure/hut.NBT"));
		assertFalse(AssetWorkspaceService.isDataAsset("src/main/resources/data/trail/recipe/lamp.json"));
		assertFalse(AssetWorkspaceService.isDataAsset("data/trail/structure/hut.nbt"));
	}

	private void write(String relative) throws Exception {
		Path file = root.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.write(file, new byte[] { 0x1f, (byte) 0x8b, 8, 0 });
	}
}
