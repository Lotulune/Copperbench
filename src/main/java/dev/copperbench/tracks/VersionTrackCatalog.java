/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.tracks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * First-party source of truth for the four Minecraft version tracks and their
 * Fabric/NeoForge support status. Dynamic tracks may coincide with a fixed track
 * without duplicating generators (ADR-0004).
 */
public final class VersionTrackCatalog {

	public static final String LATEST_MINECRAFT = "26.2";
	public static final String PREVIOUS_MINECRAFT = "26.1";
	public static final String FIXED_1211 = "1.21.1";
	public static final String FIXED_1201 = "1.20.1";

	public enum TrackId {
		LATEST_STABLE, PREVIOUS_STABLE, MINECRAFT_1_21_1, MINECRAFT_1_20_1
	}

	public enum LoaderId {
		FABRIC, NEOFORGE
	}

	public enum SupportStatus {
		SUPPORTED, PREVIEW, UNAVAILABLE, COINCIDES
	}

	public record LoaderStatus(LoaderId loader, String generatorId, String minecraftVersion, SupportStatus status,
			String pluginId, String reasonCode, String notes) {
		public LoaderStatus {
			Objects.requireNonNull(loader);
			Objects.requireNonNull(generatorId);
			Objects.requireNonNull(minecraftVersion);
			Objects.requireNonNull(status);
			Objects.requireNonNull(reasonCode);
			Objects.requireNonNull(notes);
		}
	}

	public record Track(TrackId id, String minecraftVersion, String displayName, boolean dynamic,
			List<LoaderStatus> loaders) {
		public Track {
			Objects.requireNonNull(id);
			Objects.requireNonNull(minecraftVersion);
			Objects.requireNonNull(displayName);
			loaders = List.copyOf(loaders);
		}

		public Optional<LoaderStatus> loader(LoaderId loader) {
			return loaders.stream().filter(status -> status.loader() == loader).findFirst();
		}
	}

	public record CapabilityDecision(String generatorId, SupportStatus status, String reasonCode, String message) {
		public CapabilityDecision {
			Objects.requireNonNull(generatorId);
			Objects.requireNonNull(status);
			Objects.requireNonNull(reasonCode);
			Objects.requireNonNull(message);
		}

		public boolean generatable() {
			return status == SupportStatus.SUPPORTED;
		}
	}

	private final List<Track> tracks;

	private VersionTrackCatalog(List<Track> tracks) {
		this.tracks = List.copyOf(tracks);
	}

	public static VersionTrackCatalog official() {
		LoaderStatus fabricLatest = new LoaderStatus(LoaderId.FABRIC, "fabric-26.2", LATEST_MINECRAFT,
				SupportStatus.SUPPORTED, null, "TRACK_SUPPORTED",
				"Latest stable Minecraft 26.2. First-party Fabric vertical slice with compile and runClient evidence (unobfuscated Loom 1.17.19).");
		LoaderStatus neoForgeLatest = new LoaderStatus(LoaderId.NEOFORGE, "neoforge-26.2", LATEST_MINECRAFT,
				SupportStatus.SUPPORTED, null, "TRACK_SUPPORTED",
				"Latest stable Minecraft 26.2. First-party NeoForge vertical slice with compile and runClient evidence (NeoForge 26.2.0.63).");
		LoaderStatus fabricPrevious = new LoaderStatus(LoaderId.FABRIC, "fabric-26.1.2", PREVIOUS_MINECRAFT,
				SupportStatus.SUPPORTED, "generator-fabric-26.1.2", "TRACK_SUPPORTED",
				"Previous stable Minecraft 26.1.2. First-party Fabric vertical slice with compile and runClient evidence (unobfuscated Loom, Fabric API 0.155.2+26.1.2).");
		LoaderStatus neoForgePrevious = new LoaderStatus(LoaderId.NEOFORGE, "neoforge-26.1.2", PREVIOUS_MINECRAFT,
				SupportStatus.SUPPORTED, "generator-26.1.x", "TRACK_SUPPORTED",
				"Previous stable Minecraft 26.1.2. First-party NeoForge vertical slice with compile and runClient evidence (NeoForge 26.1.2.95).");
		LoaderStatus fabric1211 = new LoaderStatus(LoaderId.FABRIC, "fabric-1.21.1", FIXED_1211,
				SupportStatus.SUPPORTED, null, "TRACK_SUPPORTED",
				"Maintenance track. Copperbench-owned Fabric 1.21.1 vertical slice with golden build and runClient evidence.");
		LoaderStatus neoForge1211 = new LoaderStatus(LoaderId.NEOFORGE, "neoforge-1.21.1", FIXED_1211,
				SupportStatus.SUPPORTED, "generator-1.21.1", "TRACK_SUPPORTED",
				"Maintenance track. Copperbench-owned NeoForge 1.21.1 vertical slice with golden build and runClient evidence.");
		LoaderStatus fabric1201 = new LoaderStatus(LoaderId.FABRIC, "fabric-1.20.1", FIXED_1201,
				SupportStatus.SUPPORTED, null, "TRACK_SUPPORTED",
				"Maintenance track. Copperbench-owned Fabric 1.20.1 vertical slice with compile and runClient evidence (Gradle 8.8 + loom 1.7.4).");
		LoaderStatus neoForge1201 = new LoaderStatus(LoaderId.NEOFORGE, "neoforge-1.20.1", FIXED_1201,
				SupportStatus.SUPPORTED, null, "TRACK_SUPPORTED",
				"Maintenance track. Copperbench-owned NeoForge 1.20.1 vertical slice with compile and runClient evidence (NeoForged Forge 1.20.1-47.1.106 + userdev 7.0.165).");
		return new VersionTrackCatalog(List.of(
				new Track(TrackId.LATEST_STABLE, LATEST_MINECRAFT, "Latest stable (Minecraft 26.2)", true,
						List.of(fabricLatest, neoForgeLatest)),
				new Track(TrackId.PREVIOUS_STABLE, PREVIOUS_MINECRAFT, "Previous stable (Minecraft 26.1)", true,
						List.of(fabricPrevious, neoForgePrevious)),
				new Track(TrackId.MINECRAFT_1_21_1, FIXED_1211, "Minecraft 1.21.1 (maintenance)", false,
						List.of(fabric1211, neoForge1211)),
				new Track(TrackId.MINECRAFT_1_20_1, FIXED_1201, "Minecraft 1.20.1 (maintenance)", false,
						List.of(fabric1201, neoForge1201))));
	}

	public List<Track> tracks() {
		return tracks;
	}

	public Optional<LoaderStatus> findGenerator(String generatorId) {
		if (generatorId == null || generatorId.isBlank())
			return Optional.empty();
		return tracks.stream().flatMap(track -> track.loaders().stream())
				.filter(status -> status.generatorId().equals(generatorId))
				.min(Comparator.comparingInt(status -> rank(status.status())));
	}

	private static int rank(SupportStatus status) {
		return switch (status) {
			case SUPPORTED -> 0;
			case PREVIEW -> 1;
			case COINCIDES -> 2;
			case UNAVAILABLE -> 3;
		};
	}

	public CapabilityDecision decision(String generatorId) {
		return findGenerator(generatorId).map(status -> new CapabilityDecision(status.generatorId(), status.status(),
				status.reasonCode(), status.notes())).orElseGet(() -> new CapabilityDecision(
				generatorId == null ? "" : generatorId, SupportStatus.UNAVAILABLE, "UNSUPPORTED_GENERATOR",
				"The requested generator is not in the version-track catalog."));
	}

	public boolean firstPartyGenerator(String generatorId) {
		return switch (generatorId == null ? "" : generatorId) {
			case "fabric-1.21.1", "neoforge-1.21.1", "fabric-26.1.2", "neoforge-26.1.2",
					"fabric-1.20.1", "neoforge-1.20.1", "fabric-26.2", "neoforge-26.2" -> true;
			default -> false;
		};
	}

	public boolean migratable(String sourceGeneratorId, String targetGeneratorId) {
		return firstPartyGenerator(sourceGeneratorId) && firstPartyGenerator(targetGeneratorId)
				&& !Objects.equals(sourceGeneratorId, targetGeneratorId)
				&& sameMinecraft(sourceGeneratorId, targetGeneratorId);
	}

	private boolean sameMinecraft(String sourceGeneratorId, String targetGeneratorId) {
		Optional<LoaderStatus> source = findGenerator(sourceGeneratorId);
		Optional<LoaderStatus> target = findGenerator(targetGeneratorId);
		return source.isPresent() && target.isPresent()
				&& source.get().minecraftVersion().equals(target.get().minecraftVersion())
				&& source.get().loader() != target.get().loader();
	}

	public JsonObject toProjection() {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", "1.0");
		root.addProperty("latestMinecraftVersion", LATEST_MINECRAFT);
		root.addProperty("previousMinecraftVersion", PREVIOUS_MINECRAFT);
		JsonArray items = new JsonArray();
		for (Track track : tracks) {
			JsonObject json = new JsonObject();
			json.addProperty("id", track.id().name().toLowerCase(Locale.ROOT));
			json.addProperty("minecraftVersion", track.minecraftVersion());
			json.addProperty("displayName", track.displayName());
			json.addProperty("dynamic", track.dynamic());
			JsonArray loaders = new JsonArray();
			for (LoaderStatus status : track.loaders()) {
				JsonObject loader = new JsonObject();
				loader.addProperty("loader", status.loader().name().toLowerCase(Locale.ROOT));
				loader.addProperty("generatorId", status.generatorId());
				loader.addProperty("minecraftVersion", status.minecraftVersion());
				loader.addProperty("status", status.status().name().toLowerCase(Locale.ROOT));
				if (status.pluginId() == null)
					loader.add("pluginId", com.google.gson.JsonNull.INSTANCE);
				else
					loader.addProperty("pluginId", status.pluginId());
				loader.addProperty("reasonCode", status.reasonCode());
				loader.addProperty("notes", status.notes());
				loaders.add(loader);
			}
			json.add("loaders", loaders);
			items.add(json);
		}
		root.add("tracks", items);
		return root;
	}
}
