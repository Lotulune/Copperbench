package dev.copperbench.core.workspace.project;

import java.util.List;
import java.util.Objects;

/**
 * What can be read from a Gradle project Copperbench did not create, without running or changing it.
 *
 * @param name           display name from the mod metadata, the mod ID, or the folder
 * @param modId          mod ID, or {@code null} when the metadata does not state one
 * @param loader         {@code fabric}, {@code neoforge}, or {@code unknown}
 * @param minecraftVersion exact Minecraft version, or {@code unknown}
 * @param generatorId    supported version track whose pinned coordinates acceptance uses, or {@code null}
 * @param problems       reasons building and acceptance are unavailable; empty when the project is supported
 */
public record GradleProjectIdentity(String name, String modId, String loader, String minecraftVersion,
		String generatorId, List<Problem> problems) {

	public static final String UNKNOWN = "unknown";

	public GradleProjectIdentity {
		Objects.requireNonNull(name);
		Objects.requireNonNull(loader);
		Objects.requireNonNull(minecraftVersion);
		problems = List.copyOf(problems);
	}

	/** Whether building and acceptance can use a version track's pinned coordinates. */
	public boolean supported() {
		return generatorId != null && problems.isEmpty();
	}

	/** A stable code plus what is missing, for diagnostics and the project summary. */
	public record Problem(String code, String message) {
		public Problem {
			Objects.requireNonNull(code);
			Objects.requireNonNull(message);
		}
	}
}
