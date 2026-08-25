/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.io;

import net.mcreator.io.zip.ZipIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZipIOTest {

	@TempDir Path temporaryDirectory;

	@Test void extractsAnArchiveIntoAnEmptyDestination() throws Exception {
		Path archive = archive("workspace/readme.txt", "safe");
		Path destination = temporaryDirectory.resolve("workspace");

		ZipIO.unzip(archive.toString(), destination.toString());

		assertEquals("safe", Files.readString(destination.resolve("workspace/readme.txt")));
	}

	@Test void rejectsTraversalWithoutLeavingPartialOutput() throws Exception {
		Path archive = temporaryDirectory.resolve("malicious.zip");
		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
			write(output, "workspace/readme.txt", "partial");
			write(output, "../outside.txt", "escaped");
		}
		Path destination = temporaryDirectory.resolve("imported");

		assertThrows(IOException.class, () -> ZipIO.unzip(archive.toString(), destination.toString()));

		assertFalse(Files.exists(temporaryDirectory.resolve("outside.txt")));
		assertFalse(Files.exists(destination));
	}

	@Test void refusesToMergeIntoANonEmptyDestination() throws Exception {
		Path archive = archive("workspace/readme.txt", "new");
		Path destination = temporaryDirectory.resolve("existing");
		Files.createDirectories(destination);
		Files.writeString(destination.resolve("keep.txt"), "keep");

		assertThrows(IOException.class, () -> ZipIO.unzip(archive.toString(), destination.toString()));

		assertEquals("keep", Files.readString(destination.resolve("keep.txt")));
	}

	private Path archive(String entry, String value) throws IOException {
		Path archive = temporaryDirectory.resolve("workspace.zip");
		try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
			write(output, entry, value);
		}
		return archive;
	}

	private static void write(ZipOutputStream output, String name, String value) throws IOException {
		output.putNextEntry(new ZipEntry(name));
		output.write(value.getBytes(StandardCharsets.UTF_8));
		output.closeEntry();
	}
}
