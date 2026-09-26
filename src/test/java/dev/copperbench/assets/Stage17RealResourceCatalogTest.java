package dev.copperbench.assets;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit, offline probe of actual cached Minecraft client archives; no synthetic vanilla resources. */
class Stage17RealResourceCatalogTest {
    @Test
    @EnabledIfSystemProperty(named = "copperbench.stage17.resourceCatalogProbe", matches = "true")
    void originalFiveVanillaParentsResolveAgainstRealArchivesAcrossEightTrackContexts() throws Exception {
        Path evidence = Path.of("build/stage17-resource-catalog-probes", UUID.randomUUID().toString()).toAbsolutePath();
        Files.createDirectories(evidence);
        JsonArray results = new JsonArray();
        try {
            for (String loader : List.of("fabric", "neoforge")) for (String version : List.of("1.20.1", "1.21.1", "26.1.2", "26.2")) {
                String generator = loader + "-" + version;
                Path workspace = evidence.resolve(generator);
                Files.createDirectories(workspace);
                Files.writeString(workspace.resolve("probe.mcreator"), "{\"workspaceSettings\":{\"currentGenerator\":\"" + generator + "\"}}", StandardCharsets.UTF_8);
                Path assets = workspace.resolve("src/main/resources/assets/probe");
                for (String category : List.of("block", "item")) {
                    Path texture = assets.resolve("textures/" + category + "/sample.png");
                    Files.createDirectories(texture.getParent());
                    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", texture.toFile());
                }
                List<String> parents = List.of("block/cube_all", "block/cube_all", "item/generated", "item/generated", "item/handheld");
                for (int index = 0; index < parents.size(); index++) {
                    boolean block = index < 2;
                    Path model = assets.resolve("models/" + (block ? "block" : "item") + "/sample" + index + ".json");
                    Files.createDirectories(model.getParent());
                    Files.writeString(model, "{\"parent\":\"minecraft:" + parents.get(index) + "\",\"textures\":{\""
                            + (block ? "all" : "layer0") + "\":\"probe:" + (block ? "block" : "item") + "/sample\"}}", StandardCharsets.UTF_8);
                }
                JsonObject result = new JsonObject(); result.addProperty("generator", generator); results.add(result);
                var graph = new AssetWorkspaceService(workspace).referenceGraph();
                result.add("references", new GsonBuilder().create().toJsonTree(graph.references()));
                result.add("diagnostics", new GsonBuilder().create().toJsonTree(graph.diagnostics()));
                assertTrue(graph.diagnostics().isEmpty(), generator + ": " + graph.diagnostics());
                var vanilla = graph.references().stream().filter(reference -> reference.rawValue().startsWith("minecraft:")).toList();
                assertEquals(5, vanilla.size(), generator);
                assertTrue(vanilla.stream().allMatch(reference -> reference.resolution().equals("vanilla_resolved")
                        && reference.resourceVersion().equals(version) && reference.resourceSource().contains("sha256=")), generator);
                Path typo = assets.resolve("models/block/typo.json");
                Files.writeString(typo, "{\"parent\":\"minecraft:block/cube_all_typo\"}", StandardCharsets.UTF_8);
                var after = new AssetWorkspaceService(workspace).referenceGraph();
                assertTrue(after.diagnostics().stream().anyMatch(issue -> issue.code().equals("MISSING_ASSET_REFERENCE")
                        && issue.sourcePath().endsWith("typo.json")), generator);
                result.addProperty("typoRejected", true);
                result.addProperty("status", "passed");
            }
        } finally {
            Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(results), StandardCharsets.UTF_8);
            System.out.println("STAGE17_REAL_RESOURCE_EVIDENCE=" + evidence);
        }
    }
}
