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
import dev.copperbench.ProductIdentity;
import dev.copperbench.tracks.VersionTrackCatalog;

/**
 * Machine-readable Stage 8 release notes: support matrix, privacy defaults,
 * source pointers, and an honest G7 automation status. This is not a G7 pass.
 */
public final class ReleaseManifest {

	private ReleaseManifest() {
	}

	public static JsonObject official() {
		VersionTrackCatalog catalog = VersionTrackCatalog.official();
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", "1.0");
		JsonObject product = new JsonObject();
		product.addProperty("name", ProductIdentity.NAME);
		product.addProperty("version", ProductIdentity.VERSION);
		product.addProperty("id", ProductIdentity.ID);
		product.addProperty("publisher", ProductIdentity.PUBLISHER);
		product.addProperty("license", "GPL-3.0");
		product.addProperty("upstreamCore", ProductIdentity.UPSTREAM_NAME);
		root.add("product", product);

		JsonObject platform = new JsonObject();
		platform.addProperty("os", "windows");
		platform.addProperty("arch", "x64");
		platform.addProperty("minimumOs", SupportedPlatform.MINIMUM_OS);
		platform.addProperty("offlineFirst", true);
		root.add("platform", platform);

		JsonObject privacy = new JsonObject();
		privacy.addProperty("accountsRequired", false);
		privacy.addProperty("defaultTelemetry", false);
		privacy.addProperty("implicitNetworkServices", ProductIdentity.IMPLICIT_NETWORK_SERVICES_ENABLED);
		privacy.addProperty("cdnFrontend", false);
		root.add("privacy", privacy);
		root.add("packaging", WindowsDistributionLayout.toJson());
		root.add("bundledPlugins", BundledPluginInventory.toJson());
		root.add("featureCoverage", FeatureCoverageCatalog.toJson());
		root.add("developmentSbom", DevelopmentSbom.toJson());

		JsonObject focus = new JsonObject();
		focus.addProperty("stage", "github_public_fork");
		focus.addProperty("reason",
				"Public identity is Copperbench. Distribution is an unsigned GPL GitHub fork with no product website. Authenticode is out of first-release scope. G7 is not passed: Hyper-V guest GUI stay-alive is not claimed.");
		focus.add("deferred", new JsonArray());
		root.add("developmentFocus", focus);

		root.add("versionTracks", catalog.toProjection());

		JsonArray golden = new JsonArray();
		JsonArray generateReady = new JsonArray();
		for (var track : catalog.tracks()) {
			for (var loader : track.loaders()) {
				if ("TRACK_SUPPORTED".equals(loader.reasonCode()))
					golden.add(loader.generatorId());
				else if ("TRACK_GENERATE_READY".equals(loader.reasonCode()))
					generateReady.add(loader.generatorId());
			}
		}
		JsonObject claims = new JsonObject();
		claims.add("goldenCompileClaimed", golden);
		claims.add("generateReadyNotGolden", generateReady);
		root.add("claims", claims);
		root.add("elementCoverage", ElementCoverageCatalog.toJson());
		root.add("upstreamTools", UpstreamToolCatalog.toJson());

		JsonArray limits = new JsonArray();
		limits.add(limitation("WINDOWS_10_NOT_SUPPORTED",
				"Windows 10 is not a supported platform. First release targets Windows 11 x64 only."));
		limits.add(limitation("HYPERV_GUEST_GUI_START_NOT_CLAIMED",
				"The Windows 11 Hyper-V guest completed silent install, upgrade, and uninstall with the NIC disconnected. copperbench.exe did not remain running for 10 seconds, so guest GUI stay-alive is not claimed."));
		limits.add(limitation("CODE_SIGNING_UNSIGNED_GITHUB",
				"First public binaries are unsigned GitHub Releases. jsign 7.4 remains wired if Authenticode secrets are added later; SmartScreen may warn. This is policy, not a pending certificate."));
		limits.add(limitation("PUBLIC_DISTRIBUTION_GITHUB_ONLY",
				"Copperbench is the public product name. There is no product domain, store listing, or trademark campaign. Support is the public GitHub repository. Product ID dev.copperbench.studio is a reverse-DNS identifier, not a live website."));
		limits.add(limitation("RESOURCE_PACK_PREPARE_DOES_NOT_AUTO_LAUNCH",
				"prepare_resource_pack_client writes run/resourcepacks and options.txt and does not launch Minecraft. Fabric 1.21.1 runClient listed file/copper_ready_pack.zip in ResourceManager."));
		limits.add(limitation("THIRD_PARTY_PLUGIN_NOT_UNIVERSALLY_SUPPORTED",
				"Third-party plugins are classified A/B/C/X. Unsupported plugins are not claimed as supported."));
		limits.add(limitation("OFFLINE_BUILD_GRADLE_MODE_ONLY",
				"The cached-dependency build uses Gradle --offline after a cache-warm, not an OS-level network disconnect."));
		limits.add(limitation("OFFLINE_BUILD_ONLY_1211",
				"The official Stage 8 cached-dependency --offline claim remains Fabric and NeoForge 1.21.1. 26.x probes recorded their own cache-warm/--offline jars separately."));
		root.add("knownLimitations", limits);

		JsonObject source = new JsonObject();
		source.addProperty("license", "LICENSE.txt");
		source.addProperty("notices", "compliance/THIRD_PARTY_NOTICES.md");
		source.addProperty("changes", "CHANGES-FROM-UPSTREAM.md");
		source.addProperty("sourceDistribution", "compliance/SOURCE_DISTRIBUTION.md");
		source.addProperty("baselineLock", "compliance/baseline.lock.json");
		root.add("source", source);

		JsonObject g7 = new JsonObject();
		g7.addProperty("status", "in_progress");
		JsonArray automated = new JsonArray();
		automated.add("release_manifest");
		automated.add("privacy_defaults");
		automated.add("installer_default_keep_user_data");
		automated.add("source_distribution_files");
		automated.add("windows_export_recipe");
		automated.add("bundled_plugin_inventory");
		automated.add("installed_plugin_inventory");
		automated.add("windows11_silent_install_upgrade_uninstall");
		automated.add("windows11_hyperv_guest_install_upgrade_uninstall");
		automated.add("offline_cached_dependency_build");
		automated.add("hyperv_readiness_probe");
		automated.add("vmware_readiness_probe");
		automated.add("code_signing_readiness_probe");
		automated.add("jcef_snap_dpi_smoke");
		automated.add("resource_pack_1211_client_load");
		automated.add("feature_coverage_audit");
		automated.add("development_sbom");
		g7.add("automatedChecks", automated);
		g7.add("pendingMachineEvidence", new JsonArray());
		root.add("g7", g7);
		return root;
	}

	private static JsonObject limitation(String code, String message) {
		JsonObject json = new JsonObject();
		json.addProperty("code", code);
		json.addProperty("message", message);
		return json;
	}
}
