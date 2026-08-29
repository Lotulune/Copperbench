/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.copperbench.ProductIdentity;
import dev.copperbench.automation.audit.SensitiveDataRedactor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a bounded, redacted support bundle without uploading any data. */
public final class DiagnosticBundleService {

	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withLocale(Locale.ROOT).withZone(ZoneOffset.UTC);
	private static final long MAX_LOG_BYTES = 5L * 1024 * 1024;
	private static final long MAX_REPRODUCTION_BYTES = 25L * 1024 * 1024;
	private static final int MAX_REPRODUCTION_FILES = 128;
	private static final Pattern WINDOWS_PATH = Pattern.compile("(?i)(?:[a-z]:\\\\|\\\\\\\\)[^\\r\\n\\t\\\"']+");
	private static final Pattern UNIX_HOME = Pattern.compile("/(?:Users|home)/[^/\\s]+(?:/[^\\s\\\"']*)?");
	private static final Set<String> REPRODUCTION_EXTENSIONS = Set.of(
			".mcreator", ".mod.json", ".json", ".xml", ".gradle", ".properties", ".toml", ".java");
	private static final Set<String> EXCLUDED_ROOTS = Set.of(".git", ".gradle", "build", "run", "logs");

	private final Path outputRoot;
	private final Path logRoot;
	private final Path workspaceRoot;
	private final Supplier<JsonObject> taskSnapshot;
	private final Clock clock;

	public DiagnosticBundleService(Path outputRoot, Path logRoot, Path workspaceRoot,
			Supplier<JsonObject> taskSnapshot, Clock clock) {
		this.outputRoot = outputRoot.toAbsolutePath().normalize();
		this.logRoot = logRoot.toAbsolutePath().normalize();
		this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
		this.taskSnapshot = taskSnapshot;
		this.clock = clock;
	}

	public Result export(UUID failureId, boolean includeWorkspaceFiles) throws IOException {
		Files.createDirectories(outputRoot);
		Instant generatedAt = clock.instant();
		Path target = outputRoot.resolve("copperbench-diagnostics-" + FILE_STAMP.format(generatedAt) + "-"
				+ UUID.randomUUID().toString().substring(0, 8) + ".zip");
		List<BundleEntry> entries = new ArrayList<>();
		entries.add(new BundleEntry("environment.json", JSON.toJson(environment(generatedAt)).getBytes(StandardCharsets.UTF_8)));
		entries.add(new BundleEntry("tasks.json", JSON.toJson(sanitizedTaskSnapshot()).getBytes(StandardCharsets.UTF_8)));
		addLogs(entries);
		int reproductionFiles = includeWorkspaceFiles ? addReproduction(entries) : 0;
		entries.sort(Comparator.comparing(BundleEntry::name));
		entries.add(new BundleEntry("manifest.json", JSON.toJson(manifest(generatedAt, failureId,
				includeWorkspaceFiles, reproductionFiles, entries)).getBytes(StandardCharsets.UTF_8)));

		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
			for (BundleEntry entry : entries) {
				ZipEntry zipEntry = new ZipEntry(entry.name());
				zipEntry.setTime(0);
				zip.putNextEntry(zipEntry);
				zip.write(entry.content());
				zip.closeEntry();
			}
		}
		return new Result(target, includeWorkspaceFiles, reproductionFiles);
	}

	private JsonObject environment(Instant generatedAt) {
		JsonObject result = new JsonObject();
		result.addProperty("schemaVersion", "1.0");
		result.addProperty("generatedAt", generatedAt.toString());
		result.addProperty("product", ProductIdentity.NAME);
		result.addProperty("productVersion", ProductIdentity.VERSION);
		result.addProperty("osName", System.getProperty("os.name", "unknown"));
		result.addProperty("osVersion", System.getProperty("os.version", "unknown"));
		result.addProperty("osArchitecture", System.getProperty("os.arch", "unknown"));
		result.addProperty("javaVersion", System.getProperty("java.version", "unknown"));
		result.addProperty("processors", Runtime.getRuntime().availableProcessors());
		result.addProperty("maxMemoryMiB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
		return result;
	}

	private JsonObject sanitizedTaskSnapshot() {
		JsonObject snapshot = taskSnapshot == null ? new JsonObject() : taskSnapshot.get();
		if (snapshot == null) snapshot = new JsonObject();
		return JSON.fromJson(redact(JSON.toJson(snapshot)), JsonObject.class);
	}

	private void addLogs(List<BundleEntry> entries) throws IOException {
		if (!Files.isDirectory(logRoot)) return;
		try (var paths = Files.list(logRoot)) {
			for (Path path : paths.filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
					.sorted().toList()) {
				if (Files.size(path) > MAX_LOG_BYTES) continue;
				String name = path.getFileName().toString();
				if (!name.endsWith(".log") && !name.endsWith(".txt")) continue;
				entries.add(new BundleEntry("logs/" + safeName(name),
						redact(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));
			}
		}
	}

	private int addReproduction(List<BundleEntry> entries) throws IOException {
		if (!Files.isDirectory(workspaceRoot)) return 0;
		long total = 0;
		int count = 0;
		try (var paths = Files.walk(workspaceRoot, 8)) {
			for (Path path : paths.filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
					.sorted().toList()) {
				Path relative = workspaceRoot.relativize(path).normalize();
				if (relative.getNameCount() == 0 || EXCLUDED_ROOTS.contains(relative.getName(0).toString())) continue;
				String normalized = relative.toString().replace('\\', '/');
				if (REPRODUCTION_EXTENSIONS.stream().noneMatch(normalized::endsWith)) continue;
				long size = Files.size(path);
				if (size > MAX_LOG_BYTES || total + size > MAX_REPRODUCTION_BYTES || count >= MAX_REPRODUCTION_FILES) continue;
				entries.add(new BundleEntry("reproduction/" + normalized, Files.readAllBytes(path)));
				total += size;
				count++;
			}
		}
		return count;
	}

	private JsonObject manifest(Instant generatedAt, UUID failureId, boolean includeWorkspaceFiles,
			int reproductionFiles, List<BundleEntry> entries) {
		JsonObject manifest = new JsonObject();
		manifest.addProperty("schemaVersion", "1.0");
		manifest.addProperty("kind", "copperbench-diagnostic-bundle");
		manifest.addProperty("generatedAt", generatedAt.toString());
		if (failureId != null) manifest.addProperty("failureId", failureId.toString());
		manifest.addProperty("userConfirmedWorkspaceFiles", includeWorkspaceFiles);
		manifest.addProperty("reproductionFileCount", reproductionFiles);
		manifest.addProperty("reproductionFilesIncludedWithoutContentRedaction", includeWorkspaceFiles);
		JsonArray redactions = new JsonArray();
		redactions.add("credentials and bearer tokens");
		redactions.add("user name and home directory");
		redactions.add("workspace and external absolute paths in generated summaries and logs");
		manifest.add("redactions", redactions);
		JsonArray files = new JsonArray();
		entries.forEach(entry -> files.add(entry.name()));
		manifest.add("files", files);
		return manifest;
	}

	private String redact(String source) {
		String result = SensitiveDataRedactor.redact(source);
		String userName = System.getProperty("user.name", "");
		String userHome = System.getProperty("user.home", "");
		if (!userName.isBlank()) result = result.replace(userName, "[USER]");
		if (!userHome.isBlank()) result = result.replace(userHome, "%USERPROFILE%");
		result = result.replace(workspaceRoot.toString(), "%WORKSPACE%");
		result = WINDOWS_PATH.matcher(result).replaceAll("[PATH]");
		return UNIX_HOME.matcher(result).replaceAll("[PATH]");
	}

	private static String safeName(String name) {
		return name.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private record BundleEntry(String name, byte[] content) {
	}

	public record Result(Path path, boolean includedWorkspaceFiles, int reproductionFileCount) {
	}
}
