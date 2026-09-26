package dev.copperbench.assets;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.copperbench.generator.WorkspaceExecutionSnapshot;
import net.mcreator.io.UserFolderManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Offline inspection of byte-identical item/blockstate resources from actual versioned client archives. */
class Stage17RealItemReferencesTest {
    @Test
    @EnabledIfSystemProperty(named = "copperbench.stage17.resourceCatalogProbe", matches = "true")
    void actualModernItemBranchesResolveAcrossBothLoaderContexts() throws Exception {
        Path evidence = Files.createDirectories(Path.of("build/stage17-real-item-references", UUID.randomUUID().toString()).toAbsolutePath());
        JsonArray results = new JsonArray();
        try {
            for (String loader : List.of("fabric", "neoforge")) for (String version : List.of("26.1.2", "26.2")) {
                String generator = loader + "-" + version;
                Path root = Files.createDirectories(evidence.resolve(generator));
                Files.writeString(root.resolve("probe.mcreator"), "{\"workspaceSettings\":{\"currentGenerator\":\"" + generator + "\"}}");
                Path archive = UserFolderManager.getGradleHome().toPath().resolve("caches/fabric-loom/" + version + "/minecraft-client.jar");
                String archiveHash = WorkspaceExecutionSnapshot.sha256(archive);
                JsonObject result = new JsonObject(); results.add(result);
                result.addProperty("generator", generator); result.addProperty("archive", archive.toString());
                result.addProperty("archiveSha256", archiveHash);
                JsonArray inputs = new JsonArray(); result.add("inputs", inputs);
                try (ZipFile zip = new ZipFile(archive.toFile())) {
                    assertEquals(version, JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("version.json")).readAllBytes(),
                            StandardCharsets.UTF_8)).getAsJsonObject().get("id").getAsString());
                    for (String resource : List.of("items/bow.json", "items/bundle.json", "items/shield.json", "items/chest.json", "blockstates/oak_log.json")) {
                        String source = "assets/minecraft/" + resource;
                        byte[] bytes;
                        try (var stream = zip.getInputStream(zip.getEntry(source))) { bytes = stream.readAllBytes(); }
                        Path copy = root.resolve("src/main/resources/assets/probe/" + resource);
                        Files.createDirectories(copy.getParent()); Files.write(copy, bytes);
                        JsonObject input = new JsonObject(); input.addProperty("archiveEntry", source);
                        input.addProperty("copiedPath", root.relativize(copy).toString());
                        input.addProperty("sha256", WorkspaceExecutionSnapshot.sha256(copy)); inputs.add(input);
                    }
                }
                var graph = new AssetWorkspaceService(root).referenceGraph();
                result.add("references", new GsonBuilder().create().toJsonTree(graph.references()));
                result.add("diagnostics", new GsonBuilder().create().toJsonTree(graph.diagnostics()));
                assertTrue(graph.references().size() >= 12, graph.references().toString());
                assertTrue(graph.references().stream().allMatch(r -> r.resolution().equals("vanilla_resolved")
                        && r.resourceVersion().equals(version)), graph.references().toString());
                assertFalse(graph.diagnostics().stream().anyMatch(d -> d.severity() == AssetDiagnostic.Severity.ERROR), graph.diagnostics().toString());
                assertTrue(graph.diagnostics().stream().allMatch(d -> d.code().equals("ASSET_RENDERER_REFERENCES_UNVERIFIED")), graph.diagnostics().toString());
                assertEquals(archiveHash, WorkspaceExecutionSnapshot.sha256(archive));
                result.addProperty("status", "passed");
            }
        } finally {
            Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(results), StandardCharsets.UTF_8);
            System.out.println("STAGE17_REAL_ITEM_REFERENCE_EVIDENCE=" + evidence);
        }
    }
}
