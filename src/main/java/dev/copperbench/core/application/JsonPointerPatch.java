package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 6901 setter used for typed domain field changes. */
final class JsonPointerPatch {

	private JsonPointerPatch() {
	}

	static void set(JsonObject root, String pointer, JsonElement value) {
		List<String> tokens = tokens(pointer);
		if (tokens.isEmpty())
			throw new IllegalArgumentException("The document root cannot be replaced");
		JsonElement current = root;
		for (int index = 0; index < tokens.size() - 1; index++) {
			String token = tokens.get(index);
			String nextToken = tokens.get(index + 1);
			if (current.isJsonObject()) {
				JsonObject object = current.getAsJsonObject();
				if (!object.has(token) || object.get(token).isJsonNull())
					object.add(token, looksLikeIndex(nextToken) ? new JsonArray() : new JsonObject());
				current = object.get(token);
			} else if (current.isJsonArray()) {
				JsonArray array = current.getAsJsonArray();
				int arrayIndex = parseIndex(token, array.size(), true);
				while (array.size() <= arrayIndex)
					array.add(JsonNull.INSTANCE);
				if (array.get(arrayIndex).isJsonNull())
					array.set(arrayIndex, looksLikeIndex(nextToken) ? new JsonArray() : new JsonObject());
				current = array.get(arrayIndex);
			} else {
				throw new IllegalArgumentException("Pointer traverses a scalar value");
			}
		}

		String last = tokens.getLast();
		JsonElement replacement = value == null ? JsonNull.INSTANCE : value.deepCopy();
		if (current.isJsonObject()) {
			current.getAsJsonObject().add(last, replacement);
		} else if (current.isJsonArray()) {
			JsonArray array = current.getAsJsonArray();
			int arrayIndex = parseIndex(last, array.size(), true);
			while (array.size() < arrayIndex)
				array.add(JsonNull.INSTANCE);
			if (arrayIndex == array.size())
				array.add(replacement);
			else
				array.set(arrayIndex, replacement);
		} else {
			throw new IllegalArgumentException("Pointer parent is a scalar value");
		}
	}

	private static List<String> tokens(String pointer) {
		if (pointer == null || !pointer.startsWith("/") || pointer.length() == 1)
			throw new IllegalArgumentException("A non-root JSON Pointer is required");
		List<String> tokens = new ArrayList<>();
		for (String token : pointer.substring(1).split("/", -1))
			tokens.add(token.replace("~1", "/").replace("~0", "~"));
		return tokens;
	}

	private static boolean looksLikeIndex(String token) {
		return token.matches("0|[1-9][0-9]*");
	}

	private static int parseIndex(String token, int size, boolean allowAppend) {
		if (!looksLikeIndex(token))
			throw new IllegalArgumentException("Invalid array index: " + token);
		int index = Integer.parseInt(token);
		if (index > size || (!allowAppend && index == size))
			throw new IllegalArgumentException("Array index is out of bounds: " + token);
		return index;
	}
}
