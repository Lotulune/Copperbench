package dev.copperbench.assets;

import java.util.Objects;

/** Stable diagnostic emitted while resolving asset references. */
public record AssetDiagnostic(String code, Severity severity, String sourcePath, String targetPath, String message,
		String sourcePointer) {
	public AssetDiagnostic(String code, Severity severity, String sourcePath, String targetPath, String message) {
		this(code, severity, sourcePath, targetPath, message, "");
	}
	public AssetDiagnostic {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(sourcePath, "sourcePath");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(sourcePointer, "sourcePointer");
		if (!sourcePointer.isEmpty() && !sourcePointer.startsWith("/")) throw new IllegalArgumentException("Invalid source pointer");
	}

	public enum Severity { ERROR, WARNING, INFO }
}
