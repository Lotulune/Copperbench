package dev.copperbench.assets;

import com.google.gson.JsonParser;
import dev.copperbench.core.workspace.WorkspaceFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
		if (!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS))
			throw new AssetPathViolationException("Resource pack requires pack.mcmeta");
		try {
			List<Path> files = WorkspaceFiles.regularFiles(source, relative -> false);
			WorkspaceFiles.requireRegularFile(source, metadata);
			try (var reader = new InputStreamReader(Files.newInputStream(metadata, LinkOption.NOFOLLOW_LINKS),
					StandardCharsets.UTF_8)) {
				JsonParser.parseReader(reader);
			}
			WorkspaceFiles.writeAtomically(assets.workspaceRoot(), output, stream -> {
				try (ZipOutputStream zip = new ZipOutputStream(stream)) {
					for (Path file : files) {
						Path input = WorkspaceFiles.requireRegularFile(source, file);
						String name = source.relativize(file).toString().replace('\\', '/');
						ZipEntry entry = new ZipEntry(name);
						entry.setTime(0L);
						zip.putNextEntry(entry);
						try (InputStream content = Files.newInputStream(input, LinkOption.NOFOLLOW_LINKS)) {
							content.transferTo(zip);
						}
						zip.closeEntry();
					}
				}
			});
			return new ExportResult(outputRelativePath.replace('\\', '/'),
					sha256(WorkspaceFiles.requireRegularFile(assets.workspaceRoot(), output)), files.size());
		} catch (IOException | RuntimeException exception) {
			throw new AssetPathViolationException("Resource pack export failed: " + exception.getMessage());
		}
	}

	private Path resolveDirectory(String relative) {
		Path path = resolveWorkspacePath(relative);
		try {
			return WorkspaceFiles.requireDirectory(WorkspaceFiles.requireWithin(assets.workspaceRoot(), path));
		} catch (IOException exception) {
			throw new AssetPathViolationException("Resource pack directory is not authorized");
		}
	}

	private Path resolveOutput(String relative) {
		Path path = resolveWorkspacePath(relative);
		try {
			Path canonical = WorkspaceFiles.requireWithin(assets.workspaceRoot(), path);
			if (canonical.getFileName() == null || canonical.startsWith(assets.workspaceRoot().resolve(".copperbench")))
				throw new IOException("Resource pack output is not authorized");
			return canonical;
		} catch (IOException exception) {
			throw new AssetPathViolationException("Resource pack output is not authorized");
		}
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
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
				byte[] buffer = new byte[8192];
				for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
	}

	public record ExportResult(String relativePath, String sha256, int fileCount) { }
}
