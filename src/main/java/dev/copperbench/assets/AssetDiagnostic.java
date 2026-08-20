package dev.copperbench.assets;

import java.util.Objects;

/** Stable diagnostic emitted while resolving asset references. */
public record AssetDiagnostic(String code, Severity severity, String sourcePath, String targetPath, String message) {
	public AssetDiagnostic {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(sourcePath, "sourcePath");
		Objects.requireNonNull(message, "message");
	}

	public enum Severity { ERROR, WARNING, INFO }
}
