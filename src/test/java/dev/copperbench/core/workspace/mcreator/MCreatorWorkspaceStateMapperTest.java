/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.core.workspace.mcreator;

import dev.copperbench.core.workspace.ProductMetadataManager;
import dev.copperbench.core.workspace.WorkspaceState;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MCreatorWorkspaceStateMapperTest {

	@TempDir Path root;

	@BeforeAll static void initializeUpstreamRuntime() throws Exception {
		McreatorTestRuntime.ensureInitialized();
	}

	@Test void mapsResourcePackWorkspaceKindAndLoaderToTheUiContract() throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("copper_pack");
		settings.setModName("Copper Pack");
		settings.setVersion("1.0.0");
		settings.setCurrentGenerator("resourcepack-1.21.1");
		UUID workspaceId = UUID.fromString("55555555-5555-4555-8555-555555555555");
		Path workspaceFile = root.resolve("copper_pack.mcreator");

		try (Workspace workspace = Workspace.createWorkspace(workspaceFile.toFile(), settings)) {
			WorkspaceState mapped = new MCreatorWorkspaceStateMapper().map(workspace,
					new ProductMetadataManager.Metadata(1, workspaceId, 0));
			assertEquals("resource_pack", mapped.kind());
			assertEquals("resourcepack-1.21.1", mapped.generator().get("id").getAsString());
			assertEquals("resource_pack", mapped.generator().get("loader").getAsString());
			assertEquals("1.21.1", mapped.generator().get("minecraftVersion").getAsString());
			assertTrue(mapped.elements().isEmpty());
		}
	}
}
