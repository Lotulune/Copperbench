package dev.copperbench.assets;

import com.google.gson.*;
import dev.copperbench.generator.ResourceDependencyCapture;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class PreviewArchiveReadsTest {
    @TempDir Path temporary;
    private record Fixture(Path root,Path archive,AssetDescriptor model,JsonObject document,AssetDescriptor meshModel,JsonObject meshDocument) {}

    @Test void thirtyTwoVanillaTexturesAndMetadataShareTwoFullReadPhaseHashes()throws Exception {
        assertBatch(fixture(false));
    }
    @Test void thirtyTwoDependencyTexturesShareTwoFullReadPhaseHashes()throws Exception {
        assertBatch(fixture(true));
    }
    private void assertBatch(Fixture fixture)throws Exception {
        AtomicInteger scopes=new AtomicInteger(),hashes=new AtomicInteger();
        var service=new ModelPreviewService(fixture.root(),new AssetWorkspaceService(fixture.root()).list(),List.of(),()->{
            scopes.incrementAndGet();return new PreviewArchiveReads(path->{hashes.incrementAndGet();return WorkspaceExecutionSnapshot.sha256(path);});
        });
        for(var input:List.of(Map.entry(fixture.model(),fixture.document()),Map.entry(fixture.meshModel(),fixture.meshDocument()))) {
            scopes.set(0);hashes.set(0);
            JsonObject scene=service.asset(input.getKey(),input.getValue());
            assertEquals(32,scene.getAsJsonObject("textures").size());assertTrue(scene.getAsJsonArray("missingTextures").isEmpty());
            assertEquals(1,scopes.get());assertEquals(2,hashes.get(),"One complete digest before and after each model/mesh preview, not per texture");
        }
    }

    @Test void changedArchivesCannotProduceASuccessfulPreviewEvenWithIdenticalSizeAndMtime()throws Exception {
        for(boolean dependency:List.of(false,true)) {
            Fixture fixture=fixture(dependency);FileTime modified=Files.getLastModifiedTime(fixture.archive());long size=Files.size(fixture.archive());
            AtomicInteger hashes=new AtomicInteger();
            var service=new ModelPreviewService(fixture.root(),new AssetWorkspaceService(fixture.root()).list(),List.of(),()->new PreviewArchiveReads(path->{
                // The second full hash happens after all texture reads and after closing the ZipFile.
                if(hashes.incrementAndGet()==2)changeArchiveComment(path,modified);
                return WorkspaceExecutionSnapshot.sha256(path);
            }));
            IOException error=assertThrows(IOException.class,()->service.asset(fixture.model(),fixture.document()));
            assertTrue(error.getMessage().contains("changed"),error.getMessage());assertEquals(2,hashes.get());
            assertEquals(size,Files.size(fixture.archive()));assertEquals(modified,Files.getLastModifiedTime(fixture.archive()));
        }
    }

    @Test void preReadContentChangesAndUnsafeOrOversizedEntriesAreRejected()throws Exception {
        Fixture fixture=fixture(false);Path archive=fixture.archive();String expected=WorkspaceExecutionSnapshot.sha256(archive);
        try(PreviewArchiveReads reads=new PreviewArchiveReads()) {
            assertThrows(IOException.class,()->reads.read(archive,expected,"assets/minecraft/../secret",4096));
            assertThrows(IOException.class,()->reads.read(archive,expected,"assets/minecraft/textures/block/t0.png",1));
        }
        FileTime modified=Files.getLastModifiedTime(archive);changeArchiveComment(archive,modified);
        try(PreviewArchiveReads reads=new PreviewArchiveReads()) {
            assertThrows(IOException.class,()->reads.read(archive,expected,"assets/minecraft/textures/block/t0.png",4096));
        }
    }

    private static void changeArchiveComment(Path archive,FileTime modified)throws IOException {
        byte[] bytes=Files.readAllBytes(archive);bytes[bytes.length-1]^=1;
        Files.write(archive,bytes);Files.setLastModifiedTime(archive,modified);
    }
    private Fixture fixture(boolean dependency)throws Exception {
        Path root=Files.createDirectories(temporary.resolve(dependency?"dependency":"vanilla"));
        Files.writeString(root.resolve("test.mcreator"),"{\"workspaceSettings\":{\"currentGenerator\":\"fabric-1.21.1\"}}");
        Path vanilla=root.resolve(".gradle/caches/fabric-loom/1.21.1/minecraft-client.jar");
        writeArchive(vanilla,"minecraft",!dependency,false);
        Path archive=dependency?temporary.resolve("dependency-resources.jar"):vanilla;
        if(dependency)writeArchive(archive,"dep",true,true);
        String namespace=dependency?"dep":"minecraft";
        JsonObject model=new JsonObject();JsonArray elements=new JsonArray();model.add("elements",elements);
        for(int i=0;i<32;i++) {
            JsonObject box=PreviewModels.box(i,0,0,i+1,1,1);JsonObject face=new JsonObject(),faces=new JsonObject();
            face.addProperty("texture",namespace+":block/t"+i);faces.add("north",face);box.add("faces",faces);elements.add(box);
        }
        Path file=root.resolve("src/main/resources/assets/probe/models/block/grid.json");Files.createDirectories(file.getParent());Files.writeString(file,model.toString());
        JsonObject mesh=model.deepCopy();JsonArray textures=new JsonArray();mesh.add("textures",textures);
        for(int i=0;i<32;i++) {
            JsonObject texture=new JsonObject();texture.addProperty("source",namespace+":block/t"+i);textures.add(texture);
            mesh.getAsJsonArray("elements").get(i).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north").addProperty("texture",i);
        }
        Path meshFile=file.resolveSibling("grid.bbmodel");Files.writeString(meshFile,mesh.toString());
        if(dependency)capture(root,archive);
        return new Fixture(root,archive,AssetDescriptor.fromFile(root,file),model,AssetDescriptor.fromFile(root,meshFile),mesh);
    }
    private static void writeArchive(Path path,String namespace,boolean textures,boolean dependency)throws Exception {
        Files.createDirectories(path.getParent());BufferedImage image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,0xffc98145);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();assertTrue(ImageIO.write(image,"png",bytes));
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(path))) {
            zip.setComment("original");
            if(dependency)entry(zip,"fabric.mod.json","{\"schemaVersion\":1,\"id\":\"preview_resources\",\"version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8));
            else {
                entry(zip,"version.json","{\"id\":\"1.21.1\"}".getBytes(StandardCharsets.UTF_8));
                entry(zip,"assets/minecraft/models/block/cube_all.json","{}".getBytes(StandardCharsets.UTF_8));
                entry(zip,"assets/minecraft/models/item/generated.json","{}".getBytes(StandardCharsets.UTF_8));
            }
            if(textures)for(int i=0;i<32;i++) {
                String resource="assets/"+namespace+"/textures/block/t"+i+".png";
                entry(zip,resource,bytes.toByteArray());entry(zip,resource+".mcmeta","{\"animation\":{}}".getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    private static void entry(ZipOutputStream zip,String name,byte[] bytes)throws IOException {zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
    private static void capture(Path root,Path archive)throws Exception {
        // Record the dependency fixture through the existing capture API; this does not execute Gradle.
        var ticket=ResourceDependencyCapture.prepare(root,new ArrayList<>(List.of("build")),ignored->{});assertNotNull(ticket);
        JsonObject document=new JsonObject(),artifact=new JsonObject();document.addProperty("complete",true);document.add("configurations",new JsonArray());
        artifact.addProperty("path",archive.toString());artifact.addProperty("configuration",":runtimeClasspath");artifact.addProperty("component","org.example:preview_resources:1.0.0");
        JsonArray artifacts=new JsonArray();artifacts.add(artifact);document.add("artifacts",artifacts);
        Files.writeString(root.resolve(ticket.capturePath()),document.toString());ticket.finish(true);
    }
}
