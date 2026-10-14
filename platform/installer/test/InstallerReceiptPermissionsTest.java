/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

/** Regression for readable system installation metadata after atomic replacement. */
public final class InstallerReceiptPermissionsTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("copperbench-receipt-permissions-");
        try {
            Path receipt = directory.resolve(InstallEngine.RECEIPT);
            Files.writeString(receipt, "target=/temporary/staging\n");
            var readable = PosixFilePermissions.fromString("rw-r--r--");
            Files.setPosixFilePermissions(receipt, readable);
            Properties next = new Properties();
            next.setProperty("target", "/opt/copperbench");
            next.setProperty("scope", "system");
            InstallerMain.saveProperties(receipt, next);
            if (!Files.getPosixFilePermissions(receipt).equals(readable))
                throw new AssertionError("A replaced system receipt must remain readable to ordinary users");
            if (!InstallerMain.loadProperties(receipt).getProperty("target").equals("/opt/copperbench"))
                throw new AssertionError("The updated receipt was not published");
            Path privateFile = directory.resolve("request.properties");
            InstallerMain.saveProperties(privateFile, next);
            var privatePermissions = PosixFilePermissions.fromString("rw-------");
            if (!Files.getPosixFilePermissions(privateFile).equals(privatePermissions))
                throw new AssertionError("New private transfer files must remain private");
            InstallerMain.saveProperties(privateFile, next);
            if (!Files.getPosixFilePermissions(privateFile).equals(privatePermissions))
                throw new AssertionError("Replacing a private file must not broaden access");
            System.out.println("PASS: system receipt readability survives replacement; private files remain private");
        } finally { InstallerMain.deleteScratch(directory); }
    }
}
