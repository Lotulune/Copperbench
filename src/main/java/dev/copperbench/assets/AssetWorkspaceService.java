package dev.copperbench.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Read-only asset catalog and reference graph service for a workspace. */
public final class AssetWorkspaceService {
	private static final Set<String> RESOURCE_PREFIXES = Set.of("textures/", "models/", "animations/", "sounds/",
			"lang/", "blockstates/", "items/", "font/", "shaders/");
	private static final Set<String> KNOWN_EXTENSIONS = Set.of(".json", ".png", ".jpg", ".jpeg", ".ogg", ".wav",
			".bbmodel", ".mcmeta", ".zip");
	private static final Pattern URI = Pattern.compile("^[a-z][a-z0-9+.-]*://.*$", Pattern.CASE_INSENSITIVE);

	private final Path root;

	public AssetWorkspaceService(Path workspaceRoot) {
		Objects.requireNonNull(workspaceRoot, "workspaceRoot");
		try {
			root = workspaceRoot.toRealPath();
		} catch (IOException exception) {
			throw new AssetPathViolationException("Asset workspace does not exist: " + workspaceRoot);
		}
		if (!Files.isDirectory(root))
			throw new AssetPathViolationException("Asset workspace is not a directory: " + workspaceRoot);
	}

	public Path workspaceRoot() {
		return root;
	}

	/** Lists supported files from known asset roots in deterministic workspace-relative path order. */
	public List<AssetDescriptor> list() {
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile).filter(this::isAssetPath).map(this::descriptor)
					.sorted(Comparator.comparing(AssetDescriptor::relativePath))
					.toList();
		} catch (IOException exception) {
			throw new IllegalStateException("Asset workspace could not be scanned", exception);
		}
	}

	private boolean isAssetPath(Path path) {
		String relative = root.relativize(path).toString().replace('\\', '/');
		return relative.startsWith("assets/") || relative.startsWith("models/")
				|| relative.startsWith("resourcepacks/") || relative.startsWith("src/main/resources/assets/")
				|| relative.startsWith("src/main/assets/") || relative.equals("pack.mcmeta")
				|| relative.equals("pack.png") || relative.equals("src/main/pack.mcmeta")
				|| relative.equals("src/main/pack.png");
	}

	/** Alias used by query adapters that expose the catalog as an asset collection. */
	public List<AssetDescriptor> assets() {
		return list();
	}

	public List<AssetDescriptor> search(String query, AssetCategory category) {
		String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		return list().stream().filter(asset -> category == null || asset.category() == category)
				.filter(asset -> normalizedQuery.isEmpty() || asset.relativePath().toLowerCase(Locale.ROOT).contains(normalizedQuery)
						|| asset.id().contains(normalizedQuery))
				.toList();
	}

	public List<AssetDescriptor> search(String query) {
		return search(query, null);
	}

	public Optional<AssetDescriptor> findById(String id) {
		if (id == null || id.isBlank())
			return Optional.empty();
		return list().stream().filter(asset -> asset.id().equals(id)).findFirst();
	}

	public Optional<AssetDescriptor> findByRelativePath(String relativePath) {
		String normalized = relativePath == null ? "" : relativePath.replace('\\', '/');
		return list().stream().filter(asset -> asset.relativePath().equals(normalized)).findFirst();
	}

	/** Resolves a task path only when it remains inside this workspace and points at a regular file. */
	public Path resolveAuthorizedPath(String relativePath) {
		if (relativePath == null || relativePath.isBlank())
			throw new AssetPathViolationException("Asset path must not be blank");
		Path requested;
		try {
			requested = Path.of(relativePath);
		} catch (RuntimeException exception) {
			throw new AssetPathViolationException("Asset path is invalid");
		}
		if (requested.isAbsolute())
			throw new AssetPathViolationException("Absolute asset paths are not authorized");
		Path normalized = root.resolve(requested).normalize();
		if (!normalized.startsWith(root))
			throw new AssetPathViolationException("Asset path escapes the workspace");
		try {
			Path real = normalized.toRealPath();
			if (!real.startsWith(root) || !Files.isRegularFile(real))
				throw new AssetPathViolationException("Asset path is not an authorized file");
			return real;
		} catch (IOException exception) {
			throw new AssetPathViolationException("Asset path does not exist");
		}
	}

	public Path resolveAsset(String relativePath) {
		return resolveAuthorizedPath(relativePath);
	}

	public AssetReferenceGraph referenceGraph() {
		List<AssetDescriptor> assets = list();
		Map<String, AssetDescriptor> byPath = new HashMap<>();
		for (AssetDescriptor asset : assets)
			byPath.put(asset.relativePath(), asset);
		List<AssetReference> references = new ArrayList<>();
		List<AssetDiagnostic> diagnostics = new ArrayList<>();
		for (AssetDescriptor source : assets) {
			if (!isStructured(source.relativePath()))
				continue;
			Path file = root.resolve(source.relativePath());
			try {
				JsonElement document = JsonParser.parseString(Files.readString(file));
				collectStrings(document, null,
						(candidate, prefix) -> addReference(source, candidate, prefix, byPath, references, diagnostics));
			} catch (Exception exception) {
				diagnostics.add(new AssetDiagnostic("INVALID_ASSET_DOCUMENT", AssetDiagnostic.Severity.ERROR,
						source.relativePath(), null, "Asset document is not valid JSON"));
			}
		}
		references.sort(Comparator.comparing(AssetReference::sourcePath).thenComparing(AssetReference::targetPath));
		diagnostics.sort(Comparator.comparing(AssetDiagnostic::sourcePath).thenComparing(diagnostic ->
				diagnostic.targetPath() == null ? "" : diagnostic.targetPath()).thenComparing(AssetDiagnostic::code));
		return new AssetReferenceGraph(assets, references, diagnostics);
	}

	public AssetReferenceGraph buildReferenceGraph() {
		return referenceGraph();
	}

	private AssetDescriptor descriptor(Path path) {
		try {
			return AssetDescriptor.fromFile(root, path);
		} catch (IOException exception) {
			throw new IllegalStateException("Asset metadata could not be read: " + path, exception);
		}
	}

	private static boolean isStructured(String path) {
		String lower = path.toLowerCase(Locale.ROOT);
		return lower.endsWith(".json") || lower.endsWith(".bbmodel") || lower.endsWith(".mcmeta");
	}

	private static void collectStrings(JsonElement value, String inheritedPrefix,
			java.util.function.BiConsumer<String, String> consumer) {
		if (value == null || value.isJsonNull())
			return;
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
			consumer.accept(value.getAsString(), inheritedPrefix);
			return;
		}
		if (value.isJsonArray()) {
			JsonArray array = value.getAsJsonArray();
			array.forEach(element -> collectStrings(element, inheritedPrefix, consumer));
			return;
		}
		if (value.isJsonObject()) {
			JsonObject object = value.getAsJsonObject();
			object.entrySet().forEach(entry -> collectStrings(entry.getValue(), prefixForKey(entry.getKey(), inheritedPrefix), consumer));
		}
	}

	private static String prefixForKey(String key, String inheritedPrefix) {
		return switch (key.toLowerCase(Locale.ROOT)) {
			case "parent", "model" -> "models/";
			case "texture", "textures", "layer0", "layer1", "particle" -> "textures/";
			case "animation", "animations" -> "animations/";
			case "sound", "sounds" -> "sounds/";
			default -> inheritedPrefix;
		};
	}

	private static void addReference(AssetDescriptor source, String rawValue, String expectedPrefix,
			Map<String, AssetDescriptor> byPath,
			List<AssetReference> references, List<AssetDiagnostic> diagnostics) {
		String value = rawValue == null ? "" : rawValue.trim();
		if (!isReferenceCandidate(value, expectedPrefix))
			return;
		String targetPath;
		try {
			targetPath = normalizeReference(value, source.relativePath(), expectedPrefix);
		} catch (AssetPathViolationException exception) {
			diagnostics.add(new AssetDiagnostic("REFERENCE_PATH_ESCAPE", AssetDiagnostic.Severity.ERROR,
					source.relativePath(), value, "Asset reference escapes the workspace"));
			return;
		}
		AssetDescriptor target = byPath.get(targetPath);
		if (target == null) {
			diagnostics.add(new AssetDiagnostic("MISSING_ASSET_REFERENCE", AssetDiagnostic.Severity.ERROR,
					source.relativePath(), targetPath, "Referenced asset does not exist"));
			return;
		}
		AssetReference.ReferenceKind kind = value.indexOf(':') >= 0 ? AssetReference.ReferenceKind.RESOURCE_ID
				: AssetReference.ReferenceKind.JSON_STRING;
		references.add(new AssetReference(source.id(), source.relativePath(), targetPath, target.id(), kind));
	}

	private static boolean isReferenceCandidate(String value, String expectedPrefix) {
		if (value.isBlank() || value.length() > 512 || value.contains(" ") || URI.matcher(value).matches())
			return false;
		String lower = value.toLowerCase(Locale.ROOT);
		if (lower.startsWith("#") || lower.equals("true") || lower.equals("false"))
			return false;
		if (lower.startsWith("../") || lower.startsWith("./") || lower.startsWith("/") || lower.contains(":"))
			return true;
		return expectedPrefix != null || RESOURCE_PREFIXES.stream().anyMatch(lower::startsWith)
				|| KNOWN_EXTENSIONS.stream().anyMatch(lower::endsWith);
	}

	private static String normalizeReference(String value, String sourcePath, String expectedPrefix) {
		String candidate = value.replace('\\', '/');
		if (candidate.startsWith("/") || candidate.matches("^[a-zA-Z]:/.*"))
			throw new AssetPathViolationException("Absolute asset reference");
		if (candidate.equals("..") || candidate.startsWith("../") || candidate.contains("/../")
				|| candidate.contains("/./"))
			throw new AssetPathViolationException("Asset reference escapes workspace");
		if (candidate.contains(":") && !candidate.startsWith("assets/")) {
			int separator = candidate.indexOf(':');
			String namespace = candidate.substring(0, separator);
			String path = candidate.substring(separator + 1);
			if (expectedPrefix != null && !hasKnownPrefix(path))
				path = expectedPrefix + path;
			candidate = "assets/" + namespace + "/" + path;
		} else if (!candidate.startsWith("assets/")) {
			String namespace = namespace(sourcePath);
			if (expectedPrefix != null && !hasKnownPrefix(candidate))
				candidate = expectedPrefix + candidate;
			candidate = "assets/" + namespace + "/" + candidate;
		}
		String resourceRoot = resourceRoot(sourcePath);
		if (!resourceRoot.isEmpty() && candidate.startsWith("assets/"))
			candidate = resourceRoot + candidate;
		String lower = candidate.toLowerCase(Locale.ROOT);
		if (!hasExtension(lower)) {
			if (lower.contains("/textures/")) candidate += ".png";
			else if (lower.contains("/sounds/")) candidate += ".ogg";
			else candidate += ".json";
		}
		Path normalized = Path.of(candidate).normalize();
		if (normalized.isAbsolute() || normalized.startsWith(".."))
			throw new AssetPathViolationException("Asset reference escapes workspace");
		return normalized.toString().replace('\\', '/');
	}

	private static String namespace(String sourcePath) {
		String[] parts = sourcePath.split("/");
		for (int index = 0; index + 1 < parts.length; index++) {
			if (parts[index].equals("assets")) return parts[index + 1];
		}
		return "minecraft";
	}

	private static String resourceRoot(String sourcePath) {
		String[] parts = sourcePath.split("/");
		for (int index = 0; index < parts.length; index++) {
			if (parts[index].equals("assets")) {
				if (index == 0) return "";
				return String.join("/", java.util.Arrays.copyOfRange(parts, 0, index)) + "/";
			}
		}
		return "";
	}

	private static boolean hasExtension(String value) {
		return KNOWN_EXTENSIONS.stream().anyMatch(value::endsWith);
	}

	private static boolean hasKnownPrefix(String value) {
		String lower = value.toLowerCase(Locale.ROOT);
		return RESOURCE_PREFIXES.stream().anyMatch(lower::startsWith) || lower.startsWith("assets/");
	}
}
