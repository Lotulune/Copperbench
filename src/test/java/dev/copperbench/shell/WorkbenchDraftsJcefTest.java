package dev.copperbench.shell;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.bridge.JcefWindowBridgeTransport;
import dev.copperbench.core.application.InMemoryWorkspaceTaskGateway;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceSession;
import dev.copperbench.testing.McreatorTestRuntime;
import dev.copperbench.window.WindowsWindowChromeController;
import net.mcreator.io.UserFolderManager;
import net.mcreator.ui.chromium.WebView;
import net.mcreator.util.TerribleModuleHacks;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real native renderer, Core workspace, window transport and production close dialog. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "copperbench.stage4.jcefSmoke", matches = "true")
class WorkbenchDraftsJcefTest {
    private static final Gson JSON = new Gson();

    @Test @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void retainsDraftsConfirmsNativeCloseAndOpensSupportedAssets() throws Exception {
        Path evidence = Files.createDirectories(Path.of("output/uiux-fix-20261007/native", UUID.randomUUID().toString()).toAbsolutePath());
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(evidence.resolve("runtime-home")).toString());
        System.out.println("UIUX_NATIVE_EVIDENCE=" + evidence);
        try {
            assertTrue(UserFolderManager.createUserFolderIfNotExists());
            TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());
            TerribleModuleHacks.openMCreatorRequirements();
            McreatorTestRuntime.ensureInitialized();
            UiLocalePreferences.save("zh");
            verify(evidence);
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    private void verify(Path evidence) throws Exception {
        Path root = Files.createDirectories(evidence.resolve("workspace"));
        var settings = new WorkspaceSettings("uiux_acceptance");
        settings.setModName("UIUX Native Acceptance");
        settings.setVersion("1.0.0");
        settings.setCurrentGenerator("fabric-1.21.1");
        Clock clock = Clock.systemUTC();
        JsonObject receipt = new JsonObject();
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("uiux_acceptance.mcreator").toFile(), settings)) {
            assertTrue(workspace.getGenerator().generateBase());
            write(root, "models/custom/native_model.json", "{\"parent\":\"minecraft:block/cube_all\"}\n");
            write(root, "assets/uiux_acceptance/lang/legacy.lang", "item.native=Native language\n");
            try (MCreatorWorkspaceSession session = MCreatorWorkspaceSession.attach(workspace,
                    store -> new InMemoryWorkspaceTaskGateway(clock, UUID::randomUUID), clock, UUID::randomUUID)) {
                var block = create(session, 0, "block", "native_block");
                var function = create(session, block.newRevision(), "function", "native_function");
                String blockId = block.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
                String functionId = function.data().getAsJsonObject().getAsJsonObject("element").get("id").getAsString();
                receipt.addProperty("workspaceId", session.workspaceId().toString());
                receipt.addProperty("workspace", root.toString());
                receipt.addProperty("preparation", "Created isolated native workspace, two elements and two assets before desktop interaction");
                JFrame[] frame = new JFrame[1];
                WindowsWindowChromeController[] chrome = new WindowsWindowChromeController[1];
                AtomicInteger drafts = new AtomicInteger(), closeAttempts = new AtomicInteger();
                AtomicBoolean closed = new AtomicBoolean();
                SwingUtilities.invokeAndWait(() -> {
                    frame[0] = new JFrame("Copperbench UIUX native acceptance");
                    frame[0].setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                    chrome[0] = WindowsWindowChromeController.prepare(frame[0]);
                    frame[0].setSize(1366, 900);
                    frame[0].setLocationRelativeTo(null);
                });
                Runnable close = () -> {
                    closeAttempts.incrementAndGet();
                    if (CopperbenchProductShell.confirmClose(frame[0], drafts.get())) {
                        closed.set(true); frame[0].dispose();
                    }
                };
                try (WebView web = new WebView(CopperbenchProductShell.UI_URL);
                     var core = web.attachCoreBridge(session.workspaceId(), session.uiEntry());
                     var window = JcefWindowBridgeTransport.attach(web, frame[0], close, chrome[0]::accept,
                             chrome[0]::isUsingCustomFrame, chrome[0]::pointerGesture, drafts::set)) {
                    SwingUtilities.invokeAndWait(() -> {
                        frame[0].addWindowListener(new WindowAdapter() {
                            @Override public void windowClosing(WindowEvent event) { close.run(); }
                        });
                        frame[0].setContentPane(web);
                        frame[0].setVisible(true);
                        assertTrue(chrome[0].install());
                        web.forceLoad();
                    });
                    try {
                        await(web, "window.copperbenchHost?.workspaceId === " + JSON.toJson(session.workspaceId().toString())
                                + " && document.querySelector('[data-testid=workbench-main] h1')?.textContent === 'UIUX Native Acceptance'");
                        click(web, "[data-testid=nav-elements]");
                        click(web, "[data-element-id='" + functionId + "']");
                        await(web, "document.querySelector('[data-testid=function-code-editor]')?.readOnly === false");
                        fill(web, "[data-testid=function-code-editor]", "say native retained draft\n");
                        waitFor(() -> drafts.get() == 1, "Function draft was not reported to the native host");
                        click(web, "[data-testid=nav-assets]");
                        click(web, "[data-testid=workbench-tab-elements]");
                        await(web, "document.querySelector('[data-testid=function-code-editor]')?.value === 'say native retained draft\\n'");
                        capture(web, evidence.resolve("function-retained.png"));
                        click(web, "[data-testid=function-save-btn]");
                        waitFor(() -> drafts.get() == 0, "Committed function remained dirty");
                        try (var files = Files.list(root.resolve("elements"))) {
                            assertTrue(files.anyMatch(path -> {
                                try { return Files.readString(path).contains("say native retained draft"); }
                                catch (Exception exception) { throw new IllegalStateException(exception); }
                            }), "Function changes did not reach the workspace files");
                        }
                        receipt.addProperty("functionDraftAndDiskSave", "passed");

                        click(web, "[data-testid=function-back-btn]");
                        click(web, "[data-element-id='" + blockId + "']");
                        fill(web, "[data-testid=field-displayName]", "Native retained block");
                        waitFor(() -> drafts.get() == 1, "Element draft was not reported to the native host");
                        // Address the real Swing control directly; never send input to an unrelated foreground window.
                        click(web, "[data-testid=window-close-btn]");
                        JButton keep = waitForButton("继续编辑");
                        captureDialog(keep, evidence.resolve("native-close-confirmation.png"));
                        SwingUtilities.invokeLater(() -> keep.doClick(0));
                        waitFor(() -> closeAttempts.get() == 1 && !hasDialog(), "Keep editing did not dismiss the native dialog");
                        assertFalse(closed.get());
                        assertEquals("Native retained block", read(web, "document.querySelector('[data-testid=field-displayName]').value"));
                        click(web, "[data-testid=inspector-save-btn]");
                        waitFor(() -> drafts.get() == 0, "Committed element remained dirty");
                        assertTrue(CopperbenchProductShell.confirmClose(frame[0], 0), "Clean shell should close without a dialog");
                        receipt.addProperty("nativeKeepEditingAndCleanGuard", "passed");

                        click(web, "[data-testid=workbench-command-trigger]");
                        await(web, "document.activeElement?.getAttribute('role') === 'combobox'");
                        for (int i = 0; i < 12; i++) {
                            web.executeScriptAsync("document.querySelector('[role=combobox]').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true,cancelable:true}))");
                            int selection = i + 1;
                            await(web, "document.querySelector('#workbench-command-" + selection + "')?.getAttribute('aria-selected') === 'true'");
                        }
                        await(web, "document.querySelector('#workbench-command-12')?.getAttribute('aria-selected') === 'true'");
                        assertEquals("true", read(web, "(() => {const l=document.querySelector('#workbench-command-results'),s=l.querySelector('[aria-selected=true]');const a=l.getBoundingClientRect(),b=s.getBoundingClientRect();return b.top>=a.top-1&&b.bottom<=a.bottom+1;})()"));
                        capture(web, evidence.resolve("native-keyboard-search.png"));
                        web.executeScriptAsync("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}))");
                        await(web, "document.querySelector('[data-testid=workbench-command-palette]') === null");
                        receipt.addProperty("nativeKeyboardSearch", "passed");

                        for (String path : new String[]{"models/custom/native_model.json", "assets/uiux_acceptance/lang/legacy.lang"}) {
                            click(web, "[data-testid=nav-assets]");
                            click(web, "[data-asset-id][title='" + path + "']");
                            click(web, ".asset-library-source-link");
                            await(web, "document.querySelector('[data-testid=source-editor]')?.value === " + JSON.toJson(Files.readString(root.resolve(path))));
                            capture(web, evidence.resolve(path.endsWith(".lang") ? "native-language-source.png" : "native-model-source.png"));
                        }
                        receipt.addProperty("nativeAssetSourcePaths", "passed");

                        click(web, "[data-testid=nav-elements]");
                        fill(web, "[data-testid=field-displayName]", "Discard this native draft");
                        waitFor(() -> drafts.get() == 1, "Final element draft was not counted");
                        // Also exercise the system window-close event, not only the React close button.
                        SwingUtilities.invokeLater(() -> frame[0].dispatchEvent(new WindowEvent(frame[0], WindowEvent.WINDOW_CLOSING)));
                        JButton discard = waitForButton("关闭并放弃");
                        SwingUtilities.invokeLater(() -> discard.doClick(0));
                        waitFor(closed::get, "Discard did not close the native test window");
                        receipt.addProperty("nativeSystemCloseDiscard", "passed");
                        receipt.addProperty("closeAttempts", closeAttempts.get());
                        receipt.addProperty("interaction", "Targeted JCEF DOM events and real Swing button actions; no global desktop input");
                        Files.writeString(evidence.resolve("receipt.json"), receipt.toString(), StandardCharsets.UTF_8);
                    } finally {
                        SwingUtilities.invokeAndWait(() -> {
                            for (Window owned : frame[0].getOwnedWindows()) owned.dispose();
                            chrome[0].close(); frame[0].dispose();
                        });
                    }
                }
            }
        }
    }

    private static CommandResult create(MCreatorWorkspaceSession session, long revision, String type, String name) {
        JsonObject payload = new JsonObject();
        payload.addProperty("clientMutationId", UUID.randomUUID().toString());
        payload.addProperty("elementType", type); payload.addProperty("name", name);
        payload.add("initialValues", new JsonObject());
        var result = session.uiEntry().execute(Command.of(UUID.randomUUID(), session.workspaceId(), revision, Operation.CREATE_MOD_ELEMENT, payload)).result();
        assertEquals("committed", result.status(), result.diagnostics().toString());
        return result;
    }
    private static void write(Path root, String relative, String content) throws Exception {
        Files.createDirectories(root.resolve(relative).getParent()); Files.writeString(root.resolve(relative), content, StandardCharsets.UTF_8);
    }
    private static String read(WebView web, String expression) { return web.executeScript(expression, WebView.JSExecutionType.RETURN_VALUE); }
    private static void await(WebView web, String expression) throws Exception {
        waitFor(() -> "true".equals(read(web, expression)), "Native UI condition failed: " + expression);
    }
    private static void click(WebView web, String selector) throws Exception {
        String target = "document.querySelector(" + JSON.toJson(selector) + ")";
        await(web, target + " != null && !" + target + ".disabled");
        web.executeScriptAsync(target + ".click()");
    }
    private static void fill(WebView web, String selector, String value) throws Exception {
        String target = "document.querySelector(" + JSON.toJson(selector) + ")";
        await(web, target + " != null && !" + target + ".readOnly");
        web.executeScriptAsync("{const e=" + target + ";Object.getOwnPropertyDescriptor(e instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype,'value').set.call(e," + JSON.toJson(value) + ");e.dispatchEvent(new Event('input',{bubbles:true}));}");
        await(web, target + ".value === " + JSON.toJson(value));
    }
    private static void waitFor(java.util.function.BooleanSupplier condition, String failure) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) { if (condition.getAsBoolean()) return; Thread.sleep(100); }
        fail(failure);
    }
    private static JButton waitForButton(String text) throws Exception {
        JButton[] result = new JButton[1];
        waitFor(() -> {
            try { SwingUtilities.invokeAndWait(() -> {
                for (Window window : Window.getWindows()) if (window instanceof JDialog && window.isShowing()) {
                    JButton found = findButton(window, text); if (found != null) result[0] = found;
                }
            }); } catch (Exception exception) { throw new IllegalStateException(exception); }
            return result[0] != null;
        }, "Native confirmation button missing: " + text);
        return result[0];
    }
    private static JButton findButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) return button;
            if (component instanceof Container nested) { JButton result = findButton(nested, text); if (result != null) return result; }
        }
        return null;
    }
    private static boolean hasDialog() { return java.util.Arrays.stream(Window.getWindows()).anyMatch(window -> window instanceof JDialog && window.isShowing()); }
    private static void capture(WebView web, Path path) throws Exception {
        String raw = web.getBrowser().getDevToolsClient().executeDevToolsMethod("Page.captureScreenshot").get(10, TimeUnit.SECONDS);
        String base64 = JsonParser.parseString(raw).getAsJsonObject().get("data").getAsString();
        Files.write(path, java.util.Base64.getDecoder().decode(base64));
    }
    private static void captureDialog(JButton button, Path path) throws Exception {
        BufferedImage[] result = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            Window dialog = SwingUtilities.getWindowAncestor(button);
            result[0] = new BufferedImage(dialog.getWidth(), dialog.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = result[0].createGraphics();
            try { dialog.printAll(graphics); } finally { graphics.dispose(); }
        });
        ImageIO.write(result[0], "png", path.toFile());
    }
}
