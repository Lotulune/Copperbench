package dev.copperbench.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

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

	@Test void rejectsOutputWithLinkedAncestorBeforeCreatingMissingParents() throws Exception {
		Path workspace = resourcePackWorkspace();
		Path outside = Files.createDirectory(temp.resolve("outside"));
		createDirectoryLink(workspace.resolve("exports"), outside);
		var service = new ResourcePackExportService(new AssetWorkspaceService(workspace));

		assertThrows(AssetPathViolationException.class,
				() -> service.export("resource-pack", "exports/not-created/pack.zip"));

		assertFalse(Files.exists(outside.resolve("not-created")));
	}

	@Test void doesNotOpenAPreexistingPredictableTemporaryFileLink() throws Exception {
		Path workspace = resourcePackWorkspace();
		Path exports = Files.createDirectory(workspace.resolve("exports"));
		Path externalDocument = Files.writeString(temp.resolve("user-document.txt"), "keep user content");
		Path oldTemporary = exports.resolve("pack.zip.tmp");
		createFileLink(oldTemporary, externalDocument);
		var service = new ResourcePackExportService(new AssetWorkspaceService(workspace));

		service.export("resource-pack", "exports/pack.zip");

		assertEquals("keep user content", Files.readString(externalDocument));
		assertTrue(Files.isSymbolicLink(oldTemporary), "Export must leave unrelated existing files alone");
		try (ZipFile zip = new ZipFile(exports.resolve("pack.zip").toFile())) {
			assertTrue(zip.getEntry("pack.mcmeta") != null);
		}
		try (var files = Files.list(exports)) {
			assertEquals(List.of("pack.zip", "pack.zip.tmp"),
					files.map(path -> path.getFileName().toString()).sorted().toList());
		}
	}

	@Test void rejectsAnOutputFileLinkWithoutChangingItsTarget() throws Exception {
		Path workspace = resourcePackWorkspace();
		Path externalDocument = Files.writeString(temp.resolve("user-document.txt"), "keep user content");
		Path output = workspace.resolve("pack.zip");
		createFileLink(output, externalDocument);
		var service = new ResourcePackExportService(new AssetWorkspaceService(workspace));

		assertThrows(AssetPathViolationException.class, () -> service.export("resource-pack", "pack.zip"));

		assertEquals("keep user content", Files.readString(externalDocument));
		assertTrue(Files.isSymbolicLink(output));
	}

	@Test void rejectsSourceFileLinksBeforePublishingAnArchive() throws Exception {
		Path workspace = resourcePackWorkspace();
		Path externalDocument = Files.writeString(temp.resolve("user-document.txt"), "keep user content");
		createFileLink(workspace.resolve("resource-pack/external.txt"), externalDocument);
		var service = new ResourcePackExportService(new AssetWorkspaceService(workspace));

		assertThrows(AssetPathViolationException.class, () -> service.export("resource-pack", "exports/pack.zip"));

		assertFalse(Files.exists(workspace.resolve("exports")));
		assertEquals("keep user content", Files.readString(externalDocument));
	}

	@Test void rejectsRedirectedSourceDirectories() throws Exception {
		Path workspace = resourcePackWorkspace();
		Path outside = Files.createDirectory(temp.resolve("outside"));
		Files.writeString(outside.resolve("user-document.txt"), "keep user content");
		createDirectoryLink(workspace.resolve("resource-pack/external"), outside);
		var service = new ResourcePackExportService(new AssetWorkspaceService(workspace));

		assertThrows(AssetPathViolationException.class, () -> service.export("resource-pack", "exports/pack.zip"));

		assertFalse(Files.exists(workspace.resolve("exports")));
	}

	@Test void rejectsDanglingOutputAncestors() throws Exception {
		Path workspace = resourcePackWorkspace();
		Path missing = temp.resolve("missing-external-directory");
		createFileLink(workspace.resolve("exports"), missing);
		var service = new ResourcePackExportService(new AssetWorkspaceService(workspace));

		assertThrows(AssetPathViolationException.class,
				() -> service.export("resource-pack", "exports/nested/pack.zip"));

		assertFalse(Files.exists(missing, LinkOption.NOFOLLOW_LINKS));
	}

	@Test void failedPublicationCleansTemporaryFilesAndPreservesExistingOutput() throws Exception {
		Path workspace = resourcePackWorkspace();
		Path exports = Files.createDirectory(workspace.resolve("exports"));
		Path blockedOutput = Files.createDirectory(exports.resolve("pack.zip"));
		Files.writeString(blockedOutput.resolve("keep.txt"), "keep user content");
		var service = new ResourcePackExportService(new AssetWorkspaceService(workspace));

		assertThrows(AssetPathViolationException.class, () -> service.export("resource-pack", "exports/pack.zip"));

		assertEquals("keep user content", Files.readString(blockedOutput.resolve("keep.txt")));
		try (var files = Files.list(exports)) {
			assertEquals(List.of("pack.zip"), files.map(path -> path.getFileName().toString()).toList());
		}
	}

	@Test void replacesAnExistingArchiveWithACompleteNewArchive() throws Exception {
		Path workspace = resourcePackWorkspace();
		var service = new ResourcePackExportService(new AssetWorkspaceService(workspace));
		service.export("resource-pack", "exports/pack.zip");
		Files.writeString(workspace.resolve("resource-pack/updated.txt"), "new asset");

		service.export("resource-pack", "exports/pack.zip");

		try (ZipFile zip = new ZipFile(workspace.resolve("exports/pack.zip").toFile())) {
			assertTrue(zip.getEntry("pack.mcmeta") != null);
			assertEquals("new asset", new String(zip.getInputStream(zip.getEntry("updated.txt")).readAllBytes(),
					StandardCharsets.UTF_8));
		}
		try (var files = Files.list(workspace.resolve("exports"))) {
			assertEquals(List.of("pack.zip"), files.map(path -> path.getFileName().toString()).toList());
		}
	}

	private Path resourcePackWorkspace() throws IOException {
		Path workspace = Files.createDirectory(temp.resolve("workspace"));
		Path pack = Files.createDirectory(workspace.resolve("resource-pack"));
		Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":34,\"description\":\"Copper\"}}");
		return workspace;
	}

	private static void createFileLink(Path link, Path target) {
		try {
			Files.createSymbolicLink(link, target);
		} catch (UnsupportedOperationException | IOException exception) {
			abort("File symbolic links are unavailable in this test environment: " + exception.getMessage());
		}
	}

	private static void createDirectoryLink(Path link, Path target) throws Exception {
		if (java.io.File.separatorChar == '\\') {
			Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString())
					.redirectErrorStream(true).start();
			String detail = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			assertEquals(0, process.waitFor(), detail);
		} else {
			Files.createSymbolicLink(link, target);
		}
	}
}
