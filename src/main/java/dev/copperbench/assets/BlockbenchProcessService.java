package dev.copperbench.assets;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Manages one external Blockbench process without exposing arbitrary paths or arguments. */
public final class BlockbenchProcessService implements AutoCloseable {
	private final AssetWorkspaceService assets;
	private final Path executable;
	private final ProcessStarter starter;
	private final BlockbenchInstallationDetector.Installation installation;
	private final EditLifecycle lifecycle;
	private Process process;
	private AssetTaskLease lease;
	private Snapshot snapshot;
	private PreparedEdit preparedEdit;

	public BlockbenchProcessService(AssetWorkspaceService assets, Path executable) {
		this(assets, executable, command -> new ProcessBuilder(command).directory(assets.workspaceRoot().toFile())
				.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start(),
				new BlockbenchInstallationDetector(), EditLifecycle.noOp());
	}

	public BlockbenchProcessService(AssetWorkspaceService assets, Path executable, EditLifecycle lifecycle) {
		this(assets, executable, command -> new ProcessBuilder(command).directory(assets.workspaceRoot().toFile())
				.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start(),
				new BlockbenchInstallationDetector(), lifecycle);
	}

	BlockbenchProcessService(AssetWorkspaceService assets, Path executable, ProcessStarter starter) {
		this(assets, executable, starter, new BlockbenchInstallationDetector(path -> "5.0.0"), EditLifecycle.noOp());
	}

	BlockbenchProcessService(AssetWorkspaceService assets, Path executable, ProcessStarter starter,
			BlockbenchInstallationDetector detector) {
		this(assets, executable, starter, detector, EditLifecycle.noOp());
	}

	BlockbenchProcessService(AssetWorkspaceService assets, Path executable, ProcessStarter starter,
			BlockbenchInstallationDetector detector, EditLifecycle lifecycle) {
		this.assets = Objects.requireNonNull(assets, "assets");
		this.executable = executable == null ? null : executable.toAbsolutePath().normalize();
		this.starter = Objects.requireNonNull(starter, "starter");
		this.installation = Objects.requireNonNull(detector, "detector").detect(this.executable);
		this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
		this.snapshot = availabilitySnapshot();
	}

	public synchronized Snapshot status() {
		if (process != null && !process.isAlive()) {
			int exitCode = process.exitValue();
			String currentHash = currentHash(snapshot.relativePath());
			String diagnostic = exitCode == 0 ? null : "BLOCKBENCH_EXITED_ABNORMALLY";
			Completion completion = null;
			if (currentHash == null) diagnostic = "ASSET_MISSING_AFTER_BLOCKBENCH";
			else if (!currentHash.equals(snapshot.openedSha256())) {
				diagnostic = "ASSET_CHANGED_EXTERNALLY";
				try {
					AssetDescriptor current = assets.findByRelativePath(snapshot.relativePath()).orElseThrow(() ->
							new BlockbenchBridgeException("ASSET_MISSING_AFTER_BLOCKBENCH",
									"The edited Blockbench asset is no longer indexed"));
					completion = lifecycle.complete(preparedEdit, current);
					if (completion != null) diagnostic = null;
				} catch (RuntimeException exception) {
					diagnostic = "BLOCKBENCH_CHANGE_COMMIT_FAILED";
				}
			}
			snapshot = new Snapshot(State.EXITED, snapshot.assetId(), snapshot.relativePath(), process.pid(), exitCode,
					snapshot.openedSha256(), currentHash, installation.version(), diagnostic,
					preparedEdit == null ? null : preparedEdit.recoveryPointId(),
					completion == null ? null : completion.workspaceRevision(), completion != null);
			process = null;
			preparedEdit = null;
			releaseLease();
		}
		return snapshot;
	}

	public synchronized Snapshot openAsset(String assetId) {
		Snapshot current = status();
		if (current.state() == State.RUNNING)
			throw new BlockbenchBridgeException("BLOCKBENCH_ALREADY_RUNNING", "A Blockbench asset task is already running");
		if (!isAvailable()) {
			snapshot = availabilitySnapshot();
			return snapshot;
		}
		AssetDescriptor descriptor = assets.findById(assetId).orElseThrow(() ->
				new BlockbenchBridgeException("ASSET_NOT_FOUND", "The requested asset is not indexed"));
		if (!descriptor.relativePath().toLowerCase(java.util.Locale.ROOT).endsWith(".bbmodel"))
			throw new BlockbenchBridgeException("BLOCKBENCH_ASSET_UNSUPPORTED",
					"Only indexed .bbmodel sources can be opened in Blockbench");
		Path authorized = assets.resolveAuthorizedPath(descriptor.relativePath());
		try {
			lease = AssetTaskLease.tryAcquire(assets.workspaceRoot(), descriptor.id()).orElseThrow(() ->
					new BlockbenchBridgeException("BLOCKBENCH_ASSET_LEASED",
							"The requested asset is already open in another Blockbench task"));
			preparedEdit = lifecycle.prepare(descriptor);
			process = starter.start(List.of(executable.toString(), authorized.toString()));
			snapshot = new Snapshot(State.RUNNING, descriptor.id(), descriptor.relativePath(), process.pid(), null,
				descriptor.sha256(), descriptor.sha256(), installation.version(), null,
				preparedEdit == null ? null : preparedEdit.recoveryPointId(), null, false);
			return snapshot;
		} catch (IOException exception) {
			process = null;
			preparedEdit = null;
			releaseLease();
			snapshot = new Snapshot(State.FAILED, descriptor.id(), descriptor.relativePath(), null, null,
					descriptor.sha256(), descriptor.sha256(), installation.version(), "BLOCKBENCH_START_FAILED",
					null, null, false);
			return snapshot;
		} catch (RuntimeException exception) {
			preparedEdit = null;
			releaseLease();
			throw exception;
		}
	}

	private boolean isAvailable() {
		return installation.state() == BlockbenchInstallationDetector.State.READY;
	}

	private String currentHash(String relativePath) {
		if (relativePath == null) return null;
		try {
			return AssetDescriptor.fromFile(assets.workspaceRoot(), assets.resolveAuthorizedPath(relativePath)).sha256();
		} catch (RuntimeException | IOException exception) {
			return null;
		}
	}

	private Snapshot availabilitySnapshot() {
		return isAvailable()
				? new Snapshot(State.READY, null, null, null, null, null, null, installation.version(), null,
						null, null, false)
				: new Snapshot(State.UNAVAILABLE, null, null, null, null, null, null, installation.version(),
						installation.diagnosticCode(), null, null, false);
	}

	private void releaseLease() {
		if (lease == null) return;
		try {
			lease.close();
		} catch (IOException ignored) {
			// Closing the process still ends the in-memory ownership even if the OS reports a release error.
		} finally {
			lease = null;
		}
	}

	@Override public synchronized void close() {
		if (process != null && process.isAlive()) process.destroy();
		process = null;
		preparedEdit = null;
		releaseLease();
		snapshot = availabilitySnapshot();
	}

	public enum State { UNAVAILABLE, READY, RUNNING, EXITED, FAILED }

	public record Snapshot(State state, String assetId, String relativePath, Long processId, Integer exitCode,
			String openedSha256, String currentSha256, String blockbenchVersion, String diagnosticCode,
			String recoveryPointId, Long workspaceRevision, boolean changeCommitted) {
	}

	public record PreparedEdit(String recoveryPointId, long openedRevision, String assetId, String relativePath,
			String openedSha256) {
	}

	public record Completion(String recoveryPointId, long workspaceRevision) {
	}

	public interface EditLifecycle {
		PreparedEdit prepare(AssetDescriptor asset);
		Completion complete(PreparedEdit prepared, AssetDescriptor current);

		static EditLifecycle noOp() {
			return new EditLifecycle() {
				@Override public PreparedEdit prepare(AssetDescriptor asset) { return null; }
				@Override public Completion complete(PreparedEdit prepared, AssetDescriptor current) { return null; }
			};
		}
	}

	@FunctionalInterface interface ProcessStarter {
		Process start(List<String> command) throws IOException;
	}
}
