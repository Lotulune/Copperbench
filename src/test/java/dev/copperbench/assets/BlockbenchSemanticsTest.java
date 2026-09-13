package dev.copperbench.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.JGitLocalHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchSemanticsTest {
	@TempDir Path root;
	private static final String MODEL = "assets/blockbench/model.bbmodel";

	@ParameterizedTest @ValueSource(strings = {"signal_lantern", "signal_lantern_lit", "signal_lantern_saved_5_1_6"})
	void realModelsHaveOneEmbeddedTextureAndNoMetadataErrors(String fixture) throws Exception {
		JsonObject model = fixture(fixture);
		var parsed = BbmodelDocument.parse(model);
		assertEquals(1, parsed.textures().size());
		assertEquals(78, parsed.faceBindings().size());
		assertTrue(parsed.issues().isEmpty(), parsed.issues().toString());
		var assets = new AssetWorkspaceService(root);
		assertTrue(assets.referenceGraph().diagnostics().isEmpty());
		var service = new BbmodelRoundTripService(assets);
		var before = service.inspect(MODEL);
		assertEquals(1, before.textureReferences().size());
		assertTrue(service.compare(before, service.inspect(MODEL)).compatible());
		model.getAsJsonArray("textures").remove(0);
		write(model);
		var report = service.compare(before, service.inspect(MODEL));
		assertFalse(report.compatible());
		assertTrue(report.diagnostics().stream().anyMatch(issue -> issue.code().equals("TEXTURE_REFERENCE_DROPPED")));
		assertTrue(report.diagnostics().stream().anyMatch(issue -> issue.code().equals("BBMODEL_FACE_TEXTURE_UNRESOLVED")));
		assertTrue(report.handlingOptions().contains("accept_intentional_changes"));
		assertEquals(0, JsonParser.parseString(Files.readString(root.resolve(MODEL))).getAsJsonObject().getAsJsonArray("textures").size());
	}

	@Test void uuidBindingsAndReorderedTextureIndicesRetainIdentity() throws Exception {
		JsonObject model = fixture("signal_lantern");
		JsonObject first = model.getAsJsonArray("textures").get(0).getAsJsonObject();
		JsonObject second = first.deepCopy(); second.addProperty("uuid", "second-texture"); second.addProperty("id", "1");
		model.getAsJsonArray("textures").add(second);
		write(model);
		var service = new BbmodelRoundTripService(new AssetWorkspaceService(root));
		var before = service.inspect(MODEL);
		JsonArray reordered = new JsonArray(); reordered.add(second); reordered.add(first); model.add("textures", reordered);
		model.getAsJsonArray("elements").forEach(element -> element.getAsJsonObject().getAsJsonObject("faces").entrySet()
				.forEach(face -> face.getValue().getAsJsonObject().addProperty("texture", 1)));
		write(model);
		assertTrue(service.compare(before, service.inspect(MODEL)).compatible());
		model.getAsJsonArray("elements").forEach(element -> element.getAsJsonObject().getAsJsonObject("faces").entrySet()
				.forEach(face -> face.getValue().getAsJsonObject().addProperty("texture", first.get("uuid").getAsString())));
		write(model);
		assertTrue(service.compare(before, service.inspect(MODEL)).compatible());
		model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north").addProperty("texture", 0);
		write(model);
		assertTrue(service.compare(before, service.inspect(MODEL)).diagnostics().stream()
				.anyMatch(issue -> issue.code().equals("FACE_TEXTURE_BINDING_CHANGED")));
	}

	@ParameterizedTest @ValueSource(strings = {"remove", "corrupt", "replace", "unbind", "delete_element", "malformed_table"})
	void semanticLossOrEditsCannotBeReportedAsUnchanged(String mutation) throws Exception {
		JsonObject model = fixture("signal_lantern");
		var service = new BbmodelRoundTripService(new AssetWorkspaceService(root));
		var before = service.inspect(MODEL);
		JsonObject texture = model.getAsJsonArray("textures").get(0).getAsJsonObject();
		switch (mutation) {
			case "remove" -> texture.remove("source");
			case "corrupt" -> texture.addProperty("source", "data:image/png;base64,bm90IGFuIGltYWdl");
			case "replace" -> {
				try (var input = getClass().getResourceAsStream("/assets/blockbench/signal_lantern_lit.bbmodel")) {
					texture.add("source", JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
							.getAsJsonObject().getAsJsonArray("textures").get(0).getAsJsonObject().get("source"));
				}
			}
			case "unbind" -> model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north").add("texture", null);
			case "delete_element" -> model.getAsJsonArray("elements").remove(0);
			case "malformed_table" -> model.addProperty("textures", "unsupported");
		}
		write(model);
		assertFalse(service.compare(before, service.inspect(MODEL)).compatible(), mutation);
	}

	@Test void localFilesFallbacksAndExternalNamespacesHaveDifferentDiagnostics() throws Exception {
		JsonObject model = fixture("signal_lantern");
		JsonObject texture = model.getAsJsonArray("textures").get(0).getAsJsonObject();
		byte[] png = Base64.getDecoder().decode(texture.get("source").getAsString().split(",", 2)[1]);
		texture.addProperty("relative_path", "../textures/lantern image.png");
		Path image = root.resolve("assets/textures/lantern image.png"); Files.createDirectories(image.getParent()); Files.write(image, png);
		texture.addProperty("path", root.resolveSibling("not-in-workspace.png").toString());
		write(model);
		var assets = new AssetWorkspaceService(root);
		var graph = assets.referenceGraph();
		assertEquals("/textures/0/relative_path", graph.references().getFirst().sourcePointer());
		assertTrue(graph.diagnostics().stream().allMatch(issue -> issue.severity() == AssetDiagnostic.Severity.INFO));
		texture.remove("source"); texture.remove("path"); write(model);
		var service = new BbmodelRoundTripService(assets); var before = service.inspect(MODEL);
		Files.delete(image);
		assertFalse(service.compare(before, service.inspect(MODEL)).compatible());
		assertTrue(assets.referenceGraph().diagnostics().stream().anyMatch(issue -> issue.code().equals("MISSING_ASSET_REFERENCE")));
		texture.remove("relative_path"); texture.addProperty("path", "minecraft:block/stone"); write(model);
		assertTrue(assets.referenceGraph().diagnostics().stream().allMatch(issue -> issue.code().equals("EXTERNAL_ASSET_REFERENCE")));
		texture.addProperty("path", "blockbench:textures/missing"); write(model);
		assertTrue(assets.referenceGraph().diagnostics().stream().anyMatch(issue -> issue.code().equals("MISSING_ASSET_REFERENCE")));
		texture.addProperty("path", "https://example.com/texture.png"); write(model);
		assertTrue(assets.referenceGraph().diagnostics().stream().allMatch(issue -> issue.code().equals("EXTERNAL_ASSET_REFERENCE")));
	}

	@Test void ambiguousIdsInvalidSourcesAndUnresolvedUuidBindingsRemainVisible() throws Exception {
		JsonObject model = fixture("signal_lantern");
		JsonObject texture = model.getAsJsonArray("textures").get(0).getAsJsonObject();
		JsonObject duplicate = texture.deepCopy(); duplicate.addProperty("uuid", "another-uuid");
		model.getAsJsonArray("textures").add(duplicate);
		assertTrue(BbmodelDocument.parse(model).issues().stream().anyMatch(issue -> issue.code().equals("BBMODEL_TEXTURE_ID_AMBIGUOUS")));
		model.getAsJsonArray("textures").remove(1);
		texture.addProperty("source", 0);
		model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north")
				.addProperty("texture", "missing-uuid");
		var issues = BbmodelDocument.parse(model).issues();
		assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("BBMODEL_TEXTURE_SOURCE_INVALID")));
		assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("BBMODEL_FACE_TEXTURE_UNRESOLVED")));
	}

	@Test void fileReferenceMovesRewriteOnlyTheExactPath() throws Exception {
		JsonObject model = fixture("signal_lantern");
		JsonObject texture = model.getAsJsonArray("textures").get(0).getAsJsonObject();
		texture.addProperty("relative_path", "../textures/old.png"); write(model);
		Path image = root.resolve("assets/textures/old.png"); Files.createDirectories(image.getParent()); Files.write(image, new byte[] {1});
		var assets = new AssetWorkspaceService(root);
		try (var history = JGitLocalHistoryService.open(root, Clock.systemUTC())) {
			var moves = new AssetMoveService(assets, history);
			var plan = moves.preview(assets.findByRelativePath("assets/textures/old.png").orElseThrow().id(), "assets/textures/new.png");
			assertTrue(plan.canApply(), plan.toString());
			moves.apply(plan, Actor.UI, "bbmodel-move");
			var updated = JsonParser.parseString(Files.readString(root.resolve(MODEL))).getAsJsonObject();
			assertEquals("../textures/new.png", updated.getAsJsonArray("textures").get(0).getAsJsonObject().get("relative_path").getAsString());
			assertEquals(texture.get("uuid"), updated.getAsJsonArray("textures").get(0).getAsJsonObject().get("uuid"));
			assertEquals(1, assets.referenceGraph().references().size());
			var moveModel = moves.preview(assets.findByRelativePath(MODEL).orElseThrow().id(), "assets/blockbench/nested/model.bbmodel");
			assertTrue(moveModel.canApply());
			moves.apply(moveModel, Actor.UI, "bbmodel-document-move");
			var reference = assets.referenceGraph().references().getFirst();
			assertEquals("../../textures/new.png", reference.rawValue());
			assertEquals("assets/textures/new.png", reference.targetPath());
		}
	}

	private JsonObject fixture(String name) throws Exception {
		try (var input = getClass().getResourceAsStream("/assets/blockbench/" + name + ".bbmodel")) {
			var model = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			write(model); return model;
		}
	}
	private void write(JsonObject model) throws Exception {
		Files.createDirectories(root.resolve(MODEL).getParent()); Files.writeString(root.resolve(MODEL), model.toString());
	}
}
