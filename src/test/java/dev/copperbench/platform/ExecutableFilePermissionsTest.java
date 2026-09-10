package dev.copperbench.platform;

import dev.copperbench.generator.PluginWorkspaceLayout;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.fabric.Fabric1211GoldenWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		Path distribution = Path.of(".").toAbsolutePath().normalize();
		Path fabric = root.resolve("fabric");
		new Fabric1211Generator(distribution).generate(fabric, Fabric1211GoldenWorkspace.create());
		Path plugin = Files.createDirectories(root.resolve("plugin"));
		Path existing = plugin.resolve("gradlew");
		Files.writeString(existing, "#!/bin/sh\r\necho preserved\r\n");
		if (ExecutableFilePermissions.posixSupported(existing))
			Files.setPosixFilePermissions(existing, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
		PluginWorkspaceLayout.ensureGradleRuntime(plugin, distribution, "gradle-9.7.0-bin.zip");
		assertEquals("#!/bin/sh\necho preserved\n", Files.readString(existing));
		for (Path launcher : new Path[] { fabric.resolve("gradlew"), existing }) {
			assertTrue(Files.isRegularFile(launcher));
			if (ExecutableFilePermissions.posixSupported(launcher))
				assertTrue(Files.getPosixFilePermissions(launcher).contains(PosixFilePermission.OWNER_EXECUTE));
		}
	}
}
