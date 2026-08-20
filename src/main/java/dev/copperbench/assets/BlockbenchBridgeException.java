package dev.copperbench.assets;

/** Stable failure returned by the path-scoped Blockbench process boundary. */
public final class BlockbenchBridgeException extends RuntimeException {
	private final String code;

	public BlockbenchBridgeException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
