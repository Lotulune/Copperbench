package dev.copperbench.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Structural Blockbench reader shared by inspection and the asset graph. Never treats metadata as paths. */
public record BbmodelDocument(List<Texture> textures, Map<String, String> faceBindings, List<Issue> issues) {

	public enum ReferenceKind { EMBEDDED, LOCAL_FILE, RESOURCE_ID, EXTERNAL_URI }
	public record ImageReference(ReferenceKind kind, String value, String pointer) { }
	public record Texture(String identity, String pointer, List<ImageReference> images) { }
	public record Issue(String code, String location) { }

	public static BbmodelDocument parse(JsonObject root) {
		List<Texture> textures = new ArrayList<>();
		List<Issue> issues = new ArrayList<>();
		Map<String, String> indices = new LinkedHashMap<>();
		Map<String, String> aliases = new LinkedHashMap<>();
		JsonElement table = root.get("textures");
		if (table != null && table.isJsonArray()) {
			for (int index = 0; index < table.getAsJsonArray().size(); index++)
				texture(table.getAsJsonArray().get(index), Integer.toString(index), textures, indices, aliases, issues);
		} else if (table != null && table.isJsonObject()) {
			for (var entry : table.getAsJsonObject().entrySet())
				texture(entry.getValue(), entry.getKey(), textures, indices, aliases, issues);
		} else if (table != null) issues.add(new Issue("BBMODEL_TEXTURE_TABLE_INVALID", "/textures"));
		Map<String, String> bindings = new LinkedHashMap<>();
		JsonElement elements = root.get("elements");
		if (elements != null && elements.isJsonArray()) {
			for (int index = 0; index < elements.getAsJsonArray().size(); index++) {
				JsonElement entry = elements.getAsJsonArray().get(index);
				if (!entry.isJsonObject()) {
					issues.add(new Issue("BBMODEL_ELEMENT_INVALID", "/elements/" + index));
					continue;
				}
				JsonObject element = entry.getAsJsonObject();
				String identity = text(element, "uuid");
				if (identity == null) identity = "index:" + index;
				JsonElement faces = element.get("faces");
				if (faces != null && faces.isJsonObject()) for (var face : faces.getAsJsonObject().entrySet()) {
					String pointer = "/elements/" + index + "/faces/" + escape(face.getKey()) + "/texture";
					if (!face.getValue().isJsonObject()) {
						issues.add(new Issue("BBMODEL_FACE_INVALID", pointer));
						continue;
					}
					JsonElement value = face.getValue().getAsJsonObject().get("texture");
					String binding = binding(value, indices, aliases);
					if (binding == null) {
						issues.add(new Issue("BBMODEL_FACE_TEXTURE_UNRESOLVED", pointer));
						binding = "unresolved:" + value;
					}
					bindings.put(identity + "/" + face.getKey(), binding);
				}
				else if (faces != null) issues.add(new Issue("BBMODEL_FACES_INVALID", "/elements/" + index + "/faces"));
			}
		} else if (elements != null) issues.add(new Issue("BBMODEL_ELEMENTS_INVALID", "/elements"));
		return new BbmodelDocument(List.copyOf(textures), Map.copyOf(bindings), List.copyOf(issues));
	}

	private static String binding(JsonElement value, Map<String, String> indices, Map<String, String> aliases) {
		if (value == null || value.isJsonNull()) return "untextured";
		if (!value.isJsonPrimitive()) return null;
		var primitive = value.getAsJsonPrimitive();
		if (primitive.isBoolean()) return primitive.getAsBoolean() ? null : "disabled";
		String key = primitive.getAsString();
		if (primitive.isNumber()) return indices.get(key);
		if (key.startsWith("#")) key = key.substring(1);
		return aliases.containsKey(key) ? aliases.get(key) : indices.get(key);
	}

	private static void texture(JsonElement entry, String key, List<Texture> textures, Map<String, String> indices,
			Map<String, String> aliases, List<Issue> issues) {
		String pointer = "/textures/" + escape(key);
		List<ImageReference> images = new ArrayList<>();
		String identity = "slot:" + key;
		if (entry.isJsonObject()) {
			JsonObject object = entry.getAsJsonObject();
			String uuid = text(object, "uuid");
			String id = text(object, "id");
			identity = uuid != null ? "uuid:" + uuid : id != null ? "id:" + id : identity;
			if (uuid != null) alias(aliases, uuid, identity, pointer, issues);
			if (id != null) alias(aliases, id, identity, pointer, issues);
			// Blockbench loads relative_path, then path, then the embedded source fallback.
			for (String field : List.of("relative_path", "path", "source")) {
				JsonElement raw = object.get(field);
				String value = raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString() ? text(object, field) : null;
				if (value != null) images.add(image(value, pointer + "/" + field, issues));
				else if (raw != null && !raw.isJsonNull() && !(raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString()))
					issues.add(new Issue("BBMODEL_TEXTURE_SOURCE_INVALID", pointer + "/" + field));
			}
			if (object.has("layers") && !object.get("layers").isJsonNull())
				issues.add(new Issue("BBMODEL_TEXTURE_LAYERS_REVIEW_REQUIRED", pointer + "/layers"));
		} else if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
			images.add(image(entry.getAsString(), pointer, issues));
		} else issues.add(new Issue("BBMODEL_TEXTURE_INVALID", pointer));
		String finalIdentity = identity;
		if (textures.stream().anyMatch(texture -> texture.identity().equals(finalIdentity)))
			issues.add(new Issue("BBMODEL_TEXTURE_ID_DUPLICATED", pointer));
		if (images.isEmpty()) issues.add(new Issue("BBMODEL_TEXTURE_SOURCE_MISSING", pointer));
		indices.put(key, identity);
		textures.add(new Texture(identity, pointer, List.copyOf(images)));
	}

	private static void alias(Map<String, String> aliases, String key, String identity, String pointer, List<Issue> issues) {
		if (aliases.containsKey(key) && !identity.equals(aliases.get(key))) {
			aliases.put(key, null);
			issues.add(new Issue("BBMODEL_TEXTURE_ID_AMBIGUOUS", pointer));
		} else aliases.put(key, identity);
	}

	private static ImageReference image(String value, String pointer, List<Issue> issues) {
		if (value.startsWith("data:")) {
			try {
				int comma = value.indexOf(',');
				if (!value.startsWith("data:image/") || comma < 0 || !value.substring(0, comma).endsWith(";base64"))
					throw new IllegalArgumentException("Unsupported embedded image");
				byte[] bytes = Base64.getDecoder().decode(value.substring(comma + 1));
				if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) throw new IllegalArgumentException("Invalid image");
				return new ImageReference(ReferenceKind.EMBEDDED,
						HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), pointer);
			} catch (Exception exception) {
				issues.add(new Issue("BBMODEL_EMBEDDED_IMAGE_INVALID", pointer));
				return new ImageReference(ReferenceKind.EMBEDDED, "invalid", pointer);
			}
		}
		ReferenceKind kind = value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") ? ReferenceKind.EXTERNAL_URI
				: value.matches("^[a-z0-9_.-]+:[a-z0-9_./-]+$") && !value.matches("^[a-zA-Z]:[/\\\\].*")
				? ReferenceKind.RESOURCE_ID : value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")
				&& !value.matches("^[a-zA-Z]:[/\\\\].*") ? ReferenceKind.EXTERNAL_URI : ReferenceKind.LOCAL_FILE;
		return new ImageReference(kind, value.replace('\\', '/'), pointer);
	}

	static String text(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && !value.getAsJsonPrimitive().isBoolean()
				&& !value.getAsString().isBlank() ? value.getAsString() : null;
	}

	static String escape(String value) { return value.replace("~", "~0").replace("/", "~1"); }
}
