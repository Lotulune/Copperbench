package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
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
		if ("code".equals(element.getTypeString()))
			values = refreshCodeValuesFromDisk(workspace, element, values);
		Instant updatedAt = Files.isRegularFile(definitionFile)
				? Files.getLastModifiedTime(definitionFile).toInstant() : Instant.EPOCH;
		String displayName = values.has("displayName") && values.get("displayName").isJsonPrimitive()
				? values.get("displayName").getAsString() : displayName(element.getName());
		return new Element(id, element.getTypeString(), element.getRegistryName(), displayName, "valid",
				element.isCodeLocked() ? "manual" : "generated",
				updatedAt, values);
	}

	private JsonObject refreshCodeValuesFromDisk(Workspace workspace, ModElement element, JsonObject stored)
			throws IOException {
		JsonObject values = stored.deepCopy();
		Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		List<Path> bundlePaths = new ArrayList<>();
		Object bundleMetadata = element.getMetadata(MCreatorWorkspaceMutationGateway.CODE_FILES_METADATA);
		if (bundleMetadata instanceof List<?> paths) {
			for (Object value : paths) {
				Path path = workspaceRoot.resolve(value.toString()).toAbsolutePath().normalize();
				if (path.startsWith(workspaceRoot)) bundlePaths.add(path);
			}
		}
		Path primary = element.getAssociatedFiles().stream()
				.map(java.io.File::toPath)
				.map(path -> path.toAbsolutePath().normalize())
				.filter(path -> path.getFileName().toString().endsWith(".java"))
				.filter(path -> bundlePaths.stream().noneMatch(path::equals))
				.findFirst().orElse(null);
		if (primary == null || !Files.isRegularFile(primary)) return values;

		values.addProperty("code", Files.readString(primary, StandardCharsets.UTF_8));
		JsonObject fingerprints = new JsonObject();
		fingerprints.addProperty("$primary", fingerprint(primary));
		if (values.has("codeFiles") && values.get("codeFiles").isJsonArray()) {
			JsonArray refreshed = new JsonArray();
			Path base = primary.getParent();
			for (JsonElement raw : values.getAsJsonArray("codeFiles")) {
				if (!raw.isJsonObject()) {
					refreshed.add(raw.deepCopy());
					continue;
				}
				JsonObject file = raw.getAsJsonObject().deepCopy();
				if (file.has("path") && file.get("path").isJsonPrimitive()) {
					String relative = file.get("path").getAsString().replace('\\', '/');
					Path target = base.resolve(relative).toAbsolutePath().normalize();
					if (target.startsWith(base) && Files.isRegularFile(target)) {
						file.addProperty("code", Files.readString(target, StandardCharsets.UTF_8));
						fingerprints.addProperty(relative, fingerprint(target));
					}
				}
				refreshed.add(file);
			}
			values.add("codeFiles", refreshed);
		}
		values.add("sourceFingerprints", fingerprints);
		return values;
	}

	private static String fingerprint(Path path) throws IOException {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
		} catch (NoSuchAlgorithmException exception) {
			throw new AssertionError("JVM must provide SHA-256", exception);
		}
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
