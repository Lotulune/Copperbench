/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.shell;

import dev.copperbench.assets.AssetWorkspaceService;
import dev.copperbench.assets.BlockbenchExecutableLocator;
import dev.copperbench.assets.BlockbenchProcessService;
import dev.copperbench.bridge.JcefBlockbenchBridgeTransport;
import dev.copperbench.bridge.JcefCoreBridgeTransport;
import dev.copperbench.bridge.JcefDiagnosticsBridgeTransport;
import dev.copperbench.bridge.JcefLegacyPluginBridgeTransport;
import dev.copperbench.bridge.JcefMcpBridgeTransport;
import dev.copperbench.bridge.JcefWindowBridgeTransport;
import dev.copperbench.bridge.JcefWorkspaceOpenBridgeTransport;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession;
import dev.copperbench.core.contract.UiCore;
import dev.copperbench.diagnostics.DiagnosticBundleService;
import dev.copperbench.generator.LoaderRoutingWorkspaceTaskGateway;
import dev.copperbench.mcp.DesktopMcpRuntime;
import dev.copperbench.window.WindowsWindowChromeController;
import net.mcreator.ui.chromium.WebView;
import net.mcreator.io.UserFolderManager;
import net.mcreator.workspace.Workspace;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Hosts the offline React workbench and its scoped native transports. */
public final class CopperbenchProductShell extends JPanel implements AutoCloseable {

	public static final String UI_URL = "http://mcreator/copperbench/ui/index.html";

	private final MCreatorWorkspaceSession session;
	private final DesktopMcpRuntime mcpRuntime;
	private final RecoverableBrowserHost browserHost;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private CopperbenchProductShell(JFrame owner, Workspace workspace, Path distributionRoot, Runnable closeAction,
			Runnable openLegacyPluginWindow, Consumer<File> openWorkspaceAction,
			WindowsWindowChromeController windowChromeController)
			throws IOException {
		setLayout(new BorderLayout());
		setMinimumSize(new Dimension(500, 600));

		Clock clock = Clock.systemUTC();
		MCreatorWorkspaceSession createdSession = MCreatorWorkspaceSession.attach(workspace,
				store -> new LoaderRoutingWorkspaceTaskGateway(store,
				ignored -> workspace.getWorkspaceFolder().toPath(), distributionRoot, clock, UUID::randomUUID),
				clock, UUID::randomUUID);
		Path workspaceRoot = workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		DesktopMcpRuntime createdMcpRuntime = DesktopMcpRuntime.start(workspaceRoot, createdSession.workspaceId(),
				createdSession.mcpEntry(UiCore.PermissionProfile.WORKSPACE), clock);
		RecoverableBrowserHost createdBrowserHost;
		try {
			createdBrowserHost = new RecoverableBrowserHost(
					() -> createBrowser(createdSession, owner, closeAction, openLegacyPluginWindow,
							openWorkspaceAction, windowChromeController, workspaceRoot, createdMcpRuntime), closeAction);
		} catch (RuntimeException exception) {
			createdMcpRuntime.close();
			createdSession.close();
			throw exception;
		}
		this.session = createdSession;
		this.mcpRuntime = createdMcpRuntime;
		this.browserHost = createdBrowserHost;
		add(browserHost, BorderLayout.CENTER);
	}

	public static CopperbenchProductShell open(JFrame owner, Workspace workspace, Runnable closeAction,
			Runnable openLegacyPluginWindow, Consumer<File> openWorkspaceAction,
			WindowsWindowChromeController windowChromeController)
			throws IOException {
		Path distributionRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		return new CopperbenchProductShell(owner, workspace, distributionRoot, closeAction, openLegacyPluginWindow,
				openWorkspaceAction, windowChromeController);
	}

	public void forceLoad() {
		browserHost.forceLoad();
	}

	public UUID workspaceId() {
		return session.workspaceId();
	}

	@Override public void close() {
		if (!closed.compareAndSet(false, true))
			return;
		RuntimeException failure = null;
		try {
			browserHost.close();
		} catch (RuntimeException exception) {
			failure = exception;
		}
		try {
			mcpRuntime.close();
		} catch (RuntimeException exception) {
			if (failure == null) failure = exception;
			else failure.addSuppressed(exception);
		}
		try {
			session.close();
		} catch (RuntimeException exception) {
			if (failure == null) failure = exception;
			else failure.addSuppressed(exception);
		}
		if (failure != null) throw failure;
	}

	private static RecoverableBrowserHost.BrowserHandle createBrowser(MCreatorWorkspaceSession session, JFrame owner,
			Runnable closeAction, Runnable openLegacyPluginWindow, Consumer<File> openWorkspaceAction,
			WindowsWindowChromeController windowChromeController, Path workspaceRoot, DesktopMcpRuntime mcpRuntime) {
		WebView webView = new WebView(UI_URL);
		JcefCoreBridgeTransport coreTransport = null;
		JcefWindowBridgeTransport windowTransport = null;
		JcefLegacyPluginBridgeTransport legacyPluginTransport = null;
		JcefBlockbenchBridgeTransport blockbenchTransport = null;
		JcefWorkspaceOpenBridgeTransport workspaceOpenTransport = null;
		JcefDiagnosticsBridgeTransport diagnosticsTransport = null;
		JcefMcpBridgeTransport mcpTransport = null;
		try {
			coreTransport = webView.attachCoreBridge(session.workspaceId(), session.uiEntry());
			windowTransport = windowChromeController != null
					? JcefWindowBridgeTransport.attach(webView, owner, closeAction, windowChromeController::accept,
							windowChromeController::isUsingCustomFrame)
					: JcefWindowBridgeTransport.attach(webView, owner, closeAction);
			legacyPluginTransport = JcefLegacyPluginBridgeTransport.attach(webView, openLegacyPluginWindow);
			workspaceOpenTransport = openWorkspaceAction != null
					? JcefWorkspaceOpenBridgeTransport.attach(webView, openWorkspaceAction)
					: null;
			diagnosticsTransport = JcefDiagnosticsBridgeTransport.attach(webView,
					new DiagnosticBundleService(UserFolderManager.getFileFromUserFolder("diagnostics").toPath(),
							logRoot(), workspaceRoot, () -> diagnosticSnapshot(session), Clock.systemUTC()));
			mcpTransport = JcefMcpBridgeTransport.attach(webView, mcpRuntime);
			blockbenchTransport = JcefBlockbenchBridgeTransport.attach(webView,
					new BlockbenchProcessService(new AssetWorkspaceService(workspaceRoot),
							BlockbenchExecutableLocator.locate()));
			JcefCoreBridgeTransport attachedCore = coreTransport;
			JcefWindowBridgeTransport attachedWindow = windowTransport;
			JcefLegacyPluginBridgeTransport attachedLegacyPlugin = legacyPluginTransport;
			JcefBlockbenchBridgeTransport attachedBlockbench = blockbenchTransport;
			JcefWorkspaceOpenBridgeTransport attachedWorkspaceOpen = workspaceOpenTransport;
			JcefDiagnosticsBridgeTransport attachedDiagnostics = diagnosticsTransport;
			JcefMcpBridgeTransport attachedMcp = mcpTransport;
			return new RecoverableBrowserHost.BrowserHandle() {
				@Override public Component component() {
					return webView;
				}

				@Override public void addLoadListener(Runnable listener) {
					webView.addLoadListener(listener::run);
				}

				@Override public void addRendererTerminationListener(Consumer<String> listener) {
					webView.addRendererTerminationListener((status, errorCode, errorString) ->
							listener.accept(status.name() + " (" + errorCode + ": " + errorString + ")"));
				}

				@Override public void forceLoad() {
					webView.forceLoad();
				}

				@Override public void requestFocus() {
					webView.requestFocusInWindow();
				}

				@Override public void close() {
					attachedBlockbench.close();
					attachedMcp.close();
					attachedDiagnostics.close();
					if (attachedWorkspaceOpen != null)
						attachedWorkspaceOpen.close();
					attachedLegacyPlugin.close();
					attachedWindow.close();
					attachedCore.close();
					webView.close();
				}
			};
		} catch (RuntimeException exception) {
			if (blockbenchTransport != null)
				blockbenchTransport.close();
			if (mcpTransport != null)
				mcpTransport.close();
			if (diagnosticsTransport != null)
				diagnosticsTransport.close();
			if (workspaceOpenTransport != null)
				workspaceOpenTransport.close();
			if (legacyPluginTransport != null)
				legacyPluginTransport.close();
			if (windowTransport != null)
				windowTransport.close();
			if (coreTransport != null)
				coreTransport.close();
			webView.close();
			throw exception;
		}
	}

	private static Path logRoot() {
		String configured = System.getProperty("log_directory");
		return configured == null || configured.isBlank()
				? UserFolderManager.getFileFromUserFolder("logs").toPath()
				: Path.of(configured).toAbsolutePath().normalize().resolve("logs");
	}

	private static com.google.gson.JsonObject diagnosticSnapshot(MCreatorWorkspaceSession session) {
		var result = session.uiEntry().query(UiCore.Query.of(UUID.randomUUID(), session.workspaceId(),
				UiCore.Operation.GET_WORKBENCH, new com.google.gson.JsonObject()));
		com.google.gson.JsonObject snapshot = new com.google.gson.JsonObject();
		if (result.data() != null && result.data().isJsonObject()) {
			com.google.gson.JsonObject workbench = result.data().getAsJsonObject();
			snapshot.add("activeTasks", Optional.ofNullable(workbench.get("activeTasks"))
					.map(com.google.gson.JsonElement::deepCopy).orElseGet(com.google.gson.JsonArray::new));
			snapshot.add("elementCounts", Optional.ofNullable(workbench.get("elementCounts"))
					.map(com.google.gson.JsonElement::deepCopy).orElseGet(com.google.gson.JsonObject::new));
			if (workbench.has("workspace") && workbench.get("workspace").isJsonObject()) {
				com.google.gson.JsonObject workspace = workbench.getAsJsonObject("workspace");
				com.google.gson.JsonObject safeWorkspace = new com.google.gson.JsonObject();
				safeWorkspace.add("revision", workspace.get("revision").deepCopy());
				safeWorkspace.add("generator", workspace.getAsJsonObject("generator").deepCopy());
				snapshot.add("workspace", safeWorkspace);
			}
		}
		snapshot.add("diagnostics", UiCore.wireGson().toJsonTree(result.diagnostics()));
		return snapshot;
	}
}
