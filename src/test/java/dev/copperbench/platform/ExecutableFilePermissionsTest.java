package dev.copperbench.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutableFilePermissionsTest {
	@TempDir Path root;

	@Test void ownerExecuteIsAppliedOnPosixAndPortableElsewhere() throws Exception {
		Path file = root.resolve("gradlew");
		Files.writeString(file, "#!/usr/bin/env sh\n");
		ExecutableFilePermissions.ensureOwnerExecutable(file);
		assertTrue(Files.isRegularFile(file));
		if (ExecutableFilePermissions.posixSupported(file)) {
			assertTrue(Files.getPosixFilePermissions(file).contains(PosixFilePermission.OWNER_EXECUTE));
		}
	}

	@Test void missingExecutableIsRejected() {
		assertThrows(IOException.class, () -> ExecutableFilePermissions.ensureOwnerExecutable(root.resolve("missing")));
	}

	@Test void bothWorkspaceProvisioningPathsRestoreGradleWrapperExecution() throws Exception {
		String fabric = Files.readString(Path.of("src/main/java/dev/copperbench/generator/fabric/Fabric1211Generator.java"));
		assertTrue(fabric.contains("ExecutableFilePermissions.ensureOwnerExecutable(root.resolve(\"gradlew\"))"));
		String plugin = Files.readString(Path.of("src/main/java/dev/copperbench/generator/PluginWorkspaceLayout.java"));
		assertTrue(plugin.contains("ExecutableFilePermissions.ensureOwnerExecutable(normalizedRoot.resolve(\"gradlew\"))"));
	}
}
