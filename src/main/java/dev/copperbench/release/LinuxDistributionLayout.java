/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Expected Stage 15 Linux x86_64 portable layout. Does not claim Linux certification. */
public final class LinuxDistributionLayout {
    public static final List<String> REQUIRED_ENTRIES = List.of(
            "copperbench.sh", "linux-candidate-manifest.json", "LICENSE.txt", "LICENSE-ADDITIONAL-TERMS.md",
            "jdk/bin/java", "jdk/bin/jcef_helper", "jdk21/bin/java", "jdk21/bin/javac",
            "lib/copperbench.jar", "plugins", "gradlew", "gradle/wrapper/gradle-wrapper.jar", "gradle-dists");

    private LinuxDistributionLayout() {
    }

    public static List<String> missing(Path root) {
        List<String> missing = new ArrayList<>();
        for (String entry : REQUIRED_ENTRIES) {
            if (!Files.exists(root.resolve(entry))) missing.add(entry);
        }
        return List.copyOf(missing);
    }

    public static JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("status", "stage15-development");
        json.addProperty("executable", "copperbench.sh");
        json.addProperty("os", "linux");
        json.addProperty("arch", LinuxSupportTarget.ARCHITECTURE);
        json.addProperty("certificationBaseline", LinuxSupportTarget.DISTRIBUTION);
        json.addProperty("portableFormat", LinuxSupportTarget.PORTABLE_FORMAT);
        json.addProperty("desktopPackageFormat", LinuxSupportTarget.DESKTOP_PACKAGE_FORMAT);
        JsonObject bundledJdk = new JsonObject();
        bundledJdk.addProperty("installed", "jdk");
        bundledJdk.addProperty("installedJava21", "jdk21");
        bundledJdk.addProperty("sourceTreeJava25", "jdk/jbr25_linux_64");
        bundledJdk.addProperty("sourceTreeJava21", "jdk/jdk21_linux_64");
        json.add("bundledJdk", bundledJdk);
        json.addProperty("jcefBundledWithJdk", true);
        JsonArray required = new JsonArray();
        REQUIRED_ENTRIES.forEach(required::add);
        json.add("requiredEntries", required);
        return json;
    }
}
