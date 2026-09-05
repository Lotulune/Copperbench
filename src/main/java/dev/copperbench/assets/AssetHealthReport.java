/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.assets;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Core-owned health and usage summary derived from one immutable asset reference graph. */
public record AssetHealthReport(List<Entry> entries, Summary summary) {
	public AssetHealthReport {
		entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
		Objects.requireNonNull(summary, "summary");
	}

	public Optional<Entry> findById(String assetId) {
		return entries.stream().filter(entry -> entry.assetId().equals(assetId)).findFirst();
	}

	public enum Status {
		READY,
		WARNING,
		ERROR
	}

	public record Entry(String assetId, String relativePath, Status status, boolean usageAssessed, boolean unused,
			int inboundCount, int outboundCount, List<String> issueCodes) {
		public Entry {
			Objects.requireNonNull(assetId, "assetId");
			Objects.requireNonNull(relativePath, "relativePath");
			Objects.requireNonNull(status, "status");
			issueCodes = List.copyOf(Objects.requireNonNull(issueCodes, "issueCodes"));
			if (inboundCount < 0 || outboundCount < 0)
				throw new IllegalArgumentException("Asset usage counts must not be negative");
			if (unused && !usageAssessed)
				throw new IllegalArgumentException("Only usage-assessed assets can be marked unused");
		}
	}

	public record Summary(int totalAssets, int readyAssets, int warningAssets, int errorAssets, int unusedAssets,
			int missingReferences, int invalidDocuments, int pathEscapes) {
		public Summary {
			if (totalAssets < 0 || readyAssets < 0 || warningAssets < 0 || errorAssets < 0 || unusedAssets < 0
					|| missingReferences < 0 || invalidDocuments < 0 || pathEscapes < 0)
				throw new IllegalArgumentException("Asset health counts must not be negative");
			if (readyAssets + warningAssets + errorAssets != totalAssets)
				throw new IllegalArgumentException("Asset health status counts must equal totalAssets");
		}
	}
}
