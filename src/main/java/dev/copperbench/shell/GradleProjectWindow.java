/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.shell;

import dev.copperbench.core.workspace.project.GradleProjectDetector;
import dev.copperbench.core.workspace.project.GradleProjectIdentity;
import dev.copperbench.core.workspace.project.GradleProjectSettings;
import dev.copperbench.core.workspace.project.GradleProjectWorkspaceSession;
import dev.copperbench.generator.project.GradleProjectTaskGateway;
import dev.copperbench.window.WindowsWindowChromeController;
import net.mcreator.ui.init.AppIcon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A desktop window for a Gradle project Copperbench did not create (ADR-0020). It hosts the same product shell as a
 * workspace window, without the MCreator workspace, its menus or the legacy plugin window.
 */
public final class GradleProjectWindow extends JFrame {

	private static final Logger LOG = LogManager.getLogger(GradleProjectWindow.class);

	private final Path root;
	private final GradleProjectIdentity identity;
	private final Consumer<GradleProjectWindow> closed;
	private final WindowsWindowChromeController windowChromeController;
	private CopperbenchProductShell shell;

	private GradleProjectWindow(Path root, GradleProjectIdentity identity, Consumer<GradleProjectWindow> closed) {
		this.root = root;
		this.identity = identity;
		this.closed = closed;
		setTitle(identity.name() + " - " + dev.copperbench.ProductIdentity.NAME);
		setIconImages(AppIcon.getAppIcons());
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		windowChromeController = WindowsWindowChromeController.prepare(this);
		Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		setMinimumSize(new Dimension(Math.min(900, screen.width), Math.min(600, screen.height)));
		setSize(Math.min(1440, screen.width), Math.min(900, screen.height));
		setLocationRelativeTo(null);
		addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(WindowEvent event) {
				closeWindow();
			}

			@Override public void windowOpened(WindowEvent event) {
				if (windowChromeController != null && windowChromeController.isUsingCustomFrame()) windowChromeController.install();
				shell.forceLoad();
			}
		});
	}

	/**
	 * Opens the project for this window. The folder is identified first; nothing in it is written unless it is a
	 * Gradle project, and then only {@code .copperbench/}.
	 *
	 * @param openWorkspace opens another workspace or project folder chosen in this window
	 * @param closed        called after the window released the project
	 * @throws GradleProjectDetector.NotAProjectException when the folder is not a Gradle project to adopt
	 */
	public static GradleProjectWindow open(Path folder, Consumer<File> openWorkspace, Consumer<GradleProjectWindow> closed)
			throws IOException {
		Path distribution = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		GradleProjectIdentity identity = GradleProjectDetector.inspect(folder);
		GradleProjectWorkspaceSession session = GradleProjectWorkspaceSession.open(folder, (store, root) ->
				new GradleProjectTaskGateway(store, ignored -> root, distribution, Clock.systemUTC(), UUID::randomUUID,
						profile -> GradleProjectTaskGateway.productProcesses(distribution, profile, () -> { },
								() -> GradleProjectSettings.read(root).environmentGradleHome())),
				Clock.systemUTC(), UUID::randomUUID);
		GradleProjectWindow window;
		try {
			window = new GradleProjectWindow(session.root(), identity, closed);
		} catch (RuntimeException failure) {
			try {
				session.close();
			} catch (IOException | RuntimeException cleanup) {
				failure.addSuppressed(cleanup);
			}
			throw failure;
		}
		try {
			window.shell = CopperbenchProductShell.openGradleProject(window, session, window::closeWindow, openWorkspace,
					window.windowChromeController);
		} catch (IOException | RuntimeException failure) {
			// The shell closed the session when it failed to start
			if (window.windowChromeController != null) window.windowChromeController.close();
			window.dispose();
			throw failure;
		}
		window.setContentPane(window.shell);
		return window;
	}

	public Path root() {
		return root;
	}

	/** What the project's files identified when the window opened it. */
	public GradleProjectIdentity identity() {
		return identity;
	}

	/** Closes after the shell confirmed unsaved drafts and running Agent work; returns whether it closed. */
	public boolean closeWindow() {
		if (shell == null) return true;
		if (!shell.confirmClose()) return false;
		try {
			shell.close();
		} catch (RuntimeException failure) {
			LOG.warn("Failed to close the Gradle project window cleanly", failure);
		} finally {
			shell = null;
		}
		if (windowChromeController != null) windowChromeController.close();
		try {
			dispose();
		} catch (RuntimeException ignored) {
		}
		closed.accept(this);
		return true;
	}
}
