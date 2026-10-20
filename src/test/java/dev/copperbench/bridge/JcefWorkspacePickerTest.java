/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.bridge;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JcefWorkspacePickerTest {

	@TempDir Path directory;

	@Test void pickerRequestsUseThePackagedWorkbenchAndABoundedObjectEnvelope() {
		assertTrue(JcefWorkspaceOpenBridgeTransport.trustedUrl("http://mcreator/copperbench/ui/index.html#simple"));
		for (String url : new String[]{"http://mcreator.evil/copperbench/ui/index.html",
				"http://evil@mcreator/copperbench/ui/index.html", "http://mcreator:80/copperbench/ui/index.html",
				"http://mcreator/copperbench/ui/%69ndex.html", "file:///index.html", "about:blank"})
			assertFalse(JcefWorkspaceOpenBridgeTransport.trustedUrl(url), url);
		assertFalse(JcefWorkspaceOpenBridgeTransport.trustedUrl(null));
		for (String body : new String[]{"null", "[]", "false", "\"path\""})
			assertThrows(IllegalArgumentException.class, () -> JcefWorkspaceOpenBridgeTransport.parseRequest(
					JcefWorkspaceOpenBridgeTransport.QUERY_PREFIX + body));
		assertThrows(IllegalArgumentException.class, () -> JcefWorkspaceOpenBridgeTransport.parseRequest(
				JcefWorkspaceOpenBridgeTransport.QUERY_PREFIX + " ".repeat(JcefWorkspaceOpenBridgeTransport.MAX_REQUEST_CHARS)));
	}

	@Test void folderHintsAcceptOnlyBoundedStringsAndKnownProperties() {
		assertNull(JcefWorkspaceOpenBridgeTransport.pickerInitialPath(JsonParser.parseString(
				"{\"operation\":\"choose_workspace_folder\"}").getAsJsonObject()));
		for (String body : new String[]{"{\"initialPath\":null}", "{\"initialPath\":7}",
				"{\"initialPath\":[]}", "{\"initialPath\":\"bad\\u0000path\"}", "{\"delete\":true}",
				"{\"initialPath\":\"" + "x".repeat(32769) + "\"}"})
			assertThrows(IllegalArgumentException.class, () -> JcefWorkspaceOpenBridgeTransport.pickerInitialPath(
					JsonParser.parseString(body).getAsJsonObject()), body.substring(0, Math.min(body.length(), 60)));
	}

	@Test void cancellationHasNoSelectedPathAndFileSelectionRequiresAnExistingWorkspace() throws Exception {
		var cancelled = JcefWorkspaceOpenBridgeTransport.pickerResult(null, false);
		assertTrue(cancelled.get("cancelled").getAsBoolean());
		assertEquals(1, cancelled.size());
		Path workspace = Files.writeString(directory.resolve("my-creation.mcreator"), "{}");
		var selected = JcefWorkspaceOpenBridgeTransport.pickerResult(workspace.toFile(), false);
		assertFalse(selected.get("cancelled").getAsBoolean());
		assertEquals(workspace.toAbsolutePath().toString(), selected.get("workspaceFile").getAsString());
		assertThrows(IllegalArgumentException.class, () -> JcefWorkspaceOpenBridgeTransport.pickerResult(directory.toFile(), false));
		assertThrows(IllegalArgumentException.class, () -> JcefWorkspaceOpenBridgeTransport.pickerResult(directory.resolve("missing.mcreator").toFile(), false));
		Path otherFile = Files.writeString(directory.resolve("notes.txt"), "keep");
		assertThrows(IllegalArgumentException.class, () -> JcefWorkspaceOpenBridgeTransport.pickerResult(otherFile.toFile(), false));
	}

	@Test void choosingAFolderDoesNotCreateDirectoriesOrApplyLegacyEmptyFolderRestrictions() throws Exception {
		Path existingFile = Files.writeString(directory.resolve("keep.txt"), "untouched");
		Path proposed = directory.resolve("not-created").resolve("my-creation");
		assertEquals(directory.toFile(), JcefWorkspaceOpenBridgeTransport.pickerInitialDirectory(proposed.toString(), null));
		assertFalse(Files.exists(proposed.getParent()));
		var selected = JcefWorkspaceOpenBridgeTransport.pickerResult(directory.toFile(), true);
		assertEquals(directory.toAbsolutePath().toString(), selected.get("path").getAsString());
		assertEquals("untouched", Files.readString(existingFile));
		assertThrows(IllegalArgumentException.class, () -> JcefWorkspaceOpenBridgeTransport.pickerResult(existingFile.toFile(), true));
	}

	@Test void bootstrapAdvertisesPickersWithoutRemovingExistingExportOperations() {
		String script = JcefWorkspaceOpenBridgeTransport.generateBootstrapScript();
		for (String value : new String[]{"workspacePickerAvailable: true", "workspaceFolderPickerAvailable: true",
				"chooseWorkspace:", "chooseWorkspaceFolder:", "choose_workspace", "choose_workspace_folder",
				"open_verified_export_folder", "open_build_folder"}) assertTrue(script.contains(value), value);
	}
}
