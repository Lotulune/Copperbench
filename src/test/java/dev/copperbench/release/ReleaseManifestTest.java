/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.tracks.VersionTrackCatalog;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseManifestTest {

	@Test void officialManifestMatchesTheUiCoreFixtureAndCatalog() throws Exception {
		JsonObject actual = ReleaseManifest.official();
		JsonObject fixture = JsonParser.parseString(Files.readString(
				Path.of("ui-core/fixtures/v1.0/release/release-notes.json"))).getAsJsonObject();
		assertEquals(fixture, actual);
		assertEquals(VersionTrackCatalog.official().toProjection(), actual.getAsJsonObject("versionTracks"));
		assertFalse(actual.getAsJsonObject("privacy").get("implicitNetworkServices").getAsBoolean());
		assertEquals("in_progress", actual.getAsJsonObject("g7").get("status").getAsString());
		JsonArray golden = actual.getAsJsonObject("claims").getAsJsonArray("goldenCompileClaimed");
		assertEquals("fabric-26.2", golden.get(0).getAsString());
		assertEquals("neoforge-26.2", golden.get(1).getAsString());
		assertEquals("fabric-26.1.2", golden.get(2).getAsString());
		assertEquals("neoforge-26.1.2", golden.get(3).getAsString());
		assertEquals("fabric-1.21.1", golden.get(4).getAsString());
		assertEquals("neoforge-1.21.1", golden.get(5).getAsString());
		assertEquals("fabric-1.20.1", golden.get(6).getAsString());
		assertEquals("neoforge-1.20.1", golden.get(7).getAsString());
		assertEquals(8, golden.size());
		assertEquals(0, actual.getAsJsonObject("claims").getAsJsonArray("generateReadyNotGolden").size());
		assertFalse(actual.getAsJsonArray("knownLimitations").toString()
				.contains("FABRIC_262_COMPILE_PREVIEW_NOT_GOLDEN"));
		assertFalse(actual.getAsJsonArray("knownLimitations").toString()
				.contains("NEOFORGE_1201_COMPILE_PROBE_NOT_GOLDEN"));
		String limits = actual.getAsJsonArray("knownLimitations").toString();
		assertTrue(limits.contains("HYPERV_GUEST_GUI_START_NOT_CLAIMED"));
		assertTrue(limits.contains("RESOURCE_PACK_PREPARE_DOES_NOT_AUTO_LAUNCH"));
		assertFalse(limits.contains("CLEAN_WINDOWS_HYPERV_GUEST_PENDING"));
		assertFalse(limits.contains("CLEAN_WIN11_EXTERNAL_MACHINE_PENDING"));
		assertFalse(limits.contains("RESOURCE_PACK_CLIENT_NOT_LAUNCHED"));
		String coverage = actual.getAsJsonArray("featureCoverage").toString();
		assertTrue(coverage.contains("HTMAXBUTTON"));
		assertTrue(coverage.contains("file/copper_ready_pack.zip"));
		assertTrue(coverage.contains("window_chrome_jcef"));
		String pending = actual.getAsJsonObject("g7").getAsJsonArray("pendingMachineEvidence").toString();
		assertFalse(pending.contains("hyperv_guest_windows10"));
		assertFalse(pending.contains("hyperv_guest_windows11"));
		assertFalse(pending.contains("windows11_clean_external_machine"));
		assertEquals(0, actual.getAsJsonObject("g7").getAsJsonArray("pendingMachineEvidence").size());
		assertFalse(pending.contains("code_signing"));
		assertFalse(pending.contains("final_public_brand_legal_review"));
		assertTrue(limits.contains("CODE_SIGNING_UNSIGNED_GITHUB"));
		assertTrue(limits.contains("PUBLIC_DISTRIBUTION_GITHUB_ONLY"));
		assertFalse(limits.contains("CODE_SIGNING_PENDING"));
		assertFalse(limits.contains("FINAL_PUBLIC_BRAND_PENDING"));
		assertEquals("github_public_fork", actual.getAsJsonObject("developmentFocus").get("stage").getAsString());
		assertEquals(0, actual.getAsJsonObject("developmentFocus").getAsJsonArray("deferred").size());
		assertEquals("Windows 11", actual.getAsJsonObject("platform").get("minimumOs").getAsString());
		assertTrue(limits.contains("WINDOWS_10_NOT_SUPPORTED"));
		assertFalse(limits.contains("WINDOWS_10_MACHINE_RETEST_PENDING"));
		assertTrue(coverage.contains("windows_10"));
		JsonObject elements = actual.getAsJsonObject("elementCoverage");
		assertEquals(4, elements.getAsJsonArray("firstPartySlice").size());
		assertTrue(elements.getAsJsonArray("unsupportedInNewUi").toString().contains("livingentity"));
		assertEquals(19, actual.getAsJsonObject("upstreamTools").getAsJsonArray("tools").size());
	}
}
