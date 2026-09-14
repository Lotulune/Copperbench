package dev.copperbench.assets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcreator.io.UserFolderManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

/** User-selected editor location, separate from workspace files and Agent configuration. */
public final class BlockbenchConfiguration {
	private final Path file;
	private final BlockbenchInstallationDetector detector;
	public BlockbenchConfiguration(Path file) { this(file, new BlockbenchInstallationDetector()); }
	BlockbenchConfiguration(Path file, BlockbenchInstallationDetector detector) { this.file = file; this.detector = detector; }
	public static BlockbenchConfiguration productDefault() {
		return new BlockbenchConfiguration(UserFolderManager.getFileFromConfigFolder("blockbench.json").toPath());
	}
	public Path executable() {
		try {
			if (!Files.isRegularFile(file) || Files.size(file) > 8192) return null;
			String value = JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("executable").getAsString();
			Path path = Path.of(value);
			return path.isAbsolute() ? path.normalize() : null;
		} catch (IOException | RuntimeException exception) { return null; }
	}
	public boolean onboardingDismissed() {
		try { JsonObject value = readConfiguration(); return value.has("onboardingDismissed") && value.get("onboardingDismissed").getAsBoolean(); }
		catch (RuntimeException exception) { return false; }
	}
	public void dismissOnboarding() {
		JsonObject value = readConfiguration(); value.addProperty("onboardingDismissed", true);
		writeConfiguration(value);
	}
	private JsonObject readConfiguration() {
		try { return Files.isRegularFile(file) && Files.size(file) <= 8192 ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject(); }
		catch (IOException | RuntimeException exception) { return new JsonObject(); }
	}
	private void writeConfiguration(JsonObject value) {
		try {
			Files.createDirectories(file.getParent());
			Path temporary = Files.createTempFile(file.getParent(), "blockbench-", ".pending");
			Files.writeString(temporary, value.toString(), StandardCharsets.UTF_8);
			try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
			catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
		} catch (IOException exception) { throw new BlockbenchBridgeException("BLOCKBENCH_CONFIG_FAILED", "Could not save Blockbench preferences"); }
	}
	public void select(Path executable) {
		String name = executable.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!(name.equals("blockbench.exe") || name.matches("blockbench_[0-9]+\\.[0-9]+\\.[0-9]+_portable\\.exe")
				|| name.equals("blockbench") || (name.startsWith("blockbench") && name.endsWith(".appimage"))))
			throw new BlockbenchBridgeException("BLOCKBENCH_EXECUTABLE_INVALID", "Select the Blockbench application executable");
		var installation = detector.detect(executable);
		if (installation.state() != BlockbenchInstallationDetector.State.READY
				&& installation.state() != BlockbenchInstallationDetector.State.READY_UNVERIFIED)
			throw new BlockbenchBridgeException("BLOCKBENCH_EXECUTABLE_INVALID", "The selected Blockbench executable or version could not be verified");
		try {
			JsonObject value = readConfiguration();
			value.addProperty("executable", executable.toRealPath().toString());
			writeConfiguration(value);
		} catch (IOException exception) { throw new BlockbenchBridgeException("BLOCKBENCH_CONFIG_FAILED", "Could not save the editor location"); }
	}
}
