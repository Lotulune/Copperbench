/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.platform;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Platform-specific global user directories. Workspace-local .copperbench metadata is intentionally unrelated. */
public record UserDataPaths(Path data, Path config, Path cache, Path state, Path runtime) {
    private static final String APPLICATION_DIRECTORY = "copperbench";

    public UserDataPaths {
        data = normalize(data);
        config = normalize(config);
        cache = normalize(cache);
        state = normalize(state);
        runtime = normalize(runtime);
    }

    public static UserDataPaths current() {
        String home = System.getProperty("user.home", ".");
        return resolve(RuntimePlatform.current(), System.getenv(), Path.of(home));
    }

    public static UserDataPaths resolve(RuntimePlatform platform, Map<String, String> environment, Path userHome) {
        Objects.requireNonNull(platform);
        Objects.requireNonNull(environment);
        Path home = normalize(Objects.requireNonNull(userHome));

        Path override = homeOverride(environment);
        if (override != null) return new UserDataPaths(override, override, override, override, override);

        if (platform.operatingSystem() != RuntimePlatform.OperatingSystem.LINUX) {
            Path legacy = home.resolve(".copperbench");
            return new UserDataPaths(legacy, legacy, legacy, legacy, legacy);
        }

        Path data = xdg(environment, "XDG_DATA_HOME", home.resolve(".local/share")).resolve(APPLICATION_DIRECTORY);
        Path config = xdg(environment, "XDG_CONFIG_HOME", home.resolve(".config")).resolve(APPLICATION_DIRECTORY);
        Path cache = xdg(environment, "XDG_CACHE_HOME", home.resolve(".cache")).resolve(APPLICATION_DIRECTORY);
        Path state = xdg(environment, "XDG_STATE_HOME", home.resolve(".local/state")).resolve(APPLICATION_DIRECTORY);
        Path runtimeBase = absoluteEnvironmentPath(environment, "XDG_RUNTIME_DIR");
        Path runtime = runtimeBase == null ? state.resolve("run") : runtimeBase.resolve(APPLICATION_DIRECTORY);
        return new UserDataPaths(data, config, cache, state, runtime);
    }

    private static Path xdg(Map<String, String> environment, String key, Path fallback) {
        Path value = absoluteEnvironmentPath(environment, key);
        return value == null ? normalize(fallback) : value;
    }

    static Path homeOverride(Map<String, String> environment) {
        Path override = absoluteEnvironmentPath(environment, "COPPERBENCH_HOME");
        return override != null ? override : absoluteEnvironmentPath(environment, "MCREATOR_HOME");
    }

    private static Path absoluteEnvironmentPath(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) return null;
        try {
            Path path = Path.of(value.trim());
            return path.isAbsolute() ? normalize(path) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
