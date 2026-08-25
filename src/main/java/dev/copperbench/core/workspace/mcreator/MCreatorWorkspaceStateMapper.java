package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.ProductMetadataManager;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFileManager;
import net.mcreator.workspace.elements.ModElement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Projects an opened upstream workspace into the application service's transaction state. */
public final class MCreatorWorkspaceStateMapper {

	private static final String RESOURCE_PACK_GENERATOR_ID = "resourcepack-1.21.1";

	public WorkspaceState map(Workspace workspace, ProductMetadataManager.Metadata metadata) throws IOException {
		JsonObject document = JsonParser.parseString(Files.readString(
				workspace.getFileManager().getWorkspaceFile().toPath())).getAsJsonObject();
		MCreatorWorkspaceRegistryMapper.projectIntoDocument(workspace, metadata.workspaceId(), document);
		List<Element> elements = new ArrayList<>();
		for (ModElement element : workspace.getModElements())
			elements.add(projectElement(workspace, metadata.workspaceId(), element));
		return new WorkspaceState(metadata.workspaceId(), workspace.getWorkspaceSettings().getModName(), kind(workspace),
				metadata.revision(), workspace.isDirty(), generator(workspace), document, elements);
	}

	private Element projectElement(Workspace workspace, UUID workspaceId, ModElement element) throws IOException {
		UUID id = storedElementId(element);
		if (id == null)
			id = elementId(workspaceId, element);
		Path definitionFile = workspace.getFolderManager().getModElementsDir().toPath()
				.resolve(element.getName() + ".mod.json");
		JsonObject values = storedValues(element);
		if (values == null && Files.isRegularFile(definitionFile)) {
			JsonObject raw = JsonParser.parseString(Files.readString(definitionFile)).getAsJsonObject();
			values = raw.has("definition") && raw.get("definition").isJsonObject()
					? raw.getAsJsonObject("definition").deepCopy() : raw;
		}
		if (values == null)
			values = new JsonObject();
		Instant updatedAt = Files.isRegularFile(definitionFile)
				? Files.getLastModifiedTime(definitionFile).toInstant() : Instant.EPOCH;
		String displayName = values.has("displayName") && values.get("displayName").isJsonPrimitive()
				? values.get("displayName").getAsString() : displayName(element.getName());
		return new Element(id, element.getTypeString(), element.getRegistryName(), displayName, "valid", "generated",
				updatedAt, values);
	}

	private JsonObject generator(Workspace workspace) {
		String id = workspace.getWorkspaceSettings().getCurrentGenerator();
		if (RESOURCE_PACK_GENERATOR_ID.equals(id))
			return resourcePackGenerator(id, workspace);
		int separator = id.indexOf('-');
		String loader = separator > 0 ? id.substring(0, separator) : id;
		String minecraftVersion = separator > 0 ? id.substring(separator + 1) : "unknown";
		JsonObject generator = new JsonObject();
		generator.addProperty("id", id);
		generator.addProperty("loader", loader);
		generator.addProperty("minecraftVersion", minecraftVersion);
		generator.addProperty("displayName", loader.substring(0, 1).toUpperCase(Locale.ROOT) + loader.substring(1)
				+ " " + minecraftVersion);
		generator.addProperty("state", workspace.getGeneratorConfiguration() == null ? "missing" : "ready");
		return generator;
	}

	private String kind(Workspace workspace) {
		return RESOURCE_PACK_GENERATOR_ID.equals(workspace.getWorkspaceSettings().getCurrentGenerator())
				? "resource_pack" : "mod";
	}

	private JsonObject resourcePackGenerator(String id, Workspace workspace) {
		JsonObject generator = new JsonObject();
		generator.addProperty("id", id);
		generator.addProperty("loader", "resource_pack");
		generator.addProperty("minecraftVersion", "1.21.1");
		generator.addProperty("displayName", "Resource Pack 1.21.1");
		generator.addProperty("state", workspace.getGeneratorConfiguration() == null ? "missing" : "ready");
		return generator;
	}

	private UUID storedElementId(ModElement element) {
		Object value = element.getMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_ID_METADATA);
		if (value == null)
			return null;
		try {
			return UUID.fromString(String.valueOf(value));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private JsonObject storedValues(ModElement element) {
		Object value = element.getMetadata(MCreatorWorkspaceMutationGateway.ELEMENT_VALUES_METADATA);
		if (value == null)
			return null;
		var tree = WorkspaceFileManager.gson.toJsonTree(value);
		return tree.isJsonObject() ? tree.getAsJsonObject() : null;
	}

	public static UUID elementId(UUID workspaceId, ModElement element) {
		String identity = workspaceId + "\n" + element.getTypeString() + "\n" + element.getName();
		return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
	}

	private String displayName(String name) {
		String[] words = name.replace('-', '_').split("_");
		StringBuilder result = new StringBuilder();
		for (String word : words) {
			if (word.isEmpty())
				continue;
			if (!result.isEmpty())
				result.append(' ');
			result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return result.toString();
	}
}
