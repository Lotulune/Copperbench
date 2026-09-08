/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.chromium;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WindowsDisplayAdapterProbe {

	private static final int DISPLAY_DEVICE_ATTACHED_TO_DESKTOP = 0x00000001;

	private WindowsDisplayAdapterProbe() {
	}

	static boolean requiresSoftwareRendering() {
		try {
			return requiresSoftwareRendering(activeAdapterNames());
		} catch (RuntimeException | UnsatisfiedLinkError error) {
			return false;
		}
	}

	static boolean requiresSoftwareRendering(List<String> activeAdapterNames) {
		if (activeAdapterNames.isEmpty())
			return false;
		boolean foundKnownSoftwareAdapter = false;
		for (String adapterName : activeAdapterNames) {
			String normalized = adapterName == null ? "" : adapterName.trim().toLowerCase(Locale.ROOT);
			if (normalized.equals("microsoft hyper-v video") || normalized.equals("microsoft basic display adapter")) {
				foundKnownSoftwareAdapter = true;
				continue;
			}
			return false;
		}
		return foundKnownSoftwareAdapter;
	}

	static List<String> activeAdapterNames() {
		List<String> adapters = new ArrayList<>();
		for (int index = 0; ; index++) {
			DisplayDevice device = new DisplayDevice();
			device.cb = device.size();
			device.write();
			if (!User32.INSTANCE.EnumDisplayDevicesW(null, index, device, 0))
				break;
			device.read();
			if ((device.stateFlags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP) == 0)
				continue;
			String name = Native.toString(device.deviceString);
			if (!name.isBlank())
				adapters.add(name);
		}
		return List.copyOf(adapters);
	}

	private interface User32 extends StdCallLibrary {
		User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.UNICODE_OPTIONS);

		boolean EnumDisplayDevicesW(String deviceName, int deviceIndex, DisplayDevice displayDevice, int flags);
	}

	@Structure.FieldOrder({ "cb", "deviceName", "deviceString", "stateFlags", "deviceId", "deviceKey" })
	public static class DisplayDevice extends Structure {
		public int cb;
		public char[] deviceName = new char[32];
		public char[] deviceString = new char[128];
		public int stateFlags;
		public char[] deviceId = new char[128];
		public char[] deviceKey = new char[128];
	}
}
