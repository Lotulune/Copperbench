/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.platform;

import java.util.Locale;

/** Small platform adapter for product/runtime layout decisions. */
public record RuntimePlatform(OperatingSystem operatingSystem, Architecture architecture) {

    public enum OperatingSystem { WINDOWS, LINUX, MAC, OTHER }
    public enum Architecture { X86_64, AARCH64, OTHER }

    public static RuntimePlatform current() {
        return detect(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    public static RuntimePlatform detect(String osName, String osArch) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String arch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
        OperatingSystem operatingSystem = os.contains("win") ? OperatingSystem.WINDOWS
                : os.contains("linux") ? OperatingSystem.LINUX
                : os.contains("mac") || os.contains("darwin") ? OperatingSystem.MAC : OperatingSystem.OTHER;
        Architecture architecture = arch.contains("amd64") || arch.contains("x86_64")
                ? Architecture.X86_64
                : arch.contains("aarch64") || arch.contains("arm64") ? Architecture.AARCH64 : Architecture.OTHER;
        return new RuntimePlatform(operatingSystem, architecture);
    }

    public boolean isLinuxX64() {
        return operatingSystem == OperatingSystem.LINUX && architecture == Architecture.X86_64;
    }

    public String javaExecutableName() {
        return operatingSystem == OperatingSystem.WINDOWS ? "java.exe" : "java";
    }

    /** Source-tree Java home used before a packaged flat jdk/jdk21 layout exists. */
    public String sourceJavaHome(int javaRelease) {
        if (javaRelease <= 21) {
            return switch (operatingSystem) {
                case WINDOWS -> "jdk/jdk21_win_64";
                case LINUX -> architecture == Architecture.X86_64 ? "jdk/jdk21_linux_64" : null;
                default -> null;
            };
        }
        return switch (operatingSystem) {
            case WINDOWS -> "jdk/jbr25_win_64";
            case LINUX -> architecture == Architecture.X86_64 ? "jdk/jbr25_linux_64" : null;
            case MAC -> architecture == Architecture.AARCH64
                    ? "jdk/jbr25_mac_aarch64/Contents/Home"
                    : architecture == Architecture.X86_64 ? "jdk/jbr25_mac_x64/Contents/Home" : null;
            default -> null;
        };
    }
}
