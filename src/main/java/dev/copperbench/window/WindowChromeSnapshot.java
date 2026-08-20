/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.window;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Validated CSS viewport regions reported by the React title bar. */
public record WindowChromeSnapshot(String schemaVersion, long sequence, String coordinateSpace,
		double devicePixelRatio, Viewport viewport, List<Region> regions) {

	public static final String SCHEMA_VERSION = "1.0";
	private static final Gson JSON = new Gson();
	private static final int MAX_REGIONS = 128;

	public WindowChromeSnapshot {
		if (!SCHEMA_VERSION.equals(schemaVersion))
			throw new IllegalArgumentException("Unsupported window chrome schema version");
		if (sequence < 0)
			throw new IllegalArgumentException("Window chrome sequence must not be negative");
		if (!"css_viewport".equals(coordinateSpace))
			throw new IllegalArgumentException("Unsupported window chrome coordinate space");
		if (!Double.isFinite(devicePixelRatio) || devicePixelRatio < 0.5 || devicePixelRatio > 8)
			throw new IllegalArgumentException("Invalid window chrome device pixel ratio");
		Objects.requireNonNull(viewport, "Window chrome viewport must not be null");
		regions = List.copyOf(Objects.requireNonNull(regions, "Window chrome regions must not be null"));
		if (regions.size() > MAX_REGIONS)
			throw new IllegalArgumentException("Too many window chrome regions");
		Set<String> ids = new HashSet<>();
		for (Region region : regions) {
			Objects.requireNonNull(region, "Window chrome region must not be null");
			if (!ids.add(region.id()))
				throw new IllegalArgumentException("Duplicate window chrome region id: " + region.id());
		}
	}

	public static WindowChromeSnapshot parse(String json) {
		try {
			WindowChromeSnapshot snapshot = JSON.fromJson(json, WindowChromeSnapshot.class);
			if (snapshot == null)
				throw new IllegalArgumentException("Window chrome snapshot must not be null");
			return snapshot;
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Malformed window chrome snapshot", exception);
		}
	}

	public Target targetAt(double cssX, double cssY) {
		for (Region region : regions) {
			if (region.kind() != Kind.CAPTION && region.bounds().contains(cssX, cssY))
				return region.kind().target;
		}
		for (Region region : regions) {
			if (region.kind() == Kind.CAPTION && region.bounds().contains(cssX, cssY))
				return Target.CAPTION;
		}
		return Target.CLIENT;
	}

	public record Viewport(double width, double height) {
		public Viewport {
			if (!positiveFinite(width) || !positiveFinite(height) || width > 100_000 || height > 100_000)
				throw new IllegalArgumentException("Invalid window chrome viewport");
		}
	}

	public record Region(String id, Kind kind, Bounds bounds) {
		public Region {
			if (id == null || id.isBlank() || id.length() > 128)
				throw new IllegalArgumentException("Invalid window chrome region id");
			Objects.requireNonNull(kind, "Window chrome region kind must not be null");
			Objects.requireNonNull(bounds, "Window chrome region bounds must not be null");
		}
	}

	public record Bounds(double x, double y, double width, double height) {
		public Bounds {
			if (!Double.isFinite(x) || !Double.isFinite(y) || !positiveFinite(width) || !positiveFinite(height)
					|| Math.abs(x) > 100_000 || Math.abs(y) > 100_000 || width > 100_000 || height > 100_000)
				throw new IllegalArgumentException("Invalid window chrome region bounds");
		}

		boolean contains(double pointX, double pointY) {
			return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
		}
	}

	public enum Kind {
		@SerializedName("caption") CAPTION(Target.CAPTION),
		@SerializedName("client") CLIENT(Target.CLIENT),
		@SerializedName("minimize") MINIMIZE(Target.MINIMIZE),
		@SerializedName("maximize") MAXIMIZE(Target.MAXIMIZE),
		@SerializedName("close") CLOSE(Target.CLOSE);

		private final Target target;

		Kind(Target target) {
			this.target = target;
		}
	}

	public enum Target {
		CLIENT, CAPTION, MINIMIZE, MAXIMIZE, CLOSE
	}

	private static boolean positiveFinite(double value) {
		return Double.isFinite(value) && value > 0;
	}
}
