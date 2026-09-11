package dev.copperbench.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PrivatePathPermissionsTest {
    @TempDir Path root;

    @Test void ownerOnlyPermissionsAreAppliedWhenPosixIsAvailableAndArePortableOtherwise() throws Exception {
        Path directory = root.resolve("private");
        PrivatePathPermissions.createPrivateDirectory(directory);
        Path file = directory.resolve("descriptor.json");
        Files.writeString(file, "{}\n");
        PrivatePathPermissions.makePrivateFile(file);
        assertTrue(Files.isDirectory(directory));
        assertTrue(Files.isRegularFile(file));
        if (PrivatePathPermissions.posixSupported(file)) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(file));
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE), Files.getPosixFilePermissions(directory));
        }
    }
}
