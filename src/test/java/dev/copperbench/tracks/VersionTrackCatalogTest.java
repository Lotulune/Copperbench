/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.tracks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.tracks.VersionTrackCatalog.LoaderId;
import dev.copperbench.tracks.VersionTrackCatalog.SupportStatus;
import dev.copperbench.tracks.VersionTrackCatalog.TrackId;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionTrackCatalogTest {

	private final VersionTrackCatalog catalog = VersionTrackCatalog.official();

	@Test void officialCatalogHasFourTracksAndTwoLoadersEach() {
		assertEquals(4, catalog.tracks().size());
		assertEquals(TrackId.LATEST_STABLE, catalog.tracks().get(0).id());
		catalog.tracks().forEach(track -> assertEquals(2, track.loaders().size()));
	}

	@Test void allFourTracksAreSupported() {
		assertEquals("26.2", VersionTrackCatalog.LATEST_MINECRAFT);
		assertEquals("26.1", VersionTrackCatalog.PREVIOUS_MINECRAFT);
		assertEquals(SupportStatus.SUPPORTED, catalog.decision("fabric-26.2").status());
		assertEquals("TRACK_SUPPORTED", catalog.decision("neoforge-26.2").reasonCode());
		assertEquals(SupportStatus.SUPPORTED, catalog.decision("fabric-26.1.2").status());
		assertEquals("TRACK_SUPPORTED", catalog.decision("neoforge-26.1.2").reasonCode());
		assertEquals(SupportStatus.SUPPORTED, catalog.decision("fabric-1.21.1").status());
		assertEquals(SupportStatus.SUPPORTED, catalog.decision("neoforge-1.21.1").status());
		assertEquals(SupportStatus.SUPPORTED, catalog.decision("fabric-1.20.1").status());
		assertEquals(SupportStatus.SUPPORTED, catalog.decision("neoforge-1.20.1").status());
		assertEquals("TRACK_SUPPORTED", catalog.decision("neoforge-1.20.1").reasonCode());
		assertEquals(SupportStatus.SUPPORTED,
				catalog.tracks().get(0).loader(LoaderId.FABRIC).orElseThrow().status());
		assertTrue(catalog.decision("fabric-1.21.1").generatable());
		assertTrue(catalog.decision("fabric-1.20.1").generatable());
		assertTrue(catalog.decision("neoforge-1.20.1").generatable());
		assertTrue(catalog.decision("fabric-26.1.2").generatable());
		assertTrue(catalog.decision("fabric-26.2").generatable());
		assertEquals("UNSUPPORTED_GENERATOR", catalog.decision("fabric-26.3").reasonCode());
	}

	@Test void firstPartyGeneratorsCoverAllFourTracks() {
		assertTrue(catalog.firstPartyGenerator("fabric-1.20.1"));
		assertTrue(catalog.firstPartyGenerator("neoforge-1.20.1"));
		assertTrue(catalog.firstPartyGenerator("fabric-26.1.2"));
		assertTrue(catalog.firstPartyGenerator("fabric-26.2"));
		assertTrue(catalog.firstPartyGenerator("neoforge-26.2"));
		assertFalse(catalog.firstPartyGenerator("fabric-26.3"));
	}

	@Test void onlySameVersionFabricNeoForgePairsAreMigratable() {
		assertTrue(catalog.migratable("fabric-1.21.1", "neoforge-1.21.1"));
		assertTrue(catalog.migratable("neoforge-1.21.1", "fabric-1.21.1"));
		assertTrue(catalog.migratable("fabric-1.20.1", "neoforge-1.20.1"));
		assertFalse(catalog.migratable("fabric-1.21.1", "fabric-1.21.1"));
		assertFalse(catalog.migratable("fabric-1.21.1", "neoforge-1.20.1"));
		assertTrue(catalog.migratable("fabric-26.1.2", "neoforge-26.1.2"));
		assertFalse(catalog.migratable("fabric-26.1.2", "neoforge-1.21.1"));
		assertTrue(catalog.migratable("fabric-26.2", "neoforge-26.2"));
		assertFalse(catalog.migratable("fabric-26.2", "neoforge-26.1.2"));
	}

	@Test void projectionMatchesTheUiCoreFixture() throws Exception {
		JsonObject projection = catalog.toProjection();
		JsonObject fixture = JsonParser.parseString(Files.readString(
				Path.of("ui-core/fixtures/v1.0/tracks/version-tracks.json"))).getAsJsonObject();
		fixture.remove("currentWorkspace");
		assertEquals(fixture, projection);
	}
}
