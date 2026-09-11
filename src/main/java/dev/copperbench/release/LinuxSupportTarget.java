/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

/** Frozen Stage 15 certification target. This is not yet a public support claim. */
public final class LinuxSupportTarget {
    public static final String DISTRIBUTION = "Ubuntu 24.04 LTS";
    public static final String DISTRIBUTION_ID = "ubuntu";
    public static final String MINIMUM_VERSION = "24.04";
    public static final String ARCHITECTURE = "x86_64";
    public static final String PRIMARY_DESKTOP_SESSION = "GNOME Wayland";
    public static final String COMPATIBILITY_DESKTOP_SESSION = "GNOME on Xorg";
    public static final String PORTABLE_FORMAT = "tar.gz";
    public static final String DESKTOP_PACKAGE_FORMAT = "deb";

    private LinuxSupportTarget() {
    }
}
