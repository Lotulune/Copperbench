package dev.copperbench.agent;

import com.google.gson.JsonObject;
import dev.copperbench.bridge.*;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import dev.copperbench.shell.CopperbenchProductShell;
import net.mcreator.Launcher;
import net.mcreator.io.LoggingSystem;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.chromium.*;
import net.mcreator.ui.init.*;
import net.mcreator.ui.laf.themes.ThemeManager;
import net.mcreator.util.*;

import javax.swing.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;

/** Opt-in native composer acceptance: fixture Core projection, real attachment bridge and installed CLIs. */
public final class AgentComposerDesktopMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: <isolated-attachment-workspace>");
        Path root = Path.of(args[0]).toAbsolutePath().normalize(); Files.createDirectories(root);
        LoggingSystem.init();
        TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
        TerribleModuleHacks.openMCreatorRequirements();
        Properties config = new Properties(); try (var input = Launcher.class.getResourceAsStream("/mcreator.conf")) { config.load(input); }
        Launcher.version = new MCreatorVersionNumber(config);
        PreferencesManager.init(); PluginLoader.initInstance(); ThemeManager.loadThemes(); UIRES.preloadImages(); ThemeManager.applySelectedTheme(); L10N.initTranslations();
        UUID workspaceId = UUID.randomUUID();
        JsonObject generator = AgentJson.object("id", "fabric-1.21.1", "loader", "fabric", "minecraftVersion", "1.21.1", "displayName", "Fabric 1.21.1", "state", "ready");
        RevisionedWorkspaceStore store = new RevisionedWorkspaceStore();
        store.register(new WorkspaceState(workspaceId, "Composer acceptance", "mod", 0, false, generator, new JsonObject(), List.of()));
        Clock clock = Clock.systemUTC();
        WorkspaceApplicationService core = new WorkspaceApplicationService(store, new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID);
        WorkspaceEntryAdapter adapter = new WorkspaceEntryAdapter(core, new RequestContext(Actor.UI, PermissionProfile.WORKSPACE));
        AgentChatService agent = new AgentChatService(root);
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Copperbench Composer Acceptance");
            WebView web = new WebView(CopperbenchProductShell.UI_URL);
            JcefCoreBridgeTransport coreBridge = web.attachCoreBridge(workspaceId, adapter);
            JcefAgentBridgeTransport agentBridge = JcefAgentBridgeTransport.attach(web, agent);
            JcefAssetImportBridgeTransport dropBridge = JcefAssetImportBridgeTransport.attach(web, frame, core, agentBridge::handleFileDrop);
            frame.setContentPane(web); frame.setSize(1400, 900); frame.setLocationByPlatform(true);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent event) {
                    dropBridge.close(); agentBridge.close(); coreBridge.close(); agent.close(); web.close(); CefUtils.close(); System.exit(0);
                }
            });
            frame.setVisible(true); web.forceLoad();
        });
    }
}
