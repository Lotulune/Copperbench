package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** A bounded, signed-in-plan workspace file mutation used by local reusable templates. */
public record WorkspacePlanArtifact(String relativePath, String sha256, byte[] content) {

	public static final int MAX_SINGLE_ARTIFACT_BYTES = 4 * 1024 * 1024;
	public static final int MAX_TOTAL_ARTIFACT_BYTES = 16 * 1024 * 1024;
	public static final int MAX_ARTIFACTS = 64;

	public WorkspacePlanArtifact {
		relativePath = normalizeRelativePath(relativePath);
		Objects.requireNonNull(sha256, "sha256");
		if (!sha256.matches("^[a-f0-9]{64}$")) throw new IllegalArgumentException("Artifact SHA-256 is invalid");
		content = Objects.requireNonNull(content, "content").clone();
		if (content.length > MAX_SINGLE_ARTIFACT_BYTES)
			throw new IllegalArgumentException("Template asset exceeds the per-file size limit");
		if (!sha256.equals(sha256(content))) throw new IllegalArgumentException("Template asset SHA-256 does not match content");
	}

	@Override public byte[] content() {
		return content.clone();
	}

	public static WorkspacePlanArtifact of(String relativePath, byte[] content) {
		byte[] copy = Objects.requireNonNull(content, "content").clone();
		return new WorkspacePlanArtifact(relativePath, sha256(copy), copy);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("relativePath", relativePath);
		json.addProperty("sha256", sha256);
		json.addProperty("size", content.length);
		json.addProperty("contentBase64", Base64.getEncoder().encodeToString(content));
		return json;
	}

	public static List<WorkspacePlanArtifact> fromJson(JsonArray array) {
		if (array == null || array.isEmpty()) return List.of();
		if (array.size() > MAX_ARTIFACTS) throw new IllegalArgumentException("Too many template assets in workspace plan");
		List<WorkspacePlanArtifact> result = new ArrayList<>();
		long total = 0;
		for (JsonElement raw : array) {
			if (!raw.isJsonObject()) throw new IllegalArgumentException("Template asset entry must be an object");
			JsonObject json = raw.getAsJsonObject();
			String path = requiredString(json, "relativePath");
			String hash = requiredString(json, "sha256").toLowerCase(Locale.ROOT);
			String encoded = requiredString(json, "contentBase64");
			byte[] content;
			try {
				content = Base64.getDecoder().decode(encoded);
			} catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException("Template asset contentBase64 is invalid", exception);
			}
			WorkspacePlanArtifact artifact = new WorkspacePlanArtifact(path, hash, content);
			if (json.has("size") && json.get("size").getAsLong() != content.length)
				throw new IllegalArgumentException("Template asset size does not match content");
			total += content.length;
			if (total > MAX_TOTAL_ARTIFACT_BYTES) throw new IllegalArgumentException("Template assets exceed total size limit");
			result.add(artifact);
		}
		return List.copyOf(result);
	}

	public Path resolve(Path workspaceRoot) {
		Path root = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
		Path target = root.resolve(relativePath).normalize();
		if (!target.startsWith(root) || target.startsWith(root.resolve(".copperbench")))
			throw new IllegalArgumentException("Template asset path escapes the workspace");
		return target;
	}

	public static String normalizeRelativePath(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("Template asset path is required");
		Path requested = Path.of(value.replace('\\', '/')).normalize();
		if (requested.isAbsolute() || requested.startsWith(".."))
			throw new IllegalArgumentException("Template asset path must be workspace-relative");
		String normalized = requested.toString().replace('\\', '/');
		if (normalized.isBlank() || normalized.startsWith(".copperbench/") || !isAssetRoot(normalized))
			throw new IllegalArgumentException("Template asset path is outside known asset roots: " + normalized);
		return normalized;
	}

	private static boolean isAssetRoot(String relative) {
		return relative.startsWith("assets/") || relative.startsWith("models/") || relative.startsWith("resourcepacks/")
				|| relative.startsWith("src/main/resources/assets/") || relative.startsWith("src/main/assets/")
				|| relative.equals("pack.mcmeta") || relative.equals("pack.png")
				|| relative.equals("src/main/pack.mcmeta") || relative.equals("src/main/pack.png");
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static String requiredString(JsonObject json, String property) {
		if (!json.has(property) || !json.get(property).isJsonPrimitive() || json.get(property).getAsString().isBlank())
			throw new IllegalArgumentException(property + " is required");
		return json.get(property).getAsString();
	}
}

