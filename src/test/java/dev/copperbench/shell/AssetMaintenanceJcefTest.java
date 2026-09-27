package dev.copperbench.shell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.ui.chromium.WebView;
import net.mcreator.util.TerribleModuleHacks;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real JCEF/source-workspace replay; preparation writes are separate from UI actions. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "copperbench.stage4.jcefSmoke", matches = "true")
class AssetMaintenanceJcefTest {
    @BeforeAll static void initialize() throws Exception {
        TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
        TerribleModuleHacks.openMCreatorRequirements();
        McreatorTestRuntime.ensureInitialized();
    }

    @Test @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void importsBindsAndRefreshesWithoutReopeningTheNativeTaskPanel() throws Exception {
        Path evidence = Files.createDirectories(Path.of("build/maintenance-jcef", UUID.randomUUID().toString()).toAbsolutePath());
        Path root = Files.createDirectories(evidence.resolve("workspace"));
        Path document = root.resolve("maintenance_fixture.mcreator");
        Clock clock = Clock.systemUTC();
        UUID workspaceId = UUID.randomUUID(), taskId = UUID.randomUUID();
        WorkspaceSettings settings = new WorkspaceSettings("maintenance_fixture");
        settings.setModName("Maintenance Fixture");
        settings.setVersion("1.0.0");
        settings.setCurrentGenerator("fabric-1.21.1");
        System.out.println("MAINTENANCE_JCEF_EVIDENCE=" + evidence);
        try (Workspace workspace = Workspace.createWorkspace(document.toFile(), settings)) {
            assertTrue(workspace.getGenerator().generateBase());
            workspace.getFileManager().loadOrCreateProductMetadata(workspaceId);
            try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
                    store -> new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID)) {
                JsonObject create = JsonParser.parseString("""
                        {"elementType":"block","name":"maintenance_lamp","initialValues":{"displayName":"Maintenance Lamp"}}
                        """).getAsJsonObject();
                create.addProperty("clientMutationId", UUID.randomUUID().toString());
                var created = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, 0, Operation.CREATE_MOD_ELEMENT, create)).result();
                assertEquals("committed", created.status(), created.diagnostics().toString());
                JsonObject begin = new JsonObject();
                begin.addProperty("taskId", taskId.toString());
                begin.add("elementId", created.data().getAsJsonObject().getAsJsonObject("element").get("id"));
                var started = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, created.newRevision(), Operation.BEGIN_BLOCKBENCH_TASK, begin)).result();
                assertEquals("completed", started.status(), started.diagnostics().toString());
                Path edit = Path.of(started.data().getAsJsonObject().get("editPath").getAsString());
                Files.writeString(edit, "{\"meta\":{\"model_format\":\"java_block\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16]}],\"textures\":[]}", StandardCharsets.UTF_8);
                Files.writeString(edit.getParent().resolve("game.json"), "{\"textures\":{\"all\":\"maintenance_fixture:block/lamp\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"up\":{\"texture\":\"#all\"}}}]}", StandardCharsets.UTF_8);
                ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", edit.getParent().resolve("lamp.png").toFile());
                JsonObject taskPayload = new JsonObject(); taskPayload.addProperty("taskId", taskId.toString());
                var task = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_BLOCKBENCH_TASK, taskPayload)).data().getAsJsonObject();
                JsonObject finish = taskPayload.deepCopy(); finish.add("savedSha256", task.get("editSha256"));
                var finished = session.uiEntry().execute(Command.of(UUID.randomUUID(), workspaceId, created.newRevision(), Operation.FINISH_BLOCKBENCH_TASK, finish)).result();
                assertEquals("completed", finished.status(), finished.diagnostics().toString());
                Files.writeString(evidence.resolve("preparation.json"), finished.data().toString(), StandardCharsets.UTF_8);

                JFrame[] frame = new JFrame[1];
                try (WebView web = new WebView(CopperbenchProductShell.UI_URL);
                     var bridge = web.attachCoreBridge(workspaceId, session.uiEntry())) {
                    SwingUtilities.invokeAndWait(() -> {
                        frame[0] = new JFrame("Copperbench maintenance validation");
                        frame[0].setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                        frame[0].setSize(1366, 900);
                        frame[0].setLocationRelativeTo(null);
                        frame[0].setContentPane(web);
                        frame[0].setVisible(true);
                        web.forceLoad();
                    });
                    try {
                        await(web, "document.querySelector('[data-testid=nav-assets]') !== null");
                        web.executeScriptAsync("const s=document.querySelector('[data-testid=ui-language-select]'); s.value='zh'; s.dispatchEvent(new Event('change',{bubbles:true}));");
                        web.executeScriptAsync("document.querySelector('[data-testid=nav-assets]').click()");
                        await(web, "document.querySelector('[data-testid=blockbench-tasks]') !== null");
                        web.executeScriptAsync("window.__maintenancePanel=document.querySelector('[data-testid=blockbench-tasks]'); window.__maintenancePanel.querySelector('summary').click()");
                        await(web, "document.querySelector('.modeling-import-review > summary') !== null");
                        web.executeScriptAsync("document.querySelector('.modeling-import-review > summary').click()");
                        clickButton(web, "识别并预览回导");
                        await(web, "Array.from(document.querySelectorAll('button')).some(b=>b.textContent==='应用回导'&&!b.disabled)");
                        clickButton(web, "应用回导");
                        await(web, "window.__maintenancePanel.open && window.__maintenancePanel.textContent.includes('文件已回导') && document.querySelector('[data-testid=asset-details]') !== null");
                        assertEquals("true", read(web, "window.__maintenancePanel===document.querySelector('[data-testid=blockbench-tasks]')"));
                        await(web, "Array.from(document.querySelectorAll('button')).some(b=>b.textContent==='关联所选模型'&&!b.disabled)");
                        clickButton(web, "关联所选模型");
                        await(web, "window.__maintenancePanel.textContent.includes('元素定义已关联')");
                        // Observe the real refresh response; do not accept the pre-refresh DOM as completion.
                        web.executeScriptAsync("""
                                window.__maintenanceRefresh = 'pending';
                                const invoke = window.copperbenchHost.invoke.bind(window.copperbenchHost);
                                window.copperbenchHost.invoke = async raw => {
                                    const result = await invoke(raw);
                                    if (JSON.parse(raw).operation === 'list_assets') {
                                        requestAnimationFrame(() => requestAnimationFrame(() => {
                                            window.__maintenanceRefresh = JSON.parse(result).status;
                                        }));
                                    }
                                    return result;
                                };
                                window.dispatchEvent(new Event('focus'));
                                """);
                        await(web, "window.__maintenanceRefresh === 'succeeded' && document.querySelector('[data-testid=asset-details]') !== null && document.querySelector('[data-testid=asset-browser-loading]') === null && window.__maintenancePanel.open && window.__maintenancePanel.textContent.includes('元素定义已关联')");
                        assertEquals("true", read(web, "window.__maintenancePanel===document.querySelector('[data-testid=blockbench-tasks]')"));
                        var bound = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_BLOCKBENCH_TASK, taskPayload)).data();
                        assertEquals("bound", bound.getAsJsonObject().getAsJsonObject("binding").get("state").getAsString());
                        Files.writeString(evidence.resolve("bound.json"), bound.toString(), StandardCharsets.UTF_8);
                        ImageIO.write(new Robot().createScreenCapture(frame[0].getBounds()), "png", evidence.resolve("bound-refresh.png").toFile());
                    } finally {
                        Files.writeString(evidence.resolve("terminal-dom.txt"), String.valueOf(read(web, "document.body.innerText")), StandardCharsets.UTF_8);
                        SwingUtilities.invokeAndWait(frame[0]::dispose);
                    }
                }
            }
        }
        // Use a new workspace/session, not the prior in-memory state, to verify binding persistence.
        try (Workspace reopened = Workspace.readFromFS(document.toFile(), null);
             MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(reopened,
                     store -> new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID)) {
            JsonObject payload = new JsonObject(); payload.addProperty("taskId", taskId.toString());
            var task = session.uiEntry().query(Query.of(UUID.randomUUID(), workspaceId, Operation.GET_BLOCKBENCH_TASK, payload)).data();
            assertEquals("bound", task.getAsJsonObject().getAsJsonObject("binding").get("state").getAsString());
            Files.writeString(evidence.resolve("reopened.json"), task.toString(), StandardCharsets.UTF_8);
        }
    }

    private static String read(WebView web, String expression) {
        return web.executeScript(expression, WebView.JSExecutionType.RETURN_VALUE);
    }

    private static void await(WebView web, String expression) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < deadline) {
            if ("true".equals(read(web, expression))) return;
            Thread.sleep(100);
        }
        fail("Native UI condition not reached: " + expression + "\n" + read(web, "document.body.innerText"));
    }

    private static void clickButton(WebView web, String label) throws Exception {
        String text = new com.google.gson.Gson().toJson(label);
        await(web, "Array.from(document.querySelectorAll('button')).some(b=>b.textContent===" + text + "&&!b.disabled)");
        web.executeScriptAsync("Array.from(document.querySelectorAll('button')).find(b=>b.textContent===" + text + "&&!b.disabled).click()");
    }
}
