package dev.copperbench.assets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic editor process for bridge tests; never launches or closes a user's editor. */
public final class BlockbenchProcessFixture extends Process {
	private volatile boolean alive = true;
	public final CountDownLatch destroyed = new CountDownLatch(1);
	public final AtomicInteger starts = new AtomicInteger();

	public BlockbenchProcessService service(Path root, BlockbenchProcessService.EditLifecycle lifecycle) throws Exception {
		Path executable = Files.write(root.resolve("Blockbench.exe"), new byte[] { 1 });
		return new BlockbenchProcessService(new AssetWorkspaceService(root), executable, command -> { starts.incrementAndGet(); return this; },
				new BlockbenchInstallationDetector(path -> "5.1.6"), lifecycle);
	}

	@Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
	@Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
	@Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
	@Override public int waitFor() { alive = false; return 0; }
	@Override public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 0; }
	@Override public void destroy() { alive = false; destroyed.countDown(); }
	@Override public boolean isAlive() { return alive; }
	@Override public long pid() { return 12345; }
	public void finish() { alive = false; }
}
