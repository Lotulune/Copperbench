package dev.copperbench.assets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Resolves an explicit setting first, then standard Windows Blockbench install locations. */
public final class BlockbenchExecutableLocator {
	private BlockbenchExecutableLocator() {
	}

	public static Path locate() {
		List<Path> candidates = new ArrayList<>();
		String configured = System.getProperty("copperbench.blockbench.executable", "").trim();
		if (!configured.isEmpty()) candidates.add(Path.of(configured));
		add(candidates, System.getenv("LOCALAPPDATA"), "Programs/Blockbench/Blockbench.exe");
		add(candidates, System.getenv("ProgramFiles"), "Blockbench/Blockbench.exe");
		add(candidates, System.getenv("ProgramFiles(x86)"), "Blockbench/Blockbench.exe");
		return candidates.stream().map(path -> path.toAbsolutePath().normalize()).filter(Files::isRegularFile)
				.findFirst().orElse(null);
	}

	private static void add(List<Path> candidates, String root, String suffix) {
		if (root != null && !root.isBlank()) candidates.add(Path.of(root).resolve(suffix));
	}
}
