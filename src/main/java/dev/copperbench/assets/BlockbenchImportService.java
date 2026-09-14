package dev.copperbench.assets;

import com.google.gson.*;
import dev.copperbench.core.contract.UiCore.Actor;
import dev.copperbench.history.*;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

/** Reviewed multi-file import of actual editor exports, with a durable, file-scoped recovery journal. */
public final class BlockbenchImportService {
	/** Commits durable workspace revision metadata before reporting the file transaction complete. */
	public interface RevisionCommit {
		void commit() throws Exception;
		void rollback() throws Exception;
	}
	private static final RevisionCommit NO_REVISION = new RevisionCommit() {
		public void commit() {}
		public void rollback() {}
	};
	private static final Pattern OUTPUT = Pattern.compile("(?:src/main/resources/|src/main/)?assets/([a-z0-9_.-]+)/(models|textures|blockstates|items)/([a-z0-9_./-]+)\\.(json|png)");
	private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private final Path root;
	private final BlockbenchModelingService tasks;
	private final LocalHistoryService history;
	private final AssetWorkspaceService assets;

	public BlockbenchImportService(Path root, UUID workspaceId, LocalHistoryService history, Clock clock) {
		assets = new AssetWorkspaceService(root);
		this.root = assets.workspaceRoot();
		tasks = new BlockbenchModelingService(root, workspaceId, history, clock);
		this.history = history;
	}

	public record Plan(UUID taskId, String token, AssetImportBatchPlan batch, JsonArray outputs) {
		public JsonObject toJson() {
			JsonObject value = batch.toJson();
			value.addProperty("canApply", batch.issueCodes().isEmpty());
			value.addProperty("hasFileChanges", batch.changedCount() > 0);
			value.addProperty("taskId", taskId.toString()); value.addProperty("planToken", token);
			value.addProperty("requiresReplacementConfirmation", batch.replaceCount() > 0);
			value.add("outputs", outputs.deepCopy());
			return value;
		}
	}

	public Plan preview(UUID taskId, JsonArray outputs) {
		if (history == null) throw fail("MODEL_RECOVERY_UNAVAILABLE", "Import requires local history");
		JsonObject task = tasks.get(taskId);
		if (!"ready_to_import".equals(task.get("state").getAsString())) throw fail("MODEL_NOT_READY", "Finish the saved modeling task before previewing import");
		if (task.get("sourceChanged").getAsBoolean()) throw fail("MODEL_SOURCE_CONFLICT", "The original asset changed; preserve and review the candidate");
		if (task.get("candidateChanged").getAsBoolean()) throw fail("MODEL_CANDIDATE_CHANGED", "The completed candidate changed");
		if (outputs == null || outputs.isEmpty() || outputs.size() > 63) throw fail("MODEL_OUTPUTS_REQUIRED", "Supply between 1 and 63 exported game files");
		List<AssetImportBatchService.Request> requests = new ArrayList<>();
		requests.add(new AssetImportBatchService.Request(tasks.taskDirectory(taskId).resolve("candidate.bbmodel"), task.get("targetRelativePath").getAsString()));
		Map<String, JsonObject> models = new HashMap<>();
		Set<String> textures = new HashSet<>();
		List<JsonObject> references = new ArrayList<>();
		Path edit = tasks.taskDirectory(taskId).resolve("edit");
		Set<String> destinations = new HashSet<>();
		Set<String> resources = new HashSet<>();
		long total = 0;
		for (JsonElement entry : outputs) {
			JsonObject output = entry.getAsJsonObject();
			if (!output.keySet().equals(Set.of("sourceRelativePath", "targetRelativePath"))) throw fail("MODEL_OUTPUT_INVALID", "Export mappings require sourceRelativePath and targetRelativePath only");
			String source = output.get("sourceRelativePath").getAsString();
			String destination = output.get("targetRelativePath").getAsString();
			if (source.contains("..") || source.contains(":") || source.contains("\\") || source.startsWith("/")) throw fail("MODEL_OUTPUT_PATH", "Export sources must be inside the task edit directory");
			Path input = tasks.safe(edit.resolve(source));
			if (!input.startsWith(edit)) throw fail("MODEL_OUTPUT_PATH", "Export source escapes its edit directory");
			var match = OUTPUT.matcher(destination);
			if (!match.matches() || destination.contains("..") || destination.contains("//") || !destinations.add(destination))
				throw fail("MODEL_OUTPUT_PATH", "Use unique lowercase game-resource paths under an assets namespace");
			Path destinationPath = tasks.safe(root.resolve(destination));
			if (Files.exists(destinationPath) && !Files.isRegularFile(destinationPath)) throw fail("MODEL_OUTPUT_PATH", "An export destination is not a regular file");
			byte[] bytes = tasks.bytes(input); total += bytes.length;
			if (total > 128L * 1024 * 1024) throw fail("MODEL_OUTPUT_SIZE", "Exported files exceed the 128 MiB batch limit");
			String id = match.group(1) + ":" + match.group(3);
			if (!resources.add(match.group(2) + "/" + id)) throw fail("MODEL_OUTPUT_PATH", "A resource cannot be exported into multiple asset roots");
			if (match.group(2).equals("textures")) {
				if (!match.group(4).equals("png")) throw fail("MODEL_TEXTURE_FORMAT", "Game textures must be PNG files");
				try { if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) throw new IllegalArgumentException(); }
				catch (Exception exception) { throw fail("MODEL_TEXTURE_FORMAT", "An exported PNG is invalid"); }
				textures.add(id);
			} else {
				if (!match.group(4).equals("json")) throw fail("MODEL_OUTPUT_FORMAT", "Game model definitions must be JSON");
				JsonObject json;
				try { json = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(); }
				catch (RuntimeException exception) { throw fail("MODEL_OUTPUT_JSON", "An exported game definition is not a JSON object"); }
				if (match.group(2).equals("models")) {
					if (!json.has("elements") && !json.has("parent")) throw fail("MODEL_OUTPUT_JSON", "A game model must define geometry or a parent");
					models.put(id, json);
				} else references.add(json);
			}
			requests.add(new AssetImportBatchService.Request(input, destination));
		}
		if (models.isEmpty()) throw fail("MODEL_GAME_EXPORT_REQUIRED", "Include a Minecraft game model JSON; the bbmodel source is not a game export");
		for (var model : models.entrySet()) validateModel(model.getKey(), model.getValue(), models, textures, new HashSet<>());
		for (JsonObject definition : references) validateModelLinks(definition, models);
		AssetImportBatchPlan batch = new AssetImportBatchService(assets, history).preview(requests);
		if (!batch.issueCodes().isEmpty()) throw fail("MODEL_OUTPUT_CONFLICT", "The export mappings conflict");
		return new Plan(taskId, UUID.randomUUID().toString(), batch, outputs.deepCopy());
	}

	public JsonObject apply(Plan plan, Actor actor, long committedRevision) { return apply(plan, actor, committedRevision, NO_REVISION); }

	public JsonObject apply(Plan plan, Actor actor, long committedRevision, RevisionCommit revision) {
		return apply(plan, actor, committedRevision, ignored -> {}, revision);
	}

	JsonObject apply(Plan plan, Actor actor, long committedRevision, IntConsumer beforeWrite) {
		return apply(plan, actor, committedRevision, beforeWrite, NO_REVISION);
	}

	private JsonObject apply(Plan plan, Actor actor, long committedRevision, IntConsumer beforeWrite, RevisionCommit revision) {
		JsonObject task = tasks.read(plan.taskId());
		if ("imported".equals(task.get("state").getAsString())) return replay(plan.taskId(), plan.token());
		Plan current = preview(plan.taskId(), plan.outputs());
		if (!current.batch().toJson().equals(plan.batch().toJson())) throw fail("MODEL_IMPORT_STALE", "Exported files or destinations changed after preview");
		Path directory = tasks.taskDirectory(plan.taskId());
		JsonArray journal = new JsonArray();
		boolean revisionAttempted = false;
		try {
			int index = 0;
			for (AssetImportPlan item : plan.batch().items()) {
				byte[] input = tasks.bytes(item.source());
				if (!BlockbenchModelingService.hash(input).equals(item.sourceSha256())) throw fail("MODEL_IMPORT_STALE", "An export changed while staging");
				Path target = tasks.safe(root.resolve(item.targetRelativePath()));
				String oldHash = Files.exists(target) ? BlockbenchModelingService.hash(tasks.bytes(target)) : null;
				if (!Objects.equals(oldHash, item.targetSha256())) throw fail("MODEL_IMPORT_STALE", "A destination changed while staging");
				if (oldHash != null) {
					byte[] backup = tasks.bytes(target);
					if (!oldHash.equals(BlockbenchModelingService.hash(backup))) throw fail("MODEL_IMPORT_STALE", "A destination changed while backing up");
					tasks.write(directory.resolve("import-backup/" + index), backup);
				}
				tasks.write(directory.resolve("import-staged/" + index), input);
				JsonObject record = new JsonObject();
				record.addProperty("targetRelativePath", item.targetRelativePath()); record.addProperty("oldSha256", oldHash);
				record.addProperty("sha256", item.sourceSha256()); record.addProperty("index", index++);
				journal.add(record);
			}
			RecoveryPoint recovery = history.createRecoveryPoint(new RecoveryPointRequest("Before modeling import", actor, plan.taskId().toString(), RecoveryPointSource.ASSET));
			task.addProperty("importRecoveryPointId", recovery.id()); task.addProperty("importToken", plan.token());
			task.add("importFiles", journal); task.addProperty("state", "importing");
			tasks.save(task, "import_begin", actor);
			AssetImportService writer = new AssetImportService(assets, history);
			for (JsonElement raw : journal) {
				JsonObject item = raw.getAsJsonObject(); int number = item.get("index").getAsInt();
				beforeWrite.accept(number);
				String relative = item.get("targetRelativePath").getAsString();
				Path target = tasks.safe(root.resolve(relative));
				String old = item.get("oldSha256").isJsonNull() ? null : item.get("oldSha256").getAsString();
				if (!Objects.equals(old, Files.exists(target) ? BlockbenchModelingService.hash(tasks.bytes(target)) : null))
					throw fail("MODEL_IMPORT_STALE", "A destination changed before writing");
				if (Objects.equals(old, item.get("sha256").getAsString())) continue;
				Path staged = tasks.safe(directory.resolve("import-staged/" + number));
				if (!item.get("sha256").getAsString().equals(BlockbenchModelingService.hash(tasks.bytes(staged))))
					throw fail("MODEL_IMPORT_STAGE_CHANGED", "A staged export changed");
				var imported = writer.writeSourceToTarget(staged, relative);
				if (!item.get("sha256").getAsString().equals(imported.sha256())) throw fail("MODEL_IMPORT_STAGE_CHANGED", "A staged export changed during writing");
			}
			revisionAttempted = true;
			revision.commit();
			task.addProperty("state", "imported"); task.addProperty("importedRevision", committedRevision);
			tasks.save(task, "import_complete", actor);
			return tasks.projection(task);
		} catch (Exception exception) {
			if (revisionAttempted) rollbackRevision(revision);
			if ("importing".equals(tasks.read(plan.taskId()).get("state").getAsString())) {
				try { recover(plan.taskId(), actor); }
				catch (RuntimeException recovery) { throw fail("MODEL_IMPORT_RECOVERY_REQUIRED", "Import stopped; its journal and backups are preserved. Resolve conflicts and recover this task"); }
			}
			if (exception instanceof BlockbenchBridgeException bridge) throw bridge;
			throw fail("MODEL_IMPORT_FAILED", "Import failed; restored destinations and preserved the editing candidate");
		}
	}

	public JsonObject replay(UUID taskId, String token) {
		JsonObject task = tasks.read(taskId);
		if (!"imported".equals(task.get("state").getAsString()) || !token.equals(task.get("importToken").getAsString()))
			throw fail("MODEL_IMPORT_RECEIPT", "This task has no matching successful import receipt");
		for (JsonElement raw : task.getAsJsonArray("importFiles")) {
			JsonObject item = raw.getAsJsonObject();
			Path target = journalTarget(item);
			if (!Files.isRegularFile(target) || !item.get("sha256").getAsString().equals(BlockbenchModelingService.hash(tasks.bytes(target))))
				throw fail("MODEL_IMPORTED_FILES_CHANGED", "Previously imported files have changed; inspect the workspace before retrying");
		}
		return tasks.projection(task);
	}

	public List<String> modelResources(UUID taskId) {
		JsonObject task = tasks.read(taskId);
		if (!"imported".equals(task.get("state").getAsString())) throw fail("MODEL_NOT_IMPORTED", "Import the modeling result before binding it to an element");
		replay(taskId, task.get("importToken").getAsString());
		List<String> resources = new ArrayList<>();
		for (JsonElement raw : task.getAsJsonArray("importFiles")) {
			var match = OUTPUT.matcher(raw.getAsJsonObject().get("targetRelativePath").getAsString());
			if (match.matches() && match.group(2).equals("models")) resources.add(match.group(1) + ":" + match.group(3));
		}
		return List.copyOf(resources);
	}

	public JsonObject recover(UUID taskId, Actor actor) { return recover(taskId, actor, NO_REVISION); }

	public JsonObject recover(UUID taskId, Actor actor, RevisionCommit revision) {
		JsonObject task = tasks.read(taskId);
		if (!"importing".equals(task.get("state").getAsString())) throw fail("MODEL_RECOVERY_NOT_REQUIRED", "This task has no interrupted import");
		Path directory = tasks.taskDirectory(taskId);
		JsonArray files = task.getAsJsonArray("importFiles");
		// Verify every target and backup before restoring anything; preserve edits made by somebody else.
		for (JsonElement raw : files) {
			JsonObject item = raw.getAsJsonObject(); Path target = journalTarget(item);
			if (Files.exists(target) && !Files.isRegularFile(target)) throw fail("MODEL_RECOVERY_CONFLICT", "An import destination is now a directory");
			String old = item.get("oldSha256").isJsonNull() ? null : item.get("oldSha256").getAsString();
			String actual = Files.isRegularFile(target) ? BlockbenchModelingService.hash(tasks.bytes(target)) : null;
			if (!Objects.equals(actual, old) && !Objects.equals(actual, item.get("sha256").getAsString()))
				throw fail("MODEL_RECOVERY_CONFLICT", "A destination has an unrelated change; import recovery will not overwrite it");
			if (old != null && !old.equals(BlockbenchModelingService.hash(tasks.bytes(backup(directory, item)))))
				throw fail("MODEL_RECOVERY_BACKUP_CHANGED", "An import backup is missing or changed");
		}
		boolean revisionAttempted = false;
		try {
			for (JsonElement raw : files) {
				JsonObject item = raw.getAsJsonObject(); Path target = journalTarget(item);
				String actual = Files.isRegularFile(target) ? BlockbenchModelingService.hash(tasks.bytes(target)) : null;
				String old = item.get("oldSha256").isJsonNull() ? null : item.get("oldSha256").getAsString();
				if ((Files.exists(target) && !Files.isRegularFile(target))
						|| (!Objects.equals(actual, old) && !Objects.equals(actual, item.get("sha256").getAsString())))
					throw fail("MODEL_RECOVERY_CONFLICT", "An import destination changed during recovery");
				if (item.get("oldSha256").isJsonNull()) Files.deleteIfExists(target);
				else tasks.write(target, tasks.bytes(backup(directory, item)));
			}
			revisionAttempted = true;
			revision.commit();
			task.addProperty("state", "ready_to_import"); tasks.save(task, "import_recovered", actor);
			return tasks.projection(task);
		} catch (Exception exception) {
			if (revisionAttempted) rollbackRevision(revision);
			if (exception instanceof BlockbenchBridgeException bridge) throw bridge;
			throw fail("MODEL_IMPORT_RECOVERY_REQUIRED", "Recovery was interrupted; retry with the preserved journal");
		}
	}

	private static void rollbackRevision(RevisionCommit revision) {
		try { revision.rollback(); }
		catch (Exception exception) { throw fail("MODEL_IMPORT_RECOVERY_REQUIRED", "Workspace revision recovery failed; the import journal is preserved"); }
	}

	private Path journalTarget(JsonObject item) {
		String path = item.get("targetRelativePath").getAsString();
		if (path.contains("..") || !(OUTPUT.matcher(path).matches() || path.matches("(?:models/|assets/|src/main/resources/assets/)[a-z0-9_./-]+\\.bbmodel")))
			throw fail("MODEL_JOURNAL_INVALID", "The import journal has an invalid destination");
		return tasks.safe(root.resolve(path));
	}
	private Path backup(Path directory, JsonObject item) {
		int index = item.get("index").getAsInt();
		if (index < 0 || index >= 64) throw fail("MODEL_JOURNAL_INVALID", "Invalid import backup index");
		return tasks.safe(directory.resolve("import-backup/" + index));
	}

	private void validateModel(String id, JsonObject model, Map<String, JsonObject> models, Set<String> textures, Set<String> visiting) {
		model = inheritedModel(id, model, models, visiting);
		if (model.has("textures")) for (var entry : model.getAsJsonObject("textures").entrySet()) {
			String value = entry.getValue().getAsString(); Set<String> aliases = new HashSet<>();
			while (value.startsWith("#")) {
				String alias = value.substring(1);
				if (!aliases.add(alias)) throw fail("MODEL_TEXTURE_CYCLE", "An exported model has cyclic texture variables");
				if (!model.getAsJsonObject("textures").has(alias)) throw fail("MODEL_TEXTURE_MISSING", "A texture variable is unresolved: " + alias);
				value = model.getAsJsonObject("textures").get(alias).getAsString();
			}
			String texture = resource(value);
			validateTextureAtlasPath(texture);
			if (!textures.contains(texture) && !exists("textures", texture, ".png")) throw fail("MODEL_TEXTURE_MISSING", "An exported texture is missing: " + texture);
		}
		if (model.has("elements")) {
			if (!model.get("elements").isJsonArray() || model.getAsJsonArray("elements").isEmpty()) throw fail("MODEL_GAME_GEOMETRY", "Exported geometry must be a nonempty array");
			for (JsonElement raw : model.getAsJsonArray("elements")) {
				JsonObject element = raw.getAsJsonObject();
				for (String field : List.of("from", "to")) {
					JsonArray vector = element.getAsJsonArray(field);
					if (vector == null || vector.size() != 3) throw fail("MODEL_GAME_GEOMETRY", "Exported cube coordinates must have three numbers");
					for (JsonElement coordinate : vector) if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber() || !Double.isFinite(coordinate.getAsDouble()))
						throw fail("MODEL_GAME_GEOMETRY", "Exported coordinates must be finite numbers");
				}
				if (!element.has("faces") || !element.get("faces").isJsonObject()) throw fail("MODEL_GAME_GEOMETRY", "Exported cubes must declare their faces");
				for (var face : element.getAsJsonObject("faces").entrySet()) {
					if (!Set.of("up", "down", "north", "south", "east", "west").contains(face.getKey())) throw fail("MODEL_GAME_GEOMETRY", "Unknown cube face");
					String texture = face.getValue().getAsJsonObject().get("texture").getAsString();
					String resolved = resolveTexture(texture, model, models, new HashSet<>());
					validateTextureAtlasPath(resolved);
					if (!textures.contains(resolved) && !exists("textures", resolved, ".png")) throw fail("MODEL_TEXTURE_MISSING", "An exported face texture is missing: " + resolved);
				}
			}
		}
	}
	private static void validateTextureAtlasPath(String texture) {
		if (texture.startsWith("minecraft:")) return;
		String path = texture.substring(texture.indexOf(':') + 1);
		if (!path.startsWith("block/") && !path.startsWith("item/"))
			throw fail("MODEL_TEXTURE_ATLAS_PATH", "Export custom model textures under textures/block/ or textures/item/ and update their model references; custom atlas definitions are not supported by this workflow: " + texture);
	}
	private JsonObject inheritedModel(String id, JsonObject model, Map<String, JsonObject> models, Set<String> visiting) {
		if (!visiting.add(id)) throw fail("MODEL_PARENT_CYCLE", "Exported models have cyclic parents");
		if (visiting.size() > 64) throw fail("MODEL_PARENT_DEPTH", "Model inheritance exceeds 64 levels");
		JsonObject result = model.deepCopy();
		if (model.has("parent")) {
			String parent = resource(model.get("parent").getAsString());
			JsonObject parentModel = models.get(parent);
			if (parentModel == null && !parent.startsWith("minecraft:")) {
				Path file = existingResource("models", parent, ".json");
				if (file == null) throw fail("MODEL_PARENT_MISSING", "A custom parent model is missing: " + parent);
				try { parentModel = JsonParser.parseString(new String(tasks.bytes(file), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(); }
				catch (RuntimeException exception) { throw fail("MODEL_OUTPUT_JSON", "A custom parent is not a valid JSON object: " + parent); }
			}
			if (parentModel != null) {
				JsonObject inherited = inheritedModel(parent, parentModel, models, visiting);
				JsonObject merged = inherited.has("textures") ? inherited.getAsJsonObject("textures").deepCopy() : new JsonObject();
				if (model.has("textures")) model.getAsJsonObject("textures").entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue().deepCopy()));
				result.add("textures", merged);
				if (!model.has("elements") && inherited.has("elements")) result.add("elements", inherited.get("elements").deepCopy());
			}
		}
		visiting.remove(id);
		return result;
	}
	private String resolveTexture(String texture, JsonObject model, Map<String, JsonObject> models, Set<String> seen) {
		if (!texture.startsWith("#")) return resource(texture);
		if (!seen.add(texture)) throw fail("MODEL_TEXTURE_CYCLE", "An exported face has cyclic texture variables");
		String name = texture.substring(1);
		if (model.has("textures") && model.getAsJsonObject("textures").has(name)) return resolveTexture(model.getAsJsonObject("textures").get(name).getAsString(), model, models, seen);
		if (model.has("parent")) {
			String parent = resource(model.get("parent").getAsString());
			if (models.containsKey(parent)) { seen.remove(texture); return resolveTexture(texture, models.get(parent), models, seen); }
		}
		throw fail("MODEL_TEXTURE_MISSING", "An exported face texture variable is unresolved: " + name);
	}
	private void validateModelLinks(JsonElement value, Map<String, JsonObject> models) {
		if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> validateModelLinks(child, models));
		if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
			if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) {
				String id = resource(entry.getValue().getAsString());
				if (!models.containsKey(id) && !exists("models", id, ".json")) throw fail("MODEL_REFERENCE_MISSING", "A blockstate/item model reference is missing: " + id);
			} else validateModelLinks(entry.getValue(), models);
		}
	}
	private boolean exists(String kind, String id, String extension) {
		if (id.startsWith("minecraft:")) return true;
		return existingResource(kind, id, extension) != null;
	}
	private Path existingResource(String kind, String id, String extension) {
		String path = id.replace(':', '/'); int separator = path.indexOf('/');
		String relative = "assets/" + path.substring(0, separator) + "/" + kind + "/" + path.substring(separator + 1) + extension;
		for (String prefix : List.of("src/main/resources/", "src/main/", "")) {
			Path file = tasks.safe(root.resolve(prefix + relative));
			if (Files.isRegularFile(file)) return file;
		}
		return null;
	}
	private static String resource(String value) {
		String id = value.contains(":") ? value : "minecraft:" + value;
		if (!RESOURCE.matcher(id).matches() || id.contains("..") || id.contains("//")) throw fail("MODEL_RESOURCE_INVALID", "An exported resource identifier is invalid");
		return id;
	}
	private static BlockbenchBridgeException fail(String code, String message) { return new BlockbenchBridgeException(code, message); }
}
