package dev.copperbench.assets;

import dev.copperbench.platform.RuntimePlatform;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Resolves an explicit setting first, then platform-standard Blockbench locations. */
public final class BlockbenchExecutableLocator {
	private BlockbenchExecutableLocator() {
	}

	public static Path locate() {
		return locate(RuntimePlatform.current(), System.getenv(),
				System.getProperty("copperbench.blockbench.executable", "").trim());
	}

	static Path locate(RuntimePlatform platform, Map<String, String> environment, String configured) {
		List<Path> candidates = new ArrayList<>();
		if (configured != null && !configured.isBlank()) candidates.add(Path.of(configured.trim()));
		switch (platform.operatingSystem()) {
			case WINDOWS -> {
				add(candidates, environment.get("LOCALAPPDATA"), "Programs/Blockbench/Blockbench.exe");
				add(candidates, environment.get("ProgramFiles"), "Blockbench/Blockbench.exe");
				add(candidates, environment.get("ProgramFiles(x86)"), "Blockbench/Blockbench.exe");
			}
			case LINUX -> {
				addPathCandidates(candidates, environment.get("PATH"), "blockbench");
				add(candidates, environment.get("HOME"), ".local/bin/blockbench");
				add(candidates, environment.get("HOME"), "Applications/Blockbench.AppImage");
				candidates.add(Path.of("/usr/local/bin/blockbench"));
				candidates.add(Path.of("/usr/bin/blockbench"));
			}
			case MAC -> {
				candidates.add(Path.of("/Applications/Blockbench.app/Contents/MacOS/Blockbench"));
				add(candidates, environment.get("HOME"), "Applications/Blockbench.app/Contents/MacOS/Blockbench");
			}
			default -> addPathCandidates(candidates, environment.get("PATH"), "blockbench");
		}
		return candidates.stream().map(path -> path.toAbsolutePath().normalize())
				.filter(path -> isRunnableCandidate(platform, path)).findFirst().orElse(null);
	}

	private static boolean isRunnableCandidate(RuntimePlatform platform, Path path) {
		if (!Files.isRegularFile(path)) return false;
		return platform.operatingSystem() == RuntimePlatform.OperatingSystem.WINDOWS || Files.isExecutable(path);
	}

	private static void addPathCandidates(List<Path> candidates, String pathValue, String executable) {
		if (pathValue == null || pathValue.isBlank()) return;
		for (String entry : pathValue.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
			if (!entry.isBlank()) candidates.add(Path.of(entry).resolve(executable));
		}
	}

	private static void add(List<Path> candidates, String root, String suffix) {
		if (root != null && !root.isBlank()) candidates.add(Path.of(root).resolve(suffix));
	}
}
