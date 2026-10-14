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
import dev.copperbench.tracks.VersionTrackCatalog.Track;
import dev.copperbench.tracks.VersionTrackCatalog.LoaderStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VersionTrackCatalogTest {

	private final VersionTrackCatalog catalog = VersionTrackCatalog.official();

	@Test void officialCatalogHasFourTracksAndTwoLoadersEach() {
		assertEquals(4, catalog.tracks().size());
		assertEquals(TrackId.LATEST_STABLE, catalog.tracks().get(0).id());
		catalog.tracks().forEach(track -> assertEquals(2, track.loaders().size()));
	}

	@Test void latestIsRegisteredPreviewWhileOlderExactTargetsRemainSupported() {
		assertEquals("26.3", VersionTrackCatalog.LATEST_MINECRAFT);
		assertEquals("26.2", VersionTrackCatalog.PREVIOUS_MINECRAFT);
		assertEquals(SupportStatus.SUPPORTED, catalog.decision("fabric-26.2").status());
		assertEquals("TRACK_SUPPORTED", catalog.decision("neoforge-26.2").reasonCode());
		assertEquals(SupportStatus.SUPPORTED, catalog.decision("fabric-26.1.2").status());
		assertEquals("TRACK_SUPPORTED", catalog.decision("neoforge-26.1.2").reasonCode());
		assertEquals(SupportStatus.SUPPORTED, catalog.decision("fabric-1.21.1").status());
		assertEquals(SupportStatus.SUPPORTED, catalog.decision("neoforge-1.21.1").status());
		assertEquals(SupportStatus.SUPPORTED, catalog.decision("fabric-1.20.1").status());
		assertEquals(SupportStatus.SUPPORTED, catalog.decision("neoforge-1.20.1").status());
		assertEquals("TRACK_SUPPORTED", catalog.decision("neoforge-1.20.1").reasonCode());
		assertEquals(SupportStatus.PREVIEW,
				catalog.tracks().get(0).loader(LoaderId.FABRIC).orElseThrow().status());
		assertTrue(catalog.decision("fabric-1.21.1").generatable());
		assertTrue(catalog.decision("fabric-1.20.1").generatable());
		assertTrue(catalog.decision("neoforge-1.20.1").generatable());
		assertTrue(catalog.decision("fabric-26.1.2").generatable());
		assertTrue(catalog.decision("fabric-26.2").generatable());
		assertEquals("PREVIEW_NEGOTIATION_REQUIRED", catalog.decision("fabric-26.3").reasonCode());
		assertFalse(catalog.decision("fabric-26.3").generatable());
	}

	@Test void firstPartyGeneratorsCoverAllFourTracks() {
		assertTrue(catalog.firstPartyGenerator("fabric-1.20.1"));
		assertTrue(catalog.firstPartyGenerator("neoforge-1.20.1"));
		assertTrue(catalog.firstPartyGenerator("fabric-26.1.2"));
		assertTrue(catalog.firstPartyGenerator("fabric-26.2"));
		assertTrue(catalog.firstPartyGenerator("neoforge-26.2"));
		assertTrue(catalog.firstPartyGenerator("fabric-26.3"));
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

	@Test void rotatingDisplayRetainsAllExactIdentitiesAndMigrationBoundaries() {
		var rotated = catalog.withTracks(catalog.tracks().stream()
				.filter(track -> track.id() != TrackId.PREVIOUS_STABLE).toList());
		assertEquals(3, rotated.tracks().size());
		Set<String> expected = Set.of("fabric-26.2", "neoforge-26.2", "fabric-26.1.2", "neoforge-26.1.2",
				"fabric-1.21.1", "neoforge-1.21.1", "fabric-1.20.1", "neoforge-1.20.1");
		assertEquals(expected, rotated.registrations().stream().filter(entry -> entry.status().status() == SupportStatus.SUPPORTED).map(entry -> entry.profile().generatorId())
				.collect(Collectors.toSet()));
		for (String source : expected) {
			assertEquals(catalog.findGenerator(source), rotated.findGenerator(source));
			assertTrue(rotated.firstPartyGenerator(source));
			assertTrue(rotated.decision(source).generatable());
			for (String target : expected)
				assertEquals(catalog.migratable(source, target), rotated.migratable(source, target), source + " -> " + target);
		}
		assertTrue(rotated.migratable("fabric-26.1.2", "neoforge-26.1.2"));
		assertFalse(rotated.migratable("fabric-26.1.2", "neoforge-26.2"));
		assertEquals(4, catalog.tracks().size(), "Display selection must not mutate the original catalog");
	}

	@Test void coincidingTracksDoNotDuplicateOrDowngradeAnExactGenerator() {
		var fixed = catalog.tracks().get(2);
		var coinciding = fixed.loaders().stream().map(status -> new LoaderStatus(status.loader(), status.generatorId(),
				status.minecraftVersion(), status.javaRelease(), SupportStatus.COINCIDES, status.pluginId(),
				"TRACK_COINCIDES", "Same exact generator as the maintenance track")).toList();
		var display = List.of(new Track(TrackId.LATEST_STABLE, "1.21.1", "Latest", true, coinciding), fixed);
		var selected = catalog.withTracks(display);
		assertEquals(10, selected.registrations().size());
		assertEquals(catalog.findGenerator("fabric-1.21.1"), selected.findGenerator("fabric-1.21.1"));
		assertTrue(selected.decision("fabric-1.21.1").generatable());
		assertEquals(21, selected.findRegistration("fabric-1.21.1").orElseThrow().profile().javaRelease());
		assertEquals(17, selected.findRegistration("neoforge-1.20.1").orElseThrow().profile().javaRelease());
	}

	@Test void unknownIdsRemainUnavailableAndCannotBeIntroducedByDisplaySelection() {
		for (String id : new String[] { null, "", " ", "fabric-26.4", "fabric-26.1", "neoforge-26.4" }) {
			assertTrue(catalog.findGenerator(id).isEmpty());
			assertFalse(catalog.firstPartyGenerator(id));
			assertEquals("UNSUPPORTED_GENERATOR", catalog.decision(id).reasonCode());
			assertFalse(catalog.migratable(id, "fabric-26.2"));
		}
		var unknown = new LoaderStatus(LoaderId.FABRIC, "fabric-26.4", "26.4", 25, SupportStatus.PREVIEW,
				"unregistered", "TRACK_PREVIEW", "Not registered");
		assertThrows(IllegalArgumentException.class, () -> catalog.withTracks(
				List.of(new Track(TrackId.LATEST_STABLE, "26.3", "Latest", true, List.of(unknown)))));
	}
}
