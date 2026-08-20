package dev.copperbench.assets;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Deterministic, workspace-scoped ZIP exporter for standalone Minecraft resource packs. */
public final class ResourcePackExportService {
	private final AssetWorkspaceService assets;

	public ResourcePackExportService(AssetWorkspaceService assets) {
		this.assets = Objects.requireNonNull(assets, "assets");
	}

	public ExportResult export(String sourceRelativeDirectory, String outputRelativePath) {
		Path source = resolveDirectory(sourceRelativeDirectory);
		Path output = resolveOutput(outputRelativePath);
		if (output.startsWith(source)) throw new AssetPathViolationException("Output must be outside the resource pack source");
		if (!output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
			throw new AssetPathViolationException("Resource pack output must be a .zip file");
		Path metadata = source.resolve("pack.mcmeta");
		if (!Files.isRegularFile(metadata))
			throw new AssetPathViolationException("Resource pack requires pack.mcmeta");
		try {
			JsonParser.parseString(Files.readString(metadata));
			List<Path> files = Files.walk(source).filter(Files::isRegularFile).filter(path -> !Files.isSymbolicLink(path))
					.sorted(Comparator.comparing(path -> source.relativize(path).toString().replace('\\', '/'))).toList();
			Path temp = output.resolveSibling(output.getFileName() + ".tmp");
			Files.createDirectories(output.getParent());
			try (OutputStream stream = Files.newOutputStream(temp); ZipOutputStream zip = new ZipOutputStream(stream)) {
				for (Path file : files) {
					String name = source.relativize(file).toString().replace('\\', '/');
					ZipEntry entry = new ZipEntry(name);
					entry.setTime(0L);
					zip.putNextEntry(entry);
					Files.copy(file, zip);
					zip.closeEntry();
				}
			}
			try {
				Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
			}
			return new ExportResult(outputRelativePath.replace('\\', '/'), sha256(output), files.size());
		} catch (IOException | RuntimeException exception) {
			throw new AssetPathViolationException("Resource pack export failed: " + exception.getMessage());
		}
	}

	private Path resolveDirectory(String relative) {
		Path path = resolveWorkspacePath(relative);
		try {
			Path real = path.toRealPath();
			if (!Files.isDirectory(real) || !real.startsWith(assets.workspaceRoot())) throw new IOException();
			return real;
		} catch (IOException exception) {
			throw new AssetPathViolationException("Resource pack directory is not authorized");
		}
	}

	private Path resolveOutput(String relative) {
		Path path = resolveWorkspacePath(relative);
		if (path.getFileName() == null || path.startsWith(assets.workspaceRoot().resolve(".copperbench")))
			throw new AssetPathViolationException("Resource pack output is not authorized");
		try {
			Path parent = path.getParent();
			if (parent != null && Files.exists(parent) && !parent.toRealPath().startsWith(assets.workspaceRoot()))
				throw new IOException("output parent escapes workspace");
		} catch (IOException exception) {
			throw new AssetPathViolationException("Resource pack output is not authorized");
		}
		return path;
	}

	private Path resolveWorkspacePath(String relative) {
		if (relative == null || relative.isBlank()) throw new AssetPathViolationException("Path must not be blank");
		Path requested;
		try { requested = Path.of(relative); } catch (RuntimeException exception) { throw new AssetPathViolationException("Path is invalid"); }
		Path normalized = assets.workspaceRoot().resolve(requested).normalize();
		if (requested.isAbsolute() || !normalized.startsWith(assets.workspaceRoot()))
			throw new AssetPathViolationException("Path escapes the workspace");
		return normalized;
	}

	private static String sha256(Path path) throws IOException {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
		catch (NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
	}

	public record ExportResult(String relativePath, String sha256, int fileCount) { }
}
