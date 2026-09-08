package dev.copperbench.assets;

import dev.copperbench.platform.RuntimePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

	@Test void linuxExecutableWithoutWindowsVersionMetadataIsLaunchableButUnverified() throws IOException {
		assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
		Path executable = Files.writeString(temp.resolve("blockbench"), "fixture");
		Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwxr-xr-x"));
		var result = new BlockbenchInstallationDetector(RuntimePlatform.detect("Linux", "x86_64"), path -> null)
				.detect(executable);
		assertEquals(BlockbenchInstallationDetector.State.READY_UNVERIFIED, result.state());
		assertNull(result.version());
		assertNull(result.diagnosticCode());
	}

	@Test void linuxNonExecutableIsRejectedBeforeVersionDetection() throws IOException {
		assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
		Path executable = Files.writeString(temp.resolve("blockbench"), "fixture");
		Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rw-r--r--"));
		var detector = new BlockbenchInstallationDetector(RuntimePlatform.detect("Linux", "x86_64"),
				path -> { throw new AssertionError("must not read"); });
		var result = detector.detect(executable);
		assertEquals(BlockbenchInstallationDetector.State.UNAVAILABLE, result.state());
		assertEquals("BLOCKBENCH_NOT_EXECUTABLE", result.diagnosticCode());
	}
}
