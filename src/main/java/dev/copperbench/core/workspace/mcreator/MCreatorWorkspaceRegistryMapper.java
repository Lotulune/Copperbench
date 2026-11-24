package dev.copperbench.core.workspace.mcreator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.copperbench.core.workspace.UnknownFieldPreservingJsonStore;
import net.mcreator.minecraft.TagType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceFileManager;
import net.mcreator.workspace.elements.SoundElement;
import net.mcreator.workspace.elements.TagElement;
import net.mcreator.workspace.elements.VariableElement;
import net.mcreator.workspace.elements.VariableType;
import net.mcreator.workspace.elements.VariableTypeLoader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Projects stable registry identities while keeping MCreator's live collections authoritative for generation. */
final class MCreatorWorkspaceRegistryMapper {

	private MCreatorWorkspaceRegistryMapper() {
	}

	static void projectIntoDocument(Workspace workspace, UUID workspaceId, JsonObject document) {
		JsonObject product = document.has(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE)
				&& document.get(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE).isJsonObject()
				? document.getAsJsonObject(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE).deepCopy()
				: new JsonObject();
		JsonObject existing = product.has("registries") && product.get("registries").isJsonObject()
				? product.getAsJsonObject("registries") : new JsonObject();
		JsonObject registries = new JsonObject();
		registries.add("variables", projectVariables(workspace, workspaceId, entries(existing, "variables")));
		registries.add("tags", projectTags(workspace, workspaceId, entries(existing, "tags")));
		registries.add("languageKeys", projectLanguage(workspace, workspaceId, entries(existing, "languageKeys")));
		registries.add("sounds", projectSounds(workspace, workspaceId, entries(existing, "sounds")));
		product.add("registries", registries);
		document.add(UnknownFieldPreservingJsonStore.PRODUCT_NAMESPACE, product);
	}

	static void synchronize(Workspace workspace, JsonObject registries) {
		for (VariableElement variable : List.copyOf(workspace.getVariableElements()))
			workspace.removeVariableElement(variable);
		for (JsonElement raw : entries(registries, "variables")) {
			JsonObject item = raw.getAsJsonObject();
			VariableElement variable = new VariableElement(required(item, "name"));
			var type = VariableTypeLoader.INSTANCE.fromName(string(item, "dataType", "number"));
			if (type == null) throw new IllegalArgumentException("Unsupported variable data type: "
					+ string(item, "dataType", "number"));
			variable.setType(type);
			variable.setScope(scope(string(item, "scope", "global")));
			if (item.has("value") && !item.get("value").isJsonNull())
				variable.setValue(WorkspaceFileManager.gson.fromJson(item.get("value"), Object.class));
			workspace.addVariableElement(variable);
		}

		workspace.getTagElements().clear();
		for (JsonElement raw : entries(registries, "tags")) {
			JsonObject item = raw.getAsJsonObject();
			TagType type = TagType.valueOf(string(item, "category", "items").toUpperCase(Locale.ROOT));
			String namespace = string(item, "namespace", "mod");
			TagElement tag = new TagElement(type, namespace + ":" + required(item, "name"));
			ArrayList<TagElement.Entry> members = new ArrayList<>();
			if (item.has("members") && item.get("members").isJsonArray())
				item.getAsJsonArray("members").forEach(member -> members.add(TagElement.Entry.unmanaged(member.getAsString())));
			workspace.getTagElements().put(tag, members);
		}

		workspace.getLanguageMap().clear();
		workspace.getLanguageMap().put("en_us", new LinkedHashMap<>());
		for (JsonElement raw : entries(registries, "languageKeys")) {
			JsonObject item = raw.getAsJsonObject();
			String key = required(item, "key");
			JsonObject translations = item.has("translations") && item.get("translations").isJsonObject()
					? item.getAsJsonObject("translations") : new JsonObject();
			for (var translation : translations.entrySet())
				workspace.getLanguageMap().computeIfAbsent(translation.getKey(), ignored -> new LinkedHashMap<>())
						.put(key, translation.getValue().getAsString());
			for (var language : workspace.getLanguageMap().values()) language.putIfAbsent(key, "");
		}

		if (registries.has("sounds") && registries.get("sounds").isJsonArray()) synchronizeSounds(workspace, registries);
		workspace.markDirty();
	}

	/**
	 * Updates sound events in place. Removing one only unregisters it: its .ogg files stay, because they are workspace
	 * assets with their own reviewed deletion.
	 */
	private static void synchronizeSounds(Workspace workspace, JsonObject registries) {
		Map<String, JsonObject> wanted = new LinkedHashMap<>();
		for (JsonElement raw : entries(registries, "sounds")) wanted.put(required(raw.getAsJsonObject(), "name"), raw.getAsJsonObject());
		for (SoundElement sound : List.copyOf(workspace.getSoundElements()))
			if (!wanted.containsKey(sound.getName())) workspace.unregisterSoundElement(sound);
		Map<String, SoundElement> existing = new LinkedHashMap<>();
		workspace.getSoundElements().forEach(sound -> existing.put(sound.getName(), sound));
		for (var entry : wanted.entrySet()) {
			JsonObject item = entry.getValue();
			List<String> files = new ArrayList<>();
			if (item.has("files") && item.get("files").isJsonArray()) item.getAsJsonArray("files").forEach(file -> files.add(file.getAsString()));
			String subtitle = string(item, "subtitle", "");
			SoundElement sound = existing.get(entry.getKey());
			if (sound == null) {
				workspace.addSoundElement(new SoundElement(entry.getKey(), files, string(item, "category", "neutral"),
						subtitle.isBlank() ? null : subtitle));
			} else {
				sound.setFiles(files);
				sound.setCategory(string(item, "category", "neutral"));
				sound.setSubtitle(subtitle.isBlank() ? null : subtitle);
			}
		}
	}

	private static JsonArray projectSounds(Workspace workspace, UUID workspaceId, JsonArray existing) {
		Map<String, JsonObject> byName = index(existing, "name");
		JsonArray result = new JsonArray();
		for (SoundElement sound : workspace.getSoundElements()) {
			JsonObject item = copy(byName.get(sound.getName()));
			identity(item, workspaceId, "sound", sound.getName());
			item.addProperty("kind", "sound");
			item.addProperty("name", sound.getName());
			JsonArray files = new JsonArray();
			sound.getFiles().forEach(files::add);
			item.add("files", files);
			item.addProperty("category", sound.getCategory());
			if (sound.getSubtitle() != null && !sound.getSubtitle().isBlank()) item.addProperty("subtitle", sound.getSubtitle());
			else item.remove("subtitle");
			item.add("support", support("supported", "SOUND_SUPPORTED"));
			result.add(item);
		}
		return result;
	}

	private static JsonArray projectVariables(Workspace workspace, UUID workspaceId, JsonArray existing) {
		Map<String, JsonObject> byName = index(existing, "name");
		JsonArray result = new JsonArray();
		for (VariableElement variable : workspace.getVariableElements()) {
			JsonObject item = copy(byName.get(variable.getName()));
			identity(item, workspaceId, "variable", variable.getName());
			item.addProperty("kind", "variable");
			item.addProperty("name", variable.getName());
			item.addProperty("dataType", variable.getTypeString());
			item.addProperty("scope", scope(variable.getScope()));
			item.add("value", WorkspaceFileManager.gson.toJsonTree(variable.getValue()));
			item.add("support", support("supported", "VARIABLE_TYPE_SUPPORTED"));
			result.add(item);
		}
		return result;
	}

	private static JsonArray projectTags(Workspace workspace, UUID workspaceId, JsonArray existing) {
		Map<String, JsonObject> byIdentity = new LinkedHashMap<>();
		for (JsonElement raw : existing) {
			JsonObject item = raw.getAsJsonObject();
			byIdentity.put(string(item, "category", "") + "\n" + string(item, "namespace", "") + ":"
					+ string(item, "name", ""), item);
		}
		JsonArray result = new JsonArray();
		for (var tag : workspace.getTagElements().entrySet()) {
			TagElement value = tag.getKey();
			String category = value.type().name().toLowerCase(Locale.ROOT);
			String key = category + "\n" + value.getMCreatorNamespace() + ":" + value.getName();
			JsonObject item = copy(byIdentity.get(key));
			identity(item, workspaceId, "tag", key);
			item.addProperty("kind", "tag");
			item.addProperty("category", category);
			item.addProperty("namespace", value.getMCreatorNamespace());
			item.addProperty("name", value.getName());
			JsonArray members = new JsonArray();
			tag.getValue().forEach(member -> members.add(member.name()));
			item.add("members", members);
			item.add("support", support("supported", "TAG_TYPE_SUPPORTED"));
			result.add(item);
		}
		return result;
	}

	private static JsonArray projectLanguage(Workspace workspace, UUID workspaceId, JsonArray existing) {
		Map<String, JsonObject> byKey = index(existing, "key");
		Map<String, JsonObject> translations = new LinkedHashMap<>();
		for (var language : workspace.getLanguageMap().entrySet()) {
			for (var value : language.getValue().entrySet())
				translations.computeIfAbsent(value.getKey(), ignored -> new JsonObject())
						.addProperty(language.getKey(), value.getValue());
		}
		JsonArray result = new JsonArray();
		for (var value : translations.entrySet()) {
			JsonObject item = copy(byKey.get(value.getKey()));
			identity(item, workspaceId, "language_key", value.getKey());
			item.addProperty("kind", "language_key");
			item.addProperty("key", value.getKey());
			item.add("translations", value.getValue());
			result.add(item);
		}
		return result;
	}

	private static JsonArray entries(JsonObject owner, String key) {
		return owner != null && owner.has(key) && owner.get(key).isJsonArray()
				? owner.getAsJsonArray(key) : new JsonArray();
	}

	private static Map<String, JsonObject> index(JsonArray entries, String field) {
		Map<String, JsonObject> result = new LinkedHashMap<>();
		for (JsonElement raw : entries) {
			JsonObject item = raw.getAsJsonObject();
			result.put(string(item, field, ""), item);
		}
		return result;
	}

	private static void identity(JsonObject item, UUID workspaceId, String kind, String naturalKey) {
		if (!item.has("id")) item.addProperty("id", UUID.nameUUIDFromBytes(
				(workspaceId + "\n" + kind + "\n" + naturalKey).getBytes(StandardCharsets.UTF_8)).toString());
	}

	private static JsonObject support(String state, String reasonCode) {
		JsonObject support = new JsonObject();
		support.addProperty("state", state);
		support.addProperty("reasonCode", reasonCode);
		return support;
	}

	private static JsonObject copy(JsonObject value) { return value == null ? new JsonObject() : value.deepCopy(); }
	private static String required(JsonObject value, String key) {
		String result = string(value, key, "");
		if (result.isBlank()) throw new IllegalArgumentException(key + " is required");
		return result;
	}
	private static String string(JsonObject value, String key, String fallback) {
		return value != null && value.has(key) && value.get(key).isJsonPrimitive()
				? value.get(key).getAsString() : fallback;
	}
	private static String scope(VariableType.Scope scope) {
		return switch (scope) {
			case GLOBAL_MAP -> "map";
			case GLOBAL_WORLD -> "world";
			case GLOBAL_SESSION -> "global";
			case PLAYER_LIFETIME -> "player_lifetime";
			case PLAYER_PERSISTENT -> "player_persistent";
			case LOCAL -> "local";
		};
	}
	private static VariableType.Scope scope(String scope) {
		return switch (scope) {
			case "map" -> VariableType.Scope.GLOBAL_MAP;
			case "world" -> VariableType.Scope.GLOBAL_WORLD;
			case "player_lifetime" -> VariableType.Scope.PLAYER_LIFETIME;
			case "player_persistent" -> VariableType.Scope.PLAYER_PERSISTENT;
			case "local" -> VariableType.Scope.LOCAL;
			default -> VariableType.Scope.GLOBAL_SESSION;
		};
	}
}
