package dev.copperbench.headless;

import dev.copperbench.core.workspace.WorkspaceCreationService;
import dev.copperbench.network.ChinaMirrorService;
import net.mcreator.Launcher;
import net.mcreator.io.LoggingSystem;
import net.mcreator.io.UserFolderManager;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.util.MCreatorVersionNumber;
import net.mcreator.util.TerribleModuleHacks;
import net.mcreator.util.UTF8Forcer;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Test-only source launcher used by the Stage 15 Xvfb compatibility smoke.
 *
 * <p>This is deliberately not packaged as a Copperbench product command. It uses the package-private
 * approval seam that the JUnit suite already exercises so the Xvfb smokes can prepare deterministic
 * workspaces without pretending that synthetic X11 key events are user approval. The actual packaged
 * graphical/headless product processes are launched separately by the Stage 15 verification scripts.</p>
 */
public final class Stage15GraphicalWorkspaceFixture {

	private Stage15GraphicalWorkspaceFixture() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 1 && args.length != 4)
			throw new IllegalArgumentException("Usage: Stage15GraphicalWorkspaceFixture <workspace-folder> "
					+ "[<generator-id> <mod-name> <mod-id>]");

		Path workspaceFolder = Path.of(args[0]).toAbsolutePath().normalize();
		String generatorId = args.length == 4 ? args[1] : "resourcepack-1.21.1";
		String modName = args.length == 4 ? args[2] : "Stage15 Graphical Smoke";
		String modId = args.length == 4 ? args[3] : "stage15_graphical_smoke";
		PrintWriter machineOutput = new PrintWriter(new FileOutputStream(FileDescriptor.out), true);
		initializePackagedRuntime();

		int exitCode = BootstrapProductLauncher.run(new String[] {
				"create-workspace",
				"--generator-id", generatorId,
				"--mod-name", modName,
				"--mod-id", modId,
				"--workspace-folder", workspaceFolder.toString(),
				"--version", "1.0.0"
		}, machineOutput, new WorkspaceCreationService(), request -> true);

		if (exitCode != HeadlessExitCode.SUCCESS.code())
			System.exit(exitCode);

		// Keep the later packaged graphical launch deterministic: no first-run mirror dialog may obscure JCEF.
		ChinaMirrorService.rememberChoice(false);
	}

	private static void initializePackagedRuntime() throws Exception {
		System.setProperty("java.awt.headless", "true");
		System.setProperty("jdk.xml.maxElementDepth", "0");
		LoggingSystem.init();
		LoggingSystem.disableConsoleOutput();
		TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
		TerribleModuleHacks.openMCreatorRequirements();
		UTF8Forcer.forceGlobalUTF8();

		Properties configuration = new Properties();
		try (var stream = Launcher.class.getResourceAsStream("/mcreator.conf")) {
			if (stream == null)
				throw new IllegalStateException("Packaged mcreator.conf is missing");
			configuration.load(stream);
		}
		Launcher.version = new MCreatorVersionNumber(configuration);
		PreferencesManager.init();
		if (!UserFolderManager.createUserFolderIfNotExists())
			throw new IllegalStateException("Could not create isolated Copperbench user directories");
	}
}
