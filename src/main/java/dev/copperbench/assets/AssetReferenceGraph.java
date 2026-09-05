package dev.copperbench.assets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
		Map<String, Integer> inbound = new HashMap<>();
		Map<String, Integer> outbound = new HashMap<>();
		for (AssetReference reference : references) {
			inbound.merge(reference.targetPath(), 1, Integer::sum);
			outbound.merge(reference.sourcePath(), 1, Integer::sum);
		}
		Map<String, List<AssetDiagnostic>> diagnosticsBySource = new HashMap<>();
		for (AssetDiagnostic diagnostic : diagnostics)
			diagnosticsBySource.computeIfAbsent(diagnostic.sourcePath(), ignored -> new ArrayList<>()).add(diagnostic);

		List<AssetHealthReport.Entry> entries = new ArrayList<>(assets.size());
		int ready = 0;
		int warnings = 0;
		int errors = 0;
		int unusedCount = 0;
		for (AssetDescriptor asset : assets) {
			List<AssetDiagnostic> assetDiagnostics = diagnosticsBySource.getOrDefault(asset.relativePath(), List.of());
			boolean usageAssessed = usageAssessed(asset);
			int inboundCount = inbound.getOrDefault(asset.relativePath(), 0);
			int outboundCount = outbound.getOrDefault(asset.relativePath(), 0);
			boolean unused = usageAssessed && inboundCount == 0;
			Set<String> issueCodes = new LinkedHashSet<>();
			assetDiagnostics.stream().map(AssetDiagnostic::code).sorted().forEach(issueCodes::add);
			boolean hasError = assetDiagnostics.stream()
					.anyMatch(diagnostic -> diagnostic.severity() == AssetDiagnostic.Severity.ERROR);
			boolean hasWarning = assetDiagnostics.stream()
					.anyMatch(diagnostic -> diagnostic.severity() == AssetDiagnostic.Severity.WARNING);
			AssetHealthReport.Status status = hasError ? AssetHealthReport.Status.ERROR
					: hasWarning ? AssetHealthReport.Status.WARNING : AssetHealthReport.Status.READY;
			switch (status) {
				case READY -> ready++;
				case WARNING -> warnings++;
				case ERROR -> errors++;
			}
			if (unused) unusedCount++;
			entries.add(new AssetHealthReport.Entry(asset.id(), asset.relativePath(), status, usageAssessed, unused,
					inboundCount, outboundCount, List.copyOf(issueCodes)));
		}
		entries.sort(Comparator.comparing(AssetHealthReport.Entry::relativePath));
		AssetHealthReport.Summary summary = new AssetHealthReport.Summary(assets.size(), ready, warnings, errors,
				unusedCount, countDiagnostic("MISSING_ASSET_REFERENCE"), countDiagnostic("INVALID_ASSET_DOCUMENT"),
				countDiagnostic("REFERENCE_PATH_ESCAPE"));
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
}
