package dev.copperbench.window;

import com.google.gson.JsonObject;
import dev.copperbench.bridge.JcefWindowBridgeTransport;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import dev.copperbench.shell.CopperbenchProductShell;
import net.mcreator.Launcher;
import net.mcreator.io.LoggingSystem;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.chromium.*;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.ThemeManager;
import net.mcreator.util.MCreatorVersionNumber;
import net.mcreator.util.TerribleModuleHacks;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Clock;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Real product React/JCEF/Win32 surface for manual mouse verification, with an in-memory workspace. */
public final class WindowChromeManualMain {
    public static void main(String[] args) throws Exception {
        LoggingSystem.init();
        TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
        TerribleModuleHacks.openMCreatorRequirements();
        Properties configuration = new Properties();
        configuration.load(Launcher.class.getResourceAsStream("/mcreator.conf"));
        Launcher.version = new MCreatorVersionNumber(configuration);
        PreferencesManager.init();
        PluginLoader.initInstance();
        ThemeManager.loadThemes();
        L10N.initTranslations();
        UUID id = UUID.randomUUID();
        JsonObject generator = new JsonObject();
        generator.addProperty("id", "fabric-1.21.1");
        generator.addProperty("displayName", "Fabric 1.21.1");
        generator.addProperty("state", "ready");
        RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
        store.register(new WorkspaceState(id, "Window interaction verification", "mod", 0, false,
                generator, new JsonObject(), List.of()));
        Clock clock = Clock.systemUTC();
        WorkspaceApplicationService service = new WorkspaceApplicationService(store,
                new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID);
        WorkspaceEntryAdapter adapter = new WorkspaceEntryAdapter(service,
                new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
        SwingUtilities.invokeAndWait(() -> {
            JFrame window = new JFrame("Copperbench - window interaction verification");
            WindowsWindowChromeController chrome = WindowsWindowChromeController.prepare(window);
            if (chrome == null) throw new IllegalStateException("Windows custom chrome is required");
            WebView webView = new WebView(CopperbenchProductShell.UI_URL);
            webView.attachCoreBridge(id, adapter);
            JcefWindowBridgeTransport transport = JcefWindowBridgeTransport.attach(webView, window,
                    window::dispose, chrome::accept, chrome::isUsingCustomFrame, chrome::pointerGesture);
            window.setContentPane(webView);
            window.setSize(1100, 760);
            window.setLocation(250, 140);
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            window.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) {
                    transport.close();
                    chrome.close();
                    webView.close();
                    new Thread(() -> { CefUtils.close(); System.exit(0); }).start();
                }
            });
            window.setVisible(true);
            if (!chrome.install()) throw new IllegalStateException("Window chrome failed to install");
            webView.forceLoad();
        });
    }
}
