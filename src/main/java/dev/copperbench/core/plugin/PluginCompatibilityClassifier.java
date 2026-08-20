package dev.copperbench.core.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Classifies an installed MCreator plugin without loading its Java code. */
public final class PluginCompatibilityClassifier {

	private static final List<String> LEGACY_UI_MARKERS = List.of("javax/swing", "javax.swing", "java/awt/Component",
			"java/awt/Container", "java/awt/Window", "java/awt/Menu", "net/mcreator/ui", "net.mcreator.ui");
	private static final List<String> BLOCKED_INTERNAL_MARKERS = List.of("sun/misc/Unsafe", "jdk/internal");
	private static final List<String> REFLECTION_MARKERS = List.of("getDeclaredField", "getDeclaredMethod",
			"setAccessible");

	public Assessment assess(Path plugin, long productVersion, long productMajorVersion) throws IOException {
		PluginContents contents = Files.isDirectory(plugin) ? directoryContents(plugin) : zipContents(plugin);
		JsonObject manifest;
		try {
			manifest = JsonParser.parseString(new String(contents.manifest(), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (RuntimeException exception) {
			return rejected("unknown", contents.hash(), false, false, "PLUGIN_MANIFEST_INVALID");
		}

		String id = manifest.has("id") ? manifest.get("id").getAsString() : "unknown";
		boolean javaPlugin = manifest.has("javaplugin") && !manifest.get("javaplugin").isJsonNull();
		if (!supportsVersion(manifest, productVersion, productMajorVersion))
			return rejected(id, contents.hash(), javaPlugin, false, "PLUGIN_VERSION_UNSUPPORTED");
		if (!javaPlugin)
			return new Assessment(id, Level.A, Route.RESOURCE_PIPELINE, false, true, contents.hash(), List.of());

		String code = contents.javaCodeText();
		if (containsAny(code, BLOCKED_INTERNAL_MARKERS) || containsAny(code, REFLECTION_MARKERS)
				&& (code.contains("net/mcreator") || code.contains("net.mcreator")))
			return rejected(id, contents.hash(), true, true, "PLUGIN_USES_BLOCKED_INTERNAL_API");
		if (containsAny(code, LEGACY_UI_MARKERS))
			return new Assessment(id, Level.C, Route.LEGACY_SWING_WINDOW, true, true, contents.hash(),
					List.of("LEGACY_UI_ONLY"));

		List<String> limitations = code.isEmpty() ? List.of("JAVA_CODE_NOT_AVAILABLE_FOR_STATIC_SCAN") : List.of();
		return new Assessment(id, Level.B, Route.JAVA_COMPATIBILITY_API, true, true, contents.hash(), limitations);
	}

	private boolean supportsVersion(JsonObject manifest, long productVersion, long productMajorVersion) {
		if (!manifest.has("supportedversions") || manifest.get("supportedversions").isJsonNull())
			return true;
		JsonArray supported = manifest.getAsJsonArray("supportedversions");
		for (var version : supported) {
			long declared = version.getAsLong();
			if (declared == productVersion || declared == productMajorVersion)
				return true;
		}
		return false;
	}

	private PluginContents directoryContents(Path root) throws IOException {
		Path manifest = root.resolve("plugin.json");
		if (!Files.isRegularFile(manifest))
			throw new IOException("Plugin directory does not contain plugin.json: " + root);
		List<Path> files;
		try (var stream = Files.walk(root)) {
			files = stream.filter(Files::isRegularFile).sorted(Comparator.comparing(path -> root.relativize(path).toString()))
					.toList();
		}
		MessageDigest digest = sha256();
		StringBuilder code = new StringBuilder();
		for (Path file : files) {
			byte[] bytes = Files.readAllBytes(file);
			digest.update(root.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			digest.update(bytes);
			if (isJavaCode(file.getFileName().toString()))
				code.append(new String(bytes, StandardCharsets.ISO_8859_1));
		}
		return new PluginContents(Files.readAllBytes(manifest), code.toString(), HexFormat.of().formatHex(digest.digest()));
	}

	private PluginContents zipContents(Path archive) throws IOException {
		MessageDigest digest = sha256();
		byte[] archiveBytes = Files.readAllBytes(archive);
		digest.update(archiveBytes);
		byte[] manifest = null;
		StringBuilder code = new StringBuilder();
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			var entries = zip.stream().filter(entry -> !entry.isDirectory()).sorted(Comparator.comparing(ZipEntry::getName))
					.toList();
			for (ZipEntry entry : entries) {
				byte[] bytes = zip.getInputStream(entry).readAllBytes();
				if (entry.getName().equals("plugin.json"))
					manifest = bytes;
				if (isJavaCode(entry.getName()))
					code.append(new String(bytes, StandardCharsets.ISO_8859_1));
			}
		}
		if (manifest == null)
			throw new IOException("Plugin archive does not contain a root plugin.json: " + archive);
		return new PluginContents(manifest, code.toString(), HexFormat.of().formatHex(digest.digest()));
	}

	private static boolean isJavaCode(String name) {
		return name.endsWith(".class") || name.endsWith(".java");
	}

	private static boolean containsAny(String value, List<String> markers) {
		return markers.stream().anyMatch(value::contains);
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private Assessment rejected(String id, String hash, boolean javaPlugin, boolean versionSupported, String reason) {
		return new Assessment(id, Level.X, Route.REJECTED, javaPlugin, versionSupported, hash, List.of(reason));
	}

	public enum Level { A, B, C, X }

	public enum Route { RESOURCE_PIPELINE, JAVA_COMPATIBILITY_API, LEGACY_SWING_WINDOW, REJECTED }

	public record Assessment(String pluginId, Level level, Route route, boolean containsJavaCode,
			boolean versionSupported, String sha256, List<String> limitations) {
		public Assessment {
			limitations = List.copyOf(new ArrayList<>(limitations));
		}
	}

	private record PluginContents(byte[] manifest, String javaCodeText, String hash) {
	}
}
