package dev.copperbench.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockbenchInstallationDetectorTest {
	@TempDir Path temp;

	@Test void reportsMissingExecutableWithoutReadingVersion() {
		var detector = new BlockbenchInstallationDetector(path -> { throw new AssertionError("must not read"); });
		var result = detector.detect(temp.resolve("missing.exe"));
		assertEquals(BlockbenchInstallationDetector.State.UNAVAILABLE, result.state());
		assertEquals("BLOCKBENCH_NOT_CONFIGURED", result.diagnosticCode());
	}

	@Test void rejectsUnsupportedMajorVersion() throws IOException {
		Path executable = Files.write(temp.resolve("Blockbench.exe"), new byte[] { 1 });
		var result = new BlockbenchInstallationDetector(path -> "3.9.4").detect(executable);
		assertEquals(BlockbenchInstallationDetector.State.INCOMPATIBLE, result.state());
		assertEquals("BLOCKBENCH_VERSION_UNSUPPORTED", result.diagnosticCode());
	}

	@Test void reportsDetectedCompatibleVersion() throws IOException {
		Path executable = Files.write(temp.resolve("Blockbench.exe"), new byte[] { 1 });
		var result = new BlockbenchInstallationDetector(path -> "5.1.6").detect(executable);
		assertEquals(BlockbenchInstallationDetector.State.READY, result.state());
		assertEquals("5.1.6", result.version());
	}
}
