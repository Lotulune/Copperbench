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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Test-only source launcher used by the Stage 15 Xvfb compatibility smoke.
 *
 * <p>This is deliberately not packaged as a Copperbench product command. It uses the package-private
 * approval seam that the JUnit suite already exercises so the Xvfb smoke can prepare a deterministic
 * workspace without pretending that synthetic X11 key events are user approval. The actual packaged
 * graphical process is launched separately by verify-stage15-linux-x11-ci-smoke.sh.</p>
 */
public final class Stage15GraphicalWorkspaceFixture {

	private Stage15GraphicalWorkspaceFixture() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 1)
			throw new IllegalArgumentException("Usage: Stage15GraphicalWorkspaceFixture <workspace-folder>");

		Path workspaceFolder = Path.of(args[0]).toAbsolutePath().normalize();
		initializePackagedRuntime();

		StringWriter payload = new StringWriter();
		int exitCode = BootstrapProductLauncher.run(new String[] {
				"create-workspace",
				"--generator-id", "resourcepack-1.21.1",
				"--mod-name", "Stage15 Graphical Smoke",
				"--mod-id", "stage15_graphical_smoke",
				"--workspace-folder", workspaceFolder.toString(),
				"--version", "1.0.0"
		}, new PrintWriter(payload, true), new WorkspaceCreationService(), request -> true);

		System.out.println(payload.toString().trim());
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
