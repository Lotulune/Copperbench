package dev.copperbench.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class VersionCopyFingerprintTest {
    @TempDir Path root;
    @Test void binaryContentsCannotMasqueradeAsAnotherFileInTheTreeFingerprint() throws Exception {
        Path single = Files.createDirectory(root.resolve("single"));
        Path multiple = Files.createDirectory(root.resolve("multiple"));
        // Both trees have the same path-NUL-raw-bytes-NUL concatenation, but different files.
        Files.write(single.resolve("a"), new byte[]{0, 'b', 0, 'x'});
        Files.write(multiple.resolve("a"), new byte[0]); Files.write(multiple.resolve("b"), new byte[]{'x'});
        assertNotEquals(VersionCopyUpgradeExecution.fingerprint(single), VersionCopyUpgradeExecution.fingerprint(multiple));
        assertEquals(VersionCopyUpgradeExecution.fingerprint(single), VersionCopyUpgradeExecution.fingerprint(single));
    }
}
