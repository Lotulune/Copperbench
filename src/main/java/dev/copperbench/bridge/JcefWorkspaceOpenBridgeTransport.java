/*
 * Copyright (C) 2026 Copperbench contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.copperbench.bridge;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcreator.ui.chromium.WebView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import javax.swing.*;
import java.io.Closeable;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static dev.copperbench.shell.UiLocalePreferences.text;

/**
 * Scoped transport for opening a newly created workspace in a new host window.
 * The browser side only supplies the workspace file path produced by the
 * create_workspace command; the host owns the actual open flow, so the UI never
 * re-implements workspace loading or domain validation.
 */
public final class JcefWorkspaceOpenBridgeTransport extends CefMessageRouterHandlerAdapter implements Closeable {

	private static final Logger LOG = LogManager.getLogger(JcefWorkspaceOpenBridgeTransport.class);
	private static final Gson JSON = new Gson();
	public static final String QUERY_PREFIX = "copperbench:workspace-open:";
	static final int MAX_REQUEST_CHARS = 128 * 1024;

	private final WebView webView;
	private final Consumer<File> openAction;
	private final Path workspaceRoot;
	private final UUID workspaceId;
	private final CefBrowser expectedBrowser;
	private final CefMessageRouter router;
	private final WebView.PageLoadListener loadStartListener;
	private final Runnable closeListener;
	private final AtomicBoolean closed = new AtomicBoolean(false);
	private final AtomicBoolean pickerOpen = new AtomicBoolean(false);
	private final Set<Long> pendingPickers = ConcurrentHashMap.newKeySet();

	private JcefWorkspaceOpenBridgeTransport(WebView webView, Consumer<File> openAction, Path workspaceRoot, UUID workspaceId) {
		this.webView = Objects.requireNonNull(webView, "webView must not be null");
		this.openAction = openAction;
		this.workspaceRoot = workspaceRoot == null ? null : workspaceRoot.toAbsolutePath().normalize();
		this.workspaceId = workspaceId;
		this.expectedBrowser = webView.getBrowser();
		this.router = webView.getRouter();
		this.loadStartListener = this::installHost;
		this.closeListener = this::close;
		this.router.addHandler(this, false);
		this.webView.addLoadStartListener(loadStartListener);
		this.webView.addCloseListener(closeListener);
		installHost();
	}

	public static JcefWorkspaceOpenBridgeTransport attach(WebView webView, Consumer<File> openAction) {
		return attach(webView, Objects.requireNonNull(openAction), null);
	}

	public static JcefWorkspaceOpenBridgeTransport attach(WebView webView, Consumer<File> openAction,
			Path workspaceRoot) {
		return attach(webView, openAction, workspaceRoot, null);
	}

	public static JcefWorkspaceOpenBridgeTransport attach(WebView webView, Consumer<File> openAction,
			Path workspaceRoot, UUID workspaceId) {
		return new JcefWorkspaceOpenBridgeTransport(webView, openAction, workspaceRoot, workspaceId);
	}

	private void installHost() {
		if (!closed.get())
			webView.executeScriptAsync(generateBootstrapScript(openAction != null, workspaceRoot != null, workspaceRoot != null && workspaceId != null));
	}

	@Override public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
			boolean persistent, CefQueryCallback callback) {
		if (request == null || !request.startsWith(QUERY_PREFIX))
			return false;
		if (closed.get()) {
			callback.failure(503, "Workspace open bridge is closed");
			return true;
		}
		if (browser != expectedBrowser || frame == null || !frame.isMain()
				|| !trustedUrl(browser.getURL()) || !trustedUrl(frame.getURL())) {
			callback.failure(403, "Workspace access is only available to the trusted workbench main frame");
			return true;
		}
		if (persistent) {
			callback.failure(400, "Workspace requests must not be persistent");
			return true;
		}

		File workspaceFile;
		try {
			JsonObject payload = parseRequest(request);
			if (payload.has("operation")) {
				String operation = requiredString(payload, "operation");
				if ("choose_workspace".equals(operation)) {
					if (payload.size() != 1) throw new IllegalArgumentException("Unknown workspace picker property");
					choosePath(false, null, queryId, callback);
					return true;
				}
				if ("choose_workspace_folder".equals(operation)) {
					choosePath(true, pickerInitialPath(payload), queryId, callback);
					return true;
				}
				if ("open_verified_export_folder".equals(operation) && payload.size() == 2) {
					String taskId = requiredString(payload, "taskId");
					UUID id = UUID.fromString(taskId);
					if (!id.toString().equalsIgnoreCase(taskId)) throw new IllegalArgumentException("Invalid export task UUID");
					openVerifiedExportFolder(id, callback);
					return true;
				}
				if (!"open_build_folder".equals(operation) || payload.size() != 1)
					throw new IllegalArgumentException("Unknown workspace open operation");
				openBuildFolder(callback);
				return true;
			}
			if (openAction == null) {
				callback.failure(503, "Workspace opening is not available");
				return true;
			}
			String workspaceFilePath = requiredString(payload, "workspaceFile");
			workspaceFile = new File(workspaceFilePath);
		} catch (RuntimeException exception) {
			callback.failure(400, "Invalid workspace open request: " + exception.getMessage());
			return true;
		}
		if (!workspaceFile.isFile() || !workspaceFile.getName().endsWith(".mcreator")) {
			callback.failure(404, "Workspace file does not exist: " + workspaceFile.getName());
			return true;
		}

		callback.success("{\"status\":\"accepted\"}");
		SwingUtilities.invokeLater(() -> {
			try {
				openAction.accept(workspaceFile);
			} catch (RuntimeException exception) {
				LOG.error("Failed to open newly created workspace {}", workspaceFile, exception);
			}
		});
		return true;
	}

	@Override public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
		if (browser == expectedBrowser) pendingPickers.remove(queryId);
	}

	public static boolean trustedUrl(String url) {
		try {
			URI uri = URI.create(url);
			return "http".equals(uri.getScheme()) && "mcreator".equals(uri.getHost())
					&& uri.getPort() == -1 && uri.getUserInfo() == null
					&& "/copperbench/ui/index.html".equals(uri.getRawPath());
		} catch (RuntimeException ignored) { return false; }
	}

	static JsonObject parseRequest(String request) {
		if (request == null || !request.startsWith(QUERY_PREFIX) || request.length() > MAX_REQUEST_CHARS)
			throw new IllegalArgumentException("Invalid workspace request size or prefix");
		var parsed = JsonParser.parseString(request.substring(QUERY_PREFIX.length()));
		if (!parsed.isJsonObject()) throw new IllegalArgumentException("Workspace request must be an object");
		return parsed.getAsJsonObject();
	}

	static String pickerInitialPath(JsonObject payload) {
		if (payload.keySet().stream().anyMatch(key -> !Set.of("operation", "initialPath").contains(key)))
			throw new IllegalArgumentException("Unknown workspace folder picker property");
		if (!payload.has("initialPath")) return null;
		String path = requiredString(payload, "initialPath");
		if (path.length() > 32768 || path.indexOf('\0') >= 0)
			throw new IllegalArgumentException("Invalid initial folder path");
		return path.isBlank() ? null : path;
	}

	private void choosePath(boolean folder, String initialPath, long queryId, CefQueryCallback callback) {
		if (!pickerOpen.compareAndSet(false, true)) {
			callback.failure(409, "A workspace picker is already open");
			return;
		}
		pendingPickers.add(queryId);
		SwingUtilities.invokeLater(() -> {
			try {
				if (closed.get() || !pendingPickers.contains(queryId)) return;
				SystemFileChooser chooser = new SystemFileChooser();
				chooser.setMultiSelectionEnabled(false);
				chooser.setAcceptAllFileFilterUsed(false);
				chooser.setDialogTitle(folder ? text("选择工作区文件夹", "Choose workspace folder")
						: text("打开工作区", "Open workspace"));
				if (folder) {
					chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
				} else {
					chooser.setFileSelectionMode(SystemFileChooser.FILES_ONLY);
					chooser.addChoosableFileFilter(new SystemFileChooser.FileNameExtensionFilter(
							text("Copperbench 工作区 (*.mcreator)", "Copperbench workspace (*.mcreator)"), "mcreator"));
				}
				File initialDirectory = pickerInitialDirectory(initialPath, workspaceRoot);
				if (initialDirectory != null) chooser.setCurrentDirectory(initialDirectory);
				var owner = SwingUtilities.getWindowAncestor(webView.getBrowser().getUIComponent());
				File selected = chooser.showOpenDialog(owner) == SystemFileChooser.APPROVE_OPTION
						? chooser.getSelectedFile() : null;
				JsonObject result = pickerResult(selected, folder);
				if (!closed.get() && pendingPickers.contains(queryId)) callback.success(result.toString());
			} catch (RuntimeException exception) {
				LOG.warn("Could not choose a workspace {}", folder ? "folder" : "file", exception);
				if (!closed.get() && pendingPickers.contains(queryId))
					callback.failure(400, exception.getMessage() == null ? "Could not open workspace picker" : exception.getMessage());
			} finally {
				pendingPickers.remove(queryId);
				pickerOpen.set(false);
			}
		});
	}

	static File pickerInitialDirectory(String initialPath, Path workspaceRoot) {
		File directory = initialPath != null ? Path.of(initialPath).toAbsolutePath().normalize().toFile()
				: workspaceRoot == null ? null : workspaceRoot.toFile();
		while (directory != null && !directory.isDirectory()) directory = directory.getParentFile();
		return directory;
	}

	static JsonObject pickerResult(File selected, boolean folder) {
		JsonObject result = new JsonObject();
		result.addProperty("cancelled", selected == null);
		if (selected != null) {
			if (folder ? !selected.isDirectory() : !selected.isFile() || !selected.getName().endsWith(".mcreator"))
				throw new IllegalArgumentException(folder ? "Select an existing folder" : "Select an existing .mcreator workspace file");
			result.addProperty(folder ? "path" : "workspaceFile", selected.toPath().toAbsolutePath().normalize().toString());
		}
		return result;
	}

	private void openBuildFolder(CefQueryCallback callback) {
		if (workspaceRoot == null) {
			callback.failure(503, "Build folder is not configured");
			return;
		}
		Path directory = workspaceRoot.resolve("build/libs");
		if (!Files.isDirectory(directory)) {
			callback.failure(404, "Build output directory does not exist. Build the workspace first.");
			return;
		}
		openFolder(directory, callback);
	}

	private void openVerifiedExportFolder(UUID taskId, CefQueryCallback callback) {
		if (workspaceRoot == null || workspaceId == null) {
			callback.failure(503, "Verified export folder is not configured"); return;
		}
		try {
			Path directory = dev.copperbench.generator.VerifiedArtifactExporter.exportDirectory(workspaceRoot, workspaceId, taskId);
			openFolder(directory, callback);
		} catch (java.io.IOException exception) {
			callback.failure(404, exception.getMessage());
		}
	}

	private void openFolder(Path directory, CefQueryCallback callback) {
		Thread.ofVirtual().name("Copperbench-Open-Output-Folder").start(() -> {
			try {
				if (!java.awt.Desktop.isDesktopSupported()
						|| !java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN))
					throw new IllegalStateException("Desktop folder opening is not supported");
				java.awt.Desktop.getDesktop().open(directory.toFile());
				callback.success("{\"status\":\"opened\"}");
			} catch (Exception exception) {
				LOG.warn("Failed to open output directory {}", directory, exception);
				callback.failure(500, "Could not open output directory");
			}
		});
	}

	public static String generateBootstrapScript() {
		return generateBootstrapScript(true, false, false);
	}

	private static String generateBootstrapScript(boolean available, boolean buildFolderAvailable, boolean verifiedExportFolderAvailable) {
		return """
				(function() {
				    window.__COPPERBENCH_WORKSPACE_OPEN_HOST__ = {
				        available: %s,
				        buildFolderAvailable: %s,
				        verifiedExportFolderAvailable: %s,
				        workspacePickerAvailable: true,
				        workspaceFolderPickerAvailable: true,
				        chooseWorkspace: function() {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') { reject(new Error('JCEF workspace picker is not available')); return; }
				                window.cefQuery({ request: %s + JSON.stringify({ operation: 'choose_workspace' }), persistent: false,
				                    onSuccess: function(result) { try { resolve(JSON.parse(result)); } catch (error) { reject(error); } },
				                    onFailure: function(code, message) { var error = new Error(message); error.code = code; reject(error); } });
				            });
				        },
				        chooseWorkspaceFolder: function(initialPath) {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') { reject(new Error('JCEF workspace folder picker is not available')); return; }
				                window.cefQuery({ request: %s + JSON.stringify({ operation: 'choose_workspace_folder', initialPath: initialPath }), persistent: false,
				                    onSuccess: function(result) { try { resolve(JSON.parse(result)); } catch (error) { reject(error); } },
				                    onFailure: function(code, message) { var error = new Error(message); error.code = code; reject(error); } });
				            });
				        },
				        openVerifiedExportFolder: function(taskId) {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') { reject(new Error('JCEF workspace open transport is not available')); return; }
				                window.cefQuery({ request: %s + JSON.stringify({ operation: 'open_verified_export_folder', taskId: taskId }), persistent: false,
				                    onSuccess: function() { resolve(); }, onFailure: function(code, message) { var error = new Error(message); error.code = code; reject(error); } });
				            });
				        },
				        openBuildFolder: function() {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') {
				                    reject(new Error('JCEF workspace open transport is not available'));
				                    return;
				                }
				                window.cefQuery({
				                    request: %s + JSON.stringify({ operation: 'open_build_folder' }),
				                    persistent: false,
				                    onSuccess: function() { resolve(); },
				                    onFailure: function(code, message) {
				                        var error = new Error(message);
				                        error.code = code;
				                        reject(error);
				                    }
				                });
				            });
				        },
				        open: function(workspaceFile) {
				            return new Promise(function(resolve, reject) {
				                if (typeof window.cefQuery !== 'function') {
				                    reject(new Error('JCEF workspace open transport is not available'));
				                    return;
				                }
				                window.cefQuery({
				                    request: %s + JSON.stringify({ workspaceFile: workspaceFile }),
				                    persistent: false,
				                    onSuccess: function() { resolve(); },
				                    onFailure: function(code, message) {
				                        reject(new Error('Workspace open failed [' + code + ']: ' + message));
				                    }
				                });
				            });
				        }
				    };
				})();
				""".formatted(available, buildFolderAvailable, verifiedExportFolderAvailable, JSON.toJson(QUERY_PREFIX),
				JSON.toJson(QUERY_PREFIX), JSON.toJson(QUERY_PREFIX), JSON.toJson(QUERY_PREFIX), JSON.toJson(QUERY_PREFIX));
	}

	private static String requiredString(JsonObject payload, String name) {
		if (!payload.has(name) || !payload.get(name).isJsonPrimitive() || !payload.getAsJsonPrimitive(name).isString())
			throw new IllegalArgumentException("Missing workspace open property: " + name);
		return payload.get(name).getAsString();
	}

	@Override public void close() {
		if (!closed.compareAndSet(false, true))
			return;
		webView.removeLoadStartListener(loadStartListener);
		webView.removeCloseListener(closeListener);
		pendingPickers.clear();
		try {
			router.removeHandler(this);
		} catch (Exception ignored) {
		}
	}

	public boolean isClosed() {
		return closed.get();
	}
}
