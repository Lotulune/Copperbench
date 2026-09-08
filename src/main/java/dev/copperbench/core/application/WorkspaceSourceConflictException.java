package dev.copperbench.core.application;

/** Raised when a source file changed on disk after the caller's projected source snapshot. */
public final class WorkspaceSourceConflictException extends IllegalStateException {

	private final String fieldPath;
	private final String workspacePath;
	private final String expectedFingerprint;
	private final String actualFingerprint;

	public WorkspaceSourceConflictException(String fieldPath, String workspacePath, String expectedFingerprint,
			String actualFingerprint) {
		super("Source changed outside Copperbench before write: " + workspacePath + " (expected "
				+ expectedFingerprint + ", found " + actualFingerprint + ")");
		this.fieldPath = fieldPath;
		this.workspacePath = workspacePath;
		this.expectedFingerprint = expectedFingerprint;
		this.actualFingerprint = actualFingerprint;
	}

	public String fieldPath() { return fieldPath; }

	public String workspacePath() { return workspacePath; }

	public String expectedFingerprint() { return expectedFingerprint; }

	public String actualFingerprint() { return actualFingerprint; }
}
