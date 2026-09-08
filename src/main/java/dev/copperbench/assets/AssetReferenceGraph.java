package dev.copperbench.assets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;

/** Deterministic snapshot of asset descriptors, reference edges and diagnostics. */
public record AssetReferenceGraph(List<AssetDescriptor> assets, List<AssetReference> references,
		List<AssetDiagnostic> diagnostics) {
	public AssetReferenceGraph {
		assets = List.copyOf(Objects.requireNonNull(assets, "assets"));
		references = List.copyOf(Objects.requireNonNull(references, "references"));
		diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
	}

	public List<AssetReference> outgoing(String sourcePath) {
		return references.stream().filter(reference -> reference.sourcePath().equals(sourcePath)).toList();
	}

	public List<AssetReference> incoming(String targetPath) {
		return references.stream().filter(reference -> reference.targetPath().equals(targetPath)).toList();
	}

	/** Derives stable health and reverse-usage metadata without rescanning the workspace. */
	public AssetHealthReport healthReport() {
		return healthReport(Map.of(), Set.of(), false);
	}

	/**
	 * Derives health using the shared workspace reference-index signals as a second usage layer.
	 * {@code workspaceUsageComplete} must be false whenever arbitrary custom code can hide resource usage.
	 */
	public AssetHealthReport healthReport(Map<String, Integer> workspaceResourceReferences,
			Set<String> workspaceTextSignals, boolean workspaceUsageComplete) {
		workspaceResourceReferences = Map.copyOf(Objects.requireNonNull(workspaceResourceReferences,
				"workspaceResourceReferences"));
		workspaceTextSignals = Set.copyOf(Objects.requireNonNull(workspaceTextSignals, "workspaceTextSignals"));
		Set<String> workspaceTextTokens = workspaceTextTokens(workspaceTextSignals);
		Map<String, Integer> inbound = new HashMap<>();
		Map<String, Integer> outbound = new HashMap<>();
		for (AssetReference reference : references) {
			inbound.merge(reference.targetPath(), 1, Integer::sum);
			outbound.merge(reference.sourcePath(), 1, Integer::sum);
		}
		Map<String, List<AssetDiagnostic>> diagnosticsBySource = new HashMap<>();
		for (AssetDiagnostic diagnostic : diagnostics)
			diagnosticsBySource.computeIfAbsent(diagnostic.sourcePath(), ignored -> new ArrayList<>()).add(diagnostic);
		Map<String, List<AssetDescriptor>> duplicateGroups = new HashMap<>();
		for (AssetDescriptor asset : assets)
			duplicateGroups.computeIfAbsent(asset.mediaType() + "\n" + asset.sha256(), ignored -> new ArrayList<>())
					.add(asset);
		duplicateGroups.entrySet().removeIf(entry -> entry.getValue().size() < 2);
		Map<String, List<String>> duplicatePaths = new HashMap<>();
		for (List<AssetDescriptor> group : duplicateGroups.values()) {
			List<String> paths = group.stream().map(AssetDescriptor::relativePath).sorted().toList();
			for (AssetDescriptor asset : group)
				duplicatePaths.put(asset.relativePath(), paths.stream()
						.filter(path -> !path.equals(asset.relativePath())).toList());
		}

		List<AssetHealthReport.Entry> entries = new ArrayList<>(assets.size());
		int ready = 0;
		int warnings = 0;
		int errors = 0;
		int unusedCount = 0;
		int safeUnusedCount = 0;
		int duplicateAssetCount = 0;
		for (AssetDescriptor asset : assets) {
			List<AssetDiagnostic> assetDiagnostics = diagnosticsBySource.getOrDefault(asset.relativePath(), List.of());
			boolean usageAssessed = usageAssessed(asset);
			int inboundCount = inbound.getOrDefault(asset.relativePath(), 0);
			int outboundCount = outbound.getOrDefault(asset.relativePath(), 0);
			boolean unused = usageAssessed && inboundCount == 0;
			int workspaceReferenceCount = workspaceReferenceCount(asset, workspaceResourceReferences, workspaceTextTokens);
			boolean cleanupAssessed = workspaceUsageComplete && cleanupAssessed(asset);
			List<String> duplicates = duplicatePaths.getOrDefault(asset.relativePath(), List.of());
			boolean duplicateContent = !duplicates.isEmpty();
			Set<String> issueCodes = new LinkedHashSet<>();
			assetDiagnostics.stream().map(AssetDiagnostic::code).sorted().forEach(issueCodes::add);
			if (duplicateContent) issueCodes.add("DUPLICATE_ASSET_CONTENT");
			boolean hasError = assetDiagnostics.stream()
					.anyMatch(diagnostic -> diagnostic.severity() == AssetDiagnostic.Severity.ERROR);
			boolean hasWarning = assetDiagnostics.stream()
					.anyMatch(diagnostic -> diagnostic.severity() == AssetDiagnostic.Severity.WARNING);
			boolean safeUnused = cleanupAssessed && unused && workspaceReferenceCount == 0 && !hasError;
			AssetHealthReport.Status status = hasError ? AssetHealthReport.Status.ERROR
					: hasWarning || duplicateContent ? AssetHealthReport.Status.WARNING : AssetHealthReport.Status.READY;
			switch (status) {
				case READY -> ready++;
				case WARNING -> warnings++;
				case ERROR -> errors++;
			}
			if (unused) unusedCount++;
			if (safeUnused) safeUnusedCount++;
			if (duplicateContent) duplicateAssetCount++;
			entries.add(new AssetHealthReport.Entry(asset.id(), asset.relativePath(), status, usageAssessed, unused,
					inboundCount, outboundCount, workspaceReferenceCount, cleanupAssessed, safeUnused, duplicateContent,
					duplicates, List.copyOf(issueCodes)));
		}
		entries.sort(Comparator.comparing(AssetHealthReport.Entry::relativePath));
		AssetHealthReport.Summary summary = new AssetHealthReport.Summary(assets.size(), ready, warnings, errors,
				unusedCount, safeUnusedCount, duplicateAssetCount, duplicateGroups.size(), countDiagnostic("MISSING_ASSET_REFERENCE"),
				countDiagnostic("INVALID_ASSET_DOCUMENT"), countDiagnostic("REFERENCE_PATH_ESCAPE"));
		return new AssetHealthReport(entries, summary);
	}

	private int countDiagnostic(String code) {
		return (int) diagnostics.stream().filter(diagnostic -> diagnostic.code().equals(code)).count();
	}

	private static boolean usageAssessed(AssetDescriptor asset) {
		if (asset.relativePath().toLowerCase(java.util.Locale.ROOT).endsWith(".bbmodel")) return false;
		return switch (asset.category()) {
			case MODEL, TEXTURE, ANIMATION, SOUND -> true;
			default -> false;
		};
	}

	private static boolean cleanupAssessed(AssetDescriptor asset) {
		if (!usageAssessed(asset)) return false;
		if (asset.category() != AssetCategory.MODEL && asset.category() != AssetCategory.TEXTURE) return false;
		String path = asset.relativePath().toLowerCase(Locale.ROOT);
		return path.startsWith("assets/") || path.startsWith("src/main/resources/assets/")
				|| path.startsWith("src/main/assets/");
	}

	private static int workspaceReferenceCount(AssetDescriptor asset, Map<String, Integer> resourceReferences,
			Set<String> textTokens) {
		int count = 0;
		for (String alias : resourceAliases(asset)) count += resourceReferences.getOrDefault(alias, 0);
		String stem = assetStem(asset.relativePath());
		if (!stem.isBlank() && (textTokens.contains(stem) || textTokens.contains(normalizeSignal(stem)))) count++;
		return count;
	}

	private static Set<String> workspaceTextTokens(Set<String> signals) {
		LinkedHashSet<String> tokens = new LinkedHashSet<>();
		for (String raw : signals) {
			if (raw == null || raw.isBlank()) continue;
			String value = raw.toLowerCase(Locale.ROOT).trim();
			tokens.add(value);
			int separator = Math.max(value.lastIndexOf('/'), value.lastIndexOf(':'));
			if (separator >= 0 && separator < value.length() - 1) tokens.add(value.substring(separator + 1));
			String normalized = normalizeSignal(value);
			if (!normalized.isBlank()) tokens.add(normalized);
		}
		return Set.copyOf(tokens);
	}

	private static Set<String> resourceAliases(AssetDescriptor asset) {
		String path = asset.relativePath().toLowerCase(Locale.ROOT);
		int assetsIndex = path.indexOf("assets/");
		if (assetsIndex < 0) return Set.of();
		String tail = path.substring(assetsIndex + "assets/".length());
		int namespaceEnd = tail.indexOf('/');
		if (namespaceEnd <= 0) return Set.of();
		String namespace = tail.substring(0, namespaceEnd);
		String resourcePath = tail.substring(namespaceEnd + 1);
		for (String prefix : List.of("textures/", "models/", "animations/", "sounds/")) {
			if (!resourcePath.startsWith(prefix)) continue;
			String value = stripAssetExtension(resourcePath.substring(prefix.length()));
			if (value.isBlank()) return Set.of();
			LinkedHashSet<String> aliases = new LinkedHashSet<>();
			aliases.add(namespace + ":" + value);
			aliases.add(namespace + ":" + prefix.substring(0, prefix.length() - 1) + "/" + value);
			return Set.copyOf(aliases);
		}
		return Set.of();
	}

	private static String stripAssetExtension(String value) {
		String lower = value.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".animation.json")) return value.substring(0, value.length() - ".animation.json".length());
		int dot = value.lastIndexOf('.');
		return dot < 0 ? value : value.substring(0, dot);
	}

	private static String assetStem(String relativePath) {
		String file = relativePath.replace('\\', '/');
		int slash = file.lastIndexOf('/');
		if (slash >= 0) file = file.substring(slash + 1);
		return stripAssetExtension(file).toLowerCase(Locale.ROOT);
	}

	private static String normalizeSignal(String value) {
		return value.replaceAll("[^a-z0-9]", "");
	}
}
