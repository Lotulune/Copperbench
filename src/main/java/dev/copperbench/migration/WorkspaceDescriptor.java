package dev.copperbench.migration;

import dev.copperbench.core.workspace.WorkspaceFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Resolves one real root-level workspace document before a copy writes anything. */
final class WorkspaceDescriptor {
	private WorkspaceDescriptor() {}

	static Path find(Path directory) throws IOException {
		if (directory == null || !Files.isDirectory(directory))
			throw new IllegalArgumentException("A workspace directory is required");
		Path root = WorkspaceFiles.requireDirectory(directory);
		try (var files = Files.list(root)) {
			var candidates = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
					.endsWith(".mcreator")).sorted().toList();
			if (candidates.size() != 1)
				throw new IllegalArgumentException("Workspace directory must contain exactly one .mcreator file");
			return WorkspaceFiles.requireRegularFile(root, candidates.getFirst());
		}
	}
}
