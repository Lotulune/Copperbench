package dev.copperbench.assets;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/** Cross-process lease for one stable asset identity. */
final class AssetTaskLease implements AutoCloseable {
	private final FileChannel channel;
	private final FileLock lock;

	private AssetTaskLease(FileChannel channel, FileLock lock) {
		this.channel = channel;
		this.lock = lock;
	}

	static Optional<AssetTaskLease> tryAcquire(Path workspaceRoot, String assetId) throws IOException {
		if (assetId == null || !assetId.matches("asset:[0-9a-f]{64}"))
			throw new IllegalArgumentException("A stable asset identity is required");
		Path leases = workspaceRoot.resolve(".copperbench/asset-leases");
		Files.createDirectories(leases);
		Path file = leases.resolve(assetId.substring("asset:".length()) + ".lock");
		FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		try {
			FileLock lock = channel.tryLock();
			if (lock == null) {
				channel.close();
				return Optional.empty();
			}
			return Optional.of(new AssetTaskLease(channel, lock));
		} catch (OverlappingFileLockException exception) {
			channel.close();
			return Optional.empty();
		} catch (IOException exception) {
			channel.close();
			throw exception;
		}
	}

	@Override public void close() throws IOException {
		try {
			if (lock.isValid()) lock.release();
		} finally {
			if (channel.isOpen()) channel.close();
		}
	}
}
