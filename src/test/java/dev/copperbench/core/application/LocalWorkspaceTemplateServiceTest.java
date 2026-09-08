package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalWorkspaceTemplateServiceTest {

	@TempDir Path temporaryDirectory;

	@Test void reusableBundleIsIntegrityCheckedAndRemapsContainedElementIds() throws Exception {
		Path templateRoot = temporaryDirectory.resolve("templates");
		Path workspaceRoot = temporaryDirectory.resolve("source");
		String assetPath = "src/main/resources/assets/demo/textures/item/reusable.bin";
		byte[] assetBytes = new byte[] { 3, 1, 4, 1, 5, 9 };
		Path asset = workspaceRoot.resolve(assetPath);
		Files.createDirectories(asset.getParent());
		Files.write(asset, assetBytes);

		UUID itemId = UUID.fromString("11111111-1111-4111-8111-111111111101");
		UUID procedureId = UUID.fromString("11111111-1111-4111-8111-111111111102");
		JsonObject itemValues = new JsonObject();
		itemValues.addProperty("displayName", "Reusable Item");
		itemValues.addProperty("procedureRef", procedureId.toString());
		JsonObject procedureValues = new JsonObject();
		procedureValues.addProperty("displayName", "Reusable Procedure");
		procedureValues.addProperty("ownerRef", itemId.toString());

		WorkspaceState source = state("fabric-1.21.1", List.of(
				new Element(itemId, "item", "alpha_item", "Reusable Item", "valid", "generated",
						Instant.parse("2026-09-08T00:00:00Z"), itemValues),
				new Element(procedureId, "procedure", "beta_procedure", "Reusable Procedure", "valid", "generated",
						Instant.parse("2026-09-08T00:00:00Z"), procedureValues)));

		LocalWorkspaceTemplateService service = new LocalWorkspaceTemplateService(templateRoot,
				Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));
		JsonObject create = new JsonObject();
		create.addProperty("templateName", "combat_bundle");
		create.addProperty("description", "Two reusable elements plus one asset");
		JsonArray elementIds = new JsonArray();
		elementIds.add(itemId.toString());
		elementIds.add(procedureId.toString());
		create.add("elementIds", elementIds);
		JsonArray assetPaths = new JsonArray();
		assetPaths.add(assetPath);
		create.add("assetPaths", assetPaths);

		JsonObject metadata = service.create(source, workspaceRoot, create, new JsonObject());
		assertEquals(2, metadata.get("elementCount").getAsInt());
		assertEquals(1, metadata.get("assetCount").getAsInt());
		assertEquals(1, service.list().get("templateCount").getAsInt());

		UUID remappedItem = UUID.fromString("22222222-2222-4222-8222-222222222201");
		UUID remappedProcedure = UUID.fromString("22222222-2222-4222-8222-222222222202");
		ArrayDeque<UUID> plannedIds = new ArrayDeque<>(List.of(remappedItem, remappedProcedure));
		LocalWorkspaceTemplateService.PreparedInstantiation prepared = service.prepareInstantiation("combat_bundle",
				state("fabric-1.21.1", List.of()), plannedIds::removeFirst);

		assertEquals(2, prepared.operations().size());
		JsonObject first = prepared.operations().get(0).getAsJsonObject();
		JsonObject second = prepared.operations().get(1).getAsJsonObject();
		assertEquals(remappedItem.toString(), first.get("plannedId").getAsString());
		assertEquals(remappedProcedure.toString(), second.get("plannedId").getAsString());
		assertEquals(remappedProcedure.toString(), first.getAsJsonObject("payload")
				.getAsJsonObject("initialValues").get("procedureRef").getAsString());
		assertEquals(remappedItem.toString(), second.getAsJsonObject("payload")
				.getAsJsonObject("initialValues").get("ownerRef").getAsString());
		assertEquals(assetPath, prepared.artifacts().getFirst().relativePath());
		assertArrayEquals(assetBytes, prepared.artifacts().getFirst().content());

		Path stored = templateRoot.resolve("combat_bundle.json");
		JsonObject tampered = JsonParser.parseString(Files.readString(stored, StandardCharsets.UTF_8)).getAsJsonObject();
		tampered.addProperty("description", "tampered without re-signing");
		Files.writeString(stored, tampered.toString(), StandardCharsets.UTF_8);
		assertThrows(IllegalArgumentException.class, () -> service.load("combat_bundle"));
	}

	private static WorkspaceState state(String generatorId, List<Element> elements) {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", generatorId);
		return new WorkspaceState(UUID.randomUUID(), "template-test", "mcreator", 0, false,
				generator, new JsonObject(), elements);
	}
}
