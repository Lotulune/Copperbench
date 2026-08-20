package dev.copperbench.core.workspace;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/** Cross-process single-writer lease for a workspace directory. */
public final class WorkspaceFileLease implements AutoCloseable {

	private final Path workspaceDirectory;
	private final FileChannel channel;
	private final FileLock lock;

	private WorkspaceFileLease(Path workspaceDirectory, FileChannel channel, FileLock lock) {
		this.workspaceDirectory = workspaceDirectory;
		this.channel = channel;
		this.lock = lock;
	}

	public static Optional<WorkspaceFileLease> tryAcquire(Path workspaceDirectory) throws IOException {
		Path metadataDirectory = workspaceDirectory.toAbsolutePath().normalize().resolve(".copperbench");
		Files.createDirectories(metadataDirectory);
		Path lockFile = metadataDirectory.resolve("workspace.write.lock");
		FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		try {
			FileLock lock = channel.tryLock();
			if (lock == null) {
				channel.close();
				return Optional.empty();
			}
			return Optional.of(new WorkspaceFileLease(workspaceDirectory.toAbsolutePath().normalize(), channel, lock));
		} catch (OverlappingFileLockException exception) {
			channel.close();
			return Optional.empty();
		} catch (IOException exception) {
			channel.close();
			throw exception;
		}
	}

	public void requireValidFor(Path workspaceFile) {
		Path absoluteFile = workspaceFile.toAbsolutePath().normalize();
		if (!lock.isValid() || !absoluteFile.startsWith(workspaceDirectory))
			throw new IllegalStateException("A valid writer lease for this workspace is required");
	}

	@Override public void close() throws IOException {
		try {
			if (lock.isValid())
				lock.release();
		} finally {
			if (channel.isOpen())
				channel.close();
		}
	}
}
