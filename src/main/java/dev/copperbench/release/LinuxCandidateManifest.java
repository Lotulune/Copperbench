/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.release;

import com.google.gson.JsonObject;
import dev.copperbench.ProductIdentity;
import dev.copperbench.platform.RuntimePlatform;

/** Stage 15-only manifest. Its status deliberately prevents a premature Linux support claim. */
public final class LinuxCandidateManifest {
	private LinuxCandidateManifest() {
	}

	public static JsonObject development() {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", "1.0");
		root.addProperty("kind", "stage15-linux-candidate");
		root.addProperty("status", "development-not-certified");
		root.addProperty("formalSupportClaim", false);
		root.addProperty("productId", ProductIdentity.ID);
		root.addProperty("productVersion", ProductIdentity.VERSION);
		JsonObject platform = new JsonObject();
		platform.addProperty("os", "linux");
		platform.addProperty("arch", LinuxSupportTarget.ARCHITECTURE);
		platform.addProperty("certificationBaseline", LinuxSupportTarget.DISTRIBUTION);
		platform.addProperty("primaryDesktopSession", LinuxSupportTarget.PRIMARY_DESKTOP_SESSION);
		platform.addProperty("compatibilityDesktopSession", LinuxSupportTarget.COMPATIBILITY_DESKTOP_SESSION);
		root.add("platform", platform);
		root.add("packaging", LinuxDistributionLayout.toJson());
		root.add("candidateInventory", DevelopmentSbom.toInstalledCandidateJson(RuntimePlatform.detect("Linux", "x86_64")));
		JsonObject prerequisites = new JsonObject();
		prerequisites.addProperty("systemJavaRequired", false);
		prerequisites.addProperty("systemGradleRequired", false);
		prerequisites.addProperty("systemGitRequiredForBaseline", false);
		root.add("prerequisites", prerequisites);
		return root;
	}
}
