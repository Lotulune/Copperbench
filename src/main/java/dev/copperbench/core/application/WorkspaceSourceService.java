package dev.copperbench.core.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Bounded source projections and hash-checked text edits. Does not invent Mod Elements from Java. */
final class WorkspaceSourceService {
	static final int MAX_FILE_BYTES = 1024 * 1024;
	private static final int MAX_FILES = 2000, MAX_VISITED = 10000, MAX_INDEX_BYTES = 16 * 1024 * 1024, MAX_EVIDENCE = 300;
	private static final Set<String> EXTENSIONS = Set.of("java", "json", "mcmeta", "xml", "properties", "toml", "yaml", "yml", "txt", "md", "gradle", "kts", "vert", "frag", "glsl", "vsh", "fsh", "lang");
	private static final Set<String> ROOT_FILES = Set.of("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "mcreator.gradle", "gradle.properties", "README.md", "pack.mcmeta");
	private static final Set<String> EXCLUDED = Set.of("build", "target", "out", "run", "node_modules", "output", "logs", "cache", "caches");
	private final Path root;
	private final Map<String, String> ownership;
	private final boolean writable;

	WorkspaceSourceService(Path root, Map<String, String> ownership, boolean writable) {
		if (root == null) throw failure("WORKSPACE_SOURCE_UNAVAILABLE", "The workspace root is unavailable.");
		this.root = root.toAbsolutePath().normalize();
		this.ownership = Map.copyOf(ownership);
		this.writable = writable;
	}

	JsonObject list(String search, int offset, int limit) throws IOException {
		if (offset < 0 || limit < 1 || limit > 200 || search.length() > 256)
			throw failure("WORKSPACE_SOURCE_REQUEST_INVALID", "Source listing requires a non-negative offset and a limit from 1 to 200.");
		Inventory inventory = inventory();
		String term = search.toLowerCase(Locale.ROOT);
		List<String> matches = inventory.paths().stream().filter(path -> path.toLowerCase(Locale.ROOT).contains(term)).toList();
		int start = Math.min(Math.max(0, offset), matches.size()), end = Math.min(matches.size(), start + Math.min(200, Math.max(1, limit)));
		JsonArray files = new JsonArray();
		for (String path : matches.subList(start, end)) files.add(describe(path, Files.size(resolve(path))));
		JsonObject result = new JsonObject(); result.add("files", files); result.addProperty("total", matches.size());
		if (end < matches.size()) result.addProperty("nextOffset", end); else result.add("nextOffset", JsonNull.INSTANCE);
		result.addProperty("truncated", inventory.truncated()); result.addProperty("maxFileBytes", MAX_FILE_BYTES);
		return result;
	}

	JsonObject read(String relative) throws IOException {
		byte[] bytes = readBytes(relative);
		JsonObject result = describe(relative, bytes.length);
		result.addProperty("content", decode(bytes)); result.addProperty("sha256", sha256(bytes));
		return result;
	}

	Edit prepare(String relative, String content, String expectedSha256) throws IOException {
		if (expectedSha256 == null || !expectedSha256.matches("[a-f0-9]{64}"))
			throw failure("WORKSPACE_SOURCE_REQUEST_INVALID", "A SHA-256 from the current source read is required.");
		if (content.length() > MAX_FILE_BYTES) throw failure("WORKSPACE_SOURCE_TOO_LARGE", "Source text exceeds the 1 MiB limit.");
		byte[] before = readBytes(relative);
		JsonObject description = describe(relative, before.length);
		if (!description.get("editable").getAsBoolean()) throw failure("WORKSPACE_SOURCE_READ_ONLY", "This source file is read-only; use its owning editor or generator.");
		if (!sha256(before).equals(expectedSha256)) throw failure("WORKSPACE_SOURCE_CONFLICT", "The file changed on disk. Reload it before saving.");
		ByteBuffer encoded;
		try { encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(content)); }
		catch (java.nio.charset.CharacterCodingException exception) { throw failure("WORKSPACE_SOURCE_ENCODING", "Source content must be valid UTF-8 text."); }
		if (encoded.remaining() > MAX_FILE_BYTES) throw failure("WORKSPACE_SOURCE_TOO_LARGE", "Source text exceeds the 1 MiB limit.");
		byte[] after = new byte[encoded.remaining()]; encoded.get(after);
		return new Edit(relative, before, after);
	}

	void verify(Edit edit) throws IOException {
		if (!sha256(readBytes(edit.relativePath())).equals(sha256(edit.before())))
			throw failure("WORKSPACE_SOURCE_CONFLICT", "The file changed on disk. Reload it before saving.");
	}

	/** The callback persists the revision/reloads source-backed projections; failure restores this file only. */
	void apply(Edit edit, Commit commit) throws Exception {
		verify(edit);
		atomicWrite(edit.relativePath(), edit.after(), sha256(edit.before()));
		try { commit.run(); }
		catch (Exception exception) {
			try { atomicWrite(edit.relativePath(), edit.before(), sha256(edit.after())); }
			catch (Exception rollback) { exception.addSuppressed(rollback); }
			throw exception;
		}
	}

	private void atomicWrite(String relative, byte[] content, String expected) throws IOException {
		Path target = resolve(relative);
		Path temporary = Files.createTempFile(target.getParent(), ".copperbench-source-", ".tmp");
		try {
			Files.write(temporary, content);
			resolve(relative);
			if (!sha256(readBytes(relative)).equals(expected)) throw failure("WORKSPACE_SOURCE_CONFLICT", "The file changed during save. Reload it before saving.");
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally { Files.deleteIfExists(temporary); }
	}

	private JsonObject describe(String relative, long size) {
		String mode = ownership.getOrDefault(relative, "manual");
		if (ownership.entrySet().stream().anyMatch(entry -> entry.getKey().endsWith("/")
				&& relative.startsWith(entry.getKey()) && entry.getValue().equals("generated"))) mode = "generated";
		JsonObject result = new JsonObject(); result.addProperty("relativePath", relative);
		result.addProperty("name", relative.substring(relative.lastIndexOf('/') + 1)); result.addProperty("language", extension(relative));
		result.addProperty("size", size); result.addProperty("ownership", mode);
		String reason = mode.equals("generated") ? "GENERATED_SOURCE" : !writable ? "WORKSPACE_READ_ONLY" : size > MAX_FILE_BYTES ? "WORKSPACE_SOURCE_TOO_LARGE" : null;
		result.addProperty("editable", reason == null); result.addProperty("reasonCode", reason);
		return result;
	}

	private Path resolve(String relative) throws IOException {
		if (!allowed(relative)) throw failure("WORKSPACE_SOURCE_PATH_INVALID", "Select a supported workspace-relative source path.");
		if (Files.isSymbolicLink(root)) throw failure("WORKSPACE_SOURCE_PATH_INVALID", "Symbolic workspace roots are not supported for source access.");
		Path realRoot = root.toRealPath(), current = realRoot;
		for (String part : relative.split("/")) {
			current = current.resolve(part);
			if (Files.isSymbolicLink(current) || !current.toRealPath().startsWith(realRoot)
					|| !current.toRealPath().equals(current.toAbsolutePath().normalize()))
				throw failure("WORKSPACE_SOURCE_PATH_INVALID", "Source access cannot follow symbolic links or junctions.");
		}
		if (!Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) throw failure("WORKSPACE_SOURCE_PATH_INVALID", "Select an existing regular source file.");
		return current;
	}

	private byte[] readBytes(String relative) throws IOException {
		Path path = resolve(relative);
		try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
			byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
			if (bytes.length > MAX_FILE_BYTES) throw failure("WORKSPACE_SOURCE_TOO_LARGE", "Source text exceeds the 1 MiB limit.");
			decode(bytes); return bytes;
		}
	}

	private static String decode(byte[] bytes) throws IOException {
		try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
		catch (java.nio.charset.CharacterCodingException exception) { throw failure("WORKSPACE_SOURCE_ENCODING", "This file is not valid UTF-8 text."); }
	}

	private static boolean allowed(String relative) {
		if (relative == null || relative.isBlank() || relative.length() > 1024 || relative.contains("\\") || relative.contains(":")) return false;
		for (String part : relative.split("/", -1)) if (part.isBlank() || part.startsWith(".") || EXCLUDED.contains(part)) return false;
		return (relative.startsWith("src/") || relative.startsWith("assets/") || relative.startsWith("data/") || relative.startsWith("models/") || ROOT_FILES.contains(relative))
				&& EXTENSIONS.contains(extension(relative));
	}

	static boolean supports(String relative, long size) {
		return allowed(relative) && size <= MAX_FILE_BYTES;
	}

	private Inventory inventory() throws IOException {
		List<String> paths = new ArrayList<>(); boolean[] truncated = { false }; int[] visited = { 0 };
		Path realRoot = root.toRealPath();
		for (String directory : List.of("src", "assets", "data", "models")) {
			if (!Files.isDirectory(root.resolve(directory), LinkOption.NOFOLLOW_LINKS)) continue;
			Files.walkFileTree(root.resolve(directory), EnumSet.noneOf(FileVisitOption.class), 16, new SimpleFileVisitor<>() {
				@Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					if (++visited[0] > MAX_VISITED) { truncated[0] = true; return FileVisitResult.TERMINATE; }
					String name = dir.getFileName().toString();
					return name.startsWith(".") || EXCLUDED.contains(name) || Files.isSymbolicLink(dir)
							|| !dir.toRealPath().equals(realRoot.resolve(root.relativize(dir))) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
				}
				@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (attrs.isDirectory()) truncated[0] = true;
					if (++visited[0] > MAX_VISITED || paths.size() >= MAX_FILES) { truncated[0] = true; return FileVisitResult.TERMINATE; }
					String relative = root.relativize(file).toString().replace('\\', '/');
					if (attrs.isRegularFile() && !attrs.isSymbolicLink() && allowed(relative)) paths.add(relative);
					return FileVisitResult.CONTINUE;
				}
			});
			if (truncated[0]) break;
		}
		for (String name : ROOT_FILES) if (Files.isRegularFile(root.resolve(name), LinkOption.NOFOLLOW_LINKS)) {
			if (paths.size() >= MAX_FILES) { truncated[0] = true; break; }
			paths.add(name);
		}
		paths.sort(String::compareTo); return new Inventory(paths, truncated[0]);
	}

	JsonObject index() throws IOException {
		Inventory inventory = inventory(); JsonArray entries = new JsonArray(); int bytes = 0, scanned = 0, skipped = 0;
		boolean truncated = inventory.truncated();
		for (String relative : inventory.paths()) {
			if (!relative.endsWith(".java") && !relative.endsWith("/fabric.mod.json")) continue;
			if (entries.size() >= MAX_EVIDENCE * 3 || bytes >= MAX_INDEX_BYTES) { truncated = true; break; }
			try {
				byte[] content = readBytes(relative);
				if (bytes + content.length > MAX_INDEX_BYTES) { truncated = true; break; }
				bytes += content.length; scanned++;
				String source = decode(content);
				if (relative.endsWith(".java")) javaEvidence(relative, source, entries); else manifestEvidence(relative, source, entries);
			} catch (SourceFailure | com.google.gson.JsonParseException | IllegalStateException exception) { skipped++; }
		}
		if (entries.size() >= MAX_EVIDENCE) truncated = true;
		JsonArray ordered = new JsonArray();
		entries.asList().stream().sorted(Comparator.comparingInt(entry -> switch (entry.getAsJsonObject().get("kind").getAsString()) {
			case "entrypoint" -> 0; case "registration" -> 1; default -> 2;
		})).limit(MAX_EVIDENCE).forEach(ordered::add);
		JsonObject result = new JsonObject(); result.add("entries", ordered); result.addProperty("scannedFiles", scanned);
		result.addProperty("skippedFiles", skipped); result.addProperty("truncated", truncated); return result;
	}

	private static void javaEvidence(String path, String source, JsonArray entries) {
		ASTParser parser = ASTParser.newParser(AST.JLS21); parser.setSource(source.toCharArray()); parser.setResolveBindings(false);
		Map<String, String> options = new HashMap<>();
		org.jboss.forge.roaster._shade.org.eclipse.jdt.core.JavaCore.setComplianceOptions("21", options);
		parser.setCompilerOptions(options);
		CompilationUnit unit = (CompilationUnit) parser.createAST(null);
		Set<String> imports = new HashSet<>(), deferredFields = new HashSet<>();
		for (Object raw : unit.imports()) imports.add(((ImportDeclaration) raw).getName().getFullyQualifiedName());
		unit.accept(new ASTVisitor() {
			@Override public boolean visit(FieldDeclaration node) {
				if (node.getType().toString().contains("DeferredRegister") && imports.stream().anyMatch(value -> value.endsWith(".registries.DeferredRegister")))
					for (Object raw : node.fragments()) deferredFields.add(((VariableDeclarationFragment) raw).getName().getIdentifier());
				return true;
			}
		});
		unit.accept(new ASTVisitor() {
			private void add(String kind, ASTNode node, String symbol, String resource) {
				int start = node.getStartPosition(), end = Math.min(source.length(), start + node.getLength());
				evidence(entries, kind, path, unit.getLineNumber(start), symbol, source.substring(start, Math.min(end, start + 400)), resource);
			}
			@Override public boolean visit(TypeDeclaration node) {
				for (Object implemented : node.superInterfaceTypes()) {
					String type = implemented.toString();
					if ((type.equals("ModInitializer") && imports.contains("net.fabricmc.api.ModInitializer"))
							|| (type.equals("ClientModInitializer") && imports.contains("net.fabricmc.api.ClientModInitializer")))
						add("entrypoint", node.getName(), node.getName() + " implements " + type, null);
				}
				for (Object modifier : node.modifiers()) if (modifier instanceof Annotation annotation
						&& annotation.getTypeName().getFullyQualifiedName().equals("Mod")
						&& (imports.contains("net.minecraftforge.fml.common.Mod") || imports.contains("net.neoforged.fml.common.Mod")))
					add("entrypoint", annotation, node.getName().getIdentifier(), null);
				return true;
			}
			@Override public boolean visit(MethodInvocation node) {
				String receiver = node.getExpression() == null ? "" : node.getExpression().toString();
				String method = node.getName().getIdentifier();
				if (method.equals("register") && ((receiver.equals("Registry") && imports.stream().anyMatch(value -> value.equals("net.minecraft.core.Registry") || value.equals("net.minecraft.registry.Registry")))
						|| deferredFields.contains(receiver))) add("registration", node, registrationSymbol(node, receiver), null);
				if ((method.equals("fromNamespaceAndPath") || method.equals("of")) && resourceType(receiver, imports))
					addResource(node, receiver + "." + method, node.arguments());
				return true;
			}
			@Override public boolean visit(ClassInstanceCreation node) {
				if (resourceType(node.getType().toString(), imports)) addResource(node, node.getType().toString(), node.arguments());
				return true;
			}
			private void addResource(ASTNode node, String symbol, List<?> arguments) {
				if (arguments.size() == 1 && arguments.getFirst() instanceof StringLiteral value) add("resource_reference", node, symbol, value.getLiteralValue());
				if (arguments.size() == 2 && arguments.get(0) instanceof StringLiteral namespace && arguments.get(1) instanceof StringLiteral value)
					add("resource_reference", node, symbol, namespace.getLiteralValue() + ":" + value.getLiteralValue());
			}
		});
	}

	private static boolean resourceType(String type, Set<String> imports) {
		return type.equals("net.minecraft.resources.ResourceLocation") || type.equals("net.minecraft.util.Identifier")
				|| type.equals("ResourceLocation") && imports.contains("net.minecraft.resources.ResourceLocation")
				|| type.equals("Identifier") && imports.contains("net.minecraft.util.Identifier");
	}

	private static String registrationSymbol(MethodInvocation node, String receiver) {
		if (!node.arguments().isEmpty() && node.arguments().getFirst() instanceof StringLiteral literal)
			return literal.getLiteralValue() + " · " + receiver + ".register";
		for (ASTNode parent = node.getParent(); parent != null; parent = parent.getParent()) {
			if (parent instanceof VariableDeclarationFragment field) return field.getName() + " · " + receiver + ".register";
			if (parent instanceof MethodDeclaration method) return method.getName() + " · " + receiver + ".register";
		}
		return receiver + ".register";
	}

	private static void manifestEvidence(String path, String source, JsonArray entries) {
		JsonObject manifest = JsonParser.parseString(source).getAsJsonObject();
		if (!manifest.has("entrypoints") || !manifest.get("entrypoints").isJsonObject()) return;
		for (var entry : manifest.getAsJsonObject("entrypoints").entrySet()) if (entry.getValue().isJsonArray())
			for (var value : entry.getValue().getAsJsonArray()) {
				String symbol = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString()
						: value.isJsonObject() && value.getAsJsonObject().has("value") ? value.getAsJsonObject().get("value").getAsString() : "";
				if (symbol.isBlank()) continue;
				int offset = source.indexOf(new com.google.gson.JsonPrimitive(symbol).toString());
				int line = offset < 0 ? 1 : 1 + (int) source.substring(0, offset).chars().filter(character -> character == '\n').count();
				evidence(entries, "entrypoint", path, line, symbol, entry.getKey() + ": " + value, null);
			}
	}

	private static void evidence(JsonArray entries, String kind, String path, int line, String symbol, String text, String resource) {
		String id = sha256((path + "\n" + line + "\n" + kind + "\n" + text).getBytes(StandardCharsets.UTF_8));
		// Identical occurrences on one line share the same visible evidence/location.
		if (entries.asList().stream().anyMatch(entry -> entry.getAsJsonObject().get("id").getAsString().equals(id))) return;
		if (entries.asList().stream().filter(entry -> entry.getAsJsonObject().get("kind").getAsString().equals(kind)).count() >= MAX_EVIDENCE) return;
		JsonObject entry = new JsonObject(); entry.addProperty("id", id);
		entry.addProperty("kind", kind); entry.addProperty("relativePath", path); entry.addProperty("line", Math.max(1, line));
		entry.addProperty("symbol", symbol); entry.addProperty("evidence", text);
		if (resource != null) entry.addProperty("resourceId", resource); entries.add(entry);
	}

	private static String extension(String path) { int dot = path.lastIndexOf('.'); return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT); }
	private static String sha256(byte[] bytes) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
		catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
	}
	private record Inventory(List<String> paths, boolean truncated) {}
	record Edit(String relativePath, byte[] before, byte[] after) {
		boolean changed() { return !Arrays.equals(before, after); }
		JsonObject result() { JsonObject result = new JsonObject(); result.addProperty("relativePath", relativePath);
			result.addProperty("sha256", sha256(after)); result.addProperty("size", after.length); result.addProperty("changed", changed()); return result; }
	}
	@FunctionalInterface interface Commit { void run() throws Exception; }
	static SourceFailure failure(String code, String message) { return new SourceFailure(code, message); }
	static final class SourceFailure extends IllegalArgumentException {
		final String code;
		SourceFailure(String code, String message) { super(message); this.code = code; }
	}
}
