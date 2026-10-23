package dev.copperbench.core.application;

import com.google.gson.*;
import dev.copperbench.core.contract.UiCore.*;
import dev.copperbench.core.workspace.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ElementVisualPreviewQueryTest {
    @TempDir Path root;
    @Test void readsEffectiveAppearanceAndDraftsWithoutSavingOrStartingTasks() throws Exception {
        UUID workspaceId=UUID.randomUUID(), elementId=UUID.randomUUID(); Instant stamp=Instant.parse("2026-10-23T00:00:00Z");
        JsonObject values=JsonParser.parseString("{\"displayName\":\"Lamp\",\"texture\":\"old\",\"fields\":{\"texture\":\"old\"}}").getAsJsonObject();
        JsonObject generator=JsonParser.parseString("{\"id\":\"fabric-1.21.1\",\"loader\":\"fabric\",\"minecraftVersion\":\"1.21.1\",\"state\":\"ready\"}").getAsJsonObject();
        var element=new WorkspaceState.Element(elementId,"block","lamp","Lamp","valid","generated",stamp,values);
        var store=new RevisionedWorkspaceStore();store.register(new WorkspaceState(workspaceId,"test","mod",7,false,generator,new JsonObject(),List.of(element)));
        var tasks=new InMemoryWorkspaceTaskGateway(Clock.systemUTC(),UUID::randomUUID);
        WorkspaceMutationGateway mutations=new WorkspaceMutationGateway() {
            @Override public void persist(WorkspaceState before,WorkspaceState after,Operation operation,WorkspaceState.Element affected){fail("Preview must not save");}
            @Override public JsonObject elementConfiguration(WorkspaceState.Element element) {
                JsonObject result=new JsonObject();result.add("effectiveValues",JsonParser.parseString("{\"displayName\":\"Lamp\",\"fields\":{\"texture\":\"actual\"}}").getAsJsonObject());return result;
            }
        };
        var service=new WorkspaceApplicationService(store,tasks,mutations,null,null,id->root,Clock.systemUTC(),UUID::randomUUID);
        JsonObject payload=new JsonObject();payload.addProperty("elementId",elementId.toString());payload.addProperty("expectedUpdatedAt",stamp.toString());
        var context=new RequestContext(Actor.UI,PermissionProfile.READ_ONLY);
        var result=service.query(Query.of(UUID.randomUUID(),workspaceId,Operation.GET_ELEMENT_VISUAL_PREVIEW,payload),context);
        assertEquals("succeeded",result.status(),()->result.diagnostics().toString());
        assertEquals("test:block/actual",result.data().getAsJsonObject().getAsJsonObject("scene").getAsJsonArray("missingTextures").get(0).getAsString());
        payload.add("changes",JsonParser.parseString("[{\"path\":\"/texture\",\"value\":\"draft\"}]").getAsJsonArray());
        var draft=service.query(Query.of(UUID.randomUUID(),workspaceId,Operation.GET_ELEMENT_VISUAL_PREVIEW,payload),context);
        assertEquals("succeeded",draft.status(),()->draft.diagnostics().toString());
        assertEquals("test:block/draft",draft.data().getAsJsonObject().getAsJsonObject("scene").getAsJsonArray("missingTextures").get(0).getAsString());
        assertEquals(7,draft.revision());assertTrue(tasks.active(workspaceId).isEmpty());assertEquals("old",values.get("texture").getAsString());
        try(var files=Files.list(root)){assertEquals(0,files.count());}
        payload.addProperty("expectedUpdatedAt","2026-10-22T00:00:00Z");
        assertNotEquals("succeeded",service.query(Query.of(UUID.randomUUID(),workspaceId,Operation.GET_ELEMENT_VISUAL_PREVIEW,payload),context).status());
        UUID withoutGenerator=UUID.randomUUID();
        store.register(new WorkspaceState(withoutGenerator,"test","mod",0,false,new JsonObject(),new JsonObject(),List.of(element)));
        payload.remove("expectedUpdatedAt");
        assertEquals("succeeded",service.query(Query.of(UUID.randomUUID(),withoutGenerator,Operation.GET_ELEMENT_VISUAL_PREVIEW,payload),context).status());
    }
}
