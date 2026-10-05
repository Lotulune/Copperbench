package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WorkspaceSourceServiceTest {
	@TempDir Path root;

	@Test void listsPagesAndProtectsGeneratorOwnedFilesWithoutPromotingJavaToElements() throws Exception {
		write("src/main/java/example/Entry.java", "class Entry {}");
		write("src/main/java/example/Generated.java", "class Generated {}");
		write("src/build/Hidden.java", "ignored");
		write("build/NotSource.java", "ignored");
		write("src/main/resources/assets/example/models/item/card.json", "{}");
		WorkspaceSourceService service = new WorkspaceSourceService(root, Map.of("src/main/java/example/Generated.java", "generated"), true);
		JsonObject first = service.list(".java", 0, 1);
		assertEquals(2, first.get("total").getAsInt());
		assertEquals(1, first.get("nextOffset").getAsInt());
		assertEquals("manual", first.getAsJsonArray("files").get(0).getAsJsonObject().get("ownership").getAsString());
		JsonObject generated = service.read("src/main/java/example/Generated.java");
		assertFalse(generated.get("editable").getAsBoolean());
		assertEquals("WORKSPACE_SOURCE_READ_ONLY", assertThrows(WorkspaceSourceService.SourceFailure.class,
				() -> service.prepare("src/main/java/example/Generated.java", "edited", generated.get("sha256").getAsString())).code);
		assertTrue(service.list(".java", 1, 1).get("nextOffset").isJsonNull());
	}

	@Test void rejectsTraversalInvalidUtf8OversizeAndLinks() throws Exception {
		WorkspaceSourceService service = new WorkspaceSourceService(root, Map.of(), true);
		write("src/main/java/Valid.java", "valid");
		for (String path : new String[]{"../outside.java", "src/../outside.java", "src\\main\\java\\Valid.java", "C:/outside.java", "src/.hidden.java", "/src/main/java/Valid.java"})
			assertEquals("WORKSPACE_SOURCE_PATH_INVALID", assertThrows(WorkspaceSourceService.SourceFailure.class, () -> service.read(path)).code, path);
		Files.write(root.resolve("src/main/java/Invalid.java"), new byte[]{(byte) 0xc3, 0x28});
		assertEquals("WORKSPACE_SOURCE_ENCODING", assertThrows(WorkspaceSourceService.SourceFailure.class,
				() -> service.read("src/main/java/Invalid.java")).code);
		write("src/main/java/Large.java", "x".repeat(WorkspaceSourceService.MAX_FILE_BYTES + 1));
		assertEquals("WORKSPACE_SOURCE_TOO_LARGE", assertThrows(WorkspaceSourceService.SourceFailure.class,
				() -> service.read("src/main/java/Large.java")).code);
		String sha = service.read("src/main/java/Valid.java").get("sha256").getAsString();
		assertEquals("WORKSPACE_SOURCE_TOO_LARGE", assertThrows(WorkspaceSourceService.SourceFailure.class,
				() -> service.prepare("src/main/java/Valid.java", "汉".repeat(400000), sha)).code);
	}

	@Test void cannotFollowAnExternalSymbolicFile() throws Exception {
		Path outside = Files.createTempFile(root.getParent(), "source-outside-", ".java");
		try {
			write("src/main/java/Original.java", "original");
			try { Files.createSymbolicLink(root.resolve("src/main/java/Link.java"), outside); }
			catch (IOException | UnsupportedOperationException exception) { assumeTrue(false, "Host cannot create symlinks: " + exception.getMessage()); }
			assertEquals("WORKSPACE_SOURCE_PATH_INVALID", assertThrows(WorkspaceSourceService.SourceFailure.class,
					() -> new WorkspaceSourceService(root, Map.of(), true).read("src/main/java/Link.java")).code);
		} finally { Files.deleteIfExists(outside); }
	}

	@Test void hashGuardAndFailedCommitRollbackNeverOverwriteAnExternalEdit() throws Exception {
		String path = "src/main/java/Entry.java";
		write(path, "before\r\n");
		WorkspaceSourceService service = new WorkspaceSourceService(root, Map.of(), true);
		String sha = service.read(path).get("sha256").getAsString();
		WorkspaceSourceService.Edit edit = service.prepare(path, "after\r\n", sha);
		assertThrows(IOException.class, () -> service.apply(edit, () -> { throw new IOException("disk metadata failed"); }));
		assertEquals("before\r\n", Files.readString(root.resolve(path)));
		assertThrows(IOException.class, () -> service.apply(edit, () -> {
			Files.writeString(root.resolve(path), "external"); throw new IOException("disk metadata failed");
		}));
		assertEquals("external", Files.readString(root.resolve(path)), "rollback must not overwrite concurrent content");
		assertEquals("WORKSPACE_SOURCE_CONFLICT", assertThrows(WorkspaceSourceService.SourceFailure.class,
				() -> service.apply(edit, () -> fail("stale edit committed"))).code);
	}

	@Test void discoveryUsesJavaSyntaxAndManifestEvidenceWithStableIds() throws Exception {
		write("src/main/java/example/Entry.java", """
				package example;
				import net.fabricmc.api.ModInitializer;
				import net.minecraft.registry.Registry;
				import net.minecraft.util.Identifier;
				public class Entry implements ModInitializer {
				  // Registry.register(ignored, new Identifier("fake", "comment"), null);
				  public void onInitialize() {
				    String ignored = "Registry.register(alsoFake)";
				    Registry.register(REGISTRY, Identifier.of("example", "real_item"), ITEM);
				  }
				}
				""");
		write("src/main/resources/fabric.mod.json", "{\"entrypoints\":{\"main\":[\"example.Entry\"]}}");
		write("src/main/java/example/Unrelated.java", "class Unrelated { void go() { Registry.register(FAKE); } }");
		WorkspaceSourceService service = new WorkspaceSourceService(root, Map.of(), true);
		JsonObject index = service.index();
		JsonArray entries = index.getAsJsonArray("entries");
		assertEquals(4, entries.size(), entries.toString());
		assertEquals(3, index.get("scannedFiles").getAsInt());
		assertFalse(index.get("truncated").getAsBoolean());
		assertEquals(index, service.index());
		assertTrue(entries.asList().stream().anyMatch(item -> item.getAsJsonObject().has("resourceId")
				&& item.getAsJsonObject().get("resourceId").getAsString().equals("example:real_item")));
		assertTrue(entries.asList().stream().allMatch(item -> item.getAsJsonObject().get("line").getAsInt() >= 1));
		assertFalse(entries.toString().contains("fake:comment"));
	}

	@Test void identicalSameLineEvidenceDoesNotProduceDuplicateUiIds() throws Exception {
		write("src/main/java/example/Entry.java", """
				import net.minecraft.util.Identifier;
				class Entry { void run() { new Identifier("example", "item"); new Identifier("example", "item"); } }
				""");
		JsonArray entries = new WorkspaceSourceService(root, Map.of(), true).index().getAsJsonArray("entries");
		assertEquals(1, entries.size(), entries.toString());
		assertEquals("example:item", entries.get(0).getAsJsonObject().get("resourceId").getAsString());
	}

	@Test void discoveryAndInventoryAreBoundedAndEmptyWorkspaceIsEmpty() throws Exception {
		WorkspaceSourceService service = new WorkspaceSourceService(root, Map.of(), true);
		assertEquals(0, service.list("", 0, 100).get("total").getAsInt());
		assertTrue(service.index().getAsJsonArray("entries").isEmpty());
		String source = "import net.minecraft.util.Identifier; class Many { void init() {" + "Identifier.of(\"example\", \"item\");\n".repeat(350) + "}}";
		write("src/main/java/Many.java", source);
		assertEquals(300, service.index().getAsJsonArray("entries").size());
		assertTrue(service.index().get("truncated").getAsBoolean());
	}

	private void write(String relative, String content) throws IOException {
		Path path = root.resolve(relative); Files.createDirectories(path.getParent()); Files.writeString(path, content, StandardCharsets.UTF_8);
	}
}
