package dev.copperbench.assets;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelPreviewServiceTest {
    @TempDir Path root;
    private Path image(String name, int color) throws Exception {
        Path file = root.resolve("src/main/resources/assets/test/textures/" + name + ".png");
        Files.createDirectories(file.getParent()); var image = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++) for(int x=0;x<16;x++) image.setRGB(x,y,color);
        ImageIO.write(image,"png",file.toFile()); return file;
    }
    private Path model(String name, String content) throws Exception {
        Path file=root.resolve("src/main/resources/assets/test/models/"+name+".json"); Files.createDirectories(file.getParent()); Files.writeString(file,content); return file;
    }
    private ModelPreviewService service() {return new ModelPreviewService(root,new AssetWorkspaceService(root).list());}
    private JsonObject json(String value) {return JsonParser.parseString(value).getAsJsonObject();}

    @Test void blockUsesSixRealFaceTexturesWithoutGeneratingOrMutatingFiles() throws Exception {
        for(String name:List.of("bottom","top","front","left","back","right")) image("block/"+name,0xff55aa77);
        var values=json("{\"texture\":\"bottom\",\"textureTop\":\"top\",\"textureFront\":\"front\",\"textureLeft\":\"left\",\"textureBack\":\"back\",\"textureRight\":\"right\"}");
        String before=values.toString(); var scene=service().element("block","test_block","test",values);
        assertEquals("model",scene.get("kind").getAsString()); assertEquals(6,scene.getAsJsonObject("textures").size());
        var faces=scene.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
        assertEquals("test:block/front",faces.getAsJsonObject("north").get("texture").getAsString());
        assertEquals("test:block/left",faces.getAsJsonObject("east").get("texture").getAsString());
        assertTrue(scene.getAsJsonArray("missingTextures").isEmpty()); assertEquals(before,values.toString());
        assertFalse(Files.exists(root.resolve("src/main/resources/assets/test/models")));
    }
    @Test void templatesSupportSlabsStairsAndCrossPlantsBeforeDownloadingTheClient() throws Exception {
        image("block/leaf",0xff55aa77);
        var slab=service().element("block","leaf","test",json("{\"texture\":\"leaf\",\"blockBase\":\"Slab\"}"));
        assertEquals(8,slab.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("to").get(1).getAsInt());
        var stairs=service().element("block","leaf","test",json("{\"texture\":\"leaf\",\"blockBase\":\"Stairs\"}"));
        assertEquals(2,stairs.getAsJsonArray("elements").size());
        var plant=service().element("plant","leaf","test",json("{\"texture\":\"leaf\"}"));
        assertEquals(2,plant.getAsJsonArray("elements").size());
        assertTrue(plant.getAsJsonArray("elements").get(0).getAsJsonObject().has("rotation"));
    }
    @Test void inheritedCustomGeometryKeepsUvRotationAndChildTextureOverrides() throws Exception {
        image("block/blue",0xff2233cc); image("block/red",0xffcc3322);
        model("block/base","{\"textures\":{\"a\":\"test:block/red\",\"side\":\"#a\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,8,16],\"rotation\":{\"axis\":\"y\",\"angle\":22.5,\"origin\":[8,8,8]},\"faces\":{\"north\":{\"texture\":\"#side\",\"uv\":[0,8,16,16],\"rotation\":90}}}]}");
        model("block/child","{\"parent\":\"test:block/base\",\"textures\":{\"a\":\"test:block/blue\"}}");
        var result=service().element("block","lamp","test",json("{\"modelResource\":\"test:block/child\"}"));
        var element=result.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals(22.5,element.getAsJsonObject("rotation").get("angle").getAsDouble());
        var face=element.getAsJsonObject("faces").getAsJsonObject("north");
        assertEquals(90,face.get("rotation").getAsInt()); assertEquals("test:block/blue",face.get("texture").getAsString());
        assertEquals(Set.of("test:block/blue"),result.getAsJsonObject("textures").keySet());
    }
    @Test void generatedItemsReturnTheirActualLayersAndInlineDraftBytes() throws Exception {
        Path image=image("item/wand",0xff55aa77);
        var result=service().element("item","wand","test",json("{\"texture\":\"wand\"}"));
        assertEquals("item",result.get("kind").getAsString()); assertEquals("test:item/wand",result.getAsJsonArray("layers").get(0).getAsString());
        JsonObject draft=new JsonObject();draft.addProperty("textureBase64",Base64.getEncoder().encodeToString(Files.readAllBytes(image)));
        var inline=service().element("block","wand","test",draft);
        assertEquals(draft.get("textureBase64"),inline.getAsJsonObject("textures").getAsJsonObject("@inline").get("base64"));
    }
    @Test void missingAssetsAndCyclesNeverBecomeAnUnrelatedDefaultCube() throws Exception {
        var missing=service().element("block","lamp","test",json("{\"modelResource\":\"test:block/missing\"}"));
        assertEquals("unavailable",missing.get("kind").getAsString()); assertFalse(missing.has("elements"));
        model("block/cycle","{\"parent\":\"test:block/cycle\"}");
        var cycle=service().element("block","lamp","test",json("{\"modelResource\":\"test:block/cycle\"}"));
        assertEquals("model_parent_cycle",cycle.get("reason").getAsString());
        var texture=service().element("block","lamp","test",json("{\"texture\":\"missing\"}"));
        assertEquals(1,texture.getAsJsonArray("missingTextures").size()); assertTrue(texture.getAsJsonObject("textures").isEmpty());
        assertThrows(IllegalArgumentException.class,()->service().element("block","lamp","test",json("{\"modelResource\":\"test:../../secret\"}")));
    }
    @Test void legacyCustomModelsUseTheSelectedTextureMapping() throws Exception {
        image("block/blue",0xff2233cc); image("block/red",0xffcc3322);
        Files.createDirectories(root.resolve("models"));
        Files.writeString(root.resolve("models/chair.json"),"{\"parent\":\"minecraft:block/cube_all\"}");
        Files.writeString(root.resolve("models/chair.json.textures"),"{\"mappings\":{\"default\":{\"map\":{\"all\":\"red\"}},\"blue\":{\"map\":{\"all\":\"blue\"}}}}");
        var result=service().element("block","chair","test",json("{\"renderType\":2,\"customModelName\":\"chair:blue\"}"));
        assertEquals(Set.of("test:block/blue"),result.getAsJsonObject("textures").keySet());
    }
    @Test void assetModelsShareTheSameInheritedGeometryAsElements() throws Exception {
        image("block/copper",0xffbb6633);
        Path path=model("block/copper","{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"test:block/copper\"}}");
        var asset=AssetDescriptor.fromFile(root,path);
        var scene=service().asset(asset,JsonParser.parseString(Files.readString(path)).getAsJsonObject());
        assertEquals("model",scene.get("kind").getAsString()); assertEquals(1,scene.getAsJsonArray("elements").size());
        assertEquals(1,scene.getAsJsonObject("textures").size());
    }
    @Test void doublePlantsUseBothHalvesAndTallTexturesAreOnlyCroppedWithAnimationMetadata() throws Exception {
        Path top=image("block/top",0xff55aa77); image("block/bottom",0xff997733);
        ImageIO.write(new BufferedImage(16,32,BufferedImage.TYPE_INT_ARGB),"png",top.toFile());
        var result=service().element("plant","flower","test",json("{\"texture\":\"top\",\"textureBottom\":\"bottom\",\"plantType\":\"double\"}"));
        assertEquals(4,result.getAsJsonArray("elements").size());
        assertEquals(32,result.getAsJsonArray("elements").get(3).getAsJsonObject().getAsJsonArray("to").get(1).getAsInt());
        assertFalse(result.getAsJsonObject("textures").getAsJsonObject("test:block/top").has("frame"));
        Files.writeString(top.resolveSibling("top.png.mcmeta"),"{\"animation\":{\"frames\":[1,0]}}");
        var animated=service().element("block","flower","test",json("{\"texture\":\"top\"}"));
        assertEquals(16,animated.getAsJsonObject("textures").getAsJsonObject("test:block/top").getAsJsonObject("frame").get("y").getAsInt());
    }
}
