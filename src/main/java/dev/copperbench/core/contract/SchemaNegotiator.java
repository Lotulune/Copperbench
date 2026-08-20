package dev.copperbench.core.contract;

import dev.copperbench.core.contract.UiCore.Diagnostic;
import dev.copperbench.core.contract.UiCore.Handshake;
import dev.copperbench.core.contract.UiCore.HandshakeResult;

import java.util.Comparator;
import java.util.List;

/** Selects the highest mutually supported UI-Core schema before bridge activation. */
public final class SchemaNegotiator {

	private final List<String> supportedVersions;

	public SchemaNegotiator(List<String> supportedVersions) {
		this.supportedVersions = supportedVersions.stream().distinct().sorted(versionComparator().reversed()).toList();
		if (this.supportedVersions.isEmpty())
			throw new IllegalArgumentException("At least one UI-Core schema version must be supported");
	}

	public HandshakeResult negotiate(Handshake request) {
		String selected = request.supportedSchemaVersions().stream().filter(supportedVersions::contains)
				.max(versionComparator()).orElse(null);
		if (selected != null)
			return new HandshakeResult("handshake_result", request.requestId(), "compatible", selected,
					supportedVersions, List.of());
		Diagnostic diagnostic = Diagnostic.error("UI_CORE_SCHEMA_INCOMPATIBLE",
				"diagnostic.ui_core_schema_incompatible",
				"The UI and Java Core do not support a common schema version.", null, null);
		return new HandshakeResult("handshake_result", request.requestId(), "incompatible", null,
				supportedVersions, List.of(diagnostic));
	}

	private static Comparator<String> versionComparator() {
		return Comparator.comparingInt(SchemaNegotiator::major).thenComparingInt(SchemaNegotiator::minor);
	}

	private static int major(String version) {
		return component(version, 0);
	}

	private static int minor(String version) {
		return component(version, 1);
	}

	private static int component(String version, int index) {
		String[] components = version.split("\\.", -1);
		if (components.length != 2)
			throw new IllegalArgumentException("Schema version must use major.minor format: " + version);
		return Integer.parseInt(components[index]);
	}
}
