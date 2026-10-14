package dev.copperbench.installer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class InstallerReceiptTest {
    @TempDir Path directory;

    @Test void atomicReplacementKeepsSystemReceiptReadable() throws Exception {
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Path receipt = directory.resolve(InstallEngine.RECEIPT);
        Files.writeString(receipt, "target=/temporary/staging\n");
        var readable = PosixFilePermissions.fromString("rw-r--r--");
        Files.setPosixFilePermissions(receipt, readable);
        Properties properties = new Properties();
        properties.setProperty("target", "/opt/copperbench");
        properties.setProperty("scope", "system");

        InstallerMain.saveProperties(receipt, properties);

        assertEquals(readable, Files.getPosixFilePermissions(receipt));
        assertEquals("/opt/copperbench", InstallerMain.loadProperties(receipt).getProperty("target"));
    }

    @Test void transferFilesStayPrivateWhenCreatedAndReplaced() throws Exception {
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Path transfer = directory.resolve("request.properties");
        var privatePermissions = PosixFilePermissions.fromString("rw-------");
        Properties properties = new Properties();
        properties.setProperty("operation", "install");

        InstallerMain.saveProperties(transfer, properties);
        assertEquals(privatePermissions, Files.getPosixFilePermissions(transfer));
        properties.setProperty("operation", "uninstall");
        InstallerMain.saveProperties(transfer, properties);
        assertEquals(privatePermissions, Files.getPosixFilePermissions(transfer));
        assertEquals("uninstall", InstallerMain.loadProperties(transfer).getProperty("operation"));
    }
}
