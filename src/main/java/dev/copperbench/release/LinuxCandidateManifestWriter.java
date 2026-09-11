/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Build-time entrypoint for materializing the Stage 15 development candidate manifest. */
public final class LinuxCandidateManifestWriter {
	private LinuxCandidateManifestWriter() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 1 || args[0].isBlank())
			throw new IllegalArgumentException("Expected output path argument");
		Path output = Path.of(args[0]).toAbsolutePath().normalize();
		Files.createDirectories(output.getParent());
		Files.writeString(output,
				new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(LinuxCandidateManifest.development())
						+ System.lineSeparator(), StandardCharsets.UTF_8);
	}
}
