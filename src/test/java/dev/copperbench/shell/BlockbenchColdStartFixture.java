package dev.copperbench.shell;

import dev.copperbench.testing.McreatorTestRuntime;
import dev.copperbench.window.WindowsWindowChromeController;
import net.mcreator.io.UserFolderManager;
import net.mcreator.ui.chromium.CefUtils;
import net.mcreator.util.TerribleModuleHacks;
import net.mcreator.workspace.Workspace;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real product host and installed Blockbench, opened on a caller-prepared isolated workspace copy. */
public final class BlockbenchColdStartFixture {
	public static void main(String[] args) throws Exception {
		Path document = Path.of(args[0]).toRealPath();
		Path evidence = Path.of(args[1]).toAbsolutePath();
		Files.createDirectories(evidence);
		UserFolderManager.createUserFolderIfNotExists();
		TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
		TerribleModuleHacks.openMCreatorRequirements();
		McreatorTestRuntime.ensureInitialized();
		UiLocalePreferences.save("zh");
		Workspace workspace = Workspace.readFromFS(document.toFile(), null);
		SwingUtilities.invokeAndWait(() -> {
			try {
				JFrame frame = new JFrame("Copperbench Blockbench Cold Start Acceptance");
				frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
				var chrome = WindowsWindowChromeController.prepare(frame);
				CopperbenchProductShell[] shell = new CopperbenchProductShell[1];
				Runnable close = () -> {
					if (!shell[0].confirmClose()) return;
					shell[0].close();
					workspace.close();
					chrome.close();
					frame.dispose();
					Thread.ofPlatform().name("cold-start-fixture-close").start(() -> {
						try {
							CefUtils.close();
							Files.writeString(evidence.resolve("normal-exit.txt"), "closed");
							System.exit(0);
						} catch (Exception exception) { exception.printStackTrace(); System.exit(1); }
					});
				};
				shell[0] = CopperbenchProductShell.open(frame, workspace, close, () -> {}, file -> {}, chrome);
				frame.addWindowListener(new WindowAdapter() {
					@Override public void windowClosing(WindowEvent event) { close.run(); }
				});
				frame.setContentPane(shell[0]);
				frame.setSize(1450, 960);
				frame.setLocationRelativeTo(null);
				frame.setVisible(true);
				chrome.install();
				shell[0].forceLoad();
			} catch (Exception exception) { throw new RuntimeException(exception); }
		});
	}
}
