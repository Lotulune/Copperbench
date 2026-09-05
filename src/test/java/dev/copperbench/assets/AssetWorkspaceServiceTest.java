package dev.copperbench.assets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetWorkspaceServiceTest {
	@TempDir Path temp;
	private Path workspace;

	@BeforeEach
	void fixture() throws IOException {
		workspace = temp.resolve("workspace");
		Path model = workspace.resolve("assets/copperbench/models/copper_lamp.json");
		Path texture = workspace.resolve("assets/copperbench/textures/block/copper_lamp.png");
		Path language = workspace.resolve("assets/copperbench/lang/en_us.json");
		Files.createDirectories(model.getParent());
		Files.createDirectories(texture.getParent());
		Files.createDirectories(language.getParent());
		Files.writeString(model, "{\"textures\":{\"all\":\"copperbench:textures/block/copper_lamp\"},"
				+ "\"missing\":\"copperbench:textures/block/missing_lamp\"}");
		Files.write(texture, new byte[] { 0, 1, 2, 3, 4 });
		Files.writeString(language, "{\"block.copperbench.copper_lamp\":\"Copper Lamp\"}");
	}

	@Test
	void scansStableDescriptorsAndCategorizesFixtures() {
		AssetWorkspaceService first = new AssetWorkspaceService(workspace);
		AssetWorkspaceService second = new AssetWorkspaceService(workspace);

		List<AssetDescriptor> assets = first.list();
		assertEquals(List.of("assets/copperbench/lang/en_us.json", "assets/copperbench/models/copper_lamp.json",
				"assets/copperbench/textures/block/copper_lamp.png"),
				assets.stream().map(AssetDescriptor::relativePath).toList());
		assertEquals(AssetCategory.LANGUAGE, assets.get(0).category());
		assertEquals(AssetCategory.MODEL, assets.get(1).category());
		assertEquals(AssetCategory.TEXTURE, assets.get(2).category());
		for (AssetDescriptor asset : assets) {
			assertTrue(asset.id().matches("asset:[0-9a-f]{64}"));
			assertEquals(asset.id(), second.findById(asset.id()).orElseThrow().id());
			assertEquals(64, asset.sha256().length());
		}
	}

	@Test
	void searchesByPathAndCategoryInDeterministicOrder() {
		AssetWorkspaceService service = new AssetWorkspaceService(workspace);

		assertEquals(1, service.search("lamp", AssetCategory.MODEL).size());
		assertEquals("assets/copperbench/textures/block/copper_lamp.png",
				service.search("copper", AssetCategory.TEXTURE).getFirst().relativePath());
		assertEquals(3, service.search("", null).size());
		assertTrue(service.search("missing", null).isEmpty());
	}

	@Test
	void buildsReferencesAndReportsMissingTargets() {
		AssetReferenceGraph graph = new AssetWorkspaceService(workspace).referenceGraph();

		assertEquals(1, graph.references().size());
		AssetReference reference = graph.references().getFirst();
		assertEquals("assets/copperbench/models/copper_lamp.json", reference.sourcePath());
		assertEquals("assets/copperbench/textures/block/copper_lamp.png", reference.targetPath());
		assertNotNull(reference.targetAssetId());
		assertTrue(graph.diagnostics().stream().anyMatch(diagnostic ->
				diagnostic.code().equals("MISSING_ASSET_REFERENCE")
						&& diagnostic.targetPath().endsWith("missing_lamp.png")));
	}

	@Test
	void derivesReverseUsageAndHealthWithoutTreatingStaticOrphansAsErrors() {
		AssetReferenceGraph graph = new AssetWorkspaceService(workspace).referenceGraph();
		AssetHealthReport health = graph.healthReport();

		assertEquals(1, graph.incoming("assets/copperbench/textures/block/copper_lamp.png").size());
		AssetHealthReport.Entry texture = health.entries().stream()
				.filter(entry -> entry.relativePath().endsWith("textures/block/copper_lamp.png"))
				.findFirst().orElseThrow();
		assertEquals(1, texture.inboundCount());
		assertEquals(AssetHealthReport.Status.READY, texture.status());

		AssetHealthReport.Entry model = health.entries().stream()
				.filter(entry -> entry.relativePath().endsWith("models/copper_lamp.json"))
				.findFirst().orElseThrow();
		assertTrue(model.unused());
		assertTrue(model.usageAssessed());
		assertEquals(AssetHealthReport.Status.ERROR, model.status());
		assertTrue(model.issueCodes().contains("MISSING_ASSET_REFERENCE"));

		AssetHealthReport.Entry language = health.entries().stream()
				.filter(entry -> entry.relativePath().endsWith("lang/en_us.json"))
				.findFirst().orElseThrow();
		assertFalse(language.usageAssessed());
		assertFalse(language.unused());
		assertEquals(AssetHealthReport.Status.READY, language.status());

		assertEquals(3, health.summary().totalAssets());
		assertEquals(1, health.summary().errorAssets());
		assertEquals(0, health.summary().warningAssets());
		assertEquals(1, health.summary().unusedAssets());
		assertEquals(1, health.summary().missingReferences());
	}

	@Test
	void rejectsAbsoluteTraversalAndMissingAuthorizedPaths() {
		AssetWorkspaceService service = new AssetWorkspaceService(workspace);
		assertTrue(Files.isRegularFile(service.resolveAuthorizedPath("assets/copperbench/models/copper_lamp.json")));
		assertThrows(AssetPathViolationException.class, () -> service.resolveAuthorizedPath("../outside.txt"));
		assertThrows(AssetPathViolationException.class, () -> service.resolveAuthorizedPath(workspace.toString()));
		assertThrows(AssetPathViolationException.class,
				() -> service.resolveAuthorizedPath("assets/copperbench/models/unknown.json"));
	}

	@Test
	void detectsExactDuplicateContentWithinTheSameMediaType() throws IOException {
		Path original = workspace.resolve("assets/copperbench/textures/block/copper_lamp.png");
		Path duplicate = workspace.resolve("assets/copperbench/textures/block/copper_lamp_copy.png");
		Files.copy(original, duplicate);

		AssetHealthReport report = new AssetWorkspaceService(workspace).referenceGraph().healthReport();
		AssetHealthReport.Entry originalHealth = report.entries().stream()
				.filter(entry -> entry.relativePath().endsWith("copper_lamp.png")).findFirst().orElseThrow();
		AssetHealthReport.Entry duplicateHealth = report.entries().stream()
				.filter(entry -> entry.relativePath().endsWith("copper_lamp_copy.png")).findFirst().orElseThrow();

		assertTrue(originalHealth.duplicateContent());
		assertTrue(duplicateHealth.duplicateContent());
		assertEquals(List.of("assets/copperbench/textures/block/copper_lamp_copy.png"),
				originalHealth.duplicatePaths());
		assertTrue(originalHealth.issueCodes().contains("DUPLICATE_ASSET_CONTENT"));
		assertEquals(AssetHealthReport.Status.WARNING, originalHealth.status());
		assertEquals(2, report.summary().duplicateAssets());
		assertEquals(1, report.summary().duplicateGroups());
	}

	@Test
	void invalidJsonIsAStableDiagnosticInsteadOfAServiceFailure() throws IOException {
		Files.writeString(workspace.resolve("assets/copperbench/models/broken.json"), "{broken");
		AssetReferenceGraph graph = new AssetWorkspaceService(workspace).referenceGraph();
		assertTrue(graph.diagnostics().stream().anyMatch(diagnostic ->
			diagnostic.code().equals("INVALID_ASSET_DOCUMENT")));
	}

	@Test
	void referenceTraversalIsReportedAsPathEscape() throws IOException {
		Files.writeString(workspace.resolve("assets/copperbench/models/escape.json"),
				"{\"texture\":\"../outside.png\"}");
		AssetReferenceGraph graph = new AssetWorkspaceService(workspace).referenceGraph();
		assertTrue(graph.diagnostics().stream().anyMatch(diagnostic ->
			diagnostic.code().equals("REFERENCE_PATH_ESCAPE")
					&& diagnostic.sourcePath().endsWith("escape.json")));
	}

	@Test
	void resolvesMinecraftResourceIdsUsingModelFieldSemantics() throws IOException {
		Path model = workspace.resolve("assets/copperbench/models/semantic.json");
		Path parent = workspace.resolve("assets/copperbench/models/block/cube_all.json");
		Files.createDirectories(parent.getParent());
		Files.writeString(parent, "{}");
		Files.writeString(model, "{\"parent\":\"copperbench:block/cube_all\",\"textures\":{"
				+ "\"all\":\"copperbench:block/copper_lamp\"}}");

		List<AssetReference> outgoing = new AssetWorkspaceService(workspace).referenceGraph()
				.outgoing("assets/copperbench/models/semantic.json");
		assertEquals(List.of("assets/copperbench/models/block/cube_all.json",
				"assets/copperbench/textures/block/copper_lamp.png"),
				outgoing.stream().map(AssetReference::targetPath).sorted().toList());
	}

	@Test
	void resolvesGeneratedResourceRootsAndIgnoresBuildAndSourceTrees() throws IOException {
		Path model = workspace.resolve("src/main/resources/assets/copperbench/models/block/generated.json");
		Path texture = workspace.resolve("src/main/resources/assets/copperbench/textures/block/generated.png");
		Path buildDocument = workspace.resolve("build/generated/broken.json");
		Path javaSource = workspace.resolve("src/main/java/dev/copperbench/Generated.java");
		Files.createDirectories(model.getParent());
		Files.createDirectories(texture.getParent());
		Files.createDirectories(buildDocument.getParent());
		Files.createDirectories(javaSource.getParent());
		Files.writeString(model, "{\"textures\":{\"all\":\"copperbench:block/generated\"}}");
		Files.write(texture, new byte[] { 5, 6, 7 });
		Files.writeString(buildDocument, "{broken");
		Files.writeString(javaSource, "class Generated {}");

		AssetReferenceGraph graph = new AssetWorkspaceService(workspace).referenceGraph();

		assertTrue(graph.assets().stream().anyMatch(asset -> asset.relativePath().equals(
				"src/main/resources/assets/copperbench/models/block/generated.json")
				&& asset.updatedAt() != null));
		assertTrue(graph.references().stream().anyMatch(reference -> reference.targetPath().equals(
				"src/main/resources/assets/copperbench/textures/block/generated.png")));
		assertFalse(graph.assets().stream().anyMatch(asset -> asset.relativePath().startsWith("build/")
				|| asset.relativePath().startsWith("src/main/java/")));
		assertFalse(graph.diagnostics().stream().anyMatch(diagnostic -> diagnostic.sourcePath().startsWith("build/")));
	}

	@Test void indexesLegacyLangAssetsWithConsistentMediaType() throws IOException {
		Path legacy = workspace.resolve("assets/copperbench/lang/legacy.lang");
		Files.writeString(legacy, "tile.copperbench.lamp=Copper Lamp");

		AssetDescriptor descriptor = new AssetWorkspaceService(workspace).findByRelativePath(
				"assets/copperbench/lang/legacy.lang").orElseThrow();
		assertEquals(AssetCategory.LANGUAGE, descriptor.category());
		assertEquals("text/plain", descriptor.mediaType());
	}
}
