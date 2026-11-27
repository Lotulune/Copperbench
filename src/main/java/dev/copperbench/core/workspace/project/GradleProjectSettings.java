package dev.copperbench.core.workspace.project;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.WorkspaceFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

/**
 * An adopted project's own choices (ADR-0020 decisions 7 and 8), kept under {@code settings} in
 * {@code .copperbench/workspace.json}: whether {@code .copperbench/} was added to the project's {@code .gitignore},
 * and which Gradle user home its builds use.
 *
 * @param gitIgnore             {@code added}, {@code declined}, or {@code null} when the user has not been asked
 * @param environmentGradleHome whether builds use the environment's Gradle user home instead of the product's
 */
public record GradleProjectSettings(String gitIgnore, boolean environmentGradleHome) {

	public static final String KEY = "settings";
	public static final String ADDED = "added";
	public static final String DECLINED = "declined";
	static final String ENTRY = ".copperbench/";
	private static final Pattern IGNORED = Pattern.compile("^/?\\.copperbench(/|/\\*|/\\*\\*)?$");

	/** The stored choices; a missing or unreadable file means the defaults, so a build never fails on them. */
	public static GradleProjectSettings read(Path root) {
		try {
			Path file = root.resolve(GradleProjectWorkspaceSession.METADATA_FILE);
			if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 1024 * 1024)
				return new GradleProjectSettings(null, false);
			JsonElement settings = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().get(KEY);
			if (settings == null || !settings.isJsonObject()) return new GradleProjectSettings(null, false);
			String gitIgnore = string(settings.getAsJsonObject(), "gitIgnore");
			return new GradleProjectSettings(ADDED.equals(gitIgnore) || DECLINED.equals(gitIgnore) ? gitIgnore : null,
					"environment".equals(string(settings.getAsJsonObject(), "gradleUserHome")));
		} catch (IOException | RuntimeException unreadable) {
			return new GradleProjectSettings(null, false);
		}
	}

	/** Whether the folder is inside a Git working tree, which is when the {@code .gitignore} question applies. */
	public static boolean inGitRepository(Path root) {
		for (Path current = root.toAbsolutePath().normalize(); current != null; current = current.getParent())
			if (Files.exists(current.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) return true;
		return false;
	}

	/** Whether the project's own {@code .gitignore} already leaves out {@code .copperbench/}. */
	public static boolean ignoredByGit(Path root) throws IOException {
		Path file = root.resolve(".gitignore");
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return false;
		if (Files.size(file) > 1024 * 1024) throw new IOException(".gitignore is larger than 1 MiB.");
		return Files.readString(file, StandardCharsets.UTF_8).lines().map(String::strip).anyMatch(line -> IGNORED.matcher(line).matches());
	}

	/** Appends {@code .copperbench/} to the project's {@code .gitignore} in its own line endings, creating the file if needed. */
	static void addToGitIgnore(Path root) throws IOException {
		if (ignoredByGit(root)) return;
		Path file = WorkspaceFiles.canonicalPath(root.resolve(".gitignore"));
		String existing = Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? Files.readString(file, StandardCharsets.UTF_8) : "";
		String newline = existing.contains("\r\n") ? "\r\n" : "\n";
		String text = (existing.isEmpty() || existing.endsWith("\n") ? "" : newline) + ENTRY + newline;
		Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	/** The environment's own Gradle user home: {@code GRADLE_USER_HOME}, else {@code ~/.gradle}. */
	public static Path environmentGradleUserHome() {
		String configured = System.getenv("GRADLE_USER_HOME");
		return (configured != null && !configured.isBlank() ? Path.of(configured)
				: Path.of(System.getProperty("user.home"), ".gradle")).toAbsolutePath().normalize();
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}
}
