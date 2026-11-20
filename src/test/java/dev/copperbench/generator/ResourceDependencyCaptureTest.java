/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceDependencyCaptureTest {
	@TempDir Path root;

	@Test void earlierHashesAreReusedOnlyForTheSamePathSizeAndModificationTime() throws Exception {
		String hash = "a".repeat(64);
		Path index = root.resolve(ResourceDependencyCapture.INDEX);
		Files.createDirectories(index.getParent());
		Files.writeString(index, """
				{"state":"available","artifacts":[
				  {"path":"D:/cache/fabric-api.jar","sha256":"%s","size":42,"lastModifiedMillis":1700},
				  {"path":"D:/cache/legacy.jar","sha256":"%s"},
				  {"path":"D:/cache/bad.jar","sha256":"not-a-hash","size":1,"lastModifiedMillis":1}
				]}""".formatted(hash, hash));
		assertEquals(Map.of("D:/cache/fabric-api.jar|42|1700", hash), ResourceDependencyCapture.previousHashes(index),
				"entries without stat metadata or with invalid hashes are hashed again");

		Files.writeString(index, "{\"state\":\"collecting\"}");
		assertEquals(Map.of(), ResourceDependencyCapture.previousHashes(index));
		Files.writeString(index, "not json");
		assertEquals(Map.of(), ResourceDependencyCapture.previousHashes(index));
		assertEquals(Map.of(), ResourceDependencyCapture.previousHashes(root.resolve("missing.json")));
	}
}
