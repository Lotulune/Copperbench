package dev.copperbench.core.application;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.core.workspace.WorkspaceState.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Local-only reusable element/procedure/asset bundles. Templates never bypass Workspace Plan review. */
final class LocalWorkspaceTemplateService {

	private static final Gson GSON = new Gson();
	private static final String KIND = "copperbench_local_template";
	private static final String SCHEMA_VERSION = "1.0";
	private static final Pattern TEMPLATE_NAME = Pattern.compile("^[a-z][a-z0-9_-]{0,63}$");
	private static final int MAX_ELEMENTS = 50;
	private static final int MAX_ASSETS = WorkspacePlanArtifact.MAX_ARTIFACTS;
	private static final int MAX_DESCRIPTION = 512;

	private final Path root;
	private final Clock clock;

	LocalWorkspaceTemplateService(Path root, Clock clock) {
		this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	static LocalWorkspaceTemplateService productDefault(Clock clock) {
		String configured = System.getProperty("copperbench.templates.dir", "").trim();
		Path root = configured.isEmpty()
				? Path.of(System.getProperty("user.home"), ".copperbench", "templates")
				: Path.of(configured);
		return new LocalWorkspaceTemplateService(root, clock);
	}

	JsonObject create(WorkspaceState state, Path workspaceRoot, JsonObject payload, JsonObject references) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(workspaceRoot, "workspaceRoot");
		Objects.requireNonNull(payload, "payload");
		String templateName = templateName(requiredString(payload, "templateName"));
		String description = optionalString(payload, "description", "");
		if (description.length() > MAX_DESCRIPTION) throw new IllegalArgumentException("description is too long");
		List<UUID> selectedIds = elementIds(payload);
		List<String> selectedAssetPaths = assetPaths(payload);
		if (selectedIds.isEmpty() && selectedAssetPaths.isEmpty())
			throw new IllegalArgumentException("At least one elementId or assetPath is required");

		Map<UUID, Element> selected = new LinkedHashMap<>();
		for (UUID id : selectedIds) {
			Element element = state.element(id);
			if (element == null) throw new IllegalArgumentException("Selected element does not exist: " + id);
			String expectedOwnership = element.type().equals("code") ? "manual" : "generated";
			if (!expectedOwnership.equals(element.ownership()))
				throw new IllegalArgumentException("Selected element has non-recreatable ownership: " + element.name());
			selected.put(id, element);
		}
		validateReferenceClosure(selected.keySet(), references == null ? new JsonObject() : references);

		Path sourceRoot = workspaceRoot.toAbsolutePath().normalize();
		List<WorkspacePlanArtifact> artifacts = new ArrayList<>();
		Set<String> uniqueAssets = new LinkedHashSet<>();
		for (String requested : selectedAssetPaths) {
			String relative = WorkspacePlanArtifact.normalizeRelativePath(requested);
			if (!uniqueAssets.add(relative)) continue;
			Path source = sourceRoot.resolve(relative).normalize();
			assertSourceAssetSafe(sourceRoot, source);
			if (!Files.isRegularFile(source)) throw new IllegalArgumentException("Selected asset does not exist: " + relative);
			try {
				artifacts.add(WorkspacePlanArtifact.of(relative, Files.readAllBytes(source)));
			} catch (IOException exception) {
				throw new IllegalStateException("Unable to read selected template asset: " + relative, exception);
			}
		}

		String generatorId = generatorId(state);
		JsonObject document = new JsonObject();
		document.addProperty("schemaVersion", SCHEMA_VERSION);
		document.addProperty("kind", KIND);
		document.addProperty("name", templateName);
		document.addProperty("description", description);
		document.addProperty("createdAt", clock.instant().toString());
		document.addProperty("sourceGeneratorId", generatorId);
		JsonArray elements = new JsonArray();
		selected.values().stream().sorted(Comparator.comparing(Element::name)).forEach(element -> {
			JsonObject item = new JsonObject();
			item.addProperty("sourceElementId", element.id().toString());
			item.addProperty("type", element.type());
			item.addProperty("name", element.name());
			item.addProperty("displayName", element.displayName());
			item.addProperty("state", element.state());
			item.addProperty("ownership", element.ownership());
			JsonObject values = element.values().deepCopy();
			if (!values.has("displayName")) values.addProperty("displayName", element.displayName());
			item.add("values", values);
			elements.add(item);
		});
		document.add("elements", elements);
		document.add("assets", artifactArray(artifacts));
		document.addProperty("templateSha256", documentSha256(document));

		Path target = templatePath(templateName);
		boolean overwrite = optionalBoolean(payload, "overwrite", false);
		try {
			Files.createDirectories(root);
			assertTemplateRootSafe();
			if (Files.exists(target) && !overwrite)
				throw new IllegalArgumentException("A local template with this name already exists");
			writeAtomically(target, GSON.toJson(document).getBytes(StandardCharsets.UTF_8));
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to store local template", exception);
		}
		return metadata(load(templateName));
	}

	JsonObject list() {
		JsonObject result = new JsonObject();
		result.addProperty("schemaVersion", SCHEMA_VERSION);
		result.addProperty("kind", "local_template_list");
		JsonArray templates = new JsonArray();
		int invalid = 0;
		try {
			if (Files.isDirectory(root)) {
				assertTemplateRootSafe();
				List<Path> files;
				try (var stream = Files.list(root)) {
					files = stream.filter(Files::isRegularFile)
							.filter(path -> path.getFileName().toString().endsWith(".json"))
							.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
				}
				for (Path file : files) {
					String name = file.getFileName().toString().replaceFirst("\\.json$", "");
					try {
						templates.add(metadata(load(name)));
					} catch (RuntimeException exception) {
						invalid++;
					}
				}
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to list local templates", exception);
		}
		result.add("templates", templates);
		result.addProperty("templateCount", templates.size());
		result.addProperty("invalidTemplateCount", invalid);
		result.addProperty("localOnly", true);
		return result;
	}

	PreparedInstantiation prepareInstantiation(String requestedName, WorkspaceState target, Supplier<UUID> ids) {
		LoadedTemplate template = load(requestedName);
		String targetGenerator = generatorId(target);
		if (!template.sourceGeneratorId().equals(targetGenerator))
			throw new IllegalArgumentException("Template generator mismatch: expected " + template.sourceGeneratorId()
					+ ", target is " + targetGenerator);
		Map<String, String> idRemap = new LinkedHashMap<>();
		for (JsonElement raw : template.document().getAsJsonArray("elements")) {
			String sourceId = requiredString(raw.getAsJsonObject(), "sourceElementId");
			idRemap.put(sourceId, ids.get().toString());
		}
		JsonArray operations = new JsonArray();
		for (JsonElement raw : template.document().getAsJsonArray("elements")) {
			JsonObject element = raw.getAsJsonObject();
			JsonObject operation = new JsonObject();
			operation.addProperty("operation", "create_mod_element");
			operation.addProperty("plannedId", idRemap.get(requiredString(element, "sourceElementId")));
			JsonObject payload = new JsonObject();
			payload.addProperty("elementType", requiredString(element, "type"));
			payload.addProperty("name", requiredString(element, "name"));
			payload.add("initialValues", rewriteIds(element.getAsJsonObject("values"), idRemap));
			operation.add("payload", payload);
			operations.add(operation);
		}
		JsonObject metadata = metadata(template);
		metadata.addProperty("instantiationMode", "workspace_plan");
		metadata.addProperty("recoveryRequired", true);
		return new PreparedInstantiation(operations, template.assets(), metadata);
	}

	LoadedTemplate load(String requestedName) {
		String name = templateName(requestedName);
		Path file = templatePath(name);
		try {
			if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Local template not found: " + name);
			assertTemplateRootSafe();
			JsonElement raw = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!raw.isJsonObject()) throw new IllegalArgumentException("Local template document must be an object");
			JsonObject document = raw.getAsJsonObject();
			if (!SCHEMA_VERSION.equals(requiredString(document, "schemaVersion"))
					|| !KIND.equals(requiredString(document, "kind"))
					|| !name.equals(requiredString(document, "name")))
				throw new IllegalArgumentException("Local template metadata is invalid");
			String expected = requiredString(document, "templateSha256");
			if (!expected.equals(documentSha256(document)))
				throw new IllegalArgumentException("Local template integrity check failed");
			if (!document.has("elements") || !document.get("elements").isJsonArray()
					|| !document.has("assets") || !document.get("assets").isJsonArray())
				throw new IllegalArgumentException("Local template elements/assets are invalid");
			if (document.getAsJsonArray("elements").size() > MAX_ELEMENTS)
				throw new IllegalArgumentException("Local template contains too many elements");
			List<WorkspacePlanArtifact> assets = WorkspacePlanArtifact.fromJson(document.getAsJsonArray("assets"));
			return new LoadedTemplate(name, requiredString(document, "sourceGeneratorId"), document.deepCopy(), assets);
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to read local template", exception);
		}
	}

	private JsonObject metadata(LoadedTemplate template) {
		JsonObject document = template.document();
		JsonObject result = new JsonObject();
		result.addProperty("name", template.name());
		result.addProperty("description", optionalString(document, "description", ""));
		result.addProperty("createdAt", requiredString(document, "createdAt"));
		result.addProperty("sourceGeneratorId", template.sourceGeneratorId());
		result.addProperty("elementCount", document.getAsJsonArray("elements").size());
		result.addProperty("assetCount", template.assets().size());
		long bytes = template.assets().stream().mapToLong(asset -> asset.content().length).sum();
		result.addProperty("assetBytes", bytes);
		result.addProperty("templateSha256", requiredString(document, "templateSha256"));
		result.addProperty("localOnly", true);
		return result;
	}

	private static void validateReferenceClosure(Set<UUID> selected, JsonObject references) {
		if (references.has("diagnostics") && references.get("diagnostics").isJsonArray()) {
			for (JsonElement raw : references.getAsJsonArray("diagnostics")) {
				if (!raw.isJsonObject()) continue;
				JsonObject diagnostic = raw.getAsJsonObject();
				if (!diagnostic.has("elementId") || !diagnostic.get("elementId").isJsonPrimitive()) continue;
				UUID source = UUID.fromString(diagnostic.get("elementId").getAsString());
				if (selected.contains(source))
					throw new IllegalArgumentException("Selected elements contain an unresolved structured reference: " + source);
			}
		}
		if (!references.has("edges") || !references.get("edges").isJsonArray()) return;
		for (JsonElement raw : references.getAsJsonArray("edges")) {
			if (!raw.isJsonObject()) continue;
			JsonObject edge = raw.getAsJsonObject();
			if (!edge.has("sourceId") || !edge.get("sourceId").isJsonPrimitive()) continue;
			UUID source = UUID.fromString(edge.get("sourceId").getAsString());
			if (!selected.contains(source) || !edge.has("targetId") || edge.get("targetId").isJsonNull()) continue;
			UUID target = UUID.fromString(edge.get("targetId").getAsString());
			String targetKind = optionalString(edge, "targetKind", "");
			if ("element".equals(targetKind) && !selected.contains(target))
				throw new IllegalArgumentException("Selected elements depend on an element outside the template: " + target);
			if ("registry".equals(targetKind))
				throw new IllegalArgumentException("Selected elements depend on a workspace registry entry not bundled by local templates: " + target);
		}
	}

	private static void assertSourceAssetSafe(Path root, Path source) {
		try {
			Path normalizedRoot = root.toAbsolutePath().normalize();
			Path normalizedSource = source.toAbsolutePath().normalize();
			if (!normalizedSource.startsWith(normalizedRoot)) throw new IllegalArgumentException("Asset path escapes workspace");
			Path realRoot = normalizedRoot.toRealPath();
			if (!normalizedSource.toRealPath().startsWith(realRoot))
				throw new IllegalArgumentException("Asset path traverses a link outside workspace");
		} catch (IOException exception) {
			throw new IllegalArgumentException("Unable to validate selected asset path", exception);
		}
	}

	private void assertTemplateRootSafe() throws IOException {
		Path parent = root.getParent();
		if (parent != null && Files.exists(parent) && Files.isSymbolicLink(parent))
			throw new IOException("Local template parent cannot be a symbolic link");
		if (Files.exists(root) && Files.isSymbolicLink(root))
			throw new IOException("Local template root cannot be a symbolic link");
	}

	private Path templatePath(String name) {
		Path target = root.resolve(name + ".json").normalize();
		if (!target.startsWith(root)) throw new IllegalArgumentException("Template path escapes local template root");
		return target;
	}

	private static JsonArray artifactArray(List<WorkspacePlanArtifact> artifacts) {
		JsonArray array = new JsonArray();
		artifacts.forEach(artifact -> array.add(artifact.toJson()));
		return array;
	}

	private static JsonElement rewriteIds(JsonElement value, Map<String, String> remap) {
		if (value == null || value.isJsonNull()) return value == null ? com.google.gson.JsonNull.INSTANCE : value.deepCopy();
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
			String string = value.getAsString();
			return new com.google.gson.JsonPrimitive(remap.getOrDefault(string, string));
		}
		if (value.isJsonArray()) {
			JsonArray array = new JsonArray();
			for (JsonElement item : value.getAsJsonArray()) array.add(rewriteIds(item, remap));
			return array;
		}
		if (value.isJsonObject()) {
			JsonObject object = new JsonObject();
			for (var entry : value.getAsJsonObject().entrySet()) object.add(entry.getKey(), rewriteIds(entry.getValue(), remap));
			return object;
		}
		return value.deepCopy();
	}

	private static String documentSha256(JsonObject document) {
		JsonObject unsigned = document.deepCopy();
		unsigned.remove("templateSha256");
		return sha256(GSON.toJson(unsigned).getBytes(StandardCharsets.UTF_8));
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static void writeAtomically(Path target, byte[] bytes) throws IOException {
		Path temporary = Files.createTempFile(target.getParent(), ".copperbench-template-", ".tmp");
		try {
			Files.write(temporary, bytes);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static List<UUID> elementIds(JsonObject payload) {
		if (!payload.has("elementIds")) return List.of();
		if (!payload.get("elementIds").isJsonArray()) throw new IllegalArgumentException("elementIds must be an array");
		JsonArray array = payload.getAsJsonArray("elementIds");
		if (array.size() > MAX_ELEMENTS) throw new IllegalArgumentException("Too many elementIds");
		LinkedHashSet<UUID> result = new LinkedHashSet<>();
		for (JsonElement raw : array) {
			if (!raw.isJsonPrimitive()) throw new IllegalArgumentException("Each elementId must be a UUID string");
			result.add(UUID.fromString(raw.getAsString()));
		}
		return List.copyOf(result);
	}

	private static List<String> assetPaths(JsonObject payload) {
		if (!payload.has("assetPaths")) return List.of();
		if (!payload.get("assetPaths").isJsonArray()) throw new IllegalArgumentException("assetPaths must be an array");
		JsonArray array = payload.getAsJsonArray("assetPaths");
		if (array.size() > MAX_ASSETS) throw new IllegalArgumentException("Too many assetPaths");
		List<String> result = new ArrayList<>();
		for (JsonElement raw : array) {
			if (!raw.isJsonPrimitive()) throw new IllegalArgumentException("Each assetPath must be a string");
			result.add(raw.getAsString());
		}
		return List.copyOf(result);
	}

	private static String generatorId(WorkspaceState state) {
		JsonObject generator = state.generator();
		if (!generator.has("id") || !generator.get("id").isJsonPrimitive() || generator.get("id").getAsString().isBlank())
			throw new IllegalArgumentException("Workspace generator ID is unavailable");
		return generator.get("id").getAsString();
	}

	private static String templateName(String value) {
		if (!TEMPLATE_NAME.matcher(value).matches()) throw new IllegalArgumentException("templateName is invalid");
		return value;
	}

	private static String requiredString(JsonObject json, String property) {
		if (!json.has(property) || !json.get(property).isJsonPrimitive() || json.get(property).getAsString().isBlank())
			throw new IllegalArgumentException(property + " is required");
		return json.get(property).getAsString();
	}

	private static String optionalString(JsonObject json, String property, String fallback) {
		return json.has(property) && json.get(property).isJsonPrimitive() ? json.get(property).getAsString() : fallback;
	}

	private static boolean optionalBoolean(JsonObject json, String property, boolean fallback) {
		return json.has(property) && json.get(property).isJsonPrimitive() ? json.get(property).getAsBoolean() : fallback;
	}

	record LoadedTemplate(String name, String sourceGeneratorId, JsonObject document,
			List<WorkspacePlanArtifact> assets) {
		LoadedTemplate {
			document = document.deepCopy();
			assets = List.copyOf(assets);
		}
		@Override public JsonObject document() { return document.deepCopy(); }
	}

	record PreparedInstantiation(JsonArray operations, List<WorkspacePlanArtifact> artifacts, JsonObject metadata) {
		PreparedInstantiation {
			operations = operations.deepCopy();
			artifacts = List.copyOf(artifacts);
			metadata = metadata.deepCopy();
		}
		@Override public JsonArray operations() { return operations.deepCopy(); }
		@Override public JsonObject metadata() { return metadata.deepCopy(); }
	}
}
