/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core.workspace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * An existing data pack ZIP imported into a new data pack workspace. {@link #inspect} only reads; {@link #extract}
 * copies the pack's {@code data/} tree and {@code pack.png} into the workspace's {@code src/main}. The generator owns
 * {@code pack.mcmeta}, so only its description carries over and the fields it cannot write are reported.
 */
public final class DataPackArchive {
	public static final int MAX_ENTRIES = 20_000;
	public static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024, MAX_TOTAL_BYTES = 256L * 1024 * 1024;
	private static final int MAX_METADATA_BYTES = 1024 * 1024, MAX_LISTED = 50;
	/** Pack formats the bundled generators write, newest first; 26.1 packs declare the major version 101. */
	private static final Map<String, Integer> GENERATOR_FORMATS = Map.of("datapack-26.1.x", 101, "datapack-1.21.1", 48,
			"datapack-1.20.1", 15);
	private static final List<String> GENERATORS_NEWEST_FIRST = List.of("datapack-26.1.x", "datapack-1.21.1", "datapack-1.20.1");
	private static final Pattern NAMESPACE_AS_MOD_ID = Pattern.compile("[a-z][a-z0-9_]{1,31}");
	/** Namespaces that packs commonly extend rather than own. */
	private static final Set<String> SHARED_NAMESPACES = Set.of("minecraft", "c", "fabric", "neoforge", "forge");
	private static final Set<String> KEPT_PACK_FIELDS = Set.of("description", "pack_format", "min_format", "max_format");

	private DataPackArchive() { }

	/**
	 * What an archive holds and what importing it keeps.
	 *
	 * @param root               the folder inside the ZIP that holds {@code pack.mcmeta}: empty, or one wrapping folder
	 * @param description        the plain-text pack description, or {@code null} when it is absent or a text component
	 * @param suggestedGeneratorId the newest bundled generator whose pack format the pack declares, or {@code null}
	 * @param suggestedModId     the pack's own namespace when it is also a valid workspace ID, or {@code null}
	 * @param skipped            top-level entries other than {@code data/}, {@code pack.mcmeta} and {@code pack.png}
	 * @param droppedMetadata    {@code pack.mcmeta} fields the generated {@code pack.mcmeta} does not keep
	 */
	public record Inspection(String root, JsonObject pack, String description, String suggestedGeneratorId,
			List<String> namespaces, String suggestedModId, int dataFiles, boolean packIcon, List<String> skipped,
			List<String> droppedMetadata) {
		public Inspection {
			pack = pack.deepCopy();
			namespaces = List.copyOf(namespaces);
			skipped = List.copyOf(skipped);
			droppedMetadata = List.copyOf(droppedMetadata);
		}

		/** Whether the pack declares the pack format that {@code generatorId} writes. */
		public boolean supports(String generatorId) {
			Integer format = GENERATOR_FORMATS.get(generatorId);
			return format != null && DataPackArchive.supports(pack, format);
		}

		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("root", root);
			for (String key : List.of("pack_format", "min_format", "max_format", "supported_formats"))
				if (pack.has(key)) json.add(key.equals("pack_format") ? "packFormat" : camel(key), pack.get(key).deepCopy());
			json.addProperty("description", description);
			json.addProperty("suggestedGeneratorId", suggestedGeneratorId);
			json.add("supportedGeneratorIds", strings(GENERATORS_NEWEST_FIRST.stream().filter(this::supports).toList()));
			json.add("namespaces", strings(namespaces));
			json.addProperty("suggestedModId", suggestedModId);
			json.addProperty("dataFiles", dataFiles);
			json.addProperty("packIcon", packIcon);
			json.add("skipped", strings(skipped));
			json.add("droppedMetadata", strings(droppedMetadata));
			return json;
		}
	}

	/** A stable code with a message that names the problem; never Java exception text. */
	public static final class ArchiveException extends IOException {
		private static final long serialVersionUID = 1L;
		private final String code;

		ArchiveException(String code, String message) { super(message); this.code = code; }
		ArchiveException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }

		public String code() { return code; }
	}

	public static Inspection inspect(Path archive) throws ArchiveException {
		Path file = archiveFile(archive);
		try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
			List<String> names = new ArrayList<>();
			Set<String> seen = new HashSet<>();
			long declared = 0;
			int count = 0;
			for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
				ZipEntry entry = entries.nextElement();
				if (++count > MAX_ENTRIES)
					throw new ArchiveException("IMPORT_ARCHIVE_TOO_LARGE", "The ZIP has more than " + MAX_ENTRIES + " entries.");
				String name = entry.getName();
				checkName(name);
				if (entry.isDirectory()) continue;
				// Windows folds case, so two names that differ only in case would write one file.
				if (!seen.add(name.toLowerCase(Locale.ROOT)))
					throw new ArchiveException("IMPORT_ARCHIVE_PATH_INVALID", "The ZIP contains " + name + " twice.");
				if (entry.getSize() > MAX_ENTRY_BYTES || (declared += Math.max(0, entry.getSize())) > MAX_TOTAL_BYTES)
					throw tooLarge();
				names.add(name);
			}
			String root = root(names);
			JsonObject metadata = metadata(zip, root + "pack.mcmeta");
			JsonObject pack = metadata.getAsJsonObject("pack");
			List<String> dropped = new ArrayList<>();
			metadata.keySet().stream().filter(key -> !key.equals("pack")).sorted().forEach(dropped::add);
			pack.keySet().stream().filter(key -> !KEPT_PACK_FIELDS.contains(key)).sorted().map(key -> "pack." + key).forEach(dropped::add);
			String description = pack.get("description") instanceof JsonPrimitive text && text.isString() ? text.getAsString() : null;
			if (description == null && pack.has("description")) dropped.add("pack.description");

			Map<String, Integer> namespaceFiles = new TreeMap<>();
			Set<String> skipped = new LinkedHashSet<>();
			int dataFiles = 0;
			boolean icon = false;
			for (String name : names) {
				String relative = name.startsWith(root) ? name.substring(root.length()) : null;
				String[] parts = (relative == null ? name : relative).split("/");
				if ("pack.mcmeta".equals(relative)) continue;
				if ("pack.png".equals(relative)) { icon = true; continue; }
				if (relative != null && parts[0].equals("data")) {
					// Files directly under data/ and hidden files such as .DS_Store are never loaded.
					if (parts.length > 2 && !hidden(parts)) {
						dataFiles++;
						namespaceFiles.merge(parts[1], 1, Integer::sum);
					}
				} else if (skipped.size() < MAX_LISTED) skipped.add(parts.length > 1 ? parts[0] + "/" : parts[0]);
			}
			if (dataFiles == 0)
				throw new ArchiveException("IMPORT_ARCHIVE_NOT_DATA_PACK", "The ZIP has no files under data/"
						+ (skipped.contains("assets/") ? "; it looks like a resource pack." : "."));
			String suggestedGenerator = GENERATORS_NEWEST_FIRST.stream()
					.filter(generator -> supports(pack, GENERATOR_FORMATS.get(generator))).findFirst().orElse(null);
			String suggestedModId = namespaceFiles.entrySet().stream()
					.filter(entry -> !SHARED_NAMESPACES.contains(entry.getKey()) && NAMESPACE_AS_MOD_ID.matcher(entry.getKey()).matches())
					// Most files wins; TreeMap order makes the alphabetically first namespace win a tie.
					.reduce((best, next) -> next.getValue() > best.getValue() ? next : best)
					.map(Map.Entry::getKey).orElse(null);
			return new Inspection(root, pack, description, suggestedGenerator, List.copyOf(namespaceFiles.keySet()),
					suggestedModId, dataFiles, icon, List.copyOf(skipped), dropped);
		} catch (ArchiveException exception) {
			throw exception;
		} catch (ZipException exception) {
			throw new ArchiveException("IMPORT_ARCHIVE_UNREADABLE", "The file is not a readable ZIP archive.", exception);
		} catch (IOException | RuntimeException exception) {
			throw new ArchiveException("IMPORT_ARCHIVE_UNREADABLE", "The ZIP archive could not be read.", exception);
		}
	}

	/**
	 * Copies {@code data/} and {@code pack.png} from the inspected root into {@code sourceRoot}, never replacing a file.
	 * Sizes are counted from the bytes actually read, not from the ZIP headers.
	 *
	 * @return the number of files written
	 */
	public static int extract(Path archive, Inspection inspection, Path sourceRoot) throws ArchiveException {
		Path target = sourceRoot.toAbsolutePath().normalize(), data = target.resolve("data");
		String root = inspection.root();
		long total = 0;
		int written = 0;
		byte[] buffer = new byte[64 * 1024];
		try (ZipFile zip = new ZipFile(archiveFile(archive).toFile(), StandardCharsets.UTF_8)) {
			for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				checkName(name);
				if (entry.isDirectory() || !name.startsWith(root)) continue;
				String relative = name.substring(root.length());
				String[] parts = relative.split("/");
				boolean icon = relative.equals("pack.png");
				if (!icon && !(parts[0].equals("data") && parts.length > 2 && !hidden(parts))) continue;
				Path destination = target.resolve(relative).normalize();
				if (icon ? !destination.equals(target.resolve("pack.png")) : !destination.startsWith(data))
					throw new ArchiveException("IMPORT_ARCHIVE_PATH_INVALID", "The ZIP entry " + name + " leaves the pack folder.");
				Files.createDirectories(destination.getParent());
				long size = 0;
				try (InputStream input = zip.getInputStream(entry);
					 OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
					for (int read; (read = input.read(buffer)) > 0;) {
						size += read;
						total += read;
						if (size > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) throw tooLarge();
						output.write(buffer, 0, read);
					}
				} catch (FileAlreadyExistsException exception) {
					throw new ArchiveException("IMPORT_ARCHIVE_PATH_INVALID", "The ZIP entry " + name
							+ " would replace a file that already exists in the workspace.", exception);
				}
				written++;
			}
		} catch (ArchiveException exception) {
			throw exception;
		} catch (IOException | RuntimeException exception) {
			throw new ArchiveException("IMPORT_ARCHIVE_UNREADABLE", "The ZIP archive could not be copied into the workspace.", exception);
		}
		return written;
	}

	private static Path archiveFile(Path archive) throws ArchiveException {
		Path file = archive.toAbsolutePath().normalize();
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
			throw new ArchiveException("IMPORT_ARCHIVE_UNREADABLE", "Select an existing .zip file.");
		return file;
	}

	private static void checkName(String name) throws ArchiveException {
		String path = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
		boolean invalid = path.isEmpty() || name.length() > 1024 || name.startsWith("/") || name.contains("\\")
				|| name.contains(":") || name.chars().anyMatch(character -> character < 0x20);
		if (!invalid) for (String part : path.split("/", -1))
			if (part.isEmpty() || part.equals(".") || part.equals("..")) { invalid = true; break; }
		if (invalid) throw new ArchiveException("IMPORT_ARCHIVE_PATH_INVALID", "The ZIP contains an unsafe entry name: "
				+ name.replaceAll("\\p{Cntrl}", "?"));
	}

	/** {@code pack.mcmeta} at the top, or inside the single folder that wraps everything else, as zipping a folder does. */
	private static String root(List<String> names) throws ArchiveException {
		if (names.contains("pack.mcmeta")) return "";
		Set<String> tops = new HashSet<>();
		for (String name : names) if (!name.startsWith("__MACOSX/")) tops.add(name.contains("/") ? name.substring(0, name.indexOf('/') + 1) : name);
		if (tops.size() == 1 && names.contains(tops.iterator().next() + "pack.mcmeta")) return tops.iterator().next();
		throw new ArchiveException("IMPORT_ARCHIVE_NOT_DATA_PACK", "The ZIP has no pack.mcmeta at its top level.");
	}

	private static JsonObject metadata(ZipFile zip, String name) throws IOException {
		ZipEntry entry = zip.getEntry(name);
		byte[] bytes;
		try (InputStream input = zip.getInputStream(entry)) { bytes = input.readNBytes(MAX_METADATA_BYTES + 1); }
		if (bytes.length > MAX_METADATA_BYTES) throw tooLarge();
		try {
			JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
			if (parsed instanceof JsonObject metadata && metadata.get("pack") instanceof JsonObject) return metadata;
		} catch (RuntimeException invalid) { /* reported below */ }
		throw new ArchiveException("IMPORT_ARCHIVE_NOT_DATA_PACK", "pack.mcmeta is not a JSON object with a \"pack\" object.");
	}

	/** Mirrors pack_format, supported_formats and the 1.21.9+ min_format/max_format declarations. */
	static boolean supports(JsonObject pack, int format) {
		if (Integer.valueOf(format).equals(major(pack.get("pack_format")))) return true;
		JsonElement supported = pack.get("supported_formats");
		Integer low = null, high = null;
		if (supported instanceof JsonPrimitive) low = high = major(supported);
		else if (supported instanceof JsonArray range && range.size() == 2) { low = major(range.get(0)); high = major(range.get(1)); }
		else if (supported instanceof JsonObject range) { low = major(range.get("min_inclusive")); high = major(range.get("max_inclusive")); }
		if (low != null && high != null && low <= format && format <= high) return true;
		Integer minimum = major(pack.get("min_format")), maximum = major(pack.get("max_format"));
		return minimum != null && minimum <= format && format <= (maximum == null ? minimum : maximum);
	}

	/** The major format of a number such as 48 or 101.1, or of a [major, minor] pair; {@code null} for anything else. */
	private static Integer major(JsonElement format) {
		if (format instanceof JsonArray parts && !parts.isEmpty()) format = parts.get(0);
		return format instanceof JsonPrimitive value && value.isNumber() ? (int) Math.floor(value.getAsDouble()) : null;
	}

	/** Hidden files such as .DS_Store are never data and the workspace source rules refuse them. */
	private static boolean hidden(String[] parts) {
		for (String part : parts) if (part.startsWith(".")) return true;
		return false;
	}

	private static ArchiveException tooLarge() {
		return new ArchiveException("IMPORT_ARCHIVE_TOO_LARGE", "The ZIP is larger than the import limit ("
				+ MAX_ENTRY_BYTES / (1024 * 1024) + " MiB per file, " + MAX_TOTAL_BYTES / (1024 * 1024) + " MiB in total).");
	}

	private static JsonArray strings(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array;
	}

	private static String camel(String key) {
		StringBuilder result = new StringBuilder();
		boolean upper = false;
		for (char character : key.toCharArray()) {
			if (character == '_') { upper = true; continue; }
			result.append(upper ? Character.toUpperCase(character) : character);
			upper = false;
		}
		return result.toString();
	}
}
