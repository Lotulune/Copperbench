package dev.copperbench.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Validates the semantic identity that must survive a Blockbench .bbmodel round trip. */
public final class BbmodelRoundTripService {
	private final AssetWorkspaceService assets;

	public BbmodelRoundTripService(AssetWorkspaceService assets) {
		this.assets = Objects.requireNonNull(assets, "assets");
	}

	public Snapshot inspect(String relativePath) {
		AssetDescriptor descriptor = assets.findByRelativePath(relativePath).orElseThrow(() ->
				new AssetPathViolationException("The .bbmodel is not indexed: " + relativePath));
		if (!descriptor.relativePath().toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
			throw new AssetPathViolationException("Only .bbmodel assets can be inspected");
		try {
			Path path = assets.resolveAuthorizedPath(descriptor.relativePath());
			JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
			BbmodelDocument model = BbmodelDocument.parse(root);
			List<Diagnostic> issues = assets.bbmodelDiagnostics(descriptor, model).stream()
					.map(issue -> new Diagnostic(issue.code(), issue.targetPath(), issue.severity())).toList();
			return new Snapshot(descriptor.id(), string(root, "meta", "format_version"),
					model.textures().stream().map(BbmodelDocument.Texture::identity).toList(),
					ids(root, "elements"), ids(root, "animations"), sha256(path), model.textures(), model.faceBindings(), issues);
		} catch (IOException | RuntimeException exception) {
			throw new AssetPathViolationException("The .bbmodel is invalid: " + descriptor.relativePath());
		}
	}

	public RoundTripReport compare(Snapshot before, Snapshot after) {
		Objects.requireNonNull(before, "before");
		Objects.requireNonNull(after, "after");
		List<Diagnostic> diagnostics = new ArrayList<>(after.diagnostics());
		if (!before.assetId().equals(after.assetId())) diagnostics.add(new Diagnostic("ASSET_ID_CHANGED", "assetId"));
		if (!Objects.equals(before.formatVersion(), after.formatVersion()))
			diagnostics.add(new Diagnostic("BBMODEL_FORMAT_CHANGED", "meta.format_version"));
		missing(before.textureReferences(), after.textureReferences(), "TEXTURE_REFERENCE_DROPPED", "textures", diagnostics);
		missing(before.elementIds(), after.elementIds(), "MODEL_ELEMENT_DROPPED", "elements", diagnostics);
		missing(before.animationIds(), after.animationIds(), "ANIMATION_REFERENCE_DROPPED", "animations", diagnostics);
		for (var texture : before.textures()) {
			var current = after.textures().stream().filter(value -> value.identity().equals(texture.identity())).findFirst();
			if (current.isPresent() && !imageValues(texture).equals(imageValues(current.get())))
				diagnostics.add(new Diagnostic("TEXTURE_CONTENT_OR_REFERENCE_CHANGED", texture.pointer()));
		}
		for (var binding : before.faceBindings().entrySet()) {
			if (!Objects.equals(binding.getValue(), after.faceBindings().get(binding.getKey())))
				diagnostics.add(new Diagnostic("FACE_TEXTURE_BINDING_CHANGED", "faces:" + binding.getKey()));
		}
		TreeSet<String> added = new TreeSet<>(after.textureReferences());
		added.removeAll(before.textureReferences());
		added.forEach(value -> diagnostics.add(new Diagnostic("TEXTURE_ADDED", "textures:" + value, AssetDiagnostic.Severity.INFO)));
		boolean compatible = diagnostics.stream().allMatch(issue -> issue.severity() == AssetDiagnostic.Severity.INFO);
		return new RoundTripReport(compatible, List.copyOf(diagnostics), compatible ? List.of()
				: List.of("inspect_changes", "accept_intentional_changes", "restore_previous_version"));
	}

	private static void missing(List<String> before, List<String> after, String code, String field,
			List<Diagnostic> diagnostics) {
		TreeSet<String> removed = new TreeSet<>(before);
		removed.removeAll(after);
		removed.forEach(value -> diagnostics.add(new Diagnostic(code, field + ":" + value)));
	}

	private static String string(JsonObject root, String objectName, String key) {
		JsonElement value = root.has(objectName) && root.get(objectName).isJsonObject()
				? root.getAsJsonObject(objectName).get(key) : null;
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static List<String> imageValues(BbmodelDocument.Texture texture) {
		return texture.images().stream().map(image -> image.kind() + ":" + image.value()).toList();
	}

	private static List<String> ids(JsonObject root, String key) {
		TreeSet<String> values = new TreeSet<>();
		JsonElement value = root.get(key);
		if (value != null && value.isJsonArray()) for (JsonElement element : value.getAsJsonArray()) {
			if (!element.isJsonObject()) continue;
			JsonObject object = element.getAsJsonObject();
			String id = primitive(object, "uuid");
			if (id == null) id = primitive(object, "id");
			if (id == null) id = primitive(object, "name");
			if (id != null && !id.isBlank()) values.add(id);
		}
		return List.copyOf(values);
	}

	private static String primitive(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static String sha256(Path path) throws IOException {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
		} catch (NoSuchAlgorithmException exception) {
			throw new AssertionError(exception);
		}
	}

	public record Snapshot(String assetId, String formatVersion, List<String> textureReferences,
			List<String> elementIds, List<String> animationIds, String sha256, List<BbmodelDocument.Texture> textures,
			Map<String, String> faceBindings, List<Diagnostic> diagnostics) {
		public Snapshot(String assetId, String formatVersion, List<String> textureReferences,
				List<String> elementIds, List<String> animationIds, String sha256) {
			this(assetId, formatVersion, textureReferences, elementIds, animationIds, sha256, List.of(), Map.of(), List.of());
		}
	}
	public record Diagnostic(String code, String location, AssetDiagnostic.Severity severity) {
		public Diagnostic(String code, String location) { this(code, location, AssetDiagnostic.Severity.WARNING); }
	}
	/** Advisory comparison, never an import veto: intentional deletions can be accepted after review. */
	public record RoundTripReport(boolean compatible, List<Diagnostic> diagnostics, List<String> handlingOptions) { }
}
