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

	@Test void caseAliasesKeepGeneratedFileAndDirectoryOwnership() throws Exception {
		write("src/main/java/example/Generated.java", "class Generated {}");
		write("src/main/resources/assets/example/lang/en_us.json", "{}");
		String alias = "src/main/java/example/generated.java";
		assumeTrue(Files.exists(root.resolve(alias)) && Files.isSameFile(root.resolve(alias), root.resolve("src/main/java/example/Generated.java")),
				"Requires a case-insensitive filesystem");
		WorkspaceSourceService service = new WorkspaceSourceService(root, Map.of(
				"src/main/java/example/Generated.java", "generated", "src/main/resources/assets/example/lang/", "generated"), true);
		for (String path : new String[]{alias, "src/main/resources/assets/example/LANG/en_us.json"}) {
			JsonObject read = service.read(path);
			assertFalse(read.get("editable").getAsBoolean(), path);
			assertEquals("WORKSPACE_SOURCE_READ_ONLY", assertThrows(WorkspaceSourceService.SourceFailure.class,
					() -> service.prepare(path, "overwritten", read.get("sha256").getAsString())).code);
		}
		assertEquals("class Generated {}", Files.readString(root.resolve(alias)));
	}

	@Test void distinctCaseSensitiveFilesDoNotShareOwnership() throws Exception {
		write("src/main/java/Generated.java", "class Generated {}");
		assumeTrue(!Files.exists(root.resolve("src/main/java/generated.java")), "Requires a case-sensitive filesystem");
		write("src/main/java/generated.java", "class generated {}");
		WorkspaceSourceService service = new WorkspaceSourceService(root, Map.of("src/main/java/Generated.java", "generated"), true);
		assertTrue(service.read("src/main/java/generated.java").get("editable").getAsBoolean());
	}

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

	@Test void createsHandWrittenFilesWithMissingParentsButNeverReplacesOrClaimsGeneratedPaths() throws Exception {
		write("src/main/java/example/Existing.java", "class Existing {}");
		WorkspaceSourceService service = new WorkspaceSourceService(root, Map.of(
				"src/main/java/example/Generated.java", "generated", "src/main/resources/assets/example/lang/", "generated"), true);
		String path = "src/main/java/example/behavior/EmberWand.java";
		WorkspaceSourceService.Edit edit = service.prepareCreate(path, "class EmberWand {}\n");
		assertTrue(edit.creates());
		assertTrue(edit.changed());
		boolean[] committed = { false };
		service.apply(edit, () -> committed[0] = true);
		assertTrue(committed[0]);
		assertEquals("class EmberWand {}\n", Files.readString(root.resolve(path)));
		JsonObject read = service.read(path);
		assertEquals("manual", read.get("ownership").getAsString());
		assertEquals(edit.result().get("sha256").getAsString(), read.get("sha256").getAsString());
		WorkspaceSourceService.Edit empty = service.prepareCreate("src/main/resources/data/example/tags/item/empty.json", "");
		service.apply(empty, () -> { });
		assertEquals(0, Files.size(root.resolve("src/main/resources/data/example/tags/item/empty.json")));
		service.apply(service.prepareCreate("copperbench-tests.json", "{\"schemaVersion\":\"1.0\"}"), () -> { });
		assertEquals("manual", service.read("copperbench-tests.json").get("ownership").getAsString(),
				"the GameTest configuration is hand-maintained alongside src/gametest");

		for (String existing : new String[]{"src/main/java/example/Existing.java", path})
			assertEquals("WORKSPACE_SOURCE_CONFLICT", assertThrows(WorkspaceSourceService.SourceFailure.class,
					() -> service.prepareCreate(existing, "replaced")).code, existing);
		assertEquals("class Existing {}", Files.readString(root.resolve("src/main/java/example/Existing.java")));
		for (String owned : new String[]{"src/main/java/example/Generated.java", "src/main/resources/assets/example/lang/en_us.json"})
			assertEquals("WORKSPACE_SOURCE_READ_ONLY", assertThrows(WorkspaceSourceService.SourceFailure.class,
					() -> service.prepareCreate(owned, "{}")).code, owned);
		for (String invalid : new String[]{"../outside.java", "build/Out.java", "src/main/resources/assets/example/textures/item/a.png",
				"src/main/java/example/Existing.java/Nested.java", "src/.hidden/Entry.java"})
			assertEquals("WORKSPACE_SOURCE_PATH_INVALID", assertThrows(WorkspaceSourceService.SourceFailure.class,
					() -> service.prepareCreate(invalid, "x")).code, invalid);
		assertEquals("WORKSPACE_SOURCE_READ_ONLY", assertThrows(WorkspaceSourceService.SourceFailure.class,
				() -> new WorkspaceSourceService(root, Map.of(), false).prepareCreate("src/main/java/ReadOnly.java", "x")).code);
		assertFalse(Files.exists(root.resolve("src/main/java/ReadOnly.java")));
	}

	@Test void generatedJavaAcceptsChangesOnlyInsideUserCodeBlocks() throws Exception {
		String path = "src/main/java/example/ExampleMod.java";
		String generated = """
				public class ExampleMod {
					public void onInitialize() {
						// Start of user code block mod init
						// End of user code block mod init
					}
				}
				""".replace("\n", "\r\n");
		write(path, generated);
		write("src/main/java/example/NoBlocks.java", "class NoBlocks {}");
		WorkspaceSourceService service = new WorkspaceSourceService(root, Map.of(path, "generated",
				"src/main/java/example/NoBlocks.java", "generated"), true);
		JsonObject read = service.read(path);
		assertFalse(read.get("editable").getAsBoolean(), "the file as a whole stays generator-owned");
		assertEquals("[\"mod init\"]", read.getAsJsonArray("userCodeBlocks").toString());

		String hooked = generated.replace("// Start of user code block mod init\r\n",
				"// Start of user code block mod init\r\n\t\tmy.content.Content.register();\r\n");
		WorkspaceSourceService.Edit edit = service.prepare(path, hooked, read.get("sha256").getAsString());
		service.apply(edit, () -> { });
		assertEquals(hooked, Files.readString(root.resolve(path)));

		String sha = service.read(path).get("sha256").getAsString();
		for (String outside : new String[]{hooked.replace("onInitialize", "onInit"), hooked.replace("\r\n", "\n"),
				hooked.replace("// End of user code block mod init", "// End of user code block other"),
				hooked + "// Start of user code block extra\r\n// End of user code block extra\r\n"})
			assertEquals("WORKSPACE_SOURCE_READ_ONLY", assertThrows(WorkspaceSourceService.SourceFailure.class,
					() -> service.prepare(path, outside, sha)).code);
		String noBlocksSha = service.read("src/main/java/example/NoBlocks.java").get("sha256").getAsString();
		assertFalse(service.read("src/main/java/example/NoBlocks.java").has("userCodeBlocks"));
		assertEquals("WORKSPACE_SOURCE_READ_ONLY", assertThrows(WorkspaceSourceService.SourceFailure.class,
				() -> service.prepare("src/main/java/example/NoBlocks.java", "class NoBlocks {}", noBlocksSha)).code);
		WorkspaceSourceService readOnly = new WorkspaceSourceService(root, Map.of(path, "generated"), false);
		assertEquals("WORKSPACE_SOURCE_READ_ONLY", assertThrows(WorkspaceSourceService.SourceFailure.class,
				() -> readOnly.prepare(path, hooked.replace("register()", "register(); "), sha)).code);
		assertEquals(hooked, Files.readString(root.resolve(path)));
	}

	@Test void failedCreationRemovesOnlyItsOwnFileAndDirectoriesAndRacesBecomeConflicts() throws Exception {
		write("src/main/java/Keep.java", "keep");
		WorkspaceSourceService service = new WorkspaceSourceService(root, Map.of(), true);
		String path = "src/main/java/example/deep/Created.java";
		WorkspaceSourceService.Edit edit = service.prepareCreate(path, "created");
		assertThrows(IOException.class, () -> service.apply(edit, () -> { throw new IOException("disk metadata failed"); }));
		assertFalse(Files.exists(root.resolve("src/main/java/example")), "directories created for the failed file are removed");
		assertTrue(Files.exists(root.resolve("src/main/java/Keep.java")));

		assertThrows(IOException.class, () -> service.apply(edit, () -> {
			Files.writeString(root.resolve(path), "external"); throw new IOException("disk metadata failed");
		}));
		assertEquals("external", Files.readString(root.resolve(path)), "rollback must not delete concurrent content");

		String raced = "src/main/java/Raced.java";
		WorkspaceSourceService.Edit stale = service.prepareCreate(raced, "mine");
		write(raced, "theirs");
		assertEquals("WORKSPACE_SOURCE_CONFLICT", assertThrows(WorkspaceSourceService.SourceFailure.class,
				() -> service.apply(stale, () -> fail("stale creation committed"))).code);
		assertEquals("theirs", Files.readString(root.resolve(raced)));
	}

	@Test void creationCannotFollowALinkedParentDirectory() throws Exception {
		Path outside = Files.createTempDirectory(root.getParent(), "source-outside-dir-");
		try {
			Files.createDirectories(root.resolve("src/main/java"));
			try { Files.createSymbolicLink(root.resolve("src/main/java/linked"), outside); }
			catch (IOException | UnsupportedOperationException exception) { assumeTrue(false, "Host cannot create symlinks: " + exception.getMessage()); }
			assertEquals("WORKSPACE_SOURCE_PATH_INVALID", assertThrows(WorkspaceSourceService.SourceFailure.class,
					() -> new WorkspaceSourceService(root, Map.of(), true).prepareCreate("src/main/java/linked/Escape.java", "x")).code);
			assertFalse(Files.exists(outside.resolve("Escape.java")));
		} finally { Files.deleteIfExists(outside); }
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
