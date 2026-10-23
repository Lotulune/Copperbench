package dev.copperbench.assets;

import com.google.gson.*;
import dev.copperbench.core.application.AssetPreviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtendedModelPreviewTest {
    @TempDir Path root;
    private Path write(String path,String text)throws Exception {Path file=root.resolve(path);Files.createDirectories(file.getParent());Files.writeString(file,text);return file;}
    private Path texture(String path)throws Exception {
        Path file=root.resolve(path);Files.createDirectories(file.getParent());var image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,(x+y)%4==0?0xff447755:0xffc98145);
        ImageIO.write(image,"png",file.toFile());return file;
    }
    private JsonObject asset(Path file)throws Exception {var assets=new AssetWorkspaceService(root).list();var descriptor=AssetDescriptor.fromFile(root,file);return new ModelPreviewService(root,assets).asset(descriptor,Files.readAllBytes(file));}
    private JsonObject element(String type,String values)throws Exception {return new ModelPreviewService(root,new AssetWorkspaceService(root).list()).element(type,"subject","test",JsonParser.parseString(values).getAsJsonObject());}
    private double min(JsonObject scene,int axis){return coordinates(scene,axis).stream().mapToDouble(Double::doubleValue).min().orElseThrow();}
    private double max(JsonObject scene,int axis){return coordinates(scene,axis).stream().mapToDouble(Double::doubleValue).max().orElseThrow();}
    private List<Double> coordinates(JsonObject scene,int axis){List<Double> out=new ArrayList<>();for(JsonElement raw:scene.getAsJsonArray("meshes")){JsonArray p=raw.getAsJsonObject().getAsJsonArray("positions");for(int i=axis;i<p.size();i+=3)out.add(p.get(i).getAsDouble());}return out;}

    @Test void objResolvesActualMaterialsNormalsNegativeIndicesAndTextureMapping()throws Exception {
        texture("models/pixels.png");texture("src/main/resources/assets/test/textures/block/mapped.png");
        write("models/shape.mtl","newmtl copper\nKd 1 0.5 0.25\nd 0.7\nmap_Kd pixels.png\n");
        Path file=write("models/shape.obj","mtllib shape.mtl\nv 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nvn 0 0 1\nusemtl copper\nf -4/1/1 -3/2/1 -2/3/1 -1/4/1\n");
        JsonObject scene=asset(file),mesh=scene.getAsJsonArray("meshes").get(0).getAsJsonObject();
        assertEquals("mesh",scene.get("kind").getAsString());assertEquals(1,scene.getAsJsonObject("textures").size());assertEquals(18,mesh.getAsJsonArray("positions").size());
        assertEquals(.7,mesh.get("opacity").getAsDouble(),.0001);assertEquals(.5,mesh.getAsJsonArray("color").get(1).getAsDouble());assertEquals(1,mesh.getAsJsonArray("normals").get(2).getAsDouble());
        write("models/shape.obj.textures","{\"mappings\":{\"mapped\":{\"map\":{\"copper\":\"mapped\"}}}}");
        var bound=element("block","{\"renderType\":3,\"customModelName\":\"shape:mapped\"}");
        assertTrue(bound.getAsJsonObject("textures").has("test:block/mapped"));
        var graph=new AssetWorkspaceService(root).referenceGraph();var descriptor=AssetDescriptor.fromFile(root,file);
        assertEquals(AssetCategory.MODEL,descriptor.category());assertEquals("model",AssetPreviewService.preview(root,graph,descriptor.id(),descriptor.sha256()).get("kind").getAsString());
    }
    @Test void concaveObjPolygonsHaveTheCorrectAreaAndBoundedMaterialBatches()throws Exception {
        Path file=write("models/concave.obj","v 0 0 0\nv 2 0 0\nv 2 2 0\nv 1 1 0\nv 0 2 0\nf 1 2 3 4 5\n");
        var scene=asset(file);var p=scene.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("positions");
        assertEquals(27,p.size());double area=0;for(int i=0;i<p.size();i+=9){double ax=p.get(i+3).getAsDouble()-p.get(i).getAsDouble(),ay=p.get(i+4).getAsDouble()-p.get(i+1).getAsDouble(),bx=p.get(i+6).getAsDouble()-p.get(i).getAsDouble(),by=p.get(i+7).getAsDouble()-p.get(i+1).getAsDouble();area+=Math.abs(ax*by-ay*bx)/2;}
        assertEquals(3,area,.00001);assertTrue(scene.get("untextured").getAsBoolean());
        String repeated="v 0 0 0\nv 1 0 0\nv 0 1 0\n"+"f 1 2 3\n".repeat(2000);assertEquals(1,asset(write("models/many.obj",repeated)).getAsJsonArray("meshes").size());
    }
    @Test void objCannotReadUnindexedOrExternalMaterialTextures()throws Exception {
        write("models/escape.mtl","newmtl material\nmap_Kd ../../secret.png\n");
        Path file=write("models/escape.obj","mtllib escape.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl material\nf 1 2 3\n");
        JsonObject scene=asset(file);assertTrue(scene.getAsJsonObject("textures").isEmpty());assertEquals(1,scene.getAsJsonArray("missingTextures").size());
    }
    @Test void bbmodelEmbeddedTexturesAndNestedVersionFiveGroupsKeepTheirTransforms()throws Exception {
        String image=Base64.getEncoder().encodeToString(Files.readAllBytes(texture("models/embedded.png")));
        JsonObject document=JsonParser.parseString("""
                {"meta":{"format_version":"5.0","model_format":"free"},"resolution":{"width":16,"height":16},
                "elements":[{"uuid":"cube","type":"cube","from":[8,8,0],"to":[16,16,8],"faces":{"south":{"uv":[0,0,16,16],"texture":0},"north":{"texture":null},"up":{"texture":null},"down":{"texture":null},"east":{"texture":null},"west":{"texture":null}}}],
                "groups":[{"uuid":"parent","origin":[8,0,0],"rotation":[0,0,90]},{"uuid":"child","origin":[8,8,0],"rotation":[0,0,90]}],
                "outliner":[{"uuid":"parent","children":[{"uuid":"child","children":["cube"]}]}],"textures":[]}
                """).getAsJsonObject();
        JsonObject texture=new JsonObject();texture.addProperty("uuid","texture");texture.addProperty("path","C:/outside-workspace/absent.png");texture.addProperty("source","data:image/png;base64,"+image);document.getAsJsonArray("textures").add(texture);
        JsonObject scene=asset(write("models/nested.bbmodel",document.toString()));
        assertEquals(-.5,min(scene,0),.00001);assertEquals(0,max(scene,0),.00001);assertEquals(-.5,min(scene,1),.00001);assertEquals(0,max(scene,1),.00001);
        assertEquals(1,scene.getAsJsonObject("textures").size());assertTrue(scene.getAsJsonArray("missingTextures").isEmpty());
        assertEquals(image,scene.getAsJsonObject("textures").entrySet().iterator().next().getValue().getAsJsonObject().get("base64").getAsString());
    }
    @Test void bbmodelMeshVerticesAreLocalToTheirOriginAndHiddenGeometryStaysHidden()throws Exception {
        JsonObject scene=asset(write("models/mesh.bbmodel","""
                {"resolution":{"width":16,"height":16},"elements":[
                {"uuid":"mesh","type":"mesh","origin":[16,0,0],"rotation":[0,0,90],"vertices":{"a":[0,0,0],"b":[16,0,0],"c":[0,16,0]},"faces":{"f":{"vertices":["a","b","c"],"uv":{"a":[0,0],"b":[16,0],"c":[0,16]}}}},
                {"uuid":"hidden","visibility":false,"from":[0,0,0],"to":[100,100,100]}],"outliner":["mesh","hidden"]}
                """));
        assertEquals(0,min(scene,0),.00001);assertEquals(1,max(scene,0),.00001);assertEquals(1,max(scene,1),.00001);assertEquals(1,scene.getAsJsonArray("meshes").size());
        assertTrue(scene.get("untextured").getAsBoolean());
    }
    @Test void malformedAndCyclicBlockbenchDataIsRejectedWithoutSyntheticGeometry()throws Exception {
        assertThrows(java.io.IOException.class,()->asset(write("models/cycle.bbmodel","{\"elements\":[],\"groups\":[{\"uuid\":\"g\",\"children\":[\"g\"]}],\"outliner\":[\"g\"]}")));
        assertThrows(java.io.IOException.class,()->asset(write("models/missing-vertex.bbmodel","{\"elements\":[{\"type\":\"mesh\",\"vertices\":{},\"faces\":{\"x\":{\"vertices\":[\"a\",\"b\",\"c\"]}}}]}")));
    }
    @Test void modernJavaEntityUsesTheProjectSkinAndConfiguredModelVersion()throws Exception {
        String source=Files.readString(Path.of("src/test/resources/entitymodel-mojmap-1.17.x.java"));
        write("models/mojmap-1.17.x/Creature.java",source);write("models/mojmap-1.21.x/Creature.java",source.replace("8.0F, 12.0F, 4.0F","8.0F, 16.0F, 4.0F"));
        texture("src/main/resources/assets/test/textures/entities/skin.png");
        JsonObject values=JsonParser.parseString("{\"mobModelName\":\"Creature\",\"mobModelTexture\":\"skin\"}").getAsJsonObject();
        JsonObject scene=new ModelPreviewService(root,new AssetWorkspaceService(root).list(),List.of("mojmap-1.21.x","mojmap-1.17.x")).element("livingentity","creature","test",values);
        assertEquals("mesh",scene.get("kind").getAsString());assertEquals("java",scene.get("format").getAsString());assertTrue(scene.getAsJsonObject("textures").has("test:entities/skin"));
        assertTrue(scene.get("defaultPose").getAsBoolean());assertEquals(1,scene.getAsJsonArray("meshes").size());
    }
    @Test void javaAssetParsesGeometryWithoutExecutingConstructorsOrStaticInitializers()throws Exception {
        String property="copperbench.preview.executed";System.clearProperty(property);
        Path file=write("models/Legacy.java","""
                public class Legacy {
                  static { System.setProperty("copperbench.preview.executed", "yes"); }
                  private final ModelRenderer body;
                  public Legacy() { this.textureWidth=64; this.textureHeight=32; System.setProperty("copperbench.preview.executed", "yes");
                    this.body=new ModelRenderer(this); this.body.setRotationPoint(0,24,0);
                    this.body.setTextureOffset(0,0).addBox(-4,-8,-2,8,8,4,0.0F,false);
                    setRotationAngle(this.body,0,0,0); }
                }
                """);
        JsonObject scene=asset(file);assertEquals("mesh",scene.get("kind").getAsString());assertNull(System.getProperty(property));
        assertEquals(0,min(scene,1),.00001);assertEquals(.5,max(scene,1),.00001);assertEquals(-.25,min(scene,0),.00001);assertTrue(scene.get("untextured").getAsBoolean());
        String dynamic=Files.readString(file).replace("setRotationAngle(this.body,0,0,0);","for(int i=0;i<5;i++)this.body.addBox(i,0,0,1,1,1);");
        var unavailable=asset(write("models/Dynamic.java",dynamic));assertEquals("unavailable",unavailable.get("kind").getAsString());assertEquals("java_geometry_unsupported",unavailable.get("reason").getAsString());
    }
    @Test void yarnJavaExportKeepsChildPivotAndInflation()throws Exception {
        Path file=write("models/Yarn.java","""
                public class Yarn {
                  public static TexturedModelData getTexturedModelData() {
                    ModelData data=new ModelData(); ModelPartData root=data.getRoot();
                    ModelPartData body=root.addChild("body",ModelPartBuilder.create(),ModelTransform.pivot(0,24,0));
                    ModelPartData child=body.addChild("child",ModelPartBuilder.create().uv(0,0).mirrored().cuboid(0,0,0,8,8,8,new Dilation(1.0F)),ModelTransform.of(0,0,0,0,0,0));
                    return TexturedModelData.of(data,64,32);
                  }
                }
                """);
        JsonObject scene=asset(file);assertEquals(-9d/16,min(scene,0),.0001);assertEquals(1d/16,max(scene,0),.0001);assertEquals(1,scene.getAsJsonArray("meshes").size());
    }

    @Test void cyclicAndExcessivelyDeepJavaBuildersReturnUnsupportedInsteadOfOverflowing()throws Exception {
        String factory="""
                public class BuilderModel {
                  public static LayerDefinition createBodyLayer() {
                    MeshDefinition mesh=new MeshDefinition(); PartDefinition root=mesh.getRoot();
                    %s
                    root.addOrReplaceChild("body", b, PartPose.ZERO);
                    return LayerDefinition.create(mesh,64,32);
                  }
                }
                """;
        for(String definition:List.of(
                "CubeListBuilder b=CubeListBuilder.create(); b=b.texOffs(0,0).addBox(0,0,0,1,1,1);",
                "CubeListBuilder a=CubeListBuilder.create(); CubeListBuilder b=a; a=b;",
                "CubeListBuilder b=CubeListBuilder.create()"+".texOffs(0,0)".repeat(180)+".addBox(0,0,0,1,1,1);")) {
            var failure=assertThrows(JavaModelPreview.UnsupportedGeometry.class,()->JavaModelPreview.parse(factory.formatted(definition),null));
            assertTrue(failure.getMessage().contains("cyclic")||failure.getMessage().contains("depth"),failure.getMessage());
        }
    }

    @Test void currentClassHelpersCannotSilentlyOmitAdditionalJavaGeometry()throws Exception {
        for(String call:List.of("addExtraGeometry()","this.addExtraGeometry()","HelperModel.addExtraGeometry()")) {
            String code="""
                    public class HelperModel {
                      private static ModelRenderer body;
                      public HelperModel() {
                        body=new ModelRenderer(this);
                        body.addBox(0,0,0,8,8,8);
                        %s;
                      }
                      private static void addExtraGeometry() { body.addBox(8,0,0,8,8,8); }
                    }
                    """.formatted(call);
            var failure=assertThrows(JavaModelPreview.UnsupportedGeometry.class,()->JavaModelPreview.parse(code,null));
            assertTrue(failure.getMessage().contains("current-class helper"),failure.getMessage());
        }
    }

    /** Generates browser fixtures from these real parsers; invoked by the task-local verification init script. */
    public static void main(String[] args)throws Exception {
        ExtendedModelPreviewTest fixture=new ExtendedModelPreviewTest();fixture.root=Files.createDirectories(Path.of(args[0]).resolve("workspace"));
        Path pixels=fixture.texture("models/pixels.png");
        fixture.write("models/pyramid.mtl","newmtl copper\nKd 1 1 1\nmap_Kd pixels.png\n");
        Path obj=fixture.write("models/pyramid.obj","mtllib pyramid.mtl\nv -0.8 0 -0.8\nv 0.8 0 -0.8\nv 0.8 0 0.8\nv -0.8 0 0.8\nv 0 1.8 0\nvt 0 0\nvt 1 0\nvt 0.5 1\nusemtl copper\nf 1/1 2/2 5/3\nf 2/1 3/2 5/3\nf 3/1 4/2 5/3\nf 4/1 1/2 5/3\nf 4/1 3/2 2/3 1/1\n");
        JsonObject bb=JsonParser.parseString("""
                {"meta":{"model_format":"free","format_version":"5.0"},"resolution":{"width":16,"height":16},"textures":[],
                "elements":[{"uuid":"base","from":[-6,0,-6],"to":[6,5,6],"box_uv":true,"uv_offset":[0,0]},
                {"uuid":"upper","from":[-3,5,-3],"to":[3,20,3],"origin":[0,5,0],"rotation":[0,0,-22.5],"faces":{}}],
                "groups":[{"uuid":"turn","origin":[0,0,0],"rotation":[0,30,0]}],"outliner":[{"uuid":"turn","children":["base","upper"]}]}
                """).getAsJsonObject();
        JsonObject tex=new JsonObject();tex.addProperty("source","data:image/png;base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(pixels)));bb.getAsJsonArray("textures").add(tex);
        for(JsonElement raw:bb.getAsJsonArray("elements")){JsonObject faces=new JsonObject();for(String direction:PreviewModels.DIRECTIONS){JsonObject face=new JsonObject();face.addProperty("texture",0);face.add("uv",PreviewMeshBuilder.array(0,0,16,16));faces.add(direction,face);}raw.getAsJsonObject().add("faces",faces);}
        Path blockbench=fixture.write("models/rotated.bbmodel",bb.toString());
        fixture.write("models/Creature.java",Files.readString(Path.of("src/test/resources/entitymodel-mojmap-1.17.x.java")));
        fixture.texture("src/main/resources/assets/test/textures/entities/skin.png");
        JsonObject scenes=new JsonObject();scenes.add("obj",fixture.asset(obj));scenes.add("bbmodel",fixture.asset(blockbench));
        scenes.add("java",fixture.element("livingentity","{\"mobModelName\":\"Creature\",\"mobModelTexture\":\"skin\"}"));
        JsonObject result=new JsonObject();result.addProperty("schemaVersion","1.0");result.add("scenes",scenes);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }
}
