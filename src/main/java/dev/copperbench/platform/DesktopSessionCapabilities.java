/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.awt.GraphicsEnvironment;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Read-only host desktop/session facts for task, MCP, and diagnostics surfaces. */
public record DesktopSessionCapabilities(
		RuntimePlatform platform, Session session, boolean waylandDisplayPresent, boolean x11DisplayPresent) {

	public enum Session { WAYLAND, X11, HEADLESS, UNKNOWN, NATIVE }

	public static DesktopSessionCapabilities current() {
		return detect(RuntimePlatform.current(), System.getenv(), GraphicsEnvironment.isHeadless());
	}

	static DesktopSessionCapabilities detect(RuntimePlatform platform, Map<String, String> environment, boolean headless) {
		Objects.requireNonNull(platform);
		Objects.requireNonNull(environment);
		if (platform.operatingSystem() != RuntimePlatform.OperatingSystem.LINUX)
			return new DesktopSessionCapabilities(platform, Session.NATIVE, false, false);
		String waylandDisplay = environment.get("WAYLAND_DISPLAY");
		String x11Display = environment.get("DISPLAY");
		boolean wayland = waylandDisplay != null && !waylandDisplay.isBlank();
		boolean x11 = x11Display != null && !x11Display.isBlank();
		if (headless) return new DesktopSessionCapabilities(platform, Session.HEADLESS, wayland, x11);
		String declared = environment.get("XDG_SESSION_TYPE");
		if (declared != null) {
			String normalized = declared.trim().toLowerCase(Locale.ROOT);
			if ("wayland".equals(normalized)) return new DesktopSessionCapabilities(platform, Session.WAYLAND, wayland, x11);
			if ("x11".equals(normalized)) return new DesktopSessionCapabilities(platform, Session.X11, wayland, x11);
		}
		if (wayland) return new DesktopSessionCapabilities(platform, Session.WAYLAND, true, x11);
		if (x11) return new DesktopSessionCapabilities(platform, Session.X11, false, true);
		return new DesktopSessionCapabilities(platform, Session.UNKNOWN, false, false);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("os", platform.operatingSystem().name().toLowerCase(Locale.ROOT));
		json.addProperty("arch", platform.architecture().name().toLowerCase(Locale.ROOT));
		JsonObject desktop = new JsonObject();
		desktop.addProperty("sessionType", session.name().toLowerCase(Locale.ROOT));
		desktop.addProperty("waylandDisplayPresent", waylandDisplayPresent);
		desktop.addProperty("x11DisplayPresent", x11DisplayPresent);
		desktop.addProperty("certificationRole", switch (session) {
			case WAYLAND -> "stage15-primary-target";
			case X11 -> "stage15-compatibility-target";
			case HEADLESS -> "not-graphical";
			case UNKNOWN -> "unverified";
			case NATIVE -> "not-applicable";
		});
		JsonArray limitations = new JsonArray();
		if (session == Session.X11) limitations.add("X11 requires compatibility evidence separate from the primary Wayland path.");
		if (session == Session.HEADLESS) limitations.add("Graphical JCEF and runClient cannot be certified in a headless session.");
		if (session == Session.UNKNOWN) limitations.add("Linux desktop backend could not be classified as Wayland or X11.");
		desktop.add("knownLimitations", limitations);
		if (platform.operatingSystem() == RuntimePlatform.OperatingSystem.LINUX)
			desktop.addProperty("stage15CertificationPending", true);
		json.add("desktop", desktop);
		return json;
	}
}
