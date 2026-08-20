package dev.copperbench.core.workspace;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Merges a newly serialized model into its previous JSON document without discarding unknown object fields. */
public final class UnknownFieldPreservingJsonMerge {

	private static final List<String> ARRAY_IDENTITY_FIELDS = List.of("id", "name", "rid");

	private UnknownFieldPreservingJsonMerge() {
	}

	public static String mergeExistingFile(Path file, String serializedModel, Gson gson) throws IOException {
		return mergeExistingFile(file, serializedModel, serializedModel, gson);
	}

	public static String mergeExistingFile(Path file, String serializedModel, String serializedKnownShape, Gson gson)
			throws IOException {
		JsonElement generated = JsonParser.parseString(serializedModel);
		if (!Files.isRegularFile(file))
			return gson.toJson(generated);

		JsonElement existing = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
		JsonElement merged = merge(existing, generated);
		removeKnownNulls(merged, generated, JsonParser.parseString(serializedKnownShape));
		return gson.toJson(merged);
	}

	public static JsonElement merge(JsonElement existing, JsonElement generated) {
		if (existing != null && existing.isJsonObject() && generated.isJsonObject())
			return mergeObjects(existing.getAsJsonObject(), generated.getAsJsonObject());
		if (existing != null && existing.isJsonArray() && generated.isJsonArray())
			return mergeArrays(existing.getAsJsonArray(), generated.getAsJsonArray());
		return generated.deepCopy();
	}

	private static JsonObject mergeObjects(JsonObject existing, JsonObject generated) {
		JsonObject merged = existing.deepCopy();
		for (var entry : generated.entrySet()) {
			JsonElement previous = existing.get(entry.getKey());
			merged.add(entry.getKey(), previous == null ? entry.getValue().deepCopy()
					: merge(previous, entry.getValue()));
		}
		return merged;
	}

	private static JsonArray mergeArrays(JsonArray existing, JsonArray generated) {
		String identityField = commonIdentityField(existing, generated);
		JsonArray merged = new JsonArray();
		for (int index = 0; index < generated.size(); index++) {
			JsonElement generatedValue = generated.get(index);
			JsonElement previous = identityField == null ? samePosition(existing, generated, index)
					: findByIdentity(existing, identityField,
							generatedValue.getAsJsonObject().get(identityField).getAsString());
			merged.add(previous == null ? generatedValue.deepCopy() : merge(previous, generatedValue));
		}
		return merged;
	}

	private static JsonElement samePosition(JsonArray existing, JsonArray generated, int index) {
		if (existing.size() != generated.size() || index >= existing.size())
			return null;
		JsonElement previous = existing.get(index);
		JsonElement current = generated.get(index);
		if (current.isJsonObject())
			return null;
		return previous.isJsonObject() == current.isJsonObject() && previous.isJsonArray() == current.isJsonArray()
				? previous : null;
	}

	private static String commonIdentityField(JsonArray existing, JsonArray generated) {
		for (String field : ARRAY_IDENTITY_FIELDS) {
			if (hasUniqueObjectIdentity(existing, field) && hasUniqueObjectIdentity(generated, field))
				return field;
		}
		return null;
	}

	private static boolean hasUniqueObjectIdentity(JsonArray values, String field) {
		java.util.HashSet<String> identities = new java.util.HashSet<>();
		for (JsonElement value : values) {
			if (!value.isJsonObject() || !value.getAsJsonObject().has(field)
					|| !value.getAsJsonObject().get(field).isJsonPrimitive()
					|| !identities.add(value.getAsJsonObject().get(field).getAsString()))
				return false;
		}
		return !values.isEmpty();
	}

	private static JsonElement findByIdentity(JsonArray values, String field, String identity) {
		for (JsonElement value : values) {
			if (value.isJsonObject() && value.getAsJsonObject().has(field)
					&& identity.equals(value.getAsJsonObject().get(field).getAsString()))
				return value;
		}
		return null;
	}

	private static void removeKnownNulls(JsonElement merged, JsonElement generated, JsonElement knownShape) {
		if (merged.isJsonObject() && generated.isJsonObject() && knownShape.isJsonObject()) {
			JsonObject mergedObject = merged.getAsJsonObject();
			JsonObject generatedObject = generated.getAsJsonObject();
			for (var entry : knownShape.getAsJsonObject().entrySet()) {
				if (!generatedObject.has(entry.getKey()) && entry.getValue().isJsonNull()) {
					mergedObject.remove(entry.getKey());
				} else if (generatedObject.has(entry.getKey()) && mergedObject.has(entry.getKey())) {
					removeKnownNulls(mergedObject.get(entry.getKey()), generatedObject.get(entry.getKey()),
							entry.getValue());
				}
			}
		} else if (merged.isJsonArray() && generated.isJsonArray() && knownShape.isJsonArray()) {
			int size = Math.min(merged.getAsJsonArray().size(),
					Math.min(generated.getAsJsonArray().size(), knownShape.getAsJsonArray().size()));
			for (int index = 0; index < size; index++)
				removeKnownNulls(merged.getAsJsonArray().get(index), generated.getAsJsonArray().get(index),
						knownShape.getAsJsonArray().get(index));
		}
	}
}
