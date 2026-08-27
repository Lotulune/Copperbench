/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.io.zip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.WINDOWS)
class ZipIOWindowsPublishTest {

	@TempDir Path temporaryDirectory;

	@Test void retriesTransientDirectoryLockDuringAtomicPublish() throws Exception {
		Path staging = Files.createDirectory(temporaryDirectory.resolve("staging"));
		Path payload = Files.writeString(staging.resolve("payload.txt"), "safe");
		Path destination = temporaryDirectory.resolve("destination");
		AtomicReference<Throwable> releaseFailure = new AtomicReference<>();

		try (FileChannel channel = FileChannel.open(payload, StandardOpenOption.READ)) {
			Thread releaser = new Thread(() -> {
				try {
					Thread.sleep(125);
					channel.close();
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					releaseFailure.set(interrupted);
				} catch (IOException exception) {
					releaseFailure.set(exception);
				}
			}, "zipio-test-lock-releaser");
			releaser.start();
			try {
				ZipIO.publishStaging(staging, destination);
			} finally {
				releaser.join();
			}
		}

		assertNull(releaseFailure.get());
		assertFalse(Files.exists(staging));
		assertEquals("safe", Files.readString(destination.resolve("payload.txt")));
	}

	@Test void preservesFailureWhenDirectoryLockDoesNotClear() throws Exception {
		Path staging = Files.createDirectory(temporaryDirectory.resolve("locked-staging"));
		Path payload = Files.writeString(staging.resolve("payload.txt"), "safe");
		Path destination = temporaryDirectory.resolve("locked-destination");

		try (FileChannel ignored = FileChannel.open(payload, StandardOpenOption.READ)) {
			assertThrows(AccessDeniedException.class, () -> ZipIO.publishStaging(staging, destination));
		}

		assertTrue(Files.exists(staging));
		assertFalse(Files.exists(destination));
	}
}
