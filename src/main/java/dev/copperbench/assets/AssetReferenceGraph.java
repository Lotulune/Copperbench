package dev.copperbench.assets;

import java.util.List;
import java.util.Objects;

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
}
