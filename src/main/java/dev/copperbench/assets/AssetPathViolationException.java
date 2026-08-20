package dev.copperbench.assets;

/** Raised when an asset task attempts to access outside its authorized workspace root. */
public final class AssetPathViolationException extends IllegalArgumentException {
	public AssetPathViolationException(String message) {
		super(message);
	}
}
