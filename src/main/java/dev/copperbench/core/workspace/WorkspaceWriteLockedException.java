package dev.copperbench.core.workspace;

import java.nio.file.Path;

/** Raised when another process or workspace session already owns the workspace writer lease. */
public final class WorkspaceWriteLockedException extends IllegalStateException {

	public WorkspaceWriteLockedException(Path workspaceDirectory) {
		super("Workspace is already open for writing: " + workspaceDirectory.toAbsolutePath().normalize());
	}

	public WorkspaceWriteLockedException(Path workspaceDirectory, Throwable cause) {
		super("Could not acquire workspace writer lease: " + workspaceDirectory.toAbsolutePath().normalize(), cause);
	}
}
