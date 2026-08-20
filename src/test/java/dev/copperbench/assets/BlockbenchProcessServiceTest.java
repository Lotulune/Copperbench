package dev.copperbench.assets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockbenchProcessServiceTest {
	@TempDir Path temp;
	private Path workspace;
	private AssetDescriptor model;

	@BeforeEach void fixture() throws IOException {
		workspace = temp.resolve("workspace");
		Path modelFile = workspace.resolve("assets/copperbench/models/copper_lamp.bbmodel");
		Files.createDirectories(modelFile.getParent());
		Files.writeString(modelFile, "{}");
		model = new AssetWorkspaceService(workspace).list().getFirst();
	}

	@Test void reportsUnavailableWithoutStartingAnything() {
		var service = new BlockbenchProcessService(new AssetWorkspaceService(workspace), null,
				command -> { throw new AssertionError("starter must not run"); });
		assertEquals(BlockbenchProcessService.State.UNAVAILABLE, service.openAsset(model.id()).state());
		assertEquals("BLOCKBENCH_NOT_CONFIGURED", service.status().diagnosticCode());
	}

	@Test void launchesOnlyTheConfiguredExecutableAndAuthorizedIndexedModel() throws IOException {
		Path executable = temp.resolve("Blockbench.exe");
		Files.write(executable, new byte[] { 1 });
		AtomicReference<List<String>> command = new AtomicReference<>();
		FakeProcess process = new FakeProcess(7788);
		var service = new BlockbenchProcessService(new AssetWorkspaceService(workspace), executable, arguments -> {
			command.set(arguments);
			return process;
		});

		var opened = service.openAsset(model.id());
		assertEquals(BlockbenchProcessService.State.RUNNING, opened.state());
		assertEquals(7788L, opened.processId());
		assertEquals(executable.toAbsolutePath().toString(), command.get().get(0));
		assertTrue(command.get().get(1).endsWith("copper_lamp.bbmodel"));
		assertThrows(BlockbenchBridgeException.class, () -> service.openAsset(model.id()));
		service.close();
		assertTrue(process.destroyed);
	}

	@Test void rejectsAnIndexedNonBlockbenchAsset() throws IOException {
		Path json = workspace.resolve("assets/copperbench/models/plain.json");
		Files.writeString(json, "{}");
		AssetDescriptor unsupported = new AssetWorkspaceService(workspace).findByRelativePath(
				"assets/copperbench/models/plain.json").orElseThrow();
		Path executable = temp.resolve("Blockbench.exe");
		Files.write(executable, new byte[] { 1 });
		var service = new BlockbenchProcessService(new AssetWorkspaceService(workspace), executable,
				command -> { throw new AssertionError("starter must not run"); });

		BlockbenchBridgeException error = assertThrows(BlockbenchBridgeException.class,
				() -> service.openAsset(unsupported.id()));
		assertEquals("BLOCKBENCH_ASSET_UNSUPPORTED", error.code());
	}

	@Test
	@EnabledIfSystemProperty(named = "copperbench.blockbench.executable", matches = ".+")
	void startsAndStopsTheInstalledBlockbenchExecutable() {
		Path executable = Path.of(System.getProperty("copperbench.blockbench.executable"));
		var service = new BlockbenchProcessService(new AssetWorkspaceService(workspace), executable);
		var opened = service.openAsset(model.id());
		assertTrue(opened.state() == BlockbenchProcessService.State.RUNNING
				|| opened.state() == BlockbenchProcessService.State.EXITED);
		service.close();
	}

	@Test void reportsAnExternalAssetChangeAfterBlockbenchExits() throws IOException {
		Path executable = temp.resolve("Blockbench.exe");
		Files.write(executable, new byte[] { 1 });
		FakeProcess process = new FakeProcess(9911);
		var service = new BlockbenchProcessService(new AssetWorkspaceService(workspace), executable,
				arguments -> process);
		service.openAsset(model.id());
		Files.writeString(workspace.resolve("assets/copperbench/models/copper_lamp.bbmodel"), "{\"edited\":true}");
		process.finish(0);

		var exited = service.status();
		assertEquals(BlockbenchProcessService.State.EXITED, exited.state());
		assertEquals("ASSET_CHANGED_EXTERNALLY", exited.diagnosticCode());
		assertTrue(!exited.openedSha256().equals(exited.currentSha256()));
	}

	@Test void reportsAbnormalExitWithoutChangingTheCommittedAsset() throws IOException {
		Path executable = temp.resolve("Blockbench.exe");
		Files.write(executable, new byte[] { 1 });
		FakeProcess process = new FakeProcess(9922);
		var service = new BlockbenchProcessService(new AssetWorkspaceService(workspace), executable,
				arguments -> process);
		service.openAsset(model.id());
		process.finish(17);
		var exited = service.status();
		assertEquals(BlockbenchProcessService.State.EXITED, exited.state());
		assertEquals("BLOCKBENCH_EXITED_ABNORMALLY", exited.diagnosticCode());
		assertEquals(exited.openedSha256(), exited.currentSha256());
	}

	@Test void leasesAnAssetAcrossServiceInstancesUntilTheTaskEnds() throws IOException {
		Path executable = temp.resolve("Blockbench.exe");
		Files.write(executable, new byte[] { 1 });
		FakeProcess firstProcess = new FakeProcess(101);
		FakeProcess secondProcess = new FakeProcess(202);
		var first = new BlockbenchProcessService(new AssetWorkspaceService(workspace), executable,
				arguments -> firstProcess);
		var second = new BlockbenchProcessService(new AssetWorkspaceService(workspace), executable,
				arguments -> secondProcess);

		first.openAsset(model.id());
		BlockbenchBridgeException leased = assertThrows(BlockbenchBridgeException.class,
				() -> second.openAsset(model.id()));
		assertEquals("BLOCKBENCH_ASSET_LEASED", leased.code());
		first.close();
		assertEquals(BlockbenchProcessService.State.RUNNING, second.openAsset(model.id()).state());
		second.close();
	}

	@Test void internalLeaseFilesAreNotIndexedAsAssets() throws IOException {
		try (AssetTaskLease ignored = AssetTaskLease.tryAcquire(workspace, model.id()).orElseThrow()) {
			assertEquals(1, new AssetWorkspaceService(workspace).list().size());
		}
	}

	private static final class FakeProcess extends Process {
		private final long pid;
		private boolean alive = true;
		private boolean destroyed;
		private int exitCode;

		private FakeProcess(long pid) { this.pid = pid; }
		@Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
		@Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
		@Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
		@Override public int waitFor() { alive = false; return 0; }
		@Override public boolean waitFor(long timeout, TimeUnit unit) { alive = false; return true; }
		@Override public int exitValue() { if (alive) throw new IllegalThreadStateException(); return exitCode; }
		@Override public void destroy() { alive = false; destroyed = true; }
		@Override public boolean isAlive() { return alive; }
		@Override public long pid() { return pid; }
		private void finish(int code) { exitCode = code; alive = false; }
	}
}
