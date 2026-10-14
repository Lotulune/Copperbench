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
import dev.copperbench.generator.JavaGeneratorProfile;
import dev.copperbench.generator.fabric.Fabric1211Generator;
import dev.copperbench.generator.neoforge.NeoForge1211Generator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * First-party registry of exact generators, with a separate four-track display.
 * Rotating or coinciding display tracks never removes a persisted generator or
 * changes its execution profile (ADR-0004).
 */
public final class VersionTrackCatalog {

	public static final String LATEST_MINECRAFT = "26.3";
	public static final String PREVIOUS_MINECRAFT = "26.2";
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

	public record LoaderStatus(LoaderId loader, String generatorId, String minecraftVersion, int javaRelease,
			SupportStatus status, String pluginId, String reasonCode, String notes) {
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
	private final Map<String, Registration> generators;

	/** Identity reserved before a product backend is ready; it must never launch an older backend. */
	public record PendingProfile(String generatorId, String minecraftVersion, int javaRelease, LoaderId loader)
			implements JavaGeneratorProfile {}

	/** A support decision and its exact backend identity, independent of display tracks. */
	public record Registration(LoaderStatus status, JavaGeneratorProfile profile) {
		public Registration {
			Objects.requireNonNull(status);
			Objects.requireNonNull(profile);
			if (profile instanceof PendingProfile pending) {
				if (!status.generatorId().equals(pending.generatorId()) || status.javaRelease() != pending.javaRelease()
						|| !status.minecraftVersion().equals(pending.minecraftVersion()) || status.loader() != pending.loader()
						|| status.status() == SupportStatus.SUPPORTED)
					throw new IllegalArgumentException("Pending target cannot claim an executable backend");
			} else if (!status.generatorId().equals(profile.generatorId()) || status.javaRelease() != profile.javaRelease()
					|| !(profile instanceof Fabric1211Generator.Profile || profile instanceof NeoForge1211Generator.Profile)
					|| (status.loader() == LoaderId.FABRIC) != (profile instanceof Fabric1211Generator.Profile))
				throw new IllegalArgumentException("Generator status and execution profile do not match");
		}
	}

	private VersionTrackCatalog(List<Registration> registrations, List<Track> tracks) {
		Map<String, Registration> byId = new LinkedHashMap<>();
		for (Registration registration : registrations)
			if (byId.putIfAbsent(registration.status().generatorId(), registration) != null)
				throw new IllegalArgumentException("Duplicate generator registration: " + registration.status().generatorId());
		this.generators = Collections.unmodifiableMap(byId);
		this.tracks = List.copyOf(tracks);
		for (Track track : this.tracks)
			for (LoaderStatus status : track.loaders())
				if (!generators.containsKey(status.generatorId()))
					throw new IllegalArgumentException("Unregistered display generator: " + status.generatorId());
	}

	public static VersionTrackCatalog official() {
		LoaderStatus fabricLatest = new LoaderStatus(LoaderId.FABRIC, "fabric-26.2", "26.2", 25,
				SupportStatus.SUPPORTED, "generator-fabric-26.2", "TRACK_SUPPORTED",
				"前一稳定轨 Minecraft 26.2。新建工作区模板基于 Goldorion Fabric 26.1.2 适配，使用 Fabric API 0.158.0+26.2；内置功能范围已有编译和客户端启动（runClient）验证记录。");
		LoaderStatus neoForgeLatest = new LoaderStatus(LoaderId.NEOFORGE, "neoforge-26.2", "26.2", 25,
				SupportStatus.SUPPORTED, "generator-26.2", "TRACK_SUPPORTED",
				"前一稳定轨 Minecraft 26.2。新建工作区模板基于 NeoForge 26.1.2 适配，使用 NeoForge 26.2.0.63；内置功能范围已有编译和客户端启动（runClient）验证记录。");
		LoaderStatus fabricPrevious = new LoaderStatus(LoaderId.FABRIC, "fabric-26.1.2", "26.1.2", 25,
				SupportStatus.SUPPORTED, "generator-fabric-26.1.2", "TRACK_SUPPORTED",
				"历史精确版本 Minecraft 26.1.2。使用无混淆 Loom 和 Fabric API 0.155.2+26.1.2；内置功能范围已有编译和客户端启动（runClient）验证记录。");
		LoaderStatus neoForgePrevious = new LoaderStatus(LoaderId.NEOFORGE, "neoforge-26.1.2", "26.1.2", 25,
				SupportStatus.SUPPORTED, "generator-26.1.x", "TRACK_SUPPORTED",
				"历史精确版本 Minecraft 26.1.2。使用 NeoForge 26.1.2.95；内置功能范围已有编译和客户端启动（runClient）验证记录。");
		LoaderStatus fabric1211 = new LoaderStatus(LoaderId.FABRIC, "fabric-1.21.1", FIXED_1211, 21,
				SupportStatus.SUPPORTED, "generator-1.21.1", "TRACK_SUPPORTED",
				"维护轨 Minecraft 1.21.1。新建工作区模板基于 Goldorion Fabric 26.1.2 适配，使用 ResourceLocation 和 Java 21；内置功能范围已有基准构建（Golden Build）和客户端启动（runClient）验证记录。");
		LoaderStatus neoForge1211 = new LoaderStatus(LoaderId.NEOFORGE, "neoforge-1.21.1", FIXED_1211, 21,
				SupportStatus.SUPPORTED, "generator-1.21.1", "TRACK_SUPPORTED",
				"维护轨 Minecraft 1.21.1。使用 Copperbench 维护的 NeoForge 生成器；内置功能范围已有基准构建（Golden Build）和客户端启动（runClient）验证记录。");
		LoaderStatus fabric1201 = new LoaderStatus(LoaderId.FABRIC, "fabric-1.20.1", FIXED_1201, 17,
				SupportStatus.SUPPORTED, "generator-1.20.1", "TRACK_SUPPORTED",
				"维护轨 Minecraft 1.20.1。使用 Java 17、Gradle 8.8 和 Loom 1.7.4；内置功能范围已有编译和客户端启动（runClient）验证记录。");
		LoaderStatus neoForge1201 = new LoaderStatus(LoaderId.NEOFORGE, "neoforge-1.20.1", FIXED_1201, 17,
				SupportStatus.SUPPORTED, "generator-1.20.1", "TRACK_SUPPORTED",
				"维护轨 Minecraft 1.20.1。此轨道的新建工作区模板使用 Forge 1.20.1-47.1.106 和 userdev 7.0.165；内置功能范围已有编译和客户端启动（runClient）验证记录。");
		LoaderStatus fabricPreview = preview(LoaderId.FABRIC, "fabric-26.3", "generator-fabric-26.3");
		LoaderStatus neoForgePreview = preview(LoaderId.NEOFORGE, "neoforge-26.3", "generator-neoforge-26.3");
		List<Registration> registrations = List.of(
				new Registration(fabricPreview, Fabric1211Generator.Profile.FABRIC_263),
				new Registration(neoForgePreview, NeoForge1211Generator.Profile.NEOFORGE_263),
				new Registration(fabric1211, Fabric1211Generator.Profile.FABRIC_1211),
				new Registration(fabricPrevious, Fabric1211Generator.Profile.FABRIC_261),
				new Registration(fabricLatest, Fabric1211Generator.Profile.FABRIC_262),
				new Registration(fabric1201, Fabric1211Generator.Profile.FABRIC_1201),
				new Registration(neoForge1211, NeoForge1211Generator.Profile.NEOFORGE_1211),
				new Registration(neoForgePrevious, NeoForge1211Generator.Profile.NEOFORGE_261),
				new Registration(neoForgeLatest, NeoForge1211Generator.Profile.NEOFORGE_262),
				new Registration(neoForge1201, NeoForge1211Generator.Profile.NEOFORGE_1201));
		return new VersionTrackCatalog(registrations, List.of(
				new Track(TrackId.LATEST_STABLE, LATEST_MINECRAFT, "最新稳定轨（Minecraft 26.3）", true,
						List.of(fabricPreview, neoForgePreview)),
				new Track(TrackId.PREVIOUS_STABLE, PREVIOUS_MINECRAFT, "前一稳定轨（Minecraft 26.2）", true,
						List.of(fabricLatest, neoForgeLatest)),
				new Track(TrackId.MINECRAFT_1_21_1, FIXED_1211, "维护轨（Minecraft 1.21.1）", false,
						List.of(fabric1211, neoForge1211)),
				new Track(TrackId.MINECRAFT_1_20_1, FIXED_1201, "维护轨（Minecraft 1.20.1）", false,
						List.of(fabric1201, neoForge1201))));
	}

	public List<Track> tracks() {
		return tracks;
	}

	private static LoaderStatus preview(LoaderId loader, String id, String plugin) {
		return new LoaderStatus(loader, id, "26.3", 25, SupportStatus.PREVIEW, plugin,
				"PREVIEW_NEGOTIATION_REQUIRED", "26.3 已登记；通过预览扩展查询精确操作、类型与字段范围。环境探针不代表产品原生或客户端验收。");
	}

	/** @return whether this target requires the separately negotiated preview contract */
	public boolean requiresPreview(String generatorId) {
		return findGenerator(generatorId).map(value -> value.status() == SupportStatus.PREVIEW).orElse(false);
	}

	/**
	 * Returns a display selection while retaining every exact generator registration.
	 * @param displayTracks display groups whose generator IDs are already registered
	 * @return an immutable catalog with the same execution identities and support decisions
	 */
	public VersionTrackCatalog withTracks(List<Track> displayTracks) {
		return new VersionTrackCatalog(registrations(), displayTracks);
	}

	/** @return each registered exact generator once, including generators outside the display tracks */
	public List<Registration> registrations() {
		return List.copyOf(generators.values());
	}

	/**
	 * Resolves a persisted identity without consulting the display selection.
	 * @param generatorId the exact generator ID; null and unknown IDs are absent
	 * @return the registered support decision and execution profile, when known
	 */
	public Optional<Registration> findRegistration(String generatorId) {
		return Optional.ofNullable(generators.get(generatorId));
	}

	public Optional<LoaderStatus> findGenerator(String generatorId) {
		return findRegistration(generatorId).map(Registration::status);
	}

	public CapabilityDecision decision(String generatorId) {
		return findGenerator(generatorId).map(status -> new CapabilityDecision(status.generatorId(), status.status(),
				status.reasonCode(), status.notes())).orElseGet(() -> new CapabilityDecision(
				generatorId == null ? "" : generatorId, SupportStatus.UNAVAILABLE, "UNSUPPORTED_GENERATOR",
				"所选生成器不在版本轨道目录中。"));
	}

	public boolean firstPartyGenerator(String generatorId) {
		return findRegistration(generatorId).isPresent();
	}

	public boolean migratable(String sourceGeneratorId, String targetGeneratorId) {
		return firstPartyGenerator(sourceGeneratorId) && firstPartyGenerator(targetGeneratorId)
				&& decision(sourceGeneratorId).generatable() && decision(targetGeneratorId).generatable()
				&& !Objects.equals(sourceGeneratorId, targetGeneratorId)
				&& sameMinecraft(sourceGeneratorId, targetGeneratorId);
	}

	private boolean sameMinecraft(String sourceGeneratorId, String targetGeneratorId) {
		Optional<Registration> source = findRegistration(sourceGeneratorId);
		Optional<Registration> target = findRegistration(targetGeneratorId);
		return source.isPresent() && target.isPresent()
				&& source.get().profile().minecraftVersion().equals(target.get().profile().minecraftVersion())
				&& source.get().status().loader() != target.get().status().loader();
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
				loader.addProperty("javaRelease", status.javaRelease());
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
