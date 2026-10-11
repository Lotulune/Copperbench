package dev.copperbench.gradle;

import java.nio.file.Path;
import java.util.Map;

/** Shared, read-only cache configuration for workspace setup, tasks and diagnostics. */
public final class GradleCachePolicy {
    private GradleCachePolicy() {}

    /** Resolves the explicit JVM override, then caller environment, relative to the product process directory. */
    public static Path userHome(Map<String, String> environment, String systemOverride, Path productDefault) {
        String source = userHomeSource(environment, systemOverride);
        String configured = switch (source) {
            case "COPPERBENCH_GRADLE_USER_HOME", "GRADLE_USER_HOME" -> environment.get(source);
            case "copperbench.gradle.user.home" -> systemOverride;
            default -> null;
        };
        return (configured == null ? productDefault : Path.of(configured)).toAbsolutePath().normalize();
    }

    /** Identifies the source of the effective cache directory without inspecting or creating it. */
    public static String userHomeSource(Map<String, String> environment, String systemOverride) {
        if (present(systemOverride)) return "copperbench.gradle.user.home";
        if (present(environment.get("COPPERBENCH_GRADLE_USER_HOME"))) return "COPPERBENCH_GRADLE_USER_HOME";
        if (present(environment.get("GRADLE_USER_HOME"))) return "GRADLE_USER_HOME";
        return "product_default";
    }

    /** Controls reuse from the user's other caches and bundled distributions; the chosen cache remains usable. */
    public static boolean reuseExternalDistributions(Map<String, String> environment) {
        String value = environment.get("COPPERBENCH_GRADLE_REUSE_EXTERNAL");
        if (!present(value) || value.equals("true")) return true;
        if (value.equals("false")) return false;
        throw new IllegalArgumentException("COPPERBENCH_GRADLE_REUSE_EXTERNAL must be true or false");
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
