package dev.copperbench.assets;

import com.google.gson.*;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.*;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

/** Durable editing sessions. Completion freezes a candidate; it does not import into the workspace. */
public final class BlockbenchModelingService {
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	private static final long MAX_BYTES = 32L * 1024 * 1024;
	private final AssetWorkspaceService assets;
	private final LocalHistoryService history;
	private final Clock clock;
	private final UUID workspaceId;

	public BlockbenchModelingService(Path root, UUID workspaceId, LocalHistoryService history, Clock clock) {
		this.assets = new AssetWorkspaceService(root);
		this.workspaceId = workspaceId;
		this.history = history;
		this.clock = clock;
	}

	public JsonObject begin(UUID taskId, String assetId, String requestedTarget, long revision, Actor actor) {
		if ((assetId == null) == (requestedTarget == null)) throw problem("MODEL_TASK_INPUT", "Specify either assetId or targetRelativePath");
		Path directory = taskDirectory(taskId);
		Path manifest = safe(directory.resolve("task.json"));
		if (Files.exists(manifest)) {
			JsonObject saved = read(taskId);
			if (!Objects.equals(text(saved, "sourceAssetId"), assetId)
					|| (requestedTarget != null && !target(requestedTarget).equals(text(saved, "targetRelativePath"))))
				throw problem("MODEL_TASK_ID_REUSED", "This task ID belongs to a different modeling request");
			return projection(saved);
		}
		if (Files.exists(directory)) throw problem("MODEL_TASK_INCOMPLETE", "This task directory contains an interrupted preparation; its files are preserved. Use a new task ID");
		String relative;
		String sourceHash = null;
		JsonObject model;
		if (assetId != null) {
			AssetDescriptor source = assets.findById(assetId).orElseThrow(() -> problem("MODEL_SOURCE_MISSING", "The source asset is not indexed"));
			relative = target(source.relativePath());
			Path file = assets.resolveAuthorizedPath(relative);
			byte[] bytes = bytes(file);
			sourceHash = hash(bytes);
			model = portableModel(bytes, file.getParent(), assets.workspaceRoot());
		} else {
			relative = target(requestedTarget);
			if (Files.exists(safe(assets.workspaceRoot().resolve(relative))))
				throw problem("MODEL_TARGET_EXISTS", "The target already exists; start from its asset ID instead");
			model = JsonParser.parseString("{\"meta\":{\"format_version\":\"5.0\",\"model_format\":\"java_block\",\"box_uv\":false},"
					+ "\"resolution\":{\"width\":16,\"height\":16},\"elements\":[],\"outliner\":[],\"textures\":[]}").getAsJsonObject();
			model.addProperty("name", Path.of(relative).getFileName().toString().replace(".bbmodel", ""));
		}
		// Other active tasks for the same target must be resolved first. No OS-level lock of the external editor is implied.
		JsonArray currentTasks = list().getAsJsonArray("tasks");
		if (currentTasks.size() >= 256) throw problem("MODEL_TASK_LIMIT", "This workspace has reached the 256 modeling task limit");
		for (JsonElement entry : currentTasks) {
			JsonObject other = entry.getAsJsonObject();
			if (relative.equals(text(other, "targetRelativePath")) && Set.of("editing", "ready_to_import", "importing").contains(text(other, "state")))
				throw problem("MODEL_TARGET_BUSY", "Another modeling task already owns this target");
		}
		if (history == null) throw problem("MODEL_RECOVERY_UNAVAILABLE", "Modeling requires local-history recovery");
		try {
			RecoveryPoint recovery = history.createRecoveryPoint(new RecoveryPointRequest("Before modeling: " + relative,
					actor, taskId.toString(), RecoveryPointSource.BLOCKBENCH));
			Path edit = safe(directory.resolve("edit/model.bbmodel"));
			Files.createDirectories(edit.getParent());
			byte[] initial = JSON.toJson(model).getBytes(java.nio.charset.StandardCharsets.UTF_8);
			write(edit, initial);
			JsonObject saved = new JsonObject();
			saved.addProperty("taskId", taskId.toString());
			saved.addProperty("workspaceId", workspaceId.toString());
			saved.addProperty("sourceAssetId", assetId);
			saved.addProperty("targetRelativePath", relative);
			saved.addProperty("sourceSha256", sourceHash);
			saved.addProperty("openedRevision", revision);
			saved.addProperty("recoveryPointId", recovery.id());
			saved.addProperty("initialEditSha256", hash(initial));
			saved.addProperty("format", "java_block");
			saved.addProperty("state", "editing");
			saved.addProperty("createdAt", clock.instant().toString());
			saved.add("activity", new JsonArray());
			save(saved, "begin", actor);
			return projection(saved);
		} catch (LocalHistoryException | IOException exception) {
			throw problem("MODEL_TASK_PREPARE_FAILED", "Could not prepare the modeling task; no source asset was changed");
		}
	}

	public JsonObject get(UUID taskId) { return projection(read(taskId)); }

	public JsonObject list() {
		JsonArray result = new JsonArray();
		Path directory = safe(assets.workspaceRoot().resolve(".copperbench/modeling-tasks"));
		if (Files.isDirectory(directory)) try (var children = Files.list(directory)) {
			List<Path> entries = children.filter(Files::isDirectory).sorted().toList();
			if (entries.size() > 256) throw problem("MODEL_TASK_LIMIT", "This workspace has reached the 256 modeling task limit");
			for (Path child : entries) {
				UUID id;
				try { id = UUID.fromString(child.getFileName().toString()); }
				catch (IllegalArgumentException ignored) { continue; }
				if (Files.isRegularFile(safe(child.resolve("task.json")))) result.add(get(id));
			}
		} catch (IOException exception) { throw problem("MODEL_TASK_READ_FAILED", "Could not list modeling tasks"); }
		JsonObject response = new JsonObject();
		response.add("tasks", result);
		return response;
	}

	public JsonObject finish(UUID taskId, String savedSha256, Actor actor) {
		JsonObject saved = read(taskId);
		if (Set.of("importing", "imported").contains(text(saved, "state")))
			throw problem("MODEL_IMPORT_STATE", "This task has entered import; inspect its receipt or recover it before further edits");
		if ("cancelled".equals(text(saved, "state"))) throw problem("MODEL_TASK_CANCELLED", "The task was cancelled; its files are preserved");
		if (savedSha256 == null || !savedSha256.matches("[0-9a-f]{64}"))
			throw problem("MODEL_SAVED_HASH_REQUIRED", "Read the saved editSha256 and send it as savedSha256");
		Path directory = taskDirectory(taskId);
		byte[] edit = bytes(safe(directory.resolve("edit/model.bbmodel")));
		if (!savedSha256.equals(hash(edit))) throw problem("MODEL_EDIT_CHANGED", "The saved edit changed; refresh the task before finishing");
		if (sourceChanged(saved)) throw problem("MODEL_SOURCE_CONFLICT", "The source or target changed; the editing copy is preserved");
		if ("ready_to_import".equals(text(saved, "state"))) {
			if (!savedSha256.equals(text(saved, "finishedEditSha256"))
					|| !Objects.equals(text(saved, "candidateSha256"), hash(bytes(safe(directory.resolve("candidate.bbmodel"))))))
				throw problem("MODEL_CANDIDATE_CHANGED", "The completed task files changed; start a new task to review the changes");
			return projection(saved);
		}
		JsonObject model = portableModel(edit, directory.resolve("edit"), directory.resolve("edit"));
		if (model.getAsJsonArray("elements").isEmpty()) throw problem("MODEL_EMPTY", "Add model geometry before completing this task");
		byte[] candidate = JSON.toJson(model).getBytes(java.nio.charset.StandardCharsets.UTF_8);
		// Recheck after resolving textures; never freeze a result assembled across two saves.
		if (!savedSha256.equals(hash(bytes(directory.resolve("edit/model.bbmodel")))))
			throw problem("MODEL_EDIT_CHANGED", "The editing file changed during completion; save and retry");
		try {
			write(directory.resolve("candidate.bbmodel"), candidate);
			saved.addProperty("state", "ready_to_import");
			saved.addProperty("finishedEditSha256", savedSha256);
			saved.addProperty("candidateSha256", hash(candidate));
			save(saved, "finish", actor);
			return projection(saved);
		} catch (IOException exception) { throw problem("MODEL_TASK_SAVE_FAILED", "Could not freeze the candidate; editing files are preserved"); }
	}

	public JsonObject cancel(UUID taskId, Actor actor) {
		JsonObject saved = read(taskId);
		if (Set.of("importing", "imported").contains(text(saved, "state")))
			throw problem("MODEL_IMPORT_STATE", "An importing or imported task cannot be cancelled; use import recovery or workspace history");
		if (!"cancelled".equals(text(saved, "state"))) {
			saved.addProperty("state", "cancelled");
			save(saved, "cancel", actor);
		}
		return projection(saved);
	}

	JsonObject projection(JsonObject saved) {
		JsonObject result = saved.deepCopy();
		Path directory = taskDirectory(UUID.fromString(text(saved, "taskId")));
		Path edit = safe(directory.resolve("edit/model.bbmodel"));
		String editHash = Files.isRegularFile(edit) ? hash(bytes(edit)) : null;
		result.addProperty("editPath", edit.toString());
		result.addProperty("editSha256", editHash);
		result.addProperty("hasSavedChanges", editHash != null && !editHash.equals(text(saved, "initialEditSha256")));
		result.addProperty("sourceChanged", !Set.of("importing", "imported").contains(text(saved, "state")) && sourceChanged(saved));
		Path candidate = safe(directory.resolve("candidate.bbmodel"));
		result.addProperty("candidateChanged", "ready_to_import".equals(text(saved, "state"))
				&& (!Files.isRegularFile(candidate) || !Objects.equals(text(saved, "candidateSha256"), hash(bytes(candidate)))
				|| !Objects.equals(editHash, text(saved, "finishedEditSha256"))));
		result.addProperty("candidatePath", "ready_to_import".equals(text(saved, "state")) ? safe(directory.resolve("candidate.bbmodel")).toString() : null);
		result.addProperty("imported", "imported".equals(text(saved, "state")));
		result.addProperty("recoveryRequired", "importing".equals(text(saved, "state")));
		return result;
	}

	private boolean sourceChanged(JsonObject saved) {
		Path target = safe(assets.workspaceRoot().resolve(target(text(saved, "targetRelativePath"))));
		return !Objects.equals(text(saved, "sourceSha256"), Files.exists(target) ? hash(bytes(target)) : null);
	}

	JsonObject read(UUID id) {
		try {
			JsonObject saved = JsonParser.parseString(new String(bytes(taskDirectory(id).resolve("task.json")), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			if (!id.toString().equals(text(saved, "taskId")) || !workspaceId.toString().equals(text(saved, "workspaceId"))
					|| !Set.of("editing", "ready_to_import", "cancelled", "importing", "imported").contains(text(saved, "state")))
				throw new IllegalArgumentException();
			target(text(saved, "targetRelativePath"));
			return saved;
		} catch (RuntimeException exception) { throw problem("MODEL_TASK_INVALID", "The task record is missing, invalid or belongs to another workspace"); }
	}

	void save(JsonObject saved, String operation, Actor actor) {
		JsonObject event = new JsonObject();
		event.addProperty("operation", operation);
		event.addProperty("actor", actor.name().toLowerCase(Locale.ROOT));
		event.addProperty("at", clock.instant().toString());
		saved.getAsJsonArray("activity").add(event);
		try { write(taskDirectory(UUID.fromString(text(saved, "taskId"))).resolve("task.json"), JSON.toJson(saved).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
		catch (IOException exception) { throw problem("MODEL_TASK_SAVE_FAILED", "Could not persist modeling task state"); }
	}

	Path taskDirectory(UUID id) { return safe(assets.workspaceRoot().resolve(".copperbench/modeling-tasks/" + id)); }

	private String target(String relative) {
		if (relative == null || !relative.matches("(?:models/|assets/|src/main/resources/assets/)[a-z0-9_./-]+\\.bbmodel")
				|| relative.contains("..") || relative.contains("//"))
			throw problem("MODEL_TARGET_INVALID", "Use a lowercase .bbmodel path inside a workspace model or asset directory");
		safe(assets.workspaceRoot().resolve(relative));
		return relative;
	}

	Path safe(Path path) {
		Path normalized = path.toAbsolutePath().normalize();
		if (!normalized.startsWith(assets.workspaceRoot())) throw problem("MODEL_PATH_INVALID", "A modeling path escapes the workspace");
		Path current = assets.workspaceRoot();
		for (Path component : assets.workspaceRoot().relativize(normalized)) {
			current = current.resolve(component);
			if (Files.isSymbolicLink(current)) throw problem("MODEL_PATH_INVALID", "Modeling paths may not use symbolic links");
			try {
				if (Files.exists(current) && !current.toRealPath().equals(current))
					throw problem("MODEL_PATH_INVALID", "Modeling paths may not use redirected directories");
			} catch (IOException exception) { throw problem("MODEL_PATH_INVALID", "Could not resolve modeling path"); }
		}
		return normalized;
	}

	byte[] bytes(Path file) {
		try {
			file = safe(file);
			if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) throw new IOException();
			try (var stream = Files.newInputStream(file)) {
				byte[] data = stream.readNBytes((int) MAX_BYTES + 1);
				if (data.length > MAX_BYTES) throw new IOException();
				return data;
			}
		} catch (IOException exception) { throw problem("MODEL_FILE_UNAVAILABLE", "A modeling file is missing or exceeds 32 MiB"); }
	}

	void write(Path target, byte[] bytes) throws IOException {
		target = safe(target);
		if (bytes.length > MAX_BYTES) throw problem("MODEL_SIZE_LIMIT", "The portable model exceeds 32 MiB");
		Files.createDirectories(target.getParent());
		Path temporary = Files.createTempFile(target.getParent(), "modeling-", ".pending");
		Files.write(temporary, bytes);
		try { Files.move(temporary, safe(target), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
		catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, safe(target), StandardCopyOption.REPLACE_EXISTING); }
	}

	private JsonObject portableModel(byte[] content, Path base, Path allowedRoot) {
		try {
			JsonObject model = JsonParser.parseString(new String(content, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			if (!model.has("meta") || !"java_block".equals(text(model.getAsJsonObject("meta"), "model_format"))
					|| !model.has("elements") || !model.get("elements").isJsonArray())
				throw problem("MODEL_FORMAT_UNSUPPORTED", "This stage supports Blockbench Java block/item models (java_block)");
			for (JsonElement raw : model.getAsJsonArray("elements")) {
				if (!raw.isJsonObject()) throw problem("MODEL_GEOMETRY_INVALID", "Model elements must be cubes");
				JsonObject cube = raw.getAsJsonObject();
				if (cube.has("type") && !"cube".equals(text(cube, "type"))) throw problem("MODEL_GEOMETRY_INVALID", "Java block/item candidates only support cubes");
				for (String field : List.of("from", "to")) {
					if (!cube.has(field) || !cube.get(field).isJsonArray() || cube.getAsJsonArray(field).size() != 3)
						throw problem("MODEL_GEOMETRY_INVALID", "Cube from/to coordinates must contain three numbers");
					for (JsonElement coordinate : cube.getAsJsonArray(field))
						if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber() || !Double.isFinite(coordinate.getAsDouble()))
							throw problem("MODEL_GEOMETRY_INVALID", "Cube coordinates must be finite numbers");
				}
			}
			JsonElement textures = model.get("textures");
			if (textures != null && textures.isJsonArray()) {
				JsonArray array = textures.getAsJsonArray();
				for (int i = 0; i < array.size(); i++) array.set(i, portableTexture(array.get(i), base, allowedRoot));
			} else if (textures != null && textures.isJsonObject()) {
				JsonObject object = textures.getAsJsonObject();
				for (String key : new ArrayList<>(object.keySet())) object.add(key, portableTexture(object.get(key), base, allowedRoot));
			} else if (textures != null) throw problem("MODEL_TEXTURE_INVALID", "The texture table is invalid");
			var parsed = BbmodelDocument.parse(model);
			if (!parsed.issues().isEmpty()) throw problem("MODEL_STRUCTURE_INVALID", "Model validation requires review: " + parsed.issues().getFirst().code());
			return model;
		} catch (BlockbenchBridgeException exception) { throw exception; }
		catch (RuntimeException exception) { throw problem("MODEL_JSON_INVALID", "Save a valid Blockbench project before continuing"); }
	}

	private JsonElement portableTexture(JsonElement texture, Path base, Path allowedRoot) {
		List<String> values = new ArrayList<>();
		if (texture.isJsonObject()) for (String key : List.of("relative_path", "path", "source")) {
			String value = text(texture.getAsJsonObject(), key);
			if (value != null) values.add(value);
		} else if (texture.isJsonPrimitive() && texture.getAsJsonPrimitive().isString()) values.add(texture.getAsString());
		String embedded = null;
		for (String value : values) {
			if (value.startsWith("data:image/")) { embedded = value; break; }
			try {
				Path candidate = base.resolve(value.replace('\\', '/')).toAbsolutePath().normalize();
				if (!candidate.startsWith(allowedRoot.toAbsolutePath().normalize()) || !Files.isRegularFile(candidate)) continue;
				byte[] image = bytes(candidate);
				var decoded = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image));
				if (decoded == null) continue;
				var png = new java.io.ByteArrayOutputStream();
				javax.imageio.ImageIO.write(decoded, "png", png);
				embedded = "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
				break;
			} catch (IOException | InvalidPathException ignored) { }
		}
		if (embedded == null) throw problem("MODEL_TEXTURE_UNAVAILABLE", "Embed the texture or copy its image into the task edit directory; external files and URLs are not fetched");
		if (!texture.isJsonObject()) return new JsonPrimitive(embedded);
		JsonObject result = texture.getAsJsonObject().deepCopy();
		result.remove("relative_path");
		result.remove("path");
		result.addProperty("source", embedded);
		return result;
	}

	private static String text(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
	}
	static String hash(byte[] bytes) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
		catch (java.security.NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
	}
	private static BlockbenchBridgeException problem(String code, String message) { return new BlockbenchBridgeException(code, message); }
}
