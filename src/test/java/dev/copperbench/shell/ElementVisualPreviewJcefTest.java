package dev.copperbench.shell;

import com.google.gson.*;
import dev.copperbench.core.application.*;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import dev.copperbench.testing.McreatorTestRuntime;
import net.mcreator.io.UserFolderManager;
import net.mcreator.ui.chromium.WebView;
import net.mcreator.util.TerribleModuleHacks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Real Core query, production UI bundle and native JCEF/WebGL renderer in an isolated workspace. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named="copperbench.stage4.jcefSmoke",matches="true")
class ElementVisualPreviewJcefTest {
    @Test @Timeout(value=3,unit=TimeUnit.MINUTES)
    void rendersProjectGeometryInTheNativeDesktop() throws Exception {
        Path evidence=Files.createDirectories(Path.of("output/element-preview-validation/native",UUID.randomUUID().toString()).toAbsolutePath());
        System.out.println("ELEMENT_PREVIEW_NATIVE_EVIDENCE="+evidence);
        String previousHome=System.getProperty("user.home");
        System.setProperty("user.home",Files.createDirectories(evidence.resolve("runtime-home")).toString());
        try {
            assertTrue(UserFolderManager.createUserFolderIfNotExists());
            TerribleModuleHacks.openAllFor(ClassLoader.getSystemClassLoader().getUnnamedModule());TerribleModuleHacks.openMCreatorRequirements();
            McreatorTestRuntime.ensureInitialized(); UiLocalePreferences.save("zh");
            Path root=Files.createDirectories(evidence.resolve("workspace"));
            Path textures=Files.createDirectories(root.resolve("src/main/resources/assets/preview_test/textures/block"));
            for(var entry:Map.of("side",0xffb67542,"top",0xff579773).entrySet()) {
                var image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,(x+y)%7==0?0xff324e40:entry.getValue());
                ImageIO.write(image,"png",textures.resolve(entry.getKey()+".png").toFile());
            }
            UUID workspaceId=UUID.randomUUID(),elementId=UUID.randomUUID();
            JsonObject generator=JsonParser.parseString("{\"id\":\"fabric-1.21.1\",\"loader\":\"fabric\",\"minecraftVersion\":\"1.21.1\",\"state\":\"ready\"}").getAsJsonObject();
            JsonObject values=JsonParser.parseString("{\"displayName\":\"预览方块\",\"texture\":\"side\",\"textureTop\":\"top\",\"renderType\":10}").getAsJsonObject();
            var element=new WorkspaceState.Element(elementId,"block","preview_block","预览方块","valid","generated",Instant.now(),values);
            var store=new RevisionedWorkspaceStore();store.register(new WorkspaceState(workspaceId,"preview_test","mod",0,false,generator,new JsonObject(),List.of(element)));
            var service=new WorkspaceApplicationService(store,new InMemoryWorkspaceTaskGateway(Clock.systemUTC(),UUID::randomUUID),WorkspaceMutationGateway.noOp(),null,null,id->root,Clock.systemUTC(),UUID::randomUUID);
            var entry=new WorkspaceEntryAdapter(service,new RequestContext(Actor.UI,PermissionProfile.WORKSPACE));
            JsonObject payload=new JsonObject();payload.addProperty("elementId",elementId.toString());
            var query=entry.query(Query.of(UUID.randomUUID(),workspaceId,Operation.GET_ELEMENT_VISUAL_PREVIEW,payload));
            assertEquals("succeeded",query.status(),()->query.diagnostics().toString());
            Files.writeString(evidence.resolve("core-preview.json"),new Gson().toJson(query),StandardCharsets.UTF_8);
            JFrame[] frame=new JFrame[1];
            try(WebView web=new WebView(CopperbenchProductShell.UI_URL);var bridge=web.attachCoreBridge(workspaceId,entry)) {
                SwingUtilities.invokeAndWait(()->{frame[0]=new JFrame("Copperbench element preview acceptance");frame[0].setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                    frame[0].setSize(1366,900);frame[0].setLocationRelativeTo(null);frame[0].setContentPane(web);frame[0].setVisible(true);web.forceLoad();});
                try {
                    await(web,"document.querySelector('[data-testid=app-shell]') !== null");
                    web.executeScriptAsync("document.querySelector('[data-testid=mode-professional]')?.click()");
                    await(web,"document.querySelector('[data-testid=nav-elements]') !== null");
                    web.executeScriptAsync("document.querySelector('[data-testid=nav-elements]').click()");
                    await(web,"document.querySelector('[data-element-id=\""+elementId+"\"]') !== null");
                    web.executeScriptAsync("document.querySelector('[data-element-id=\""+elementId+"\"]').click()");
                    await(web,"document.querySelector('[data-testid=model-scene-preview]')?.dataset.previewState === 'ready'");
                    String first=read(web,"document.querySelector('[data-testid=model-scene-preview] canvas').toDataURL().split(',')[1]");
                    Files.write(evidence.resolve("native-canvas.png"),Base64.getDecoder().decode(first));
                    web.executeScriptAsync("document.querySelector('[aria-label=\"向左旋转\"]').click()");
                    await(web,"document.querySelector('[data-testid=model-scene-preview] canvas').toDataURL().split(',')[1] !== "+new Gson().toJson(first));
                    web.executeScriptAsync("document.querySelector('[aria-label=\"放大\"]').click()");
                    await(web,"document.querySelector('[aria-label=\"缩放比例\"]')?.textContent === '116%'");
                    Files.write(evidence.resolve("native-canvas-orbit-zoom.png"),Base64.getDecoder().decode(read(web,"document.querySelector('[data-testid=model-scene-preview] canvas').toDataURL().split(',')[1]")));
                    Files.writeString(evidence.resolve("result.json"),"{\"status\":\"passed\",\"renderer\":\"native-jcef\",\"query\":\"real-core\",\"orbit\":true,\"zoom\":true}",StandardCharsets.UTF_8);
                } finally {Files.writeString(evidence.resolve("terminal-dom.txt"),String.valueOf(read(web,"document.body.innerText")),StandardCharsets.UTF_8);SwingUtilities.invokeAndWait(frame[0]::dispose);}
            }
        } finally {System.setProperty("user.home",previousHome);}
    }
    private static String read(WebView web,String expression){return web.executeScript(expression,WebView.JSExecutionType.RETURN_VALUE);}
    private static void await(WebView web,String expression)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
        while(System.nanoTime()<deadline){if("true".equals(read(web,expression)))return;Thread.sleep(100);}
        fail("Native preview condition not reached: "+expression+"\n"+read(web,"document.body.innerText"));
    }
}
